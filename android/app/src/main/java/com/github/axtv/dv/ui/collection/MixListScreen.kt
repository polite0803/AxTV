/*
 * 线 F：合集 / 系列入口列表。
 *
 * 固定签名（供导航线分发）：
 *   MixListScreen(onBack, onOpenMix)
 *
 * 两个来源：
 *   - 收藏合集 → CollectionApi.mixListCollection(cursor, count)
 *   - 我的系列 → CollectionApi.seriesList(secUserId, cursor, count)，
 *     secUserId 取当前登录账号 profileSelf().user().secUid（系列列表接口必填）。
 */
package com.github.axtv.dv.ui.collection

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.foundation.ExperimentalTvFoundationApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.tv.material3.Text
import axtv.ui.EpisodePickerSpec
import com.github.axtv.dv.ui.AxTvColors
import com.github.axtv.dv.ui.focusRing
import kotlinx.coroutines.launch

private enum class ListTab(val label: String) {
    MIX("收藏合集"),
    SERIES("我的系列"),
}

private sealed interface ListUiState {
    data object Loading : ListUiState
    data class Failed(val message: String) : ListUiState
    data class Loaded(
        val items: List<CollectionSummary>,
        val hasMore: Boolean,
        val cursor: Long,
        val loadingMore: Boolean = false,
        val moreError: String? = null,
    ) : ListUiState
}

