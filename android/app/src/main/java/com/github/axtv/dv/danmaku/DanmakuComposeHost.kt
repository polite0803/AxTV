/*
 * Compose 侧的弹幕宿主与最小发送入口。
 *
 * 只提供「可被播放器线直接叠上去」的组件，不修改 ui/player 包：
 * - [DanmakuOverlayHost]：把 [DanmakuOverlayView] 包进 AndroidView，负责 attach/detach 与配置同步；
 * - [DanmakuSendBar]：最小可用的本地发送入口（占位，无后端）。
 *
 * 播放器线接入方式：
 * <pre>
 * DanmakuOverlayHost(
 *     source = media3Source,
 *     comments = comments,
 *     config = DanmakuSettingsBinder(settings).read(),
 *     modifier = Modifier.fillMaxSize(),
 * )
 * </pre>
 */
package com.github.axtv.dv.danmaku

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.tv.material3.Text

/**
 * 弹幕浮层宿主。
 *
 * @param source   播放器数据源；null 时浮层保持静止（用于播放器尚未接线的联调阶段）
 * @param comments 弹幕时间轴（可分批传入）
 * @param config   设置快照，一般由 [DanmakuSettingsBinder.read] 提供
 * @param onControllerReady 拿到控制面后，可用于挂 [DanmakuSendBar] 或本地发送
 */
@Composable
fun DanmakuOverlayHost(
    source: DanmakuPlaybackSource?,
    comments: List<DanmakuComment>,
    config: DanmakuConfig,
    modifier: Modifier = Modifier,
    onControllerReady: (DanmakuOverlayController) -> Unit = {},
) {
    val context = LocalContext.current
    val overlay = remember { DanmakuOverlayView(context) }

    LaunchedEffect(overlay, comments) { overlay.setComments(comments) }
    LaunchedEffect(overlay, config) { overlay.setConfig(config) }
    LaunchedEffect(overlay, source) { overlay.attach(source) }
    LaunchedEffect(overlay) { onControllerReady(overlay) }
    DisposableEffect(overlay) {
        onDispose { overlay.detach() }
    }

    AndroidView(factory = { overlay }, modifier = modifier)
}

/**
 * 最小发送入口（占位）。
 *
 * 当前没有写接口，[DanmakuOverlayController.sendLocal] 只在本地入队并立即上屏；
 * 后端发送（`/aweme/v1/web/danmaku/get_v2/` 对应写接口尚未确认）留待后续。
 */
@Composable
fun DanmakuSendBar(
    controller: DanmakuOverlayController?,
    modifier: Modifier = Modifier,
    hint: String = "发个弹幕（本地占位，未接后端）",
) {
    var draft by remember { mutableStateOf("") }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = draft,
            onValueChange = { draft = it },
            singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
            modifier = Modifier.weight(1f),
            decorationBox = { innerTextField ->
                Box {
                    if (draft.isEmpty()) {
                        Text(
                            text = hint,
                            color = Color.White.copy(alpha = 0.45f),
                            style = TextStyle(fontSize = 13.sp),
                        )
                    }
                    innerTextField()
                }
            },
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = "发送",
            color = if (draft.isNotBlank()) DanmakuUiAccent else Color.White.copy(alpha = 0.35f),
            style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold),
            modifier = Modifier
                .clickable(enabled = draft.isNotBlank()) {
                    val text = draft.trim()
                    if (text.isNotEmpty()) {
                        controller?.sendLocal(text)
                        draft = ""
                    }
                }
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

/** 弹幕发送栏强调色（res/ 不可改，按规格在代码里定义常量）。 */
private val DanmakuUiAccent = Color(0xFF6BD5FF)
