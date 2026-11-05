/*
 * 弹幕引擎（纯 JVM，可 JVM 单测）。
 *
 * 输入：视频时间轴位置 + 弹幕条目 + 视图尺寸；
 * 输出：当前帧每条弹幕的屏幕坐标（[DanmakuRender]）。
 *
 * 时间轴映射与运动学：
 * - 滚动 x(t) = (textWidth + canvasWidth) - pxPerMs × max(0, t - entryTime)
 *   （原 DanmakuStyle.java:157-174）
 * - 轨道 release / exit 与选轨见 DanmakuLaneAllocator.kt（原 DanmakuStyle.java:249-279）
 * - 暂停：引擎以播放器位置为唯一时间源；播放器暂停时位置不前进 → 弹幕静止。
 *   原版 refactor/ 未留下点播弹幕暂停行为的实现，这里按时间轴语义实现，可信度「推断」。
 * - seek：位置跳变超过 SEEK_JUMP_MS 或明显回退时重建轨道，避免把旧条目画到错误位置。
 */
package com.github.axtv.dv.danmaku

import kotlin.math.abs

class DanmakuEngine(
    initialConfig: DanmakuConfig = DanmakuConfig(),
    private val density: DanmakuDensity,
    private val textMeasurer: DanmakuTextMeasurer,
) {
    private var config: DanmakuConfig = initialConfig.normalized()

    /** 已按时间升序、按 id 去重的待调度条目。 */
    private var items: List<DanmakuComment> = emptyList()
    private var nextIndex: Int = 0

    private val active = ArrayList<ActiveDanmaku>()
    private val pendingLocal = ArrayList<DanmakuComment>()

    private var geometry: DanmakuGeometry? = null
    private var scrollAllocator: ScrollLaneAllocator? = null

    /** 引擎时间（毫秒）：正常时等于播放器位置；暂停时冻结。 */
    private var clockMs: Long = -1L
    private var lastReportedPositionMs: Long = -1L
    private var lastFrame: List<DanmakuRender> = emptyList()

    /** 当前引擎时间；未开始时为 0。 */
    fun currentPositionMs(): Long = clockMs.coerceAtLeast(0L)

    fun hasActive(): Boolean = active.isNotEmpty()

    fun activeCount(): Int = active.size

    fun lastFrame(): List<DanmakuRender> = lastFrame

    /** 设置本片弹幕。分批追加时不会清掉已在屏条目，只重排未来条目。 */
    @Synchronized
    fun setItems(comments: List<DanmakuComment>) {
        val sorted = comments.distinctBy { it.id }.sortedBy { it.timeMs }
        if (sorted == items) {
            return
        }
        items = sorted
        nextIndex = if (clockMs >= 0L) firstIndexAfter(clockMs) else 0
    }

    @Synchronized
    fun updateConfig(newConfig: DanmakuConfig) {
        val normalized = newConfig.normalized()
        if (normalized == config) {
            return
        }
        config = normalized
        val current = geometry
        if (current != null) {
            geometry = DanmakuGeometry(current.widthPx, current.heightPx, config, density)
            rebuildAllocators()
        }
        if (!config.enabled) {
            active.clear()
            pendingLocal.clear()
            lastFrame = emptyList()
        }
        resetSchedule(clockMs.coerceAtLeast(0L))
    }

    /** 本地发送（占位）：立即入队，下一次 sync 在当前位置上屏。 */
    @Synchronized
    fun submitLocal(
        text: String,
        type: DanmakuType = DanmakuType.SCROLL,
        colorArgb: Int = DanmakuComment.DEFAULT_COLOR_ARGB,
    ): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || !config.enabled) {
            return false
        }
        pendingLocal.add(DanmakuComment.local(trimmed, currentPositionMs(), type, colorArgb))
        return true
    }

    /** 播放器 seek 时主动调用，立即以新位置重建时间轴。 */
    @Synchronized
    fun seekTo(positionMs: Long) {
        val position = positionMs.coerceAtLeast(0L)
        clockMs = position
        lastReportedPositionMs = position
        resetSchedule(position)
        lastFrame = emptyList()
    }

    fun reset() {
        active.clear()
        pendingLocal.clear()
        lastFrame = emptyList()
        clockMs = -1L
        lastReportedPositionMs = -1L
        nextIndex = 0
        rebuildAllocators()
    }

    /**
     * 推进到某一帧并返回该帧的绘制列表。
     *
     * @param positionMs 播放器当前位置
     * @param isPlaying  播放器是否正在播放
     * @param widthPx    视图宽（同时是速度基准宽度）
     * @param heightPx   视图高
     */
    @Synchronized
    fun sync(positionMs: Long, isPlaying: Boolean, widthPx: Float, heightPx: Float): List<DanmakuRender> {
        if (widthPx <= 0f || heightPx <= 0f) {
            lastFrame = emptyList()
            return lastFrame
        }
        val position = positionMs.coerceAtLeast(0L)
        ensureLayout(widthPx, heightPx)

        val firstFrame = clockMs < 0L || lastReportedPositionMs < 0L
        val jumped = !firstFrame && (
            position < clockMs - BACKWARD_TOLERANCE_MS ||
                abs(position - clockMs) > DanmakuMetrics.SEEK_JUMP_MS
            )

        if (firstFrame || jumped) {
            clockMs = position
            resetSchedule(position)
        } else if (isPlaying) {
            clockMs = position
        }
        // isPlaying == false 且位置未跳变：clockMs 保持不变 → 暂停时弹幕静止。
        lastReportedPositionMs = position

        if (!config.enabled) {
            active.clear()
            pendingLocal.clear()
            lastFrame = emptyList()
            return lastFrame
        }

        scheduleDue()
        schedulePendingLocal()
        pruneActive()
        lastFrame = buildFrame()
        return lastFrame
    }

    // ───────────────────────────── 内部实现 ─────────────────────────────

    private fun ensureLayout(widthPx: Float, heightPx: Float) {
        val current = geometry
        if (current != null &&
            current.widthPx == widthPx &&
            current.heightPx == heightPx &&
            current.config == config
        ) {
            return
        }
        geometry = DanmakuGeometry(widthPx, heightPx, config, density)
        rebuildAllocators()
        resetSchedule(clockMs.coerceAtLeast(0L))
    }

    private fun rebuildAllocators() {
        val lanes = geometry?.laneCount ?: 1
        scrollAllocator = ScrollLaneAllocator(lanes)
    }

    private fun resetSchedule(atMs: Long) {
        active.clear()
        pendingLocal.clear()
        scrollAllocator?.reset()
        nextIndex = firstIndexAfter(atMs - DanmakuMetrics.ACTIVE_RETENTION_MS)
    }

    /** 第一条 timeMs > [timeMs] 的下标；没有则返回 size。 */
    private fun firstIndexAfter(timeMs: Long): Int {
        var low = 0
        var high = items.size
        while (low < high) {
            val mid = (low + high) ushr 1
            if (items[mid].timeMs <= timeMs) {
                low = mid + 1
            } else {
                high = mid
            }
        }
        return low
    }

    private fun scheduleDue() {
        val g = geometry ?: return
        val now = clockMs
        while (nextIndex < items.size) {
            val item = items[nextIndex]
            if (item.timeMs > now) {
                break
            }
            nextIndex++
            if (now - item.timeMs > DanmakuMetrics.ACTIVE_RETENTION_MS) {
                continue
            }
            schedule(item, item.timeMs, g)
        }
    }

    private fun schedulePendingLocal() {
        if (pendingLocal.isEmpty()) {
            return
        }
        val g = geometry ?: return
        val entry = clockMs.coerceAtLeast(0L)
        val pending = pendingLocal.toList()
        pendingLocal.clear()
        pending.forEach { schedule(it, entry, g) }
    }

    private fun schedule(comment: DanmakuComment, entryTimeMs: Long, g: DanmakuGeometry) {
        if (!DanmakuFilter.accepts(comment, config)) {
            return
        }
        val localScale = comment.fontSizeScale.takeIf { it > 0f } ?: 1f
        val fontSize = (g.fontSizePx * localScale).coerceAtLeast(1f)
        val textWidth = textMeasurer.measure(comment.text, fontSize).coerceAtLeast(0f)

        // 原版渲染链不区分弹幕类型：danmaku_type / danmaku_type_bits 在 src/ 反编译
        // 产物中只有定义、无任何调用点（wo0.java:139-157、om5.java、i42.java:553-608
        // 均只消费 offset_time / text / status / is_ad / danmaku_id），
        // 也没有固定弹幕停留时长的证据值（唯一时长常量是 i42.java:387 的 8000/speed，
        // 属滚动弹幕）。因此所有类型一律回落滚动（证实），固定轨道逻辑已删除。
        val placement = scrollAllocator?.assign(
            entryTimeMs,
            textWidth,
            g.pixelsPerMs,
            g.horizontalGapPx,
            g.widthPx,
        ) ?: return
        active.add(
            ActiveDanmaku(comment, textWidth, fontSize, placement.lane,
                entryTimeMs, placement.exitTimeMs),
        )
    }

    private fun pruneActive() {
        val now = clockMs
        active.removeAll { item -> now > item.exitTimeMs + DanmakuMetrics.CULL_MARGIN_MS }
    }

    private fun buildFrame(): List<DanmakuRender> {
        val g = geometry ?: return emptyList()
        val now = clockMs
        val frame = ArrayList<DanmakuRender>(active.size)
        for (item in active) {
            val elapsed = now - item.entryTimeMs
            if (elapsed < -DanmakuMetrics.CULL_MARGIN_MS) {
                continue
            }
            val x = (item.textWidthPx + g.widthPx) - g.pixelsPerMs * elapsed.coerceAtLeast(0L).toFloat()
            if (x + item.textWidthPx <= 0f) {
                continue
            }
            frame.add(renderOf(item, x, g.scrollBaselineY(item.lane, item.fontSizePx)))
        }
        return frame
    }

    private fun renderOf(item: ActiveDanmaku, x: Float, baselineY: Float): DanmakuRender = DanmakuRender(
        id = item.comment.id,
        text = item.comment.text,
        x = x,
        baselineY = baselineY,
        textWidthPx = item.textWidthPx,
        fontSizePx = item.fontSizePx,
        alpha = config.alpha,
        colorArgb = item.comment.colorArgb,
        type = item.comment.type,
    )

    private class ActiveDanmaku(
        val comment: DanmakuComment,
        val textWidthPx: Float,
        val fontSizePx: Float,
        val lane: Int,
        val entryTimeMs: Long,
        val exitTimeMs: Long,
    )

    private companion object {
        /** 允许的回退量（毫秒）：小回退不视为 seek。 */
        const val BACKWARD_TOLERANCE_MS = 300L
    }
}
