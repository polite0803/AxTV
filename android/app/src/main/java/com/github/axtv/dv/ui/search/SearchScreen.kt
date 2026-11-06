/*
 * 搜索页（线 B）。
 *
 * 对外契约（导航线接线用，签名固定）：
 *   @Composable fun SearchScreen(onBack: () -> Unit, onOpenDetail: (String) -> Unit)
 *
 * 职责边界：
 * - 只负责搜索页 UI 与搜索编排，不实现详情页、不接播放器；
 * - 数据只走 axtv.api.SearchApi（AppRuntime.api），不新建网络客户端；
 * - 不改 MainActivity / AxTvApp / ui/nav / refactor / 构建脚本。
 *
 * 焦点约定：
 * - 输入框获焦后由系统输入法输入（不依赖 Leanback 键盘，也不引第三方键盘库）；
 * - DPAD_UP/DOWN 在输入框上直接跳出输入框（避免被文本光标吞掉导致「卡在输入框」）；
 * - 结果列表用 LazyColumn；分栏切换 / 首屏数据落地后由 SearchState.focusSignal 复位焦点；
 * - 搜索历史用 LazyRow（tv-foundation 1.0.0 已移除 TvLazy*，本项目统一用 foundation lazy）。
 */
package com.github.axtv.dv.ui.search

import axtv.ui.SearchUiSpec
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.foundation.ExperimentalTvFoundationApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.github.axtv.dv.ui.AxTvColors
import com.github.axtv.dv.ui.focusRing
import kotlinx.coroutines.launch

private const val VIDEO_COLUMNS = 3

/**
 * 单页搜索（热词/联想 → 结果 tab 全流程在一个页面内完成）。
 * 线 B 的固定交付签名；导航线若采用「搜索页 + 结果页」双路由，请用 SearchPage / SearchResultsPage。
 */
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onOpenDetail: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    SearchPageContent(
        initialQuery = null,
        autoSearch = false,
        submitToExternal = false,
        resultsBackToPrevious = false,
        onSubmitQuery = null,
        onBack = onBack,
        onOpenDetail = onOpenDetail,
        modifier = modifier,
    )
}

/**
 * 导航接线约定（ui/nav/CONTRACT.md）里的搜索输入页：
 * 只负责输入、热词与联想；提交后通过 onOpenResults(keyword) 上抛，页面自身不加载结果。
 */
@Composable
fun SearchPage(
    query: String? = null,
    onOpenResults: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SearchPageContent(
        initialQuery = query,
        autoSearch = false,
        submitToExternal = true,
        resultsBackToPrevious = false,
        onSubmitQuery = onOpenResults,
        onBack = onBack,
        onOpenDetail = { },
        modifier = modifier,
    )
}

/**
 * 导航接线约定里的搜索结果页：按路由参数 query 直接进入结果态，
 * 页面内仍可编辑关键词重新搜索；点击视频/直播通过 onOpenDetail(awemeId) 上抛。
 */
