/*
 * 样板页 3：播放器浮层（画面 = Media3 PlayerView，浮层 = Compose）。
 * 规格出处：refactor/ui/NOTES-player-ui.md（lq2.java 菜单装配）、
 *           refactor/ui/axtv/ui/PlayerMenuSpec.java（文案/倍速/画质/行规格）、
 *           PlayerUiPalette.java（角色 → 用色）。
 *
 * 播放链路：ExoPlayer（Media3Engine）← PlayerEngine（状态机）← PlayerSession ← 本页；
 * 数据源由调用方通过 PlaylistProvider 注入，本页不直接访问 API。
 */
package com.github.axtv.dv.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.tv.material3.Text
import axtv.player.PlaybackError
import axtv.player.PlayerState
import axtv.settings.Settings
import axtv.ui.PlayerMenuSpec
import com.github.axtv.dv.adapter.AppRuntime
import com.github.axtv.dv.danmaku.DanmakuComment
import com.github.axtv.dv.danmaku.DanmakuConfig
import com.github.axtv.dv.danmaku.DanmakuOverlayHost
import com.github.axtv.dv.ui.AxTvColors
import com.github.axtv.dv.ui.focusRing
import com.github.axtv.dv.widget.WidgetPlaybackItem
import com.github.axtv.dv.widget.WidgetRuntime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.abs

