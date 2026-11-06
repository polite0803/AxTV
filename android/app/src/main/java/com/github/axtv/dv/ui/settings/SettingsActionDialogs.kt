/*
 * 设置页动作行的弹层：账号管理 / 互动凭据 / 配置导出导入 / 二维码链接。
 *
 * 弹层骨架沿用 UpdateDialog.kt（Dialog + usePlatformDefaultWidth = false + 自绘焦点环 +
 * withFrameNanos 抢焦点），文本输入沿用 SearchScreen.kt 的 BasicTextField + 方向键跳焦点写法。
 *
 * 各项动作只调核心层已有 API，不在 UI 侧另存副本：
 *   切换  Settings.switchAccount          refactor/settings/axtv/settings/Settings.java:169
 *   删除  Settings.setAccounts + 登录态收口  同文件 :143
 *   保存  Settings.setInteraction          同文件 :263（凭据文本先过 panel 层解析器）
 *   清除  Settings.clearInteraction        同文件 :272
 *   二维码链接形态参考原版：src/app/p000/h30.java:200（项目地址卡片 + 「扫码或按确认键…」）
 */
package com.github.axtv.dv.ui.settings

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Text
import axtv.panel.InteractionSecurityParser
import axtv.settings.Settings
import axtv.settings.SettingsTypes
import com.github.axtv.dv.adapter.AppRuntime
import com.github.axtv.dv.adapter.SettingsConfigIO
import com.github.axtv.dv.ui.AxTvColors
import com.github.axtv.dv.ui.focusRing
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import java.io.File

/** 二维码弹层的一项（标题 + 地址 + 提示，与原版 gb5 三字段一致）。 */
internal data class QrLink(
    val title: String,
    val url: String,
    val hint: String,
    /** 电视上没有可直接打开该地址的接收者时设为 false（如局域网面板地址）。 */
    val openable: Boolean = true,
)

// ════════════════════════ 二维码链接 ════════════════════════

@Composable
internal fun QrLinksDialog(
    links: List<QrLink>,
    onOpen: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    if (links.isEmpty()) {
        SimpleMessageDialog(title = "二维码链接", message = "还没有可扫码的外部链接。", onDismiss = onDismiss)
        return
    }
    var activeLink by remember { mutableStateOf(links.first()) }
    val requester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { requester.requestFocus() }
    }
    ActionDialogShell(onDismiss = onDismiss) {
        DialogTitle("二维码链接", "手机扫码或直接按确认键打开")
        Box(
            modifier = Modifier
                .size(260.dp)
                .background(Color.White, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            val bitmap = rememberQrBitmap(activeLink.url)
            if (bitmap != null) {
                androidx.compose.foundation.Image(
                    bitmap = bitmap,
                    contentDescription = activeLink.title,
                    modifier = Modifier.size(240.dp),
                )
            } else {
                DialogBody("二维码生成失败")
            }
        }
        DialogBody("${activeLink.title} · ${activeLink.url}", secondary = false)
        DialogBody(activeLink.hint)
        if (links.size > 1) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                links.forEachIndexed { index, link ->
                    DialogChip(
                        label = link.title,
                        primary = false,
                        selected = link == activeLink,
                        modifier = if (index == 0) Modifier.focusRequester(requester) else Modifier,
                        onClick = { activeLink = link },
                    )
                }
            }
        } else {
            DialogChip(label = links.first().title, modifier = Modifier.focusRequester(requester), onClick = {})
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (activeLink.openable) {
                DialogChip(label = "打开链接", primary = true, onClick = { onOpen(activeLink.url) })
            }
            DialogChip(label = "关闭", onClick = onDismiss)
        }
    }
}

/** zxing 本地编码，不依赖任何在线二维码服务；失败返回 null 由界面提示。 */
@Composable
private fun rememberQrBitmap(text: String): ImageBitmap? = remember(text) {
    runCatching { encodeQr(text, 320) }.getOrNull()
}

