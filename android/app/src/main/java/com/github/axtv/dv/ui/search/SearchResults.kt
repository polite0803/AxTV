/*
 * 搜索结果的展示模型与分栏加载状态（从 SearchState.kt 拆出）。
 * 拆分原因: SearchState.kt 超 800 行；ResultsState 是纯状态容器，与请求编排无耦合。
 */
package com.github.axtv.dv.ui.search

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** 搜索页三个结果分栏（顺序与 UI tab 一致）。 */
internal enum class SearchTab(val label: String) {
    VIDEO("视频"),
    USER("用户"),
    LIVE("直播"),
}

/** 视频结果卡片的展示模型（已从 Aweme 收敛为 UI 需要的字段）。 */
internal data class VideoResult(
    val id: String,
    val title: String,
    val author: String,
    val coverUrl: String,
    val durationMs: Int,
)

/** 用户结果行的展示模型。 */
internal data class UserResult(
    val id: String,
    val name: String,
    val signature: String,
    val avatarUrl: String,
    val followerCount: Long,
)

/** 直播结果行的展示模型。 */
internal data class LiveResult(
    val id: String,
    val name: String,
    val avatarUrl: String,
)

/**
 * 单个分栏的加载状态。
 *
 * 与 UI 的约定：
 * - loading=true → 展示首屏加载态
 * - error!=null → 展示失败 + 重试
 * - items 为空且 attempted → 展示空态
 * - loadingMore/loadMoreError 只影响列表末尾的「加载更多」行
 */
internal class ResultsState<T> {

    var items by mutableStateOf<List<T>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var hasMore by mutableStateOf(false)
        private set
    var loadingMore by mutableStateOf(false)
        private set
    var loadMoreError by mutableStateOf<String?>(null)
        private set

    /**
     * 诊断信息（status_code / Cookie 是否含 sessionid），供搜索失败或结果为空时界面与日志排查。
     * 空态优先展示：能直接区分「未登录/ Cookie 过期（返回 0+空）」与「接口字段错（解析为空）」。
     */
    var diagnostic by mutableStateOf<String?>(null)
        private set

    /** 是否已经发起过一次请求（用于切换 tab 时判断是否需要懒加载）。 */
    internal var attempted by mutableStateOf(false)
        private set

    /** 下一页 offset；首屏时归零。 */
    internal var offset: Int = 0
        private set

    /** 本次搜索会话 id（来自上一页 impr_id），翻页需要带回。 */
    internal var searchId: String = ""
        private set

    internal fun reset() {
        items = emptyList()
        loading = false
        error = null
        hasMore = false
        loadingMore = false
        loadMoreError = null
        attempted = false
        offset = 0
        searchId = ""
        diagnostic = null
    }

    internal fun markFirstLoading() {
        loading = true
        error = null
        loadingMore = false
        loadMoreError = null
    }

    internal fun applyFirst(
        page: List<T>,
        more: Boolean,
        nextOffset: Int,
        nextSearchId: String,
        key: (T) -> String,
        diagnostic: String? = null,
    ) {
        items = page.distinctBy(key)
        hasMore = more
        offset = nextOffset
        if (nextSearchId.isNotBlank()) {
            searchId = nextSearchId
        }
        loading = false
        attempted = true
        error = null
        this.diagnostic = diagnostic
    }

    internal fun applyFirstError(message: String, diagnostic: String? = null) {
        loading = false
        attempted = true
        error = message
        this.diagnostic = diagnostic
    }

    internal fun markMoreLoading() {
        loadingMore = true
        loadMoreError = null
    }

    internal fun applyMore(
        page: List<T>,
        more: Boolean,
        nextOffset: Int,
        nextSearchId: String,
        key: (T) -> String,
    ) {
        items = (items + page).distinctBy(key)
        hasMore = more
        offset = nextOffset
        if (nextSearchId.isNotBlank()) {
            searchId = nextSearchId
        }
        loadingMore = false
        loadMoreError = null
    }

    internal fun applyMoreError(message: String) {
        loadingMore = false
        loadMoreError = message
    }
}