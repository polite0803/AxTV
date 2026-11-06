/*
 * 合集 / 系列 / 追更数据层（线 F）。
 *
 * 只依赖核心层门面 axtv.api.CollectionApi / UserApi / DouyinApi，不直接拼 URL，
 * 也不触碰 refactor/ 其它模块或 Android 资源。
 *
 * 接口与返回类型出处：
 *   - mixDetail / mixAwemes / mixListCollection / seriesDetail / seriesAwemes
 *     → refactor/api/axtv/api/CollectionApi.java
 *   - 追更写回 recordMixProgress（scene="pc_web"）→ 原 n11.m8062t0:4700-4708
 *   - series 列表需要 sec_user_id，由 UserApi.profileSelf() 的 Author.secUid 提供
 */
package com.github.axtv.dv.ui.collection

import axtv.api.ApiException
import axtv.api.CollectionApi
import axtv.api.DouyinApi
import axtv.api.UserApi
import axtv.model.Aweme
import axtv.model.MixInfo
import com.github.axtv.dv.adapter.AppRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 当前详情来自 mix 接口还是 series 接口。 */
enum class CollectionSource { MIX, SERIES, FEATURED }

/** 合集内的一集（序号从 1 开始，按接口返回顺序）。 */
data class CollectionEpisode(
    val awemeId: String,
    val number: Int,
    val title: String,
    val durationMs: Long,
    val coverUrl: String,
    val authorName: String,
    /** 服务端记录的单集播放位置（毫秒）；仅用于恢复进度的兜底推断，不是集号。 */
    val playProgressMs: Long,
    /** 接口原始作品：连播队列直接用它，避免再逐集请求详情。 */
    val aweme: Aweme,
)

/** 一页剧集 + 分页游标。 */
data class EpisodePage(
    val episodes: List<CollectionEpisode>,
    val hasMore: Boolean,
    val cursor: Long,
)

/** 合集详情（mix / series 归一化后的展示模型）。 */
data class CollectionDetail(
    /** 路由入参 id（widget 的 mix/detail?id= 原样透传）。 */
    val routeId: String,
    /** 接口返回的合集/系列 id（MixInfo.mixId，series 经 series_id 备用 key 映射）。 */
    val canonicalId: String,
    val name: String,
    val desc: String,
    val coverUrl: String,
    /** MixInfo 无作者字段，作者取自剧集 aweme.author.nickname；可能为空。 */
    val author: String,
    /** 展示总集数 = max(total_episode, updated_to_episode, current_episode, 已加载集数)。 */
    val total: Int,
    /** 服务端记录的集进度（statis.current_episode；0 表示没有进度）。 */
    val currentEpisode: Int,
    val updatedToEpisode: Int,
    val totalEpisode: Int,
    val source: CollectionSource,
    val episodes: List<CollectionEpisode>,
    val hasMore: Boolean,
    val cursor: Long,
)

/** 合集列表项（MixInfo 无作者字段，列表只展示可核实字段）。 */
data class CollectionSummary(
    val id: String,
    val name: String,
    val desc: String,
    val coverUrl: String,
    val currentEpisode: Int,
    val total: Int,
    val source: CollectionSource,
)

data class CollectionListPage(
    val items: List<CollectionSummary>,
    val hasMore: Boolean,
    val cursor: Long,
)

object CollectionRepository {

    private const val PAGE_SIZE = 20

    /** 首屏为定位进度最多自动续取的页数（20×6=120 集上限）。 */
    private const val MAX_AUTO_PAGES = 6

    /** 原 n11.m8015G0 系列内视频调用点用 pull_type=2（n11.java:2020）。 */
    private const val SERIES_PULL_TYPE = 2

    /** 原 n11.m8062t0:4703 追更写入的 scene 固定为 pc_web。 */
    private const val RECORD_SCENE = "pc_web"

    @Volatile
    private var cachedSelfSecUserId: String? = null

