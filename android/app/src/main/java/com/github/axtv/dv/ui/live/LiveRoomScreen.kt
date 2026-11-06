/*
 * 线 C —— 直播间页（全屏播放窗口）。
 *
 * 规格出处：
 * - refactor/ui/axtv/ui/LivePageSpec.java（在线人数标签、错误覆盖层）
 * - refactor/ui/axtv/ui/DetailPageSpec.java（LIVE 红点、标题/主播兜底文案）
 * - refactor/live/NOTES.md（enterRoom → stream_url 取流；info_by_scene 兜底）
 * - .../ui/player/PlayerOverlayScreen.kt（全屏播放页的浮层/自动隐藏/按键分发写法，本页对齐同一套语言）
 *
 * 版面（0.2.x 重设计）：
 *   画面 = 满屏铺满（默认「铺满」裁边，←/→ 切「适应」留黑边），不再有固定宽度侧栏挤占画面；
 *   信息 = 覆盖在画面上的两层渐变浮层：顶部（LIVE 徽标 + 标题 + 主播 + 在线人数）、
 *          底部（操作提示 + 弹幕连接状态 + 当前画面模式）；
 *   弹幕 = 「滚动样式」叠在画面上（DanmakuOverlayHost），「列表样式」改为右侧半透明浮层，
 *          可用 ↑/↓ 或 MENU 独立开关，不再长期占据画面宽度；
 *   全屏 = 进入本页隐藏系统栏（沉浸模式），离开时恢复；应用顶栏由 AxTvApp 的 immersive 判定不渲染。
 *
 * 遥控器语义（焦点始终落在画面层；错误卡片出现时把按键交回焦点系统，保证「重试」可达）：
 *   ←/→  切换画面模式（铺满 ↔ 适应）
 *   ↑/↓  开关右侧弹幕列表浮层
 *   确认  信息层显隐
 *   MENU/INFO  开关右侧弹幕列表浮层
 *   BACK  退出房间（直接回直播列表）
 *   信息层隐藏时，←/→/↑/↓/MENU 的第一下只负责把信息层唤出来（不做二次动作）；
 *   无按键 5 秒后信息层自动隐去，出错时保持常显。
 *
 * 取流链路（未改）：
 *   LiveApi.enterRoom(webRid) → LiveRoomEnterResponse.firstRoom() → LiveRoom.streamUrl()
 *   → LiveStreamResolver.resolveWithFallback(..., AUTO) 得到首选地址
 *   → flv/hls 全部取值兜底，失败时自动切下一个地址（只重试一次）→ 仍失败出错误卡片。
 *   没有 webRid 或进房拿不到房间时，退回 LiveApi.roomInfoByScene(roomId)
 *   （scene 固定 aweme_video_feed_pc）。
 *
 * 边界：只读调用 Media3Engine / LiveApi，不修改 player 与核心层。
 */
@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.github.axtv.dv.ui.live

import android.content.Context
import android.graphics.Color as AndroidColor
import android.view.KeyEvent as AndroidKeyEvent
import android.view.TextureView
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Text
import axtv.model.DataModels
import axtv.model.LiveRoom
import axtv.player.MediaEngine
import axtv.player.PlaybackError
import axtv.player.VideoSize
import axtv.settings.Settings
import axtv.settings.SettingsTypes
import axtv.ui.DetailPageSpec
import axtv.ui.LivePageSpec
import coil.compose.AsyncImage
import com.github.axtv.dv.adapter.AppRuntime
import com.github.axtv.dv.danmaku.DanmakuConfig
import com.github.axtv.dv.danmaku.DanmakuOverlayController
import com.github.axtv.dv.danmaku.DanmakuOverlayHost
import com.github.axtv.dv.danmaku.DanmakuSettingsBinder
import com.github.axtv.dv.player.Media3Engine
import com.github.axtv.dv.ui.AxTvColors
import com.github.axtv.dv.ui.focusRing
import com.github.axtv.dv.ui.login.qr.findActivity
import kotlinx.coroutines.delay

/** 弹幕列表最多保留条数（列表样式只展示最近若干条，不做滚动浮层）。 */
internal const val MAX_COMMENTS = 40

/** 弹幕列表一次展示条数。 */
internal const val VISIBLE_COMMENTS = 8

/** 信息层（顶栏 + 底栏）无按键自动隐藏延时。 */
internal const val CHROME_AUTO_HIDE_MS = 5_000L

