/*
 * 线 C —— 直播间页。
 *
 * 规格出处：
 * - refactor/ui/axtv/ui/LivePageSpec.java（在线人数标签、错误覆盖层）
 * - refactor/ui/axtv/ui/DetailPageSpec.java（LIVE 红点、标题/主播兜底文案）
 * - refactor/live/NOTES.md（enterRoom → stream_url 取流；info_by_scene 兜底）
 * - android/app/src/main/java/com/github/axtv/dv/player/Media3Engine.kt（复用公开接口）
 * - .../ui/player/PlayerSurface.kt（SURFACE_VIEW / TEXTURE_VIEW 两种宿主写法，本文件就地复刻）
 *
 * 取流链路：
 *   LiveApi.enterRoom(webRid) → LiveRoomEnterResponse.firstRoom() → LiveRoom.streamUrl()
 *   → LiveStreamResolver.resolveWithFallback(..., AUTO) 得到首选地址
 *   → flv/hls 全部取值兜底，失败时自动切下一个地址（只重试一次）→ 仍失败出错误卡片。
 *   没有 webRid 或进房拿不到房间时，退回 LiveApi.roomInfoByScene(roomId)
 *   （scene 固定 aweme_video_feed_pc）。
 *
 * 弹幕：设置项 live_danmaku_style 二选一 ——
 *   「滚动样式」复用录播的 DanmakuOverlayHost（danmaku/ 包，滚动渲染器），
 *   「列表样式」用右侧的 LiveDanmakuFeed 列表。两种样式都走同一条 WebSocket 订阅。
 *
 * 边界：只读调用 Media3Engine / LiveApi，不修改 player 与核心层。
 */
package com.github.axtv.dv.ui.live

import android.content.Context
import android.graphics.Color as AndroidColor
import android.view.TextureView
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Text
import axtv.model.DataModels
import axtv.model.LiveRoom
import axtv.player.MediaEngine
import axtv.player.PlaybackError
import axtv.player.VideoSize
import axtv.settings.Settings
import axtv.ui.DetailPageSpec
import axtv.ui.LivePageSpec
import coil.compose.AsyncImage
import com.github.axtv.dv.adapter.AppRuntime
import com.github.axtv.dv.player.Media3Engine
import com.github.axtv.dv.danmaku.DanmakuConfig
import com.github.axtv.dv.danmaku.DanmakuOverlayController
import com.github.axtv.dv.danmaku.DanmakuOverlayHost
import com.github.axtv.dv.danmaku.DanmakuSettingsBinder
import com.github.axtv.dv.ui.AxTvColors
import com.github.axtv.dv.ui.focusRing

/** 弹幕列表最多保留条数（列表样式只展示最近若干条，不做滚动浮层）。 */
private const val MAX_COMMENTS = 40

/** 弹幕列表一次展示条数。 */
private const val VISIBLE_COMMENTS = 8

