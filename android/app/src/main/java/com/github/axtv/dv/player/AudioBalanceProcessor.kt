/*
 * Android 适配层 —— 「音量均衡」音频处理器。
 *
 * 证据（原版语义不是左右声道 balance，而是**响度对齐**）：
 * - src/app/p000/rs2.java：`extends BaseAudioProcessor`，构造入参就是 `EnumC0642fr`
 *   （= axtv.settings.Settings.AudioBalance，见 refactor/settings/.../Settings.java:1315），
 *   OFF 时整段直通；
 * - src/app/p000/os2.java:56-155：四档参数表 + dB→线性 + 一阶平滑系数；
 * - src/app/p000/qs2.java：参数容器，toString 给出字段名
 *   （startupGainDb / maxGainDb / minGainDb / silenceGateDb / calibrationSignalSec /
 *    programIntegrationSec / gainAttackSec / gainReleaseSec）；
 * - src/app/p000/po5.java:23-30：`buildAudioSink(...)` 把处理器塞进 DefaultAudioSink；
 * - src/app/p000/fl4.java:573-584：`audio_balance_level`（缺省 "off"）→ 枚举 → 状态流。
 * 目标响度 -20 dBFS 与 `- 0.691` 偏置在 rs2.java:212，峰值余量 0.98/peak 在 rs2.java:256。
 *
 * 与原版的差异（如实记录，不冒充逐位还原）：
 * 原版在功率统计前对每个声道做一遍 ps2 双级预滤波（src/app/p000/ps2.java，纯 DSP 状态机），
 * 本实现直接用时域均方功率；其余常数、公式、平滑与限幅顺序逐条照抄 rs2.queueInput。
 */
package com.github.axtv.dv.player

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import axtv.settings.Settings
import com.github.axtv.dv.adapter.AppRuntime
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** dB → 线性（os2.m8819b）。 */
private fun dbToLinear(db: Double): Float = 10.0.pow(db / 20.0).toFloat()

/** 一阶平滑系数：`1 - exp(-t/tau)` 夹到 0..1；时间常数非正时直接到位（os2.m8818a）。 */
private fun smoothingCoeff(elapsedSec: Double, timeConstantSec: Double): Double = when {
    elapsedSec <= 0.0 -> 0.0
    timeConstantSec <= 0.0 -> 1.0
    else -> (1.0 - exp(-elapsedSec / timeConstantSec)).coerceIn(0.0, 1.0)
}

/**
 * 把短期响度对齐到 -20 dBFS 的 Media3 音频处理器（设置项 `audio_balance` 的唯一消费点）。
 *
 * 四档映射（逐档取自 os2.m8820c；档位越高 → 跟踪越快、可调范围越大）：
 *
 * | 档位 | 起始增益 | 上限 | 下限 | 静音门限 | 标定窗口 | 积分常数 | attack | release |
 * |---|---|---|---|---|---|---|---|---|
 * | 关闭 | — | — | — | -120 | — | — | 0.05 | 1.2 |（整段直通，参数不参与）
 * | 低   | -0.5 | 1.0 | -8  | -55 | 2.5 | 10 | 3.0 | 8.0 |
 * | 中   | -1.5 | 2.0 | -12 | -58 | 2.0 | 8  | 2.0 | 8.0 |
 * | 高   | -3.0 | 2.5 | -16 | -60 | 1.5 | 6  | 1.5 | 8.0 |
 *
 * release 三档同为 8 s：原版 `qs2` 的第 8 个实参恒为初始值 8.0，
 * `os2.java:143` 里给 high 赋的 `d9 = 5.0` 没有传进构造器（死值），此处按传进去的值还原。
 *
 * 线程：Media3 播放线程单线程调用；设置值最多每秒读一次（SharedPreferences 读为内存态）。
 */
class AudioBalanceProcessor : BaseAudioProcessor() {

    private var level: Settings.AudioBalance = AppRuntime.settings?.audioBalance()
        ?: Settings.AudioBalance.OFF
    private var params: Params = Params.of(level)
    private var appliedGain: Float = params.startupGainLinear
    private var calibrationElapsedSec: Double = 0.0
    private var energyAccumulator: Double = 0.0
    private var calibratedSamples: Long = 0L
    private var integratedPower: Double? = null
    private var lastLevelCheckMs: Long = 0L

