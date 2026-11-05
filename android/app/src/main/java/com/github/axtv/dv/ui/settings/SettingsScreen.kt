/*
 * 样板页 2：设置页（代表性分组 + 焦点链验证）。
 * 规格出处：refactor/ui/NOTES.md §设置页（qm4.java:7.3k 注册表，62 项）、
 *           refactor/ui/axtv/ui/SettingsPageSpec.java（key/type/section/settingsKey 逐项实证）。
 * 这里只取 16 个代表项（标题/账号卡/4 组 section/开关/枚举/动作/版本），
 * 其余 46 项由同一渲染器按规格表批量展开，不重写控件语义。
 */
package com.github.axtv.dv.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
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
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.material3.Text
import axtv.ui.SettingsPageSpec
import axtv.ui.SettingsPageSpec.Control
import axtv.ui.SettingsPageSpec.ControlType
import com.github.axtv.dv.adapter.AppRuntime
import com.github.axtv.dv.adapter.SettingsBinder
import com.github.axtv.dv.ui.AxTvColors
import com.github.axtv.dv.ui.focusRing

/** 代表性分组：覆盖设置页的「标题 / 账号 / 分组头 / 开关 / 枚举 / 动作 / 版本 / 说明」。 */
private val DEMO_KEYS = setOf(
    "settings_title",
    "settings_account",
    "live_resolution_header",
    "video_danmaku",
    "live_danmaku",
    "live_danmaku_style",
    "danmaku_emoji",
    "danmaku_blocklist",
    "video_codec_header",
    "app_font_scale",
    "default_home",
    "theme_mode",
    "video_resolution_header",
    "player_surface_type",
    "surface_buffer_align",
    "disable_double_back_exit",
    "about_version",
    "form_factor_header",
    "form_factor_note",
)

/** 枚举项兜底文案；binder 可用时改用 SettingsBinder.enumLabels()（顺序与核心层枚举一致）。 */
private val DEMO_ENUM_OPTIONS = mapOf(
    "live_danmaku_style" to listOf("滚动样式", "列表样式"),
    "app_font_scale" to listOf("标准", "大", "特大", "超大"),
    "default_home" to listOf("推荐", "精选", "直播", "移动推荐"),
    "theme_mode" to listOf("自动", "浅色", "暗色"),
    "player_surface_type" to listOf("SurfaceView", "TextureView", "GL SurfaceView"),
)

@OptIn(ExperimentalTvFoundationApi::class)
@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val controls = remember {
        SettingsPageSpec.MAIN_PAGE.filter { control -> control.key() in DEMO_KEYS }
    }
    // 装配根因修复：此前只写 remember 内存状态，从未接触 Settings；现由 Application 注入的真实实例读写。
    val binder = remember { AppRuntime.settings?.let(::SettingsBinder) }
    val switchStates = remember {
        mutableStateMapOf<String, Boolean>().apply {
            controls.filter { it.type() == ControlType.SWITCH_ROW || it.type() == ControlType.ANIMATED_SWITCH_ROW }
                .forEach { put(it.key(), binder?.readBoolean(it.key()) ?: false) }
        }
    }
    val enumIndexes = remember {
        mutableStateMapOf<String, Int>().apply {
            controls.filter { it.type() == ControlType.ENUM_ROW || it.type() == ControlType.ANIMATED_ENUM_ROW }
                .forEach { put(it.key(), binder?.readEnumIndex(it.key()) ?: 0) }
        }
    }
    val firstFocusableIndex = remember(controls) {
        controls.indexOfFirst {
            it.type() != ControlType.TITLE &&
                it.type() != ControlType.SECTION_HEADER &&
                it.type() != ControlType.NOTE
        }
    }
    val firstRequester = remember { FocusRequester() }
    var focusedKey by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { firstRequester.requestFocus() }
    }

    TvLazyColumn(
        modifier = modifier.fillMaxSize(),
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
                    text = "代表 ${controls.size}/62 项 · 全量注册表见 SettingsPageSpec.MAIN_PAGE",
                    color = AxTvColors.TextSecondary,
                    style = TextStyle(fontSize = 13.sp),
                )
            }
        }
        items(count = controls.size) { index ->
            val control = controls[index]
            val isFirst = index == firstFocusableIndex
            SettingItem(
                control = control,
                checked = switchStates[control.key()] == true,
                enumIndex = enumIndexes[control.key()] ?: 0,
                enumOptions = binder?.enumLabels(control.key()) ?: DEMO_ENUM_OPTIONS[control.key()].orEmpty(),
                modifier = if (isFirst) Modifier.focusRequester(firstRequester) else Modifier,
                onToggle = {
                    val next = switchStates[control.key()] != true
                    switchStates[control.key()] = next
                    binder?.writeBoolean(control.key(), next)
                },
                onEnumChange = { delta ->
                    val options =
                        binder?.enumLabels(control.key()) ?: DEMO_ENUM_OPTIONS[control.key()].orEmpty()
                    if (options.isNotEmpty()) {
                        val next = ((enumIndexes[control.key()] ?: 0) + delta).mod(options.size)
                        enumIndexes[control.key()] = next
                        binder?.writeEnumIndex(control.key(), next)
                    }
                },
                onFocused = { focusedKey = control.key() },
            )
        }
        item {
            Text(
                text = "焦点链验证：↑/↓ 逐行移动 · OK 切换开关 · ←/→ 调整枚举" +
                    (focusedKey?.let { " · 当前焦点 $it" } ?: ""),
                color = AxTvColors.TextSecondary,
                style = TextStyle(fontSize = 12.sp),
            )
        }
    }
}