/** 直播画面的填充方式：铺满（裁边，默认）/ 适应（保留完整画面，可能留黑边）。 */
internal enum class LiveFitMode(val label: String, val resizeMode: Int) {
    FILL("铺满", AspectRatioFrameLayout.RESIZE_MODE_ZOOM),
    FIT("适应", AspectRatioFrameLayout.RESIZE_MODE_FIT),
}

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
    val surfaceType = remember { settings?.surfaceType() ?: SettingsTypes.SurfaceType.SURFACE_VIEW }

    val controller = remember { LivePlaybackController(context) }
    val retryRequester = remember { FocusRequester() }
    val surfaceFocusRequester = remember { FocusRequester() }

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

    // 弹幕样式（设置项 live_danmaku_style，缺省 SCROLL）：滚动 = 录播那套浮层，列表 = 右侧浮层。
    val danmakuStyle = remember(settings) {
        settings?.liveDanmakuStyle() ?: SettingsTypes.DanmakuStyle.SCROLL
    }
    val rollingDanmaku = danmakuStyle == SettingsTypes.DanmakuStyle.SCROLL
    var danmakuController by remember { mutableStateOf<DanmakuOverlayController?>(null) }
    // 直播的总开关是 live_danmaku_enabled（DanmakuSettingsBinder 读的是点播开关），其余样式参数共用。
    val danmakuConfig = remember(settings) {
        val base = settings?.let { DanmakuSettingsBinder(it).read() } ?: DanmakuConfig()
        base.copy(enabled = settings?.liveDanmakuEnabled() == true)
    }

    // 全屏版面状态：信息层显隐、右侧弹幕浮层显隐、画面填充方式。
    var chromeVisible by rememberSaveable { mutableStateOf(true) }
    var chromeWakeAt by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var danmakuPanelVisible by rememberSaveable { mutableStateOf(true) }
    // 画面填充：默认自动 —— 与屏幕同比例（横屏流在 16:9 屏上）就铺满，竖屏流则整屏高居中不裁边。
    // 用户按 ←/→ 后改为显式选择（0 = 铺满，1 = 适应），同一房间内保持。
    var fitOverride by rememberSaveable { mutableStateOf<Int?>(null) }
    val screenAspect = LocalConfiguration.current.let {
        if (it.screenHeightDp == 0) 16f / 9f else it.screenWidthDp.toFloat() / it.screenHeightDp
    }
    val videoAspect = controller.videoSize.aspectRatio()
    // 竖屏流（比例小于屏幕）会被铺满裁掉大半画面，自动降级为「适应」；横屏流铺满几乎不裁切。
    val autoFill = videoAspect > 0f && videoAspect >= screenAspect * 0.98f
    val fillScreen = fitOverride?.let { it == 0 } ?: autoFill
    val fitMode = if (fillScreen) LiveFitMode.FILL else LiveFitMode.FIT

    fun wakeChrome() {
        chromeVisible = true
        chromeWakeAt = System.currentTimeMillis()
    }

    // 进入本页即沉浸：隐藏系统栏（TV 上本就是全屏，主要作用于手机/平板）。
    LiveImmersiveBars()

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
                    android.util.Log.i(
                        "AxTV_LIVE",
                        "room load returned no room: webRid=$webRid roomId=$roomId",
                    )
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
            webRidOverride = webRid,
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

    val overlayMessage = loadError ?: playFailure

    // 出错时信息层常显；其余情况 5 秒无按键自动隐去。
    LaunchedEffect(chromeVisible, chromeWakeAt, overlayMessage) {
        if (!chromeVisible || overlayMessage != null) return@LaunchedEffect
        val remaining = chromeWakeAt + CHROME_AUTO_HIDE_MS - System.currentTimeMillis()
        delay(remaining.coerceAtLeast(0L))
        chromeVisible = false
    }

    // 焦点落点：出错时交给「重试」，否则回到画面层（画面层是唯一接键的地方）。
    LaunchedEffect(overlayMessage) {
        withFrameNanos { }
        runCatching {
            if (overlayMessage != null) retryRequester.requestFocus() else surfaceFocusRequester.requestFocus()
        }
    }

    /** 画面层按键：本页所有版面操作都从这里进（详见文件头语义表）。 */
    fun dispatchLiveKey(event: KeyEvent): Boolean {
        // 错误卡片出现时把按键交回焦点系统，否则方向键/确认键会被画面层吃掉，「重试」永远按不到。
        if (overlayMessage != null) return false
        val native = event.nativeKeyEvent
        val keyCode = native.keyCode
        val isSwitch = keyCode == AndroidKeyEvent.KEYCODE_DPAD_UP || keyCode == AndroidKeyEvent.KEYCODE_DPAD_DOWN
        val isFit = keyCode == AndroidKeyEvent.KEYCODE_DPAD_LEFT || keyCode == AndroidKeyEvent.KEYCODE_DPAD_RIGHT
        val isConfirm = keyCode == AndroidKeyEvent.KEYCODE_DPAD_CENTER ||
            keyCode == AndroidKeyEvent.KEYCODE_ENTER ||
            keyCode == AndroidKeyEvent.KEYCODE_NUMPAD_ENTER
        val isMenu = keyCode == AndroidKeyEvent.KEYCODE_MENU || keyCode == AndroidKeyEvent.KEYCODE_INFO
        if (!isSwitch && !isFit && !isConfirm && !isMenu) return false

        // KeyDown/KeyUp 都要吃掉：漏掉 KeyUp 会让 Compose 的默认焦点搜索替我们挪焦点。
        if (event.type != KeyEventType.KeyDown) return true

        // 信息层隐藏时，第一下只负责唤醒它，不做二次动作（避免看不见状态就切了模式）。
        if (!chromeVisible && !isConfirm) {
            wakeChrome()
            return true
        }

        when {
            isSwitch -> if (!rollingDanmaku) danmakuPanelVisible = !danmakuPanelVisible

            isFit -> fitOverride = if (fillScreen) 1 else 0

            isConfirm -> {
                if (chromeVisible) chromeVisible = false else wakeChrome()
                return true
            }

            isMenu -> if (!rollingDanmaku) danmakuPanelVisible = !danmakuPanelVisible
        }
        wakeChrome()
        return true
    }

    // BACK 直接返回直播列表：本页没有模态面板，信息层与弹幕浮层都是随画面存在/消失的覆盖层，
    // 让 BACK 先「关浮层」再「退页面」只会在遥控器上多按两次才出得去。
    BackHandler(onBack = onBack)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(surfaceFocusRequester)
            .focusable()
            .onPreviewKeyEvent { event -> dispatchLiveKey(event) },
    ) {
        if (room != null) {
            // 不论是否出首帧都先挂上画面宿主，否则 ExoPlayer 不会渲染首帧。
            LivePlayerSurface(
                controller = controller,
                surfaceType = surfaceType,
                aspectRatio = controller.videoSize.aspectRatio(),
                fitMode = fitMode,
                modifier = Modifier.fillMaxSize(),
            )
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
            if (!controller.firstFrame) {
                LiveCover(
                    coverUrl = room?.cover()?.firstUrl().orEmpty(),
                    message = if (playFailure == null) "正在连接直播画面…" else "画面不可用",
                )
            }
        } else {
            LiveStagePlaceholder(if (loading) "正在进入直播间…" else loadError ?: "加载失败")
        }

        // 列表样式弹幕：右侧半透明浮层，可独立开关，不再占据画面宽度。
        if (!rollingDanmaku && danmakuPanelVisible && room != null) {
            LiveDanmakuPanel(
                danmakuState = danmakuState,
                comments = comments,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 28.dp, top = 96.dp, bottom = 96.dp)
                    .width(330.dp)
                    .fillMaxHeight(),
            )
        }

        if (chromeVisible && room != null) {
            LiveTopBar(
                room = room,
                onlineCount = onlineCount,
                modifier = Modifier.align(Alignment.TopStart),
            )
            LiveBottomBar(
                fitMode = fitMode,
                danmakuState = danmakuState,
                rollingDanmaku = rollingDanmaku,
                danmakuPanelVisible = danmakuPanelVisible,
                modifier = Modifier.align(Alignment.BottomStart),
            )
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
}

/**
 * 沉浸模式：隐藏状态栏/导航栏，离开本页恢复。
 *
 * TV 端本来就是全屏，这一步不产生视觉变化；手机/平板上则是「播放窗口真的占满屏幕」的关键，
 * 也是本页从「带顶栏的分栏页」改成播放窗口的前提。
 */
