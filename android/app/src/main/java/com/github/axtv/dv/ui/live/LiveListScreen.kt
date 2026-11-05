/*
 * 线 C —— 直播入口页（列表）。
 *
 * 规格出处：
 * - refactor/ui/axtv/ui/DetailPageSpec.java（直播间卡片、分区 chip、LIVE 红点、空态文案）
 * - refactor/ui/axtv/ui/LiveBrowseSpec.java（TEXT_LIVE_BADGE / TEXT_ANCHOR_FALLBACK / 网格列数）
 * - refactor/ui/axtv/ui/LivePageSpec.java（在线人数标签）
 *
 * 数据：axtv.api.LiveApi.feed / followFeed / partitionRooms（只读调用，经 LiveRepository）。
 * 结构：LazyColumn + 自适应列数的 Row 网格，焦点由 Compose 默认 D-pad 遍历；
 *      加载 / 失败（可重试）/ 空态三态齐全。
 */
package com.github.axtv.dv.ui.live

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.foundation.ExperimentalTvFoundationApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import axtv.ui.DetailPageSpec
import axtv.ui.LiveBrowseSpec
import com.github.axtv.dv.adapter.AppRuntime
import com.github.axtv.dv.ui.AxTvColors
import com.github.axtv.dv.ui.focusRing

/** 直播列表页固定签名（供导航接线）。 */
@OptIn(ExperimentalTvFoundationApi::class)
@Composable
fun LiveListScreen(
    onBack: () -> Unit,
    onOpenRoom: (roomId: String) -> Unit,
) {
    val branch = remember { AppRuntime.settings?.clientIdentityBranch() }
    var selectedTabName by rememberSaveable { mutableStateOf(LiveTab.FEED.name) }
    val tab = LiveTab.entries.firstOrNull { it.name == selectedTabName } ?: LiveTab.FEED

    var cards by remember { mutableStateOf<List<LiveCardUi>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var reloadToken by remember { mutableIntStateOf(0) }
    var focusRestored by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val retryRequester = remember { FocusRequester() }
    val firstCardRequester = remember { FocusRequester() }

    LaunchedEffect(tab, reloadToken) {
        loading = true
        error = null
        cards = emptyList()
        focusRestored = false
        LiveRepository.rooms(tab)
            .onSuccess { cards = it }
            .onFailure { error = describeLiveError(it, branch) }
        loading = false
    }

    // 数据到位后把焦点交给第一张卡片，保证遥控器进入页面即可操作。
    LaunchedEffect(cards.size) {
        if (!focusRestored && cards.isNotEmpty()) {
            runCatching { listState.scrollToItem(0) }
            withFrameNanos { }
            runCatching { firstCardRequester.requestFocus() }
            focusRestored = true
        }
    }

    // 失败 / 空态没有卡片可聚焦，把焦点交给「重试」，避免 D-pad 无处可去。
    LaunchedEffect(error, cards.size, loading) {
        if (!loading && cards.isEmpty()) {
            withFrameNanos { }
            runCatching { retryRequester.requestFocus() }
        }
    }

    BackHandler(onBack = onBack)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AxTvColors.Bg)
            .padding(horizontal = 40.dp, vertical = 20.dp),
    ) {
        Text(
            text = "直播",
            color = AxTvColors.TextPrimary,
            style = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold),
        )
        Text(
            text = "推荐 / 关注 / 分区 · 启动身份：${branch?.displayName ?: "未知"}",
            color = AxTvColors.TextSecondary,
            style = TextStyle(fontSize = 13.sp),
        )
        Spacer(Modifier.height(14.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LiveTab.entries.forEach { item ->
                LiveTabChip(
                    label = item.label,
                    selected = item == tab,
                    onClick = { selectedTabName = item.name },
                )
            }
        }
        Spacer(Modifier.height(16.dp))

        when {
            loading -> LiveStatePanel(
                title = "加载中…",
                detail = "正在请求直播列表（${tab.label}）",
            )

            error != null -> LiveStatePanel(
                title = "加载失败",
                detail = error.orEmpty(),
                actionLabel = "重试",
                actionRequester = retryRequester,
                onAction = { reloadToken += 1 },
            )

            cards.isEmpty() -> LiveStatePanel(
                title = DetailPageSpec.TEXT_GRID_EMPTY,
                detail = "「${tab.label}」下暂时没有正在直播的房间",
                actionLabel = "刷新",
                actionRequester = retryRequester,
                onAction = { reloadToken += 1 },
            )

            else -> LiveRoomGrid(
                cards = cards,
                listState = listState,
                firstCardRequester = firstCardRequester,
                modifier = Modifier.weight(1f),
                onOpenRoom = onOpenRoom,
            )
        }
    }
}

