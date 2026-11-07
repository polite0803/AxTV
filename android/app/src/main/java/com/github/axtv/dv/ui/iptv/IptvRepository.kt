/*
 * 电视台直播 —— 数据仓库（Android 侧）。
 *
 * 职责：把「内置清单 + 用户推送的源」统一取回、解析成频道，按 group-title 分栏，
 *       并为每个频道聚合「同名的候选播放地址」（按内置优先、再按来源名排序）。
 *       聚合/分组/可见性判定全部交回核心层 ChannelCatalog 完成（PLAN R1/R2）。
 *
 * 边界与红线（见 docs/PLAN-电视台持久化与连通性.md §3.3）：
 *  - 只走公开可访问的清单，不伪造 Referer/UA 破解防盗链、不解密 DRM；
 *  - 每个源单独记录健康度：拉取/解析失败要带原因显式呈现，绝不把失败渲染成「没有频道」的假空态；
 *  - 仅在每个候选都确证死亡时隐藏（PLAN R1）；无候选地址时不隐藏——隐藏它会让用户永远看不到这条目。
 *  - 目录与活性是两份独立输入：目录几天拉一次、活性一天一次（PLAN P3）。
 */
package com.github.axtv.dv.ui.iptv

import axtv.iptv.BuiltInSources
import axtv.iptv.Channel
import axtv.iptv.ChannelCatalog
import axtv.iptv.GroupOverrides
import axtv.iptv.LivenessRecord
import axtv.iptv.Playlist
import axtv.iptv.PlaylistLoader
import axtv.iptv.SourceInputDetector
import com.github.axtv.dv.adapter.AppRuntime
import com.github.axtv.dv.panel.AndroidIptvStore
import com.github.axtv.dv.settings.OkHttpHttpFetcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

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
 * 聚合/分组/可见性判定全部交回核心层 ChannelCatalog 完成（PLAN R1/R2）。
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

        // 收集所有已解析源：内置 + 用户推送
        val allSources = mutableListOf<axtv.iptv.ChannelCatalog.ResolvedSource>()

        for ((id, url) in BuiltInSources.URLS) {
            if (id.startsWith("disabled")) continue
            val display = BuiltInSources.DISPLAY_NAMES[id] ?: id
            val playlist = PlaylistLoader.loadFromUrl(url, id, fetcher, timeout)
            allSources.add(axtv.iptv.ChannelCatalog.ResolvedSource(display, display, true, playlist))
        }

        for (s in store.sources()) {
            val raw = s.urlOrContent()
            val playlist = if (raw != null && SourceInputDetector.isContent(raw)) {
                PlaylistLoader.loadFromContent(raw, s.sourceId())
            } else {
                PlaylistLoader.loadFromUrl(raw, s.sourceId(), fetcher, timeout)
            }
            allSources.add(axtv.iptv.ChannelCatalog.ResolvedSource(s.name(), s.name(), false, playlist))
        }

        // === 步骤 1：从设置读取被禁用的内置 sourceId ===
        val disabledBuiltInIds = mutableSetOf<String>()
        val builtInDisabledStr = AppRuntime.settings?.iptvDisabledBuiltinIds() ?: ""
        if (builtInDisabledStr.isNotBlank()) {
            val disabledArr = JSONArray(builtInDisabledStr)
            for (i in 0 until disabledArr.length()) {
                disabledBuiltInIds.add(disabledArr.getString(i))
            }
        }

        // === 步骤 2：用核心层 ChannelCatalog.merge 聚合 ===
        val disabledSet = if (disabledBuiltInIds.isEmpty()) null else disabledBuiltInIds.toSet()
        val merged = ChannelCatalog.merge(allSources, disabledSet)

        // === 步骤 3：从设置读取 GroupOverrides 与 hideDead ===
        val overrides = GroupOverrides.parse(AppRuntime.settings?.iptvGroupOverrides() ?: "")
        val hideDead = AppRuntime.settings?.iptvHideDead() ?: false

        // === 步骤 4：应用分组与可见性过滤 ===
        val filtered = ChannelCatalog.groupBy(merged, overrides, null, hideDead)

        // === 步骤 5：转换为 IptvCatalog ===
        val groups = filtered.entries
            .map { (title, entries) ->
                val iptvChannels = entries.map { entry ->
                    val cand = ChannelCatalog.candidatesForChannel(merged, entry.id())
                    IptvChannel(
                        id = entry.id(),
                        name = entry.name(),
                        logoUrl = entry.logoUrl(),
                        sourceName = entry.sourceName(),
                        candidates = cand.filter { it.isNotBlank() }
                    )
                }
                IptvGroup(title = title, channels = iptvChannels)
            }
            .sortedWith(compareBy<IptvGroup> { it.channels.size }.reversed().thenBy { it.title })

        val total = groups.sumOf { it.channels.size }
        val result = IptvCatalog(groups = groups, sources = buildLivenessHealth(allSources, null), totalChannels = total)

        // 持久化缓存
        cached = result
        result
    }

    /** 为 IptvCatalog 的 sources 字段建立健康度列表。 */
    private fun buildLivenessHealth(
        allSources: List<axtv.iptv.ChannelCatalog.ResolvedSource>,
        liveness: Map<String, LivenessRecord>?
    ): List<IptvSourceHealth> {
        val result = mutableListOf<IptvSourceHealth>()
        for (r in allSources) {
            val pl = r.playlist
            result.add(IptvSourceHealth(
                sourceName = r.displayName,
                builtIn = r.builtIn,
                channels = if (pl != null && pl.ok()) pl.channels().size else 0,
                bytes = if (pl != null) pl.bytes() else 0,
                error = if (pl != null && pl.failed()) pl.rawError() else null,
            ))
        }
        return result.filter { it.channels > 0 || it.error != null }
    }

    /** 播放器按频道 id 取候选地址；目录未加载或 id 失效时返回空。 */
    fun candidatesForChannel(channelId: String): List<String> =
        cached?.channelIndex?.get(channelId)?.candidates ?: emptyList()

    fun channelName(channelId: String): String =
        cached?.channelIndex?.get(channelId)?.name ?: channelId

    /** 按列表展示顺序（分组顺序 + 组内顺序）的扁平频道表，且仅保留有可播候选的频道——用于播放页换台。 */
    fun orderedChannels(): List<IptvChannel> =
        cached?.groups?.flatMap { it.channels }?.filter { it.candidates.isNotEmpty() } ?: emptyList()

    /** 内部中间态：一个源解析后的展示名、是否内置、解析结果。 */
    data class ResolvedSource(
        val sourceName: String,
        val builtIn: Boolean,
        val playlist: Playlist,
    )
}
