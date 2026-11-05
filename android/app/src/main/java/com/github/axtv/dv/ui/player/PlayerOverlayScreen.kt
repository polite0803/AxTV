/*
 * 样板页 3：播放器浮层（画面 = Media3 PlayerView，浮层 = Compose）。
 * 规格出处：refactor/ui/NOTES-player-ui.md（lq2.java 菜单装配）、
 *           refactor/ui/axtv/ui/PlayerMenuSpec.java（文案/倍速/画质/行规格）、
 *           PlayerUiPalette.java（角色 → 用色）。
 *
 * 播放链路：ExoPlayer（Media3Engine）← PlayerEngine（状态机）← PlayerSession ← 本页；
 * 数据源由调用方通过 PlaylistProvider 注入，本页不直接访问 API。
 *
 * 全屏：无按键 4 秒后顶部标题与底部按钮一起隐去（应用顶栏在播放页不渲染，见 AxTvApp）。
 * 方向键与确认键始终由画面层接收（不让底部按钮抢焦点，否则控制层一隐藏焦点就落空）：
 * 上下换视频、左右 ±10s 微调、确认播放/暂停、菜单键开播放面板，任意按键唤醒控制层，
 * BACK 第一次只唤醒控制层。语义出处 refactor/app/p000/on5.java 的 r13 tap map
 * （21/22 → seek、19/20 → 换条）与 PlayerSession.handleRemoteKey。
 */
package com.github.axtv.dv.ui.player

import android.view.KeyEvent as AndroidKeyEvent
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import com.github.axtv.dv.ui.collection.CollectionEpisode
import com.github.axtv.dv.ui.focusRing
import com.github.axtv.dv.widget.WidgetPlaybackItem
import com.github.axtv.dv.widget.WidgetRuntime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs

/** 控制层（顶部标题 + 底部按钮）无操作自动隐藏延时。 */
private const val CONTROLS_AUTO_HIDE_MS = 4_000L

