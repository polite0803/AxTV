package com.github.axtv.dv.danmaku

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 类型映射取证回归单测（纯 JVM）。
 *
 * 取证结论（2026-11-05，见 DanmakuMapper.kt 头注释）：
 * - danmaku_type 在 src/ 反编译产物中只有定义、无任何调用点，取值语义未知；
 * - danmaku_type_bits 无读取点，不参与分类；
 * - 原版渲染链把所有条目按滚动处理，映射端对任意类型值一律回落 SCROLL。
 */
class DanmakuMapperTest {

    /** 位置传参构造（Java record 无具名参数）。 */
    private fun item(danmakuType: Int, danmakuTypeBits: Int = 0): axtv.model.DanmakuItem =
        axtv.model.DanmakuItem(
            "d1", "i1", "u1", 5000L, "hi", 1, 0, 0.0,
            danmakuType, 0, false, false, false,
            danmakuTypeBits, false, 0, false,
        )

    @Test
    fun typeZeroMapsToScroll() {
        assertEquals(DanmakuType.SCROLL, item(0).toDanmakuComment(0).type)
    }

    @Test
    fun unknownTypeValuesFallBackToScroll() {
        // 原版从不消费 danmaku_type，任何非零值语义未知 → 一律回落滚动。
        for (value in intArrayOf(1, 2, 3, 99, -1)) {
            assertEquals("danmaku_type=$value", DanmakuType.SCROLL, item(value).toDanmakuComment(0).type)
        }
    }

    @Test
    fun danmakuTypeBitsDoesNotParticipateInClassification() {
        // danmaku_type_bits 无读取点（证实不参与），置位与否不影响映射结果。
        for (bits in intArrayOf(0, 1, 2, 255)) {
            assertEquals("bits=$bits", DanmakuType.SCROLL, item(0, bits).toDanmakuComment(0).type)
            assertEquals("bits=$bits", DanmakuType.SCROLL, item(2, bits).toDanmakuComment(0).type)
        }
    }

    @Test
    fun scrollMappingKeepsTimelineFieldsIntact() {
        val comment = item(0).toDanmakuComment(0)
        assertEquals("d1", comment.id)
        assertEquals(5000L, comment.timeMs)
        assertEquals("hi", comment.text)
        assertEquals(false, comment.isLocal)
    }
}
