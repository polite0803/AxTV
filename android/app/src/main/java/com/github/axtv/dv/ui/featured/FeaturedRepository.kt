/*
 * 精选频道数据层（对应反编译 qg5 的「精选/分区内容列表」，仓储原型 f50）。
 *
 * 只依赖核心层门面 axtv.api.CollectionApi / FeedApi，不直接拼 URL：
 *   - 分区列表 → CollectionApi.channelSetting（/home/channel/setting）
 *   - 分区内容 → FeedApi.moduleFeed（/module/feed，按 channel id 拉分组）
 * 分页阈值、预取条数、数量上限、空判定全部复用 axtv.ui.ChannelListSpec，不另立口径。
 *
 * 参数取值来源（反编译侧）：
 *   - channel 固定 "channel_pc_web"           src/app/p000/n11.java:877
 *   - 加载更多 pull_type = 2                  src/app/p000/n11.java:2414
 *   - refresh_index：首屏取 1、翻页取页序号    与 FeedDataSource 的 tab/feed 用法一致
 */
package com.github.axtv.dv.ui.featured

import axtv.api.ApiException
import axtv.api.CollectionApi
import axtv.api.DouyinApi
import axtv.api.FeedApi
import axtv.app.FeedDataSource
import axtv.model.Aweme
import axtv.model.ChannelConfigItem
import axtv.ui.ChannelListSpec
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.github.axtv.dv.adapter.AppRuntime
import com.github.axtv.dv.ui.collection.collectionMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 一个精选分区（对位 ChannelListSpec.ChannelGroup，并补上 moduleFeed 的分页状态）。 */
data class FeaturedChannel(
    /** 分区 id：频道设置的 key，即 moduleFeed 的 tag_id。 */
    val tagId: String,
    /** 分区名：Tab 文本。 */
    val name: String,
    /** 分区标题：仓储定位键 / 播放页信息面板标题；当前与 name 同源。 */
    val title: String,
    val videos: List<Aweme> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    /** 服务端 has_more；同时充当 ChannelListSpec 的 loadable（空 && !loadable → EMPTY）。 */
    val hasMore: Boolean = true,
    /** 下一次 moduleFeed 的 refresh_index。 */
    val nextPageIndex: Int = 1,
    /** 已展示作品 id（原 kq5 维护最近 10 个，作为 presented_ids / pre_item_ids 回传）。 */
    val presentedIds: List<String> = emptyList(),
) {
    /** 交给 ChannelListSpec 的只读视图（videos 保持同一实例，不复制）。 */
    fun toSpecGroup(): ChannelListSpec.ChannelGroup =
        ChannelListSpec.ChannelGroup(name, title, videos, loading, error, hasMore)
}

object FeaturedRepository {

    /** n11.java:877 的固定 channel 参数。 */
    private const val CHANNEL_SETTING_PARAM = "channel_pc_web"

    /** 与首页一致的每页条数（FeedDataSource.DEFAULT_PAGE_SIZE；Java 常量不能用于 const val）。 */
    private val PAGE_SIZE = FeedDataSource.DEFAULT_PAGE_SIZE

    /** 首屏 refresh_index / pull_type；翻页 pull_type 见 FILE_HEAD 注释。 */
    private const val FIRST_REFRESH_INDEX = 1
    private const val PULL_TYPE_REFRESH = 1
    private const val PULL_TYPE_LOAD_MORE = 2

    private const val STATUS_LOGIN_REQUIRED = 8
    private const val STATUS_RATE_LIMITED = 2154

    /** 频道设置里「全部」分区的 key（服务端下发 "0"，内置兜底表用空串，两者同义）。 */
    private fun isAllTag(tagId: String): Boolean = tagId.isEmpty() || tagId == "0"

    /** presented_ids 回传上限（原 kq5 取最近 10 个）。 */
    private const val PRESENTED_ID_LIMIT = 10

    /**
     * 内置频道表：服务端列表为空时的兜底（逐字照抄 f50.f5850j）。
     * 顺序与 id 都与原版一致，「全部」用空串表示。
     */
    private val FALLBACK_CHANNELS = listOf(
        "全部" to "",
        "公开课" to "100000",
        "游戏" to "300205",
        "二次元" to "300206",
        "音乐" to "300209",
        "影视" to "300215",
        "美食" to "300204",
        "知识" to "300213",
        "小剧场" to "300214",
        "生活vlog" to "300216",
        "体育" to "300207",
        "旅行" to "300221",
        "亲子" to "300217",
        "动物" to "300220",
        "三农" to "300219",
        "汽车" to "300228",
        "美妆穿搭" to "300222",
    ).map { (name, tagId) -> FeaturedChannel(tagId = tagId, name = name, title = name) }

    /** 最近一次成功加载的分区列表快照，供「精选播放」路由构造播放队列。 */
    var cachedAwemes by mutableStateOf<List<Aweme>>(emptyList())
        private set

    fun cache(items: List<Aweme>) {
        cachedAwemes = items.toList()
    }

