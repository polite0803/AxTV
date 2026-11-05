/*
 * 样板页 2：设置页（全量控件表 + 焦点链验证）。
 * 规格出处：refactor/ui/NOTES.md §2（主设置页 62 项，qm4.java m8567Z 注册顺序）
 *           refactor/ui/NOTES.md §3（弹幕样式面板 15 项，j16.java:1084-1356）
 *           refactor/ui/axtv/ui/SettingsPageSpec.java（key/type/section/settingsKey 逐项实证）
 * 规格数组实际为 79 项：主设置页 64 + 弹幕样式面板 15。
 * NOTES.md 记 77（主 62 + 面板 15），漏列 `interface_identity_header` / `client_identity_branch` 两项。
 *
 * 另有 qm4 运行时插入的枚举选择行（不在 77 项内）：点播清晰度 5、直播清晰度 4、
 * 解码 3、UI 形态 4、取色来源 3，共 19 行；以及 1 个非控件「主题预览」。
 */
package com.github.axtv.dv.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.foundation.ExperimentalTvFoundationApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.tv.material3.Text
import axtv.ui.SettingsPageSpec
import axtv.ui.SettingsPageSpec.Control
import axtv.ui.SettingsPageSpec.ControlType
import axtv.ui.SettingsPageSpec.LiveQualityOption
import axtv.ui.SettingsPageSpec.VideoQualityOption
import com.github.axtv.dv.BuildConfig
import com.github.axtv.dv.adapter.AppRuntime
import com.github.axtv.dv.adapter.SettingsBinder
import com.github.axtv.dv.ui.AxTvColors
import com.github.axtv.dv.ui.focusRing
import kotlin.math.roundToInt

private val MAIN_COUNT = SettingsPageSpec.MAIN_PAGE.size
private val PANEL_COUNT = SettingsPageSpec.DANMAKU_STYLE_PANEL.size
private const val THEME_PREVIEW_KEY = "theme_preview"

/** 结构行：不参与焦点链。 */
private fun Control.isStructural(): Boolean = when (type()) {
    ControlType.TITLE,
    ControlType.SECTION_HEADER,
    ControlType.STYLE_HEADER,
    ControlType.HEADER,
    ControlType.NOTE -> true

    else -> false
}

private fun Control.isSwitch(): Boolean = when (type()) {
    ControlType.SWITCH_ROW, ControlType.ANIMATED_SWITCH_ROW, ControlType.SWITCH -> true
    else -> false
}

private fun Control.isEnum(): Boolean = when (type()) {
    ControlType.ENUM_ROW, ControlType.ANIMATED_ENUM_ROW, ControlType.OPTION -> true
    else -> false
}

/** 同一配置键被两个行共用时，用规范键让两行共享一份 UI 状态。 */
private fun canonicalKey(key: String): String = when (key) {
    "danmaku" -> "live_danmaku"
    "blocklist" -> "danmaku_blocklist"
    else -> key
}

// ════════════════════════════ 行模型 ════════════════════════════

private sealed interface SettingsRow {
    val key: String
}

private data class ControlRow(val control: Control) : SettingsRow {
    override val key: String get() = control.key()
}

/** 动态插入的「单选行」：每档一行，行内用选中圆点表示当前值。 */
private data class SelectRow(
    override val key: String,
    val label: String,
    val description: String?,
    val binderKey: String,
    val optionIndex: Int,
) : SettingsRow

/** 非控件绘制：按当前主题模式 / 色彩风格渲染的小预览卡。 */
private data class ThemePreviewRow(override val key: String = THEME_PREVIEW_KEY) : SettingsRow

private val LIVE_DEPENDENT_PANEL_KEYS = setOf(
    "style_header", "style_opacity", "style_area", "style_vspacing", "style_hspacing",
    "style_font", "style_speed", "filter_package", "blocklist", "style",
)

