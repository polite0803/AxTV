/*
 * 播放数据源接口：由调用方（信息流 / 合集 / 搜索页）提供条目与取流结果。
 *
 * 播放器只认「条目 → 直链 / 备用直链 / 多码率列表」，不直接依赖 API，
 * 避免把网络层硬连进 UI。多码率列表来源为 axtv.model.BitRate（取流结果）。
 */
package com.github.axtv.dv.ui.player

import axtv.model.BitRate
import axtv.model.Cover
import axtv.model.PlayAddr
import axtv.model.Video

/** 播放列表中的一条。 */
data class PlaylistItem(
    val id: String,
    val title: String = "",
    val durationMs: Long = 0L,
    /** 作者昵称；用于桌面「正在播放」小组件，空表示未知。 */
    val author: String = "",
    /** 封面直链；用于桌面「正在播放」小组件，空表示无封面。 */
    val coverUrl: String = "",
)

/**
 * 一路可选码率。
 *
 * @param label   浮层展示文案（如 1080P / 原画）
 * @param url     该档位直链
 * @param height  像素高度，用于排序与选中态匹配，未知传 0
 * @param kbps    码率（bps），仅用于排序，未知传 0
 */
data class QualityTrack(
    val label: String,
    val url: String,
    val height: Int = 0,
    val kbps: Int = 0,
)

interface PlaylistProvider {

    /** 当前播放列表（按播放顺序）。 */
    fun items(): List<PlaylistItem>

    /** 条目主直链；解析失败返回 null。 */
    fun resolveUrl(itemId: String): String?

    /** 备用直链（主链播放失败时切一次）；没有返回 null。 */
    fun backupUrl(itemId: String): String? = null

    /** 该条目的多码率列表（降序）；没有多码率返回空。 */
    fun qualities(itemId: String): List<QualityTrack> = emptyList()

    /**
     * 回填单独拉到的作品详情（设置项「音视频分离拉取」）。
     *
     * 信息流条目可能不带取流信息，这时按 id 单发一次 itemVideoDetail，
     * 用返回的 video 覆盖该条目，见 ui/nav/DetailPrefetcher。默认不接（无详情可回填的数据源）。
     */
    fun adoptDetail(itemId: String, video: Video) = Unit
}

/**
 * 基于取流结果 [Video] 的实现：码率列表直接来自 `video.bitRate`。
 *
 * <p>调用方只需把接口返回的作品数据填进 [Entry]，无需在此发起网络请求。
 */
class VideoPlaylistProvider(private val entries: List<Entry>) : PlaylistProvider {

    /**
     * @param resolvedUrl 已解析好的主直链（如走 VideoPlayUrlResolver 的结果）；
     *                    为 null 时回退到码率列表 / play_addr。
     * @param author      作者昵称，供桌面「正在播放」小组件展示。
     * @param coverUrl    封面直链，供桌面「正在播放」小组件展示。
     */
    data class Entry(
        val id: String,
        val title: String = "",
        val durationMs: Long = 0L,
        val video: Video,
        val resolvedUrl: String? = null,
        val author: String = "",
        val coverUrl: String = "",
    )

    /**
     * 音视频分离拉取回填的取流信息：awemeId → video。
     * 写入来自后台线程（见 [adoptDetail] 的 @Synchronized），读取都在同一把锁内。
     */
    private val detailVideos = HashMap<String, Video>()

    override fun items(): List<PlaylistItem> =
        entries.map {
            PlaylistItem(
                id = it.id,
                title = it.title,
                durationMs = it.durationMs,
                author = it.author,
                coverUrl = it.coverUrl,
            )
        }

    @Synchronized
    override fun adoptDetail(itemId: String, video: Video) {
        detailVideos[itemId] = video
    }