@Composable
private fun SettingItem(
    control: Control,
    checked: Boolean,
    enumIndex: Int,
    enumOptions: List<String>,
    modifier: Modifier = Modifier,
    onToggle: () -> Unit,
    onEnumChange: (Int) -> Unit,
    onFocused: () -> Unit,
) {
    when (control.type()) {
        ControlType.TITLE -> Text(
            text = "设置",
            color = AxTvColors.TextPrimary,
            style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold),
        )

        ControlType.SECTION_HEADER -> Row(
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
            text = "三米外观看时可以调大一档 · 来源 SettingsPageSpec / qm4",
            color = AxTvColors.TextSecondary,
            style = TextStyle(fontSize = 13.sp),
        )

        ControlType.APP_VERSION -> SettingShell(
            modifier = modifier,
            focusableRow = false,
        ) {
            Text("版本", color = AxTvColors.TextPrimary)
            Spacer(Modifier.weight(1f))
            Text("0.0.1（versionCode 1）", color = AxTvColors.TextSecondary)
        }

        ControlType.SWITCH_ROW, ControlType.ANIMATED_SWITCH_ROW -> {
            SettingShell(
                modifier = modifier,
                onClick = onToggle,
                onFocused = onFocused,
            ) {
                Text(labelOf(control.key()), color = AxTvColors.TextPrimary)
                Spacer(Modifier.weight(1f))
                SwitchVisual(checked = checked)
            }
        }

        ControlType.ENUM_ROW, ControlType.ANIMATED_ENUM_ROW -> {
            val value = enumOptions.getOrElse(enumIndex) { "占位值" }
            SettingShell(
                modifier = modifier,
                onClick = { onEnumChange(1) },
                onKey = { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionLeft) {
                        onEnumChange(-1)
                        true
                    } else if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionRight) {
                        onEnumChange(1)
                        true
                    } else {
                        false
                    }
                },
                onFocused = onFocused,
            ) {
                Text(labelOf(control.key()), color = AxTvColors.TextPrimary)
                Spacer(Modifier.weight(1f))
                Text(
                    text = "‹  $value  ›",
                    color = AxTvColors.Accent,
                    style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                )
            }
        }

        ControlType.ACTION_BUTTON -> SettingShell(
            modifier = modifier,
            onClick = {},
            onFocused = onFocused,
        ) {
            Text(labelOf(control.key()), color = AxTvColors.TextPrimary)
            Spacer(Modifier.weight(1f))
            Text("OK", color = AxTvColors.TextSecondary, style = TextStyle(fontSize = 12.sp))
        }

        ControlType.ACCOUNT_CARD, ControlType.AUTH_STATUS_CARD -> SettingShell(
            modifier = modifier,
            onClick = {},
            onFocused = onFocused,
        ) {
            Box(
                modifier = Modifier.size(32.dp).background(AxTvColors.SurfaceHigh, CircleShape),
            )
            Text("未登录（骨架占位）", color = AxTvColors.TextPrimary)
            Spacer(Modifier.weight(1f))
            Text("账号", color = AxTvColors.TextSecondary, style = TextStyle(fontSize = 12.sp))
        }

        else -> SettingShell(
            modifier = modifier,
            onClick = {},
            onFocused = onFocused,
        ) {
            Text(labelOf(control.key()), color = AxTvColors.TextPrimary)
            Spacer(Modifier.weight(1f))
            Text(control.type().raw(), color = AxTvColors.TextSecondary, style = TextStyle(fontSize = 12.sp))
        }
    }
}

/**
 * 可聚焦行外壳：统一处理焦点自绘、OK 点击与左右键；[focusableRow] 为 false 时只做静态展示。
 */
@Composable
private fun SettingShell(
    modifier: Modifier = Modifier,
    focusableRow: Boolean = true,
    onClick: (() -> Unit)? = null,
    onKey: ((KeyEvent) -> Boolean)? = null,
    onFocused: () -> Unit = {},
    content: @Composable RowScope.() -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }
    val interaction = when {
        !focusableRow -> Modifier
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
            .then(if (onKey != null) Modifier.onKeyEvent(onKey) else Modifier)
            .then(interaction)
            .background(AxTvColors.Surface, RoundedCornerShape(10.dp))
            .focusRing(isFocused, RoundedCornerShape(10.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
private fun SwitchVisual(checked: Boolean) {
    Box(
        modifier = Modifier
            .width(40.dp)
            .height(22.dp)
            .background(
                color = if (checked) AxTvColors.Accent.copy(alpha = 0.65f) else AxTvColors.SurfaceHigh,
                shape = RoundedCornerShape(11.dp),
            ),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = 3.dp)
                .size(16.dp)
                .background(if (checked) AxTvColors.Bg else AxTvColors.TextSecondary, CircleShape),
        )
    }
}

private fun labelOf(key: String): String = when (key) {
    "settings_account" -> "账号"
    "video_danmaku" -> "视频弹幕"
    "live_danmaku" -> "直播弹幕"
    "live_danmaku_style" -> "直播弹幕样式"
    "danmaku_emoji" -> "弹幕表情"
    "danmaku_blocklist" -> "弹幕屏蔽词"
    "app_font_scale" -> "字体大小"
    "default_home" -> "默认首页"
    "theme_mode" -> "主题模式"
    "player_surface_type" -> "播放器画面类型"
    "surface_buffer_align" -> "Surface 缓冲区对齐"
    "disable_double_back_exit" -> "禁用双击返回退出"
    "about_version" -> "版本"
    else -> key
}

private fun sectionLabel(key: String): String = when (key) {
    "live_resolution_header" -> "直播设置"
    "video_codec_header" -> "视频解码"
    "video_resolution_header" -> "视频清晰度"
    "form_factor_header" -> "显示与字体"
    else -> key
}