@OptIn(ExperimentalTvFoundationApi::class)
@Composable
fun MixListScreen(
    onBack: () -> Unit,
    onOpenMix: (String) -> Unit,
) {
    var selectedTabName by rememberSaveable { mutableStateOf(ListTab.MIX.name) }
    val tab = ListTab.entries.firstOrNull { it.name == selectedTabName } ?: ListTab.MIX
    var mixState by remember { mutableStateOf<ListUiState>(ListUiState.Loading) }
    var seriesState by remember { mutableStateOf<ListUiState>(ListUiState.Loading) }
    var mixReloadToken by remember { mutableIntStateOf(0) }
    var seriesReloadToken by remember { mutableIntStateOf(0) }
    // 进程恢复时如果页签停在「我的系列」，需要视为已发起过系列加载。
    var seriesStarted by remember { mutableStateOf(selectedTabName == ListTab.SERIES.name) }

    val scope = rememberCoroutineScope()
    val mixListState = rememberLazyListState()
    val seriesListState = rememberLazyListState()
    val listState = if (tab == ListTab.MIX) mixListState else seriesListState
    val requesters = remember { mutableMapOf<String, FocusRequester>() }
    val backRequester = remember { FocusRequester() }
    val statusActionRequester = remember { FocusRequester() }

    BackHandler(onBack = onBack)

    LaunchedEffect(mixReloadToken) {
        mixState = ListUiState.Loading
        mixState = try {
            val page = CollectionRepository.loadMixList(cursor = 0L)
            ListUiState.Loaded(page.items, page.hasMore, page.cursor)
        } catch (t: Throwable) {
            ListUiState.Failed(t.collectionMessage())
        }
    }

    // 首次切到「我的系列」才发请求；切换页签不会重复拉取，重试由 seriesReloadToken 触发。
    LaunchedEffect(seriesReloadToken) {
        if (!seriesStarted) return@LaunchedEffect
        seriesState = ListUiState.Loading
        seriesState = try {
            val page = CollectionRepository.loadMySeriesList(cursor = 0L)
            ListUiState.Loaded(page.items, page.hasMore, page.cursor)
        } catch (t: Throwable) {
            ListUiState.Failed(t.collectionMessage())
        }
    }

    fun selectTab(next: ListTab) {
        selectedTabName = next.name
        if (next == ListTab.SERIES && !seriesStarted) {
            seriesStarted = true
            seriesReloadToken++
        }
    }

    fun loadMore(loaded: ListUiState.Loaded, source: ListTab) {
        if (loaded.loadingMore || !loaded.hasMore) return
        val currentTab = source
        val stateBefore = loaded
        val setState: (ListUiState) -> Unit = if (currentTab == ListTab.MIX) {
            { mixState = it }
        } else {
            { seriesState = it }
        }
        setState(stateBefore.copy(loadingMore = true, moreError = null))
        scope.launch {
            setState(
                try {
                    val page = if (currentTab == ListTab.MIX) {
                        CollectionRepository.loadMixList(stateBefore.cursor)
                    } else {
                        CollectionRepository.loadMySeriesList(stateBefore.cursor)
                    }
                    ListUiState.Loaded(
                        items = stateBefore.items + page.items,
                        hasMore = page.hasMore,
                        cursor = page.cursor,
                    )
                } catch (t: Throwable) {
                    stateBefore.copy(loadingMore = false, moreError = t.collectionMessage())
                },
            )
        }
    }

    val state = if (tab == ListTab.MIX) mixState else seriesState
    val firstItemId = (state as? ListUiState.Loaded)?.items?.firstOrNull()?.let { "${tab.name}:${it.id}" }

    // 列表首项落焦：仅在切页签或首屏数据到达时请求，加载更多不抢焦点。
    LaunchedEffect(tab, firstItemId) {
        val id = firstItemId ?: return@LaunchedEffect
        withFrameNanos { }
        runCatching { requesters[id]?.requestFocus() }
    }

    // 无可聚焦列表项时（加载中 / 失败 / 空态）给出确定焦点，避免 TV 上焦点悬空。
    val phase = when (val s = state) {
        ListUiState.Loading -> 0
        is ListUiState.Failed -> 1
        is ListUiState.Loaded -> if (s.items.isEmpty()) 2 else 3
    }
    LaunchedEffect(tab, phase) {
        if (phase == 3) return@LaunchedEffect
        withFrameNanos { }
        val target = if (phase == 0) backRequester else statusActionRequester
        runCatching { target.requestFocus() }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().background(AxTvColors.Bg),
        contentPadding = PaddingValues(horizontal = 40.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ListTabChip(
                        label = "‹ 返回",
                        selected = false,
                        onClick = onBack,
                        modifier = Modifier.focusRequester(backRequester),
                    )
                    Text(
                        text = "合集追更",
                        color = AxTvColors.TextPrimary,
                        style = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold),
                        modifier = Modifier.weight(1f),
                    )
                }
                Text(
                    text = "收藏的合集与我的系列，进入后可从上次看到的一集继续。",
                    color = AxTvColors.TextSecondary,
                    style = TextStyle(fontSize = 13.sp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ListTabChip(
                        label = ListTab.MIX.label,
                        selected = tab == ListTab.MIX,
                        onClick = { selectTab(ListTab.MIX) },
                    )
                    ListTabChip(
                        label = ListTab.SERIES.label,
                        selected = tab == ListTab.SERIES,
                        onClick = { selectTab(ListTab.SERIES) },
                    )
                }
                Spacer(Modifier.height(4.dp))
            }
        }

        when (val current = state) {
            ListUiState.Loading -> item {
                Text(
                    text = "正在加载…",
                    color = AxTvColors.TextSecondary,
                    style = TextStyle(fontSize = 14.sp),
                    modifier = Modifier.padding(vertical = 20.dp),
                )
            }

            is ListUiState.Failed -> item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        text = current.message,
                        color = AxTvColors.AccentWarm,
                        style = TextStyle(fontSize = 14.sp),
                    )
                    ListTabChip(
                        label = "重试",
                        selected = true,
                        onClick = {
                            if (tab == ListTab.MIX) {
                                mixReloadToken++
                            } else {
                                seriesStarted = true
                                seriesReloadToken++
                            }
                        },
                        modifier = Modifier.focusRequester(statusActionRequester),
                    )
                }
            }

            is ListUiState.Loaded -> {
                if (current.items.isEmpty()) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Text(
                                text = if (tab == ListTab.MIX) {
                                    "还没有收藏的合集"
                                } else {
                                    "没有可显示的系列"
                                },
                                color = AxTvColors.TextSecondary,
                                style = TextStyle(fontSize = 14.sp),
                            )
                            ListTabChip(
                                label = "刷新",
                                selected = false,
                                onClick = {
                                    if (tab == ListTab.MIX) {
                                        mixReloadToken++
                                    } else {
                                        seriesReloadToken++
                                    }
                                },
                                modifier = Modifier.focusRequester(statusActionRequester),
                            )
                        }
                    }
                } else {
                    items(
                        count = current.items.size,
                        key = { index -> "${tab.name}:${current.items[index].id}" },
                    ) { index ->
                        val summary = current.items[index]
                        val focusKey = "${tab.name}:${summary.id}"
                        SummaryRow(
                            summary = summary,
                            focusRequester = remember { FocusRequester() }
                                .also { requesters[focusKey] = it },
                            onClick = { onOpenMix(summary.id) },
                        )
                    }
                }

                if (current.hasMore || current.moreError != null) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            when {
                                current.loadingMore -> Text(
                                    text = "正在加载更多…",
                                    color = AxTvColors.TextSecondary,
                                    style = TextStyle(fontSize = 13.sp),
                                )

                                current.moreError != null -> {
                                    Text(
                                        text = "加载更多失败：${current.moreError}",
                                        color = AxTvColors.AccentWarm,
                                        style = TextStyle(fontSize = 13.sp),
                                    )
                                    ListTabChip(
                                        label = "重试",
                                        selected = true,
                                        onClick = { loadMore(current, tab) },
                                    )
                                }

                                else -> ListTabChip(
                                    label = "加载更多（已显示 ${current.items.size} 个）",
                                    selected = false,
                                    onClick = { loadMore(current, tab) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryRow(
    summary: CollectionSummary,
    focusRequester: FocusRequester,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .background(AxTvColors.Surface, RoundedCornerShape(12.dp))
            .focusRing(focused, RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(AxTvColors.SurfaceHigh, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (summary.source == CollectionSource.MIX) "合" else "系",
                color = AxTvColors.Accent,
                style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold),
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = EpisodePickerSpec.mixName(summary.name),
                color = AxTvColors.TextPrimary,
                style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = progressText(summary),
                color = AxTvColors.TextSecondary,
                style = TextStyle(fontSize = 12.sp),
            )
            if (summary.desc.isNotBlank()) {
                Text(
                    text = summary.desc,
                    color = AxTvColors.TextSecondary,
                    style = TextStyle(fontSize = 12.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(
            text = if (summary.source == CollectionSource.MIX) "合集" else "系列",
            color = AxTvColors.Accent,
            style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
        )
    }
}

@Composable
private fun ListTabChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .background(
                color = if (selected) AxTvColors.Accent.copy(alpha = 0.18f) else AxTvColors.Surface,
                shape = RoundedCornerShape(8.dp),
            )
            .focusRing(focused, RoundedCornerShape(8.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            color = if (selected) AxTvColors.Accent else AxTvColors.TextPrimary,
            style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
        )
    }
}

private fun progressText(summary: CollectionSummary): String = when {
    summary.currentEpisode > 0 && summary.total > 0 -> "第 ${summary.currentEpisode} 集 / 共 ${summary.total} 集"
    summary.total > 0 -> "共 ${summary.total} 集"
    summary.currentEpisode > 0 -> "第 ${summary.currentEpisode} 集"
    else -> "集数未知"
}
