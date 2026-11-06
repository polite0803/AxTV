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
 *
 * 两套按键语义，由「焦点在哪一层」决定（chromeFocus）：
 * - 焦点在画面层（默认）：上/下 = 上一条/下一条，左/右 = ±10s 微调（长按连发），
 *   确认 = 播放/暂停，MENU/INFO = 播放菜单面板；这一层拦下的键 KeyDown 与 KeyUp 都吃掉，
 *   漏一个 KeyUp 就会让 Compose 默认焦点搜索替我们挪焦点（表现为"按一下就跑很远"）。
 * - 焦点在控制栏：方向键走正常焦点导航、确认键激活按钮，底部菜单因此可达。
 * 进入控制栏 = 长按下键；BACK 在控制栏内按一次把焦点交回画面层（不退出播放页）。
 * 换条判定放在 KeyUp：按下时长只有松手时才知道，短按换条、长按进控制栏，两者不会同时发生。
 * 语义出处 src/app/p000/on5.java 的 r13 tap map（21/22 → seek、19/20 → 换条）
 * 与 PlayerSession.handleRemoteKey。
 */
package com.github.axtv.dv.ui.player

import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import axtv.api.InteractionApi
import axtv.player.PlaybackError
import axtv.player.PlayerState
import axtv.settings.Settings
import axtv.settings.SettingsTypes
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs

/** 控制层（顶部标题 + 底部按钮）无操作自动隐藏延时。 */
private const val CONTROLS_AUTO_HIDE_MS = 4_000L

/** 「长按下键」阈值：达到即把焦点交给控制栏，短按才是上一条/下一条。 */
private const val LONG_PRESS_MS = 400L

/** 队列接近队尾的提前量：剩余不足这么多条时继续拉下一页，保证按「下」不会卡住。 */
private const val QUEUE_MORE_AHEAD = 3

