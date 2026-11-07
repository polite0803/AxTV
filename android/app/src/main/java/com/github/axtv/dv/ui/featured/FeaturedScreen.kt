/*
 * 精选频道页（绘制层）：行为规格全部来自 refactor/ui/axtv/ui/ChannelListSpec（qg5）。
 *
 * 结构对位：
 *   - 页头：AxTV + "精选频道"（qg5:3396/3398）
 *   - 分区 Tab：chip（m9530h；文本 Tab 形态 m9536k 未展开，见 ChannelListSpec.useTextTab）
 *   - 内容：m9520c 分区状态机（LOADING / ERROR / EMPTY / CONTENT）+ 视频网格（列数取 columnCount）
 *   - 页级空态：m9524e 三态（LOADING / NO_COOKIE / NO_VIDEOS）
 *   - 分页：UI 阈值 shouldRequestLoadMore（size-5），仓储复核在 FeaturedRepository 内
 *   - 帧内焦点：withFrameNanos + runCatching，不抢首帧
 */
package com.github.axtv.dv.ui.featured

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import axtv.model.Aweme
import axtv.ui.ChannelListSpec
import coil.compose.AsyncImage
import com.github.axtv.dv.adapter.AppRuntime
import com.github.axtv.dv.ui.AxTvColors
import com.github.axtv.dv.ui.FocusMemory
import com.github.axtv.dv.ui.collection.collectionMessage
import com.github.axtv.dv.ui.focusRing
import com.github.axtv.dv.ui.library.ActionButton
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** 本页固定列数：布局枚举 ww5 未移植，取 ordinal=2 对应值（ChannelListSpec.columnCount:205-213）。 */
private const val FEATURED_COLUMNS = 2

/** 连续这么多页没有新增作品就停止自动续页，避免服务端重复返回时无限请求。 */
private const val MAX_STALLED_PAGES = 3

private const val TEXT_PLACEHOLDER = "—"

/** 分区接口不可用时的显式标注：不能把首页推荐当精选内容展示。 */
private const val TEXT_DEGRADED = "分区内容接口不可用，当前显示的是首页推荐内容"

