/*
 * 真实 aweme 列表 → 播放器数据源。
 *
 * 取流决策统一走核心层 axtv.model.VideoPlayUrlResolver.playableUrl(aweme)：
 * 清晰度/编码偏好来自 Settings，播放器侧只消费「条目 → 主直链 / 备用链 / 多码率」。
 * 换档、续播、兜底仍由 ui/player/PlayerSession 负责，本文件不改那套逻辑。
 */
package com.github.axtv.dv.ui.nav

import axtv.model.Aweme
import axtv.model.VideoPlayUrlResolver
import com.github.axtv.dv.adapter.AppRuntime
import com.github.axtv.dv.adapter.ViewedVideoTracker
import com.github.axtv.dv.ui.player.PlaylistProvider
import com.github.axtv.dv.ui.player.VideoPlaylistProvider

object PlaybackUrlResolver {

    private var initialized = false

    /** 把 Settings 的点播清晰度/编码偏好接给核心层解析器（只初始化一次）。 */
    private val settingsAdapter = object : VideoPlayUrlResolver.Settings {

        override fun targetResolution(): VideoPlayUrlResolver.TargetResolution {
            val resolution = AppRuntime.settings?.videoResolution() ?: return VideoPlayUrlResolver.TargetResolution.AUTO
            return when (resolution.name) {
                "HIGHEST" -> VideoPlayUrlResolver.TargetResolution.HIGHEST
                "P1080" -> VideoPlayUrlResolver.TargetResolution.P1080
                "P720" -> VideoPlayUrlResolver.TargetResolution.P720
                "P480" -> VideoPlayUrlResolver.TargetResolution.P540
                else -> VideoPlayUrlResolver.TargetResolution.AUTO
            }
        }

        override fun codecPreference(): VideoPlayUrlResolver.CodecPreference =
            when (AppRuntime.settings?.videoCodec()?.name) {
                "H264" -> VideoPlayUrlResolver.CodecPreference.H264
                "H265" -> VideoPlayUrlResolver.CodecPreference.H265
                else -> VideoPlayUrlResolver.CodecPreference.AUTO
            }

        override fun rawResolutionPreference(): String =
            when (AppRuntime.settings?.videoResolution()?.name) {
                "HIGHEST" -> "highest"
                "P1080" -> "1080p"
                "P720" -> "720p"
                "P480" -> "480p"
                else -> "auto"
            }
    }

    @Synchronized
    fun ensureInitialized() {
        if (initialized) return
        VideoPlayUrlResolver.INSTANCE.init(settingsAdapter, null)
        initialized = true
    }

    fun playableUrl(aweme: Aweme): String {
        ensureInitialized()
        return VideoPlayUrlResolver.INSTANCE.playableUrl(aweme)
    }
}

/**
 * 用真实 aweme 列表构造播放器数据源。
 *
 * @param startIndex 首页点击的条目序号：把该条目旋转到队首，保证「点哪条先播哪条」，
 *                   连播仍按原列表顺序；不改变 PlayerSession 的换档/续播/兜底逻辑。
 */
fun awemePlaylistProvider(awemes: List<Aweme>, startIndex: Int = 0): PlaylistProvider {
    val ordered = if (awemes.isEmpty()) {
        emptyList()
    } else {
        val start = startIndex.coerceIn(0, awemes.lastIndex)
        awemes.drop(start) + awemes.take(start)
    }
    val entries = ordered.mapIndexed { index, aweme -> awemePlaylistEntry(aweme, index) }
    return VideoPlaylistProvider(entries).also {
        // 『历史记录容量』的消费点之一：起播即把这条记进本地「已看」环形缓冲。
        // 这里是所有播放路由（首页 / 精选 / 详情 / 历史 / 合集）的唯一收口，不改 ui/player 内部逻辑。
        ordered.firstOrNull()?.awemeId()?.let(ViewedVideoTracker::record)
    }
}

/**
 * 单条 Aweme → 播放器条目。
 *
 * @param index 仅用于缺少 awemeId 时的兜底 id（aweme_{index}）；
 *              播放器内续页追加时由调用方传入递增序号，避免与首批条目重名。
 */
fun awemePlaylistEntry(aweme: Aweme, index: Int = 0): VideoPlaylistProvider.Entry {
    val video = aweme.video()
    return VideoPlaylistProvider.Entry(
        id = aweme.awemeId().ifEmpty { "aweme_$index" },
        title = aweme.desc().ifEmpty { "无标题视频" },
        durationMs = video.duration().toLong(),
        video = video,
        resolvedUrl = PlaybackUrlResolver.playableUrl(aweme).takeIf { it.isNotEmpty() },
        author = aweme.author().nickname().orEmpty(),
        coverUrl = (
            video.cover()?.firstUrl()?.orEmpty()
                ?.takeIf { it.isNotEmpty() }
                ?: video.originCover()?.firstUrl()?.orEmpty()
            ).orEmpty(),
    )
}