    /**
     * 加载合集详情与首屏剧集。
     *
     * 路由只带一个 id（mix/detail?id=），无法先验区分 mix / series：
     * 先按 mix 请求，mix_info 存在即判定为 mix；否则再按 series 请求，
     * series_info 经 MixDetailResponse 的备用 key 映射到同一个 mixInfo 字段。
     */
    suspend fun loadDetail(routeId: String): CollectionDetail = withContext(Dispatchers.IO) {
        val collectionApi = CollectionApi(requireApi())

        var mixInfo: MixInfo? = null
        var mixFailure: Throwable? = null
        try {
            mixInfo = collectionApi.mixDetail(routeId).mixInfo()
        } catch (t: Throwable) {
            mixFailure = t
        }

        // mix_info 与 series_info 共用同一个字段（备用 key）；只有拿到有效 id/名称才认定命中，
        // 否则继续尝试 series 详情，避免把空壳 mix_info 误判成合集。
        val usableMix = mixInfo?.let { it.mixId().isNotBlank() || it.mixName().isNotBlank() } == true
        val (info, source) = if (usableMix) {
            mixInfo!! to CollectionSource.MIX
        } else {
            val seriesInfo = try {
                collectionApi.seriesDetail(routeId).mixInfo()
            } catch (t: Throwable) {
                throw mixFailure ?: t
            }
            val usableSeries = seriesInfo?.let { it.mixId().isNotBlank() || it.mixName().isNotBlank() } == true
            if (!usableSeries) {
                throw mixFailure ?: IllegalStateException("未获取到合集信息（id=$routeId）")
            }
            seriesInfo!! to CollectionSource.SERIES
        }

        val canonicalId = info.mixId().takeIf { it.isNotBlank() } ?: routeId
        val statis = info.statis()

        // 首屏先取一页；若服务端进度指向更靠后的集，自动续取到覆盖该集为止（最多 MAX_AUTO_PAGES 页），
        // 否则「进入时定位到上次播放的集」会因目标集未加载而失效。
        val firstPage = loadEpisodePage(collectionApi, canonicalId, source, cursor = 0L, startNumber = 1)
        var episodes = firstPage.episodes
        var hasMore = firstPage.hasMore
        var cursor = firstPage.cursor
        var pages = 1
        val targetEpisode = statis.currentEpisode()
        while (hasMore && episodes.size < targetEpisode && pages < MAX_AUTO_PAGES) {
            val next = loadEpisodePage(collectionApi, canonicalId, source, cursor, episodes.size + 1)
            if (next.episodes.isEmpty()) {
                hasMore = next.hasMore
                cursor = next.cursor
                break
            }
            episodes = episodes + next.episodes
            hasMore = next.hasMore
            cursor = next.cursor
            pages++
        }

        // 进度优先取 statis.currentEpisode：桌面「合集追更」组件（WidgetStore.java:243）
        // 与播放器合集卡（fv4.java:897）均把该字段当作「第 N 集」展示。
        // 仅当它为 0 时，再用单集 play_progress 兜底推断最近有播放记录的一集（推断值）。
        val progressFallback = episodes.indexOfLast { it.playProgressMs > 0L } + 1
        val current = statis.currentEpisode().takeIf { it > 0 } ?: progressFallback
        val total = maxOf(
            statis.totalEpisode(),
            statis.updatedToEpisode(),
            statis.currentEpisode(),
            episodes.size,
        )

        CollectionDetail(
            routeId = routeId,
            canonicalId = canonicalId,
            name = info.mixName(),
            desc = info.desc(),
            coverUrl = info.coverUrl().firstUrl(),
            author = episodes.firstNotNullOfOrNull { it.authorName.takeIf(String::isNotBlank) }.orEmpty(),
            total = total,
            currentEpisode = current,
            updatedToEpisode = statis.updatedToEpisode(),
            totalEpisode = statis.totalEpisode(),
            source = source,
            episodes = episodes,
            hasMore = hasMore,
            cursor = cursor,
        )
    }

    /** 追加下一页剧集；序号从已加载数量继续。 */
    suspend fun loadMoreEpisodes(detail: CollectionDetail): EpisodePage = withContext(Dispatchers.IO) {
        loadEpisodePage(
            collectionApi = CollectionApi(requireApi()),
            id = detail.canonicalId,
            source = detail.source,
            cursor = detail.cursor,
            startNumber = detail.episodes.size + 1,
        )
    }