@Composable
fun FeaturedScreen(
    onOpenPlayer: (startIndex: Int) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDetail: (awemeId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var channels by remember { mutableStateOf(FeaturedRepository.cachedChannels) }
    var channelsLoaded by remember { mutableStateOf(channels.isNotEmpty()) }
    var pageError by remember { mutableStateOf<String?>(null) }
    var pageAttempt by remember { mutableIntStateOf(0) }
    var selectedTagId by rememberSaveable { mutableStateOf<String?>(null) }
    // 每次成功「换一批」+1：分区内容整屏替换后，网格必须回到第一条（见 SectionContent）。
    var refreshSeq by remember { mutableIntStateOf(0) }

    fun updateChannels(next: List<FeaturedChannel>) {
        channels = next
        FeaturedRepository.rememberChannels(next)
    }

    val visible = remember(channels) {
        channels.filter { it.videos.isNotEmpty() || it.loading }
    }
    val selectedIndex = remember(channels, selectedTagId) {
        channels.indexOfFirst { it.tagId == selectedTagId }.coerceAtLeast(0)
    }
    val selected = channels.getOrNull(selectedIndex)
    // 再点已选中分区要发起「换一批」请求，需要页级协程作用域（SectionContent 里那个只服务它自己）。
    val scope = rememberCoroutineScope()
    // 网页取数兜底需要 Activity Context（隐藏 WebView 要挂到窗口上）。
    val context = LocalContext.current

    suspend fun loadGroup(tagId: String, allowWeb: Boolean) {
        val index = channels.indexOfFirst { it.tagId == tagId }
        if (index < 0) return
        val channel = channels[index]
        if (!ChannelListSpec.canInitialLoad(channel.toSpecGroup())) return
        updateChannels(
            channels.toMutableList().also {
                it[index] = channel.copy(loading = true, error = null)
            },
        )
        try {
            var updated = FeaturedRepository.loadInitial(channel)
                .getOrElse { channel.copy(loading = false, error = it.collectionMessage()) }
            Log.i(
                "FeaturedWebFeed",
                "native tag=$tagId videos=${updated.videos.size} degraded=${updated.degraded} error=${updated.error}",
            )
            Log.i(
                "FeaturedWebFeed",
                "gate tag=$tagId allowWeb=$allowWeb empty=${updated.videos.isEmpty()} " +
                    "isAll=${FeaturedRepository.isAllTag(tagId)}",
            )
            // 原生 module/feed 拿空时，优先用磁盘缓存（1 小时 TTL），没有再走页面请求兜底。
            if (allowWeb && updated.videos.isEmpty() && !FeaturedRepository.isAllTag(tagId)) {
                val reason = FeaturedWebFeed.unsupportedReason(context)
                val cached = if (reason != null) emptyList() else FeaturedWebFeed.cached(context, tagId)
                if (cached.isNotEmpty()) Log.i("FeaturedWebFeed", "disk tag=$tagId items=${cached.size}")
                val webItems = when {
                    reason != null -> emptyList()
                    cached.isNotEmpty() -> cached
                    else -> FeaturedWebFeed.fetch(context, tagId)
                }
                Log.i("FeaturedWebFeed", "fallback tag=$tagId reason=$reason web=${webItems.size}")
                if (reason != null) updated = updated.copy(error = reason)
                if (webItems.isNotEmpty()) {
                    updated = updated.copy(
                        videos = webItems,
                        loading = false,
                        error = null,
                        hasMore = false,
                        presentedIds = webItems.map { it.awemeId() },
                        degraded = false,
                    )
                }
            }
            val at = channels.indexOfFirst { it.tagId == tagId }
            if (at >= 0) {
                updateChannels(channels.toMutableList().also { it[at] = updated })
            }
        } finally {
            // 协程被取消（切页/返回）时也要把 loading 复位，否则缓存里该分区永远停在“加载中”。
            val at = channels.indexOfFirst { it.tagId == tagId }
            if (at >= 0 && channels[at].loading) {
                updateChannels(
                    channels.toMutableList().also { it[at] = it[at].copy(loading = false) },
                )
            }
        }
    }

    /** @return 这次翻页是否真的带来了新作品（用于停滞保护）。 */
    suspend fun loadMore(tagId: String, lastVisibleIndex: Int): Boolean {
        val index = channels.indexOfFirst { it.tagId == tagId }
        if (index < 0) return false
        val channel = channels[index]
        if (channel.loading) return false
        updateChannels(
            channels.toMutableList().also {
                it[index] = channel.copy(loading = true, error = null)
            },
        )
        var grew = false
        try {
            val updated = FeaturedRepository.loadMore(channel, lastVisibleIndex)
                .getOrElse { channel.copy(loading = false, error = it.collectionMessage()) }
            grew = updated.videos.size > channel.videos.size
            val at = channels.indexOfFirst { it.tagId == tagId }
            if (at >= 0) {
                updateChannels(channels.toMutableList().also { it[at] = updated })
            }
        } finally {
            // 协程被取消时也要把 loading 复位，否则该分区永远停在“加载中”。
            val at = channels.indexOfFirst { it.tagId == tagId }
            if (at >= 0 && channels[at].loading) {
                updateChannels(
                    channels.toMutableList().also { it[at] = it[at].copy(loading = false) },
                )
            }
        }
        return grew
    }

    /**
     * 「换一批」：再点当前分区时整屏替换该分区内容。
     *
     * 对位原版 i42.java:6276——重复点击已选中的分区 Tab 会提示「正在刷新精选内容」并重新请求分组，
     * 而不是停留在旧列表；loadInitial 会因为「已有内容」短路，所以这里必须走 refresh。
     */
    suspend fun refreshGroup(tagId: String) {
        val index = channels.indexOfFirst { it.tagId == tagId }
        if (index < 0) return
        val channel = channels[index]
        if (channel.loading) return
        updateChannels(
            channels.toMutableList().also {
                it[index] = channel.copy(loading = true, error = null)
            },
        )
        try {
            var updated = FeaturedRepository.refresh(channel)
                .getOrElse { channel.copy(loading = false, error = it.collectionMessage()) }
            if (updated.videos.isEmpty() && !FeaturedRepository.isAllTag(tagId)) {
                val reason = FeaturedWebFeed.unsupportedReason(context)
                val webItems = if (reason != null) emptyList() else FeaturedWebFeed.fetch(context, tagId)
                if (reason != null) updated = updated.copy(error = reason)
                if (webItems.isNotEmpty()) {
                    updated = updated.copy(
                        videos = webItems,
                        loading = false,
                        error = null,
                        hasMore = false,
                        presentedIds = webItems.map { it.awemeId() },
                        degraded = false,
                    )
                }
            }
            if (updated.error == null) refreshSeq += 1
            val at = channels.indexOfFirst { it.tagId == tagId }
            if (at >= 0) {
                updateChannels(channels.toMutableList().also { it[at] = updated })
            }
        } finally {
            val at = channels.indexOfFirst { it.tagId == tagId }
            if (at >= 0 && channels[at].loading) {
                updateChannels(
                    channels.toMutableList().also { it[at] = it[at].copy(loading = false) },
                )
            }
        }
    }

    // 分区列表：优先复用进程内缓存；首次进入或用户点重试时才真正请求。
    LaunchedEffect(pageAttempt) {
        if (pageAttempt == 0 && channels.isNotEmpty()) {
            channelsLoaded = true
            if (selectedTagId == null) selectedTagId = channels.first().tagId
            return@LaunchedEffect
        }
        channelsLoaded = false
        pageError = null
        updateChannels(emptyList())
        selectedTagId = null
        FeaturedRepository.loadChannels().fold(
            onSuccess = { list ->
                updateChannels(list)
                channelsLoaded = true
                if (list.isEmpty()) return@fold
                selectedTagId = list.first().tagId
            },
            onFailure = {
                channelsLoaded = true
                pageError = it.collectionMessage()
            },
        )
    }

    // 切分区：并行补齐当前分区与其后 3 个分组。串行请求会让首屏多等 2 个 RTT。
    LaunchedEffect(selected?.tagId, channelsLoaded) {
        val tagId = selected?.tagId ?: return@LaunchedEffect
        if (!channelsLoaded) return@LaunchedEffect
        val start = channels.indexOfFirst { it.tagId == tagId }
        if (start < 0) return@LaunchedEffect
        val end = ChannelListSpec.prefetchEndIndexExclusive(start, channels.size)
        val tags = (start until end).map { channels[it].tagId }
        coroutineScope {
            tags.map { tag -> async { loadGroup(tag, allowWeb = tag == tagId) } }.awaitAll()
        }
    }

    // 『精选自动播放』消费点：分区首次拉到内容就直接起播第一条。
    // 每个分区在本次会议里只允许自动起播一次，否则从播放器返回会被再次踢进去。
    LaunchedEffect(selected?.tagId, selected?.videos?.size) {
        if (AppRuntime.settings?.featuredAutoPlay() != true) return@LaunchedEffect
        val channel = selected ?: return@LaunchedEffect
        if (channel.videos.isEmpty()) return@LaunchedEffect
        if (!FeaturedRepository.claimAutoPlay(channel.tagId)) return@LaunchedEffect
        FeaturedRepository.cache(channel)
        onOpenPlayer(0)
    }

    // 首帧/空态焦点：进入时聚焦当前分区 chip；无 chip 时聚焦空态操作按钮。
    var focusGiven by remember { mutableStateOf(false) }
    // FocusRequester 必须在 remember 内构造（Compose 1.10 起标记为 @RememberInComposition）。
    val chipRequesters = remember(channels) {
        channels.associate { it.tagId to FocusRequester() }.toMutableMap()
    }
    val actionRequester = remember { FocusRequester() }
    val channelScrollState = rememberScrollState()
    // 重试（pageAttempt 变化）后重新授焦：否则重试按钮随错误态消失后焦点悬在顶栏。
    LaunchedEffect(pageAttempt) {
        focusGiven = false
    }
    LaunchedEffect(channels.size, channelsLoaded, pageError) {
        if (focusGiven) return@LaunchedEffect
        val target = channels.getOrNull(selectedIndex)?.let { chipRequesters[it.tagId] }
            ?: if (channelsLoaded) actionRequester else null
        if (target == null) return@LaunchedEffect
        withFrameNanos { }
        // 只有真正落焦成功才算「已授焦」：加载态节点未挂载时失败不能吞掉这次机会。
        if (runCatching { target.requestFocus() }.isSuccess) {
            focusGiven = true
        }
    }

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 24.dp)) {
        // 页头只留页面标题：品牌标识已经在全局顶栏，页内再放一次会重复。
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = ChannelListSpec.TEXT_FEATURED_CHANNEL_TITLE,
                color = AxTvColors.TextPrimary,
                style = TextStyle(
                    fontSize = ChannelListSpec.FEATURED_TITLE_SP.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
        }

        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.horizontalScroll(channelScrollState),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            channels.forEachIndexed { index, channel ->
                val requester = chipRequesters.getValue(channel.tagId)
                FeaturedChip(
                    label = channel.name,
                    selected = ChannelListSpec.isTabSelected(index, selectedIndex),
                    onClick = {
                        if (channel.tagId == selectedTagId) {
                            scope.launch { refreshGroup(channel.tagId) }
                        } else {
                            selectedTagId = channel.tagId
                        }
                    },
                    modifier = Modifier.focusRequester(requester),
                )
            }
        }
        Spacer(Modifier.height(16.dp))

        when {
            !channelsLoaded && pageError == null -> PageMessage(ChannelListSpec.TEXT_LOADING_FEATURED)

            pageError != null -> PageAction(
                title = ChannelListSpec.TEXT_NO_VIDEOS,
                hint = pageError,
                actionLabel = ChannelListSpec.TEXT_RETRY,
                onAction = { pageAttempt += 1 },
                actionRequester = actionRequester,
            )

            channelsLoaded && visible.isEmpty() -> {
                val state = ChannelListSpec.featuredPageState(false, FeaturedRepository.hasCookie())
                if (state == ChannelListSpec.FeaturedPageState.NO_COOKIE) {
                    PageAction(
                        title = ChannelListSpec.TEXT_CONFIGURE_COOKIE,
                        hint = null,
                        actionLabel = ChannelListSpec.TEXT_GO_SETTINGS,
                        onAction = onOpenSettings,
                        actionRequester = actionRequester,
                    )
                } else {
                    PageAction(
                        title = ChannelListSpec.TEXT_NO_VIDEOS,
                        hint = ChannelListSpec.TEXT_NO_VIDEOS_HINT,
                        actionLabel = ChannelListSpec.TEXT_RETRY,
                        onAction = { pageAttempt += 1 },
                        actionRequester = actionRequester,
                    )
                }
            }

            selected != null -> SectionContent(
                channel = selected,
                refreshSeq = refreshSeq,
                onOpenPlayer = { index ->
                    FeaturedRepository.cache(selected)
                    onOpenPlayer(index)
                },
                onOpenDetail = onOpenDetail,
                // 分区 ERROR 重试：按规格重新拉取该分区（m9520c 重试 → 重新请求分组），不是翻页。
                onRetry = { loadGroup(selected.tagId, allowWeb = true) },
                onNeedMore = { lastVisible -> loadMore(selected.tagId, lastVisible) },
            )

            else -> PageMessage(TEXT_PLACEHOLDER)
        }
    }
}

