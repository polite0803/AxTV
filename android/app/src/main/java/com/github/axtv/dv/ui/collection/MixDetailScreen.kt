/*
 * 线 F：合集 / 系列详情页。
 *
 * 固定签名（供导航线分发 widget 路由 mix/detail?id=）：
 *   MixDetailScreen(mixId, onBack, onPlay)
 * 播放回调参数 = (canonical mix/series id, awemeId)。
 *
 * 行为规格出处 refactor/ui/axtv/ui/EpisodePickerSpec.java：
 *   已看过弱化（DIM_ALPHA=0.6）、上次看到高亮、继续第 N 集、未命名合集、帮助文案。
 */
package com.github.axtv.dv.ui.collection

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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

/** 每行集数（TV 横屏，5 列可保证 1080p 下单元可读）。 */
private const val EPISODE_COLUMNS = 5

/** 提前多少项触发自动续页（滚到末尾前 N 项就开始拉下一批剧集）。 */
private const val AUTO_LOAD_AHEAD = 3

private sealed interface DetailUiState {
    data object Loading : DetailUiState
    data class Failed(val message: String) : DetailUiState
    data class Ready(
        val detail: CollectionDetail,
        val loadingMore: Boolean = false,
        val moreError: String? = null,
    ) : DetailUiState
}

@OptIn(ExperimentalTvFoundationApi::class)
@Composable
fun MixDetailScreen(
    mixId: String,
    onBack: () -> Unit,
    onPlay: (mixId: String, awemeId: String) -> Unit,
) {
    var reloadToken by remember { mutableIntStateOf(0) }
    var state by remember { mutableStateOf<DetailUiState>(DetailUiState.Loading) }

    BackHandler(onBack = onBack)

    LaunchedEffect(mixId, reloadToken) {
        state = DetailUiState.Loading
        state = try {
            DetailUiState.Ready(CollectionRepository.loadDetail(mixId))
        } catch (t: Throwable) {
            DetailUiState.Failed(t.collectionMessage())
        }
    }

    when (val current = state) {
        DetailUiState.Loading -> DetailStatusPane(
            title = "正在加载合集…",
            message = null,
            onBack = onBack,
        )

        is DetailUiState.Failed -> DetailStatusPane(
            title = "合集加载失败",
            message = current.message,
            onRetry = { reloadToken++ },
            onBack = onBack,
        )

        is DetailUiState.Ready -> {
            val scope = rememberCoroutineScope()
            var syncNote by remember { mutableStateOf<String?>(null) }
            // 与原实现一致：同一集只写一次追更记录，避免重复条目（n11 的 item_id 去重集合）。
            val recordedAwemes = remember { mutableSetOf<String>() }

            fun play(detail: CollectionDetail, episode: CollectionEpisode) {
                // 追更记录只在 mix 有对应端点；series 无 series/watch/record，不写。
                if (detail.source == CollectionSource.MIX && recordedAwemes.add(episode.awemeId)) {
                    scope.launch {
                        try {
                            CollectionRepository.recordProgress(
                                mixId = detail.canonicalId,
                                awemeId = episode.awemeId,
                                episode = episode.number,
                            )
                        } catch (t: Throwable) {
                            syncNote = "进度同步失败：${t.collectionMessage()}"
                        }
                    }
                }
                onPlay(detail.canonicalId, episode.awemeId)
            }

            fun loadMore(ready: DetailUiState.Ready) {
                if (ready.loadingMore || !ready.detail.hasMore) return
                state = ready.copy(loadingMore = true, moreError = null)
                val detail = ready.detail
                scope.launch {
                    state = try {
                        val page = CollectionRepository.loadMoreEpisodes(detail)
                        DetailUiState.Ready(
                            detail.copy(
                                episodes = detail.episodes + page.episodes,
                                hasMore = page.hasMore,
                                cursor = page.cursor,
                            ),
                        )
                    } catch (t: Throwable) {
                        ready.copy(loadingMore = false, moreError = t.collectionMessage())
                    }
                }
            }

            DetailContent(
                detail = current.detail,
                loadingMore = current.loadingMore,
                moreError = current.moreError,
                syncNote = syncNote,
                onBack = onBack,
                onRetry = { reloadToken++ },
                onPlay = { episode -> play(current.detail, episode) },
                onLoadMore = { loadMore(current) },
            )
        }
    }
}