/** 直播间页固定签名（供导航接线）。 */
@Composable
fun LiveRoomScreen(
    roomId: String,
    webRid: String = "",
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val settings = remember { AppRuntime.settings }
    val branch = remember { settings?.clientIdentityBranch() }
    val surfaceType = remember { settings?.surfaceType() ?: Settings.SurfaceType.SURFACE_VIEW }

    val controller = remember { LivePlaybackController(context) }
    val retryRequester = remember { FocusRequester() }

    var room by remember { mutableStateOf<LiveRoom?>(null) }
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var playFailure by remember { mutableStateOf<String?>(null) }
    var candidates by remember { mutableStateOf<List<String>>(emptyList()) }
    var usedBackup by remember { mutableStateOf(false) }
    var reloadToken by remember { mutableIntStateOf(0) }

    var comments by remember { mutableStateOf<List<LiveCommentUi>>(emptyList()) }
    var onlineCount by remember { mutableStateOf<Long?>(null) }
    var danmakuState by remember { mutableStateOf(DanmakuState.CONNECTING) }

    // 弹幕样式（设置项 live_danmaku_style，缺省 SCROLL）：滚动 = 录播那套浮层，列表 = 右侧列表。
    val danmakuStyle = remember(settings) {
        settings?.liveDanmakuStyle() ?: Settings.DanmakuStyle.SCROLL
    }
    val rollingDanmaku = danmakuStyle == Settings.DanmakuStyle.SCROLL
    var danmakuController by remember { mutableStateOf<DanmakuOverlayController?>(null) }
    // 直播的总开关是 live_danmaku_enabled（DanmakuSettingsBinder 读的是点播开关），其余样式参数共用。
    val danmakuConfig = remember(settings) {
        val base = settings?.let { DanmakuSettingsBinder(it).read() } ?: DanmakuConfig()
        base.copy(enabled = settings?.liveDanmakuEnabled() == true)
    }

    // 进入房间：有 webRid 时先走 enterRoom，info_by_scene 兜底（原版 op2 的顺序）。
    LaunchedEffect(roomId, webRid, reloadToken) {
        loading = true
        loadError = null
        playFailure = null
        candidates = emptyList()
        usedBackup = false
        room = null
        comments = emptyList()
        onlineCount = null
        danmakuState = DanmakuState.CONNECTING
        LiveRepository.room(roomId, webRid)
            .onSuccess { loaded ->
                if (loaded == null) {
                    loadError = "未取到直播间信息（房间可能已下播或已失效）"
                } else {
                    room = loaded
                    val urls = loaded.streamCandidates()
                    candidates = urls
                    if (urls.isEmpty()) {
                        playFailure = "该房间没有返回可用推流地址（可能未开播）"
                    } else {
                        controller.play(urls.first())
                    }
                }
            }
            .onFailure { loadError = describeLiveError(it, branch) }
        loading = false
    }

    // 首个推流地址失败时切备用地址，只重试一次；再失败就落到错误卡片。
    LaunchedEffect(controller.playerError) {
        val error = controller.playerError ?: return@LaunchedEffect
        if (!usedBackup && candidates.size > 1) {
            usedBackup = true
            controller.play(candidates[1])
        } else {
            playFailure = error.message()
        }
    }

    // 弹幕：两种样式共用同一条订阅；离开页面时必须关闭 WS 与 sign.js 运行时。
    DisposableEffect(room, rollingDanmaku) {
        val loaded = room
        if (loaded == null) {
            return@DisposableEffect onDispose { }
        }
        val feed = LiveDanmakuFeed(
            context = context,
            room = loaded,
            onComment = { comment ->
                val line = "${comment.nickname}：${comment.content}"
                if (rollingDanmaku) {
                    // 滚动样式：走 sendLocal 立即上屏（条目时间轴会跳过"时间已到"的实时条目）。
                    danmakuController?.sendLocal(line)
                } else {
                    comments = (comments + comment).takeLast(MAX_COMMENTS)
                }
            },
            onUserSeq = { total -> onlineCount = total },
            onState = { state -> danmakuState = state },
        )
        feed.start()
        onDispose { feed.stop() }
    }

    DisposableEffect(Unit) {
        onDispose { controller.release() }
    }

    // 错误卡片出现时把焦点交给「重试」。
    val overlayMessage = loadError ?: playFailure
    LaunchedEffect(overlayMessage) {
        if (overlayMessage != null) {
            withFrameNanos { }
            runCatching { retryRequester.requestFocus() }
        }
    }

    BackHandler(onBack = onBack)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AxTvColors.Bg)
            .padding(horizontal = 28.dp, vertical = 20.dp),
    ) {
        Text(
            text = "直播中 · ${room?.effectiveWebRid().orEmpty().ifBlank { roomId }}",
            color = AxTvColors.TextSecondary,
            style = TextStyle(fontSize = 13.sp),
        )
        Spacer(Modifier.height(10.dp))
        Row(modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.Black),
            ) {
                when {
                    loading -> LiveVideoPlaceholder("正在进入直播间…")
                    room == null -> LiveVideoPlaceholder(loadError ?: "加载失败")
                    else -> {
                        // 不论是否出首帧都先挂上画面宿主，否则 ExoPlayer 不会渲染首帧。
                        LivePlayerSurface(
                            controller = controller,
                            surfaceType = surfaceType,
                            aspectRatio = controller.videoSize.aspectRatio(),
                            modifier = Modifier.fillMaxSize(),
                        )
                        if (!controller.firstFrame) {
                            LiveCover(
                                coverUrl = room?.cover()?.firstUrl().orEmpty(),
                                message = if (playFailure == null) "正在连接直播画面…" else "画面不可用",
                            )
                        }
                        // 滚动样式：叠一层录播浮层，弹幕由 LiveDanmakuFeed 回调经 sendLocal 上屏。
                        // 直播没有时间轴，source 传 null（浮层只跑本地入队的条目）。
                        if (rollingDanmaku) {
                            DanmakuOverlayHost(
                                source = null,
                                comments = emptyList(),
                                config = danmakuConfig,
                                modifier = Modifier.fillMaxSize(),
                                onControllerReady = { danmakuController = it },
                            )
                        }
                    }
                }
                if (overlayMessage != null) {
                    LiveErrorCard(
                        message = overlayMessage,
                        retryRequester = retryRequester,
                        onRetry = {
                            if (candidates.isNotEmpty()) {
                                usedBackup = false
                                playFailure = null
                                controller.play(candidates.first())
                            } else {
                                reloadToken += 1
                            }
                        },
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            LiveSidePanel(
                room = room,
                onlineCount = onlineCount,
                danmakuState = danmakuState,
                comments = comments,
                modifier = Modifier
                    .width(340.dp)
                    .fillMaxHeight(),
            )
        }
    }
}

