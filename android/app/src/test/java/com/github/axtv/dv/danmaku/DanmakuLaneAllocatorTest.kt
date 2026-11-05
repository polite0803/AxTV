package com.github.axtv.dv.danmaku

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 轨道分配单测（纯 JVM）。
 *
 * 参数与公式对齐 refactor/live/axtv/live/DanmakuStyle.java:249-279：
 * release = entry + (2×textWidth + horizontalGap) / pxPerMs
 * exit    = entry + (2×textWidth + canvasWidth) / pxPerMs
 */
class DanmakuLaneAllocatorTest {

    private val pxPerMs = 0.125f
    private val gap = 32f
    private val canvasWidth = 1000f

    @Test
    fun firstMessageTakesFirstLaneAndOriginalReleaseFormula() {
        val allocator = ScrollLaneAllocator(3)
        val placed = allocator.assign(0L, 16f, pxPerMs, gap, canvasWidth)!!

        assertEquals(0, placed.lane)
        // (2×16 + 32) / 0.125 = 512
        assertEquals(512L, placed.releaseTimeMs)
        // (2×16 + 1000) / 0.125 = 8256
        assertEquals(8256L, placed.exitTimeMs)
    }

    @Test
    fun simultaneousMessagesUseDifferentLanes() {
        val allocator = ScrollLaneAllocator(3)
        val first = allocator.assign(0L, 16f, pxPerMs, gap, canvasWidth)!!
        val second = allocator.assign(0L, 16f, pxPerMs, gap, canvasWidth)!!

        assertEquals(0, first.lane)
        assertEquals(1, second.lane)
    }

    @Test
    fun choosesLaneWithShortestWait() {
        val allocator = ScrollLaneAllocator(3)
        allocator.assign(0L, 16f, pxPerMs, gap, canvasWidth) // lane 0，free=512
        allocator.assign(100L, 16f, pxPerMs, gap, canvasWidth) // lane 1，free=612
        allocator.assign(400L, 200f, pxPerMs, gap, canvasWidth) // lane 2，free=3856

        // t=600：lane0 等待 88ms，lane1 等待 12ms 但未空闲（612>600），lane2 未空闲。
        val placed = allocator.assign(600L, 16f, pxPerMs, gap, canvasWidth)!!
        assertEquals(0, placed.lane)
        assertEquals(1112L, placed.releaseTimeMs)
    }

    @Test
    fun laneWithSmallestGapWinsWhenSeveralAreFree() {
        val allocator = ScrollLaneAllocator(3)
        allocator.assign(0L, 16f, pxPerMs, gap, canvasWidth) // lane 0 free 512
        allocator.assign(300L, 16f, pxPerMs, gap, canvasWidth) // lane 1 free 812

        val placed = allocator.assign(600L, 16f, pxPerMs, gap, canvasWidth)!!
        // lane 0 free=512（等待 88）比 lane 1 free=812 更早空闲 → 选 lane 0
        assertEquals(0, placed.lane)
    }

    @Test
    fun longTextReservesLaneLonger() {
        val allocator = ScrollLaneAllocator(1)
        val shortText = allocator.assign(0L, 16f, pxPerMs, gap, canvasWidth)!!
        allocator.reset()
        val longText = allocator.assign(0L, 500f, pxPerMs, gap, canvasWidth)!!

        assertEquals(512L, shortText.releaseTimeMs)
        assertEquals(8256L, longText.releaseTimeMs)
    }

    @Test
    fun dropsMessageWhenAllLanesBusy() {
        val allocator = ScrollLaneAllocator(3)
        repeat(3) {
            allocator.assign(0L, 500f, pxPerMs, gap, canvasWidth)
        }

        // 三条轨道的 release 都是 8256，t=100 时全部不可用。
        assertNull(allocator.assign(100L, 16f, pxPerMs, gap, canvasWidth))
    }

    @Test
    fun resetMakesAllLanesFreeAgain() {
        val allocator = ScrollLaneAllocator(2)
        allocator.assign(0L, 500f, pxPerMs, gap, canvasWidth)
        assertEquals(8256L, allocator.freeTimeMs(0))

        allocator.reset()
        assertEquals(0L, allocator.freeTimeMs(0))
        assertEquals(0L, allocator.freeTimeMs(1))
    }
}