@OptIn(ExperimentalTvFoundationApi::class)
@Composable
private fun DetailContent(
    detail: CollectionDetail,
    loadingMore: Boolean,
    moreError: String?,
    syncNote: String?,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onPlay: (CollectionEpisode) -> Unit,
    onLoadMore: () -> Unit,
) {
    val listState = rememberLazyListState()
    val requesters = remember { mutableMapOf<String, FocusRequester>() }
    val emptyRetryRequester = remember { FocusRequester() }
    val episodes = detail.episodes
    val rows = episodes.chunked(EPISODE_COLUMNS)
    val resumeEpisode = detail.currentEpisode.takeIf { it in 1..episodes.size } ?: 0
    val targetEpisode = episodes.getOrNull(if (resumeEpisode > 0) resumeEpisode - 1 else 0)

    // 电视端没有上滑手势：滚到接近末尾就自动续页，不再需要先发现并按下「加载更多剧集」按钮。
    val scrolledNearEnd by remember(listState) {
        derivedStateOf {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            info.totalItemsCount > 0 && lastVisible >= info.totalItemsCount - AUTO_LOAD_AHEAD
        }
    }
    LaunchedEffect(scrolledNearEnd, detail.hasMore, loadingMore, moreError) {
        // 失败后不再自动重试，留「重试」按钮给用户决定。
        if (scrolledNearEnd && detail.hasMore && !loadingMore && moreError == null) onLoadMore()
    }

    // 进入时定位到上次播放的集：先滚到对应行，下一帧再请焦点（lazy 行未组合时 requester 尚未注册）。
    LaunchedEffect(detail.canonicalId, targetEpisode?.awemeId) {
        val target = targetEpisode ?: return@LaunchedEffect
        val rowIndex = ((target.number - 1) / EPISODE_COLUMNS) + 1 // +1 跳过头部 item
        runCatching { listState.scrollToItem(rowIndex) }
        withFrameNanos { }
        runCatching { requesters[target.awemeId]?.requestFocus() }
    }

    // 空合集没有剧集可落焦，焦点给「重试」，避免 TV 焦点悬空。
    LaunchedEffect(episodes.isEmpty()) {
        if (episodes.isEmpty()) {
            withFrameNanos { }
            runCatching { emptyRetryRequester.requestFocus() }
        }
    }

    val meta = EpisodePickerSpec.metaLine(
        EpisodePickerSpec.MixMeta(
            detail.totalEpisode,
            detail.updatedToEpisode,
            detail.currentEpisode,
            null,
            detail.updatedToEpisode.takeIf { it > 0 }?.let { "第 $it 集" },
            null,
            detail.currentEpisode.takeIf { it > 0 },
        ),
    )

    LazyColumn(
        state = listState,
        // 焦点分组：方向键先在列表内移动，不往顶栏跳。
        modifier = Modifier.fillMaxSize().background(AxTvColors.Bg).focusGroup(),
        contentPadding = PaddingValues(horizontal = 40.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    CollectionPill(
                        label = "‹ 返回",
                        accent = false,
                        onClick = onBack,
                    )
                    CollectionPill(
                        label = if (detail.source == CollectionSource.MIX) "合集" else "系列",
                        accent = true,
                        onClick = null,
                    )
                    Text(
                        text = EpisodePickerSpec.mixName(detail.name),
                        color = AxTvColors.TextPrimary,
                        style = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
                Text(
                    text = if (detail.author.isBlank()) "作者信息暂不可用" else "@${detail.author}",
                    color = AxTvColors.TextSecondary,
                    style = TextStyle(fontSize = 14.sp),
                )
                Text(
                    text = meta,
                    color = AxTvColors.TextSecondary,
                    style = TextStyle(fontSize = 13.sp),
                )
                if (detail.desc.isNotBlank()) {
                    Text(
                        text = detail.desc,
                        color = AxTvColors.TextSecondary,
                        style = TextStyle(fontSize = 13.sp),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val primary = episodes.getOrNull(if (resumeEpisode > 0) resumeEpisode - 1 else 0)
                    CollectionPill(
                        label = EpisodePickerSpec.primaryButtonLabel(
                            if (resumeEpisode > 0) resumeEpisode else null,
                            1,
                        ),
                        accent = true,
                        onClick = if (primary != null) {
                            { onPlay(primary) }
                        } else {
                            null
                        },
                    )
                    if (resumeEpisode > 0) {
                        val firstEpisode = episodes.firstOrNull()
                        CollectionPill(
                            label = "从第 1 集播放",
                            accent = false,
                            onClick = if (firstEpisode != null) {
                                { onPlay(firstEpisode) }
                            } else {
                                null
                            },
                        )
                    }
                }
                Text(
                    text = EpisodePickerSpec.panelTitle(if (detail.total > 0) detail.total else episodes.size),
                    color = AxTvColors.TextPrimary,
                    style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.padding(top = 8.dp),
                )
                if (episodes.isNotEmpty()) {
                    Text(
                        text = EpisodePickerSpec.loadHint(1, episodes.size, detail.total),
                        color = AxTvColors.TextSecondary,
                        style = TextStyle(fontSize = 12.sp),
                    )
                }
            }
        }

        if (episodes.isEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        text = "该合集暂无可播放剧集",
                        color = AxTvColors.TextSecondary,
                        style = TextStyle(fontSize = 15.sp),
                    )
                    CollectionPill(
                        label = "重试",
                        accent = true,
                        onClick = onRetry,
                        modifier = Modifier.focusRequester(emptyRetryRequester),
                    )
                }
            }
        } else {
            items(count = rows.size, key = { "episode-row-$it" }) { rowIndex ->
                Row(
                    modifier = Modifier.fillMaxWidth().focusGroup(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    rows[rowIndex].forEach { episode ->
                        EpisodeCell(
                            episode = episode,
                            isCurrent = episode.number == resumeEpisode,
                            isWatched = resumeEpisode > 0 && episode.number < resumeEpisode,
                            modifier = Modifier.weight(1f),
                            // 带 key：追加剧集后行内槽位复用不会把请求器绑到别的集上。
                            focusRequester = remember(episode.awemeId) { FocusRequester() }
                                .also { requesters[episode.awemeId] = it },
                            onPlay = { onPlay(episode) },
                        )
                    }
                    // 末行不足 5 列时补空位，保持单元格等宽。
                    repeat(EPISODE_COLUMNS - rows[rowIndex].size) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }

        if (detail.hasMore || moreError != null) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    when {
                        loadingMore -> Text(
                            text = "正在加载更多剧集…",
                            color = AxTvColors.TextSecondary,
                            style = TextStyle(fontSize = 13.sp),
                        )

                        moreError != null -> {
                            Text(
                                text = "加载更多失败：$moreError",
                                color = AxTvColors.AccentWarm,
                                style = TextStyle(fontSize = 13.sp),
                            )
                            CollectionPill(label = "重试", accent = true, onClick = onLoadMore)
                        }

                        else -> Text(
                            // 续页由上面的 LaunchedEffect 在滚到末尾前自动发生；原先是可点按钮。
                            text = "继续下移自动加载（已加载 ${episodes.size} 集）",
                            color = AxTvColors.TextSecondary,
                            style = TextStyle(fontSize = 13.sp),
                        )
                    }
                }
            }
        }

        if (syncNote != null) {
            item {
                Text(
                    text = syncNote,
                    color = AxTvColors.AccentWarm,
                    style = TextStyle(fontSize = 12.sp),
                )
            }
        }

        item {
            Text(
                text = EpisodePickerSpec.HELP_TEXT,
                color = AxTvColors.TextSecondary,
                style = TextStyle(fontSize = 12.sp),
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun EpisodeCell(
    episode: CollectionEpisode,
    isCurrent: Boolean,
    isWatched: Boolean,
    focusRequester: FocusRequester,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier = modifier
            .height(104.dp)
            .focusRequester(focusRequester)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onPlay)
            .background(
                color = if (isCurrent) AxTvColors.Accent.copy(alpha = 0.16f) else AxTvColors.Surface,
                shape = RoundedCornerShape(10.dp),
            )
            .focusRing(focused, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "第 ${episode.number} 集",
                color = if (isCurrent) AxTvColors.Accent else AxTvColors.TextPrimary,
                style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
            )
            val duration = formatDuration(episode.durationMs)
            if (isCurrent) {
                Text(
                    text = "上次看到",
                    color = AxTvColors.Accent,
                    style = TextStyle(fontSize = 10.sp),
                )
            } else if (duration.isNotEmpty()) {
                Text(
                    text = duration,
                    color = AxTvColors.TextSecondary,
                    style = TextStyle(fontSize = 10.sp),
                )
            }
        }
        Text(
            text = episode.title,
            color = if (isWatched) {
                AxTvColors.TextSecondary.copy(alpha = EpisodePickerSpec.DIM_ALPHA)
            } else {
                AxTvColors.TextPrimary
            },
            style = TextStyle(fontSize = 12.sp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 可聚焦胶囊按钮；[onClick] 为 null 时只做静态标签。 */
@Composable
private fun CollectionPill(
    label: String,
    accent: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .background(
                color = if (accent) AxTvColors.Accent.copy(alpha = 0.16f) else AxTvColors.Surface,
                shape = RoundedCornerShape(8.dp),
            )
            .focusRing(focused, RoundedCornerShape(8.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            color = if (accent) AxTvColors.Accent else AxTvColors.TextPrimary,
            style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
        )
    }
}

@Composable
private fun DetailStatusPane(
    title: String,
    message: String?,
    onRetry: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
) {
    val retryRequester = remember { FocusRequester() }
    val backRequester = remember { FocusRequester() }
    LaunchedEffect(title, message) {
        withFrameNanos { }
        val target = when {
            onRetry != null -> retryRequester
            onBack != null -> backRequester
            else -> null
        }
        if (target != null) runCatching { target.requestFocus() }
    }
    Column(
        modifier = Modifier.fillMaxSize().background(AxTvColors.Bg).padding(40.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            color = AxTvColors.TextPrimary,
            style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold),
        )
        if (message != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = message,
                color = AxTvColors.TextSecondary,
                style = TextStyle(fontSize = 14.sp),
            )
        }
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (onRetry != null) {
                CollectionPill(
                    label = "重试",
                    accent = true,
                    onClick = onRetry,
                    modifier = Modifier.focusRequester(retryRequester),
                )
            }
            if (onBack != null) {
                CollectionPill(
                    label = "返回",
                    accent = false,
                    onClick = onBack,
                    modifier = Modifier.focusRequester(backRequester),
                )
            }
        }
    }
}

private fun formatDuration(durationMs: Long): String {
    if (durationMs <= 0L) return ""
    val totalSeconds = durationMs / 1000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}