@Composable
private fun LiveVideoPlaceholder(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = message,
            color = AxTvColors.TextSecondary,
            style = TextStyle(fontSize = 15.sp),
        )
    }
}

/** 首帧前的封面遮罩：绝不把 PlayerView 的黑底直接暴露给用户。 */
@Composable
private fun LiveCover(coverUrl: String, message: String) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (coverUrl.isNotEmpty()) {
            AsyncImage(
                model = coverUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f)),
        )
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = message,
                color = Color.White,
                style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
            )
            Text(
                text = "推流地址已取到，等待首帧渲染",
                color = Color.White.copy(alpha = 0.72f),
                style = TextStyle(fontSize = 12.sp),
            )
        }
    }
}

@Composable
private fun LivePlayerSurface(
    controller: LivePlaybackController,
    surfaceType: Settings.SurfaceType,
    aspectRatio: Float,
    modifier: Modifier = Modifier,
) {
    when (surfaceType) {
        Settings.SurfaceType.TEXTURE_VIEW -> {
            AndroidView(
                modifier = modifier,
                factory = { context ->
                    AspectRatioFrameLayout(context).apply {
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        setBackgroundColor(AndroidColor.BLACK)
                        val textureView = TextureView(context)
                        addView(
                            textureView,
                            ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            ),
                        )
                        controller.attachSurface(textureView)
                    }
                },
                update = { frame ->
                    if (aspectRatio > 0f) {
                        frame.setAspectRatio(aspectRatio)
                    }
                },
            )
        }

        // GL_SURFACE_VIEW 暂与 SURFACE_VIEW 同路径（GL 直通属播放器线，见 refactor/player/NOTES.md）。
        else -> {
            AndroidView(
                modifier = modifier,
                factory = { context ->
                    PlayerView(context).apply {
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        setBackgroundColor(AndroidColor.BLACK)
                        keepScreenOn = true
                        isFocusable = false
                        isFocusableInTouchMode = false
                        descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
                        controller.attachSurface(this)
                    }
                },
            )
        }
    }
}

