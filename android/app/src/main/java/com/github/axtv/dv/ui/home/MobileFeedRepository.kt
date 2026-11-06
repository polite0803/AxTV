/*
 * 移动推荐标签页数据源（UI 侧薄封装），对应原版移动页状态机 yd1 + 加载器 kq5.m7280r。
 *
 * 翻页形态与首页/关注都不同（这是原版的行为，不是遗留）：
 * - 没有服务端游标，refresh_index 每次请求后自增（kq5.f12444f）；
 * - refresh_index==1 即冷启动（cold_start=1、pull_type=2、不带已展示 id）；
 * - 已展示 id 按到达顺序维护、封顶 50、回传取末尾 10（kq5.m7280r + n11.m8030V）；
 * - 刷新 = 把序号与 id 集合清零重放冷启动（yd1.m12084r:398-399），
 *   这是服务端能「换一批」的唯一途径。
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

object MobileFeedRepository {

    /** 已展示 id 封顶（kq5.m7280r:1520-1524：超过 50 裁到最近 50）。 */
    private const val PRESENTED_CAP = 50

    /** 回传给服务端的最近已展示条数（kq5.m7280r:1480 m8501m1(10,…) 取末尾 10）。 */
    private const val PRESENTED_LIMIT = 10

    var cachedAwemes by mutableStateOf<List<Aweme>>(emptyList())
        private set

    /** 下次请求要用的 refresh_index（= 已请求次数 + 1）。 */
    var cachedNextIndex by mutableStateOf(1)
        private set

    var cachedHasMore by mutableStateOf(true)
        private set

    /** 已展示 id，按到达顺序（等价原版 ArrayList 的 remove-then-add 行为）。 */
    private val presentedIds = ArrayDeque<String>()

    fun invalidate() {
        cachedAwemes = emptyList()
        cachedNextIndex = 1
        cachedHasMore = true
        presentedIds.clear()
    }

    /**
     * 从缓存恢复界面状态后回填已展示 id（进程未重启、仅组合状态重建时用）。
     * 与 cache() 区分：cache() 在 load() 内部已被逐页调用，这里给「刷新重放」后的整体重置用。
     */
    fun cache(items: List<Aweme>, nextIndex: Int, hasMore: Boolean) {
        cachedAwemes = items.toList()
        cachedNextIndex = nextIndex
        cachedHasMore = hasMore
    }

    /**
     * 拉一页。refreshIndex==1 走冷启动语义（不回传已展示 id）；失败原样抛给 UI。
     */
    suspend fun load(refreshIndex: Int): Result<FeedDataSource.FeedPage> = withContext(Dispatchers.IO) {
        try {
            val api = AppRuntime.api
                ?: return@withContext Result.failure(
                    IllegalStateException("接口组件尚未就绪，请重启应用后重试"),
                )
            val coldStart = refreshIndex <= 1
            if (coldStart) presentedIds.clear()
            val recent = if (coldStart) emptyList() else presentedIds.takeLast(PRESENTED_LIMIT)
            val page = FeedDataSource(FeedApi(api)).loadMobile(refreshIndex, recent)
            page.awemes().forEach { aweme ->
                val id = aweme.awemeId()
                if (id.isNotEmpty()) {
                    presentedIds.remove(id)
                    presentedIds.addLast(id)
                }
            }
            while (presentedIds.size > PRESENTED_CAP) presentedIds.removeFirst()
            Result.success(page)
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    /** 按当前缓存列表重建已展示 id（登录后 invalidate 又重进页面时保持与列表一致）。 */
    fun restorePresentedIds(items: List<Aweme>) {
        presentedIds.clear()
        items.mapNotNull { it.awemeId().takeIf(String::isNotEmpty) }.forEach { presentedIds.addLast(it) }
    }
}
