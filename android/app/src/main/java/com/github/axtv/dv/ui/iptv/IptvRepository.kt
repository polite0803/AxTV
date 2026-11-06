/*
 * 电视台直播 —— 数据仓库（Android 侧）。
 *
 * 职责：把「内置清单 + 用户推送的源」统一取回、解析成频道，按 group-title 分栏，
 *       并为每个频道聚合「同名的候选播放地址」（按内置优先、再按来源名排序）。
 *
 * 复用核心层：解析走 axtv.iptv.PlaylistLoader（URL 拉取 / 正文解析都在这），
 *            频道名归一走 axtv.iptv.ChannelCatalog.normalizeName；本文件只做「取数 + 聚合 + 分组」。
 *
 * 边界与红线（见 docs/PLAN-电视直播.md §3.3）：
 *  - 只走公开可访问的清单，不伪造 Referer/UA 破解防盗链、不解密 DRM；
 *  - 每个源单独记录健康度：拉取/解析失败要带原因显式呈现，绝不把失败渲染成「没有频道」的假空态。
 */
package com.github.axtv.dv.ui.iptv

import axtv.iptv.BuiltInSources
import axtv.iptv.Channel
import axtv.iptv.ChannelCatalog
import axtv.iptv.Playlist
import axtv.iptv.PlaylistLoader
import axtv.iptv.SourceInputDetector
import com.github.axtv.dv.adapter.AppRuntime
import com.github.axtv.dv.panel.AndroidIptvStore
import com.github.axtv.dv.settings.OkHttpHttpFetcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 一个可播放频道：id 用归一化频道名（稳定、可直接进 URL），candidates 为有序候选地址。 */
data class IptvChannel(
    val id: String,
    val name: String,
    val logoUrl: String,
    val sourceName: String,
    val candidates: List<String>,
)

/** 一个分栏（group-title）。 */
data class IptvGroup(val title: String, val channels: List<IptvChannel>)

/** 单个源的健康度，供列表页顶部如实展示（失败/空/成功三种各一句，不混排）。 */
data class IptvSourceHealth(
    val sourceName: String,
    val builtIn: Boolean,
    val channels: Int,
    val bytes: Long,
    val error: String?,
)

/** 一次加载结果快照。 */
data class IptvCatalog(
    val groups: List<IptvGroup>,
    val sources: List<IptvSourceHealth>,
    val totalChannels: Int,
) {
    /** 全部频道平铺（供播放器按 id 反查候选）。 */
    val channelIndex: Map<String, IptvChannel> by lazy {
        groups.flatMap { it.channels }.associateBy { it.id }
    }

    /** 所有源都失败（没有任何频道且没有任何源报「空」）时用于区分「网络全挂」与「真的没频道」。 */
    val allFailed: Boolean get() = sources.isNotEmpty() && sources.all { it.error != null }
}

/**
 * 加载与解析仓库（进程单例，仅只读核心层与 Settings）。
 * 网络全部走 [Dispatchers.IO]；调用方（Composable）负责 loading/error 三态。
 */
object IptvRepository {

    private val fetcher by lazy { OkHttpHttpFetcher() }
    private val store = AndroidIptvStore()

    /** 最近一次成功加载的目录：列表页加载后，播放页按 channelId 反查候选无需重新拉网。 */
    @Volatile
    private var cached: IptvCatalog? = null

    fun cachedCatalog(): IptvCatalog? = cached

    /** 一次拉取并聚合全部源。失败不抛异常：每个源各自记健康度，能出多少频道出多少。 */
    suspend fun load(): IptvCatalog = withContext(Dispatchers.IO) {
        val timeout = (AppRuntime.settings?.iptvSourceLoadTimeoutSeconds() ?: 10).coerceAtLeast(1)

        val resolved = ArrayList<ResolvedSource>()

        for ((id, url) in BuiltInSources.URLS) {
            if (id.startsWith("disabled")) continue
            val display = BuiltInSources.DISPLAY_NAMES[id] ?: id
            resolved += ResolvedSource(display, true, PlaylistLoader.loadFromUrl(url, id, fetcher, timeout))
        }

        for (s in store.sources()) {
            val raw = s.urlOrContent()
            val playlist = if (SourceInputDetector.isContent(raw)) {
                PlaylistLoader.loadFromContent(raw, s.sourceId())
            } else {
                PlaylistLoader.loadFromUrl(raw, s.sourceId(), fetcher, timeout)
            }
            resolved += ResolvedSource(s.name(), false, playlist)
        }

        val health = resolved.map { r ->
            val pl = r.playlist
            IptvSourceHealth(
                sourceName = r.sourceName,
                builtIn = r.builtIn,
                channels = if (pl.ok()) pl.channels().size else 0,
                bytes = pl.bytes(),
                error = if (pl.failed()) pl.rawError() else null,
            )
        }

        // 聚合：normalizedName → 候选地址（内置优先，按来源名次之）；normalizedName → 展示信息
        val candidateMap = LinkedHashMap<String, MutableList<String>>()
        val nameToChannel = LinkedHashMap<String, Channel>()
        val nameToSource = LinkedHashMap<String, String>()

        for (r in resolved.sortedByDescending { it.builtIn }) {
            if (!r.playlist.ok()) continue
            for (ch in r.playlist.channels()) {
                val key = ChannelCatalog.normalizeName(ch.name())
                if (key.isBlank()) continue
                val url = ch.url()
                if (url.isNotBlank()) {
                    candidateMap.getOrPut(key) { ArrayList() }
                        .apply { if (!contains(url)) add(url) }
                }
                // 首个（内置优先排序后）出现的频道提供展示信息
                if (!nameToChannel.containsKey(key)) {
                    nameToChannel[key] = ch
                    nameToSource[key] = r.sourceName
                }
            }
        }

        val groupMap = LinkedHashMap<String, MutableList<IptvChannel>>()
        for ((key, ch) in nameToChannel) {
            val group = ch.group().ifBlank { "未分组" }
            val candidates = candidateMap[key] ?: listOf(ch.url())
            groupMap.getOrPut(group) { ArrayList() }.add(
                IptvChannel(
                    id = key,
                    name = ch.normalizedName().ifBlank { ch.name() },
                    logoUrl = ch.logoUrl(),
                    sourceName = nameToSource[key].orEmpty(),
                    candidates = candidates.filter { it.isNotBlank() },
                )
            )
        }

        val groups = groupMap.entries
            .map { (title, list) -> IptvGroup(title, list.sortedBy { it.name }) }
            .sortedWith(compareByDescending<IptvGroup> { it.channels.size }.thenBy { it.title })

        val total = groupMap.values.sumOf { it.size }
        val result = IptvCatalog(groups = groups, sources = health, totalChannels = total)
        cached = result
        result
    }

    /** 播放器按频道 id 取候选地址；目录未加载或 id 失效时返回空。 */
    fun candidatesForChannel(channelId: String): List<String> =
        cached?.channelIndex?.get(channelId)?.candidates ?: emptyList()

    fun channelName(channelId: String): String =
        cached?.channelIndex?.get(channelId)?.name ?: channelId

    /** 按列表展示顺序（分组顺序 + 组内顺序）的扁平频道表，且仅保留有可播候选的频道——用于播放页换台。 */
    fun orderedChannels(): List<IptvChannel> =
        cached?.groups?.flatMap { it.channels }?.filter { it.candidates.isNotEmpty() } ?: emptyList()
}

/** 内部中间态：一个源解析后的展示名、是否内置、解析结果。 */
private class ResolvedSource(
    val sourceName: String,
    val builtIn: Boolean,
    val playlist: Playlist,
)
