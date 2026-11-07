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
import com.github.axtv.dv.ui.nav.PLAYER_SOURCE_FOLLOW
import com.github.axtv.dv.ui.nav.PLAYER_SOURCE_MOBILE
import com.github.axtv.dv.ui.nav.PLAYER_SOURCE_RECOMMEND
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

/** 提前多少条触发自动续页（焦点或滚动到达末尾前 N 条就开始拉下一页）。 */
private const val AUTO_LOAD_AHEAD = 3

/** 加载更多时回传给推荐接口的最近作品数（原版取 10 个）。 */
private const val RECENT_ID_LIMIT = 10

/** 连续这么多页没有新增作品就停止自动续页，避免服务端重复返回同一页时无限请求。 */
private const val MAX_STALLED_PAGES = 3

/** 首屏至少先备这么多条：服务端偶尔只回 2 条时，不至于让整页看起来只有两个视频。 */
private const val MIN_FEED_BUFFER = 10

/** 可作为「落地标签」的取值（route `?tab=` 只接受这几个，其余静默回推荐）。 */
private val HOME_TAB_LABELS = setOf(
    HomeTabsSpec.TAB_RECOMMEND,
    HomeTabsSpec.TAB_FOLLOW,
    HomeTabsSpec.TAB_MOBILE,
)

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
    /** 落地时选中的标签；来自 route 的 `?tab=`（设置项「默认首页 → 移动推荐」）。 */
    initialTab: String? = null,
) {
    var activeTab by rememberSaveable {
        mutableStateOf(initialTab?.takeIf { it in HOME_TAB_LABELS } ?: HomeTabsSpec.TAB_RECOMMEND)
    }
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val requesters = remember { mutableMapOf<String, FocusRequester>() }
    val retryRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()

    // 初始快照跟随 activeTab（rememberSaveable 恢复时可能停在关注/移动推荐）。
    var feedItems by remember {
        mutableStateOf(
            when (activeTab) {
                HomeTabsSpec.TAB_FOLLOW -> FollowFeedRepository.cachedAwemes
                HomeTabsSpec.TAB_MOBILE -> MobileFeedRepository.cachedAwemes
                else -> HomeFeedRepository.cachedAwemes
            },
        )
    }
    var loading by remember { mutableStateOf(feedItems.isEmpty()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var loadingMore by remember { mutableStateOf(false) }
    var loadMoreError by remember { mutableStateOf<String?>(null) }
    // nextCursor 的含义按标签走：推荐=已加载页数；关注=服务端游标；移动推荐=refresh_index。
    var nextCursor by remember {
        mutableStateOf(
            when (activeTab) {
                HomeTabsSpec.TAB_FOLLOW -> FollowFeedRepository.cachedCursor
                HomeTabsSpec.TAB_MOBILE -> MobileFeedRepository.cachedNextIndex.toLong()
                else -> HomeFeedRepository.cachedNextCursor
            },
        )
    }
    var hasMore by remember {
        mutableStateOf(
            when (activeTab) {
                HomeTabsSpec.TAB_FOLLOW -> FollowFeedRepository.cachedAwemes.isNotEmpty()
                HomeTabsSpec.TAB_MOBILE -> MobileFeedRepository.cachedHasMore
                else -> HomeFeedRepository.cachedHasMore
            },
        )
    }
    var focusRestored by remember { mutableStateOf(false) }
    // 每次加载结束 +1：即使这一页没有带来新条目，也要重新判断一次「是否已经接近末尾」，
    // 否则第一页加载更多返回重复数据后，焦点卡在最后一条就再也不会触发下一页。
    var pagingTick by remember { mutableIntStateOf(0) }
    var stalledPages by remember { mutableIntStateOf(0) }
    // 刷新（已在看列表时再点一次「推荐」）：不把整页打成错误态，只在标题下提示一行。
    var refreshing by remember { mutableStateOf(false) }
    var refreshMessage by remember { mutableStateOf<String?>(null) }
    var backdropCoverUrl by remember { mutableStateOf("") }

    /** 列表变动后清掉已消失条目的焦点请求器：残留条目会让 requestFocus() 甩到远端。 */
    fun pruneRequesters() {
        val alive = feedItems.indices.map { feedKey(feedItems[it].awemeId(), it) }.toSet()
        requesters.keys.retainAll(alive)
    }

    suspend fun loadFirstPage() {
        // 已有内容时是「刷新」（保留列表 + 标题下提示一行），否则是首屏加载（走加载/错误态）。
        val firstLoad = feedItems.isEmpty()
        if (firstLoad) loading = true else refreshing = true
        errorMessage = null
        loadMoreError = null
        refreshMessage = null
        try {
            when (activeTab) {
                HomeTabsSpec.TAB_FOLLOW -> {
                    val r = FollowFeedRepository.refresh()
                    r.fold(
                        onSuccess = { fp ->
                            feedItems = fp.awemes.distinctBy { it.awemeId() }
                            FollowFeedRepository.cache(feedItems, fp.nextCursor)
                            nextCursor = fp.nextCursor
                            hasMore = feedItems.isNotEmpty() || fp.nextCursor > 0
                            stalledPages = 0
                            focusRestored = false
                            pruneRequesters()
                        },
                        onFailure = { t ->
                            if (firstLoad) errorMessage = describeFeedError(t)
                            else refreshMessage = "刷新失败：${describeFeedError(t)}"
                        },
                    )
                }
                HomeTabsSpec.TAB_MOBILE -> {
                    val r = MobileFeedRepository.load(1)
                    r.fold(
                        onSuccess = { fp ->
                            feedItems = fp.awemes.distinctBy { it.awemeId() }
                            MobileFeedRepository.cache(feedItems, 2, fp.hasMore)
                            nextCursor = 2L
                            hasMore = fp.hasMore
                            stalledPages = 0
                            focusRestored = false
                            pruneRequesters()
                        },
                        onFailure = { t ->
                            if (firstLoad) errorMessage = describeFeedError(t)
                            else refreshMessage = "刷新失败：${describeFeedError(t)}"
                        },
                    )
                }
                else -> {
                    val r = HomeFeedRepository.refresh()
                    r.fold(
                        onSuccess = { hp ->
                            feedItems = hp.awemes.distinctBy { it.awemeId() }
                            HomeFeedRepository.cache(feedItems, hp.nextCursor, hp.hasMore)
                            nextCursor = hp.nextCursor
                            hasMore = hp.hasMore
                            stalledPages = 0
                            focusRestored = false
                            pruneRequesters()
                        },
                        onFailure = { t ->
                            if (firstLoad) errorMessage = describeFeedError(t)
                            else refreshMessage = "刷新失败：${describeFeedError(t)}"
                        },
                    )
                }
            }
        } finally {
            loading = false
            refreshing = false
        }
    }

    suspend fun loadNextPage() {
        // refreshing 也要挡：刷新请求在途时，「补到 MIN_FEED_BUFFER」那条会自动翻页会和刷新同时发请求。
        if (loading || loadingMore || !hasMore || refreshing) return
        loadingMore = true
        loadMoreError = null
        val beforeSize = feedItems.size
        val recentIds = feedItems
            .takeLast(RECENT_ID_LIMIT)
            .mapNotNull { it.awemeId().takeIf(String::isNotEmpty) }
        try {
            when (activeTab) {
                HomeTabsSpec.TAB_FOLLOW -> {
                    val r = FollowFeedRepository.load(nextCursor)
                    r.fold(
                        onSuccess = { fp ->
                            val merged = (feedItems + fp.awemes).distinctBy { it.awemeId() }
                            feedItems = merged
                            nextCursor = fp.nextCursor
                            hasMore = fp.nextCursor > 0
                            stalledPages = if (merged.size > beforeSize) 0 else stalledPages + 1
                            if (stalledPages >= MAX_STALLED_PAGES) hasMore = false
                            FollowFeedRepository.cache(merged, nextCursor)
                            pruneRequesters()
                        },
                        onFailure = { loadMoreError = describeFeedError(it) },
                    )
                }
                HomeTabsSpec.TAB_MOBILE -> {
                    val r = MobileFeedRepository.load(nextCursor.toInt())
                    r.fold(
                        onSuccess = { fp ->
                            val merged = (feedItems + fp.awemes).distinctBy { it.awemeId() }
                            feedItems = merged
                            nextCursor += 1
                            hasMore = fp.hasMore
                            stalledPages = if (merged.size > beforeSize) 0 else stalledPages + 1
                            if (stalledPages >= MAX_STALLED_PAGES) hasMore = false
                            MobileFeedRepository.cache(merged, nextCursor.toInt(), hasMore)
                            pruneRequesters()
                        },
                        onFailure = { loadMoreError = describeFeedError(it) },
                    )
                }
                else -> {
                    val r = HomeFeedRepository.load(nextCursor, recentIds)
                    r.fold(
                        onSuccess = { hp ->
                            val merged = (feedItems + hp.awemes).distinctBy { it.awemeId() }
                            feedItems = merged
                            nextCursor = hp.nextCursor
                            hasMore = hp.hasMore
                            stalledPages = if (merged.size > beforeSize) 0 else stalledPages + 1
                            if (stalledPages >= MAX_STALLED_PAGES) hasMore = false
                            HomeFeedRepository.cache(merged, nextCursor, hasMore)
                            pruneRequesters()
                        },
                        onFailure = { loadMoreError = describeFeedError(it) },
                    )
                }
            }
        } finally {
            loadingMore = false
            pagingTick += 1
        }
    }

    suspend fun selectTab(tab: String) {
        if (tab == activeTab) return
        activeTab = tab
        // 切换标签：用该标签的缓存瞬间填充，再按需拉首屏。
        feedItems = when (tab) {
            HomeTabsSpec.TAB_FOLLOW -> FollowFeedRepository.cachedAwemes
            HomeTabsSpec.TAB_MOBILE -> MobileFeedRepository.cachedAwemes
            else -> HomeFeedRepository.cachedAwemes
        }
        nextCursor = when (tab) {
            HomeTabsSpec.TAB_FOLLOW -> FollowFeedRepository.cachedCursor
            HomeTabsSpec.TAB_MOBILE -> MobileFeedRepository.cachedNextIndex.toLong()
            else -> HomeFeedRepository.cachedNextCursor
        }
        hasMore = when (tab) {
            HomeTabsSpec.TAB_FOLLOW -> FollowFeedRepository.cachedAwemes.isNotEmpty()
            HomeTabsSpec.TAB_MOBILE -> MobileFeedRepository.cachedHasMore
            else -> HomeFeedRepository.cachedHasMore
        }
        loading = feedItems.isEmpty()
        errorMessage = null
        loadMoreError = null
        refreshMessage = null
        focusRestored = false
        pruneRequesters()
        if (feedItems.isEmpty()) loadFirstPage()
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
        activeTab,
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

    // 失败/空态没有列表可聚焦，把焦点交给「重试」按钮，保证遥控器可操作（两种状态同一出口）。
    LaunchedEffect(errorMessage, feedItems.size, loading) {
        if (!loading && feedItems.isEmpty()) {
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
        // 文案逐字取 HomeTabsSpec（i42.m5308P）：原版各 tab 的刷新提示并不都是「刷新…内容」。
        val statusText = when {
            refreshing -> when (activeTab) {
                HomeTabsSpec.TAB_FOLLOW -> HomeTabsSpec.REFRESH_FOLLOW
                HomeTabsSpec.TAB_MOBILE -> HomeTabsSpec.REFRESH_MOBILE
                else -> HomeTabsSpec.REFRESH_HOME
            }
            refreshMessage != null -> refreshMessage
            else -> null
        }
        if (statusText != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = statusText,
                color = AxTvColors.TextSecondary,
                style = TextStyle(fontSize = 14.sp),
            )
        }
        Spacer(Modifier.height(14.dp))

        // 首页快捷入口：把无内部回调的页面（历史/收藏/稍后再看/登录）接到统一路由。
        // 「详情」不在这里：每条信息流卡片右侧自带就近的详情按钮（FeedRow）。
        Row(
            modifier = Modifier.focusGroup(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // 快捷动作只留顶栏没有的入口：搜索/热榜/直播/合集/个人/设置 已在全局顶栏，
            // 重复摆放会让首页多出一整行。
            listOf(
                "历史" to { onOpenRoute(NavRoutes.PROFILE_HISTORY) },
                "收藏" to { onOpenRoute(NavRoutes.PROFILE_FAVORITES) },
                "稍后再看" to { onOpenRoute(NavRoutes.PROFILE_LATER) },
                "登录" to { onOpenRoute(NavRoutes.LOGIN) },
            ).forEach { (label, action) ->
                HomeTabChip(label = label, selected = false, onClick = action)
            }
            Spacer(Modifier.width(16.dp))
            // 信息流标签：推荐 / 关注 / 移动推荐（「精选」是独立页面，顶栏已有入口，不再重复）。
            listOf(
                HomeTabsSpec.TAB_RECOMMEND,
                HomeTabsSpec.TAB_FOLLOW,
                HomeTabsSpec.TAB_MOBILE,
            ).forEach { tab ->
                HomeTabChip(
                    label = tab,
                    selected = tab == activeTab,
                    onClick = {
                        if (tab == activeTab) {
                            // 对齐原版 i42.m5308P：同 Tab 再触发 → 提示刷新文案并刷新该 Tab。
                            // 已在加载中/已在刷新就忽略，避免连按攒出多个并发请求互相覆盖。
                            if (!loading && !refreshing) {
                                scope.launch { loadFirstPage() }
                            }
                        } else {
                            scope.launch { selectTab(tab) }
                        }
                    },
                )
            }
        }
        Spacer(Modifier.height(16.dp))

        when {
            loading -> FeedStatePanel(
                title = "加载中…",
                detail = "正在请求${activeTab}（FeedDataSource.loadFeed）",
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
                detail = when (activeTab) {
                    HomeTabsSpec.TAB_FOLLOW -> FollowFeedRepository.EMPTY_MESSAGE
                    HomeTabsSpec.TAB_MOBILE -> "移动推荐暂无内容"
                    else -> "接口返回成功但这一页没有作品，可稍后重试。"
                },
                actionLabel = "重试",
                actionRequester = retryRequester,
                onAction = { scope.launch { loadFirstPage() } },
            )

            else -> {
                val playerSource = when (activeTab) {
                    HomeTabsSpec.TAB_FOLLOW -> PLAYER_SOURCE_FOLLOW
                    HomeTabsSpec.TAB_MOBILE -> PLAYER_SOURCE_MOBILE
                    else -> PLAYER_SOURCE_RECOMMEND
                }
                // 本地「已看」集合：整页都看过时（原版会退回未过滤列表）用它标出重复条目。
                val viewedIds = remember(feedItems) { ViewedVideoTracker.viewedIds() }
                Text(
                    text = when (activeTab) {
                        HomeTabsSpec.TAB_FOLLOW -> "关注信息流"
                        HomeTabsSpec.TAB_MOBILE -> "移动推荐"
                        else -> "推荐信息流"
                    },
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
                            onClick = {
                                // 非推荐标签也走队列播放：PlayerFromFeedScreen 按 source 选该标签的仓储续页，
                                // 只播单条会让「到末尾自动翻页」在这两个标签上失效。
                                if (playerSource == PLAYER_SOURCE_RECOMMEND) {
                                    onOpenPlayer(index)
                                } else {
                                    onOpenRoute(NavRoutes.player(index, playerSource))
                                }
                            },
                            onDetail = {
                                val id = feedItems[index].awemeId()
                                if (id.isNotEmpty()) onOpenRoute(NavRoutes.detail(id))
                            },
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
