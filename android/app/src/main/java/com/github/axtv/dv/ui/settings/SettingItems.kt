/*
 * 设置页的条目渲染组件（从 SettingsScreen.kt 拆出）。
 * 拆分原因: SettingsScreen.kt 超 800 行；单条控件/主题预览/外观组件只被主页面调用。
 */
package com.github.axtv.dv.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import axtv.ui.SettingsPageSpec
import axtv.ui.SettingsPageSpec.Control
import axtv.ui.SettingsPageSpec.ControlType
import com.github.axtv.dv.BuildConfig
import com.github.axtv.dv.ui.AxTvColors
import com.github.axtv.dv.ui.focusRing

@Composable
internal fun SettingItem(
    control: Control,
    checked: Boolean,
    enumIndex: Int,
    enumOptions: List<String>,
    sliderValue: Float,
    textValue: String?,
    statusText: String?,
    enabled: Boolean,
    hint: String?,
    modifier: Modifier = Modifier,
    onToggle: () -> Unit,
    onEnumChange: (Int) -> Unit,
    onSliderChange: (Int) -> Unit,
    onFocused: () -> Unit = {},
    onAction: (String) -> Unit,
) {
    when (control.type()) {
        ControlType.TITLE -> Text(
            text = "设置",
            color = AxTvColors.TextPrimary,
            style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold),
        )

        ControlType.SECTION_HEADER, ControlType.STYLE_HEADER, ControlType.HEADER -> Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = sectionLabel(control.key()),
                color = AxTvColors.Accent,
                style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
            )
        }

        ControlType.NOTE -> Text(
            text = noteOf(control.key()),
            color = AxTvColors.TextSecondary,
            style = TextStyle(fontSize = 13.sp),
        )

        ControlType.APP_VERSION -> SettingShell(
            modifier = modifier,
            focusableRow = false,
        ) {
            Text("版本", color = AxTvColors.TextPrimary)
            Spacer(Modifier.weight(1f))
            Text(
                text = "${BuildConfig.VERSION_NAME}（versionCode ${BuildConfig.VERSION_CODE}）",
                color = AxTvColors.TextSecondary,
            )
        }

        ControlType.SWITCH_ROW, ControlType.ANIMATED_SWITCH_ROW, ControlType.SWITCH -> {
            SettingShell(
                modifier = modifier,
                enabled = enabled,
                onClick = onToggle,
                onFocused = onFocused,
            ) {
                RowLabel(labelOf(control.key()), hint, enabled)
                Spacer(Modifier.weight(1f))
                SwitchVisual(checked = checked, enabled = enabled)
            }
        }

        ControlType.ENUM_ROW, ControlType.ANIMATED_ENUM_ROW, ControlType.OPTION -> {
            val value = enumOptions.getOrElse(enumIndex) { "—" }
            SettingShell(
                modifier = modifier,
                enabled = enabled,
                onClick = { onEnumChange(1) },
                onKey = { event -> handleHorizontal(event, onEnumChange) },
                onFocused = onFocused,
            ) {
                RowLabel(labelOf(control.key()), hint, enabled)
                Spacer(Modifier.weight(1f))
                Text(
                    text = "‹  $value  ›",
                    color = if (enabled) AxTvColors.Accent else AxTvColors.TextSecondary,
                    style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                )
            }
        }

        ControlType.SLIDER -> {
            val spec = sliderSpec(control.key())
            SettingShell(
                modifier = modifier,
                enabled = enabled,
                onClick = { onSliderChange(1) },
                onKey = { event -> handleHorizontal(event, onSliderChange) },
                onFocused = onFocused,
            ) {
                RowLabel(labelOf(control.key()), hint, enabled)
                Spacer(Modifier.weight(1f))
                Text(
                    text = "‹  ${spec.formatter(sliderValue)}  ›",
                    color = if (enabled) AxTvColors.Accent else AxTvColors.TextSecondary,
                    style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                )
            }
        }

        ControlType.AUTH_TEXT_FIELD -> SettingShell(
            modifier = modifier,
            onClick = {},
            onFocused = onFocused,
        ) {
            RowLabel(labelOf(control.key()), hint, enabled = true)
            Spacer(Modifier.weight(1f))
            Text(
                text = textValue?.takeIf { it.isNotBlank() }?.let { preview(it) } ?: "未设置",
                color = AxTvColors.TextSecondary,
                style = TextStyle(fontSize = 12.sp),
            )
        }

        ControlType.ACTION_BUTTON, ControlType.ACTION, ControlType.AUTH_CONFIG_ACTIONS,
        ControlType.AUTH_LOGIN_CARD, ControlType.ACCOUNT_MANAGEMENT, ControlType.DANMAKU_LAYOUT_CONTROLS,
        ControlType.QUALITY, ControlType.CAMERA -> SettingShell(
            modifier = modifier,
            enabled = enabled,
            onClick = { onAction(control.key()) },
            onFocused = onFocused,
        ) {
            RowLabel(labelOf(control.key()), hint, enabled)
            Spacer(Modifier.weight(1f))
            Text(
                text = statusText ?: "OK",
                color = AxTvColors.TextSecondary,
                style = TextStyle(fontSize = 12.sp),
            )
        }

        ControlType.ACCOUNT_CARD, ControlType.AUTH_STATUS_CARD, ControlType.AUTH_SECURITY_CARD,
        ControlType.QR_LINKS_CARD -> SettingShell(
            modifier = modifier,
            // 卡片行默认无动作；about_qr_links 由 onAction 接管（其它键落到 when 的 else 不响应）。
            onClick = { onAction(control.key()) },
            onFocused = onFocused,
        ) {
            Box(
                modifier = Modifier.size(32.dp).background(AxTvColors.SurfaceHigh, CircleShape),
            )
            RowLabel(labelOf(control.key()), null, enabled = true)
            Spacer(Modifier.weight(1f))
            Text(
                text = statusText ?: cardHintOf(control.key()),
                color = AxTvColors.TextSecondary,
                style = TextStyle(fontSize = 12.sp),
            )
        }

        else -> SettingShell(
            modifier = modifier,
            onClick = {},
            onFocused = onFocused,
        ) {
            RowLabel(labelOf(control.key()), hint, enabled)
            Spacer(Modifier.weight(1f))
            Text(control.type().raw(), color = AxTvColors.TextSecondary, style = TextStyle(fontSize = 12.sp))
        }
    }
}

