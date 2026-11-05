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
import com.github.axtv.dv.adapter.ViewedVideoTracker
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
            Result.success(dropMobileOnly(dropViewed(FeedDataSource(FeedApi(api)).loadFeed(cursor))))
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    /**
     * 『历史记录容量』的另一处消费点：按本地「已看」清单去重首页条目。
     *
     * 规则照抄原版 src/app/p000/kq5.java:1005-1020：过滤后整页为空就退回未过滤的原页，
     * 避免出现「看过的都记下来了 → 首页一片空白」。
     * 容量改的是这份本地清单的保留长度（50 条 vs 1000 条 → 一条已看记录多久后重新出现在首页），
     * 服务端历史（VideoApi.historyRead）不受影响。
     */
    private fun dropViewed(page: List<Aweme>): List<Aweme> {
        val viewed = ViewedVideoTracker.viewedIds()
        if (viewed.isEmpty()) return page
        val kept = page.filterNot { it.awemeId() in viewed }
        return kept.ifEmpty { page }
    }

    /**
     * 『过滤手机视频』的消费点：丢掉竖屏条目（高 > 宽）。
     *
     * 判据取接口自带的 video.width / video.height（refactor/model/axtv/model/Video.java 的
     * `width` / `height` 两个字段），尺寸缺失（0）的条目一律保留——没有尺寸就假装不知道画幅。
     * 与『已看』过滤同一兜底规则：整页被滤空时退回原页，避免首页一片空白。
     */
    private fun dropMobileOnly(page: List<Aweme>): List<Aweme> {
        if (AppRuntime.settings?.filterMobileVideo() != true) return page
        val kept = page.filterNot { aweme ->
            val video = aweme.video()
            video.width() > 0 && video.height() > video.width()
        }
        return kept.ifEmpty { page }
    }
}
