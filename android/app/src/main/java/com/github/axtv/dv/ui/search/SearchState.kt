/*
 * 搜索页状态与数据层（只读消费 refactor/api 的 typed 门面，不改核心层）。
 *
 * 数据来源：
 * - axtv.api.SearchApi：hotSearch / suggestions / suggestWords / videoSearch / userSearch / liveSearch
 * - 网络实例来自 AppRuntime.api（已装配的 DouyinApi），不新建 OkHttp 客户端
 *
 * 线程模型：SearchApi 方法是同步阻塞调用，统一在 Dispatchers.IO 执行；
 * 状态写回发生在 rememberCoroutineScope() 的 Main 线程。
 *
 * 分页参数来源（对照反编译调用点）：
 * - 视频：首屏 offset=0 count=20，之后 offset 递增、count=10（n11.m8067w0 + kq5:537）
 * - 用户：每页 count=10（kq5:496），offset 递增
 * - 直播：首屏 count=15，之后 count=10（n11.m8064u0 内 b2 分支）
 * - sort_type/publish_time/filter_duration 默认 "0"/"0"/""（kq5:537 实参）
 * - search_id 来自上一页响应的 log_pb.impr_id（kq5:589 等）
 */
package com.github.axtv.dv.ui.search

import axtv.api.ApiException
import axtv.api.SearchApi
import axtv.model.Author
import axtv.model.HotWord
import axtv.model.SearchLiveLives
import axtv.model.SearchVideoWorkData
import axtv.ui.FeedPageSpec
import axtv.ui.SearchUiSpec
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.github.axtv.dv.adapter.AppRuntime
import com.github.axtv.dv.ui.library.isLoggedIn
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    }

    internal fun applyFirstError(message: String) {
        loading = false
        attempted = true
        error = message
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

/**
 * 搜索页状态机。
 *
 * 只暴露「能驱动 UI 的最小状态」；请求生命周期全部收敛在这里。
 */
internal class SearchState(
    private val scope: CoroutineScope,
    private val history: SearchHistoryStore,
) {

    private companion object {
        const val VIDEO_FIRST = 20
        const val VIDEO_NEXT = 10
        const val USER_COUNT = 10
        const val LIVE_FIRST = 15
        const val LIVE_NEXT = 10
        const val SUGGEST_DEBOUNCE_MS = 350L
        const val SUGGEST_LIMIT = 12
        const val SORT_DEFAULT = "0"
        const val PUBLISH_DEFAULT = "0"
        const val DURATION_DEFAULT = ""
    }

    /** 输入框文本（也是未提交的搜索词）。 */
    var query by mutableStateOf("")
        private set

    /** 最近一次真正提交的搜索词（结果列表对应的关键词）。 */
    var committedKeyword by mutableStateOf("")
        private set

    /** true = 展示结果分栏；false = 展示热搜 / 联想词。 */
    var showResults by mutableStateOf(false)
        private set

    var tab by mutableStateOf(SearchTab.VIDEO)
        private set

    // ---- 搜索前：本地搜索历史（原版 search_history / history） ----
    var historyWords by mutableStateOf<List<String>>(emptyList())
        private set

    // ---- 搜索前：热搜（hotSearch） ----
    var hotWords by mutableStateOf<List<HotWord>>(emptyList())
        private set
    var hotLoading by mutableStateOf(false)
        private set
    var hotError by mutableStateOf<String?>(null)
        private set

    // ---- 输入联想（suggestions，必要时回落 suggestWords） ----
    var suggestions by mutableStateOf<List<String>>(emptyList())
        private set
    var suggestionsLoading by mutableStateOf(false)
        private set
    var suggestionsError by mutableStateOf<String?>(null)
        private set

    // ---- 三栏结果 ----
    val video = ResultsState<VideoResult>()
    val users = ResultsState<UserResult>()
    val lives = ResultsState<LiveResult>()

    /**
     * 焦点复位信号：每次「首屏数据落地 / 切换已加载的 tab」自增。
     * 页面只负责观察该信号，按 [focusTab] 把焦点交还给列表（或状态面板按钮）。
     */
    var focusSignal by mutableStateOf(0L)
        private set
    var focusTab by mutableStateOf(SearchTab.VIDEO)
        private set

    private var hotJob: Job? = null
    private var suggestJob: Job? = null
    private var videoJob: Job? = null
    private var userJob: Job? = null
    private var liveJob: Job? = null

    // ------------------------------------------------------------------ 输入 / 提交

    fun onQueryChange(text: String) {
        if (query == text) {
            return
        }
        query = text
        // 回到编辑态：结果页让位给联想词，但结果缓存保留，重新提交时再重置。
        showResults = false
        suggestJob?.cancel()
        suggestions = emptyList()
        suggestionsError = null
        suggestionsLoading = false
        if (text.isBlank()) {
            return
        }
        suggestJob = scope.launch {
            delay(SUGGEST_DEBOUNCE_MS)
            fetchSuggestions(text.trim())
        }
    }

    /**
     * 路由参数预填：`SearchResultsPage(query)` 用 autoSearch=true 直接进入结果态；
     * `SearchPage(query)` 用 autoSearch=false 只回填并拉一次联想。
     */
    fun initialize(initialQuery: String, autoSearch: Boolean) {
        val text = initialQuery.trim()
        if (text.isEmpty() || query.isNotEmpty() || committedKeyword.isNotEmpty()) {
            return
        }
        query = text
        if (autoSearch) {
            submit()
        } else {
            suggestJob?.cancel()
            suggestionsLoading = true
            suggestionsError = null
            suggestJob = scope.launch { fetchSuggestions(text) }
        }
    }

    /** 双路由模式下点击热词/联想词：只回填输入框，结果由导航跳转后的结果页加载。 */
    fun prefill(text: String) {
        if (query == text) {
            return
        }
        query = text
        showResults = false
        suggestJob?.cancel()
        suggestions = emptyList()
        suggestionsError = null
        suggestionsLoading = false
    }

    /** 点击热搜 / 联想词：回填并立即搜索。 */
    fun searchWord(word: String) {
        query = word
        submit()
    }

    fun submit() {
        val keyword = query.trim()
        if (keyword.isEmpty()) {
            return
        }
        query = keyword
        committedKeyword = keyword
        // 原版仅在登录态下记录本地历史（ye4 写入前判断 sessionid），未登录仍展示已有历史。
        if (isLoggedIn()) {
            historyWords = history.record(keyword)
        }
        showResults = true
        suggestJob?.cancel()
        suggestions = emptyList()
        suggestionsError = null
        suggestionsLoading = false

        videoJob?.cancel()
        userJob?.cancel()
        liveJob?.cancel()
        video.reset()
        users.reset()
        lives.reset()

        loadFirst(tab)
    }

    /** BACK 的上一层：结果页 → 编辑态（关键词与结果缓存保留）。 */
    fun closeResults() {
        showResults = false
    }

    // ------------------------------------------------------------------ 分栏切换 / 重试 / 翻页

    fun selectTab(next: SearchTab) {
        if (tab == next) {
            return
        }
        tab = next
        if (!showResults) {
            return
        }
        val attempted = when (next) {
            SearchTab.VIDEO -> video.attempted
            SearchTab.USER -> users.attempted
            SearchTab.LIVE -> lives.attempted
        }
        if (attempted) {
            focusTab = next
            focusSignal++
        } else {
            loadFirst(next)
        }
    }

    fun retryTab(target: SearchTab = tab) {
        loadFirst(target)
    }

    fun loadMore(target: SearchTab = tab) {
        when (target) {
            SearchTab.VIDEO -> loadVideoMore()
            SearchTab.USER -> loadUserMore()
            SearchTab.LIVE -> loadLiveMore()
        }
    }

    fun retrySuggestions() {
        val keyword = query.trim()
        if (keyword.isEmpty()) {
            return
        }
        suggestJob?.cancel()
        suggestJob = scope.launch { fetchSuggestions(keyword) }
    }

    // ------------------------------------------------------------------ 本地搜索历史

    fun loadHistory() {
        historyWords = history.load()
    }

    fun clearHistory() {
        historyWords = history.clear()
    }

    // ------------------------------------------------------------------ 热搜

    fun loadHot() {
        hotJob?.cancel()
        hotLoading = true
        hotError = null
        hotJob = scope.launch {
            val result = call { api().hotSearch() }
            result.fold(
                onSuccess = { response ->
                    if (response.statusCode() != 0) {
                        hotError = statusError("", response.statusCode())
                    } else {
                        hotWords = (response.data()?.wordList() ?: emptyList())
                            .filter { it.word().isNotBlank() }
                            .distinctBy { it.word() }
                    }
                    hotLoading = false
                },
                onFailure = {
                    hotError = describeSearchError(it)
                    hotLoading = false
                },
            )
        }
    }

    // ------------------------------------------------------------------ 联想

    private suspend fun fetchSuggestions(keyword: String) {
        suggestionsLoading = true
        suggestionsError = null

        val primary = call { api().suggestions(keyword) }
        var failure: Throwable? = primary.exceptionOrNull()
        var words: List<String> = primary.getOrNull()
            ?.takeIf { it.statusCode() == 0 }
            ?.sugList()
            ?.map { it.content() }
            ?.filter { it.isNotBlank() }
            ?: emptyList()

        // suggestions 为空或失败时用 suggest_words 兜底（两者都是「输入联想」的数据源）。
        if (words.isEmpty()) {
            val fallback = call { api().suggestWords(keyword, SUGGEST_LIMIT) }
            if (failure == null) {
                failure = fallback.exceptionOrNull()
            }
            words = fallback.getOrNull()
                ?.takeIf { it.statusCode() == 0 }
                ?.data()
                ?.flatMap { group -> group.words() ?: emptyList() }
                ?.map { it.word() }
                ?.filter { it.isNotBlank() }
                ?: emptyList()
        }

        // 关键词已变化（或已提交）：丢弃这次联想结果。
        if (query.trim() != keyword) {
            return
        }
        suggestions = words.distinct().take(SUGGEST_LIMIT)
        suggestionsError = if (words.isEmpty()) {
            failure?.let { describeSearchError(it) } ?: "联想词暂不可用，可点击重试"
        } else {
            null
        }
        suggestionsLoading = false
    }

    // ------------------------------------------------------------------ 视频

    private fun loadFirst(target: SearchTab) {
        when (target) {
            SearchTab.VIDEO -> loadVideoFirst()
            SearchTab.USER -> loadUserFirst()
            SearchTab.LIVE -> loadLiveFirst()
        }
    }

    private fun loadVideoFirst() {
        videoJob?.cancel()
        video.markFirstLoading()
        videoJob = scope.launch {
            val keyword = committedKeyword
            val result = call {
                api().videoSearch(keyword, 0, VIDEO_FIRST, SORT_DEFAULT, PUBLISH_DEFAULT, DURATION_DEFAULT, video.searchId)
            }
            if (committedKeyword != keyword) {
                return@launch
            }
            result.fold(
                onSuccess = { response ->
                    if (response.statusCode() != 0) {
                        video.applyFirstError(statusError("", response.statusCode()))
                    } else {
                        val mapped = (response.data() ?: emptyList()).mapNotNull { it.toVideoResult() }
                        video.applyFirst(
                            page = mapped,
                            more = response.hasMorePages(),
                            nextOffset = VIDEO_FIRST,
                            nextSearchId = response.logPb()?.imprId().orEmpty(),
                            key = { it.id },
                        )
                    }
                    afterPage(SearchTab.VIDEO)
                },
                onFailure = {
                    video.applyFirstError(describeSearchError(it))
                    afterPage(SearchTab.VIDEO)
                },
            )
        }
    }

    private fun loadVideoMore() {
        if (video.loading || video.loadingMore || !video.attempted || !video.hasMore) {
            return
        }
        video.markMoreLoading()
        videoJob = scope.launch {
            val keyword = committedKeyword
            val offset = video.offset
            val result = call {
                api().videoSearch(
                    keyword, offset, VIDEO_NEXT,
                    SORT_DEFAULT, PUBLISH_DEFAULT, DURATION_DEFAULT, video.searchId,
                )
            }
            if (committedKeyword != keyword) {
                return@launch
            }
            result.fold(
                onSuccess = { response ->
                    if (response.statusCode() != 0) {
                        video.applyMoreError(statusError("", response.statusCode()))
                    } else {
                        val mapped = (response.data() ?: emptyList()).mapNotNull { it.toVideoResult() }
                        video.applyMore(
                            page = mapped,
                            more = response.hasMorePages(),
                            nextOffset = offset + VIDEO_NEXT,
                            nextSearchId = response.logPb()?.imprId().orEmpty(),
                            key = { it.id },
                        )
                    }
                },
                onFailure = { video.applyMoreError(describeSearchError(it)) },
            )
        }
    }

    // ------------------------------------------------------------------ 用户

    private fun loadUserFirst() {
        userJob?.cancel()
        users.markFirstLoading()
        userJob = scope.launch {
            val keyword = committedKeyword
            val result = call { api().userSearch(keyword, 0, USER_COUNT, users.searchId, false, "") }
            if (committedKeyword != keyword) {
                return@launch
            }
            result.fold(
                onSuccess = { response ->
                    if (response.statusCode() != 0) {
                        users.applyFirstError(statusError(SearchUiSpec.CHANNEL_USER, response.statusCode()))
                    } else {
                        val mapped = (response.userList() ?: emptyList())
                            .mapNotNull { it.userInfo().toUserResult() }
                        users.applyFirst(
                            page = mapped,
                            more = response.hasMorePages(),
                            nextOffset = USER_COUNT,
                            nextSearchId = response.logPb()?.imprId().orEmpty(),
                            key = { it.id },
                        )
                    }
                    afterPage(SearchTab.USER)
                },
                onFailure = {
                    users.applyFirstError(describeSearchError(it, SearchUiSpec.CHANNEL_USER))
                    afterPage(SearchTab.USER)
                },
            )
        }
    }

    private fun loadUserMore() {
        if (users.loading || users.loadingMore || !users.attempted || !users.hasMore) {
            return
        }
        users.markMoreLoading()
        userJob = scope.launch {
            val keyword = committedKeyword
            val offset = users.offset
            val result = call { api().userSearch(keyword, offset, USER_COUNT, users.searchId, false, "") }
            if (committedKeyword != keyword) {
                return@launch
            }
            result.fold(
                onSuccess = { response ->
                    if (response.statusCode() != 0) {
                        users.applyMoreError(statusError(SearchUiSpec.CHANNEL_USER, response.statusCode()))
                    } else {
                        val mapped = (response.userList() ?: emptyList())
                            .mapNotNull { it.userInfo().toUserResult() }
                        users.applyMore(
                            page = mapped,
                            more = response.hasMorePages(),
                            nextOffset = offset + USER_COUNT,
                            nextSearchId = response.logPb()?.imprId().orEmpty(),
                            key = { it.id },
                        )
                    }
                },
                onFailure = { users.applyMoreError(describeSearchError(it, SearchUiSpec.CHANNEL_USER)) },
            )
        }
    }

    // ------------------------------------------------------------------ 直播

    private fun loadLiveFirst() {
        liveJob?.cancel()
        lives.markFirstLoading()
        liveJob = scope.launch {
            val keyword = committedKeyword
            val result = call { api().liveSearch(keyword, 0, LIVE_FIRST, lives.searchId) }
            if (committedKeyword != keyword) {
                return@launch
            }
            result.fold(
                onSuccess = { response ->
                    if (response.statusCode() != 0) {
                        lives.applyFirstError(statusError(SearchUiSpec.CHANNEL_LIVE, response.statusCode()))
                    } else {
                        val mapped = (response.data() ?: emptyList())
                            .mapNotNull { it.lives()?.toLiveResult() }
                        lives.applyFirst(
                            page = mapped,
                            more = response.hasMorePages(),
                            nextOffset = LIVE_FIRST,
                            nextSearchId = response.logPb()?.imprId().orEmpty(),
                            key = { it.id },
                        )
                    }
                    afterPage(SearchTab.LIVE)
                },
                onFailure = {
                    lives.applyFirstError(describeSearchError(it, SearchUiSpec.CHANNEL_LIVE))
                    afterPage(SearchTab.LIVE)
                },
            )
        }
    }

    private fun loadLiveMore() {
        if (lives.loading || lives.loadingMore || !lives.attempted || !lives.hasMore) {
            return
        }
        lives.markMoreLoading()
        liveJob = scope.launch {
            val keyword = committedKeyword
            val offset = lives.offset
            val result = call { api().liveSearch(keyword, offset, LIVE_NEXT, lives.searchId) }
            if (committedKeyword != keyword) {
                return@launch
            }
            result.fold(
                onSuccess = { response ->
                    if (response.statusCode() != 0) {
                        lives.applyMoreError(statusError(SearchUiSpec.CHANNEL_LIVE, response.statusCode()))
                    } else {
                        val mapped = (response.data() ?: emptyList())
                            .mapNotNull { it.lives()?.toLiveResult() }
                        lives.applyMore(
                            page = mapped,
                            more = response.hasMorePages(),
                            nextOffset = offset + LIVE_NEXT,
                            nextSearchId = response.logPb()?.imprId().orEmpty(),
                            key = { it.id },
                        )
                    }
                },
                onFailure = { lives.applyMoreError(describeSearchError(it, SearchUiSpec.CHANNEL_LIVE)) },
            )
        }
    }

    // ------------------------------------------------------------------ 内部工具

    private fun afterPage(target: SearchTab) {
        if (showResults && tab == target) {
            focusTab = target
            focusSignal++
        }
    }

    private fun api(): SearchApi =
        SearchApi(AppRuntime.api ?: throw IllegalStateException("API 尚未装配（AppRuntime.api == null）"))

    private suspend fun <T> call(block: () -> T): Result<T> = withContext(Dispatchers.IO) {
        try {
            Result.success(block())
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    private fun statusError(prefix: String, code: Int): String =
        SearchUiSpec.errorLabel(prefix, code.toString(), "")
}

// ------------------------------------------------------------------ 模型映射

private fun SearchVideoWorkData.toVideoResult(): VideoResult? {
    val aweme = awemeInfo()
    val id = aweme.awemeId()
    if (id.isEmpty()) {
        return null
    }
    val video = aweme.video()
    val cover = video.cover().firstUrl()
        .ifBlank { video.dynamicCover().firstUrl() }
        .ifBlank { video.originCover().firstUrl() }
    return VideoResult(
        id = id,
        title = aweme.desc().ifBlank { FeedPageSpec.TEXT_UNTITLED_VIDEO },
        author = aweme.author().nickname().takeIf { it.isNotBlank() }?.let { "@$it" } ?: "未知作者",
        coverUrl = cover,
        durationMs = video.duration(),
    )
}

private fun Author.toUserResult(): UserResult? {
    val id = uid().ifBlank { secUid() }
    if (id.isEmpty()) {
        return null
    }
    return UserResult(
        id = id,
        name = nickname().ifBlank { "未知用户" },
        signature = signature(),
        avatarUrl = bestAvatar().firstUrl(),
        followerCount = followerCount(),
    )
}

private fun SearchLiveLives.toLiveResult(): LiveResult? {
    if (awemeId().isEmpty()) {
        return null
    }
    return LiveResult(
        id = awemeId(),
        name = author().nickname().ifBlank { "未知主播" },
        avatarUrl = author().bestAvatar().firstUrl(),
    )
}

// ------------------------------------------------------------------ 文案 / 格式化

/**
 * 把 ApiException.Kind 翻译成用户可读文案；业务码仍交给 SearchUiSpec（逐字规格）。
 * 非 ApiException 保留原始 message，避免吞掉装配错误等本地异常。
 */
internal fun describeSearchError(t: Throwable, channelPrefix: String = ""): String = when (t) {
    is ApiException -> when (t.kind()) {
        ApiException.Kind.NETWORK -> "网络连接失败，请检查网络后重试"
        ApiException.Kind.HTTP_STATUS -> "服务端返回异常（HTTP ${t.httpStatus()}），请稍后重试"
        ApiException.Kind.EMPTY_BODY ->
            "服务器返回空响应，可能是签名失效或 Cookie 过期，请在设置中更新 Cookie"
        ApiException.Kind.HEADER_ERROR -> "请求被服务端拦截，请稍后重试"
        ApiException.Kind.API_STATUS ->
            SearchUiSpec.errorLabel(channelPrefix, t.apiStatus().toString(), t.message.orEmpty())
        ApiException.Kind.LOGIN_REQUIRED -> "搜索需要登录态，请更新 Cookie"
        ApiException.Kind.RATE_LIMITED -> "请求过于频繁，请稍后重试"
        ApiException.Kind.MISSING_CREDENTIAL -> "缺少互动安全凭据，请在设置中补充"
        ApiException.Kind.DECODE -> "搜索结果解析失败，请稍后重试"
    }

    else -> {
        val message = t.message?.takeIf { it.isNotBlank() } ?: t.javaClass.simpleName
        when (channelPrefix) {
            SearchUiSpec.CHANNEL_USER -> SearchUiSpec.channelErrorLabel(true, message)
            SearchUiSpec.CHANNEL_LIVE -> SearchUiSpec.channelErrorLabel(false, message)
            else -> "搜索失败: $message"
        }
    }
}

/** 与首页一致的计数缩写：1.2万 / 1.0亿。 */
internal fun formatCount(value: Long): String = when {
    value >= 100_000_000L -> String.format(Locale.US, "%.1f亿", value / 100_000_000.0)
    value >= 10_000L -> String.format(Locale.US, "%.1f万", value / 10_000.0)
    else -> value.toString()
}

/** 毫秒 → mm:ss / h:mm:ss。 */
internal fun formatDuration(durationMs: Int): String {
    val totalSeconds = (durationMs.toLong() / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}
