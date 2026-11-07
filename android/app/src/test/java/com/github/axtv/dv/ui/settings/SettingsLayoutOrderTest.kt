package com.github.axtv.dv.ui.settings

import axtv.ui.SettingsPageSpec
import org.junit.Assert.assertEquals
import org.junit.Test

/** 重排只决定显示顺序：UI 顺序必须恰好覆盖规格里除已删/已单独渲染项外的全部控件。 */
class SettingsLayoutOrderTest {

    private val specKeys = (SettingsPageSpec.MAIN_PAGE + SettingsPageSpec.DANMAKU_STYLE_PANEL)
        .map { it.key() }
        .filter { it != "about_telegram" && it != "settings_title" }
        .toSet()

    @Test
    fun uiOrderCoversEveryControlExactlyOnce() {
        val orderedControls = SETTINGS_UI_ORDER.filter { it in specKeys }
        assertEquals("规格应剩 62 主项 + 15 弹幕项", 77, specKeys.size)
        assertEquals("UI 顺序不得重复控件", orderedControls.size, orderedControls.toSet().size)
        assertEquals("UI 顺序必须覆盖全部控件", specKeys, orderedControls.toSet())
    }

    @Test
    fun syntheticHeadersAreNotSpecControls() {
        assertEquals(
            "合成标题只允许 playback_header",
            setOf(PLAYBACK_HEADER),
            SETTINGS_UI_ORDER.toSet() - specKeys,
        )
    }
}
