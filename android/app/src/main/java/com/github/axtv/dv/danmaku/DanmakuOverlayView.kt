/*
 * 弹幕渲染层：Android View + Canvas 自绘。
 *
 * 选型理由（与 Compose Canvas 对比）：
 * 1. 与 SurfaceView 叠放：画面层是 Media3 PlayerView（SurfaceView/TextureView）。
 *    SurfaceView 默认在 Window 之下挖洞，同级 Compose 内容跨 SurfaceView 绘制时需要额外的
 *    graphicsLayer/窗口层处理；独立的透明 View 直接叠在 SurfaceView 之上，行为稳定。
 * 2. 每帧开销：弹幕是「高频、低状态」绘制，Canvas + 复用 Paint 可以在 1 次 invalidate 内画完，
 *    不触发 Compose 重组/布局；暂停时立即停帧，不空转。
 * 3. 依赖隔离：View 可以独立挂到 ViewGroup 或 Compose 的 AndroidView 中，
 *    播放器线不需要改 `player 包`、`ui/player 包`。
 *
 * 绘制公式见 DanmakuEngine / DanmakuGeometry；本类只负责：
 * 拉取播放器位置 → engine.sync → Canvas.drawText（先黑色描边，再填充色）。
 */
package com.github.axtv.dv.danmaku

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.Choreographer
import android.view.View

class DanmakuOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs), DanmakuOverlayController {

    private var currentConfig: DanmakuConfig = DanmakuConfig()
    private var source: DanmakuPlaybackSource? = null

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val measurePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val density: DanmakuDensity = object : DanmakuDensity {
        override fun sp(value: Float): Float = value * resources.displayMetrics.scaledDensity

        override fun dp(value: Float): Float = value * resources.displayMetrics.density
    }

    private val measurer = DanmakuTextMeasurer { text, fontSizePx ->
        measurePaint.textSize = fontSizePx
        measurePaint.measureText(text)
    }

    private val engine = DanmakuEngine(currentConfig, density, measurer)

    private var frame: List<DanmakuRender> = emptyList()
    private var attached = false
    private var frameScheduled = false

    private val playbackListener = object : DanmakuPlaybackSource.Listener {
        override fun onPlaybackStateChanged(isPlaying: Boolean) {
            startLoop()
        }

        override fun onSeek(positionMs: Long) {
            engine.seekTo(positionMs)
            startLoop()
        }

        override fun onVideoSizeChanged(widthPx: Int, heightPx: Int) {
            startLoop()
        }
    }

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            onFrame()
        }
    }

    init {
        // 弹幕层不参与焦点导航，避免抢走播放器的按键焦点。
        isFocusable = false
        isFocusableInTouchMode = false
        isClickable = false
        setWillNotDraw(false)
    }

    // ───────────────────────────────── DanmakuOverlayController ─────────────────────────

    override fun attach(source: DanmakuPlaybackSource?) {
        this.source?.removeListener(playbackListener)
        this.source = source
        source?.addListener(playbackListener)
        startLoop()
    }

    override fun detach() {
        source?.removeListener(playbackListener)
        source = null
        stopLoop()
        engine.reset()
        frame = emptyList()
        invalidate()
    }

    override fun setEnabled(enabled: Boolean) {
        setConfig(currentConfig.copy(enabled = enabled))
    }

    override fun setComments(comments: List<DanmakuComment>) {
        engine.setItems(comments)
        startLoop()
    }

    override fun setConfig(config: DanmakuConfig) {
        currentConfig = config.normalized()
        engine.updateConfig(currentConfig)
        startLoop()
    }

    override fun sendLocal(text: String, type: DanmakuType, colorArgb: Int): Boolean {
        val accepted = engine.submitLocal(text, type, colorArgb)
        if (accepted) {
            startLoop()
        }
        return accepted
    }

    override fun seekTo(positionMs: Long) {
        engine.seekTo(positionMs)
        startLoop()
    }

    // ─────────────────────────────── 帧循环 ───────────────────────────────

    private fun startLoop() {
        if (!attached || frameScheduled) {
            return
        }
        frameScheduled = true
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    private fun stopLoop() {
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        frameScheduled = false
    }

    private fun onFrame() {
        frameScheduled = false
        if (!attached) {
            return
        }
        val playback = source
        val position = playback?.positionMs() ?: engine.currentPositionMs()
        val playing = playback?.isPlaying() ?: false
        val widthPx = (playback?.viewportWidthPx()?.takeIf { it > 0 } ?: width).toFloat()
        val heightPx = (playback?.viewportHeightPx()?.takeIf { it > 0 } ?: height).toFloat()
        if (widthPx <= 0f || heightPx <= 0f) {
            // 等待布局；播放中继续轮询，暂停时由 onSizeChanged / seek 事件触发单帧。
            if (playing) {
                startLoop()
            }
            return
        }

        val next = engine.sync(position, playing, widthPx, heightPx)
        if (next != frame) {
            frame = next
            invalidate()
        }
        if (playing) {
            startLoop()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        attached = true
        startLoop()
    }

    override fun onDetachedFromWindow() {
        attached = false
        stopLoop()
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        startLoop()
    }

    // ─────────────────────────────── 绘制 ───────────────────────────────

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (item in frame) {
            val size = item.fontSizePx
            strokePaint.textSize = size
            strokePaint.color = Color.BLACK
            strokePaint.alpha = item.alpha
            strokePaint.strokeWidth = size * DanmakuMetrics.STROKE_WIDTH_RATIO
            canvas.drawText(item.text, item.x, item.baselineY, strokePaint)

            fillPaint.textSize = size
            fillPaint.color = item.colorArgb
            fillPaint.alpha = item.alpha
            canvas.drawText(item.text, item.x, item.baselineY, fillPaint)
        }
    }
}