/** 播放时钟取时间隔（整点 / 半点模式最多滞后一分钟）。 */
private const val CLOCK_TICK_MS = 15_000L

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
    /**
     * 合集分集列表（设置项 mix_playlist_panel 开启且当前条目属于合集时由调用方注入）。
     *
     * 空表 = 不属于合集，浮层不出现「合集」入口。选中某集后由 [onMixEpisodeSelected] 回调，
     * 调用方负责把播放切到那一集（本应用在 ui/nav/PlayerFromMixScreen 内换 provider）。
     */
    mixEpisodes: List<CollectionEpisode> = emptyList(),
    /** 当前集 id；用于分集面板的选中态。 */
    mixCurrentEpisodeId: String = "",
    onMixEpisodeSelected: (CollectionEpisode) -> Unit = {},
) {
    val context = LocalContext.current
    val settings = AppRuntime.settings
    val provider = playlistProvider ?: remember { demoPlaylistProvider() }
    val surfaceType = remember(settings) { settings?.surfaceType() ?: Settings.SurfaceType.SURFACE_VIEW }
    val bufferAlign = remember(settings) { settings?.surfaceBufferAlign() == true }
    val initialSpeed = remember(settings) { settings?.playbackSpeed() ?: 1.0f }
    val completionAction = remember(settings) {
        settings?.completionAction() ?: Settings.CompletionAction.AUTO_NEXT
    }
    // 设置页三项：暂停时是否隐藏控件 / 底部是否画进度条 / 播放时钟显示模式。
    val cleanPause = remember(settings) { settings?.cleanPause() ?: false }
    val showProgressBar = remember(settings) { settings?.playerProgressBar() ?: false }
    val clockMode = remember(settings) { settings?.playbackClock() ?: Settings.PlaybackClock.NEVER }
    val clockText = rememberPlayerClock(clockMode)

    // 设置项 mix_playlist_panel 的消费点：关闭时连入口都不出现（而不是点了没反应）。
    val visibleMixEpisodes = if (settings?.mixPlaylistPanelEnabled() == true) mixEpisodes else emptyList()

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
    var mixPanelOpen by rememberSaveable { mutableStateOf(false) }
    val menuTriggerRequester = remember { FocusRequester() }
    val firstMenuItemRequester = remember { FocusRequester() }
    val surfaceFocusRequester = remember { FocusRequester() }
    val firstEpisodeRequester = remember { FocusRequester() }

    /**
     * 控制层（顶部标题 + 底部按钮）显隐：无按键 [CONTROLS_AUTO_HIDE_MS] 后隐藏，
     * 任意遥控器按键唤醒。
     *
     * 保持显示的条件：菜单打开、播放出错，或「暂停中且未开启设置项『纯净暂停』」。
     */
    var chromeVisible by rememberSaveable { mutableStateOf(true) }
    var chromeWakeAt by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val latestMenuOpen by rememberUpdatedState(menuOpen)
    val latestMixPanelOpen by rememberUpdatedState(mixPanelOpen)

    /**
     * 左右键微调给出的一次性进度条反馈：『播放器进度条』默认关，
     * 但按左右键时必须看到进度条，控制层隐去后收回。
     */
    var seekFeedback by remember { mutableStateOf(false) }

    /** 「纯净暂停」关闭时，暂停要把控制层留住；开启时暂停也保持全屏纯净画面。 */
    val keepChromeWhilePaused = !session.isPlaying && !cleanPause

    fun wakeChrome() {
        chromeVisible = true
        chromeWakeAt = System.currentTimeMillis()
    }

    /**
     * 遥控器键分发（画面层始终持有焦点，对应原版 r13 的 tap map 绑在播放器节点上）。
     *
     * - 左/右 → ±[PlayerSession.SEEK_STEP_MS] 微调，长按连发（原版 ro5 即连发语义）；
     * - 上/下 → 上一条 / 下一条，只认首次按下（连发会一路跳过 N 条）；
     * - 确认 → 播放/暂停；菜单键 → 打开播放菜单；
     * - 其余键不认，交回系统（BACK 由 BackHandler 处理）。
     *
     * 控制层只是"看得见的一层"，不参与焦点导航：底部按钮保留指针点击，方向键不会被它们吃掉。
     * 菜单面板打开时整层让位，面板内部自己走焦点导航。
     * 每条按键都会唤醒控制层（标准 TV 播放器语义）。
     */
    fun dispatchPlayerKey(event: KeyEvent): Boolean {
        if (latestMenuOpen || latestMixPanelOpen) return false
        if (event.type != KeyEventType.KeyDown) return false
        // 用 nativeKeyEvent.keyCode：Compose 的 Key 对 TV 方向键解析不出稳定的 keycode（实测拿到 Unknown/0）。
        val keyCode = event.nativeKeyEvent.keyCode
        val isSwitch = keyCode == AndroidKeyEvent.KEYCODE_DPAD_UP || keyCode == AndroidKeyEvent.KEYCODE_DPAD_DOWN
        val isSeek = keyCode == AndroidKeyEvent.KEYCODE_DPAD_LEFT || keyCode == AndroidKeyEvent.KEYCODE_DPAD_RIGHT
        val isConfirm = keyCode == AndroidKeyEvent.KEYCODE_DPAD_CENTER ||
            keyCode == AndroidKeyEvent.KEYCODE_ENTER ||
            keyCode == AndroidKeyEvent.KEYCODE_NUMPAD_ENTER
        val isOpenMenu = keyCode == AndroidKeyEvent.KEYCODE_MENU || keyCode == AndroidKeyEvent.KEYCODE_INFO
        if (!isSwitch && !isSeek && !isConfirm && !isOpenMenu) return false
        val suppressedByRepeat = isSwitch && event.nativeKeyEvent.repeatCount > 0
        if (!suppressedByRepeat) {
            when {
                isConfirm -> session.togglePlayPause()
                isOpenMenu -> menuOpen = true
                else -> {
                    // 微调时把进度条拉出来（『播放器进度条』默认关，seek 是一次性反馈）。
                    if (isSeek) seekFeedback = true
                    session.handleRemoteKey(keyCode)
                }
            }
        }
        wakeChrome()
        return true
    }

    // 画面层持有焦点：进入播放页先抢一次，面板关闭后收回来（否则按键会落进焦点空洞）。
    LaunchedEffect(menuOpen, mixPanelOpen) {
        withFrameNanos { }
        runCatching {
            when {
                menuOpen -> firstMenuItemRequester.requestFocus()
                mixPanelOpen -> firstEpisodeRequester.requestFocus()
                else -> surfaceFocusRequester.requestFocus()
            }
        }
    }

    // 自动隐藏倒计时：每次按键（chromeWakeAt）与播放态变化都会重启。
    LaunchedEffect(
        chromeVisible,
        chromeWakeAt,
        menuOpen,
        mixPanelOpen,
        keepChromeWhilePaused,
        session.error,
    ) {
        if (!chromeVisible || menuOpen || mixPanelOpen || session.error != null || keepChromeWhilePaused) {
            return@LaunchedEffect
        }
        val remaining = chromeWakeAt + CONTROLS_AUTO_HIDE_MS - System.currentTimeMillis()
        delay(remaining.coerceAtLeast(0L))
        chromeVisible = false
        seekFeedback = false
    }

    // 切条目也算一次交互（连播时标题短暂浮现，与原版换条后重置控制层一致）。
    LaunchedEffect(session.playlistIndex) { wakeChrome() }

    // 控制层隐藏时 BACK 先唤出控制层，再按一次才退出播放页。
    BackHandler(enabled = !chromeVisible && !menuOpen && !mixPanelOpen) { wakeChrome() }
    // 分集面板打开时 BACK 只收面板。
    BackHandler(enabled = mixPanelOpen) { mixPanelOpen = false }

    // 画中画：小窗里只保留画面，浮层/弹幕/面板全部让位（PIP 态由 PipCoordinator 写回，见 MainActivity）。
    val inPip = PipCoordinator.inPipMode

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(surfaceFocusRequester)
            .focusable()
            .onPreviewKeyEvent { event -> dispatchPlayerKey(event) },
    ) {
        PlayerSurface(
            session = session,
            surfaceType = surfaceType,
            surfaceBufferAlign = bufferAlign,
            modifier = Modifier.fillMaxSize(),
        )

        // 弹幕层夹在画面与浮层之间：数据源走 PlayerSession.danmakuSource（位置/播放态/seek/尺寸）。
        if (!inPip) {
            DanmakuOverlayHost(
                source = session.danmakuSource,
                comments = overlayDanmakuComments,
                config = danmakuConfig,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Column(modifier = Modifier.fillMaxSize()) {
            if (chromeVisible && !inPip) PlayerHeader(session, clockText)

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

                    !cleanPause && !session.isPlaying && session.firstFrameRendered && !inPip ->
                        PlayerHint(text = "已暂停")
                }
            }

            if (chromeVisible && !inPip) {
                PlayerBottomBar(
                    session = session,
                    menuTriggerRequester = menuTriggerRequester,
                    showProgress = showProgressBar || seekFeedback,
                    episodeCount = visibleMixEpisodes.size,
                    onOpenMenu = { menuOpen = true },
                    onOpenEpisodes = { mixPanelOpen = true },
                )
            }
        }

        // 时钟：控制层可见时随标题栏显示，隐藏时浮在右上角（设置项『播放时钟』的唯一落点）。
        if (!chromeVisible && !inPip && clockText != null) {
            Text(
                text = clockText,
                color = Color.White.copy(alpha = 0.82f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 20.dp, end = 32.dp),
                style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium),
            )
        }

        if (menuOpen && !inPip) {
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

        if (mixPanelOpen && !inPip && visibleMixEpisodes.isNotEmpty()) {
            MixEpisodePanel(
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                firstRequester = firstEpisodeRequester,
                episodes = visibleMixEpisodes,
                currentEpisodeId = mixCurrentEpisodeId,
                onEpisodeSelected = { episode ->
                    mixPanelOpen = false
                    onMixEpisodeSelected(episode)
                },
                onClose = { mixPanelOpen = false },
            )
        }
    }
}

