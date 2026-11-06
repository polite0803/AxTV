/*
 * 首页真实数据源（UI 侧薄封装）。
 *
 * 核心层 FeedDataSource.loadFeed / refresh 负责请求与错误语义（不缓存、不重试、不吞异常），
 * 本类补四件事：
 * 1. 在 IO 线程执行阻塞请求；
 * 2. 保留最近一次成功加载的列表 / 游标 / hasMore 快照，返回首页时直接复用；
 * 3. 把「已看 / 过滤手机视频」这两条本地过滤接在服务端分页之后；
 * 4. 把 refresh_index 与最近已展示 id 落到设置里，让每次冷启动的请求不再是同一份字节。
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

    /** 刷新时回传给推荐接口的「最近已展示」条数（原版 kq5 取 10 个）。 */
    private const val PRESENTED_ID_LIMIT = 10

    /**
     * 清空首页缓存，供登录/登出/换账号后调用。
     *
     * 进程内缓存不属于当前登录态：继续沿用会让新账号看到旧账号那一屏，
     * 主观上就是「登录与不登录推荐没区别」。清空后首页重新进入会走首屏加载。
     */
    fun invalidate() {
        cachedAwemes = emptyList()
        cachedNextCursor = 1L
        cachedHasMore = true
        // 已展示 id 属于「上一个账号看过什么」，换账号后继续回传会把新账号没看过的条目排除掉；
        // refresh_index 只是单调序号，与账号无关，所以保留（重置会退回冷启动那一批）。
        AppRuntime.settings?.setFeedPresentedIds(emptyList())
    }

    /** 一页首页数据（已应用「已看 / 手机视频」过滤）。 */
    data class HomeFeedPage(
        val awemes: List<Aweme>,
        val hasMore: Boolean,
        /** 下一次请求要传的 cursor；两条路径都满足「下次的 refresh_index = cursor + 1」。 */
        val nextCursor: Long = 1L,
    )

    /** 最近一次成功加载的首页列表（点击条目进播放器时作为队列）。 */
    var cachedAwemes by mutableStateOf<List<Aweme>>(emptyList())
        private set

    /** 下次加载更多要用的页序号（FeedDataSource 的 cursor：已加载页数）。 */
    var cachedNextCursor by mutableStateOf(1L)
        private set

    /** 最近一次成功加载后服务端是否还有下一页。 */
    var cachedHasMore by mutableStateOf(true)
        private set

    fun cache(items: List<Aweme>, nextCursor: Long, hasMore: Boolean) {
        cachedAwemes = items.toList()
        cachedNextCursor = nextCursor
        cachedHasMore = hasMore
    }

    /**
     * 拉取一页。cursor 为已加载页数：0 = 首屏，>0 = 加载更多。
     * 失败通过 Result.failure 原样抛出对应的 ApiException，由 UI 展示可重试的错误态。
     *
     * @param recentAwemeIds 已展示的作品 id；加载更多时回传给推荐接口，首屏传空
     */
    suspend fun load(
        cursor: Long,
        recentAwemeIds: List<String> = emptyList(),
    ): Result<HomeFeedPage> = withContext(Dispatchers.IO) {
        try {
            val api = AppRuntime.api
                ?: return@withContext Result.failure(
                    IllegalStateException("接口组件尚未就绪，请重启应用后重试"),
                )
            val page = FeedDataSource(FeedApi(api)).loadFeed(cursor, recentAwemeIds)
            // 加载更多同样用掉一个 refresh_index（= cursor + 1）。原版 kq5 每发一次请求就
            // f12441c++，刷新与翻页共用同一计数器；不同步的话翻几页后再「换一批」会重复请求已经看过的页。
            advanceFeedRefreshIndexTo(cursor + 2)
            Result.success(
                HomeFeedPage(
                    awemes = dropMobileOnly(dropViewed(page.awemes())),
                    hasMore = page.hasMore(),
                    nextCursor = cursor + 1,
                ),
            )
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    /**
     * 首屏/刷新（pull_type=0）：refresh_index 从本地持久化进度继续自增，并回传最近已展示 id。
     *
     * 原版 qg5/kq5 的刷新走 kq5.m7271g → n11.m8021M：用自增后的 refresh_index 与最近展示 id
     * 请求，所以「再点一次推荐」能换一批。我们之前始终发 refresh_index=1 + 空 id，
     * 服务端每次都按冷启动第一页返回同一批（重启也一样）。本方法把这份进度落到设置里，
     * 重启后继续自增，保证请求不再是逐字节相同的冷启动请求。
     */
    suspend fun refresh(): Result<HomeFeedPage> = withContext(Dispatchers.IO) {
        try {
            val api = AppRuntime.api
                ?: return@withContext Result.failure(
                    IllegalStateException("接口组件尚未就绪，请重启应用后重试"),
                )
            val settings = AppRuntime.settings
            val index = settings?.feedRefreshIndex() ?: 1
            val recentIds = cachedAwemes
                .mapNotNull { it.awemeId().takeIf(String::isNotEmpty) }
                .takeLast(PRESENTED_ID_LIMIT)
                .ifEmpty {
                    settings?.feedPresentedIds().orEmpty().takeLast(PRESENTED_ID_LIMIT)
                }
            val page = FeedDataSource(FeedApi(api)).refresh(index, recentIds)
            val awemes = dropMobileOnly(dropViewed(page.awemes()))
            advanceFeedRefreshIndexTo(index + 1L)
            settings?.setFeedPresentedIds(
                awemes.mapNotNull { it.awemeId().takeIf(String::isNotEmpty) }
                    .takeLast(PRESENTED_ID_LIMIT),
            )
            Result.success(
                HomeFeedPage(
                    awemes = awemes,
                    hasMore = page.hasMore(),
                    nextCursor = index.toLong(),
                ),
            )
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    /**
     * 把持久化的 refresh_index 进度推到「至少 next」——只前进，不回退。
     *
     * 首屏、刷新、加载更多、播放器续页共用同一个计数器（原版 kq5.f12441c 每请求一次自增一次）；
     * 谁用掉了哪个序号，就把它推到那个序号之后，后面的请求才不会重复拿到已经看过的页。
     */
    private fun advanceFeedRefreshIndexTo(next: Long) {
        val settings = AppRuntime.settings ?: return
        if (next <= 1L) return
        val index = minOf(next, Int.MAX_VALUE.toLong()).toInt()
        if (index > settings.feedRefreshIndex()) {
            settings.setFeedRefreshIndex(index)
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
