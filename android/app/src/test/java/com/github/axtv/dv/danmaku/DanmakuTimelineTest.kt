package com.github.axtv.dv.danmaku

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 时间轴映射 / 运动学 / 暂停 / seek 单测（纯 JVM）。
 *
 * 视口 1000×120，密度与字号 1:1（sp/dp 均为 1），默认设置下：
 * fontSize=16，lineHeight=30，padding=8，horizontalGap=32，
 * duration=8000ms，pxPerMs=0.125，laneCount=4。
 */
class DanmakuTimelineTest {

    private val density = object : DanmakuDensity {
        override fun sp(value: Float): Float = value

        override fun dp(value: Float): Float = value
    }

    /** 按字数估算：宽 = 字符数 × 字号 × 0.5。 */
    private val measurer = DanmakuTextMeasurer { text, fontSizePx -> text.length * fontSizePx * 0.5f }

    private fun newEngine(config: DanmakuConfig = DanmakuConfig()) =
        DanmakuEngine(config, density, measurer)

    private fun scrollComment(timeMs: Long, text: String = "hi", id: String = "c") =
        DanmakuComment(id = id, timeMs = timeMs, text = text)

    @Test
    fun scrollPositionStartsAtRightEdgeAndMovesLeft() {
        val engine = newEngine()
        engine.setItems(listOf(scrollComment(0L)))

        val at0 = engine.sync(0L, true, 1000f, 120f)
        assertEquals(1, at0.size)
        // x = textWidth + canvasWidth = 16 + 1000
        assertEquals(1016f, at0[0].x, 0.01f)
        assertEquals(16f, at0[0].fontSizePx, 0.01f)
        assertEquals(255, at0[0].alpha)

        // 按真实播放节奏逐帧推进：单次跳变超过 SEEK_JUMP_MS(1500) 会被判为 seek
        // 并重建时间轴（见 seekBackwardRebuildsTimelineAndReplaysEntries），4s 单步不会连续播放。
        // 4s × 0.125 px/ms = 500px
        engine.sync(1000L, true, 1000f, 120f)
        engine.sync(2000L, true, 1000f, 120f)
        engine.sync(3000L, true, 1000f, 120f)
        val at4s = engine.sync(4000L, true, 1000f, 120f)
        assertEquals(516f, at4s[0].x, 0.01f)
    }

    @Test
    fun pauseFreezesDanmakuPosition() {
        val engine = newEngine()
        engine.setItems(listOf(scrollComment(0L)))

        engine.sync(0L, true, 1000f, 120f)
        val at1s = engine.sync(1000L, true, 1000f, 120f)
        assertEquals(891f, at1s[0].x, 0.01f)

        // 暂停：播放器位置不变 → 时间轴冻结
        val paused = engine.sync(1000L, false, 1000f, 120f)
        assertEquals(891f, paused[0].x, 0.01f)
        assertEquals(1000L, engine.currentPositionMs())

        // 恢复后继续按时间轴推进
        val resumed = engine.sync(1500L, true, 1000f, 120f)
        assertEquals(828.5f, resumed[0].x, 0.01f)
    }

    @Test
    fun seekBackwardRebuildsTimelineAndReplaysEntries() {
        val engine = newEngine()
        engine.setItems(listOf(scrollComment(0L)))

        // 首帧就在 3s：0s 的条目已超出 1s 追帧窗口，不上屏
        val at3s = engine.sync(3000L, true, 1000f, 120f)
        assertTrue(at3s.isEmpty())

        // seek 回 0：重建时间轴，条目重新从右边缘进入
        val afterSeek = engine.sync(0L, true, 1000f, 120f)
        assertEquals(1, afterSeek.size)
        assertEquals(1016f, afterSeek[0].x, 0.01f)
    }

    @Test
    fun explicitSeekToIsHonoredWhenPaused() {
        val engine = newEngine()
        engine.setItems(listOf(scrollComment(0L)))
        engine.sync(0L, true, 1000f, 120f)

        engine.seekTo(5000L)
        val frame = engine.sync(5000L, false, 1000f, 120f)
        assertTrue(frame.isEmpty())
        assertEquals(5000L, engine.currentPositionMs())
    }

    @Test
    fun areaPercentLimitsLaneCountAndDropsOverflow() {
        val engine = newEngine(DanmakuConfig(areaPercent = 10))
        engine.setItems(
            (0 until 5).map { scrollComment(0L, text = "a", id = "c$it") },
        )

        // 显示区域 10% → 120×0.1/30 = 0 → 至少 1 条轨道
        val frame = engine.sync(0L, true, 1000f, 120f)
        assertEquals(1, frame.size)
        assertEquals(1, engine.activeCount())
    }