@Composable
fun PlayerOverlayScreen(
    modifier: Modifier = Modifier,
    playlistProvider: PlaylistProvider? = null,
    danmakuComments: List<DanmakuComment> = emptyList(),
    /**
     * 弹幕数据源注入点：非空时按当前播放条目重新拉取并替换 [danmakuComments]。
     *
     * 缺省（null 且 [danmakuComments] 为空）时走内置的「点播评论 → 弹幕」链路
     * （[loadCommentDanmakuComments]）；评论接口失败 / 未登录会静默返回空表，不影响播放。
     * 需要换用 `VideoApi.danmaku`（录播弹幕接口）时，显式传
     * `{ itemId, durationMs -> loadDanmakuComments(AppRuntime.api, itemId, durationMs) }`。
     */
    danmakuCommentsLoader: (suspend (itemId: String, durationMs: Long) -> List<DanmakuComment>)? = null,
    danmakuConfig: DanmakuConfig = rememberDanmakuConfig(),
) {
    val context = LocalContext.current
    val settings = AppRuntime.settings
    val provider = playlistProvider ?: remember { demoPlaylistProvider() }
    val surfaceType = remember(settings) { settings?.surfaceType() ?: Settings.SurfaceType.SURFACE_VIEW }
    val initialSpeed = remember(settings) { settings?.playbackSpeed() ?: 1.0f }
    val completionAction = remember(settings) {
        settings?.completionAction() ?: Settings.CompletionAction.AUTO_NEXT
    }

    val session = remember(provider, settings) {
        PlayerSession(
            context = context,
            provider = provider,
            initialSpeed = initialSpeed,
            completionAction = completionAction,
            persistSpeed = { settings?.setPlaybackSpeed(it) },
        )
    }

    // 起播一次；表面挂载晚于起播时由 PlayerSession 补挂。
    LaunchedEffect(session) { session.start(0) }

    // 进度条轮询（ExoPlayer 的 position 不是 Compose 状态）。
    LaunchedEffect(session) {
        while (true) {
            session.refreshProgress()
            delay(500L)
        }
    }

    // 弹幕注入：显式 loader 优先；否则在未提供静态弹幕时默认走「点播评论 → 弹幕」。
    // 拉取失败静默回落空列表（弹幕缺失不影响播放），与原播放器对弹幕失败的处理一致。
    // 用 rememberUpdatedState 持有 lambda，避免调用方每次重组传入新实例时触发重复拉取。
    val activeDanmakuLoader: (suspend (itemId: String, durationMs: Long) -> List<DanmakuComment>)? =
        when {
            danmakuCommentsLoader != null -> danmakuCommentsLoader
            danmakuComments.isNotEmpty() -> null
            else -> defaultCommentDanmakuLoader
        }
    val currentItemId = provider.items().getOrNull(session.playlistIndex)?.id
    // 桌面「正在播放」小组件：只上报当前条目的元数据，不影响播放/换档逻辑。
    val currentItem = provider.items().getOrNull(session.playlistIndex)
    LaunchedEffect(
        currentItem?.id,
        currentItem?.title,
        currentItem?.author,
        currentItem?.coverUrl,
        session.isPlaying,
    ) {
        val item = currentItem ?: return@LaunchedEffect
        WidgetRuntime.publishPlayback(
            context,
            WidgetPlaybackItem(
                id = item.id,
                title = item.title,
                author = item.author,
                isPlaying = session.isPlaying,
                isLive = false,
                source = "本机播放",
                coverUrl = item.coverUrl,
            ),
        )
    }
    val latestDanmakuLoader = rememberUpdatedState(activeDanmakuLoader)
    var loadedDanmakuComments by remember { mutableStateOf(emptyList<DanmakuComment>()) }
    LaunchedEffect(currentItemId, session.durationMs, activeDanmakuLoader != null) {
        val loader = latestDanmakuLoader.value
        // 切条目 / 时长变化时先清空旧时间轴，避免上一条评论落在新视频的时间轴上。
        loadedDanmakuComments = emptyList()
        if (loader == null || currentItemId == null) {
            return@LaunchedEffect
        }
        // 弹幕总开关关闭时不发起评论请求（渲染层也会清屏，见 DanmakuEngine）。
        if (!danmakuConfig.enabled) {
            return@LaunchedEffect
        }
        loadedDanmakuComments = try {
            loader(currentItemId, session.durationMs)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            emptyList()
        }
    }
    val overlayDanmakuComments =
        if (activeDanmakuLoader == null) danmakuComments else loadedDanmakuComments

    // 生命周期：ON_STOP 暂停、ON_START 恢复；离开页面时释放 ExoPlayer。
    val lifecycleOwner = context as? LifecycleOwner
    DisposableEffect(lifecycleOwner, session) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> session.onHostStop()
                Lifecycle.Event.ON_START -> session.onHostStart()
                else -> Unit
            }
        }
        lifecycleOwner?.lifecycle?.addObserver(observer)
        onDispose {
            lifecycleOwner?.lifecycle?.removeObserver(observer)
            session.release()
        }
    }

    var menuOpen by rememberSaveable { mutableStateOf(false) }
    val menuTriggerRequester = remember { FocusRequester() }
    val firstMenuItemRequester = remember { FocusRequester() }

    // 菜单开合时把焦点交给对应区域；下一帧再请求，避免节点尚未 attach。
    LaunchedEffect(menuOpen) {
        withFrameNanos { }
        runCatching {
            if (menuOpen) firstMenuItemRequester.requestFocus() else menuTriggerRequester.requestFocus()
        }
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        PlayerSurface(
            session = session,
            surfaceType = surfaceType,
            modifier = Modifier.fillMaxSize(),
        )

        // 弹幕层夹在画面与浮层之间：数据源走 PlayerSession.danmakuSource（位置/播放态/seek/尺寸）。
        DanmakuOverlayHost(
            source = session.danmakuSource,
            comments = overlayDanmakuComments,
            config = danmakuConfig,
            modifier = Modifier.fillMaxSize(),
        )

        Column(modifier = Modifier.fillMaxSize()) {
            PlayerHeader(session, surfaceType)

            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    session.error != null -> PlayerErrorCard(
                        error = session.error!!,
                        onRetry = session::retry,
                        onNext = { session.playNext() },
                    )

                    session.retrying -> PlayerHint(
                        text = if (session.retryAttempt > 0) {
                            "重试中（第 ${session.retryAttempt} 次）…"
                        } else {
                            "正在切换备用地址…"
                        },
                    )

                    !session.firstFrameRendered && session.state != PlayerState.UNPREPARED &&
                        session.state != PlayerState.ENDED && session.state != PlayerState.ERROR ->
                        PlayerHint(text = "加载中…")

                    !session.isPlaying && session.firstFrameRendered -> PlayerHint(text = "已暂停")
                }
            }

            PlayerBottomBar(session, menuTriggerRequester, onOpenMenu = { menuOpen = true })
        }

        if (menuOpen) {
            PlayerMenuPanel(
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                firstRequester = firstMenuItemRequester,
                qualityTracks = session.qualityTracks.ifEmpty {
                    listOf(QualityTrack(PlayerMenuSpec.QUALITY_OPTIONS.first().label(), ""))
                },
                selectedQuality = session.qualityIndex,
                onQualitySelected = session::selectQuality,
                speed = session.speed,
                onSpeedSelected = session::applySpeed,
                onClose = { menuOpen = false },
            )
        }
    }
}