/** 直播设置 4 档（qm4 遍历时过滤 AUTO，直播不能选「自动」）。 */
private val LIVE_QUALITY_OPTIONS = listOf(
    LiveQualityOption.HIGHEST,
    LiveQualityOption.HIGH,
    LiveQualityOption.MID,
    LiveQualityOption.LOW,
)

private val FORM_FACTOR_NAMES = listOf("AUTO", "PHONE_PORTRAIT", "TABLET_LANDSCAPE", "TV_LANDSCAPE")
private val FORM_FACTOR_LABELS = listOf("自动", "手机竖屏", "平板横屏", "TV 横屏")
private val COLOR_SOURCE_NAMES = listOf("CONTENT", "DEVICE", "BRAND")
private val COLOR_SOURCE_LABELS = listOf("画面取色", "设备取色", "品牌色")
private val CODEC_NAMES = listOf("AUTO", "H264", "H265")
private val CODEC_LABELS = listOf("自动", "H.264", "H.265")

private val THEME_LABELS = listOf("自动", "浅色", "暗色")
private val COLOR_STYLE_LABELS = listOf("按场景", "鲜艳", "柔和", "还原")

private fun buildRows(): List<SettingsRow> {
    val rows = ArrayList<SettingsRow>(112)
    for (control in SettingsPageSpec.MAIN_PAGE) {
        rows += ControlRow(control)
        when (control.key()) {
            "live_resolution_header" -> rows += liveQualityRows()
            "form_factor_header" -> rows += formFactorRows()
            "color_source_header" -> rows += colorSourceRows()
            "video_resolution_header" -> rows += videoQualityRows()
            "video_codec_header" -> rows += codecRows()
            "theme_mode" -> rows += ThemePreviewRow()
        }
    }
    SettingsPageSpec.DANMAKU_STYLE_PANEL.forEach { rows += ControlRow(it) }
    return rows
}

private fun liveQualityRows(): List<SelectRow> = LIVE_QUALITY_OPTIONS.mapIndexed { index, option ->
    SelectRow(
        key = "live_resolution_${option.name}",
        label = option.label(),
        description = option.description(),
        binderKey = "live_video_resolution",
        optionIndex = index,
    )
}

private fun videoQualityRows(): List<SelectRow> =
    VideoQualityOption.values().mapIndexed { index, option ->
        SelectRow(
            key = "video_resolution_${option.name}",
            label = option.label(),
            description = option.description(),
            binderKey = "video_resolution",
            optionIndex = index,
        )
    }

private fun formFactorRows(): List<SelectRow> = FORM_FACTOR_NAMES.mapIndexed { index, name ->
    SelectRow(
        key = "form_factor_$name",
        label = FORM_FACTOR_LABELS[index],
        description = null,
        binderKey = "form_factor",
        optionIndex = index,
    )
}

private fun colorSourceRows(): List<SelectRow> = COLOR_SOURCE_NAMES.mapIndexed { index, name ->
    SelectRow(
        key = "color_source_$name",
        label = COLOR_SOURCE_LABELS[index],
        description = if (name == "DEVICE") "页面颜色与电视一致" else null,
        binderKey = "color_source",
        optionIndex = index,
    )
}

private fun codecRows(): List<SelectRow> = CODEC_NAMES.mapIndexed { index, name ->
    SelectRow(
        key = "video_codec_$name",
        label = CODEC_LABELS[index],
        description = null,
        binderKey = "video_codec",
        optionIndex = index,
    )
}

private fun isRowVisible(row: SettingsRow, liveDanmakuOn: Boolean): Boolean = when {
    row is ControlRow && row.key in LIVE_DEPENDENT_PANEL_KEYS -> liveDanmakuOn
    else -> true
}

private fun isRowEnabled(
    row: SettingsRow,
    liveDanmakuOn: Boolean,
    anyDanmakuOn: Boolean,
    colorFromContent: Boolean,
): Boolean = when {
    row !is ControlRow -> true
    row.key == "color_style" || row.key == "color_contrast" -> colorFromContent
    row.key == "live_danmaku_style" -> liveDanmakuOn
    row.key == "danmaku_emoji" || row.key == "danmaku_layout" -> anyDanmakuOn
    else -> true
}