private fun encodeQr(text: String, size: Int): ImageBitmap {
    val matrix = QRCodeWriter().encode(
        text,
        BarcodeFormat.QR_CODE,
        size,
        size,
        mapOf(EncodeHintType.MARGIN to 1),
    )
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
    for (x in 0 until size) {
        for (y in 0 until size) {
            bitmap.setPixel(x, y, if (matrix.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
        }
    }
    return bitmap.asImageBitmap()
}

// ════════════════════════ 账号管理 ════════════════════════

@Composable
internal fun AccountManagementDialog(
    onChanged: () -> Unit,
    onDismiss: () -> Unit,
) {
    var version by remember { mutableStateOf(0) }
    val accounts = remember(version) { AppRuntime.settings?.accounts().orEmpty() }
    val currentId = remember(version) { AppRuntime.settings?.currentAccountId().orEmpty() }
    var pendingDelete by remember { mutableStateOf<String?>(null) }
    val firstRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { firstRequester.requestFocus() }
    }

    ActionDialogShell(onDismiss = onDismiss) {
        DialogTitle("账号管理", "${accounts.size} 个账号 · 当前 ${currentId.ifBlank { "无" }}")
        if (accounts.isEmpty()) {
            DialogBody("还没有已保存的账号。登录一次后核心层会把它写进 accounts_list。")
        }
        accounts.forEachIndexed { index, account ->
            val isCurrent = account.id() == currentId
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = account.id() + if (isCurrent) "（当前）" else "",
                        color = if (isCurrent) AxTvColors.Accent else AxTvColors.TextPrimary,
                        style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                    )
                    Text(
                        text = "凭据 ${if (account.tsSign().isBlank()) "无" else "有"} · PIN ${
                            if (account.hasPin()) "已设" else "未设"
                        }",
                        color = AxTvColors.TextSecondary,
                        style = TextStyle(fontSize = 11.sp),
                    )
                }
                DialogChip(
                    label = "切换",
                    primary = !isCurrent,
                    enabled = !isCurrent,
                    modifier = if (index == 0) Modifier.focusRequester(firstRequester) else Modifier,
                    onClick = {
                        AppRuntime.settings?.switchAccount(account.id())
                        onChanged()
                        version += 1
                    },
                )
                DialogChip(label = "删除", onClick = { pendingDelete = account.id() })
            }
        }
        pendingDelete?.let { id ->
            DialogBody("删除账号「$id」会同时清掉它的登录凭据，无法撤销。", secondary = false)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DialogChip(
                    label = "确认删除",
                    primary = true,
                    onClick = {
                        deleteAccount(id)
                        pendingDelete = null
                        onChanged()
                        version += 1
                    },
                )
                DialogChip(label = "取消", onClick = { pendingDelete = null })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DialogChip(label = "关闭", onClick = onDismiss)
        }
    }
}

/**
 * 删除一个账号条目（核心层没有单条删除 API，只有整体替换 setAccounts）。
 *
 * 删的是当前账号时顺手收口登录态：还有别的账号就切到第一个，否则清 Cookie 与互动凭据 ——
 * 留着旧 Cookie 会出现「账号列表里没有了但仍然是登录态」的不一致。
 */
private fun deleteAccount(accountId: String) {
    val settings = AppRuntime.settings ?: return
    val remaining = settings.accounts().filterNot { it.id() == accountId }
    settings.setAccounts(remaining)
    if (settings.currentAccountId() != accountId) return
    if (remaining.isNotEmpty()) {
        settings.switchAccount(remaining.first().id())
    } else {
        settings.setCurrentAccountId("")
        settings.setCookie("")
        // 同步清运行时 CookieStore（douyin_auth/cookie_header），否则删除当前账号后
        // 请求仍带着旧 cookie（LibraryCommon.kt:75 缺口的反方向遗留）。
        AppRuntime.syncCookieToRuntimeStore(LocalContext.current)
        settings.clearInteraction()
    }
}

// ════════════════════════ 互动凭据：保存 / 清除 ════════════════════════════════

