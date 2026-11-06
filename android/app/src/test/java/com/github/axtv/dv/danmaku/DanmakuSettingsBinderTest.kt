package com.github.axtv.dv.danmaku

import axtv.settings.MapSettingsStore
import axtv.settings.Settings
import axtv.settings.SettingsTypes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Settings → DanmakuConfig 键映射单测。
 *
 * 只使用 refactor/settings 里已存在的键，不新增键名。
 */
class DanmakuSettingsBinderTest {

    private fun newSettings(): Settings =
        Settings(MapSettingsStore(), SettingsTypes.DeviceProfile.of(33, false))

    @Test
    fun defaultsMatchPointSettings() {
        val config = DanmakuSettingsBinder(newSettings()).read()

        assertTrue("video_danmaku_enabled 默认 true", config.enabled)
        assertEquals(1.0f, config.opacity, 0.0001f)
        assertEquals(100, config.areaPercent)
        assertEquals(1.0f, config.verticalSpacing, 0.0001f)
        assertEquals(1.0f, config.horizontalSpacing, 0.0001f)
        assertEquals(1.0f, config.fontScale, 0.0001f)
        assertEquals(1.0f, config.speedScale, 0.0001f)
        assertFalse("danmaku_block_enabled 默认 false", config.blockEnabled)
        assertTrue(config.blockWords.isEmpty())
        assertTrue("danmaku_filter_package 默认 true", config.filterPackage)
        assertTrue("danmaku_emoji_enabled 默认 true", config.emojiEnabled)
    }

    @Test
    fun readsAllExistingDanmakuKeys() {
        val settings = newSettings()
        settings.setVideoDanmakuEnabled(false)
        settings.setDanmakuOpacity(0.4f)
        settings.setDanmakuAreaPercent(60)
        settings.setDanmakuVerticalSpacing(1.5f)
        settings.setDanmakuHorizontalSpacing(0.5f)
        settings.setDanmakuFontScale(2.0f)
        settings.setDanmakuSpeedScale(1.5f)
        settings.setDanmakuBlockEnabled(true)
        settings.saveDanmakuBlockWords(listOf("  广告  ", "", "spam"))
        settings.setDanmakuFilterPackage(false)
        settings.setDanmakuEmojiEnabled(false)

        val config = DanmakuSettingsBinder(settings).read()

        assertFalse(config.enabled)
        assertEquals(0.4f, config.opacity, 0.0001f)
        assertEquals(60, config.areaPercent)
        assertEquals(1.5f, config.verticalSpacing, 0.0001f)
        assertEquals(0.5f, config.horizontalSpacing, 0.0001f)
        assertEquals(2.0f, config.fontScale, 0.0001f)
        assertEquals(1.5f, config.speedScale, 0.0001f)
        assertTrue(config.blockEnabled)
        assertEquals(listOf("广告", "spam"), config.blockWords)
        assertFalse(config.filterPackage)
        assertFalse(config.emojiEnabled)
    }

    @Test
    fun alphaFollowsOpacity() {
        val settings = newSettings()
        settings.setDanmakuOpacity(0.5f)
        assertEquals(127, DanmakuSettingsBinder(settings).read().alpha)
    }
}