@Composable
private fun LiveSidePanel(
    room: LiveRoom?,
    onlineCount: Long?,
    danmakuState: DanmakuState,
    comments: List<LiveCommentUi>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(AxTvColors.Surface, RoundedCornerShape(14.dp))
            .padding(16.dp),
    ) {
        Text(
            text = room?.title()?.ifBlank { DetailPageSpec.TEXT_NO_PROGRAM } ?: "直播间",
            color = AxTvColors.TextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold),
        )
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            val avatarUrl = room?.owner()?.bestAvatar()?.firstUrl().orEmpty()
            if (avatarUrl.isNotEmpty()) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape),
                )
                Spacer(Modifier.width(10.dp))
            }
            Column {
                Text(
                    text = room?.owner()?.nickname()?.ifBlank { DetailPageSpec.TEXT_CHANNEL_PREFIX + "1" } ?: "—",
                    color = AxTvColors.TextPrimary,
                    style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                )
                Text(
                    text = room?.owner()?.signature().orEmpty().ifBlank { "暂无简介" },
                    color = AxTvColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(fontSize = 11.sp),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            LiveRoomBadge(isLive = room?.status() == 2)
            Text(
                text = "${LivePageSpec.LABEL_VIEWERS} · ${
                    onlineCount?.let { DataModels.formatCount(it) }
                        ?: room?.let { LivePageSpec.viewerCount(it) }
                        ?: "—"
                }",
                color = AxTvColors.TextSecondary,
                style = TextStyle(fontSize = 12.sp),
            )
        }
        Spacer(Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "弹幕（列表）",
                color = AxTvColors.TextPrimary,
                style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
            )
            Text(
                text = danmakuState.label,
                color = AxTvColors.TextSecondary,
                style = TextStyle(fontSize = 11.sp),
            )
        }
        Spacer(Modifier.height(8.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val visible = comments.takeLast(VISIBLE_COMMENTS)
            if (visible.isEmpty()) {
                Text(
                    text = when (danmakuState) {
                        DanmakuState.DISABLED -> "弹幕已在设置中关闭"
                        DanmakuState.FAILED -> "弹幕连接失败；画面不受影响"
                        DanmakuState.UNAVAILABLE -> "该房间暂不可订阅弹幕"
                        else -> "等待弹幕…"
                    },
                    color = AxTvColors.TextSecondary,
                    style = TextStyle(fontSize = 12.sp),
                )
            } else {
                visible.forEach { comment ->
                    Text(
                        text = "${comment.nickname}：${comment.content}",
                        color = AxTvColors.TextPrimary.copy(alpha = 0.92f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(fontSize = 12.sp),
                    )
                }
            }
        }
        Text(
            text = "按 BACK 返回直播列表",
            color = AxTvColors.TextSecondary,
            style = TextStyle(fontSize = 11.sp),
        )
    }
}

@Composable
private fun LiveRoomBadge(isLive: Boolean) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (isLive) AxTvColors.AccentWarm else AxTvColors.SurfaceHigh)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (isLive) {
            Box(
                modifier = Modifier
                    .size(DetailPageSpec.LIVE_DOT_SIZE_DP.dp)
                    .clip(CircleShape)
                    .background(Color.White),
            )
        }
        Text(
            text = if (isLive) "LIVE" else "未开播",
            color = Color.White,
            style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold),
        )
    }
}

@Composable
private fun LiveErrorCard(
    message: String,
    retryRequester: FocusRequester,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier = modifier
            .width(420.dp)
            .background(AxTvColors.SurfaceHigh, RoundedCornerShape(14.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "直播加载失败",
            color = AxTvColors.TextPrimary,
            style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Bold),
        )
        Text(
            text = message,
            color = AxTvColors.TextSecondary,
            style = TextStyle(fontSize = 13.sp),
        )
        Box(
            modifier = Modifier
                .focusRequester(retryRequester)
                .onFocusChanged { focused = it.isFocused }
                .clickable(onClick = onRetry)
                .background(AxTvColors.Accent.copy(alpha = 0.18f), RoundedCornerShape(8.dp))
                .focusRing(focused, RoundedCornerShape(8.dp))
                .padding(horizontal = 18.dp, vertical = 10.dp),
        ) {
            Text(
                text = "重试",
                color = AxTvColors.TextPrimary,
                style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
            )
        }
    }
}

/**
 * 直播间播放控制器。
 *
 * 直接使用播放器线的公开实现 Media3Engine（不修改其源码），只维护本页需要的
 * 首帧 / 错误 / 视频尺寸三个观察值；地址切换与重试策略在页面层决定。
 */
internal class LivePlaybackController(context: Context) {
    private val engine = Media3Engine(context.applicationContext)

    var playerError by mutableStateOf<PlaybackError?>(null)
        private set
    var firstFrame by mutableStateOf(false)
        private set
    var videoSize by mutableStateOf(VideoSize.UNKNOWN)
        private set

    private val listener = object : MediaEngine.Listener {
        override fun onPlaybackStateChanged(state: Int) = Unit

        override fun onIsPlayingChanged(isPlaying: Boolean) = Unit

        override fun onPlayerError(error: PlaybackError) {
            playerError = error
        }

        override fun onRenderedFirstFrame() {
            firstFrame = true
        }

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            this@LivePlaybackController.videoSize = videoSize
        }
    }

    init {
        engine.addListener(listener)
    }

    fun play(url: String) {
        playerError = null
        firstFrame = false
        engine.stopAndClear()
        engine.setMediaItem(url, false)
        engine.prepare()
        engine.play()
    }

    fun attachSurface(host: Any?) = engine.setVideoSurface(host)

    fun release() {
        engine.removeListener(listener)
        engine.setVideoSurface(null)
        engine.release()
    }
}