    override fun onConfigure(
        inputAudioFormat: AudioProcessor.AudioFormat,
    ): AudioProcessor.AudioFormat =
        // 与 rs2.onConfigure 一致：只接 PCM 16bit / PCM float，其它编码声明不处理。
        when (inputAudioFormat.encoding) {
            C.ENCODING_PCM_16BIT, C.ENCODING_PCM_FLOAT -> inputAudioFormat
            else -> AudioProcessor.AudioFormat.NOT_SET
        }

    override fun onFlush() {
        resetState()
    }

    override fun onReset() {
        resetState()
    }

    override fun queueInput(input: ByteBuffer) {
        if (!input.hasRemaining()) {
            return
        }
        val output = replaceOutputBuffer(input.remaining()).order(ByteOrder.nativeOrder())
        refreshLevel()
        if (level == Settings.AudioBalance.OFF || !isActive()) {
            output.put(input)
            output.flip()
            return
        }
        val floatInput = inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT
        val bytesPerSample = if (floatInput) 4 else 2
        val channels = inputAudioFormat.channelCount.coerceAtLeast(1)
        val sampleRate = inputAudioFormat.sampleRate.takeIf { it > 0 } ?: FALLBACK_SAMPLE_RATE
        val totalSamples = input.remaining() / bytesPerSample
        if (totalSamples <= 0) {
            output.put(input)
            output.flip()
            return
        }
        val frames = max(1, totalSamples / channels)

        // ---- 第一遍：解码成归一化浮点，用于功率/峰值统计
        val scan = input.duplicate().order(ByteOrder.nativeOrder())
        val samples = FloatArray(totalSamples)
        var peak = 0f
        for (i in 0 until totalSamples) {
            val value = if (floatInput) scan.float else scan.short / 32768.0f
            samples[i] = value
            val magnitude = abs(value)
            if (magnitude > peak) peak = magnitude
        }

        // ---- 分声道均方功率 → 加权求和（5.1：LFE 记 0、第 5 声道起 ×1.41，同 rs2 的混合权重）
        val powerPerChannel = DoubleArray(channels)
        for (frame in 0 until frames) {
            val base = frame * channels
            for (channel in 0 until channels) {
                val value = samples[base + channel].toDouble()
                powerPerChannel[channel] += value * value
            }
        }
        var power = 0.0
        for (channel in 0 until channels) {
            val weight = when {
                channels <= 2 -> 1.0
                channel == 3 -> 0.0
                channel >= 4 -> 1.41
                else -> 1.0
            }
            power += weight * (powerPerChannel[channel] / frames)
        }
        power = power.coerceAtLeast(MIN_POWER)

        val windowSec = frames.toDouble() / max(sampleRate, MIN_INTEGRATION_RATE)
        val levelDb = log10(power) * 10.0 - LOUDNESS_OFFSET_DB
        if (levelDb > params.silenceGateDb) {
            calibrationElapsedSec += windowSec
            val current = integratedPower
            if (current == null) {
                // 标定阶段：先累积平均功率，累计到 calibrationSignalSec 才建立初值。
                energyAccumulator += power * frames
                calibratedSamples += frames.toLong()
                if (calibrationElapsedSec >= params.calibrationSignalSec && calibratedSamples > 0L) {
                    integratedPower = energyAccumulator / calibratedSamples
                }
            } else {
                integratedPower = (power - current) *
                    smoothingCoeff(windowSec, params.programIntegrationSec) + current
            }
        }

        val estimate = integratedPower
        val targetDb = if (estimate == null) {
            params.startupGainDb
        } else {
            val estimateDb = log10(estimate.coerceAtLeast(MIN_POWER)) * 10.0 - LOUDNESS_OFFSET_DB
            (-TARGET_LOUDNESS_DB - estimateDb).coerceIn(params.minGainDb, params.maxGainDb)
        }
        val limiter = if (peak > 0f) min(PEAK_HEADROOM / peak, Float.MAX_VALUE) else Float.MAX_VALUE
        val requested = min(dbToLinear(targetDb), limiter)
        val coef = smoothingCoeff(
            windowSec,
            if (requested < appliedGain) params.gainAttackSec else params.gainReleaseSec,
        )
        appliedGain = (((requested - appliedGain) * coef + appliedGain)
            .coerceIn(params.minGainLinear.toDouble(), params.maxGainLinear.toDouble())).toFloat()
        // 限幅优先：峰值余量不足时立刻压下来（不做平滑），与 rs2 末尾的硬覆盖一致。
        if (limiter < appliedGain) appliedGain = limiter

        // ---- 第二遍：按增益写回；PCM16 走整数口径（同 rs2），float 夹到 ±1
        if (floatInput) {
            for (i in 0 until totalSamples) {
                output.putFloat((input.float * appliedGain).coerceIn(-1f, 1f))
            }
        } else {
            for (i in 0 until totalSamples) {
                val scaled = (input.short * appliedGain).toInt()
                output.putShort(scaled.coerceIn(-32768, 32767).toShort())
            }
        }
        output.flip()
    }

