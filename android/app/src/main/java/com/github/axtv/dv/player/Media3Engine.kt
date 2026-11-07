/*
 * 播放内核 Android 实现：ExoPlayer（Media3）+ 缓存数据源。
 *
 * 对应 refactor/player/NOTES.md 的 ro5.m9955f（构建 ExoPlayer）与 ko5（Player.Listener）。
 * 只做内核层（换源/播放/参数/画面绑定），播放状态机与重试策略仍由核心层
 * axtv.player.PlayerEngine 负责，避免同一套逻辑两处维护。
 */
@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.github.axtv.dv.player

import android.content.Context
import android.util.Log
import android.view.KeyEvent
import android.view.Surface
import android.view.TextureView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize as Media3VideoSize
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import axtv.player.MediaEngine
import axtv.player.PlaybackError
import axtv.player.PlayerConfig
import axtv.player.PlayerEngine
import axtv.player.VideoMediaCache
import axtv.player.VideoSize
import com.github.axtv.dv.adapter.AppRuntime
import com.github.axtv.dv.widget.WidgetPlaybackItem
import com.github.axtv.dv.widget.WidgetPlayerControls
import com.github.axtv.dv.widget.WidgetRuntime
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

/** 与 Settings.videoLoadTimeoutSeconds() 的缺省一致：设置未装配时用这个超时秒数。 */
private const val DEFAULT_LOAD_TIMEOUT_SECONDS = 10

/**
 * 进程级共享资源：OkHttpClient + 磁盘缓存。
 *
 * <p>目录/上限/预载常量全部取自 [PlayerConfig]（原版 sn5），不自造数值。
 * SimpleCache 同一目录只允许实例化一次，故用单例持有。
 */
internal object Media3Resources {

    /**
     * 『视频加载超时』的消费点：取流数据源的连接/读取超时按设置档位取值。
     *
     * 进程级只建一次客户端，所以改完设置要下次进播放器才生效；settings 未装配时用
     * 核心层同一个默认值 10 秒（Settings.videoLoadTimeoutSeconds 的缺省），不自造数值。
     */
    private val httpClient: OkHttpClient by lazy {
        val seconds = (AppRuntime.settings?.videoLoadTimeoutSeconds() ?: DEFAULT_LOAD_TIMEOUT_SECONDS)
            .coerceAtLeast(1)
            .toLong()
        OkHttpClient.Builder()
            .connectTimeout(seconds, TimeUnit.SECONDS)
            .readTimeout(seconds, TimeUnit.SECONDS)
            .build()
    }

    @Volatile
    private var cache: SimpleCache? = null

    /** 进程级 SimpleCache（同一目录只允许实例化一次）；预载与播放共用。 */
    internal fun cache(context: Context): SimpleCache = synchronized(this) {
        cache ?: SimpleCache(
            File(context.applicationContext.cacheDir, PlayerConfig.CACHE_DIR_NAME),
            LeastRecentlyUsedCacheEvictor(PlayerConfig.CACHE_MAX_BYTES),
            StandaloneDatabaseProvider(context.applicationContext),
        ).also { cache = it }
    }

    /** 直连数据源（带原版固定的 UA/Referer 请求头）。 */
    fun plainFactory(): DataSource.Factory =
        OkHttpDataSource.Factory(httpClient)
            .setDefaultRequestProperties(VideoMediaCache.requestHeaders())

    /** 走 SimpleCache 的数据源，flags 取自原版 sn5（缓存出错直接回源）。 */
    fun cachedFactory(context: Context): DataSource.Factory =
        CacheDataSource.Factory()
            .setCache(cache(context))
            .setUpstreamDataSourceFactory(plainFactory())
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

