/*
 * 样板页 1：首页信息流（结构 + 焦点 + 占位内容）。
 * 规格出处：refactor/ui/NOTES.md §信息流（i42 容器 / on5 视频页）、
 *           refactor/ui/axtv/ui/FeedPageSpec.java、HomeTabsSpec.java。
 * 目标栈：TvLazyRow（相关推荐）+ TvLazyColumn（主信息流），焦点索引跨页记忆。
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.foundation.ExperimentalTvFoundationApi
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.rememberTvLazyListState
import androidx.tv.material3.Text
import axtv.ui.FeedPageSpec
import axtv.ui.HomeTabsSpec
import com.github.axtv.dv.ui.AxTvColors
import com.github.axtv.dv.ui.focusRing

private data class FeedCard(
    val id: String,
    val title: String,
    val author: String,
    val meta: String,
)

// 占位数据：真实内容由核心层 FeedRepository 注入，骨架阶段只验证结构与焦点。
private val RELATED = listOf(
    FeedCard("r1", "第 1 集 · 相关推荐", "@示例作者", "合集 · 12 集"),
    FeedCard("r2", "第 2 集 · 相关推荐", "@示例作者", "合集 · 12 集"),
    FeedCard("r3", "第 3 集 · 相关推荐", "@示例作者", "合集 · 12 集"),
    FeedCard("r4", "第 4 集 · 相关推荐", "@示例作者", "合集 · 12 集"),
    FeedCard("r5", "第 5 集 · 相关推荐", "@示例作者", "合集 · 12 集"),
)

private val FEED = (1..10).map { index ->
    FeedCard(
        id = "feed_$index",
        title = "${FeedPageSpec.TEXT_UNTITLED_VIDEO} · 第 $index 条",
        author = "@示例作者 $index",
        meta = "点赞 1.2w · 评论 345 · 收藏 678",
    )
}

@OptIn(ExperimentalTvFoundationApi::class)
@Composable
fun HomeFeedScreen(
    rememberedFocusIndex: Int,
    onFocusIndexChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var activeTab by rememberSaveable { mutableStateOf(HomeTabsSpec.TAB_RECOMMEND) }
    val listState = rememberTvLazyListState()
    val requesters = remember { mutableMapOf<Int, FocusRequester>() }

    // 焦点记忆：重新进入本页时滚到上次条目，下一帧再请求焦点。
    LaunchedEffect(Unit) {
        val target = rememberedFocusIndex.coerceIn(0, FEED.size - 1)
        runCatching { listState.scrollToItem(target) }
        withFrameNanos { }
        runCatching { requesters[target]?.requestFocus() }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 40.dp, vertical = 20.dp),
    ) {
        Text(
            text = "首页信息流",
            color = AxTvColors.TextPrimary,
            style = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold),
        )
        Text(
            text = "TvLazyRow + TvLazyColumn · 焦点记忆恢复第 ${rememberedFocusIndex + 1} 项" +
                " · 规格 refactor/ui/NOTES.md / FeedPageSpec.java",
            color = AxTvColors.TextSecondary,
            style = TextStyle(fontSize = 13.sp),
        )
        Spacer(Modifier.height(14.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                HomeTabsSpec.TAB_RECOMMEND,
                HomeTabsSpec.TAB_FOLLOW,
                HomeTabsSpec.TAB_FEATURED,
                HomeTabsSpec.TAB_MOBILE,
            ).forEach { tab ->
                HomeTabChip(label = tab, selected = tab == activeTab, onClick = { activeTab = tab })
            }
        }
        Spacer(Modifier.height(16.dp))

        Text(
            text = FeedPageSpec.TEXT_RELATED,
            color = AxTvColors.TextPrimary,
            style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
        )
        TvLazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(count = RELATED.size) { index ->
                RelatedCard(item = RELATED[index])
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = "推荐信息流",
            color = AxTvColors.TextPrimary,
            style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
        )
        TvLazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(count = FEED.size, key = { index -> FEED[index].id }) { index ->
                val requester = remember { FocusRequester() }
                SideEffect { requesters[index] = requester }
                FeedRow(
                    item = FEED[index],
                    requester = requester,
                    onFocused = { onFocusIndexChange(index) },
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
private fun RelatedCard(item: FeedCard) {
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .width(240.dp)
            .height(150.dp)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = {})
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
            text = item.title,
            color = AxTvColors.TextPrimary,
            style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
        )
        Text(
            text = "${item.author} · ${item.meta}",
            color = AxTvColors.TextSecondary,
            style = TextStyle(fontSize = 12.sp),
        )
    }
}

@Composable
private fun FeedRow(item: FeedCard, requester: FocusRequester, onFocused: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(requester)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .clickable(onClick = {})
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
                text = item.title,
                color = AxTvColors.TextPrimary,
                style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
            )
            Text(
                text = item.author,
                color = AxTvColors.TextSecondary,
                style = TextStyle(fontSize = 13.sp),
            )
            Text(
                text = item.meta,
                color = AxTvColors.TextSecondary,
                style = TextStyle(fontSize = 12.sp),
            )
        }
    }
}
