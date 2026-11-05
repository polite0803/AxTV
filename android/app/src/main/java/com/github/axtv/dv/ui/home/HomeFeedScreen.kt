/*
 * 样板页 1：首页信息流（真实数据 + 结构 + 焦点）。
 * 规格出处：refactor/ui/NOTES.md §信息流（i42 容器 / on5 视频页）、
 *           refactor/ui/axtv/ui/FeedPageSpec.java、HomeTabsSpec.java。
 * 数据：refactor/app/axtv/app/FeedDataSource.loadFeed(cursor)（cursor = 页序号）。
 * 栈：LazyRow（相关推荐）+ LazyColumn（主信息流），焦点索引跨页记忆。
 * 错误处理：加载中 / 失败（可重试）/ 空态均有画面，绝不把异常吞成空列表。
 */
package com.github.axtv.dv.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
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
import com.github.axtv.dv.adapter.ViewedVideoTracker
import com.github.axtv.dv.ui.AxTvColors
import com.github.axtv.dv.ui.appearance.AmbientBackdrop
import com.github.axtv.dv.ui.appearance.AxAppearance
import com.github.axtv.dv.ui.focusRing
import com.github.axtv.dv.ui.nav.NavRoutes
import kotlinx.coroutines.launch
import java.util.Locale