@Composable
private fun PlayerHeader(
    session: PlayerSession,
    clockText: String?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.45f))
            .padding(horizontal = 32.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 标题吃掉剩余宽度并省略号收尾：抖音标题普遍很长，不加权重要把条数/时钟/进度挤出屏幕。
        Text(
            text = session.currentTitle.ifEmpty { "播放器浮层" },
            color = AxTvColors.TextPrimary,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = "第 ${session.playlistIndex + 1}/${session.itemCount} 条",
            color = AxTvColors.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(fontSize = 13.sp),
        )
        Spacer(Modifier.width(12.dp))
        if (clockText != null) {
            Text(
                text = clockText,
                color = AxTvColors.TextSecondary,
                maxLines = 1,
                style = TextStyle(fontSize = 13.sp),
            )
            Spacer(Modifier.width(12.dp))
        }
        Text(
            text = "${formatTime(session.positionMs)} / ${formatTime(session.durationMs)}",
            color = AxTvColors.TextSecondary,
            maxLines = 1,
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
    showProgress: Boolean,
    episodeCount: Int,
    onOpenMenu: () -> Unit,
    onOpenEpisodes: () -> Unit,
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
        if (showProgress) {
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
            // 合集播放列表面板入口（设置项 mix_playlist_panel + 当前条目属于合集时才出现）。
            if (episodeCount > 0) {
                OverlayAction(label = "合集（$episodeCount 集）", onClick = onOpenEpisodes)
            }
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

/**
 * 合集分集面板（设置项 mix_playlist_panel 的界面落点）。
 *
 * 数据由调用方注入（ui/nav/PlayerFromMixScreen 走 CollectionRepository），
 * 本页只负责展示与"选哪一集"，不直接访问接口。
 */
@Composable
private fun MixEpisodePanel(
    modifier: Modifier = Modifier,
    firstRequester: FocusRequester,
    episodes: List<CollectionEpisode>,
    currentEpisodeId: String,
    onEpisodeSelected: (CollectionEpisode) -> Unit,
    onClose: () -> Unit,
) {
    val currentIndex = episodes.indexOfFirst { it.awemeId == currentEpisodeId }
    Column(
        modifier = modifier
            .width(400.dp)
            .background(AxTvColors.SurfaceHigh, RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp))
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "合集播放列表",
            color = AxTvColors.TextPrimary,
            style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold),
        )
        Text(
            text = if (currentIndex >= 0) {
                "共 ${episodes.size} 集 · 当前第 ${episodes[currentIndex].number} 集"
            } else {
                "共 ${episodes.size} 集"
            },
            color = AxTvColors.TextSecondary,
            style = TextStyle(fontSize = 13.sp),
        )
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(episodes, key = { it.awemeId }) { episode ->
                EpisodeRow(
                    label = "第 ${episode.number} 集 · ${episode.title}",
                    selected = episode.awemeId == currentEpisodeId,
                    modifier = if (episode === episodes.first()) {
                        Modifier.focusRequester(firstRequester)
                    } else {
                        Modifier
                    },
                    onClick = { onEpisodeSelected(episode) },
                )
            }
        }
        MenuRow(label = "关闭", value = "", onClick = onClose)
    }
}

