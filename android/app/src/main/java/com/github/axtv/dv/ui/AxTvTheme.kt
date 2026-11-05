/*
 * 目标栈基线主题（绘制层，见 refactor/ui/NOTES.md「绘制交给 Android 侧」）。
 * 原版色值来自 k80（Material3 ColorScheme，映射见 PlayerUiPalette.java:1-47），
 * 这里先用占位色，后续按 Palette 的「角色 → 用途」表逐项注入真实 token。
 */
package com.github.axtv.dv.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

internal object AxTvColors {
    val Bg = Color(0xFF0B0B0E)
    val Surface = Color(0xFF16161C)
    val SurfaceHigh = Color(0xFF202028)
    val TextPrimary = Color(0xFFF5F5F7)
    val TextSecondary = Color(0xB3F5F5F7)
    val Accent = Color(0xFF25F4EE)      // 抖音青（占位）
    val AccentWarm = Color(0xFFFE2C55)  // 抖音红（占位）
    val Divider = Color(0xFF2C2C34)
}

/** TV Material3 暗色基线；TV 版 Typography/Shapes 用库默认值，避免自造 token。 */
@Composable
fun AxTvTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(), content = content)
}

/** 统一的「自绘焦点框」：TV 端不用 Leanback 的焦点盒，由 Compose 自绘。 */
internal fun Modifier.focusRing(focused: Boolean, shape: Shape = RoundedCornerShape(12.dp)): Modifier =
    border(
        width = if (focused) 2.dp else 1.dp,
        color = if (focused) AxTvColors.Accent else AxTvColors.Divider,
        shape = shape,
    )