    /** 收藏的合集列表（mix/listcollection）。 */
    suspend fun loadMixList(cursor: Long): CollectionListPage = withContext(Dispatchers.IO) {
        val response = CollectionApi(requireApi()).mixListCollection(cursor, PAGE_SIZE)
        val items = response.mixInfos().orEmpty()
            .filter { it.mixId().isNotBlank() }
            .map { it.toSummary(CollectionSource.MIX) }
        CollectionListPage(
            items = items,
            hasMore = response.hasMore(),
            cursor = nextCursor(response.hasMore(), response.cursor(), cursor, items.size),
        )
    }

    /** 精选合集列表。 */
    suspend fun loadFeaturedMixList(cursor: Long): CollectionListPage = withContext(Dispatchers.IO) {
        val response = CollectionApi(requireApi()).mixListCollection(cursor, PAGE_SIZE)
        val items = response.mixInfos().orEmpty()
            .filter { it.mixId().isNotBlank() }
            .map { it.toSummary(CollectionSource.FEATURED) }
        CollectionListPage(
            items = items,
            hasMore = response.hasMore(),
            cursor = nextCursor(response.hasMore(), response.cursor(), cursor, items.size),
        )
    }

    /**
     * 我的系列列表（series/list）。
     * 该接口必须带 sec_user_id，用当前登录账号的 secUid（UserApi.profileSelf）。
     */
    suspend fun loadMySeriesList(cursor: Long): CollectionListPage = withContext(Dispatchers.IO) {
        val api = requireApi()
        val secUserId = selfSecUserId(api)
        val response = CollectionApi(api).seriesList(secUserId, cursor, PAGE_SIZE)
        val items = response.mixInfos().orEmpty()
            .filter { it.mixId().isNotBlank() }
            .map { it.toSummary(CollectionSource.SERIES) }
        CollectionListPage(
            items = items,
            hasMore = response.hasMore(),
            cursor = nextCursor(response.hasMore(), response.cursor(), cursor, items.size),
        )
    }

    /**
     * 写回追更记录（mix/watch/record）。
     *
     * 仅对 mix 写入：接口参数是 mix_id/item_id/episode/scene，没有 series 对应端点。
     * episode 从 1 开始，必须 > 0；scene 固定 pc_web（原 n11.m8062t0）。
     * 返回 status_code != 0 时抛错，由调用方展示为「同步失败」，但不阻断播放。
     */
    suspend fun recordProgress(mixId: String, awemeId: String, episode: Int) {
        if (mixId.isBlank() || awemeId.isBlank() || episode <= 0) return
        withContext(Dispatchers.IO) {
            val response = CollectionApi(requireApi())
                .recordMixProgress(mixId, awemeId, episode, RECORD_SCENE)
            if (!response.isOk()) {
                throw IllegalStateException(
                    "进度同步失败：${response.statusMsg()}（status_code=${response.statusCode()}）",
                )
            }
        }
    }

    private suspend fun loadEpisodePage(
        collectionApi: CollectionApi,
        id: String,
        source: CollectionSource,
        cursor: Long,
        startNumber: Int,
    ): EpisodePage {
        if (source == CollectionSource.MIX) {
            val response = collectionApi.mixAwemes(id, cursor, PAGE_SIZE)
            val episodes = response.flatAwemes().mapIndexed { index, aweme ->
                aweme.toEpisode(startNumber + index)
            }
            return EpisodePage(
                episodes = episodes,
                hasMore = response.hasMore(),
                cursor = nextCursor(
                    response.hasMore(),
                    maxOf(response.cursor(), response.maxCursor()),
                    cursor,
                    episodes.size,
                ),
            )
        }

        val response = collectionApi.seriesAwemes(id, cursor, PAGE_SIZE, SERIES_PULL_TYPE)
        val episodes = response.works().mapIndexed { index, aweme ->
            aweme.toEpisode(startNumber + index)
        }
        return EpisodePage(
            episodes = episodes,
            hasMore = response.hasMore(),
            cursor = nextCursor(
                response.hasMore(),
                maxOf(response.cursor() ?: 0L, response.maxCursor()),
                cursor,
                episodes.size,
            ),
        )
    }