private fun rowHint(key: String, colorFromContent: Boolean): String? = when (key) {
    "color_style", "color_contrast" -> if (!colorFromContent) "仅画面取色时生效" else null
    else -> null
}

private fun SettingsRow.isStructural(): Boolean = this is ControlRow && control.isStructural()

private fun SettingsRow.isFocusableRow(): Boolean = when (this) {
    is ControlRow -> !control.isStructural()
    is SelectRow -> true
    is ThemePreviewRow -> false
}

// ════════════════════════════ 滑杆 ════════════════════════════

/** 滑杆取值范围与步进（夹取语义由 Settings 内部保证）。 */
private class SliderSpec(
    val min: Float,
    val max: Float,
    val step: Float,
    val formatter: (Float) -> String,
)

private fun sliderSpec(key: String): SliderSpec = when (key) {
    // j16 pv0：显示 1..100%，步进 1%；持久化为 0.01..1.00。
    "style_opacity" -> SliderSpec(0.01f, 1.0f, 0.01f) { "${(it * 100).roundToInt()}%" }
    // j16 xn2：1..100，步进 1。
    "style_area" -> SliderSpec(1f, 100f, 1f) { "${it.roundToInt()}%" }
    // j16 xn2：0.5..2.0，步进 0.1，展示带 "x"。
    "style_vspacing" -> SliderSpec(0.5f, 2.0f, 0.1f) { "${formatTenth(it)}x" }
    "style_hspacing" -> SliderSpec(0.5f, 2.0f, 0.1f) { "${formatTenth(it)}x" }
    else -> SliderSpec(0f, 1f, 0.1f) { formatTenth(it) }
}

private fun formatTenth(value: Float): String =
    (value * 10).roundToInt().let { scaled -> "${scaled / 10}.${scaled % 10}" }

