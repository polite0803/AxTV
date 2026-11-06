/*
 * 搜索结果的播放队列快照。
 *
 * 搜索结果里的视频仍然进详情页；详情页的「播放」由 ui/nav/PlayerFromSearchScreen 消费本快照，
 * 按搜索结果顺序连播，并在播放器内继续翻页——不再要求用户退回搜索结果列表。
 *
 * 只缓存视频 tab 的 aweme（用户/直播条目不是视频队列的一部分），并在每次首屏/翻页后更新。
 * 不持有 Compose 状态：播放器进入时取一次快照，翻页直接追加到内部列表，避免播放中途重建 provider。
 */
package com.github.axtv.dv.ui.search

import axtv.api.SearchApi
import axtv.model.Aweme
import com.github.axtv.dv.adapter.AppRuntime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal object SearchPlaybackRepository {

    /** 与 SearchState 的视频翻页参数保持一致（原版 kq5:537）。 */
    private const val NEXT_PAGE_SIZE = 10
    private const val SORT_DEFAULT = "0"
    private const val PUBLISH_DEFAULT = "0"
    private const val DURATION_DEFAULT = ""

    @Volatile
    var cachedAwemes: List<Aweme> = emptyList()
        private set

    @Volatile
    private var query: String = ""

    @Volatile
    private var searchId: String = ""

    @Volatile
    private var nextOffset: Int = 0

    @Volatile
    private var hasMore: Boolean = false

    /** 播放器内翻页的串行闸门；同一时间只有一个播放器会消费本快照。 */
    @Volatile
    private var loadingMore: Boolean = false

    /** 新一次搜索：清空上一轮队列，避免点开新结果时播到旧关键词的视频。 */
    @Synchronized
    fun reset() {
        cachedAwemes = emptyList()
        query = ""
        searchId = ""
        nextOffset = 0
        hasMore = false
    }

    /** 视频 tab 首屏落地。 */
    @Synchronized
    fun cacheFirst(
        awemes: List<Aweme>,
        keyword: String,
        searchId: String,
        nextOffset: Int,
        hasMore: Boolean,
    ) {
        cachedAwemes = awemes.toList()
        query = keyword
        this.searchId = searchId
        this.nextOffset = nextOffset
        this.hasMore = hasMore
    }

    /** 视频 tab 翻页落地：按 id 去重后追加，与界面列表保持同一顺序。 */
    @Synchronized
    fun cacheMore(
        awemes: List<Aweme>,
        keyword: String,
        searchId: String,
        nextOffset: Int,
        hasMore: Boolean,
    ) {
        if (keyword != query) {
            return
        }
        val known = cachedAwemes.mapNotNullTo(HashSet()) { it.awemeId().takeIf(String::isNotEmpty) }
        cachedAwemes = cachedAwemes + awemes.filter { aweme ->
            val id = aweme.awemeId()
            id.isNotEmpty() && known.add(id)
        }
        if (searchId.isNotBlank()) {
            this.searchId = searchId
        }
        this.nextOffset = nextOffset
        this.hasMore = hasMore
    }

    /**
     * 播放器队尾续页：按缓存的 query / search_id / offset 继续请求视频搜索。
     * 追加到本快照并返回新增条目；失败或没有更多返回空表（播放器保持当前队列）。
     */
    suspend fun loadMore(): List<Aweme> {
        if (loadingMore || !hasMore || query.isBlank()) {
            return emptyList()
        }
        loadingMore = true
        try {
            val api = AppRuntime.api ?: return emptyList()
            val offset = nextOffset
            val keyword = query
            val sid = searchId
            val response = withContext(Dispatchers.IO) {
                SearchApi(api).videoSearch(
                    keyword,
                    offset,
                    NEXT_PAGE_SIZE,
                    SORT_DEFAULT,
                    PUBLISH_DEFAULT,
                    DURATION_DEFAULT,
                    sid,
                )
            }
            if (response.statusCode() != 0) {
                hasMore = false
                return emptyList()
            }
            val awemes = (response.data() ?: emptyList())
                .mapNotNull { it.awemeInfo().takeIf { aweme -> aweme.awemeId().isNotBlank() } }
            val fresh: List<Aweme> = synchronized(this) {
                if (keyword != query) {
                    emptyList()
                } else {
                    val known = cachedAwemes.mapNotNullTo(HashSet()) {
                        it.awemeId().takeIf(String::isNotEmpty)
                    }
                    val added = awemes.filter { aweme ->
                        val id = aweme.awemeId()
                        id.isNotEmpty() && known.add(id)
                    }
                    cachedAwemes = cachedAwemes + added
                    response.logPb()?.imprId()?.takeIf { it.isNotBlank() }?.let { searchId = it }
                    nextOffset = offset + NEXT_PAGE_SIZE
                    hasMore = response.hasMorePages() && added.isNotEmpty()
                    added
                }
            }
            return fresh
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (t: Throwable) {
            hasMore = false
            return emptyList()
        } finally {
            loadingMore = false
        }
    }
}
