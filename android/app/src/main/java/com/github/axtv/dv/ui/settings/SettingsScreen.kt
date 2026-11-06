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

import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.platform.LocalContext
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
import com.github.axtv.dv.panel.PanelRuntime
import com.github.axtv.dv.ui.AxTvColors
import com.github.axtv.dv.ui.focusRing
import com.github.axtv.dv.ui.nav.NavRoutes
import kotlin.math.roundToInt

private const val THEME_PREVIEW_KEY = "theme_preview"

/** 设置页动作行打开的弹层种类。 */
private enum class ActionDialog { ACCOUNTS, CREDENTIALS, CONFIG_IO, QR }

/** 「设置页 GitHub 主页」行打开的地址（与 git remote 同源，本仓库唯一对外主页）。 */
private const val GITHUB_HOME_URL = "https://github.com/polite0803/AxTV"

/** 用系统浏览器打开外链；TV 上没有浏览器时静默失败（不弹 toast，避免误以为成功）。 */
private fun openExternalUrl(context: android.content.Context, url: String) {
    if (url.isBlank()) return
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

/** 只读展示行：显示登录/凭据状态，本来就没有点击动作。 */
private val READONLY_KEYS = setOf(
    "settings_account", "auth_status", "auth_security_status",
    "auth_cookie", "auth_security_payload", "auth_private_key",
)

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

internal val THEME_LABELS = listOf("自动", "浅色", "暗色")
internal val COLOR_STYLE_LABELS = listOf("按场景", "鲜艳", "柔和", "还原")

private fun buildRows(): List<SettingsRow> {
    val rows = ArrayList<SettingsRow>(112)
    for (control in SettingsPageSpec.MAIN_PAGE) {
        // 原版规格里保留这一行（SettingsPageSpec.MAIN_PAGE），但仓库没有本项目群组地址，
        // 按用户要求整行移除；地址来源说明见 src/app/p000/h30.java:200。
        if (control.key() == "about_telegram") continue
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

/** 未接入行的统一副文案：值能落盘但没人读的行一律标这一句。 */
private const val TEXT_UNWIRED = "未接入"

/**
 * 「未接入」键清单：核心层有 getter/setter，但生产代码里没有任何消费点（或本应用根本没有该能力）。
 *
 * 判定来源：逐键 grep 消费点（39 项只写不读 + 15 项无处理）。这些行置灰、不吃点击，
 * 避免出现「按下去值变了、界面也记住了，但行为一样」的假响应。
 * 已接线的项不在此列：video_resolution / video_codec / live_video_resolution / player_surface_type /
 * client_identity_branch / playback_completion / playback_clock / player_progress_bar / clean_pause /
 * video_load_timeout / default_home / show_details_on_start / reverse_profile_playlist /
 * disable_double_back_exit 与全部弹幕滑杆。外观族 theme_mode / app_font_scale / color_style /
 * color_contrast / color_transition / color_source / ambient_backdrop 与 camera 行由
 * ui/appearance/AxAppearance 消费；播放族 pip_enabled / video_start_prefetch / surface_buffer_align /
 * audio_balance / live_danmaku_style / mix_playlist_panel / reverse_navigation / separate_video_fetch
 * 与弹幕包过滤由 ui/player、player/、ui/live 各自消费；账号与配置族 account_management /
 * auth_config_actions / profile（导出导入）/ feed_history_capacity（本地「已看」环形缓冲）/
 * profile_history_tab（个人页历史标签）/ about_qr_links（仓库二维码弹层）/ form_factor（屏幕方向）同轮落地。
 *
 * 仍留在清单里的行不是"没做"，是缺外部条件，逐条给证据：
 * - tv_launcher_channel：原版走 TvContract 写 content://android.media.tv/channel
 *   （src/app/p000/nb5.java:12、g73.java:727）与 preview_program（ob5.java:11、g73.java:749），
 *   依赖 com.android.providers.tv.permission.WRITE_EPG_DATA
 *   （src/resources/AndroidManifest.xml:42）——该权限由系统 TV Provider 授予，side-load 应用拿不到，
 *   原版自己在 SecurityException 时也直接放弃（g73.java:736-741）。
 */
private val UNWIRED_KEYS = setOf(
    // 开关：缺系统授权（WRITE_EPG_DATA 由系统 TV Provider 授予），证据见上面的说明。
    "tv_launcher_channel",
)

/** 动态单选行已全部接通；保留这个清单机制是为了以后出现新未接线项时仍能被显式标出。 */
private val UNWIRED_SELECT_KEYS = emptySet<String>()

private fun isRowEnabled(
    row: SettingsRow,
    liveDanmakuOn: Boolean,
    anyDanmakuOn: Boolean,
): Boolean = when {
    row is ControlRow && row.key in UNWIRED_KEYS -> false
    row is SelectRow && row.binderKey in UNWIRED_SELECT_KEYS -> false
    row.key == "danmaku_layout" -> anyDanmakuOn
    row.key == "live_danmaku_style" -> liveDanmakuOn
    row.key == "danmaku_emoji" -> anyDanmakuOn
    else -> true
}

private fun rowHint(key: String): String? = when {
    key in UNWIRED_KEYS -> TEXT_UNWIRED
    key in READONLY_KEYS -> "只读展示"
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
internal class SliderSpec(
    val min: Float,
    val max: Float,
    val step: Float,
    val formatter: (Float) -> String,
)

internal fun sliderSpec(key: String): SliderSpec = when (key) {
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
fun SettingsScreen(
    modifier: Modifier = Modifier,
    onOpenRoute: (String) -> Unit = {},
) {
    val context = LocalContext.current
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
    // 线 H：检查更新对话框（about_update 行触发；下载/安装全部走 UpdateManager）。
    var showUpdateDialog by remember { mutableStateOf(false) }
    var updateDialogWasOpen by remember { mutableStateOf(false) }
    // 取色 / 预览对话框（camera 行触发）：数据源是 AxAppearance 的运行时取色状态。
    var showPaletteDialog by remember { mutableStateOf(false) }
    // 动作行弹层：账号管理 / 互动凭据 / 配置管理 / 二维码链接。
    var actionDialog by remember { mutableStateOf<ActionDialog?>(null) }
    var actionTriggerKey by remember { mutableStateOf<String?>(null) }
    // readStatus 读的是 SharedPreferences（非快照状态），动作完成后要靠这个版本号重取一次。
    var statusVersion by remember { mutableStateOf(0) }

    val liveDanmakuOn = switchStates[canonicalKey("live_danmaku")] == true
    val anyDanmakuOn = liveDanmakuOn || switchStates["video_danmaku"] == true

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

    LaunchedEffect(actionDialog) {
        if (actionDialog == null) {
            val trigger = actionTriggerKey ?: return@LaunchedEffect
            withFrameNanos { }
            runCatching { focusRequesters[trigger]?.requestFocus() }
            actionTriggerKey = null
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 40.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    text = "设置页",
                    color = AxTvColors.TextPrimary,
                    style = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold),
                )
            }
            items(count = visibleRows.size) { index ->
                val row = visibleRows[index]
                when (row) {
                    is ControlRow -> {
                        val control = row.control
                        val enabled = isRowEnabled(row, liveDanmakuOn, anyDanmakuOn)
                        val hint = rowHint(control.key())
                        SettingItem(
                            control = control,
                            checked = switchStates[canonicalKey(control.key())] == true,
                            enumIndex = enumIndexes[control.key()] ?: 0,
                            enumOptions = binder?.enumLabels(control.key()).orEmpty(),
                            sliderValue = sliderValues[control.key()] ?: 0f,
                            textValue = binder?.readText(control.key()),
                            statusText = remember(control.key(), statusVersion, binder) {
                                binder?.readStatus(control.key())
                            },
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
                            onAction = { key ->
                                when (key) {
                                    "about_update" -> showUpdateDialog = true

                                    // 取色 / 预览：弹出配色检查页（种子、推导梯度、来源）。
                                    "camera" -> showPaletteDialog = true

                                    // 弹幕布局：面板 15 行就在同一列的下方，把焦点送过去即是「进入布局设置」。
                                    "danmaku_layout" -> runCatching {
                                        focusRequesters["style_opacity"]?.requestFocus()
                                    }

                                    "quality" -> runCatching {
                                        focusRequesters["live_resolution_HIGHEST"]?.requestFocus()
                                    }

                                    "style" -> runCatching {
                                        focusRequesters["style_opacity"]?.requestFocus()
                                    }

                                    // 登录方式：本应用的登录入口只有 Login 页（扫码 + Cookie），直接过去。
                                    "auth_login_mode" -> onOpenRoute(NavRoutes.LOGIN)

                                    // GitHub 主页：交给系统浏览器 / TV 上的浏览器（无接收者时静默失败）。
                                    "about_github" -> openExternalUrl(context, GITHUB_HOME_URL)

                                    // 账号管理：列出 Settings.accounts()，可切换 / 删除。
                                    "account_management" -> {
                                        actionTriggerKey = key
                                        actionDialog = ActionDialog.ACCOUNTS
                                    }

                                    // 互动凭据：保存（粘贴解析后 setInteraction）/ 清除（clearInteraction）。
                                    // 手机推送走 9978 面板，已在 AppRuntime 启动时装配（PanelRuntime）。
                                    "auth_config_actions" -> {
                                        actionTriggerKey = key
                                        actionDialog = ActionDialog.CREDENTIALS
                                    }

                                    // 配置管理：整份设置导出到应用私有目录 / 从该目录导入。
                                    "profile" -> {
                                        actionTriggerKey = key
                                        actionDialog = ActionDialog.CONFIG_IO
                                    }

                                    // 二维码链接：本地 zxing 渲染仓库地址并可打开。
                                    "about_qr_links" -> {
                                        actionTriggerKey = key
                                        actionDialog = ActionDialog.QR
                                    }

                                    else -> Unit
                                }
                            },
                        )
                    }

                    is SelectRow -> {
                        val selectEnabled = isRowEnabled(row, liveDanmakuOn, anyDanmakuOn)
                        SelectSettingItem(
                            label = row.label,
                            description = row.description
                                ?: if (!selectEnabled) TEXT_UNWIRED else null,
                            selected = dynamicIndexes[row.binderKey] == row.optionIndex,
                            enabled = selectEnabled,
                            modifier = Modifier.focusRequester(focusRequesters.getValue(row.key)),
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
                    }

                    is ThemePreviewRow -> ThemePreview(
                        themeModeIndex = enumIndexes["theme_mode"] ?: 0,
                        colorStyleIndex = enumIndexes["color_style"] ?: 0,
                    )
                }
            }
            item {
                Text(
                    text = "↑/↓ 移动焦点 · OK 开关/进入 · ←/→ 调值 · BACK 关闭弹层或返回上一页",
                    color = AxTvColors.TextSecondary,
                    style = TextStyle(fontSize = 12.sp),
                )
            }
        }

        if (showUpdateDialog) {
            UpdateDialog(onDismiss = { showUpdateDialog = false })
        }
        if (showPaletteDialog) {
            PalettePreviewDialog(onDismiss = { showPaletteDialog = false })
        }

        val closeActionDialog = {
            actionDialog = null
            statusVersion += 1
        }
        when (actionDialog) {
            ActionDialog.ACCOUNTS -> AccountManagementDialog(
                onChanged = { statusVersion += 1 },
                onDismiss = closeActionDialog,
            )

            ActionDialog.CREDENTIALS -> CredentialActionsDialog(
                onChanged = { statusVersion += 1 },
                onDismiss = closeActionDialog,
            )

            ActionDialog.CONFIG_IO -> ConfigIODialog(
                context = context,
                onDismiss = closeActionDialog,
            )

            ActionDialog.QR -> {
                val panel = PanelRuntime.controller
                QrLinksDialog(
                    links = buildList {
                        add(QrLink("项目地址", GITHUB_HOME_URL, "扫码或按确认键访问项目主页"))
                        if (panel != null) {
                            add(
                                QrLink(
                                    "手机推送登录信息",
                                    panel.cookieSubmitUrl(),
                                    "手机扫码后粘贴 Cookie / 互动凭据",
                                    openable = false,
                                ),
                            )
                            add(
                                QrLink(
                                    "手机搜索输入",
                                    panel.searchPanelUrl(),
                                    "手机扫码打开搜索输入页（需与电视同一局域网）",
                                    openable = false,
                                ),
                            )
                        }
                    },
                    onOpen = { openExternalUrl(context, it) },
                    onDismiss = closeActionDialog,
                )
            }

            null -> Unit
        }
    }
}