/** 分区内容：m9520c 状态机 + 视频网格 + 分页 footer。 */
@Composable
private fun SectionContent(
    channel: FeaturedChannel,
    refreshSeq: Int,
    onOpenPlayer: (Int) -> Unit,
    onOpenDetail: (String) -> Unit,
    onRetry: suspend () -> Unit,
    onNeedMore: suspend (Int) -> Boolean,
) {
    val spec = channel.toSpecGroup()
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    val errorRequester = remember { FocusRequester() }
    val videoRequesters = remember(channel.tagId) { mutableMapOf<String, FocusRequester>() }
    val lastVisibleIndex by remember(gridState) {
        derivedStateOf { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
    }

    // 从播放器返回：回到该分区上次聚焦的视频；没有记忆则保持父级给的页签焦点。
    var restoredFocus by remember(channel.tagId) { mutableStateOf(false) }
    LaunchedEffect(channel.tagId, channel.videos.size) {
        if (restoredFocus || channel.videos.isEmpty()) return@LaunchedEffect
        val remembered = FocusMemory.last("featured:${channel.tagId}")
        if (remembered != null) {
            val index = channel.videos.indexOfFirst { it.awemeId() == remembered }
            if (index >= 0) {
                runCatching { gridState.scrollToItem(index) }
                withFrameNanos { }
                withFrameNanos { }
                runCatching { videoRequesters[remembered]?.requestFocus() }
            }
        }
        restoredFocus = true
    }

    // 「换一批」整屏替换后回到第一条：网格若停在旧批的滚动位置，新批条数更少时会被截到末尾，
    // 用户看到的是一批随机尾部而不是刚换出来的内容。
    LaunchedEffect(refreshSeq) {
        if (refreshSeq == 0) return@LaunchedEffect
        runCatching { gridState.scrollToItem(0) }
        withFrameNanos { }
        withFrameNanos { }
        channel.videos.firstOrNull()?.let { first ->
            runCatching { videoRequesters[first.awemeId()]?.requestFocus() }
        }
    }

    // 同时观察“可见末项 / 已加载数量 / loading”：只看索引的话，翻页返回重复数据后
    // 索引不再变化，loading 从 true 回到 false 也不会重新触发，列表就卡在末尾不动了。
    val latestChannel by rememberUpdatedState(channel)
    var requestInFlight by remember(channel.tagId) { mutableStateOf(false) }
    var stalledPages by remember(channel.tagId) { mutableIntStateOf(0) }
    LaunchedEffect(
        channel.tagId,
        channel.videos.size,
        channel.loading,
        channel.error,
        lastVisibleIndex,
        requestInFlight,
    ) {
        val current = latestChannel
        if (requestInFlight || current.videos.isEmpty() || current.loading || current.error != null) {
            return@LaunchedEffect
        }
        if (!ChannelListSpec.shouldRequestLoadMore(lastVisibleIndex, current.videos.size)) {
            return@LaunchedEffect
        }
        if (stalledPages >= MAX_STALLED_PAGES) return@LaunchedEffect
        requestInFlight = true
        scope.launch {
            try {
                val grew = onNeedMore(lastVisibleIndex)
                stalledPages = if (grew) 0 else stalledPages + 1
            } finally {
                requestInFlight = false
            }
        }
    }

    when (ChannelListSpec.sectionState(spec)) {
        ChannelListSpec.SectionState.PLACEHOLDER -> PageMessage(TEXT_PLACEHOLDER)

        ChannelListSpec.SectionState.LOADING -> PageMessage(ChannelListSpec.TEXT_SECTION_LOADING)

        ChannelListSpec.SectionState.ERROR -> PageAction(
            title = ChannelListSpec.TEXT_SECTION_LOAD_FAILED,
            hint = channel.error,
            actionLabel = ChannelListSpec.TEXT_RETRY,
            onAction = { scope.launch { onRetry() } },
            actionRequester = errorRequester,
        )

        ChannelListSpec.SectionState.EMPTY -> PageMessage(ChannelListSpec.TEXT_SECTION_EMPTY)

        else -> Column(Modifier.fillMaxSize()) {
            Text(
                text = ChannelListSpec.videoCountText(spec).orEmpty(),
                color = AxTvColors.TextSecondary,
                style = TextStyle(fontSize = 12.sp),
            )
            if (channel.degraded) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = TEXT_DEGRADED,
                    color = AxTvColors.AccentWarm,
                    style = TextStyle(fontSize = 12.sp),
                )
            }
            Spacer(Modifier.height(8.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(FEATURED_COLUMNS),
                state = gridState,
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(items = channel.videos, key = { it.awemeId() }) { aweme ->
                    val id = aweme.awemeId()
                    val requester = remember(id) { FocusRequester() }
                        .also { videoRequesters[id] = it }
                    FeaturedVideoCard(
                        aweme = aweme,
                        modifier = Modifier.focusRequester(requester),
                        onFocused = {
                            FocusMemory.remember("featured:${channel.tagId}", id)
                        },
                        onClick = {
                            val index = channel.videos.indexOfFirst { it.awemeId() == aweme.awemeId() }
                            onOpenPlayer(index.coerceAtLeast(0))
                        },
                        onDetail = { if (id.isNotEmpty()) onOpenDetail(id) },
                    )
                }
                if (channel.loading || channel.error != null) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                            Text(
                                text = channel.error ?: ChannelListSpec.TEXT_LOADING_FEATURED,
                                color = if (channel.error != null) {
                                    AxTvColors.AccentWarm
                                } else {
                                    AxTvColors.TextSecondary
                                },
                                style = TextStyle(fontSize = 12.sp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FeaturedChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .background(AxTvColors.Surface, shape)
            .focusRing(focused || selected, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            color = if (selected || focused) AxTvColors.Accent else AxTvColors.TextSecondary,
            style = TextStyle(
                fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            ),
        )
    }
}

@Composable
private fun FeaturedVideoCard(
    aweme: Aweme,
    onClick: () -> Unit,
    onDetail: () -> Unit,
    modifier: Modifier = Modifier,
    onFocused: () -> Unit = {},
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    // 外卡只做焦点组与整体高亮（hasFocus：按钮获焦时整卡仍亮）；
    // 内容区承接调用方的 focusRequester，恢复焦点落在卡片（播放）而不是详情按钮上。
    Column(
        modifier = Modifier
            .focusGroup()
            .onFocusChanged { focused = it.hasFocus }
            .background(AxTvColors.Surface, shape)
            .focusRing(focused, shape)
            .padding(6.dp),
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .onFocusChanged { if (it.isFocused) onFocused() }
                .clickable(onClick = onClick),
        ) {
            AsyncImage(
                model = aweme.video().cover().firstUrl(),
                contentDescription = aweme.desc(),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = aweme.desc().ifBlank { aweme.author().nickname() },
                color = AxTvColors.TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(fontSize = 13.sp),
            )
        }
        Spacer(Modifier.height(6.dp))
        ActionButton(
            label = "详情",
            onClick = onDetail,
            modifier = Modifier.align(Alignment.End),
        )
    }
}

@Composable
private fun PageMessage(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = text, color = AxTvColors.TextSecondary, style = TextStyle(fontSize = 15.sp))
    }
}

@Composable
private fun PageAction(
    title: String,
    hint: String?,
    actionLabel: String,
    onAction: () -> Unit,
    actionRequester: FocusRequester,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = title,
                color = AxTvColors.TextPrimary,
                style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold),
            )
            if (!hint.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = hint,
                    color = AxTvColors.TextSecondary,
                    style = TextStyle(fontSize = 13.sp),
                )
            }
            Spacer(Modifier.height(16.dp))
            FeaturedActionButton(
                label = actionLabel,
                onClick = onAction,
                modifier = Modifier.focusRequester(actionRequester),
            )
        }
    }
}

@Composable
private fun FeaturedActionButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .background(if (focused) AxTvColors.SurfaceHigh else AxTvColors.Surface, shape)
            .focusRing(focused, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
    ) {
        Text(
            text = label,
            color = if (focused) Color.White else AxTvColors.TextPrimary,
            style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold),
        )
    }
}
