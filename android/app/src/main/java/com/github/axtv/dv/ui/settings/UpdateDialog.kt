/*
 * 设置页「检查更新」对话框。
 *
 * 规格出处：refactor/settings/axtv/settings/UpdateManager.java
 *  - checkForUpdate(false)：CHECK_FAILED / UP_TO_DATE / NO_APK / onUpdateInfo
 *  - downloadAndInstall(info)：DOWNLOADING / DOWNLOAD_DONE / INSTALL_TRIGGERED / INSTALL_FAILED / DOWNLOAD_FAILED
 * 状态文案沿用 UpdateManager 的公开常量（原文中文逐字保留），只额外补充「无 Release」的说明。
 *
 * 更新源当前指向 polite0803/AxTV 的 Release；仓库尚无 Release 时 GitHub 返回 404，
 * UpdateManager 归为 CHECK_FAILED —— 本对话框把该分支显式展示为「无可用 Release / 网络不可用」，
 * 不出现白屏或静默失败。
 */
package com.github.axtv.dv.ui.settings

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Text
import axtv.settings.UpdateManager
import com.github.axtv.dv.ui.AxTvColors
import com.github.axtv.dv.ui.focusRing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private enum class UpdatePhase {
    IDLE,
    CHECKING,
    UP_TO_DATE,
    AVAILABLE,
    DOWNLOADING,
    DOWNLOAD_FAILED,
    INSTALL_FAILED,
    CHECK_FAILED,
    INSTALL_TRIGGERED,
}

private data class UpdateUiState(
    val phase: UpdatePhase = UpdatePhase.IDLE,
    val version: String = "",
    val log: String = "",
    val progress: Float? = null,
    val message: String = "",
)

/**
 * UpdateManager 的会话壳：捕获 Listener 的最后一次状态，供协程返回后判定结果；
 * 进度回调经主线程 Handler 转发给 Compose（UpdateManager 在 IO 线程调用 Listener）。
 */
private class UpdateSession(context: Context, private val progressSink: (Float) -> Unit) {

    @Volatile
    var lastStatus: UpdateManager.Status? = null

    @Volatile
    var lastMessage: String? = null

    val manager: UpdateManager = createUpdateManager(context, object : UpdateManager.Listener {
        override fun onProgress(progress: Float) {
            progressSink(progress)
        }

        override fun onStatus(status: UpdateManager.Status?, message: String?) {
            lastStatus = status
            lastMessage = message
        }
    })

    fun reset() {
        lastStatus = null
        lastMessage = null
    }
}