    @Test
    fun opacityMapsToAlpha() {
        val engine = newEngine(DanmakuConfig(opacity = 0.5f))
        engine.setItems(listOf(scrollComment(0L)))
        val frame = engine.sync(0L, true, 1000f, 120f)
        assertEquals(127, frame[0].alpha)
    }

    @Test
    fun speedScaleChangesTravelDistance() {
        val slow = newEngine(DanmakuConfig(speedScale = 0.5f))
        val fast = newEngine(DanmakuConfig(speedScale = 2f))
        val item = scrollComment(0L)
        slow.setItems(listOf(item))
        fast.setItems(listOf(item))

        slow.sync(0L, true, 1000f, 120f)
        fast.sync(0L, true, 1000f, 120f)
        val slowAt1s = slow.sync(1000L, true, 1000f, 120f)[0].x
        val fastAt1s = fast.sync(1000L, true, 1000f, 120f)[0].x

        // 速度 0.5 → pxPerMs 0.0625；速度 2 → pxPerMs 0.25
        assertEquals(953.5f, slowAt1s, 0.01f)
        assertEquals(766f, fastAt1s, 0.01f)
    }

    @Test
    fun topDanmakuFallsBackToScrollLane() {
        // 取证结论：原版渲染链无 TOP 类型消费点（danmaku_type 无调用点），
        // 固定弹幕停留时长无证据值 → TOP 输入回落滚动，从右缘进入并占滚动轨道。
        val engine = newEngine()
        engine.setItems(
            listOf(
                DanmakuComment(id = "t", timeMs = 0L, text = "hi", type = DanmakuType.TOP),
                scrollComment(0L, id = "s"),
            ),
        )

        val frame = engine.sync(0L, true, 1000f, 120f)
        assertEquals(2, frame.size)
        // 两条都在滚动基线上（0×30 + 8 + 16 = 24），x 从右缘起算且互不重叠：
        // TOP 条先入轨 lane 0（x = 16 + 1000），SCROLL 条进 lane 1。
        assertEquals(24f, frame.first { it.id == "t" }.baselineY, 0.01f)
        assertEquals(1016f, frame.first { it.id == "t" }.x, 0.01f)
        assertEquals(24f + 30f, frame.first { it.id == "s" }.baselineY, 0.01f)
        // 渲染条保留原类型标记（供 UI 识别），但坐标按滚动运动学计算。
        assertEquals(DanmakuType.TOP, frame.first { it.id == "t" }.type)
    }

    @Test
    fun bottomDanmakuFallsBackToScrollLane() {
        // 同上：BOTTOM 输入无固定弹幕证据，回落滚动轨道，不做底部居中停留。
        val engine = newEngine()
        engine.setItems(
            listOf(DanmakuComment(id = "b", timeMs = 0L, text = "hi", type = DanmakuType.BOTTOM)),
        )
        val frame = engine.sync(0L, true, 1000f, 120f)

        assertEquals(1, frame.size)
        assertEquals(24f, frame[0].baselineY, 0.01f)
        assertEquals(1016f, frame[0].x, 0.01f)
        assertEquals(DanmakuType.BOTTOM, frame[0].type)
    }

    @Test
    fun disabledConfigShowsNothing() {
        val engine = newEngine(DanmakuConfig(enabled = false))
        engine.setItems(listOf(scrollComment(0L)))
        assertTrue(engine.sync(0L, true, 1000f, 120f).isEmpty())

        engine.updateConfig(DanmakuConfig(enabled = true))
        val frame = engine.sync(0L, true, 1000f, 120f)
        assertEquals(1, frame.size)
    }

    @Test
    fun entriesOlderThanCatchUpWindowAreNotReplayedOnFirstFrame() {
        val engine = newEngine()
        engine.setItems(listOf(scrollComment(0L)))
        // 首帧 5s：0s 的条目已超过 1s 追帧窗口
        assertTrue(engine.sync(5000L, true, 1000f, 120f).isEmpty())
    }

    @Test
    fun localSendShowsImmediatelyWithoutBackend() {
        val engine = newEngine()
        engine.sync(0L, true, 1000f, 120f)

        assertTrue(engine.submitLocal("本地弹幕"))
        val frame = engine.sync(0L, false, 1000f, 120f)

        assertEquals(1, frame.size)
        assertTrue(frame[0].id.startsWith("local-"))
        // “本地弹幕”4 个字：宽 = 4 × 16 × 0.5 = 32；x = 32 + 1000
        assertEquals(1032f, frame[0].x, 0.01f)
    }

    @Test
    fun blankLocalSendIsRejected() {
        val engine = newEngine()
        assertEquals(false, engine.submitLocal("   "))
    }
}