/** 单页最多展示的相关推荐条数（与原有 LazyRow 视觉保持一致）。 */
private const val RELATED_LIMIT = 5

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
    val requesters = remember { mutableMapOf<Int, FocusRequester>() }
    val retryRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()

    var feedItems by remember { mutableStateOf<List<Aweme>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var loadingMore by remember { mutableStateOf(false) }
    var loadMoreError by remember { mutableStateOf<String?>(null) }
    var nextCursor by remember { mutableStateOf(1L) }
    var hasMore by remember { mutableStateOf(true) }
    var focusRestored by remember { mutableStateOf(false) }

    suspend fun loadFirstPage() {
        loading = true
        errorMessage = null
        loadMoreError = null
        val result = HomeFeedRepository.load(0L)
        result.fold(
            onSuccess = { page ->
                feedItems = page.distinctBy { it.awemeId() }
                HomeFeedRepository.cache(feedItems)
                nextCursor = 1L
                hasMore = page.isNotEmpty()
                focusRestored = false
            },
            onFailure = { errorMessage = describeFeedError(it) },
        )
        loading = false
    }

    suspend fun loadNextPage() {
        if (loading || loadingMore || !hasMore) return
        loadingMore = true
        loadMoreError = null
        val result = HomeFeedRepository.load(nextCursor)
        result.fold(
            onSuccess = { page ->
                val merged = (feedItems + page).distinctBy { it.awemeId() }
                feedItems = merged
                HomeFeedRepository.cache(merged)
                nextCursor += 1
                hasMore = page.isNotEmpty()
            },
            onFailure = { loadMoreError = describeFeedError(it) },
        )
        loadingMore = false
    }

    LaunchedEffect(Unit) { loadFirstPage() }

    // 焦点记忆：数据到位后滚到上次条目，下一帧再请求焦点。
    LaunchedEffect(feedItems.size) {
        if (!focusRestored && feedItems.isNotEmpty()) {
            val target = rememberedFocusIndex.coerceIn(0, feedItems.size - 1)
            runCatching { listState.scrollToItem(target) }
            withFrameNanos { }
            runCatching { requesters[target]?.requestFocus() }
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
        AxAppearance.sampleCoverSeed(context, focusCoverUrl)
    }

    Box(modifier = modifier.fillMaxSize()) {
        AmbientBackdrop(focusCoverUrl)
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
        // 「详情」跟随当前焦点条目：信息流点击仍按原行为进入播放，详情页由此单独进入。
        val focusedDetailRoute = feedItems
            .getOrNull(rememberedFocusIndex)
            ?.awemeId()
            ?.takeIf { it.isNotEmpty() }
            ?.let { NavRoutes.detail(it) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOfNotNull(
                focusedDetailRoute?.let { "详情" to it },
                "搜索" to NavRoutes.SEARCH,
                "直播" to NavRoutes.LIVE,
                "合集" to NavRoutes.MIX_LIST,
                "个人" to NavRoutes.PROFILE,
                "历史" to NavRoutes.PROFILE_HISTORY,
                "收藏" to NavRoutes.PROFILE_FAVORITES,
                "稍后再看" to NavRoutes.PROFILE_LATER,
                "登录" to NavRoutes.LOGIN,
                "设置" to NavRoutes.SETTINGS,
            ).forEach { (label, route) ->
                HomeTabChip(label = label, selected = false, onClick = { onOpenRoute(route) })
            }
        }
        Spacer(Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    modifier = Modifier.fillMaxWidth(),
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
                        .weight(1f),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(
                        count = feedItems.size,
                        key = { index -> "${feedItems[index].awemeId().ifEmpty { "feed" }}#$index" },
                    ) { index ->
                        val requester = remember { FocusRequester() }
                        SideEffect { requesters[index] = requester }
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

/** 把 ApiException 的种类翻译成可读文案；未识别的异常保留原始 message。 */
private fun describeFeedError(t: Throwable): String = when (t) {
    is ApiException -> when (t.kind()) {
        ApiException.Kind.NETWORK -> "网络请求失败：${t.message.orEmpty()}"
        ApiException.Kind.HTTP_STATUS -> "服务端返回 HTTP ${t.httpStatus()}"
        ApiException.Kind.EMPTY_BODY -> "空响应：签名参数失效或 Cookie 已过期"
        ApiException.Kind.HEADER_ERROR -> "请求被拦截：${t.message.orEmpty()}"
        ApiException.Kind.API_STATUS -> "接口状态异常：status_code=${t.apiStatus()}"
        ApiException.Kind.LOGIN_REQUIRED -> t.message.orEmpty()
        ApiException.Kind.RATE_LIMITED -> t.message.orEmpty()
        ApiException.Kind.DECODE -> "响应解析失败：${t.message.orEmpty()}"
        ApiException.Kind.MISSING_CREDENTIAL -> t.message.orEmpty()
        else -> "请求失败：${t.message.orEmpty()}"
    }

    else -> t.message ?: t.javaClass.simpleName
}

private fun formatCount(value: Long): String = when {
    value >= 100_000_000L -> String.format(Locale.US, "%.1f亿", value / 100_000_000.0)
    value >= 10_000L -> String.format(Locale.US, "%.1f万", value / 10_000.0)
    else -> value.toString()
}

private fun Aweme.cardTitle(): String = desc().ifBlank { FeedPageSpec.TEXT_UNTITLED_VIDEO }

/** 「已看」角标文案：来源是本地环形缓冲（ViewedVideoTracker），不是服务端历史。 */
private const val TEXT_VIEWED = "已看"

private fun Aweme.cardAuthor(): String =
    author().nickname().takeIf { it.isNotBlank() }?.let { "@$it" } ?: "未知作者"

private fun Aweme.cardMeta(): String {
    val stat = statistics()
    return listOf(
        "点赞 ${formatCount(stat.diggCount())}",
        "评论 ${formatCount(stat.commentCount())}",
        "收藏 ${formatCount(stat.collectCount())}",
    ).joinToString(FeedPageSpec.TEXT_META_SEPARATOR)
}

private fun Aweme.durationLabel(): String {
    val totalSeconds = (video().duration().toLong() / 1000L).coerceAtLeast(0L)
    return String.format(Locale.US, "%02d:%02d", totalSeconds / 60L, totalSeconds % 60L)
}

@Composable
private fun FeedStatePanel(
    title: String,
    detail: String,
    actionLabel: String? = null,
    actionRequester: FocusRequester? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AxTvColors.Surface, RoundedCornerShape(12.dp))
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = title,
            color = AxTvColors.TextPrimary,
            style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
        )
        Text(
            text = detail,
            color = AxTvColors.TextSecondary,
            style = TextStyle(fontSize = 13.sp),
        )
        if (actionLabel != null && onAction != null) {
            var focused by remember { mutableStateOf(false) }
            Box(
                modifier = (if (actionRequester != null) Modifier.focusRequester(actionRequester) else Modifier)
                    .onFocusChanged { focused = it.isFocused }
                    .clickable(onClick = onAction)
                    .background(AxTvColors.SurfaceHigh, RoundedCornerShape(8.dp))
                    .focusRing(focused, RoundedCornerShape(8.dp))
                    .padding(horizontal = 18.dp, vertical = 10.dp),
            ) {
                Text(
                    text = actionLabel,
                    color = if (focused) AxTvColors.TextPrimary else AxTvColors.TextSecondary,
                    style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                )
            }
        }
    }
}

@Composable
private fun HomeTabChip(label: String, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .background(
                color = if (selected) AxTvColors.Accent.copy(alpha = 0.16f) else AxTvColors.Surface,
                shape = RoundedCornerShape(8.dp),
            )
            .focusRing(focused, RoundedCornerShape(8.dp))
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            color = if (selected || focused) AxTvColors.TextPrimary else AxTvColors.TextSecondary,
            style = TextStyle(fontSize = 14.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal),
        )
    }
}

