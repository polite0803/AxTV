/*
 * 设置页「取色 / 预览」对话框 —— camera 行的真实目的地。
 *
 * 展示内容（全部来自运行时真值，不做二次加工）：
 * - 取色来源：Settings.colorSource() 当前档 + AxAppearance 记录的实际种子来源（含回落说明）；
 * - 种子色：Settings.globalSeedArgb() 的运行时镜像（0=未取色，走品牌基线）；
 * - 推导梯度：当前调色板的强调色族与表面色（即 AxTvColors 正在绘制的那一套）；
 * - 外观设置摘要：主题模式 / 色彩风格 / 对比度 / 过渡动画现值。
 */
package com.github.axtv.dv.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Text
import axtv.settings.Settings
import axtv.settings.SettingsTypes
import com.github.axtv.dv.adapter.AppRuntime
import com.github.axtv.dv.ui.AxTvColors
import com.github.axtv.dv.ui.appearance.AxAppearance
import com.github.axtv.dv.ui.focusRing
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos

private fun hex(color: Color): String =
    String.format("#%06X", color.toArgb() and 0xFFFFFF)

/** 种子的 RGB 低 24 位转 Compose Color。 */
private fun rgbColor(rgb: Int): Color = Color(
    red = ((rgb shr 16) and 0xFF) / 255f,
    green = ((rgb shr 8) and 0xFF) / 255f,
    blue = (rgb and 0xFF) / 255f,
)

@Composable
private fun infoLine(label: String, value: String) = Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(10.dp),
) {
    Text(
        text = label,
        color = AxTvColors.TextSecondary,
        style = TextStyle(fontSize = 13.sp),
        modifier = Modifier.width(88.dp),
    )
    Text(
        text = value,
        color = AxTvColors.TextPrimary,
        style = TextStyle(fontSize = 13.sp),
    )
}

@Composable
internal fun PalettePreviewDialog(onDismiss: () -> Unit) {
    val settings = AppRuntime.settings
    val palette = AxAppearance.currentPalette.value
    val seed = AxAppearance.seedArgb.intValue
    val closeRequester = remember { FocusRequester() }

    val modeLabel = when (settings?.themeMode()) {
        SettingsTypes.ThemeMode.LIGHT -> "浅色"
        SettingsTypes.ThemeMode.DARK -> "暗色"
        else -> "自动"
    }
    val sourceLabel = when (settings?.colorSource()) {
        SettingsTypes.ColorSource.CONTENT -> "画面取色"
        SettingsTypes.ColorSource.DEVICE -> "设备取色"
        SettingsTypes.ColorSource.BRAND -> "品牌色"
        null -> "—"
    }
    val styleLabel = when (settings?.colorStyle()) {
        SettingsTypes.ColorStyle.VIBRANT -> "鲜艳"
        SettingsTypes.ColorStyle.SOFT -> "柔和"
        SettingsTypes.ColorStyle.FIDELITY -> "还原"
        else -> "按场景"
    }
    val contrastLabel = when (settings?.colorContrast()) {
        SettingsTypes.ColorContrast.MEDIUM -> "中等"
        SettingsTypes.ColorContrast.HIGH -> "高"
        else -> "标准"
    }
    val transitionLabel =
        if (settings?.colorTransition() == SettingsTypes.ColorTransition.OFF) "关闭" else "自动"

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .width(560.dp)
                .background(AxTvColors.Surface, RoundedCornerShape(16.dp))
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "取色 / 配色预览",
                color = AxTvColors.TextPrimary,
                style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold),
            )
            infoLine("取色来源", sourceLabel)
            infoLine("种子色", if (seed == 0) "未取色（品牌基线）" else hex(rgbColor(seed)))
            infoLine(
                "种子实际来自",
                AxAppearance.seedSourceLabel
                    ?: if (settings?.colorSource() == SettingsTypes.ColorSource.BRAND) "品牌色（内置调色板）" else "等待首次取色…"
            )
            infoLine("主题模式", "$modeLabel（当前渲染：${if (palette.dark) "暗色" else "浅色"}）")
            infoLine("色彩风格", styleLabel)
            infoLine("对比度", contrastLabel)
            infoLine("过渡动画", transitionLabel)

            Text(
                text = "推导梯度（正在绘制的调色板）",
                color = AxTvColors.TextSecondary,
                style = TextStyle(fontSize = 12.sp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SwatchCell("强调", palette.accent)
                SwatchCell("暖强调", palette.accentWarm)
                SwatchCell("表面", palette.surface)
                SwatchCell("高表面", palette.surfaceHigh)
                SwatchCell("分隔", palette.divider)
                SwatchCell("背景", palette.bg)
            }

            var closeFocused by remember { mutableStateOf(false) }
            Box(
                modifier = Modifier
                    .align(Alignment.End)
                    .focusRequester(closeRequester)
                    .clickable(onClick = onDismiss)
                    .background(AxTvColors.SurfaceHigh, RoundedCornerShape(8.dp))
                    .focusRing(closeFocused, RoundedCornerShape(8.dp))
                    .onFocusChanged { closeFocused = it.isFocused }
                    .padding(horizontal = 20.dp, vertical = 8.dp),
            ) {
                Text(
                    text = "关闭（BACK 亦可）",
                    color = AxTvColors.TextPrimary,
                    style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                )
            }
        }
    }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { closeRequester.requestFocus() }
    }
}

@Composable
private fun SwatchCell(name: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .background(color, CircleShape)
                .focusRing(false),
        )
        Text(text = name, color = AxTvColors.TextSecondary, style = TextStyle(fontSize = 10.sp))
        Text(text = hex(color), color = AxTvColors.TextSecondary, style = TextStyle(fontSize = 9.sp))
    }
}