@OptIn(ExperimentalTvFoundationApi::class)
@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val rows = remember { buildRows() }
    // 装配根因修复：此前只写 remember 内存状态，从未接触 Settings；现由 Application 注入的真实实例读写。
    val binder = remember { AppRuntime.settings?.let(::SettingsBinder) }

    val switchStates = remember(binder, rows) {
        mutableStateMapOf<String, Boolean>().apply {
            rows.filterIsInstance<ControlRow>()
                .map { it.control }
                .filter { it.isSwitch() }
                .forEach { put(canonicalKey(it.key()), binder?.readBoolean(it.key()) ?: false) }
        }
    }
    val enumIndexes = remember(binder, rows) {
        mutableStateMapOf<String, Int>().apply {
            rows.filterIsInstance<ControlRow>()
                .map { it.control }
                .filter { it.isEnum() }
                .forEach { put(it.key(), binder?.readEnumIndex(it.key()) ?: 0) }
        }
    }
    val sliderValues = remember(binder, rows) {
        mutableStateMapOf<String, Float>().apply {
            rows.filterIsInstance<ControlRow>()
                .map { it.control }
                .filter { it.type() == ControlType.SLIDER }
                .forEach { put(it.key(), binder?.readFloat(it.key()) ?: 0f) }
        }
    }
    val dynamicIndexes = remember(binder) {
        mutableStateMapOf<String, Int>().apply {
            listOf("video_resolution", "live_video_resolution", "video_codec", "form_factor", "color_source")
                .forEach { put(it, binder?.readEnumIndex(it) ?: 0) }
        }
    }

    val focusRequesters = remember(rows) { rows.associate { it.key to FocusRequester() } }
    var focusedKey by remember { mutableStateOf<String?>(null) }
    // 线 H：检查更新对话框（about_update 行触发；下载/安装全部走 UpdateManager）。
    var showUpdateDialog by remember { mutableStateOf(false) }
    var updateDialogWasOpen by remember { mutableStateOf(false) }

    val liveDanmakuOn = switchStates[canonicalKey("live_danmaku")] == true
    val anyDanmakuOn = liveDanmakuOn || switchStates["video_danmaku"] == true
    val colorSourceIndex = dynamicIndexes["color_source"] ?: 0
    val colorFromContent = colorSourceIndex == 0

    val visibleRows = rows.filter { isRowVisible(it, liveDanmakuOn) }
    val firstFocusableKey = visibleRows.firstOrNull { it.isFocusableRow() }?.key

    LaunchedEffect(firstFocusableKey) {
        val key = firstFocusableKey ?: return@LaunchedEffect
        withFrameNanos { }
        runCatching { focusRequesters[key]?.requestFocus() }
    }

    // 弹层关闭（含 BACK 关闭 Dialog）后把焦点还给触发行。
    LaunchedEffect(showUpdateDialog) {
        if (showUpdateDialog) {
            updateDialogWasOpen = true
        } else if (updateDialogWasOpen) {
            withFrameNanos { }
            runCatching { focusRequesters["about_update"]?.requestFocus() }
        }
    }

    val dynamicCount = rows.count { it is SelectRow }
    val controlCount = rows.count { it !is ThemePreviewRow }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 40.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "设置页",
                        color = AxTvColors.TextPrimary,
                        style = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold),
                    )
                    Text(
                        text = "规格 ${MAIN_COUNT + PANEL_COUNT} 项 = 主设置页 $MAIN_COUNT + 面板 $PANEL_COUNT" +
                            "（NOTES 记 77，规格数组另有接口身份 2 项）" +
                            " + 动态枚举行 $dynamicCount = $controlCount 个控件行",
                        color = AxTvColors.TextSecondary,
                        style = TextStyle(fontSize = 13.sp),
                    )
                }
            }
            items(count = visibleRows.size) { index ->
                val row = visibleRows[index]
                when (row) {
                    is ControlRow -> {
                        val control = row.control
                        val enabled = isRowEnabled(row, liveDanmakuOn, anyDanmakuOn, colorFromContent)
                        val hint = rowHint(control.key(), colorFromContent)
                        SettingItem(
                            control = control,
                            checked = switchStates[canonicalKey(control.key())] == true,
                            enumIndex = enumIndexes[control.key()] ?: 0,
                            enumOptions = binder?.enumLabels(control.key()).orEmpty(),
                            sliderValue = sliderValues[control.key()] ?: 0f,
                            textValue = binder?.readText(control.key()),
                            statusText = binder?.readStatus(control.key()),
                            enabled = enabled,
                            hint = hint,
                            modifier = Modifier.focusRequester(focusRequesters.getValue(row.key)),
                            onToggle = {
                                val stateKey = canonicalKey(control.key())
                                val next = switchStates[stateKey] != true
                                switchStates[stateKey] = next
                                binder?.writeBoolean(control.key(), next)
                            },
                            onEnumChange = { delta ->
                                val options = binder?.enumLabels(control.key()).orEmpty()
                                if (options.isNotEmpty()) {
                                    val next = ((enumIndexes[control.key()] ?: 0) + delta).mod(options.size)
                                    enumIndexes[control.key()] = next
                                    binder?.writeEnumIndex(control.key(), next)
                                }
                            },
                            onSliderChange = { delta ->
                                val spec = sliderSpec(control.key())
                                val current = sliderValues[control.key()] ?: spec.min
                                val raw = current + delta * spec.step
                                val snapped = (raw / spec.step).roundToInt() * spec.step
                                val next = snapped.coerceIn(spec.min, spec.max)
                                sliderValues[control.key()] = next
                                binder?.writeFloat(control.key(), next)
                            },
                            onFocused = { focusedKey = control.key() },
                            onAction = { key ->
                                when (key) {
                                    "about_update" -> showUpdateDialog = true
                                    "quality" -> runCatching {
                                        focusRequesters["live_resolution_HIGHEST"]?.requestFocus()
                                    }

                                    "style" -> runCatching {
                                        focusRequesters["style_opacity"]?.requestFocus()
                                    }

                                    else -> Unit
                                }
                            },
                        )
                    }

                    is SelectRow -> SelectSettingItem(
                        label = row.label,
                        description = row.description,
                        selected = dynamicIndexes[row.binderKey] == row.optionIndex,
                        modifier = Modifier.focusRequester(focusRequesters.getValue(row.key)),
                        onFocused = { focusedKey = row.key },
                        onClick = {
                            dynamicIndexes[row.binderKey] = row.optionIndex
                            binder?.writeEnumIndex(row.binderKey, row.optionIndex)
                            // 点播清晰度改变后，未单独设过的直播清晰度由 Settings 内部跟随；
                            // 这里同步 UI 状态，避免仍显示旧档位。
                            if (row.binderKey == "video_resolution") {
                                dynamicIndexes["live_video_resolution"] =
                                    binder?.readEnumIndex("live_video_resolution")
                                        ?: dynamicIndexes["live_video_resolution"]
                                        ?: 0
                            }
                        },
                    )

                    is ThemePreviewRow -> ThemePreview(
                        themeModeIndex = enumIndexes["theme_mode"] ?: 0,
                        colorStyleIndex = enumIndexes["color_style"] ?: 0,
                    )
                }
            }
            item {
                Text(
                    text = "↑/↓ 移动焦点 · OK 开关/进入 · ←/→ 调值 · BACK 关闭弹层或返回上一页" +
                        (focusedKey?.let { " · 当前焦点 $it" } ?: ""),
                    color = AxTvColors.TextSecondary,
                    style = TextStyle(fontSize = 12.sp),
                )
            }
        }

        if (showUpdateDialog) {
            UpdateDialog(onDismiss = { showUpdateDialog = false })
        }
    }
}