@Composable
fun SearchResultsPage(
    query: String,
    onOpenDetail: (awemeId: String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SearchPageContent(
        initialQuery = query,
        autoSearch = true,
        submitToExternal = false,
        resultsBackToPrevious = true,
        onSubmitQuery = null,
        onBack = onBack,
        onOpenDetail = onOpenDetail,
        modifier = modifier,
    )
}

@OptIn(ExperimentalTvFoundationApi::class)
@Composable
private fun SearchPageContent(
    initialQuery: String?,
    autoSearch: Boolean,
    submitToExternal: Boolean,
    resultsBackToPrevious: Boolean,
    onSubmitQuery: ((String) -> Unit)?,
    onBack: () -> Unit,
    onOpenDetail: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val historyStore = remember(context) { SearchHistoryStore(context) }
    val state = remember { SearchState(scope, historyStore) }
    val focusManager = LocalFocusManager.current

    val searchRequester = remember { FocusRequester() }
    val hotRetryRequester = remember { FocusRequester() }
    val suggestRetryRequester = remember { FocusRequester() }
    val contentActionRequester = remember { FocusRequester() }

    var searchFocused by remember { mutableStateOf(false) }
    var fieldValue by remember { mutableStateOf(TextFieldValue("")) }
    var hadResults by remember { mutableStateOf(false) }

    // 结果列表状态按分栏独立保存：切换 tab 回来时沿用滚动位置。
    val videoListState = rememberLazyListState()
    val userListState = rememberLazyListState()
    val liveListState = rememberLazyListState()
    // 搜索历史横排（LazyRow）的滚动状态。
    val historyListState = rememberLazyListState()

    // 焦点位置按分栏记忆：视频用扁平卡片下标，用户/直播用行下标。
    var videoFocus by remember { mutableStateOf(0) }
    var userFocus by remember { mutableStateOf(0) }
    var liveFocus by remember { mutableStateOf(0) }

    val videoRequesters = remember { mutableMapOf<Int, FocusRequester>() }
    val userRequesters = remember { mutableMapOf<Int, FocusRequester>() }
    val liveRequesters = remember { mutableMapOf<Int, FocusRequester>() }

    // 热搜词/联想词点击回填后，把文本同步回输入框（光标放末尾）。
    LaunchedEffect(state.query) {
        if (fieldValue.text != state.query) {
            fieldValue = TextFieldValue(state.query, TextRange(state.query.length))
        }
    }

    // 统一的「发起搜索意图」：单页模式在本页加载结果，双路由模式只上抛关键词。
    val searchIntent: (String) -> Unit = { word ->
        val keyword = word.trim()
        if (keyword.isNotEmpty()) {
            focusManager.clearFocus()
            if (submitToExternal) {
                state.prefill(keyword)
                onSubmitQuery?.invoke(keyword)
            } else {
                state.searchWord(keyword)
            }
        }
    }

    LaunchedEffect(Unit) {
        initialQuery?.let { state.initialize(it, autoSearch) }
        state.loadHistory()
        state.loadHot()
        // 结果页（autoSearch）不要抢焦点拉起输入法；输入页/单页才初始聚焦输入框。
        if (!autoSearch) {
            withFrameNanos { }
            runCatching { searchRequester.requestFocus() }
        }
    }

    // BACK：单页模式结果态 → 编辑态；双路由结果页 / 编辑态 → 交给导航（不直接退出应用）。
    BackHandler {
        if (state.showResults && !resultsBackToPrevious) {
            state.closeResults()
            focusManager.clearFocus()
        } else {
            onBack()
        }
    }

    // 首屏数据落地 / 切到已加载分栏时复位焦点；空态与错误态则把焦点交给面板按钮。
    LaunchedEffect(state.focusSignal) {
        if (state.focusSignal == 0L) {
            return@LaunchedEffect
        }
        withFrameNanos { }
        when (state.focusTab) {
            SearchTab.VIDEO -> {
                val items = state.video.items
                if (items.isEmpty()) {
                    runCatching { contentActionRequester.requestFocus() }
                } else {
                    val target = videoFocus.coerceIn(0, items.size - 1)
                    runCatching { videoListState.scrollToItem(target / VIDEO_COLUMNS) }
                    withFrameNanos { }
                    withFrameNanos { }
                    runCatching { videoRequesters[target]?.requestFocus() }
                }
            }

            SearchTab.USER -> {
                val items = state.users.items
                if (items.isEmpty()) {
                    runCatching { contentActionRequester.requestFocus() }
                } else {
                    val target = userFocus.coerceIn(0, items.size - 1)
                    runCatching { userListState.scrollToItem(target) }
                    withFrameNanos { }
                    withFrameNanos { }
                    runCatching { userRequesters[target]?.requestFocus() }
                }
            }

            SearchTab.LIVE -> {
                val items = state.lives.items
                if (items.isEmpty()) {
                    runCatching { contentActionRequester.requestFocus() }
                } else {
                    val target = liveFocus.coerceIn(0, items.size - 1)
                    runCatching { liveListState.scrollToItem(target) }
                    withFrameNanos { }
                    withFrameNanos { }
                    runCatching { liveRequesters[target]?.requestFocus() }
                }
            }
        }
    }

    // 从结果页回到编辑态：焦点交还输入框。
    LaunchedEffect(state.showResults) {
        if (state.showResults) {
            hadResults = true
        } else if (hadResults) {
            withFrameNanos { }
            runCatching { searchRequester.requestFocus() }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AxTvColors.Bg)
            .padding(horizontal = 40.dp, vertical = 20.dp),
    ) {
        Text(
            text = "搜索",
            color = AxTvColors.TextPrimary,
            style = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold),
        )
        Spacer(Modifier.height(10.dp))

        SearchBar(
            field = fieldValue,
            focused = searchFocused,
            onFocusChange = { searchFocused = it },
            onFieldChange = {
                fieldValue = it
                state.onQueryChange(it.text)
            },
            onBack = onBack,
            onSubmit = {
                val keyword = state.query.trim()
                if (keyword.isNotEmpty()) {
                    focusManager.clearFocus()
                    if (submitToExternal) {
                        onSubmitQuery?.invoke(keyword)
                    } else {
                        state.submit()
                    }
                }
            },
            onClear = {
                fieldValue = TextFieldValue("")
                state.onQueryChange("")
            },
            requester = searchRequester,
        )
        Spacer(Modifier.height(14.dp))

        if (state.showResults) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SearchTab.entries.forEach { target ->
                    TabChip(
                        label = target.label,
                        selected = state.tab == target,
                        onClick = { state.selectTab(target) },
                    )
                }
            }
            Spacer(Modifier.height(10.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                when (state.tab) {
                    SearchTab.VIDEO -> {
                        val results = state.video
                        when {
                            results.loading -> StatePanel(
                                title = "正在搜索视频…",
                                detail = "关键词：${state.committedKeyword}",
                            )

                            results.error != null -> StatePanel(
                                title = "搜索失败",
                                detail = results.error.orEmpty(),
                                diagnostic = results.diagnostic,
                                actionLabel = "重试",
                                actionRequester = contentActionRequester,
                                onAction = { state.retryTab(SearchTab.VIDEO) },
                            )

                            results.items.isEmpty() -> StatePanel(
                                title = SearchUiSpec.emptyVideoLabel(),
                                detail = "可以更换关键词，或点击下方按钮重新搜索。",
                                diagnostic = results.diagnostic,
                                actionLabel = "重新搜索",
                                actionRequester = contentActionRequester,
                                onAction = { state.retryTab(SearchTab.VIDEO) },
                            )

                            else -> {
                                val rows = results.items.chunked(VIDEO_COLUMNS)
                                LazyColumn(
                                    state = videoListState,
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    items(
                                        count = rows.size,
                                        key = { index -> rows[index].first().id },
                                    ) { rowIndex ->
                                        val row = rows[rowIndex]
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                                        ) {
                                            row.forEachIndexed { column, item ->
                                                val flatIndex = rowIndex * VIDEO_COLUMNS + column
                                                val requester = remember(item.id) { FocusRequester() }
                                                SideEffect { videoRequesters[flatIndex] = requester }
                                                VideoCard(
                                                    item = item,
                                                    modifier = Modifier.weight(1f),
                                                    requester = requester,
                                                    onFocused = { videoFocus = flatIndex },
                                                    onClick = { onOpenDetail(item.id) },
                                                )
                                            }
                                            repeat(VIDEO_COLUMNS - row.size) {
                                                Spacer(Modifier.weight(1f))
                                            }
                                        }
                                    }
                                    if (results.hasMore || results.loadingMore || results.loadMoreError != null) {
                                        item(key = "video-more") {
                                            MoreRow(
                                                loading = results.loadingMore,
                                                error = results.loadMoreError,
                                                onLoadMore = { state.loadMore(SearchTab.VIDEO) },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    SearchTab.USER -> {
                        val results = state.users
                        when {
                            results.loading -> StatePanel(
                                title = "正在搜索用户…",
                                detail = "关键词：${state.committedKeyword}",
                            )

                            results.error != null -> StatePanel(
                                title = "搜索失败",
                                detail = results.error.orEmpty(),
                                diagnostic = results.diagnostic,
                                actionLabel = "重试",
                                actionRequester = contentActionRequester,
                                onAction = { state.retryTab(SearchTab.USER) },
                            )

                            results.items.isEmpty() -> StatePanel(
                                title = SearchUiSpec.emptyLabel(true),
                                detail = "可以更换关键词，或点击下方按钮重新搜索。",
                                diagnostic = results.diagnostic,
                                actionLabel = "重新搜索",
                                actionRequester = contentActionRequester,
                                onAction = { state.retryTab(SearchTab.USER) },
                            )

                            else -> LazyColumn(
                                state = userListState,
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                items(
                                    count = results.items.size,
                                    key = { index -> results.items[index].id },
                                ) { index ->
                                    val item = results.items[index]
                                    val requester = remember(item.id) { FocusRequester() }
                                    SideEffect { userRequesters[index] = requester }
                                    PersonRow(
                                        avatarUrl = item.avatarUrl,
                                        title = item.name,
                                        subtitle = userSubtitle(item),
                                        // 契约只提供 onOpenDetail(awemeId)，个人中心页也只支持看自己；
                                        // 他人主页不可达，因此明确做成「不可点」视觉。
                                        trailing = "主页暂不可进入",
                                        requester = requester,
                                        onFocused = { userFocus = index },
                                        onClick = { },
                                        clickable = false,
                                    )
                                }
                                if (results.hasMore || results.loadingMore || results.loadMoreError != null) {
                                    item(key = "user-more") {
                                        MoreRow(
                                            loading = results.loadingMore,
                                            error = results.loadMoreError,
                                            onLoadMore = { state.loadMore(SearchTab.USER) },
                                        )
                                    }
                                }
                            }
                        }
                    }

                    SearchTab.LIVE -> {
                        val results = state.lives
                        when {
                            results.loading -> StatePanel(
                                title = "正在搜索直播…",
                                detail = "关键词：${state.committedKeyword}",
                            )

                            results.error != null -> StatePanel(
                                title = "搜索失败",
                                detail = results.error.orEmpty(),
                                diagnostic = results.diagnostic,
                                actionLabel = "重试",
                                actionRequester = contentActionRequester,
                                onAction = { state.retryTab(SearchTab.LIVE) },
                            )

                            results.items.isEmpty() -> StatePanel(
                                title = SearchUiSpec.emptyLabel(false),
                                detail = "可以更换关键词，或点击下方按钮重新搜索。",
                                diagnostic = results.diagnostic,
                                actionLabel = "重新搜索",
                                actionRequester = contentActionRequester,
                                onAction = { state.retryTab(SearchTab.LIVE) },
                            )

                            else -> LazyColumn(
                                state = liveListState,
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                items(
                                    count = results.items.size,
                                    key = { index -> results.items[index].id },
                                ) { index ->
                                    val item = results.items[index]
                                    val requester = remember(item.id) { FocusRequester() }
                                    SideEffect { liveRequesters[index] = requester }
                                    PersonRow(
                                        avatarUrl = item.avatarUrl,
                                        title = item.name,
                                        subtitle = "直播间",
                                        trailing = "直播中",
                                        requester = requester,
                                        onFocused = { liveFocus = index },
                                        onClick = { onOpenDetail(item.id) },
                                        clickable = true,
                                    )
                                }
                                if (results.hasMore || results.loadingMore || results.loadMoreError != null) {
                                    item(key = "live-more") {
                                        MoreRow(
                                            loading = results.loadingMore,
                                            error = results.loadMoreError,
                                            onLoadMore = { state.loadMore(SearchTab.LIVE) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                if (state.query.isBlank()) {
                    // 原版空输入时「历史搜索」在「热搜榜」之上（qe4.java:1270 / 122）。
                    if (state.historyWords.isNotEmpty()) {
                        SectionTitle("历史搜索")
                        LazyRow(
                            state = historyListState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(
                                count = state.historyWords.size,
                                key = { index -> "history-${state.historyWords[index]}" },
                            ) { index ->
                                val word = state.historyWords[index]
                                HistoryChip(label = word, onClick = { searchIntent(word) })
                            }
                            item(key = "history-clear") {
                                HistoryChip(
                                    label = "清空历史",
                                    accent = true,
                                    onClick = {
                                        state.clearHistory()
                                        // 整行随清空消失：焦点交还输入框，避免焦点丢失。
                                        scope.launch {
                                            withFrameNanos { }
                                            runCatching { searchRequester.requestFocus() }
                                        }
                                    },
                                )
                            }
                        }
                        Spacer(Modifier.height(18.dp))
                    }
                    SectionTitle("热搜")
                    when {
                        state.hotLoading -> StatePanel(
                            title = "正在加载热搜…",
                            detail = "接口：SearchApi.hotSearch",
                        )

                        state.hotError != null -> StatePanel(
                            title = "热搜加载失败",
                            detail = state.hotError.orEmpty(),
                            actionLabel = "重试",
                            actionRequester = hotRetryRequester,
                            onAction = { state.loadHot() },
                        )

                        state.hotWords.isEmpty() -> StatePanel(
                            title = "暂无热搜",
                            detail = "接口返回成功但没有热词，可点击重试。",
                            actionLabel = "重试",
                            actionRequester = hotRetryRequester,
                            onAction = { state.loadHot() },
                        )

                        else -> LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(
                                count = state.hotWords.size,
                                key = { index -> "${state.hotWords[index].word()}#$index" },
                            ) { index ->
                                val word = state.hotWords[index]
                                HotWordRow(
                                    position = index + 1,
                                    word = word.word(),
                                    hotValue = word.hotValue(),
                                    onClick = { searchIntent(word.word()) },
                                )
                            }
                        }
                    }
                } else {
                    SectionTitle("联想词")
                    when {
                        state.suggestionsLoading -> StatePanel(
                            title = "正在联想…",
                            detail = "输入停顿后请求 SearchApi.suggestions",
                        )

                        state.suggestionsError != null -> StatePanel(
                            title = "联想失败",
                            detail = state.suggestionsError.orEmpty(),
                            actionLabel = "重试",
                            actionRequester = suggestRetryRequester,
                            onAction = { state.retrySuggestions() },
                        )

                        state.suggestions.isEmpty() -> StatePanel(
                            title = "没有匹配的联想词",
                            detail = "按输入法的搜索键或右侧「搜索」按钮直接提交。",
                        )

                        else -> LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            items(
                                count = state.suggestions.size,
                                key = { index -> "${state.suggestions[index]}#$index" },
                            ) { index ->
                                SuggestionRow(
                                    word = state.suggestions[index],
                                    onClick = { searchIntent(state.suggestions[index]) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