@Composable
private fun EpisodeRow(
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
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(
            text = label,
            color = if (selected || focused) AxTvColors.TextPrimary else AxTvColors.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(
                fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            ),
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

/**
 * 播放时钟（设置页『播放时钟』的唯一消费点）。
 *
 * 模式语义：ALWAYS 常显、HOURLY 只在整点显示、HOURLY_AND_HALF 在整点与半点显示、NEVER 不显示。
 * 每 [CLOCK_TICK_MS] 取一次时间，保证整点 / 半点后的一分钟内出现，不必逐秒刷新。
 * 不显示时返回 null，由调用方决定是渲染在标题栏还是浮在右上角。
 */
@Composable
private fun rememberPlayerClock(mode: Settings.PlaybackClock): String? {
    if (mode == Settings.PlaybackClock.NEVER) return null
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(mode) {
        while (true) {
            nowMs = System.currentTimeMillis()
            delay(CLOCK_TICK_MS)
        }
    }
    val calendar = Calendar.getInstance().apply { timeInMillis = nowMs }
    val hour = calendar.get(Calendar.HOUR_OF_DAY)
    val minute = calendar.get(Calendar.MINUTE)
    if (!clockVisibleAt(mode, minute)) return null
    return String.format(Locale.US, "%02d:%02d", hour, minute)
}

private fun clockVisibleAt(mode: Settings.PlaybackClock, minute: Int): Boolean = when (mode) {
    Settings.PlaybackClock.ALWAYS -> true
    Settings.PlaybackClock.HOURLY -> minute == 0
    Settings.PlaybackClock.HOURLY_AND_HALF -> minute == 0 || minute == 30
    Settings.PlaybackClock.NEVER -> false
}
