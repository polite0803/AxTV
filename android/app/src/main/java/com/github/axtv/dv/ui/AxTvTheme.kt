/*
 * 目标栈基线主题（绘制层，见 refactor/ui/NOTES.md「绘制交给 Android 侧」）。
 * 原版色值来自 k80（Material3 ColorScheme，映射见 PlayerUiPalette.java:1-47）。
 *
 * 2026-11 接线：颜色不再是写死的占位表，而是 AxAppearance 快照状态驱动的调色板——
 * theme_mode / color_contrast / color_style / color_source / color_transition / app_font_scale
 * 全部在下面的 AxTvTheme 里消费，任何写入方（SettingsBinder）通知后全站即时重组，无需重启。
 */
package com.github.axtv.dv.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import androidx.tv.material3.lightColorScheme
import axtv.settings.Settings
import axtv.settings.SettingsTypes
import com.github.axtv.dv.adapter.AppRuntime
import com.github.axtv.dv.ui.appearance.AxAppearance
import com.github.axtv.dv.ui.appearance.AxPalette

internal object AxTvColors {
    /**
     * 为什么改成属性：全站 300+ 处 `AxTvColors.X` 引用一行不用改就能随调色板重组——
     * get() 在组合期读 AxAppearance.currentPalette 的快照状态，值一变即触发重绘。
     */
    private val palette: AxPalette get() = AxAppearance.currentPalette.value
    val Bg: Color get() = palette.bg
    val Surface: Color get() = palette.surface
    val SurfaceHigh: Color get() = palette.surfaceHigh
    val TextPrimary: Color get() = palette.textPrimary
    val TextSecondary: Color get() = palette.textSecondary
    val Accent: Color get() = palette.accent
    val AccentWarm: Color get() = palette.accentWarm
    /** 焦点框色：与强调色同色相、保证最低对比度（见 AxPaletteEngine.focusColor）。 */
    val Focus: Color get() = palette.focus
    val Divider: Color get() = palette.divider
}

/**
 * app_font_scale 的消费点：只在根上换一份 Density 的 fontScale 倍率。
 * 为什么这样做：既有的字面量 `.sp` 全部自动放大，图标与间距（dp）不受影响，
 * 不用重写几百处 fontSize；倍率 = 系统 fontScale × 设置档位（1.0/1.15/1.3/1.5）。
 */
private class FontScaleDensity(private val base: Density, override val fontScale: Float) : Density {
    override val density: Float get() = base.density
}

@Composable
fun AxTvTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val settings = AppRuntime.settings
    // version 与 seedArgb 是快照状态：这里读一次即订阅，SettingsBinder/取色器一写就整棵树重算。
    val version = AxAppearance.version.intValue
    val seed = AxAppearance.seedArgb.intValue
    // LocalConfiguration 随系统 uiMode 更新 —— theme_mode=自动 的实时跟随就靠它。
    val uiMode = LocalConfiguration.current.uiMode
    val target = rememberTarget(version, seed, uiMode)
    // color_transition：自动=逐色插值动画；关闭=立即替换。
    LaunchedEffect(target) {
        AxAppearance.animateTo(
            target = target,
            animate = settings?.colorTransition() == SettingsTypes.ColorTransition.AUTO,
        )
    }
    // color_source=设备取色：来源或外观设置变化后重读系统壁纸色（不可用时内部回落品牌色）。
    LaunchedEffect(settings?.colorSource(), version) {
        if (settings?.colorSource() == SettingsTypes.ColorSource.DEVICE) {
            AxAppearance.sampleDeviceSeed(context)
        }
    }

    val baseDensity = LocalDensity.current
    val scaledDensity = FontScaleDensity(
        base = baseDensity,
        fontScale = baseDensity.fontScale * (settings?.fontScale() ?: 1f),
    )
    val palette = AxAppearance.currentPalette.value
    CompositionLocalProvider(LocalDensity provides scaledDensity) {
        MaterialTheme(
            // TV Material3 的 colorScheme 只提供框架默认控件色；自绘界面统一走 AxTvColors。
            colorScheme = if (palette.dark) darkColorScheme() else lightColorScheme(),
            content = content,
        )
    }
}

/** 目标调色板：key 里三个输入都是「写外观必须重算」的维度，其余输入经 version 间接覆盖。 */
@Composable
private fun rememberTarget(version: Int, seed: Int, uiMode: Int): AxPalette =
    remember(version, seed, uiMode) { AxAppearance.targetPalette(uiMode) }

/** 统一的「自绘焦点框」：TV 端不用 Leanback 的焦点盒，由 Compose 自绘。 */
internal fun Modifier.focusRing(focused: Boolean, shape: Shape = RoundedCornerShape(12.dp)): Modifier =
    border(
        // 3dp：电视观看距离下 2dp 的高对比描边仍然偏细。
        width = if (focused) 3.dp else 1.dp,
        color = if (focused) AxTvColors.Focus else AxTvColors.Divider,
        shape = shape,
    )