    /**
     * 电视台 IPTV 用数据源：中性请求头（不带抖音 Referer），避免外部直播源的反盗链误拒。
     * 抖音点播/直播仍用 [plainFactory]。
     */
    fun iptvFactory(): DataSource.Factory =
        OkHttpDataSource.Factory(httpClient)
            .setDefaultRequestProperties(
                mapOf(
                    "User-Agent" to "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 " +
                        "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                    "Accept" to "*/*",
                ),
            )

    fun mediaSourceFactory(context: Context, useCache: Boolean): DefaultMediaSourceFactory =
        DefaultMediaSourceFactory(if (useCache) cachedFactory(context) else plainFactory())
}

/**
 * [MediaEngine] 的 Media3 实现。
 *
 * <p>线程约束：所有方法都必须在主线程调用（与 ExoPlayer 一致）。
 */
class Media3Engine(context: Context) : MediaEngine, WidgetPlayerControls {

    private val appContext = context.applicationContext

    private val player: ExoPlayer = ExoPlayer.Builder(appContext)
        // 「音量均衡」= 响度对齐：处理器经 RenderersFactory 注入 AudioSink（原版 po5 同一位置）。
        .setRenderersFactory(AudioBalanceRenderersFactory(appContext))
        .setMediaSourceFactory(Media3Resources.mediaSourceFactory(appContext, useCache = false))
        .setHandleAudioBecomingNoisy(true)
        .build()
        .apply {
            repeatMode = Player.REPEAT_MODE_OFF
            playWhenReady = PlayerConfig.PLAY_WHEN_READY_ON_BUILD
        }

    private val listeners = ArrayList<MediaEngine.Listener>()
    private var attachedView: Any? = null
    private var released = false

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            for (listener in listeners.toList()) {
                listener.onPlaybackStateChanged(playbackState)
            }
            when (playbackState) {
                Player.STATE_ENDED -> clearPlayback()
                Player.STATE_READY -> publishPlayback()
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            for (listener in listeners.toList()) {
                listener.onIsPlayingChanged(isPlaying)
            }
            publishPlayback()
        }

        override fun onPlayerError(error: PlaybackException) {
            val mapped = PlaybackError.of(error.errorCode, error.message, error)
            for (listener in listeners.toList()) {
                listener.onPlayerError(mapped)
            }
            clearPlayback()
        }

        override fun onRenderedFirstFrame() {
            for (listener in listeners.toList()) {
                listener.onRenderedFirstFrame()
            }
        }

        override fun onVideoSizeChanged(videoSize: Media3VideoSize) {
            val mapped = VideoSize(videoSize.width, videoSize.height, videoSize.pixelWidthHeightRatio)
            for (listener in listeners.toList()) {
                listener.onVideoSizeChanged(mapped)
            }
        }

        /**
         * 音轨取证埋点：Media3 在「只有音频轨无解码器」时不报错、只播视频，
         * 表现为有画面没声音且日志无痕。这里把每条音轨的 MIME/编码与是否被选中、
         * 是否受支持打出来，使无声原因可从 /logcat 直接判定，而不是靠猜。
         */
        override fun onTracksChanged(tracks: Tracks) {
            val uri = getCurrentMediaUri() ?: "?"
            var audioGroups = 0
            for (group in tracks.groups) {
                if (group.type != C.TRACK_TYPE_AUDIO) continue
                audioGroups++
                for (i in 0 until group.length) {
                    val f = group.getTrackFormat(i)
                    Log.i(
                        "AxTvAudio",
                        "uri=$uri mime=${f.sampleMimeType} codecs=${f.codecs} " +
                            "ch=${f.channelCount} rate=${f.sampleRate} " +
                            "selected=${group.isTrackSelected(i)} supported=${group.isTrackSupported(i)}",
                    )
                }
            }
            if (audioGroups == 0) {
                Log.w("AxTvAudio", "uri=$uri 清单里没有音轨（video-only 或解析未出音轨）")
            }
        }
    }

    init {
        player.addListener(playerListener)
    }

    // ------------------------------------------------------------------ 小组件装配
    /**
     * 只做「装配调用」：把内核的播放状态/媒体键桥到 widget 运行时，不改播放状态机。
     * 仅当本引擎是当前 controls 持有者时才写入快照，避免直播与视频引擎互相覆盖。
     * 标题/作者/封面元数据在 ui/player 的 PlaylistProvider 内（边界禁改），
     * 这里只能上报 URI 与播放态；widget 侧按空标题渲染，不伪造元数据。
     */
    private fun publishPlayback() {
        if (WidgetRuntime.playerControls !== this) return
        val uri = getCurrentMediaUri() ?: return
        WidgetRuntime.publishPlayback(
            appContext,
            WidgetPlaybackItem(uri, "", "", player.isPlaying, false, "本机播放", ""),
        )
    }

    /** 播放结束/出错/退出时清空快照，避免桌面显示过期条目。 */
    private fun clearPlayback() {
        if (WidgetRuntime.playerControls !== this) return
        WidgetRuntime.publishPlayback(appContext, null)
    }

    /**
     * 媒体键：优先交给核心层 [PlayerEngine]（它持有 PlaybackQueue，能真正上一条/下一条）；
     * 无 PlayerEngine（如直播间的独立引擎）时只处理播放/暂停。
     */
    override fun handle(keyCode: Int): Boolean {
        val host = listeners.lastOrNull { it is PlayerEngine } as? PlayerEngine
        when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                if (host != null) {
                    host.togglePlayPause()
                } else if (player.isPlaying) {
                    player.pause()
                } else {
                    player.play()
                }
                return true
            }

            KeyEvent.KEYCODE_MEDIA_PLAY -> {
                player.play()
                return true
            }

            KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                player.pause()
                return true
            }

            KeyEvent.KEYCODE_MEDIA_NEXT -> {
                val h = host ?: return false
                return h.playNext()
            }

            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                val h = host ?: return false
                return h.playPrevious()
            }
        }
        return false
    }

    // ------------------------------------------------------------------ 换源

    override fun setMediaItem(uri: String, useCache: Boolean) {
        val source = Media3Resources.mediaSourceFactory(appContext, useCache)
            .createMediaSource(MediaItem.fromUri(uri))
        player.setMediaSource(source)
        publishPlayback()
    }

    /**
     * 电视台 HLS：强制 HLS 媒体类型（部分直播源 URL 不以 .m3u8 结尾，靠扩展名判定会落到
     * ProgressiveMediaSource 而报 Source error），并使用中性请求头（不带抖音 Referer）。
     */
    fun setHlsMediaItem(uri: String) {
        val source = DefaultMediaSourceFactory(Media3Resources.iptvFactory())
            .createMediaSource(
                MediaItem.Builder()
                    .setUri(uri)
                    .setMimeType(MimeTypes.APPLICATION_M3U8)
                    .build(),
            )
        player.setMediaSource(source)
        publishPlayback()
    }

    override fun prepare() {
        player.prepare()
    }

    override fun stopAndClear() {
        player.stop()
        player.clearMediaItems()
        clearPlayback()
    }

    override fun stop() {
        player.stop()
    }

    // ------------------------------------------------------------------ 播放控制

    override fun play() {
        player.play()
    }

    override fun pause() {
        player.pause()
    }

    override fun setPlayWhenReady(playWhenReady: Boolean) {
        player.playWhenReady = playWhenReady
    }

    override fun isPlaying(): Boolean = player.isPlaying

    override fun getPlaybackState(): Int = player.playbackState

    override fun seekTo(positionMs: Long) {
        player.seekTo(positionMs)
    }

    override fun seekToDefaultPosition() {
        player.seekToDefaultPosition()
    }

    override fun getCurrentPosition(): Long = player.currentPosition

    override fun getDuration(): Long = player.duration

    // ------------------------------------------------------------------ 参数

    override fun setVolume(volume: Float) {
        player.volume = volume
    }

    override fun setPlaybackSpeed(speed: Float) {
        player.playbackParameters = PlaybackParameters(speed)
    }

    // ------------------------------------------------------------------ 画面

    /**
     * 三种宿主：
     * - [PlayerView]：交给 PlayerView 自己管理 surface（同时应用 resizeMode/FIT）；
     * - [TextureView]：直接交给 ExoPlayer（PlayerView 1.5.1 不支持 programmatic 切 surface 类型）；
     * - [Surface]：裸 Surface（GL 直通等外部管线用）。
     */
    override fun setVideoSurface(surface: Any?) {
        val previous = attachedView
        if (previous is PlayerView && previous !== surface) {
            // 换/摘掉 PlayerView 时必须先解绑，否则旧 view 会继续持有 player。
            previous.player = null
        }
        when (surface) {
            is PlayerView -> {
                attachedView = surface
                surface.player = player
            }

            is TextureView -> {
                attachedView = surface
                player.setVideoTextureView(surface)
            }

            is Surface -> {
                attachedView = surface
                player.setVideoSurface(surface)
            }

            null -> {
                attachedView = null
                player.setVideoSurface(null)
                if (previous is PlayerView) {
                    previous.player = null
                }
            }

            else -> throw IllegalArgumentException("不支持的画面宿主: ${surface.javaClass.name}")
        }
    }

    override fun getVideoSize(): VideoSize {
        val size = player.videoSize
        return if (size.width <= 0 || size.height <= 0) {
            VideoSize.UNKNOWN
        } else {
            VideoSize(size.width, size.height, size.pixelWidthHeightRatio)
        }
    }

    override fun getCurrentMediaUri(): String? =
        player.currentMediaItem?.localConfiguration?.uri?.toString()

    // ------------------------------------------------------------------ 监听

    override fun addListener(listener: MediaEngine.Listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
        }
        // PlayerEngine 接入即视为视频会话开始：装配媒体键与播放态上报。
        if (listener is PlayerEngine) {
            WidgetRuntime.playerControls = this
            publishPlayback()
        }
    }

    override fun removeListener(listener: MediaEngine.Listener) {
        listeners.remove(listener)
        if (listener is PlayerEngine && listeners.none { it is PlayerEngine }) {
            detachControls()
        }
    }

    /** 会话结束时摘掉挂点并清空快照；只清自己持有的，避免误清其它引擎。 */
    private fun detachControls() {
        if (WidgetRuntime.playerControls !== this) return
        WidgetRuntime.publishPlayback(appContext, null)
        WidgetRuntime.playerControls = null
    }

    override fun release() {
        if (released) {
            return
        }
        released = true
        val view = attachedView
        if (view is PlayerView) {
            view.player = null
        }
        attachedView = null
        detachControls()
        listeners.clear()
        player.removeListener(playerListener)
        player.setPlayWhenReady(false)
        player.release()
    }
}
