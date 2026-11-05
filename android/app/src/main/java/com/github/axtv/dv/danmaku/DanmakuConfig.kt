/*
 * 弹幕几何参数与设置快照（纯 JVM，不依赖 Android）。
 *
 * 常量与公式逐项取自 refactor/live/axtv/live/DanmakuStyle.java：
 * - 16sp / 30dp / 8dp / 4×padding / 8000ms / clamp(0.5..2.0)：21-33、41-71、128-140
 * - 轨道数 max(1, (height × displayArea) / lineHeight)：105-109
 * - 速度基准宽度（窗口宽度）→ px/ms：99-103
 */
package com.github.axtv.dv.danmaku

/** 像素密度换算；Android 侧由 View 的 DisplayMetrics 提供，单测可注入 1:1 假实现。 */
interface DanmakuDensity {
    fun sp(value: Float): Float
    fun dp(value: Float): Float
}

/** 文本宽度测量；Android 侧用 Paint.measureText，单测可注入按字数估算的假实现。 */
fun interface DanmakuTextMeasurer {
    fun measure(text: String, fontSizePx: Float): Float
}

/** 原版几何常量（DanmakuStyle.java）。 */
object DanmakuMetrics {
    /** 原 oh4.m8585j(16)。 */
    const val BASE_TEXT_SIZE_SP = 16f

    /** 原 iu0.mo391P(30f)。 */
    const val LINE_HEIGHT_DP = 30f

    /** 原 iu0.mo391P(8f)。 */
    const val PADDING_DP = 8f

    /** 原 4.0f × padding。 */
    const val HORIZONTAL_GAP_FACTOR = 4f

    /** 原 8000.0f / speed。 */
    const val BASE_DURATION_MS = 8000f

    /** 原 paint.setStrokeWidth(0.12f × fontSize)。 */
    const val STROKE_WIDTH_RATIO = 0.12f

    /** 原 (long) 2.0f × textWidth 的系数。 */
    const val TRAVEL_WIDTH_FACTOR = 2f

    /** 原清理已离场弹幕的窗口 now - 1000。 */
    const val ACTIVE_RETENTION_MS = 1000L

    /** 原绘制前裁剪余量 ±500ms。 */
    const val CULL_MARGIN_MS = 500L

    /**
     * 位置跳变阈值（毫秒）：超过则视为 seek，重建轨道。
     * 推断值，用于覆盖播放器正常的帧间抖动。
     */
    const val SEEK_JUMP_MS = 1500L
}

/**
 * 弹幕设置快照。
 *
 * 默认值对齐 refactor/settings/axtv/settings/Settings.java 的默认值：
 * - 开关 video_danmaku_enabled 默认 true（786-790）
 * - 不透明度 1.0（826-833）；显示区域 100（835-849）
 * - 行距 1.0、列距 1.0（851-867）；字号/速度倍率 1.0（869-900）
 * - 屏蔽总开关 false（917-924）；过滤包 true（991-998）
 *
 * 注意：直播侧 LiveDanmakuSettings.defaults()（refactor/live/axtv/live/LiveDanmakuSettings.java:104-107）
 * 的不透明度默认 0.8、显示区域 50，与点播 Settings 默认值不同，本快照取点播口径。
 */
data class DanmakuConfig(
    val enabled: Boolean = true,
    val opacity: Float = 1f,
    val areaPercent: Int = 100,
    val verticalSpacing: Float = 1f,
    val horizontalSpacing: Float = 1f,
    val fontScale: Float = 1f,
    val speedScale: Float = 1f,
    val blockEnabled: Boolean = false,
    val blockWords: List<String> = emptyList(),
    val filterPackage: Boolean = true,
    val emojiEnabled: Boolean = true,
    /** 顶部安全区（px），避免压住标题栏；推断值，默认 0。 */
    val safeTopInsetPx: Float = 0f,
    /** 底部安全区（px），避免压住进度条；推断值，默认 0。 */
    val safeBottomInsetPx: Float = 0f,
) {
    /** 不透明度 → 0..255，原 ut0.m10995r((int)(255 × opacity), 0, 255)。 */
    val alpha: Int
        get() = (255f * opacity).toInt().coerceIn(0, 255)

    /** 按原版夹取区间归一化；非法值回落到默认。 */
    fun normalized(): DanmakuConfig = copy(
        opacity = opacity.coerceIn(0.01f, 1f),
        areaPercent = areaPercent.coerceIn(1, 100),
        verticalSpacing = verticalSpacing.coerceIn(0.5f, 2f),
        horizontalSpacing = horizontalSpacing.coerceIn(0.5f, 2f),
        fontScale = if (fontScale > 0f) fontScale else 1f,
        speedScale = speedScale.coerceIn(0.5f, 2f),
        blockWords = blockWords.map { it.trim() }.filter { it.isNotEmpty() }.distinct(),
        safeTopInsetPx = safeTopInsetPx.coerceAtLeast(0f),
        safeBottomInsetPx = safeBottomInsetPx.coerceAtLeast(0f),
    )
}

/**
 * 一帧的布局参数（对应原 to0，DanmakuStyle.java:91-110）。
 *
 * @param widthPx  画布宽（也是速度基准宽度，原实现取窗口宽度）
 * @param heightPx 画布高
 */
class DanmakuGeometry(
    val widthPx: Float,
    val heightPx: Float,
    val config: DanmakuConfig,
    density: DanmakuDensity,
) {
    /** 原 fontSize = sp(16) × fontSizeScale。 */
    val fontSizePx: Float = density.sp(DanmakuMetrics.BASE_TEXT_SIZE_SP) * config.fontScale

    /** 原 lineHeight = clamp(verticalSpacing) × dp(30) × fontSizeScale。 */
    val lineHeightPx: Float = (config.verticalSpacing * density.dp(DanmakuMetrics.LINE_HEIGHT_DP) * config.fontScale)
        .coerceAtLeast(1f)

    /** 原 padding = dp(8)。 */
    val paddingPx: Float = density.dp(DanmakuMetrics.PADDING_DP)

    /** 原 horizontalGap = clamp(horizontalSpacing) × 4 × padding。 */
    val horizontalGapPx: Float = config.horizontalSpacing * DanmakuMetrics.HORIZONTAL_GAP_FACTOR * paddingPx

    /** 原 durationMs = 8000 / clamp(speed)。 */
    val durationMs: Float = DanmakuMetrics.BASE_DURATION_MS / config.speedScale

    /** 原 pxPerMs = speedReferenceWidth / durationMs。 */
    val pixelsPerMs: Float = widthPx / durationMs

    /** 原轨道数 = max(1, (int)((height × displayArea) / lineHeight))。 */
    val laneCount: Int = maxOf(1, ((heightPx * (config.areaPercent / 100f)) / lineHeightPx).toInt())

    /** 滚动弹幕基线：原 track × lineHeight + padding + fontSize。 */
    fun scrollBaselineY(lane: Int, fontSizePx: Float): Float =
        config.safeTopInsetPx + lane * lineHeightPx + paddingPx + fontSizePx
}
