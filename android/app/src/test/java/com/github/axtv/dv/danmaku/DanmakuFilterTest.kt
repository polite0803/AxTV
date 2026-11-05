package com.github.axtv.dv.danmaku

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 屏蔽词 / 表情过滤单测（纯 JVM）。 */
class DanmakuFilterTest {

    private fun comment(text: String, hasEmoji: Boolean = false) =
        DanmakuComment(id = "c", timeMs = 0L, text = text, hasEmoji = hasEmoji)

    @Test
    fun blockWordsMatchCaseInsensitively() {
        val config = DanmakuConfig(blockEnabled = true, blockWords = listOf("广告", "spam"))

        assertFalse(DanmakuFilter.accepts(comment("这是广告"), config))
        assertFalse(DanmakuFilter.accepts(comment("SPAM content"), config))
        assertTrue(DanmakuFilter.accepts(comment("正常内容"), config))
    }

    @Test
    fun blockWordsAreIgnoredWhenSwitchIsOff() {
        val config = DanmakuConfig(blockEnabled = false, blockWords = listOf("广告"))

        assertTrue(DanmakuFilter.accepts(comment("这是广告"), config))
    }

    @Test
    fun blankTextIsNeverShown() {
        assertFalse(DanmakuFilter.accepts(comment("   "), DanmakuConfig()))
    }

    @Test
    fun emojiEntriesFollowEmojiSwitch() {
        assertFalse(DanmakuFilter.accepts(comment("😀", hasEmoji = true), DanmakuConfig(emojiEnabled = false)))
        assertTrue(DanmakuFilter.accepts(comment("😀", hasEmoji = true), DanmakuConfig(emojiEnabled = true)))
        assertTrue(DanmakuFilter.accepts(comment("普通文本"), DanmakuConfig(emojiEnabled = false)))
    }

    @Test
    fun configNormalizationClampsRangesAndTrimsWords() {
        val normalized = DanmakuConfig(
            opacity = 5f,
            areaPercent = 0,
            verticalSpacing = 0.1f,
            horizontalSpacing = 9f,
            fontScale = 0f,
            speedScale = 9f,
            blockWords = listOf("  广告  ", "", "spam", "广告"),
        ).normalized()

        assertTrue(normalized.alpha in 0..255)
        assertTrue("不透明度上限夹到 255", normalized.alpha == 255)
        assertTrue("显示区域下限夹到 1", normalized.areaPercent == 1)
        assertTrue("行距下限夹到 0.5", normalized.verticalSpacing == 0.5f)
        assertTrue("列距上限夹到 2.0", normalized.horizontalSpacing == 2.0f)
        assertTrue("字号非法值回落 1.0", normalized.fontScale == 1.0f)
        assertTrue("速度上限夹到 2.0", normalized.speedScale == 2.0f)
        assertTrue("屏蔽词 trim + 去空 + 保序去重", normalized.blockWords == listOf("广告", "spam"))
    }
}