@Composable
private fun PlayerHeader(session: PlayerSession, surfaceType: Settings.SurfaceType) {
    val surfaceLabel = when (surfaceType) {
        Settings.SurfaceType.SURFACE_VIEW -> "SurfaceView"
        Settings.SurfaceType.TEXTURE_VIEW -> "TextureView"
        Settings.SurfaceType.GL_SURFACE_VIEW -> "GL（暂回退 SurfaceView）"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.45f))
            .padding(horizontal = 32.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = session.currentTitle.ifEmpty { "播放器浮层" },
            color = AxTvColors.TextPrimary,
            style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = "第 ${session.playlistIndex + 1}/${session.itemCount} 条 · 画面：$surfaceLabel",
            color = AxTvColors.TextSecondary,
            style = TextStyle(fontSize = 13.sp),
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = "${formatTime(session.positionMs)} / ${formatTime(session.durationMs)}",
            color = AxTvColors.TextSecondary,
            style = TextStyle(fontSize = 13.sp),
        )
    }
}

@Composable
private fun PlayerHint(text: String) {
    Box(
        modifier = Modifier
            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Text(text = text, color = Color.White.copy(alpha = 0.85f), style = TextStyle(fontSize = 16.sp))
    }
}

@Composable
private fun PlayerErrorCard(
    error: PlaybackError,
    onRetry: () -> Unit,
    onNext: () -> Unit,
) {
    Column(
        modifier = Modifier
            .background(AxTvColors.SurfaceHigh, RoundedCornerShape(12.dp))
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "播放失败",
            color = AxTvColors.TextPrimary,
            style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold),
        )
        Text(
            text = "${error.message()}（code=${error.errorCode()}）",
            color = AxTvColors.TextSecondary,
            style = TextStyle(fontSize = 14.sp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OverlayAction(label = "重试", onClick = onRetry)
            OverlayAction(label = "下一个", onClick = onNext)
        }
    }
}

@Composable
private fun PlayerBottomBar(
    session: PlayerSession,
    menuTriggerRequester: FocusRequester,
    onOpenMenu: () -> Unit,
) {
    val progress = if (session.durationMs > 0L) {
        (session.positionMs.toFloat() / session.durationMs).coerceIn(0f, 1f)
    } else {
        0f
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 32.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(AxTvColors.Divider, RoundedCornerShape(2.dp)),
        ) {
            if (progress > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .height(4.dp)
                        .background(AxTvColors.Accent, RoundedCornerShape(2.dp)),
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OverlayAction(
                label = if (session.playingIntent) "暂停" else "播放",
                modifier = Modifier.focusRequester(menuTriggerRequester),
                onClick = session::togglePlayPause,
            )
            OverlayAction(label = PlayerMenuSpec.TEXT_MENU_TITLE, onClick = onOpenMenu)
            OverlayAction(label = PlayerMenuSpec.LABEL_DANMAKU, onClick = {})
            OverlayAction(label = PlayerMenuSpec.LABEL_QUALITY, onClick = onOpenMenu)
            OverlayAction(label = PlayerMenuSpec.LABEL_SPEED, onClick = onOpenMenu)
        }
    }
}

@Composable
private fun OverlayAction(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .background(AxTvColors.Surface, RoundedCornerShape(8.dp))
            .focusRing(focused, RoundedCornerShape(8.dp))
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            color = if (focused) AxTvColors.TextPrimary else AxTvColors.TextSecondary,
            style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
        )
    }
}

