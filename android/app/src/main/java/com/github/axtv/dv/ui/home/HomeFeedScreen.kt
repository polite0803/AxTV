/*
 * 样板页 1：首页信息流（真实数据 + 结构 + 焦点）。
 * 规格出处：refactor/ui/NOTES.md §信息流（i42 容器 / on5 视频页）、
 *           refactor/ui/axtv/ui/FeedPageSpec.java、HomeTabsSpec.java。
 * 数据：refactor/app/axtv/app/FeedDataSource.loadFeed(cursor, recentIds)（cursor = 已加载页数）。
 * 栈：LazyRow（相关推荐）+ LazyColumn（主信息流），焦点索引跨页记忆。
 * 错误处理：加载中 / 失败（可重试）/ 空态均有画面，绝不把异常吞成空列表。
 */
package com.github.axtv.dv.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.foundation.ExperimentalTvFoundationApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.tv.material3.Text
import axtv.api.ApiException
import axtv.model.Aweme
import axtv.ui.DetailPageSpec
import axtv.ui.FeedPageSpec
import axtv.ui.HomeTabsSpec
import coil.compose.AsyncImage
import com.github.axtv.dv.adapter.ViewedVideoTracker
import com.github.axtv.dv.ui.AxTvColors
import com.github.axtv.dv.ui.appearance.AmbientBackdrop
import com.github.axtv.dv.ui.appearance.AxAppearance
import com.github.axtv.dv.ui.focusRing
import com.github.axtv.dv.ui.nav.NavRoutes
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

/** 单页最多展示的相关推荐条数（与原有 LazyRow 视觉保持一致）。 */
private const val RELATED_LIMIT = 5

/** 提前多少条触发自动续页（焦点或滚动到达末尾前 N 条就开始拉下一页）。 */
private const val AUTO_LOAD_AHEAD = 3

/** 加载更多时回传给推荐接口的最近作品数（原版取 10 个）。 */
private const val RECENT_ID_LIMIT = 10

/** 连续这么多页没有新增作品就停止自动续页，避免服务端重复返回同一页时无限请求。 */
private const val MAX_STALLED_PAGES = 3

/** 首屏至少先备这么多条：服务端偶尔只回 2 条时，不至于让整页看起来只有两个视频。 */
private const val MIN_FEED_BUFFER = 10

/**
 * 信息流条目的组合键：必须与 items(key=) 用同一个函数。
 * 之前 requester 是 remember { FocusRequester() }（无 key），LazyColumn 回收槽位时
 * 会把某个请求器和另一个条目绑在一起，requestFocus() 于是把焦点甩到很远的地方。
 */
private fun feedKey(awemeId: String, index: Int): String = "${awemeId.ifEmpty { "feed" }}#$index"

