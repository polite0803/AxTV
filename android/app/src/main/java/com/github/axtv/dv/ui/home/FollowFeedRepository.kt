/*
 * 关注标签页数据源（UI 侧薄封装），对应原版关注页状态机 ij1/hj1。
 *
 * 与首页仓储的差别都来自原版本身：
 * - 翻页按服务端游标（cursor>0?cursor:maxCursor，kq5.m7272j），不是页数；
 * - 已展示 id 集合跨页去重（hj1.java:112-121 的 LinkedHashSet f9140m），
 *   整页都被去过重后列表仍为空时，展示原版文案「暂无关注视频，请确认已关注其他用户」；
 * - 不做「已看 / 手机视频」本地过滤：原版关注页没有这两条。
 */
package com.github.axtv.dv.ui.home

import axtv.app.FeedDataSource
import axtv.api.FeedApi
import axtv.model.Aweme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.github.axtv.dv.adapter.AppRuntime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object FollowFeedRepository {

    /** 原版空列表提示（hj1.java:136-138 逐字）。 */
    const val EMPTY_MESSAGE = "暂无关注视频，请确认已关注其他用户"

    /** 一页关注数据。 */
    data class FollowPage(
        val awemes: List<Aweme>,
        /** 下次加载更多要用的游标。 */
        val nextCursor: Long,
        /** 这一页服务端有数据但全部被去重掉（列表仍未变化）。 */
        val droppedAll: Boolean,
    )

    var cachedAwemes by mutableStateOf<List<Aweme>>(emptyList())
        private set

    var cachedCursor by mutableStateOf(0L)
        private set

    /** 已展示 id 集合：与列表缓存同生命周期，清空缓存时必须一起清，否则新页会被旧集合吃光。 */
    private val seenIds = mutableSetOf<String>()

    /** 登录/登出/换账号后调用：进程内缓存不属于当前账号。 */
    fun invalidate() {
        cachedAwemes = emptyList()
        cachedCursor = 0L
        seenIds.clear()
    }

    /**
     * 首屏 / 刷新：先清空去重集合再按 cursor=0 拉第一页。
     *
     * 不清空的话，旧集合会把新回来的作品全判成「已展示」，表现为点了刷新、列表纹丝不动。
     */
    suspend fun refresh(): Result<FollowPage> {
        seenIds.clear()
        return load(0L)
    }

    fun cache(items: List<Aweme>, cursor: Long) {
        cachedAwemes = items.toList()
        cachedCursor = cursor
        seenIds.clear()
        seenIds.addAll(items.mapNotNull { it.awemeId().takeIf(String::isNotEmpty) })
    }

    /**
     * 拉一页关注流。cursor=0 即首屏/刷新后的第一页。
     * 失败通过 Result.failure 原样抛出（含原版 status=8/2154 文案），由 UI 展示可重试错误态。
     */
    suspend fun load(cursor: Long): Result<FollowPage> = withContext(Dispatchers.IO) {
        try {
            val api = AppRuntime.api
                ?: return@withContext Result.failure(
                    IllegalStateException("接口组件尚未就绪，请重启应用后重试"),
                )
            val page = FeedDataSource(FeedApi(api)).loadFollow(cursor)
            val fresh = page.awemes().filter { aweme ->
                val id = aweme.awemeId()
                id.isEmpty() || seenIds.add(id)
            }
            Result.success(
                FollowPage(
                    awemes = fresh,
                    nextCursor = page.nextCursor(),
                    droppedAll = page.awemes().isNotEmpty() && fresh.isEmpty(),
                ),
            )
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }
}