@Composable
fun PlayerOverlayScreen(
    modifier: Modifier = Modifier,
    playlistProvider: PlaylistProvider? = null,
    /**
     * 信息流/精选播放的续页入口：播放位置接近队尾时调用，返回下一批条目
     * （空表示没有更多或本次加载失败）。只对 [VideoPlaylistProvider] 生效。
     */
    playlistMore: (suspend () -> List<VideoPlaylistProvider.Entry>)? = null,
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
    /** 当前播放条目变化（连播自动切条也会回调）：合集播放页用它同步面板高亮/追更。 */
    onCurrentItemChanged: (itemId: String) -> Unit = {},
    /**
     * 「进入发布者频道」回调，参数为当前条目的 author.sec_uid。
     * 为空或当前条目没有 sec_uid 时，底部控制栏不出现该入口。
     */
    onOpenAuthor: ((secUid: String) -> Unit)? = null,
) {
    val context = LocalContext.current
    val settings = AppRuntime.settings
    val provider = playlistProvider ?: remember { demoPlaylistProvider() }
    val surfaceType = remember(settings) { settings?.surfaceType() ?: SettingsTypes.SurfaceType.SURFACE_VIEW }
    val bufferAlign = remember(settings) { settings?.surfaceBufferAlign() == true }
    val initialSpeed = remember(settings) { settings?.playbackSpeed() ?: 1.0f }
    val completionAction = remember(settings) {
        settings?.completionAction() ?: SettingsTypes.CompletionAction.AUTO_NEXT
    }
    // 设置页三项：暂停时是否隐藏控件 / 底部是否画进度条 / 播放时钟显示模式。
    val cleanPause = remember(settings) { settings?.cleanPause() ?: false }
    val showProgressBar = remember(settings) { settings?.playerProgressBar() ?: false }
    val clockMode = remember(settings) { settings?.playbackClock() ?: SettingsTypes.PlaybackClock.NEVER }
    val clockText = rememberPlayerClock(clockMode)

    // 弹幕总开关：设置项 video_danmaku_enabled 的播放页读写口（浮层『弹幕』按钮与面板里的开关都写它）。
    var danmakuOn by rememberSaveable { mutableStateOf(settings?.videoDanmakuEnabled() ?: true) }
    val activeDanmakuConfig = danmakuConfig.copy(enabled = danmakuOn)

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

    // 队尾续页：切条/追加后都会重新判断；按「下」失败时自增触发重试。
    var moreRequest by remember { mutableIntStateOf(0) }
    val latestPlaylistMore by rememberUpdatedState(playlistMore)

    // 起播一次；表面挂载晚于起播时由 PlayerSession 补挂。
    LaunchedEffect(session) { session.start(0) }

    // 播放位置进入队尾提前量时继续拉下一页；一次事件内补齐到安全余量。
    LaunchedEffect(session.playlistIndex, session.itemCount, moreRequest) {
        val loadMore = latestPlaylistMore ?: return@LaunchedEffect
        while (true) {
            val count = session.itemCount
            if (count == 0 || session.playlistIndex < count - QUEUE_MORE_AHEAD) {
                return@LaunchedEffect
            }
            val added = loadMore()
            if (added.isEmpty()) {
                return@LaunchedEffect
            }
            val before = session.itemCount
            session.appendEntries(added)
            if (session.itemCount <= before) {
                // 返回的全是已存在的条目：停止本轮，等下一次切条/按键再试。
                return@LaunchedEffect
            }
        }
    }

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
    val latestOnCurrentItemChanged by rememberUpdatedState(onCurrentItemChanged)
    LaunchedEffect(currentItemId) {
        currentItemId?.let { latestOnCurrentItemChanged(it) }
    }
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
    // 「越看越准」：真正出过画面（firstFrameRendered）才算看过，才上报 stats + 观看历史。
    // 去重在核心层 PlaybackFeedbackReporter 里按作品 id 做进程内判定（对齐 kq5.f12447i），
    // 同一条来回切换不会重复打网络；失败只写日志，不打断播放。
    // awemeId 为空的是演示数据源，不上报（否则会把 demo-1 这种假 id 发给服务端）。
    LaunchedEffect(currentItem?.awemeId, session.firstFrameRendered) {
        val item = currentItem?.takeIf { it.awemeId.isNotEmpty() } ?: return@LaunchedEffect
        if (!session.firstFrameRendered) return@LaunchedEffect
        val reporter = AppRuntime.playbackFeedback ?: return@LaunchedEffect
        val outcome = withContext(Dispatchers.IO) {
            reporter.reportWatched(item.awemeId, item.awemeType, item.authorId)
        }
        if (outcome.hasFailure()) {
            android.util.Log.w(
                "AxTV_Feedback",
                "aweme=" + item.awemeId +
                    " stats=" + outcome.statsError + " history=" + outcome.historyError,
            )
        }
    }

    val latestDanmakuLoader = rememberUpdatedState(activeDanmakuLoader)
    var loadedDanmakuComments by remember { mutableStateOf(emptyList<DanmakuComment>()) }
    LaunchedEffect(currentItemId, session.durationMs, activeDanmakuLoader != null, danmakuOn) {
        val loader = latestDanmakuLoader.value
        // 切条目 / 时长变化时先清空旧时间轴，避免上一条评论落在新视频的时间轴上。
        loadedDanmakuComments = emptyList()
        if (loader == null || currentItemId == null) {
            return@LaunchedEffect
        }
        // 弹幕总开关关闭时不发起评论请求（渲染层也会清屏，见 DanmakuEngine）。
        if (!activeDanmakuConfig.enabled) {
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

    // 播放器内的「不喜欢」：负反馈喂给推荐接口。commit/dislike/item 只需要 aweme_id，
    // 不要求互动凭据（与点赞/收藏不同，见 InteractionApi.dislike）。状态按条目重建，
    // 避免上一条视频的结论落到新视频上。
    val interactionApi = remember { AppRuntime.api?.let { InteractionApi(it) } }
    val feedbackScope = rememberCoroutineScope()
    var disliked by remember(currentItem?.awemeId) { mutableStateOf(false) }
    var busyDislike by remember(currentItem?.awemeId) { mutableStateOf(false) }
    var dislikeNotice by remember(currentItem?.awemeId) { mutableStateOf("") }

    fun markDisliked() {
        val awemeId = currentItem?.awemeId.orEmpty()
        if (awemeId.isEmpty() || busyDislike || disliked) return
        val api = interactionApi
        if (api == null) {
            dislikeNotice = "应用尚未初始化，无法标记"
            return
        }
        busyDislike = true
        feedbackScope.launch {
            val failure = try {
                val result = withContext(Dispatchers.IO) { api.dislike(awemeId) }
                if (result.statusCode() == 0) {
                    null
                } else {
                    result.statusMsg().ifBlank { "标记失败（${result.statusCode()}）" }
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                e.message ?: "标记失败"
            } finally {
                busyDislike = false
            }
            if (failure == null) {
                disliked = true
                dislikeNotice = "已标记不喜欢"
            } else {
                dislikeNotice = failure
            }
        }
    }

    var menuOpen by rememberSaveable { mutableStateOf(false) }
    var mixPanelOpen by rememberSaveable { mutableStateOf(false) }
    /** 焦点归属：false = 画面层（播放语义按键），true = 控制栏（方向键走焦点导航）。 */
    var chromeFocus by rememberSaveable { mutableStateOf(false) }
    val bottomBarFocusRequester = remember { FocusRequester() }
    val firstMenuItemRequester = remember { FocusRequester() }
    val surfaceFocusRequester = remember { FocusRequester() }
    val firstEpisodeRequester = remember { FocusRequester() }
    val errorRetryRequester = remember { FocusRequester() }

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

    /** 长按下键：把焦点交给控制栏。控制栏必须先出现在屏幕上才有地方落焦。 */
    fun enterChromeFocus() {
        wakeChrome()
        chromeFocus = true
    }

    /** 弹幕开关：写设置项 video_danmaku_enabled，浮层与面板共用同一个状态源。 */
    fun toggleDanmaku() {
        val next = !danmakuOn
        danmakuOn = next
        settings?.setVideoDanmakuEnabled(next)
    }

    /**
     * 画面层按键分发（仅在焦点位于画面层时生效；焦点进了控制栏就整层让位）。
     *
     * - 左/右 → ±[PlayerSession.SEEK_STEP_MS] 微调，长按连发（原版 ro5 即连发语义）；
     * - 上/下 → 短按（松手时判定）上一条 / 下一条；长按 → 把焦点交给控制栏；
     * - 确认 → 播放/暂停；菜单键 / INFO → 打开播放菜单；
     * - 其余键不认，交回系统（BACK 由 BackHandler 处理）。
     *
     * 命中的键 KeyDown 与 KeyUp 一律吃掉：只吃 KeyDown 的话 KeyUp 会落到 Compose
     * 默认焦点搜索上，表现为"按一下焦点自己跑走"。控制层显隐不改变焦点归属，
     * 唤醒控制层只是让它可见，方向键仍然走播放语义。
     */
    fun dispatchPlayerKey(event: KeyEvent): Boolean {
        // 错误卡片出现时把按键交回默认焦点系统，否则方向键/确认键会被播放语义吃掉，
        // 遥控器永远进不到“重试/下一个”。
        if (session.error != null) return false
        if (latestMenuOpen || latestMixPanelOpen || chromeFocus) return false
        // 用 nativeKeyEvent.keyCode：Compose 的 Key 对 TV 方向键解析不出稳定的 keycode（实测拿到 Unknown/0）。
        val native = event.nativeKeyEvent
        val keyCode = native.keyCode
        val isSwitch = keyCode == AndroidKeyEvent.KEYCODE_DPAD_UP || keyCode == AndroidKeyEvent.KEYCODE_DPAD_DOWN
        val isSeek = keyCode == AndroidKeyEvent.KEYCODE_DPAD_LEFT || keyCode == AndroidKeyEvent.KEYCODE_DPAD_RIGHT
        val isConfirm = keyCode == AndroidKeyEvent.KEYCODE_DPAD_CENTER ||
            keyCode == AndroidKeyEvent.KEYCODE_ENTER ||
            keyCode == AndroidKeyEvent.KEYCODE_NUMPAD_ENTER
        val isOpenMenu = keyCode == AndroidKeyEvent.KEYCODE_MENU || keyCode == AndroidKeyEvent.KEYCODE_INFO
        if (!isSwitch && !isSeek && !isConfirm && !isOpenMenu) return false

        when (event.type) {
            KeyEventType.KeyDown -> when {
                // 换条要等松手才能分清短按/长按，这里只负责"长按已成形"的即时反馈。
                isSwitch -> if (native.eventTime - native.downTime >= LONG_PRESS_MS) enterChromeFocus()
                isConfirm -> session.togglePlayPause()
                isOpenMenu -> menuOpen = true
                else -> {
                    // 微调时把进度条拉出来（『播放器进度条』默认关，seek 是一次性反馈）。
                    if (isSeek) seekFeedback = true
                    session.handleRemoteKey(keyCode)
                }
            }

            KeyEventType.KeyUp -> if (isSwitch && native.eventTime - native.downTime < LONG_PRESS_MS) {
                val handled = session.handleRemoteKey(keyCode)
                // 已到队尾但页面还能续页：再按一次「下」触发加载，而不是没反应。
                if (!handled && keyCode == AndroidKeyEvent.KEYCODE_DPAD_DOWN &&
                    latestPlaylistMore != null
                ) {
                    moreRequest += 1
                }
            }
        }
        wakeChrome()
        return true
    }

    // 焦点归属变化时真正落焦：面板 > 控制栏 > 画面层（否则按键会落进焦点空洞）。
    LaunchedEffect(menuOpen, mixPanelOpen, chromeFocus) {
        withFrameNanos { }
        runCatching {
            when {
                menuOpen -> firstMenuItemRequester.requestFocus()
                mixPanelOpen -> firstEpisodeRequester.requestFocus()
                chromeFocus -> bottomBarFocusRequester.requestFocus()
                else -> surfaceFocusRequester.requestFocus()
            }
        }
    }

    // 播放失败时焦点交给“重试”，保证遥控器可达；错误清除后把焦点还给画面层，
    // 否则「重试」按钮随错误卡片一起消失，焦点会掉到顶栏/焦点空洞。
    LaunchedEffect(session.error) {
        withFrameNanos { }
        runCatching {
            if (session.error != null) {
                errorRetryRequester.requestFocus()
            } else {
                surfaceFocusRequester.requestFocus()
            }
        }
    }

    // 自动隐藏倒计时：每次按键（chromeWakeAt）与播放态变化都会重启。
    // 焦点在控制栏时不收起 —— 收起来焦点就掉进空洞，这正是底部菜单不可达的老问题。
    LaunchedEffect(
        chromeVisible,
        chromeWakeAt,
        menuOpen,
        mixPanelOpen,
        chromeFocus,
        keepChromeWhilePaused,
        session.error,
    ) {
        if (!chromeVisible || menuOpen || mixPanelOpen || chromeFocus || session.error != null || keepChromeWhilePaused) {
            return@LaunchedEffect
        }
        val remaining = chromeWakeAt + CONTROLS_AUTO_HIDE_MS - System.currentTimeMillis()
        delay(remaining.coerceAtLeast(0L))
        chromeVisible = false
        seekFeedback = false
    }

    // 切条目也算一次交互（连播时标题短暂浮现，与原版换条后重置控制层一致）。
    LaunchedEffect(session.playlistIndex) { wakeChrome() }

    // BACK 逐级退出：面板 → 控制栏焦点 → 唤醒隐藏的控制层 → 交回导航栈退出播放页。
    BackHandler(enabled = menuOpen || mixPanelOpen || chromeFocus || !chromeVisible) {
        when {
            menuOpen -> menuOpen = false
            mixPanelOpen -> mixPanelOpen = false
            chromeFocus -> {
                chromeFocus = false
                wakeChrome()
            }
            else -> wakeChrome()
        }
    }

    // 画中画：小窗里只保留画面，浮层/弹幕/面板全部让位（PIP 态由 PipCoordinator 写回，见 MainActivity）。
    val inPip = PipCoordinator.inPipMode

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .then(
                // 焦点交给控制栏或面板时，这一层不再参与焦点：它是个占满全屏的矩形，
                // 留在焦点树里会让「相邻」距离计算退化，方向键从按钮出发容易被它抢走。
                if (chromeFocus || menuOpen || mixPanelOpen) Modifier
                else Modifier.focusRequester(surfaceFocusRequester).focusable()
            )
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
                config = activeDanmakuConfig,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Column(modifier = Modifier.fillMaxSize()) {
            if (chromeVisible && !inPip) {
                PlayerHeader(session, clockText, authorName = currentItem?.author.orEmpty())
            }

            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    session.error != null -> PlayerErrorCard(
                        error = session.error!!,
                        retryRequester = errorRetryRequester,
                        onRetry = session::retry,
                        onNext = { session.playNext() },
                    )

                    session.skipNotice != null -> PlayerSkipNotice(text = session.skipNotice!!)

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
                    bottomBarFocusRequester = bottomBarFocusRequester,
                    chromeFocused = chromeFocus,
                    showProgress = showProgressBar || seekFeedback,
                    episodeCount = visibleMixEpisodes.size,
                    danmakuOn = danmakuOn,
                    onToggleDanmaku = ::toggleDanmaku,
                    onOpenMenu = { menuOpen = true },
                    onOpenEpisodes = { mixPanelOpen = true },
                    authorSecUid = currentItem?.authorSecUid.orEmpty(),
                    authorName = currentItem?.author.orEmpty(),
                    onOpenAuthor = {
                        val secUid = currentItem?.authorSecUid.orEmpty()
                        if (secUid.isNotBlank()) onOpenAuthor?.invoke(secUid)
                    },
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
                danmakuOn = danmakuOn,
                onDanmakuToggle = ::toggleDanmaku,
                dislikeEnabled = currentItem?.awemeId?.isNotEmpty() == true,
                dislikeBusy = busyDislike,
                dislikeNotice = dislikeNotice,
                onDislike = ::markDisliked,
                onClose = { menuOpen = false },
                authorEnabled = currentItem?.authorSecUid?.isNotBlank() == true,
                authorName = currentItem?.author.orEmpty(),
                onOpenAuthor = {
                    val secUid = currentItem?.authorSecUid.orEmpty()
                    if (secUid.isNotBlank()) {
                        menuOpen = false
                        onOpenAuthor?.invoke(secUid)
                    }
                },
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