@Composable
private fun PlayerMenuPanel(
    modifier: Modifier = Modifier,
    firstRequester: FocusRequester,
    qualityTracks: List<QualityTrack>,
    selectedQuality: Int,
    onQualitySelected: (Int) -> Unit,
    speed: Float,
    onSpeedSelected: (Float) -> Unit,
    onClose: () -> Unit,
) {
    var danmakuOn by rememberSaveable { mutableStateOf(true) }

    Column(
        modifier = modifier
            .width(360.dp)
            .background(AxTvColors.SurfaceHigh, RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp))
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = PlayerMenuSpec.TEXT_MENU_TITLE,
            color = AxTvColors.TextPrimary,
            style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold),
        )

        Text(
            text = PlayerMenuSpec.LABEL_QUALITY,
            color = AxTvColors.TextSecondary,
            style = TextStyle(fontSize = 13.sp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            qualityTracks.forEachIndexed { index, track ->
                SegmentChip(
                    label = track.label,
                    selected = index == selectedQuality,
                    modifier = if (index == 0) Modifier.focusRequester(firstRequester) else Modifier,
                    onClick = { onQualitySelected(index) },
                )
            }
        }

        Text(
            text = PlayerMenuSpec.LABEL_SPEED,
            color = AxTvColors.TextSecondary,
            style = TextStyle(fontSize = 13.sp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PlayerMenuSpec.SPEED_OPTIONS.forEach { option ->
                SegmentChip(
                    label = PlayerMenuSpec.speedLabel(option),
                    selected = abs(option - speed) < 0.01f,
                    onClick = { onSpeedSelected(option) },
                )
            }
        }

        MenuRow(
            label = PlayerMenuSpec.LABEL_DANMAKU,
            value = if (danmakuOn) "开启" else "关闭",
            onClick = { danmakuOn = !danmakuOn },
        )
        MenuRow(label = PlayerMenuSpec.LABEL_BLOCK_WORDS, value = "0 条", onClick = {})
        MenuRow(label = PlayerMenuSpec.LABEL_FULL_DESCRIPTION, value = "", onClick = {})
        MenuRow(label = PlayerMenuSpec.LABEL_BACK, value = "", onClick = onClose)

        Text(
            text = PlayerMenuSpec.TEXT_SLIDER_HINT,
            color = AxTvColors.TextSecondary,
            style = TextStyle(fontSize = 12.sp),
        )
    }
}

@Composable
private fun SegmentChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .background(
                color = if (selected) AxTvColors.Accent.copy(alpha = 0.2f) else AxTvColors.Surface,
                shape = RoundedCornerShape(8.dp),
            )
            .focusRing(focused, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text = label,
            color = if (selected || focused) AxTvColors.TextPrimary else AxTvColors.TextSecondary,
            style = TextStyle(fontSize = 13.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal),
        )
    }
}

@Composable
private fun MenuRow(label: String, value: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .background(AxTvColors.Surface, RoundedCornerShape(8.dp))
            .focusRing(focused, RoundedCornerShape(8.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = if (focused) AxTvColors.TextPrimary else AxTvColors.TextSecondary,
            style = TextStyle(fontSize = 14.sp),
        )
        Spacer(Modifier.weight(1f))
        if (value.isNotEmpty()) {
            Text(text = value, color = AxTvColors.Accent, style = TextStyle(fontSize = 13.sp))
        }
    }
}

/**
 * 内置弹幕数据源：点播评论 → 弹幕。
 *
 * 走 [loadCommentDanmakuComments]（InteractionApi.comments → CommentDanmakuMapper）；
 * 失败 / 未登录返回空表。调用方未显式注入 loader 且没有静态弹幕时自动使用。
 */
private val defaultCommentDanmakuLoader: suspend (itemId: String, durationMs: Long) -> List<DanmakuComment> =
    { itemId, durationMs -> loadCommentDanmakuComments(AppRuntime.api, itemId, durationMs) }

private fun formatTime(millis: Long): String {
    val totalSeconds = (millis / 1000L).coerceAtLeast(0L)
    return String.format(Locale.US, "%02d:%02d", totalSeconds / 60L, totalSeconds % 60L)
}