@OptIn(ExperimentalTvFoundationApi::class)
@Composable
private fun LiveRoomGrid(
    cards: List<LiveCardUi>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    firstCardRequester: FocusRequester,
    modifier: Modifier = Modifier,
    onOpenRoom: (String) -> Unit,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        // 列数随可用宽度变化：320dp 起一列，最多 4 列（TV 常见 960dp / 1280dp 分别落 3 / 4 列）。
        val columns = (maxWidth.value / 300f).toInt().coerceIn(2, 4)
        val rows = cards.chunked(columns)
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(count = rows.size, key = { "live-row-$it" }) { rowIndex ->
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    rows[rowIndex].forEachIndexed { colIndex, card ->
                        LiveRoomCard(
                            card = card,
                            modifier = Modifier.weight(1f),
                            requester = if (rowIndex == 0 && colIndex == 0) firstCardRequester else null,
                            onClick = { onOpenRoom(card.roomId) },
                        )
                    }
                    repeat(columns - rows[rowIndex].size) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveRoomCard(
    card: LiveCardUi,
    modifier: Modifier = Modifier,
    requester: FocusRequester? = null,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier = (if (requester != null) Modifier.focusRequester(requester) else Modifier)
            .then(modifier)
            .height(214.dp)
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(12.dp))
            .background(AxTvColors.Surface)
            .focusRing(focused, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(148.dp)
                .background(AxTvColors.SurfaceHigh),
        ) {
            if (card.coverUrl.isNotEmpty()) {
                AsyncImage(
                    model = card.coverUrl,
                    contentDescription = card.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            // 底部渐变保证白字在任何封面上都能读。
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.72f),
                        ),
                    ),
            )
            LiveBadge(
                isLive = card.isLive,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp),
            )
            Text(
                text = DetailPageSpec.viewerLine(false, card.viewerCount),
                color = AxTvColors.TextPrimary,
                style = TextStyle(fontSize = 12.sp),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            )
        }
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (card.avatarUrl.isNotEmpty()) {
                AsyncImage(
                    model = card.avatarUrl,
                    contentDescription = card.ownerName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape),
                )
            }
            Column {
                Text(
                    text = card.title,
                    color = AxTvColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                )
                Text(
                    text = card.ownerName,
                    color = AxTvColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(fontSize = 12.sp),
                )
            }
        }
    }
}

@Composable
private fun LiveBadge(isLive: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (isLive) AxTvColors.AccentWarm else AxTvColors.SurfaceHigh)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (isLive) {
            Box(
                modifier = Modifier
                    .size(DetailPageSpec.LIVE_DOT_SIZE_DP.dp)
                    .clip(CircleShape)
                    .background(Color.White),
            )
        }
        Text(
            text = if (isLive) LiveBrowseSpec.TEXT_LIVE_BADGE else "未开播",
            color = Color.White,
            style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold),
        )
    }
}

@Composable
private fun LiveTabChip(label: String, selected: Boolean, onClick: () -> Unit) {
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
            style = TextStyle(
                fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            ),
        )
    }
}

@Composable
private fun LiveStatePanel(
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
