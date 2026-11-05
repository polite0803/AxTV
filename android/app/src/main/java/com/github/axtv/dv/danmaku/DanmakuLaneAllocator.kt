/*
 * 轨道分配（纯 JVM，可单测）。
 *
 * 算法来源：refactor/live/axtv/live/DanmakuStyle.java:249-279（原 wo0）。
 * - 轨道释放时间 release = entry + (2×textWidth + horizontalGap) / pxPerMs
 * - 完全离场时间 exit = entry + (2×textWidth + canvasWidth) / pxPerMs
 * - 选轨策略：在所有「已空闲」的轨道里选等待时间最短（gap 最小）的一条；没有空闲轨道则本条丢弃。
 *
 * 复杂度：单条 O(L)，L 为轨道数（由显示区域/行高决定，一般 ≤ 20）；
 * 一次批量调度 n 条为 O(n·L)。内存 O(L)。
 */
package com.github.axtv.dv.danmaku

/** 一次成功分配的轨道信息。 */
data class LanePlacement(
    val lane: Int,
    val releaseTimeMs: Long,
    val exitTimeMs: Long,
)

/**
 * 滚动弹幕轨道分配器（原 wo0）。
 *
 * 非线程安全；由 [DanmakuEngine] 在渲染线程独占调用。
 */
class ScrollLaneAllocator(private val laneCount: Int) {

    private val laneFreeMs = LongArray(laneCount)

    /** 清空占用表（seek / 尺寸变化 / 配置变化时调用）。 */
    fun reset() {
        laneFreeMs.fill(0L)
    }

    /** 当前轨道空闲时间（测试与诊断用）。 */
    fun freeTimeMs(lane: Int): Long = laneFreeMs[lane]

    /**
     * 为一条滚动弹幕分配轨道。
     *
     * @param entryTimeMs  入场时间（原 message.timestamp()）
     * @param textWidthPx  文字宽度
     * @param pixelsPerMs  速度（px/ms）
     * @param horizontalGapPx 水平间距
     * @param canvasWidthPx 画布宽（用于离场时间）
     * @return 轨道信息；全部轨道仍被占用时返回 null（本条丢弃，与原实现一致）
     */
    fun assign(
        entryTimeMs: Long,
        textWidthPx: Float,
        pixelsPerMs: Float,
        horizontalGapPx: Float,
        canvasWidthPx: Float,
    ): LanePlacement? {
        if (laneCount <= 0 || pixelsPerMs <= 0f) {
            return null
        }
        var bestLane = -1
        var bestGap = Long.MAX_VALUE
        for (lane in 0 until laneCount) {
            val free = laneFreeMs[lane]
            if (free <= entryTimeMs) {
                val gap = entryTimeMs - free
                if (gap < bestGap) {
                    bestGap = gap
                    bestLane = lane
                }
            }
        }
        if (bestLane < 0) {
            return null
        }
        val release = entryTimeMs + (((DanmakuMetrics.TRAVEL_WIDTH_FACTOR * textWidthPx) + horizontalGapPx) / pixelsPerMs).toLong()
        val exit = entryTimeMs + (((DanmakuMetrics.TRAVEL_WIDTH_FACTOR * textWidthPx) + canvasWidthPx) / pixelsPerMs).toLong()
        laneFreeMs[bestLane] = release
        return LanePlacement(bestLane, release, exit)
    }
}