@Composable
private fun SettingItem(
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
    onFocused: () -> Unit,
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
            Text(
                text = "  · ${control.key()}",
                color = AxTvColors.TextSecondary,
                style = TextStyle(fontSize = 11.sp),
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
            onClick = {},
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
private fun SelectSettingItem(
    label: String,
    description: String?,
    selected: Boolean,
    modifier: Modifier = Modifier,
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
            .clickable(onClick = onClick)
            .background(
                color = if (selected) AxTvColors.SurfaceHigh else AxTvColors.Surface,
                shape = RoundedCornerShape(10.dp),
            )
            .focusRing(isFocused, RoundedCornerShape(10.dp))
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
private fun ThemePreview(themeModeIndex: Int, colorStyleIndex: Int) {
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
private fun PreviewSwatch(color: Color) {
    Box(modifier = Modifier.size(14.dp).background(color, RoundedCornerShape(4.dp)))
}

/** 左右键统一处理：返回 true 表示已消费。 */
private fun handleHorizontal(event: KeyEvent, onChange: (Int) -> Unit): Boolean {
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
private fun SettingShell(
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
private fun RowLabel(text: String, hint: String?, enabled: Boolean) {
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
private fun SwitchVisual(checked: Boolean, enabled: Boolean = true) {
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

/** 凭据类值只做截断预览，避免整段 cookie / 私钥铺满整行。 */
private fun preview(value: String): String =
    if (value.length <= 28) value else value.take(28) + "…"

private val LABELS = mapOf(
    // 主设置页 62 项
    "settings_title" to "设置",
    "settings_account" to "账号",
    "live_resolution_header" to "直播设置",
    "danmaku_header" to "弹幕",
    "video_danmaku" to "视频弹幕",
    "live_danmaku" to "直播弹幕",
    "live_danmaku_style" to "直播弹幕样式",
    "danmaku_emoji" to "弹幕表情",
    "danmaku_blocklist" to "弹幕屏蔽词",
    "danmaku_layout" to "弹幕布局",
    "auth_status_header" to "登录状态",
    "auth_status" to "当前登录状态",
    "account_management_header" to "账号管理",
    "account_management" to "切换 / 删除账号",
    "auth_qr_header" to "扫码登录",
    "auth_login_mode" to "登录方式",
    "auth_cookie_header" to "Cookie 登录",
    "auth_cookie" to "Cookie",
    "auth_security_header" to "互动安全",
    "auth_security_status" to "安全凭据状态",
    "auth_security_payload" to "安全载荷",
    "auth_private_key" to "私钥",
    "auth_config_actions" to "凭据操作",
    "about_update" to "检查更新",
    "about_github" to "GitHub 主页",
    "about_telegram" to "Telegram 群组",
    "about_qr_links" to "二维码链接",
    "about_version" to "版本",
    "interface_identity_header" to "接口身份",
    "client_identity_branch" to "客户端身份分支",
    "form_factor_header" to "显示与字体",
    "form_factor_note" to "三米外观看时可以调大一档",
    "app_font_scale" to "字体大小",
    "default_home" to "默认首页",
    "theme_mode" to "主题模式",
    "color_source_header" to "色彩来源",
    "color_style" to "色彩风格",
    "color_contrast" to "色彩对比度",
    "ambient_motion_header" to "氛围动效",
    "ambient_backdrop" to "氛围背景",
    "color_transition" to "色彩过渡动画",
    "video_resolution_header" to "视频清晰度",
    "video_codec_header" to "视频解码",
    "filter_mobile_video" to "过滤手机视频",
    "pip_enabled" to "画中画",
    "reverse_navigation" to "反向导航",
    "reverse_profile_playlist" to "个人页倒序播放列表",
    "profile_history_tab" to "个人页默认历史标签",
    "disable_double_back_exit" to "禁用双击返回退出",
    "show_details_on_start" to "启动时显示详情",
    "player_progress_bar" to "播放器进度条",
    "clean_pause" to "纯净暂停",
    "tv_launcher_channel" to "TV 桌面频道",
    "player_surface_type" to "播放器画面类型",
    "surface_buffer_align" to "Surface 缓冲区对齐",
    "audio_balance" to "音量均衡",
    "playback_clock" to "播放时钟",
    "playback_completion" to "播放结束行为",
    "video_load_timeout" to "视频加载超时",
    "separate_video_fetch" to "音视频分离拉取",
    "video_start_prefetch" to "起播预加载",
    "mix_playlist_panel" to "合集播放列表面板",
    "featured_auto_play" to "精选自动播放",
    "feed_history_capacity" to "历史记录容量",
    // 弹幕样式面板 15 项
    "style_header" to "弹幕样式",
    "style_opacity" to "不透明度",
    "style_area" to "显示区域",
    "style_vspacing" to "行间距",
    "style_hspacing" to "水平间距",
    "style_font" to "字号",
    "style_speed" to "速度",
    "header" to "弹幕样式 · 更多",
    "quality" to "画质",
    "camera" to "取色 / 预览相机",
    "profile" to "配置管理",
    "danmaku" to "弹幕总开关",
    "filter_package" to "过滤弹幕包",
    "blocklist" to "屏蔽词",
    "style" to "样式配置操作",
)

private fun labelOf(key: String): String = LABELS[key] ?: key

private val SECTION_LABELS = mapOf(
    "live_resolution_header" to "直播设置",
    "danmaku_header" to "弹幕",
    "auth_status_header" to "登录状态",
    "account_management_header" to "账号管理",
    "auth_qr_header" to "扫码登录",
    "auth_cookie_header" to "Cookie 登录",
    "auth_security_header" to "互动安全",
    "interface_identity_header" to "接口身份",
    "form_factor_header" to "显示与字体",
    "color_source_header" to "色彩来源",
    "ambient_motion_header" to "氛围动效",
    "video_resolution_header" to "视频清晰度",
    "video_codec_header" to "视频解码",
    "style_header" to "弹幕样式面板",
    "header" to "弹幕样式 · 更多",
)

private fun sectionLabel(key: String): String = SECTION_LABELS[key] ?: key

private fun noteOf(key: String): String =
    if (key == "form_factor_note") "三米外观看时可以调大一档" else key

private fun cardHintOf(key: String): String = when (key) {
    "auth_status" -> "未登录"
    "about_qr_links" -> "二维码"
    else -> "账号"
}