@Composable
internal fun CredentialActionsDialog(
    onChanged: () -> Unit,
    onDismiss: () -> Unit,
) {
    var hasInteraction by remember {
        mutableStateOf(AppRuntime.settings?.hasInteraction() == true)
    }
    var payload by remember { mutableStateOf("") }
    var privateKey by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    val payloadFocus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    /** 唯一的保存路径：输入框回车与『保存』按钮共用，避免两处校验漂移。 */
    val save: () -> Unit = {
        val settings = AppRuntime.settings
        if (settings == null) {
            message = "配置层尚未装配，无法保存。"
        } else {
            val config = InteractionSecurityParser.parse(payload, privateKey)
            if (!config.isComplete) {
                message = "没有解析出 ticket / ts_sign / private_key，未写入。"
            } else {
                settings.setInteraction(
                    config.rawPayload,
                    SettingsTypes.Interaction(
                        config.rawPayload,
                        config.ticket,
                        config.tsSign,
                        config.privateKey,
                        config.serverCert,
                    ),
                )
                message = "已保存：ticket ${config.ticket.take(8)}…"
                hasInteraction = settings.hasInteraction()
                payload = ""
                privateKey = ""
                onChanged()
            }
        }
    }

    ActionDialogShell(onDismiss = onDismiss) {
        DialogTitle(
            "互动凭据",
            if (hasInteraction) "已就绪（ticket / ts_sign / private key 齐全）" else "未设置",
        )
        DialogBody("粘贴 security-sdk / web_protect 导出的 JSON、key=value 文本或 PEM 私钥，保存前会自动提取 ticket 与 ts_sign。")
        PasteField(
            value = payload,
            onValueChange = { payload = it },
            placeholder = "security_payload 原文",
            requester = payloadFocus,
            onSubmit = { focusManager.moveFocus(FocusDirection.Down) },
        )
        PasteField(
            value = privateKey,
            onValueChange = { privateKey = it },
            placeholder = "private_key（可留空，原文里已含 PEM 时不再需要）",
            onSubmit = save,
        )
        message?.let { DialogBody(it, secondary = false) }
        if (confirmClear) {
            DialogBody("清除后点赞 / 收藏等互动会退回未授权状态。", secondary = false)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DialogChip(
                    label = "确认清除",
                    primary = true,
                    onClick = {
                        AppRuntime.settings?.clearInteraction()
                        hasInteraction = false
                        confirmClear = false
                        message = "已清除互动凭据。"
                        onChanged()
                    },
                )
                DialogChip(label = "取消", onClick = { confirmClear = false })
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DialogChip(label = "保存", primary = true, onClick = save)
                DialogChip(
                    label = "清除",
                    enabled = hasInteraction,
                    onClick = { confirmClear = true },
                )
                DialogChip(label = "关闭", onClick = onDismiss)
            }
        }
    }
}

// ════════════════════════ 配置管理：导出 / 导入 ════════════════════════

@Composable
internal fun ConfigIODialog(
    context: Context,
    onDismiss: () -> Unit,
) {
    var message by remember { mutableStateOf<String?>(null) }
    var exports by remember { mutableStateOf(SettingsConfigIO.existingExports(context)) }
    var pendingImport by remember { mutableStateOf<File?>(null) }

    ActionDialogShell(onDismiss = onDismiss) {
        DialogTitle("配置管理", "导出 / 导入整份配置（含账号与凭据，请按机密处理）")
        DialogBody("落点：${SettingsConfigIO.targetDir(context).absolutePath}")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DialogChip(
                label = "导出",
                primary = true,
                onClick = {
                    message = runCatching { "已导出：${SettingsConfigIO.export(context).name}" }
                        .fold({ it }, { "导出失败：${it.message}" })
                    exports = SettingsConfigIO.existingExports(context)
                },
            )
            DialogChip(
                label = "导入",
                enabled = exports.isNotEmpty(),
                onClick = {
                    val first = exports.firstOrNull()
                    if (first == null) {
                        message = "还没有可导入的导出包。"
                    } else {
                        pendingImport = first
                    }
                },
            )
            DialogChip(label = "关闭", onClick = onDismiss)
        }
        if (exports.isEmpty()) {
            DialogBody("当前没有导出包。")
        } else {
            DialogBody("可导入 ${exports.size} 个：${exports.joinToString("、") { it.name }.take(120)}")
        }
        message?.let { DialogBody(it, secondary = false) }
        pendingImport?.let { file ->
            DialogBody("导入「${file.name}」会覆盖同名配置项（文件里没有的键保持不变），确认继续？", secondary = false)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DialogChip(
                    label = "确认导入",
                    primary = true,
                    onClick = {
                        message = runCatching {
                            val count = SettingsConfigIO.import(context, file)
                            // 导入后重建 Settings：核心层缓存了 accounts_list，旧实例读不到新账号表。
                            AppRuntime.refreshSettings(context)
                            // 设置仓库的 cookie 同步进运行时 CookieStore（douyin_auth），
                            // 否则搜索等请求仍读不到登录态（LibraryCommon.kt:75 缺口）。
                            AppRuntime.syncCookieToRuntimeStore(context)
                            "已导入 $count 项配置。"
                        }.fold({ it }, { "导入失败：${it.message}" })
                        pendingImport = null
                    },
                )
                DialogChip(label = "取消", onClick = { pendingImport = null })
            }
        }
    }
}