    /**
     * 『精选自动播放』的闸门：每个分区在本次会议里只允许自动起播一次。
     *
     * 播放器返回时 FeaturedScreen 会重新组合，没有这个闸门就会一遍遍把用户踢回播放器。
     */
    private val autoPlayedTags = mutableSetOf<String>()

    fun claimAutoPlay(tagId: String): Boolean = autoPlayedTags.add(tagId)

    /** 是否为「未配置 Cookie」的空态（ChannelListSpec.featuredPageState 的 hasCookie 入参）。 */
    fun hasCookie(): Boolean =
        AppRuntime.api?.cookies()?.cookieHeader()?.isNotEmpty() == true

    /** 拉分区列表；接口 data 为空视为「空列表」而不是错误。 */
    suspend fun loadChannels(): Result<List<FeaturedChannel>> = withContext(Dispatchers.IO) {
        try {
            val response = CollectionApi(requireApi()).channelSetting(CHANNEL_SETTING_PARAM)
            requireSuccess(response.statusCode(), "频道列表")
            val remote = response.configInfo().orEmpty()
                .asSequence()
                .map { it.toFeaturedChannel() }
                .filter { it.tagId.isNotEmpty() && it.name.isNotEmpty() }
                .toList()
            // 原版 f50 在服务端列表为空时回退到内置频道表（f5849i/f5850j），行为照抄。
            Result.success(if (remote.isEmpty()) FALLBACK_CHANNELS else remote)
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    /** 分区初载（q30 预取 / Tab 切换时调用）；已加载或正在加载的分区直接返回原值。 */
    suspend fun loadInitial(channel: FeaturedChannel): Result<FeaturedChannel> =
        withContext(Dispatchers.IO) {
            if (channel.loading || channel.videos.isNotEmpty() || !channel.hasMore) {
                return@withContext Result.success(channel)
            }
            try {
                // module/feed 已下线（404 Unsupported path），分区内容统一走 tab/feed 的 tag_id。
                val response = FeedApi(requireApi()).tabFeed(
                    PAGE_SIZE,
                    FIRST_REFRESH_INDEX,
                    PULL_TYPE_REFRESH,
                    emptyList(),
                    if (isAllTag(channel.tagId)) "" else channel.tagId,
                )
                requireSuccess(response.statusCode(), "分区「${channel.name}」")
                val videos = response.flatAwemes()
                Result.success(
                    channel.copy(
                        videos = videos,
                        loading = false,
                        error = null,
                        hasMore = response.hasMore(),
                        nextPageIndex = FIRST_REFRESH_INDEX + 1,
                        presentedIds = videos.map { it.awemeId() },
                    ),
                )
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (t: Throwable) {
                Result.success(channel.copy(loading = false, error = t.collectionMessage()))
            }
        }

    /**
     * 加载更多：先按 ChannelListSpec.repoAcceptsLoadMore 复核（f50.m4016s:207），
     * 未达阈值或正在加载时原样返回，不重复请求。
     *
     * @param lastVisibleIndex 视频网格最后可见项下标（仅供参考，判定走规格函数）。
     */
    suspend fun loadMore(channel: FeaturedChannel, lastVisibleIndex: Int): Result<FeaturedChannel> =
        withContext(Dispatchers.IO) {
            if (!ChannelListSpec.repoAcceptsLoadMore(lastVisibleIndex, channel.toSpecGroup())) {
                return@withContext Result.success(channel)
            }
            val page = channel.nextPageIndex
            try {
                val response = FeedApi(requireApi()).tabFeed(
                    PAGE_SIZE,
                    page,
                    PULL_TYPE_LOAD_MORE,
                    channel.presentedIds,
                    if (isAllTag(channel.tagId)) "" else channel.tagId,
                )
                requireSuccess(response.statusCode(), "分区「${channel.name}」")
                val added = response.flatAwemes()
                val videos = channel.videos + added
                Result.success(
                    channel.copy(
                        videos = videos,
                        loading = false,
                        error = null,
                        hasMore = response.hasMore(),
                        nextPageIndex = page + 1,
                        presentedIds = videos.map { it.awemeId() }.takeLast(PRESENTED_ID_LIMIT),
                    ),
                )
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (t: Throwable) {
                Result.success(channel.copy(loading = false, error = t.collectionMessage()))
            }
        }

    private fun requireApi(): DouyinApi =
        AppRuntime.api ?: throw IllegalStateException("抖音接口尚未初始化，请重启应用")

    /** 与 FeedDataSource.requireSuccess 同口径（status_code → ApiException）。 */
    private fun requireSuccess(statusCode: Int, what: String) {
        if (statusCode == 0) return
        if (statusCode == STATUS_LOGIN_REQUIRED) throw ApiException.loginRequired()
        if (statusCode == STATUS_RATE_LIMITED) throw ApiException.rateLimited()
        throw ApiException.apiStatus(statusCode, "$what 返回 status_code=$statusCode")
    }

    private fun ChannelConfigItem.toFeaturedChannel(): FeaturedChannel = FeaturedChannel(
        tagId = key(),
        name = label(),
        title = label(),
    )
}