@Composable
private fun RelatedCard(item: Aweme, viewed: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .width(240.dp)
            .height(150.dp)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .background(AxTvColors.Surface, RoundedCornerShape(12.dp))
            .focusRing(focused, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(AxTvColors.SurfaceHigh, RoundedCornerShape(8.dp)),
        )
        Text(
            text = item.cardTitle(),
            color = AxTvColors.TextPrimary,
            style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
        )
        Text(
            text = "${item.cardAuthor()} · ${item.durationLabel()}",
            color = AxTvColors.TextSecondary,
            style = TextStyle(fontSize = 12.sp),
        )
        if (viewed) ViewedBadge()
    }
}

@Composable
private fun FeedRow(
    item: Aweme,
    viewed: Boolean,
    requester: FocusRequester,
    onFocused: () -> Unit,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(requester)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .clickable(onClick = onClick)
            .background(AxTvColors.Surface, RoundedCornerShape(12.dp))
            .focusRing(focused, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier
                .width(160.dp)
                .height(90.dp)
                .background(AxTvColors.SurfaceHigh, RoundedCornerShape(8.dp)),
        )
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = item.cardTitle(),
                color = AxTvColors.TextPrimary,
                style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
            )
            Text(
                text = "${item.cardAuthor()} · ${item.durationLabel()}",
                color = AxTvColors.TextSecondary,
                style = TextStyle(fontSize = 13.sp),
            )
            Text(
                text = item.cardMeta(),
                color = AxTvColors.TextSecondary,
                style = TextStyle(fontSize = 12.sp),
            )
            if (viewed) ViewedBadge()
        }
    }
}

/** 已看角标：小胶囊，取自本地 viewed_video_ids 清单。 */
@Composable
private fun ViewedBadge() {
    Box(
        modifier = Modifier
            .background(AxTvColors.SurfaceHigh, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(
            text = TEXT_VIEWED,
            color = AxTvColors.TextSecondary,
            style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
        )
    }
}

@Composable
private fun FeedFooter(
    loadingMore: Boolean,
    error: String?,
    hasMore: Boolean,
    loadedCount: Int,
    onLoadMore: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    when {
        loadingMore -> Text(
            text = "加载更多中…",
            color = AxTvColors.TextSecondary,
            style = TextStyle(fontSize = 13.sp),
        )

        error != null -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "加载更多失败：$error",
                color = AxTvColors.AccentWarm,
                style = TextStyle(fontSize = 13.sp),
            )
            Box(
                modifier = Modifier
                    .onFocusChanged { focused = it.isFocused }
                    .clickable(onClick = onLoadMore)
                    .background(AxTvColors.Surface, RoundedCornerShape(8.dp))
                    .focusRing(focused, RoundedCornerShape(8.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(
                    text = "重试",
                    color = AxTvColors.TextPrimary,
                    style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                )
            }
        }

        hasMore -> Box(
            modifier = Modifier
                .onFocusChanged { focused = it.isFocused }
                .clickable(onClick = onLoadMore)
                .background(AxTvColors.Surface, RoundedCornerShape(8.dp))
                .focusRing(focused, RoundedCornerShape(8.dp))
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(
                text = FeedPageSpec.TEXT_LOAD_MORE,
                color = AxTvColors.TextPrimary,
                style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
            )
        }

        else -> Text(
            text = "已加载全部 $loadedCount 条",
            color = AxTvColors.TextSecondary,
            style = TextStyle(fontSize = 13.sp),
        )
    }
}