// ════════════════════════ 共用弹层骨架 ════════════════════════

@Composable
private fun SimpleMessageDialog(title: String, message: String, onDismiss: () -> Unit) {
    val requester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { requester.requestFocus() }
    }
    ActionDialogShell(onDismiss = onDismiss) {
        DialogTitle(title, null)
        DialogBody(message)
        DialogChip(label = "关闭", primary = true, modifier = Modifier.focusRequester(requester), onClick = onDismiss)
    }
}

@Composable
private fun ActionDialogShell(
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color(0xE6000000)),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .width(680.dp)
                    .background(AxTvColors.Surface, RoundedCornerShape(16.dp))
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = content,
            )
        }
    }
}

@Composable
private fun DialogTitle(title: String, subtitle: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = title,
            color = AxTvColors.TextPrimary,
            style = TextStyle(fontSize = 21.sp, fontWeight = FontWeight.Bold),
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                color = AxTvColors.TextSecondary,
                style = TextStyle(fontSize = 12.sp),
            )
        }
    }
}

@Composable
private fun DialogBody(text: String, secondary: Boolean = true) {
    Text(
        text = text,
        color = if (secondary) AxTvColors.TextSecondary else AxTvColors.AccentWarm,
        style = TextStyle(fontSize = if (secondary) 12.sp else 13.sp),
    )
}

@Composable
private fun DialogChip(
    label: String,
    primary: Boolean = false,
    selected: Boolean = false,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val background = when {
        !enabled -> AxTvColors.SurfaceHigh.copy(alpha = 0.4f)
        primary || selected -> AxTvColors.Accent.copy(alpha = 0.22f)
        else -> AxTvColors.SurfaceHigh
    }
    Box(
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .background(background, RoundedCornerShape(10.dp))
            .focusRing(focused, RoundedCornerShape(10.dp))
            .padding(horizontal = 18.dp, vertical = 10.dp),
    ) {
        Text(
            text = label,
            color = if (enabled) AxTvColors.TextPrimary else AxTvColors.TextSecondary,
            style = TextStyle(
                fontSize = 14.sp,
                fontWeight = if (primary) FontWeight.SemiBold else FontWeight.Normal,
            ),
        )
    }
}

/** 粘贴框：单行 + 系统键盘（TV 上走 IME），Enter 提交，上下键在两个字段间跳焦点。 */
@Composable
private fun PasteField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    requester: FocusRequester? = null,
    onSubmit: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (requester != null) Modifier.focusRequester(requester) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when {
                    event.key == Key.DirectionDown -> focusManager.moveFocus(FocusDirection.Down)
                    event.key == Key.DirectionUp -> focusManager.moveFocus(FocusDirection.Up)
                    event.key == Key.Enter -> {
                        onSubmit()
                        true
                    }

                    else -> false
                }
            }
            .background(AxTvColors.SurfaceHigh, RoundedCornerShape(10.dp))
            .focusRing(focused, RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        singleLine = true,
        textStyle = TextStyle(color = AxTvColors.TextPrimary, fontSize = 14.sp),
        cursorBrush = SolidColor(AxTvColors.Accent),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        keyboardActions = KeyboardActions(onNext = { onSubmit() }),
        decorationBox = { inner ->
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        color = AxTvColors.TextSecondary,
                        style = TextStyle(fontSize = 13.sp),
                    )
                }
                inner()
            }
        },
    )
}
