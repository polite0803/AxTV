/*
 * 直播间的浮层组件与播放控制器（从 LiveRoomScreen.kt 拆出）。
 * 拆分原因: LiveRoomScreen.kt 超 800 行；这些组件只被主页面调用，无独立状态。
 */
package com.github.axtv.dv.ui.live

import android.content.Context
import android.graphics.Color as AndroidColor
import android.view.TextureView
import android.view.ViewGroup
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Text
import axtv.model.LiveRoom
import axtv.model.DataModels
import axtv.player.MediaEngine
import axtv.player.PlaybackError
import axtv.player.VideoSize
import axtv.ui.DetailPageSpec
import axtv.ui.LivePageSpec
import axtv.settings.SettingsTypes
import coil.compose.AsyncImage
import com.github.axtv.dv.danmaku.DanmakuConfig
import com.github.axtv.dv.danmaku.DanmakuOverlayController
import com.github.axtv.dv.danmaku.DanmakuOverlayHost
import com.github.axtv.dv.player.Media3Engine
import com.github.axtv.dv.ui.AxTvColors
import com.github.axtv.dv.ui.focusRing
import com.github.axtv.dv.ui.login.qr.findActivity
import kotlinx.coroutines.delay

@Composable
internal fun LiveImmersiveBars() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = view.context.findActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }
}

/** 房间还没拿到时的整屏占位：纯黑 + 一行提示，不留半屏空白。 */
@Composable
internal fun LiveStagePlaceholder(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = message,
            color = Color.White.copy(alpha = 0.72f),
            style = TextStyle(fontSize = 15.sp),
        )
    }
}

/** 首帧前的封面遮罩：绝不把 PlayerView 的黑底直接暴露给用户。 */
@Composable
internal fun LiveCover(coverUrl: String, message: String) {
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
internal fun LivePlayerSurface(
    controller: LivePlaybackController,
    surfaceType: SettingsTypes.SurfaceType,
    aspectRatio: Float,
    fitMode: LiveFitMode,
    modifier: Modifier = Modifier,
) {
    when (surfaceType) {
        SettingsTypes.SurfaceType.TEXTURE_VIEW -> {
            AndroidView(
                modifier = modifier,
                factory = { context ->
                    AspectRatioFrameLayout(context).apply {
                        resizeMode = fitMode.resizeMode
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
                    frame.resizeMode = fitMode.resizeMode
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
                        resizeMode = fitMode.resizeMode
                        setBackgroundColor(AndroidColor.BLACK)
                        keepScreenOn = true
                        isFocusable = false
                        isFocusableInTouchMode = false
                        descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
                        controller.attachSurface(this)
                    }
                },
                update = { view -> view.resizeMode = fitMode.resizeMode },
            )
        }
    }
}

/** 顶部信息层：LIVE 徽标 + 房间标题 + 主播 + 在线人数。 */
@Composable
internal fun LiveTopBar(
    room: LiveRoom?,
    onlineCount: Long?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(Color.Black.copy(alpha = 0.82f), Color.Transparent),
                ),
            )
            .padding(start = 32.dp, end = 32.dp, top = 22.dp, bottom = 34.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LiveRoomBadge(isLive = room?.status() == 2)
            Spacer(Modifier.width(12.dp))
            Text(
                text = room?.title()?.ifBlank { DetailPageSpec.TEXT_NO_PROGRAM } ?: "直播间",
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold),
                modifier = Modifier.weight(1f, fill = false),
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            val avatarUrl = room?.owner()?.bestAvatar()?.firstUrl().orEmpty()
            if (avatarUrl.isNotEmpty()) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape),
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = room?.owner()?.nickname()?.ifBlank { DetailPageSpec.TEXT_CHANNEL_PREFIX + "1" } ?: "—",
                color = Color.White.copy(alpha = 0.9f),
                style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = "${LivePageSpec.LABEL_VIEWERS} · ${
                    onlineCount?.let { DataModels.formatCount(it) }
                        ?: room?.let { LivePageSpec.viewerCount(it) }
                        ?: "—"
                }",
                color = Color.White.copy(alpha = 0.66f),
                style = TextStyle(fontSize = 13.sp),
            )
        }
    }
}

/** 底部信息层：按键提示 + 弹幕状态 + 当前画面模式。 */
@Composable
internal fun LiveBottomBar(
    fitMode: LiveFitMode,
    danmakuState: DanmakuState,
    rollingDanmaku: Boolean,
    danmakuPanelVisible: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.84f)),
                ),
            )
            .padding(start = 32.dp, end = 32.dp, top = 40.dp, bottom = 22.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            text = "←/→ 画面：${fitMode.label}　　" +
                if (rollingDanmaku) {
                    "弹幕：滚动样式　　"
                } else {
                    "↑/↓ 弹幕列表（${if (danmakuPanelVisible) "开" else "关"}）　　"
                } +
                "确认 信息栏　　BACK 返回",
            color = Color.White.copy(alpha = 0.62f),
            style = TextStyle(fontSize = 12.sp),
        )
        Text(
            text = "弹幕 · ${danmakuState.label}",
            color = Color.White.copy(alpha = 0.5f),
            style = TextStyle(fontSize = 12.sp),
        )
    }
}

/** 列表样式弹幕：右侧半透明浮层（滚动样式下不渲染）。 */
@Composable
internal fun LiveDanmakuPanel(
    danmakuState: DanmakuState,
    comments: List<LiveCommentUi>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
            .padding(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "弹幕",
                color = Color.White,
                style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
            )
            Text(
                text = danmakuState.label,
                color = Color.White.copy(alpha = 0.55f),
                style = TextStyle(fontSize = 11.sp),
            )
        }
        Spacer(Modifier.height(8.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Bottom,
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
                    color = Color.White.copy(alpha = 0.6f),
                    style = TextStyle(fontSize = 12.sp),
                )
            } else {
                visible.forEach { comment ->
                    Text(
                        text = "${comment.nickname}：${comment.content}",
                        color = Color.White.copy(alpha = 0.92f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(fontSize = 12.sp),
                    )
                    Spacer(Modifier.height(6.dp))
                }
            }
        }
    }
}

@Composable
internal fun LiveRoomBadge(isLive: Boolean) {
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
internal fun LiveErrorCard(
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

    /** 电视台 HLS：强制 HLS 媒体类型 + 中性请求头（不带抖音 Referer）。 */
    fun playIptv(url: String) {
        playerError = null
        firstFrame = false
        engine.stopAndClear()
        engine.setHlsMediaItem(url)
        engine.prepare()
        engine.play()
    }

    /** 音量（0..1）。电视台播放页用 ←/→ 调节；直播间沿用系统音量，不受影响。 */
    private var volumeLevel = 1f

    fun adjustVolume(delta: Float) {
        volumeLevel = (volumeLevel + delta).coerceIn(0f, 1f)
        engine.setVolume(volumeLevel)
    }

    fun volume(): Float = volumeLevel

    fun attachSurface(host: Any?) = engine.setVideoSurface(host)

    fun release() {
        engine.removeListener(listener)
        engine.setVideoSurface(null)
        engine.release()
    }
}