    private fun resetState() {
        params = Params.of(level)
        appliedGain = if (level == Settings.AudioBalance.OFF) 1f else params.startupGainLinear
        calibrationElapsedSec = 0.0
        energyAccumulator = 0.0
        calibratedSamples = 0L
        integratedPower = null
    }

    /** 每秒最多读一次设置，避免每个音频缓冲都读一次配置。 */
    private fun refreshLevel() {
        val now = System.currentTimeMillis()
        if (now - lastLevelCheckMs < LEVEL_POLL_MS) {
            return
        }
        lastLevelCheckMs = now
        val next = AppRuntime.settings?.audioBalance() ?: Settings.AudioBalance.OFF
        if (next != level) {
            level = next
            resetState()
        }
    }

    private companion object {
        /** 目标响度 -20 dBFS（rs2.java:212）。 */
        const val TARGET_LOUDNESS_DB = 20.0

        /** 功率→dB 的固定偏置（rs2.java:212 的 `- 0.691`）。 */
        const val LOUDNESS_OFFSET_DB = 0.691

        /** 峰值余量 0.98/peak（rs2.java:256）。 */
        const val PEAK_HEADROOM = 0.98f

        /** 低于此功率按静音处理，避免 log10(0)（rs2.java:205）。 */
        const val MIN_POWER = 1.0e-12

        /** 采样率下限：异常格式按 8k 计算时间窗（rs2.java:214）。 */
        const val MIN_INTEGRATION_RATE = 8000

        /** 格式未给采样率时的兜底（rs2 用 OpusUtil.SAMPLE_RATE = 48000）。 */
        const val FALLBACK_SAMPLE_RATE = 48000

        /** 档位轮询间隔。 */
        const val LEVEL_POLL_MS = 1_000L
    }

    /** 单档参数（字段顺序与 qs2.toString 一致，便于对账）。 */
    private class Params(
        val startupGainDb: Double,
        val maxGainDb: Double,
        val minGainDb: Double,
        val silenceGateDb: Double,
        val calibrationSignalSec: Double,
        val programIntegrationSec: Double,
        val gainAttackSec: Double,
        val gainReleaseSec: Double,
    ) {
        val startupGainLinear: Float = dbToLinear(startupGainDb)
        val maxGainLinear: Float = dbToLinear(maxGainDb)
        val minGainLinear: Float = dbToLinear(minGainDb)

        companion object {
            fun of(level: Settings.AudioBalance): Params = when (level) {
                Settings.AudioBalance.OFF -> Params(0.0, 0.0, 0.0, -120.0, 0.0, 0.0, 0.05, 1.2)
                Settings.AudioBalance.LOW -> Params(-0.5, 1.0, -8.0, -55.0, 2.5, 10.0, 3.0, 8.0)
                Settings.AudioBalance.MEDIUM -> Params(-1.5, 2.0, -12.0, -58.0, 2.0, 8.0, 2.0, 8.0)
                Settings.AudioBalance.HIGH -> Params(-3.0, 2.5, -16.0, -60.0, 1.5, 6.0, 1.5, 8.0)
            }
        }
    }
}

/**
 * 注入 [AudioBalanceProcessor] 的 RenderersFactory。
 *
 * 对应原版 po5.buildAudioSink（src/app/p000/po5.java:23-30）：Media3 1.11 还没有
 * `AudioProcessorChain`，用等价的 `DefaultAudioSink.Builder.setAudioProcessors`。
 */
class AudioBalanceRenderersFactory(context: Context) : DefaultRenderersFactory(context) {

    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioTrackPlaybackParams: Boolean,
    ): AudioSink = DefaultAudioSink.Builder(context)
        .setEnableFloatOutput(enableFloatOutput)
        .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
        .setAudioProcessors(arrayOf(AudioBalanceProcessor()))
        .build()
}