/** 动态枚举行（单选）。 */
@Composable
internal fun SelectSettingItem(
    label: String,
    description: String?,
    selected: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onFocused: () -> Unit = {},
    onClick: () -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged {
                isFocused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier.focusable())
            .background(
                color = if (selected) AxTvColors.SurfaceHigh else AxTvColors.Surface,
                shape = RoundedCornerShape(10.dp),
            )
            .focusRing(isFocused, RoundedCornerShape(10.dp))
            .alpha(if (enabled) 1f else 0.55f)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = if (selected) AxTvColors.Accent else AxTvColors.TextPrimary,
                style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
            )
            if (description != null) {
                Text(
                    text = description,
                    color = AxTvColors.TextSecondary,
                    style = TextStyle(fontSize = 11.sp),
                )
            }
        }
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(
                    color = if (selected) AxTvColors.Accent else AxTvColors.TextSecondary.copy(alpha = 0.35f),
                    shape = CircleShape,
                ),
        )
    }
}

/** 主题预览：随主题模式（自动/浅色/暗色）与色彩风格变化，非焦点元素。 */
@Composable
internal fun ThemePreview(themeModeIndex: Int, colorStyleIndex: Int) {
    val dark = themeModeIndex != 1
    val background = if (dark) AxTvColors.Bg else Color(0xFFF3F3F7)
    val surface = if (dark) AxTvColors.Surface else Color(0xFFFFFFFF)
    val text = if (dark) AxTvColors.TextPrimary else Color(0xFF17171C)
    val secondary = if (dark) AxTvColors.TextSecondary else Color(0x9917171C)
    val accent = when (colorStyleIndex) {
        1 -> AxTvColors.AccentWarm
        2 -> Color(0xFF7FC8BD)
        3 -> Color(0xFF9AA0A6)
        else -> AxTvColors.Accent
    }
    val modeLabel = THEME_LABELS.getOrElse(themeModeIndex) { "—" }
    val styleLabel = COLOR_STYLE_LABELS.getOrElse(colorStyleIndex) { "—" }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(surface, RoundedCornerShape(10.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(background, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier.size(18.dp).background(accent, CircleShape),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "主题预览 · $modeLabel / $styleLabel",
                color = secondary,
                style = TextStyle(fontSize = 11.sp),
            )
            Text(
                text = "标题示例",
                color = text,
                style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
            )
            Text(
                text = "正文与次要信息",
                color = secondary,
                style = TextStyle(fontSize = 12.sp),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PreviewSwatch(accent)
            PreviewSwatch(text)
            PreviewSwatch(secondary)
        }
    }
}

@Composable
internal fun PreviewSwatch(color: Color) {
    Box(modifier = Modifier.size(14.dp).background(color, RoundedCornerShape(4.dp)))
}

/** 左右键统一处理：返回 true 表示已消费。 */
internal fun handleHorizontal(event: KeyEvent, onChange: (Int) -> Unit): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    return when (event.key) {
        Key.DirectionLeft -> {
            onChange(-1)
            true
        }

        Key.DirectionRight -> {
            onChange(1)
            true
        }

        else -> false
    }
}

