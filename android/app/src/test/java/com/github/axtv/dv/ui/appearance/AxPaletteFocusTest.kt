package com.github.axtv.dv.ui.appearance

import axtv.settings.SettingsTypes
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 焦点色回归：暗色主题下即使强调色来自「画面取色 / 还原」得到暗色，
 * 焦点框也必须与背景保持足够对比，否则遥控器焦点在暗底上完全看不出来。
 */
class AxPaletteFocusTest {

    private fun contrast(a: Color, b: Color): Float {
        val first = maxOf(a.luminance(), b.luminance()) + 0.05f
        val second = minOf(a.luminance(), b.luminance()) + 0.05f
        return first / second
    }

    @Test
    fun darkFocusContrastsWithBackgroundEvenForDarkAccent() {
        val palette = AxPaletteEngine.compute(
            dark = true,
            contrast = SettingsTypes.ColorContrast.STANDARD,
            style = SettingsTypes.ColorStyle.FIDELITY,
            source = SettingsTypes.ColorSource.CONTENT,
            seedArgb = 0xFF101018.toInt(),
        )
        val ratio = contrast(palette.focus, palette.bg)
        assertTrue("暗色主题焦点对比度不足: $ratio", ratio >= 4.5f)
    }

    @Test
    fun lightFocusContrastsWithBackgroundForBrightAccent() {
        val palette = AxPaletteEngine.compute(
            dark = false,
            contrast = SettingsTypes.ColorContrast.STANDARD,
            style = SettingsTypes.ColorStyle.FIDELITY,
            source = SettingsTypes.ColorSource.CONTENT,
            seedArgb = 0xFF25F4EE.toInt(),
        )
        val ratio = contrast(palette.focus, palette.bg)
        assertTrue("浅色主题焦点对比度不足: $ratio", ratio >= 4.5f)
    }
}