    @Synchronized
    override fun resolveUrl(itemId: String): String? {
        val entry = entry(itemId) ?: return null
        entry.resolvedUrl?.takeIf { it.isNotEmpty() }?.let { return it }
        qualities(itemId).firstOrNull()?.url?.let { return it }
        val video = videoOf(entry)
        video.playAddr().urlList().firstOrNull()?.let { return it }
        return video.playAddr265().urlList().firstOrNull()
    }

    @Synchronized
    override fun backupUrl(itemId: String): String? {
        val video = entry(itemId)?.let { videoOf(it) } ?: return null
        val sameGearBackup = video.playAddr().urlList().getOrNull(1)
        if (sameGearBackup != null) {
            return sameGearBackup
        }
        return video.playAddr265().urlList().firstOrNull()
    }

    @Synchronized
    override fun qualities(itemId: String): List<QualityTrack> {
        val video = entry(itemId)?.let { videoOf(it) } ?: return emptyList()
        val fromBitRate = video.bitRate()
            .filter { it.hasUrls() }
            .map { bitRateTrack(it) }
            .sortedWith(compareByDescending<QualityTrack> { it.height }.thenByDescending { it.kbps })
        if (fromBitRate.isNotEmpty()) {
            return fromBitRate
        }
        val primary = video.playAddr().urlList().firstOrNull() ?: return emptyList()
        return listOf(QualityTrack("原画", primary))
    }

    private fun entry(itemId: String): Entry? = entries.firstOrNull { it.id == itemId }

    /** 条目实际取流信息：单独拉到的详情优先（音视频分离拉取回填）。 */
    private fun videoOf(entry: Entry): Video = detailVideos[entry.id] ?: entry.video

    private fun bitRateTrack(bitRate: BitRate): QualityTrack {
        val height = bitRate.playAddr().height()
        val label = when {
            height > 0 -> "${height}P"
            bitRate.gearName().isNotEmpty() -> bitRate.gearName()
            else -> "原画"
        }
        return QualityTrack(
            label = label,
            url = bitRate.playAddr().urlList().first(),
            height = height,
            kbps = bitRate.bitRateValue(),
        )
    }
}

/**
 * 演示用数据源：让播放器在「无接口数据」时也能出画面、验证切码率/连播。
 *
 * <p>注意：这些是 Google 公共示例片，两个「画质」指向的是不同示例片而非同一片的不同码率，
 * 仅用于验证切轨链路，不代表真实业务数据。真实数据请由调用方构造 [VideoPlaylistProvider]。
 */
fun demoPlaylistProvider(): PlaylistProvider {
    val base = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/"
    val entries = listOf(
        VideoPlaylistProvider.Entry(
            id = "demo-1",
            title = "示例片 1 · For Bigger Blazes",
            durationMs = 15_000L,
            video = video(
                high = base + "ForBiggerBlazes.mp4",
                low = base + "ForBiggerFun.mp4",
            ),
        ),
        VideoPlaylistProvider.Entry(
            id = "demo-2",
            title = "示例片 2 · For Bigger Fun",
            durationMs = 60_000L,
            video = video(
                high = base + "ForBiggerFun.mp4",
                low = base + "ForBiggerJoyrides.mp4",
            ),
        ),
    )
    return VideoPlaylistProvider(entries)
}

private fun video(high: String, low: String): Video {
    val highTrack = bitRate("demo_1080_0", high, height = 1080)
    val lowTrack = bitRate("demo_540_0", low, height = 540)
    return Video(
        listOf(highTrack, lowTrack),
        PlayAddr(listOf(high), "", 0, 1080, 0L),
        PlayAddr(),
        PlayAddr(),
        Cover(),
        Cover(),
        Cover(),
        emptyList(),
        0,
        1920,
        1080,
        "1080p",
        "",
    )
}

private fun bitRate(gearName: String, url: String, height: Int): BitRate = BitRate(
    gearName,
    0,
    0,
    0,
    0,
    0,
    "mp4",
    PlayAddr(listOf(url), "", 0, height, 0L),
)
