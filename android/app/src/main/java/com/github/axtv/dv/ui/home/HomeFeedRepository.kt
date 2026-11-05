/*
 * 首页真实数据源（UI 侧薄封装）。
 *
 * 核心层 FeedDataSource.loadFeed(cursor) 负责请求与错误语义（不缓存、不重试、不吞异常），
 * 本类只补两件事：
 * 1. 在 IO 线程执行阻塞请求；
 * 2. 保留最近一次成功加载的列表快照，供「播放器」路由构造真实播放队列。
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

object HomeFeedRepository {

    /** 最近一次成功加载的首页列表（点击条目进播放器时作为队列）。 */
    var cachedAwemes by mutableStateOf<List<Aweme>>(emptyList())
        private set

    fun cache(items: List<Aweme>) {
        cachedAwemes = items.toList()
    }

    /**
     * 拉取一页。cursor 为页序号：0 = 首屏，>0 = 加载更多。
     * 失败通过 Result.failure 原样抛出对应的 ApiException，由 UI 展示可重试的错误态。
     */
    suspend fun load(cursor: Long): Result<List<Aweme>> = withContext(Dispatchers.IO) {
        try {
            val api = AppRuntime.api
                ?: return@withContext Result.failure(
                    IllegalStateException("API 尚未装配（AppRuntime.api == null）"),
                )
            Result.success(FeedDataSource(FeedApi(api)).loadFeed(cursor))
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }
}