@OptIn(ExperimentalTvFoundationApi::class)
@Composable
fun HomeFeedScreen(
    rememberedFocusIndex: Int,
    onFocusIndexChange: (Int) -> Unit,
    onOpenPlayer: (startIndex: Int) -> Unit,
    onOpenRoute: (route: String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var activeTab by rememberSaveable { mutableStateOf(HomeTabsSpec.TAB_RECOMMEND) }
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val requesters = remember { mutableMapOf<String, FocusRequester>() }
    val retryRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()

    var feedItems by remember { mutableStateOf(HomeFeedRepository.cachedAwemes) }
    var loading by remember { mutableStateOf(feedItems.isEmpty()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var loadingMore by remember { mutableStateOf(false) }
    var loadMoreError by remember { mutableStateOf<String?>(null) }
    var nextCursor by remember { mutableStateOf(HomeFeedRepository.cachedNextCursor) }
    var hasMore by remember { mutableStateOf(HomeFeedRepository.cachedHasMore) }
    var focusRestored by remember { mutableStateOf(false) }
    // 每次加载结束 +1：即使这一页没有带来新条目，也要重新判断一次「是否已经接近末尾」，
    // 否则第一页加载更多返回重复数据后，焦点卡在最后一条就再也不会触发下一页。
    var pagingTick by remember { mutableIntStateOf(0) }
    var stalledPages by remember { mutableIntStateOf(0) }
    var backdropCoverUrl by remember { mutableStateOf("") }

    /** 列表变动后清掉已消失条目的焦点请求器：残留条目会让 requestFocus() 甩到远端。 */
    fun pruneRequesters() {
        val alive = feedItems.indices.map { feedKey(feedItems[it].awemeId(), it) }.toSet()
        requesters.keys.retainAll(alive)
    }

    suspend fun loadFirstPage() {
        loading = true
        errorMessage = null
        loadMoreError = null
        val result = HomeFeedRepository.load(0L)
        result.fold(
            onSuccess = { page ->
                feedItems = page.awemes.distinctBy { it.awemeId() }
                HomeFeedRepository.cache(feedItems, 1L, page.hasMore)
                nextCursor = 1L
                hasMore = page.hasMore
                stalledPages = 0
                focusRestored = false
                pruneRequesters()
            },
            onFailure = { errorMessage = describeFeedError(it) },
        )
        loading = false
    }

    suspend fun loadNextPage() {
        if (loading || loadingMore || !hasMore) return
        loadingMore = true
        loadMoreError = null
        val beforeSize = feedItems.size
        val recentIds = feedItems
            .takeLast(RECENT_ID_LIMIT)
            .mapNotNull { it.awemeId().takeIf(String::isNotEmpty) }
        try {
            val result = HomeFeedRepository.load(nextCursor, recentIds)
            result.fold(
                onSuccess = { page ->
                    val merged = (feedItems + page.awemes).distinctBy { it.awemeId() }
                    feedItems = merged
                    nextCursor += 1
                    hasMore = page.hasMore
                    stalledPages = if (merged.size > beforeSize) 0 else stalledPages + 1
                    if (stalledPages >= MAX_STALLED_PAGES) hasMore = false
                    HomeFeedRepository.cache(merged, nextCursor, hasMore)
                    pruneRequesters()
                },
                onFailure = { loadMoreError = describeFeedError(it) },
            )
        } finally {
            loadingMore = false
            pagingTick += 1
        }
    }

    // 有缓存就直接复用：从播放器/详情页返回时不再重拉首屏，也不会把已翻出来的列表打回第一页。
    LaunchedEffect(Unit) {
        if (feedItems.isEmpty()) loadFirstPage() else focusRestored = false
    }

    // 电视端没有「上滑」手势，翻页必须自己发生：焦点或滚动逼近列表末尾就请求下一页。
    // 原来这里是 FeedFooter 里一个「上滑加载更多」按钮，等于要用户先发现它、再按一次才能继续看。
    val scrolledNearEnd by remember(listState) {
        derivedStateOf {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            info.totalItemsCount > 0 && lastVisible >= info.totalItemsCount - AUTO_LOAD_AHEAD
        }
    }
    // loadingMore 不能进 key：loadNextPage 自己会把它置 true，key 变化会取消正在执行的请求，
    // 导致 loadingMore 永久卡在 true（首页再也翻不了页）。这里只保留会改变「是否需要翻页」的输入。
    LaunchedEffect(
        rememberedFocusIndex,
        scrolledNearEnd,
        feedItems.size,
        hasMore,
        loading,
        loadMoreError,
        pagingTick,
    ) {
        if (!hasMore || loading || loadingMore || loadMoreError != null) return@LaunchedEffect
        val focusNearEnd = feedItems.isNotEmpty() &&
            rememberedFocusIndex >= feedItems.size - AUTO_LOAD_AHEAD
        // 服务端偶尔只回很少几条：先把缓冲区补到 MIN_FEED_BUFFER，否则电视上看起来只有两个视频。
        val needsBuffer = feedItems.size < MIN_FEED_BUFFER
        if (focusNearEnd || scrolledNearEnd || needsBuffer) loadNextPage()
    }

    // 焦点记忆：数据到位后滚到上次条目，下一帧再请求焦点。
    LaunchedEffect(feedItems.size) {
        if (!focusRestored && feedItems.isNotEmpty()) {
            val target = rememberedFocusIndex.coerceIn(0, feedItems.size - 1)
            runCatching { listState.scrollToItem(target) }
            withFrameNanos { }
            runCatching { requesters[feedKey(feedItems[target].awemeId(), target)]?.requestFocus() }
            focusRestored = true
        }
    }

    // 失败态没有列表可聚焦，把焦点交给「重试」按钮，保证遥控器可操作。
    LaunchedEffect(errorMessage, feedItems.size) {
        if (errorMessage != null && feedItems.isEmpty()) {
            withFrameNanos { }
            runCatching { retryRequester.requestFocus() }
        }
    }

    // 氛围背景 / 画面取色的首页宿主：焦点条目的封面 = 「当前封面」。
    // version 进 key：切到画面取色（或改其它外观项）后无需翻页也会重取一次。
    val focusCoverUrl = feedItems
        .getOrNull(rememberedFocusIndex.coerceIn(0, (feedItems.size - 1).coerceAtLeast(0)))
        ?.let { DetailPageSpec.coverUrl(it.video()) }
        .orEmpty()
    LaunchedEffect(focusCoverUrl, AxAppearance.version.intValue) {
        // 开「画面取色」时每次移动焦点都会换一张封面：去抖 250ms，
        // 连按方向键的过程中不要每张封面都跑一次 Coil 取色 + 全局重着色（重着色会重组整棵树）。
        // 背景图同步去抖：否则每移一格都下载/解码一张全屏封面，电视上会明显卡顿。
        delay(250L)
        backdropCoverUrl = focusCoverUrl
        AxAppearance.sampleCoverSeed(context, focusCoverUrl)
    }

    Box(modifier = modifier.fillMaxSize()) {
        AmbientBackdrop(backdropCoverUrl)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 40.dp, vertical = 20.dp),
        ) {
        Text(
            text = "首页信息流",
            color = AxTvColors.TextPrimary,
            style = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold),
        )
        Spacer(Modifier.height(14.dp))

        // 首页快捷入口：把无内部回调的页面（历史/收藏/稍后再看/登录）接到统一路由。
        // 「详情」跟随当前焦点条目，但入口本身常驻不增删：原来是按焦点有没有才渲染，
        // 信息流每移一次焦点顶栏就重排一次，「相邻」关系跟着变，表现为按一下焦点跑很远。
        val focusedAwemeId = feedItems.let { items ->
            items.getOrNull(rememberedFocusIndex) ?: items.firstOrNull()
        }?.awemeId()?.takeIf { it.isNotEmpty() }
        val detailEntry: Pair<String, () -> Unit>? = focusedAwemeId
            ?.let { id -> "详情" to { onOpenRoute(NavRoutes.detail(id)) } }
        Row(
            modifier = Modifier.focusGroup(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOfNotNull(
                detailEntry,
                "搜索" to { onOpenRoute(NavRoutes.SEARCH) },
                "直播" to { onOpenRoute(NavRoutes.LIVE) },
                "合集" to { onOpenRoute(NavRoutes.MIX_LIST) },
                "个人" to { onOpenRoute(NavRoutes.PROFILE) },
                "历史" to { onOpenRoute(NavRoutes.PROFILE_HISTORY) },
                "收藏" to { onOpenRoute(NavRoutes.PROFILE_FAVORITES) },
                "稍后再看" to { onOpenRoute(NavRoutes.PROFILE_LATER) },
                "登录" to { onOpenRoute(NavRoutes.LOGIN) },
                "设置" to { onOpenRoute(NavRoutes.SETTINGS) },
            ).forEach { (label, action) ->
                HomeTabChip(label = label, selected = false, onClick = action)
            }
        }
        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.focusGroup(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(
                HomeTabsSpec.TAB_RECOMMEND,
                HomeTabsSpec.TAB_FOLLOW,
                HomeTabsSpec.TAB_FEATURED,
                HomeTabsSpec.TAB_MOBILE,
            ).forEach { tab ->
                HomeTabChip(
                    label = tab,
                    selected = tab == activeTab,
                    onClick = {
                        // 「精选」是真入口：跳到独立精选页；「关注」「移动推荐」保持占位，本轮不动。
                        if (tab == HomeTabsSpec.TAB_FEATURED) {
                            onOpenRoute(NavRoutes.FEATURED)
                        } else {
                            activeTab = tab
                        }
                    },
                )
            }
        }
        Spacer(Modifier.height(16.dp))

        when {
            loading -> FeedStatePanel(
                title = "加载中…",
                detail = "正在请求首页推荐流（FeedDataSource.loadFeed）",
            )

            errorMessage != null && feedItems.isEmpty() -> FeedStatePanel(
                title = "加载失败",
                detail = errorMessage.orEmpty(),
                actionLabel = "重试",
                actionRequester = retryRequester,
                onAction = { scope.launch { loadFirstPage() } },
            )

            feedItems.isEmpty() -> FeedStatePanel(
                title = "暂无内容",
                detail = "接口返回成功但这一页没有作品，可稍后重试。",
                actionLabel = "重试",
                actionRequester = retryRequester,
                onAction = { scope.launch { loadFirstPage() } },
            )

            else -> {
                val related = feedItems.take(RELATED_LIMIT)
                // 本地「已看」集合：整页都看过时（原版会退回未过滤列表）用它标出重复条目。
                val viewedIds = remember(feedItems) { ViewedVideoTracker.viewedIds() }
                Text(
                    text = FeedPageSpec.TEXT_RELATED,
                    color = AxTvColors.TextPrimary,
                    style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
                )
                LazyRow(
                    // 焦点分组：一行卡片是一个岛，左右键在岛内移动，上下键才离岛。
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusGroup(),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(count = related.size) { index ->
                        RelatedCard(
                            item = related[index],
                            viewed = related[index].awemeId() in viewedIds,
                            onClick = { onOpenPlayer(index) },
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    text = "推荐信息流",
                    color = AxTvColors.TextPrimary,
                    style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
                )
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .focusGroup(),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(
                        count = feedItems.size,
                        key = { index -> feedKey(feedItems[index].awemeId(), index) },
                    ) { index ->
                        val key = feedKey(feedItems[index].awemeId(), index)
                        val requester = remember(key) { FocusRequester() }
                        SideEffect { requesters[key] = requester }
                        FeedRow(
                            item = feedItems[index],
                            viewed = feedItems[index].awemeId() in viewedIds,
                            requester = requester,
                            onFocused = { onFocusIndexChange(index) },
                            onClick = { onOpenPlayer(index) },
                        )
                    }
                    item(key = "feed_footer") {
                        FeedFooter(
                            loadingMore = loadingMore,
                            error = loadMoreError,
                            hasMore = hasMore,
                            loadedCount = feedItems.size,
                            onLoadMore = { scope.launch { loadNextPage() } },
                        )
                    }
                }
            }
        }
    }
    }
}
