/*
 * 播放会话：把核心层状态机（PlayerEngine）与 Media3 内核（Media3Engine）接到 Compose。
 *
 * 职责边界：
 * - 生命周期与状态机 → axtv.player.PlayerEngine（不重复实现重试/首帧静音/连播）
 * - 数据源 → 调用方提供的 PlaylistProvider
 * - 本类只负责：Compose 可观察状态、画质切轨、备用 URL 重试、进度轮询
 */
package com.github.axtv.dv.ui.player

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import axtv.player.PlaybackError
import axtv.player.PlaybackQueue
import axtv.player.PlayerEngine
import axtv.player.PlayerEventListener
import axtv.player.PlayerState
import axtv.player.SharedPlayerPool
import axtv.player.VideoSize
import axtv.settings.Settings
import com.github.axtv.dv.danmaku.DanmakuPlaybackSource
import com.github.axtv.dv.player.Media3Engine

class PlayerSession(
    context: Context,
    private val provider: PlaylistProvider,
    initialSpeed: Float = 1.0f,
    private val completionAction: Settings.CompletionAction = Settings.CompletionAction.AUTO_NEXT,
    private val persistSpeed: ((Float) -> Unit)? = null,
) {

    private val handler = Handler(Looper.getMainLooper())

    private val pool = SharedPlayerPool { Media3Engine(context) }

    private val queue = PlaybackQueue { item -> provider.resolveUrl(item.id()) }

    private val scheduler = object : PlayerEngine.Scheduler {
        override fun postDelayed(task: Runnable, delayMs: Long) {
            handler.postDelayed(task, delayMs)
        }

        override fun remove(task: Runnable) {
            handler.removeCallbacks(task)
        }
    }

    private val engine: PlayerEngine = PlayerEngine(pool, queue, null, null, null, scheduler)
        .independent(true)
        .muteUntilFirstFrame(true)
        .useCache(true)
        .autoPlay(true)

    // ------------------------------------------------------------------ 可观察状态

    var state by mutableStateOf(PlayerState.UNPREPARED)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    /** 用户意图（缓冲/等 surface 期间 isPlaying=false，但按钮应显示「暂停」）。 */
    var playingIntent by mutableStateOf(true)
        private set
    var firstFrameRendered by mutableStateOf(false)
        private set
    var videoSize by mutableStateOf(VideoSize.UNKNOWN)
        private set
    var error by mutableStateOf<PlaybackError?>(null)
        private set
    var retrying by mutableStateOf(false)
        private set
    var retryAttempt by mutableStateOf(0)
        private set
    var playlistIndex by mutableStateOf(0)
        private set
    var currentTitle by mutableStateOf("")
        private set
    var positionMs by mutableStateOf(0L)
        private set
    var durationMs by mutableStateOf(0L)
        private set
    var speed by mutableStateOf(initialSpeed)
        private set
    var qualityIndex by mutableStateOf(0)
        private set

    /** 当前条目的多码率列表；provider 未提供时为空。 */
    val qualityTracks: List<QualityTrack>
        get() = currentId?.let { provider.qualities(it) } ?: emptyList()

    val itemCount: Int
        get() = provider.items().size

    /**
     * 弹幕浮层数据源：由 [com.github.axtv.dv.danmaku.DanmakuOverlayHost] 消费。
     *
     * 位置与播放态由浮层每帧主动拉取；这里额外把 isPlaying / seek / 尺寸三个事件
     * 转成 [DanmakuPlaybackSource.Listener] 回调，保证暂停恢复、seek 重建时间轴、
     * 视频尺寸变化都能立即重绘。
     */
    private val danmakuBridge = object : DanmakuPlaybackSource {
        private val listeners = ArrayList<DanmakuPlaybackSource.Listener>()

        override fun positionMs(): Long {
            val current = engine.engine()?.getCurrentPosition() ?: 0L
            return current.coerceAtLeast(0L)
        }

        override fun isPlaying(): Boolean = this@PlayerSession.isPlaying

        override fun viewportWidthPx(): Int = videoSize.width()

        override fun viewportHeightPx(): Int = videoSize.height()

        override fun addListener(listener: DanmakuPlaybackSource.Listener) {
            if (!listeners.contains(listener)) {
                listeners.add(listener)
            }
        }

        override fun removeListener(listener: DanmakuPlaybackSource.Listener) {
            listeners.remove(listener)
        }

        fun notifyPlayback(isPlaying: Boolean) {
            for (listener in listeners.toList()) {
                listener.onPlaybackStateChanged(isPlaying)
            }
        }

        fun notifySeek(positionMs: Long) {
            for (listener in listeners.toList()) {
                listener.onSeek(positionMs)
            }
        }

        fun notifySize(width: Int, height: Int) {
            for (listener in listeners.toList()) {
                listener.onVideoSizeChanged(width, height)
            }
        }
    }

    val danmakuSource: DanmakuPlaybackSource
        get() = danmakuBridge

    private var currentId: String? = null
    private var pendingSurface: Any? = null
    private var fallbackUsed = false
    private var resumeOnStart = false
    private var released = false

    init {
        // 播完行为：auto_next 走队列连播；pause / replay 关闭队列自动推进。
        queue.setAutoNextEnabled(completionAction == Settings.CompletionAction.AUTO_NEXT)
        // 游标变化先于取流，保证「解析失败」时 currentId 已是新条目（备用链才不会张冠李戴）。
        queue.setListener { item, index ->
            currentId = item?.id()
            currentTitle = item?.title().orEmpty()
            playlistIndex = index
            durationMs = item?.durationMs() ?: 0L
            positionMs = 0L
            firstFrameRendered = false
            error = null
            retrying = false
            fallbackUsed = false
            qualityIndex = 0
        }
        engine.setListener(object : PlayerEventListener {
            override fun onStateChanged(state: PlayerState) {
                this@PlayerSession.state = state
                if (state == PlayerState.READY) {
                    retrying = false
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                this@PlayerSession.isPlaying = isPlaying
                danmakuBridge.notifyPlayback(isPlaying)
            }

            override fun onFirstFrameRendered(url: String) {
                firstFrameRendered = true
                retrying = false
            }

            override fun onVideoSizeChanged(size: VideoSize) {
                videoSize = size
                danmakuBridge.notifySize(size.width(), size.height())
            }

            override fun onError(error: PlaybackError) {
                handleError(error)
            }

            override fun onRetrying(attempt: Int) {
                retryAttempt = attempt
                retrying = true
            }

            override fun onEnded() {
                if (completionAction == Settings.CompletionAction.REPLAY) {
                    val url = engine.currentUrl() ?: return
                    firstFrameRendered = false
                    danmakuBridge.notifySeek(0L)
                    engine.open(url, 0L, true)
                }
            }

            override fun onItemChanged(item: PlaybackQueue.Item, index: Int) {
                danmakuBridge.notifySeek(0L)
                currentId = item.id()
                currentTitle = item.title()
                playlistIndex = index
                qualityIndex = 0
                durationMs = item.durationMs()
                positionMs = 0L
                firstFrameRendered = false
                error = null
                retrying = false
                fallbackUsed = false
            }
        })
        engine.setSpeed(initialSpeed)
    }

    // ------------------------------------------------------------------ 播放控制

    /** 载入播放列表并起播。表面可在起播前后任意时刻挂载（内部会补挂一次）。 */
    fun start(startIndex: Int = 0) {
        val items = provider.items().map { PlaybackQueue.Item(it.id, it.title, it.durationMs) }
        if (items.isEmpty()) {
            error = PlaybackError(0, "播放列表为空", false, null)
            return
        }
        queue.replaceAll(items, startIndex)
        engine.openCurrentItem()
        pendingSurface?.let { engine.attachSurface(it) }
    }

    fun togglePlayPause() {
        if (released) {
            return
        }
        playingIntent = !playingIntent
        if (playingIntent) {
            engine.play()
        } else {
            engine.pause()
        }
    }

    fun playNext(): Boolean {
        if (released) {
            return false
        }
        return engine.playNext()
    }

    fun seekTo(positionMs: Long) {
        if (released) {
            return
        }
        engine.seekTo(positionMs)
        danmakuBridge.notifySeek(positionMs)
        refreshProgress()
    }

    /**
     * 按相对位移跳转（遥控器左右键微调）。
     *
     * 与 [seekTo] 不同：先取当前位置，再 clamp 到 [0, duration] 后 seek，
     * 避免「负值被 ExoPlayer 当 0」与「超长被 clamp」两种边界都出错。
     * 与核心层 PlayerEngine.seekTo 语义一致（无负值保护）。
     */
    fun seekBy(deltaMs: Long) {
        if (released) {
            return
        }
        val current = positionMs
        val target = (current + deltaMs).coerceIn(0L, durationMs.coerceAtLeast(0L))
        seekTo(target)
    }

    fun applySpeed(value: Float) {
        speed = value
        engine.setSpeed(value)
        persistSpeed?.invoke(value)
    }

    /** 浮层「重试」：主链重开一次，并允许再走一次备用 URL。 */
    fun retry() {
        val url = engine.currentUrl() ?: return
        val position = engine.engine()?.getCurrentPosition() ?: 0L
        error = null
        retrying = true
        fallbackUsed = false
        engine.open(url, position, true)
    }

    /**
     * 切画质：同一 ExoPlayer 实例上换 MediaSource（不重建 player）。
     *
     * 码率列表来自取流结果的 BitRate，每档是一条独立直链而非同一 manifest 内的 track，
     * 因此无法用 TrackSelectionParameters 选轨；换源后按当前进度 seek 续播。
     */
    fun selectQuality(index: Int) {
        val id = currentId ?: return
        val track = provider.qualities(id).getOrNull(index) ?: return
        val position = engine.engine()?.getCurrentPosition() ?: 0L
        val wasPlaying = isPlaying
        qualityIndex = index
        fallbackUsed = false
        retrying = false
        error = null
        engine.open(track.url, position, true)
        danmakuBridge.notifySeek(position)
        if (!wasPlaying) {
            // PlayerEngine.open() 会按 autoPlay 重新置 wantPlaying，这里恢复暂停态。
            engine.pause()
        }
    }

    // ------------------------------------------------------------------ 进度 / 画布

    /** 由 UI 定时调用，刷新进度与总时长（ExoPlayer 没有 Compose 原生状态）。 */
    fun refreshProgress() {
        val mediaEngine = engine.engine() ?: return
        positionMs = mediaEngine.getCurrentPosition().coerceAtLeast(0L)
        val duration = mediaEngine.getDuration()
        if (duration > 0) {
            durationMs = duration
        }
    }

    /** 挂载画面宿主；PlayerEngine 在起播前还没有内核实例，这里做一次补挂。 */
    fun attachSurface(host: Any?) {
        pendingSurface = host
        if (engine.engine() != null) {
            engine.attachSurface(host)
        }
    }

    // ------------------------------------------------------------------ 宿主生命周期

    fun onHostStop() {
        resumeOnStart = playingIntent
        playingIntent = false
        if (isPlaying) {
            engine.pause()
        }
    }

    fun onHostStart() {
        if (resumeOnStart) {
            resumeOnStart = false
            playingIntent = true
            engine.play()
        }
    }

    fun release() {
        if (released) {
            return
        }
        released = true
        pendingSurface = null
        engine.release()
    }

    // ------------------------------------------------------------------ 遥控器键

    /**
     * 播放页遥控器键分发（对应 ro5.m9957h + r13.f18571e 的媒体键重映射）。
     *
     * - 左/右 → 按 [SEEK_STEP_MS] 相对跳转（ro5 的 C1780wn byte 7/9 → -10000L、byte 8 → +10000L）；
     * - 上/下 → 上一条 / 下一条（Media3Engine.handle 的 MEDIA_PREVIOUS / MEDIA_NEXT）；
     * - 其它键不认，返回 false 交给焦点系统。
     *
     * 与原实现的差异：原版 ro5 把左右键绑成「长按连发」，这里只做单次步进；
     * 重复按下会再次步进，语义等价且实现更简单。
     */
    fun handleRemoteKey(keyCode: Int): Boolean = when (keyCode) {
        KEY_DPAD_LEFT -> { seekBy(-SEEK_STEP_MS); true }
        KEY_DPAD_RIGHT -> { seekBy(SEEK_STEP_MS); true }
        KEY_DPAD_UP -> engine.playPrevious()
        KEY_DPAD_DOWN -> engine.playNext()
        else -> false
    }

    companion object {
        /** 左右键单次步进：10 000ms，对应 ro5 的 Renderer.DEFAULT_DURATION_TO_PROGRESS_US。 */
        const val SEEK_STEP_MS = 10_000L

        private const val KEY_DPAD_LEFT = 21
        private const val KEY_DPAD_RIGHT = 22
        private const val KEY_DPAD_UP = 19
        private const val KEY_DPAD_DOWN = 20
    }

    // ------------------------------------------------------------------ 错误 / 备用链

    /**
     * 错误策略：
     * 1. 核心层 PlayerEngine 已先对同一 URL prepare 重试一次（MAX_RETRY_PER_ITEM）；
     * 2. 仍失败则这里切「备用直链」重开一次（同一条目只做一次）；
     * 3. 备用链也失败 → 把错误交给浮层展示，不再静默黑屏。
     */
    private fun handleError(playbackError: PlaybackError) {
        val itemId = currentId
        val backup = itemId?.let { provider.backupUrl(it) }
        if (!fallbackUsed && backup != null && backup != engine.currentUrl()) {
            fallbackUsed = true
            retrying = true
            val position = engine.engine()?.getCurrentPosition() ?: 0L
            engine.open(backup, position, true)
            return
        }
        retrying = false
        error = playbackError
    }
}