    private fun selfSecUserId(api: DouyinApi): String {
        cachedSelfSecUserId?.takeIf { it.isNotBlank() }?.let { return it }
        val secUserId = UserApi(api).profileSelf().user()?.secUid().orEmpty()
        if (secUserId.isBlank()) {
            throw ApiException.loginRequired()
        }
        cachedSelfSecUserId = secUserId
        return secUserId
    }

    private fun MixInfo.toSummary(source: CollectionSource): CollectionSummary {
        val statis = statis()
        return CollectionSummary(
            id = mixId(),
            name = mixName(),
            desc = desc(),
            coverUrl = coverUrl().firstUrl(),
            currentEpisode = statis.currentEpisode(),
            total = maxOf(statis.totalEpisode(), statis.updatedToEpisode(), statis.currentEpisode()),
            source = source,
        )
    }

    private fun Aweme.toEpisode(number: Int): CollectionEpisode {
        val video = video()
        val cover = video.cover().firstUrl()
            .ifBlank { video.originCover().firstUrl() }
            .ifBlank { video.dynamicCover().firstUrl() }
        return CollectionEpisode(
            awemeId = awemeId(),
            number = number,
            title = desc().takeIf { it.isNotBlank() } ?: "第 $number 集",
            durationMs = video.duration().toLong(),
            coverUrl = cover,
            authorName = author().nickname(),
            playProgressMs = playProgress().playProgress(),
            aweme = this,
        )
    }

    /**
     * 下一页游标兜底：接口 has_more=true 但两个游标字段都为 0 时，用「请求游标 + 本页数量」推进。
     * （原 mix/listcollection 与 mix/aweme 的 cursor 语义未在反编译中确证，属推断。）
     */
    private fun nextCursor(
        hasMore: Boolean,
        rawCursor: Long,
        requestedCursor: Long,
        loadedCount: Int,
    ): Long = if (!hasMore) {
        0L
    } else {
        rawCursor.takeIf { it > 0L } ?: (requestedCursor + loadedCount)
    }

    private fun requireApi(): DouyinApi =
        AppRuntime.api ?: throw IllegalStateException("抖音接口尚未初始化，请重启应用")
}

/** 是否属于「合集已失效/不存在」而非网络或登录问题：据此决定是否提供「从本机列表移除」。 */
fun Throwable.isCollectionGone(): Boolean = when (this) {
    is ApiException -> kind() == ApiException.Kind.API_STATUS ||
        (kind() == ApiException.Kind.HTTP_STATUS && httpStatus() == 404)

    else -> message.orEmpty().let {
        it.contains("未获取到合集信息") || it.contains("不存在") || it.contains("已失效")
    }
}

/** 把 ApiException 映射为可读中文提示；非 ApiException 保留原 message。 */
fun Throwable.collectionMessage(): String = when (this) {
    is ApiException -> when (kind()) {
        ApiException.Kind.NETWORK -> "网络连接失败，请检查网络后重试"
        ApiException.Kind.HTTP_STATUS -> "服务器返回 HTTP ${httpStatus()}，请稍后重试"
        ApiException.Kind.EMPTY_BODY -> "服务器返回空响应，登录可能已过期，请在设置中重新登录"
        ApiException.Kind.HEADER_ERROR -> "接口校验失败，请稍后重试"
        ApiException.Kind.API_STATUS -> message ?: "接口返回错误，请稍后重试"
        ApiException.Kind.LOGIN_REQUIRED -> message ?: "需要登录后才能查看"
        ApiException.Kind.RATE_LIMITED -> message ?: "请求过于频繁，请稍后再试"
        ApiException.Kind.MISSING_CREDENTIAL -> message ?: "缺少互动安全凭据，请先在设置中补充"
        ApiException.Kind.DECODE -> "响应解析失败，请稍后重试"
    }

    else -> message?.takeIf { it.isNotBlank() } ?: "未知错误"
}