/**
 * 可聚焦行外壳：统一处理焦点自绘、OK 点击与左右键；[focusableRow] 为 false 时只做静态展示，
 * [enabled] 为 false 时保留焦点（可读到置灰说明）但不响应 OK / 左右键。
 */
@Composable
internal fun SettingShell(
    modifier: Modifier = Modifier,
    focusableRow: Boolean = true,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    onKey: ((KeyEvent) -> Boolean)? = null,
    onFocused: () -> Unit = {},
    content: @Composable RowScope.() -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }
    val interaction = when {
        !focusableRow -> Modifier
        !enabled -> Modifier.focusable()
        onClick != null -> Modifier.clickable(onClick = onClick)
        else -> Modifier.focusable()
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged {
                isFocused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .then(if (enabled && onKey != null) Modifier.onKeyEvent(onKey) else Modifier)
            .then(interaction)
            .background(AxTvColors.Surface, RoundedCornerShape(10.dp))
            .focusRing(isFocused, RoundedCornerShape(10.dp))
            .alpha(if (enabled) 1f else 0.55f)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
internal fun RowLabel(text: String, hint: String?, enabled: Boolean) {
    Column {
        Text(
            text = text,
            color = if (enabled) AxTvColors.TextPrimary else AxTvColors.TextSecondary,
        )
        if (hint != null) {
            Text(
                text = hint,
                color = AxTvColors.TextSecondary,
                style = TextStyle(fontSize = 11.sp),
            )
        }
    }
}

@Composable
internal fun SwitchVisual(checked: Boolean, enabled: Boolean = true) {
    Box(
        modifier = Modifier
            .width(40.dp)
            .height(22.dp)
            .background(
                color = when {
                    !enabled -> AxTvColors.SurfaceHigh
                    checked -> AxTvColors.Accent.copy(alpha = 0.65f)
                    else -> AxTvColors.SurfaceHigh
                },
                shape = RoundedCornerShape(11.dp),
            ),
        contentAlignment = if (checked && enabled) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = 3.dp)
                .size(16.dp)
                .background(
                    color = if (enabled) AxTvColors.Bg else AxTvColors.TextSecondary,
                    shape = CircleShape,
                ),
        )
    }
}