@Composable
fun UpdateDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val scope = rememberCoroutineScope()
    var ui by remember { mutableStateOf(UpdateUiState()) }
    var pendingInfo by remember { mutableStateOf<UpdateManager.ReleaseInfo?>(null) }

    val currentVersion = remember(context) {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty().ifBlank { "未知" }
    }

    val session = remember(context.applicationContext) {
        UpdateSession(context.applicationContext) { progress ->
            mainHandler.post {
                if (ui.phase == UpdatePhase.DOWNLOADING) {
                    // UpdateManager 在无 Content-Length 时按 MB 递增（会 >1），-1 表示复位；
                    // 这两种值都按「不确定进度」显示，避免误报 100%。
                    ui = ui.copy(progress = progress)
                }
            }
        }
    }

    fun startCheck() {
        session.reset()
        pendingInfo = null
        ui = UpdateUiState(phase = UpdatePhase.CHECKING)
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { session.manager.checkForUpdate(false) }
            }
            val info = result.getOrNull()
            if (info != null) {
                pendingInfo = info
                ui = UpdateUiState(
                    phase = UpdatePhase.AVAILABLE,
                    version = info.versionName(),
                    log = info.updateLog(),
                )
                return@launch
            }
            val extra = result.exceptionOrNull()?.message?.let { "\n异常：$it" }.orEmpty()
            ui = when (session.lastStatus) {
                UpdateManager.Status.UP_TO_DATE -> UpdateUiState(
                    phase = UpdatePhase.UP_TO_DATE,
                    version = currentVersion,
                    message = "${UpdateManager.MSG_UP_TO_DATE}（当前版本 $currentVersion）",
                )

                UpdateManager.Status.NO_APK -> UpdateUiState(
                    phase = UpdatePhase.CHECK_FAILED,
                    message = "检查失败：Release 中没有 .apk 资源（${UpdateManager.MSG_NO_APK}）。$extra",
                )

                else -> UpdateUiState(
                    phase = UpdatePhase.CHECK_FAILED,
                    message = noReleaseMessage() + extra,
                )
            }
        }
    }

    fun startDownload() {
        val info = pendingInfo ?: return
        session.reset()
        ui = ui.copy(phase = UpdatePhase.DOWNLOADING, progress = 0f, message = "")
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { session.manager.downloadAndInstall(info) }
            }
            val triggered = result.getOrDefault(false)
            val errorText = result.exceptionOrNull()?.message
            ui = when {
                triggered -> ui.copy(
                    phase = UpdatePhase.INSTALL_TRIGGERED,
                    progress = null,
                    message = "已唤起系统安装器，请按屏幕提示完成安装；若未弹出，请检查系统的「安装未知应用」权限。",
                )

                session.lastStatus == UpdateManager.Status.INSTALL_FAILED -> ui.copy(
                    phase = UpdatePhase.INSTALL_FAILED,
                    progress = null,
                    message = "安装失败：" + (session.lastMessage ?: UpdateManager.MSG_INSTALL_FAILED),
                )

                else -> ui.copy(
                    phase = UpdatePhase.DOWNLOAD_FAILED,
                    progress = null,
                    message = "下载失败：" + (session.lastMessage ?: errorText ?: "未知原因"),
                )
            }
        }
    }

    val primaryRequester = remember { FocusRequester() }
    LaunchedEffect(ui.phase) {
        withFrameNanos { }
        runCatching { primaryRequester.requestFocus() }
    }

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
                    .width(620.dp)
                    .background(AxTvColors.Surface, RoundedCornerShape(16.dp))
                    .padding(28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "检查更新",
                    color = AxTvColors.TextPrimary,
                    style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold),
                )
                Text(
                    text = "当前版本 $currentVersion · 更新源 ${UpdateManager.RELEASE_API}",
                    color = AxTvColors.TextSecondary,
                    style = TextStyle(fontSize = 12.sp),
                )

                when (ui.phase) {
                    UpdatePhase.IDLE -> BodyText(
                        "点击「开始检查」拉取最新 Release，比较 tag 与当前版本后给出结果。",
                    )

                    UpdatePhase.CHECKING -> BodyText("正在检查更新…")

                    UpdatePhase.UP_TO_DATE -> BodyText(ui.message)

                    UpdatePhase.AVAILABLE -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "发现新版本：${ui.version}",
                            color = AxTvColors.Accent,
                            style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
                        )
                        BodyText("当前版本 $currentVersion")
                        Text(
                            text = ui.log.ifBlank { "没有提供更新日志" },
                            color = AxTvColors.TextSecondary,
                            style = TextStyle(fontSize = 12.sp),
                            maxLines = 6,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    UpdatePhase.DOWNLOADING -> {
                        val percent = ui.progress
                            ?.takeIf { it in 0f..1f }
                            ?.let { (it * 100).roundToInt() }
                        BodyText(
                            if (percent == null) "正在下载更新包，请稍候…"
                            else "正在下载更新包… $percent%（完成后会自动唤起系统安装器）",
                        )
                    }

                    UpdatePhase.DOWNLOAD_FAILED,
                    UpdatePhase.INSTALL_FAILED,
                    UpdatePhase.CHECK_FAILED -> BodyText(ui.message, error = true)

                    UpdatePhase.INSTALL_TRIGGERED -> BodyText(ui.message)
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    when (ui.phase) {
                        UpdatePhase.IDLE -> DialogButton(
                            label = "开始检查",
                            primary = true,
                            modifier = Modifier.focusRequester(primaryRequester),
                            onClick = ::startCheck,
                        )

                        UpdatePhase.CHECKING, UpdatePhase.DOWNLOADING -> DialogButton(
                            label = if (ui.phase == UpdatePhase.CHECKING) "检查中…" else "下载中…",
                            primary = true,
                            enabled = false,
                            modifier = Modifier.focusRequester(primaryRequester),
                            onClick = {},
                        )

                        UpdatePhase.UP_TO_DATE, UpdatePhase.CHECK_FAILED -> DialogButton(
                            label = "重新检查",
                            primary = true,
                            modifier = Modifier.focusRequester(primaryRequester),
                            onClick = ::startCheck,
                        )

                        UpdatePhase.AVAILABLE -> DialogButton(
                            label = "下载并安装",
                            primary = true,
                            modifier = Modifier.focusRequester(primaryRequester),
                            onClick = ::startDownload,
                        )

                        UpdatePhase.DOWNLOAD_FAILED, UpdatePhase.INSTALL_FAILED -> DialogButton(
                            label = "重试下载",
                            primary = true,
                            modifier = Modifier.focusRequester(primaryRequester),
                            onClick = ::startDownload,
                        )

                        UpdatePhase.INSTALL_TRIGGERED -> DialogButton(
                            label = "完成",
                            primary = true,
                            modifier = Modifier.focusRequester(primaryRequester),
                            onClick = onDismiss,
                        )
                    }
                    DialogButton(label = "关闭", onClick = onDismiss)
                }
            }
        }
    }
}

@Composable
private fun BodyText(text: String, error: Boolean = false) {
    Text(
        text = text,
        color = if (error) AxTvColors.AccentWarm else AxTvColors.TextSecondary,
        style = TextStyle(fontSize = 13.sp),
    )
}

@Composable
private fun DialogButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    primary: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    val background = when {
        !enabled -> AxTvColors.SurfaceHigh
        primary -> AxTvColors.Accent.copy(alpha = 0.18f)
        else -> AxTvColors.SurfaceHigh
    }
    Row(
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            // 不可用态仍保留焦点能力，避免焦点在状态切换时悬空；OK 不触发任何动作。
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier.focusable())
            .background(background, RoundedCornerShape(10.dp))
            .focusRing(focused && enabled, RoundedCornerShape(10.dp))
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = when {
                !enabled -> AxTvColors.TextSecondary
                primary -> AxTvColors.Accent
                else -> AxTvColors.TextPrimary
            },
            style = TextStyle(fontSize = 14.sp, fontWeight = if (primary) FontWeight.SemiBold else FontWeight.Normal),
        )
    }
}

private fun noReleaseMessage(): String = buildString {
    append("检查失败：更新源没有可用的 Release，或网络不可用。\n")
    append("更新源：github.com/polite0803/AxTV 的 releases/latest。\n")
    append("若该仓库尚未发布 Release，GitHub 会返回 404，此时属于正常现象；发布 Release 后再重试即可。\n")
    append("UpdateManager 原始提示：${UpdateManager.MSG_CHECK_FAILED}")
}
