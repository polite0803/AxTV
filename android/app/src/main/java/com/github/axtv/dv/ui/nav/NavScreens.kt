/*
 * 导航接线用的装配页：不在任何功能页内部改动，只把「网络数据 → 播放器数据源」接起来。
 *
 * - NavMessageScreen：加载中 / 失败 / 空队列的统一画面（可返回、可重试）。
 * - PlayerFromFeedScreen：首页队列（HomeFeedRepository）直接播放；队列为空时先拉首屏。
 * - PlayerFromAwemeScreen：按 awemeId 走 VideoApi.detail 拉作品后单条播放，
 *   供详情页、历史、稍后再看复用（合集外单集没有整队列可播）。
 * - PlayerFromMixScreen：合集内连播（已加载剧集整队列 + 队尾续页）+ 分集列表（设置项 mix_playlist_panel 的数据源）。
 */
package com.github.axtv.dv.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import axtv.api.VideoApi
import axtv.model.Aweme
import com.github.axtv.dv.adapter.AppRuntime
import com.github.axtv.dv.ui.AxTvColors
import com.github.axtv.dv.ui.author.AuthorRepository
import com.github.axtv.dv.ui.collection.CollectionDetail
import com.github.axtv.dv.ui.collection.CollectionEpisode
import com.github.axtv.dv.ui.collection.CollectionRepository
import com.github.axtv.dv.ui.collection.CollectionSource
import com.github.axtv.dv.ui.collection.collectionMessage
import com.github.axtv.dv.ui.featured.FeaturedRepository
import com.github.axtv.dv.ui.focusRing
import com.github.axtv.dv.ui.home.FollowFeedRepository
import com.github.axtv.dv.ui.home.HomeFeedRepository
import com.github.axtv.dv.ui.home.MobileFeedRepository
import com.github.axtv.dv.ui.player.PlayerOverlayScreen
import com.github.axtv.dv.ui.player.VideoPlaylistProvider
import com.github.axtv.dv.ui.search.SearchPlaybackRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 统一提示页：加载中 / 失败 / 空态，首帧把焦点交给「返回」或「重试」。 */
@Composable
fun NavMessageScreen(
    title: String,
    message: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    retryLabel: String? = null,
    onRetry: (() -> Unit)? = null,
) {
    val actionRequester = remember { FocusRequester() }

    LaunchedEffect(retryLabel) {
        withFrameNanos { }
        runCatching { actionRequester.requestFocus() }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 40.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = title,
            color = AxTvColors.TextPrimary,
            style = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold),
        )
        Text(
            text = message,
            color = if (onRetry != null) AxTvColors.AccentWarm else AxTvColors.TextSecondary,
            style = TextStyle(fontSize = 14.sp),
        )
        if (retryLabel != null && onRetry != null) {
            NavAction(label = retryLabel, requester = actionRequester, onClick = onRetry)
        }
        NavAction(
            label = "返回",
            requester = if (retryLabel == null) actionRequester else null,
            onClick = onBack,
        )
    }
}

@Composable
private fun NavAction(
    label: String,
    requester: FocusRequester?,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier = (if (requester != null) Modifier.focusRequester(requester) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .background(AxTvColors.Surface, RoundedCornerShape(10.dp))
            .focusRing(focused, RoundedCornerShape(10.dp))
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Text(
            text = label,
            color = AxTvColors.TextPrimary,
            style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
        )
    }
}

/** 播放器续页时回传给推荐接口的最近作品 id 上限（与首页一致）。 */
private const val PLAYER_RECENT_ID_LIMIT = 10

/** 连续这么多页没有新增作品就停止续页，避免服务端重复返回时无限请求。 */
private const val PLAYER_MAX_STALLED_PAGES = 3

/**
 * 首页队列播放：队列取自首页当前标签页的仓储快照。
 *
 * - [PLAYER_SOURCE_RECOMMEND]：直接用 [HomeFeedRepository.cachedAwemes]（首页已加载过的真实列表）；
 *   队列为空时按持久化刷新进度补拉一页。
 * - [PLAYER_SOURCE_FOLLOW]：用 [FollowFeedRepository.cachedAwemes]，队尾按服务端游标续页。
 * - [PLAYER_SOURCE_MOBILE]：用 [MobileFeedRepository.cachedAwemes]，队尾按 refresh_index 续页。
 *
 * 队列只有当前已加载的几条时（例如首页首屏被过滤到 2 条），播放到队尾会按对应数据源的
 * 分页继续追加，不会卡在最后一条。
 */
@Composable
fun PlayerFromFeedScreen(
    startIndex: Int,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    source: String = PLAYER_SOURCE_RECOMMEND,
    onOpenAuthor: (String) -> Unit = {},
) {
    val initialQueue = remember(source) {
        when (source) {
            PLAYER_SOURCE_FOLLOW -> FollowFeedRepository.cachedAwemes
            PLAYER_SOURCE_MOBILE -> MobileFeedRepository.cachedAwemes
            else -> HomeFeedRepository.cachedAwemes
        }
    }
    var queue by remember(source) { mutableStateOf(initialQueue) }
    var loading by remember(source) { mutableStateOf(queue.isEmpty()) }
    var error by remember(source) { mutableStateOf<String?>(null) }
    var attempt by remember(source) { mutableIntStateOf(0) }

    // 播放器内续页状态：游标 / 是否还有更多 / 已追加的 id / 兜底序号。
    var nextCursor by remember(source) {
        mutableStateOf(
            when (source) {
                PLAYER_SOURCE_FOLLOW -> FollowFeedRepository.cachedCursor
                PLAYER_SOURCE_MOBILE -> MobileFeedRepository.cachedNextIndex.toLong()
                else -> HomeFeedRepository.cachedNextCursor
            },
        )
    }
    var hasMore by remember(source) {
        mutableStateOf(
            when (source) {
                PLAYER_SOURCE_FOLLOW -> FollowFeedRepository.cachedAwemes.isNotEmpty()
                PLAYER_SOURCE_MOBILE -> MobileFeedRepository.cachedHasMore
                else -> HomeFeedRepository.cachedHasMore
            },
        )
    }
    var stalledPages by remember(source) { mutableIntStateOf(0) }
    var entrySeq by remember(source) { mutableIntStateOf(queue.size) }
    val knownIds = remember(source) {
        queue.mapNotNull { it.awemeId().takeIf(String::isNotEmpty) }
            .toMutableSet()
    }

    LaunchedEffect(attempt) {
        if (queue.isNotEmpty()) {
            loading = false
            return@LaunchedEffect
        }
        loading = true
        error = null
        when (source) {
            PLAYER_SOURCE_FOLLOW -> FollowFeedRepository.load(0L).fold(
                onSuccess = { page ->
                    queue = page.awemes
                    FollowFeedRepository.cache(page.awemes, page.nextCursor)
                    nextCursor = page.nextCursor
                    hasMore = page.nextCursor > 0
                    stalledPages = 0
                    knownIds.clear()
                    knownIds.addAll(
                        page.awemes.mapNotNull { it.awemeId().takeIf(String::isNotEmpty) },
                    )
                    entrySeq = page.awemes.size
                },
                onFailure = { error = it.message ?: it.javaClass.simpleName },
            )

            PLAYER_SOURCE_MOBILE -> MobileFeedRepository.load(1).fold(
                onSuccess = { page ->
                    queue = page.awemes
                    MobileFeedRepository.cache(page.awemes, 2, page.hasMore)
                    nextCursor = 2L
                    hasMore = page.hasMore
                    stalledPages = 0
                    knownIds.clear()
                    knownIds.addAll(
                        page.awemes.mapNotNull { it.awemeId().takeIf(String::isNotEmpty) },
                    )
                    entrySeq = page.awemes.size
                },
                onFailure = { error = it.message ?: it.javaClass.simpleName },
            )

            // 走 refresh() 而非 load(0)：后者恒定发 refresh_index=1 + 空已展示 id，
            // 与冷启动首屏逐字节相同，服务端会回同一批——正是「推荐永远是这几个」的来源。
            else -> HomeFeedRepository.refresh().fold(
                onSuccess = { page ->
                    queue = page.awemes
                    HomeFeedRepository.cache(page.awemes, page.nextCursor, page.hasMore)
                    nextCursor = page.nextCursor
                    hasMore = page.hasMore
                    stalledPages = 0
                    knownIds.clear()
                    knownIds.addAll(
                        page.awemes.mapNotNull { it.awemeId().takeIf(String::isNotEmpty) },
                    )
                    entrySeq = page.awemes.size
                },
                onFailure = { error = it.message ?: it.javaClass.simpleName },
            )
        }
        loading = false
    }

    val loadMore: suspend () -> List<VideoPlaylistProvider.Entry> = {
        if (!hasMore) {
            emptyList()
        } else when (source) {
            PLAYER_SOURCE_FOLLOW -> FollowFeedRepository.load(nextCursor).fold(
                onSuccess = { page ->
                    nextCursor = page.nextCursor
                    hasMore = page.nextCursor > 0
                    val fresh = page.awemes.filter { aweme ->
                        val id = aweme.awemeId()
                        id.isNotEmpty() && knownIds.add(id)
                    }
                    stalledPages = if (fresh.isEmpty()) stalledPages + 1 else 0
                    if (stalledPages >= PLAYER_MAX_STALLED_PAGES) hasMore = false
                    if (fresh.isNotEmpty()) {
                        FollowFeedRepository.cache(FollowFeedRepository.cachedAwemes + fresh, nextCursor)
                    }
                    val base = entrySeq
                    entrySeq += fresh.size
                    fresh.mapIndexed { index, aweme -> awemePlaylistEntry(aweme, base + index) }
                },
                onFailure = { emptyList() },
            )

            PLAYER_SOURCE_MOBILE -> MobileFeedRepository.load(nextCursor.toInt()).fold(
                onSuccess = { page ->
                    nextCursor += 1
                    hasMore = page.hasMore
                    val fresh = page.awemes.filter { aweme ->
                        val id = aweme.awemeId()
                        id.isNotEmpty() && knownIds.add(id)
                    }
                    stalledPages = if (fresh.isEmpty()) stalledPages + 1 else 0
                    if (stalledPages >= PLAYER_MAX_STALLED_PAGES) hasMore = false
                    if (fresh.isNotEmpty()) {
                        MobileFeedRepository.cache(
                            MobileFeedRepository.cachedAwemes + fresh,
                            nextCursor.toInt(),
                            hasMore,
                        )
                    }
                    val base = entrySeq
                    entrySeq += fresh.size
                    fresh.mapIndexed { index, aweme -> awemePlaylistEntry(aweme, base + index) }
                },
                onFailure = { emptyList() },
            )

            else -> {
                val recentIds = knownIds.toList().takeLast(PLAYER_RECENT_ID_LIMIT)
                HomeFeedRepository.load(nextCursor, recentIds).fold(
                    onSuccess = { page ->
                        nextCursor += 1
                        hasMore = page.hasMore
                        val fresh = page.awemes.filter { aweme ->
                            val id = aweme.awemeId()
                            id.isNotEmpty() && knownIds.add(id)
                        }
                        stalledPages = if (fresh.isEmpty()) stalledPages + 1 else 0
                        if (stalledPages >= PLAYER_MAX_STALLED_PAGES) {
                            hasMore = false
                        }
                        if (fresh.isNotEmpty()) {
                            // 同步回仓储：从播放器返回首页时，列表已经包含续页内容。
                            HomeFeedRepository.cache(
                                HomeFeedRepository.cachedAwemes + fresh,
                                nextCursor,
                                hasMore,
                            )
                        }
                        val base = entrySeq
                        entrySeq += fresh.size
                        fresh.mapIndexed { index, aweme -> awemePlaylistEntry(aweme, base + index) }
                    },
                    onFailure = { emptyList() },
                )
            }
        }
    }

    when {
        queue.isNotEmpty() -> PlayerOverlayScreen(
            modifier = modifier,
            playlistProvider = remember(queue, startIndex) {
                awemePlaylistProvider(queue, startIndex)
            },
            playlistMore = loadMore,
            onOpenAuthor = onOpenAuthor,
        )

        loading -> NavMessageScreen(
            title = "播放器",
            message = "正在加载播放队列…",
            onBack = onBack,
            modifier = modifier,
        )

        else -> NavMessageScreen(
            title = "播放器",
            message = error ?: "播放队列为空，请先回到首页加载内容。",
            onBack = onBack,
            modifier = modifier,
            retryLabel = "重试",
            onRetry = { attempt += 1 },
        )
    }
}

/**
 * 精选队列播放：队列来自 FeaturedScreen 点击条目时写入的 [FeaturedRepository.cachedPlaybackQueue]。
 * 无队列（例如直接从外部 route 进入 featured/play）时只提示返回，不再拉数据。
 *
 * 播放到队尾时会按缓存的分区上下文继续翻页（与首页播放器同一条续页链路）。
 */
@Composable
fun PlayerFromFeaturedScreen(
    startIndex: Int,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenAuthor: (String) -> Unit = {},
) {
    val queue = FeaturedRepository.cachedAwemes
    val snapshot = remember { FeaturedRepository.cachedPlaybackQueue }

    var channel by remember { mutableStateOf(snapshot?.channel) }
    var stalledPages by remember { mutableIntStateOf(0) }
    var entrySeq by remember { mutableIntStateOf(queue.size) }
    val knownIds = remember {
        queue.mapNotNull { it.awemeId().takeIf(String::isNotEmpty) }
            .toMutableSet()
    }

    val loadMore: (suspend () -> List<VideoPlaylistProvider.Entry>)? = snapshot?.let {
        {
            val current = channel
            if (current == null || !current.hasMore) {
                emptyList()
            } else {
                FeaturedRepository.loadMore(current, current.videos.size - 1).fold(
                    onSuccess = { next ->
                        channel = next
                        val fresh = next.videos.filter { aweme ->
                            val id = aweme.awemeId()
                            id.isNotEmpty() && knownIds.add(id)
                        }
                        stalledPages = if (fresh.isEmpty()) stalledPages + 1 else 0
                        if (stalledPages >= PLAYER_MAX_STALLED_PAGES) {
                            channel = next.copy(hasMore = false)
                        }
                        val base = entrySeq
                        entrySeq += fresh.size
                        fresh.mapIndexed { index, aweme ->
                            awemePlaylistEntry(aweme, base + index)
                        }
                    },
                    onFailure = { emptyList() },
                )
            }
        }
    }

    when {
        queue.isNotEmpty() -> PlayerOverlayScreen(
            modifier = modifier,
            playlistProvider = remember(queue, startIndex) {
                awemePlaylistProvider(queue, startIndex)
            },
            playlistMore = loadMore,
            onOpenAuthor = onOpenAuthor,
        )

        else -> NavMessageScreen(
            title = "播放器",
            message = "精选播放队列为空，请先在精选页选择视频。",
            onBack = onBack,
            modifier = modifier,
        )
    }
}

/**
 * 搜索结果队列播放：队列来自 [SearchPlaybackRepository] 里视频 tab 已加载的作品。
 * 从搜索结果进详情页后点「播放」会走这里，按列表顺序连播；到队尾按同一关键词继续翻页。
 *
 * 队列在进入播放器时取一次快照：翻页只追加到 provider，不重建 provider，避免打断当前播放。
 */
@Composable
fun PlayerFromSearchScreen(
    startIndex: Int,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenAuthor: (String) -> Unit = {},
) {
    val queue = remember { SearchPlaybackRepository.cachedAwemes }
    var entrySeq by remember { mutableIntStateOf(queue.size) }
    val knownIds = remember {
        queue.mapNotNull { it.awemeId().takeIf(String::isNotEmpty) }.toMutableSet()
    }

    val loadMore: suspend () -> List<VideoPlaylistProvider.Entry> = {
        val fresh = SearchPlaybackRepository.loadMore().filter { aweme ->
            val id = aweme.awemeId()
            id.isNotEmpty() && knownIds.add(id)
        }
        val base = entrySeq
        entrySeq += fresh.size
        fresh.mapIndexed { index, aweme -> awemePlaylistEntry(aweme, base + index) }
    }

    when {
        queue.isNotEmpty() -> PlayerOverlayScreen(
            modifier = modifier,
            playlistProvider = remember(queue, startIndex) {
                awemePlaylistProvider(queue, startIndex)
            },
            playlistMore = loadMore,
            onOpenAuthor = onOpenAuthor,
        )

        else -> NavMessageScreen(
            title = "播放器",
            message = "搜索播放队列为空，请返回搜索结果重新选择视频。",
            onBack = onBack,
            modifier = modifier,
        )
    }
}

/**
 * 作者作品连播：队列来自 [AuthorRepository] 里 [AuthorChannelScreen] 已加载的该作者作品。
 *
 * - 从选中的作品播到队尾（wrap=false，不回到第一条）；
 * - 队尾按作者作品接口的 max_cursor 继续追加，直到 has_more=false；
 * - 深链进入且快照为空时先拉第一页；目标作品不在第一页时单独取详情放到队首。
 */
@Composable
fun PlayerFromAuthorScreen(
    secUid: String,
    awemeId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenAuthor: (String) -> Unit = {},
) {
    var loading by remember(secUid, awemeId) { mutableStateOf(true) }
    var error by remember(secUid, awemeId) { mutableStateOf<String?>(null) }
    var queue by remember(secUid, awemeId) { mutableStateOf(emptyList<Aweme>()) }
    var startIndex by remember(secUid, awemeId) { mutableIntStateOf(0) }
    var attempt by remember(secUid, awemeId) { mutableIntStateOf(0) }

    LaunchedEffect(secUid, awemeId, attempt) {
        loading = true
        error = null
        try {
            var loaded = AuthorRepository.snapshot(secUid)
            if (loaded.isEmpty()) {
                val response = withContext(Dispatchers.IO) {
                    AuthorRepository.fetchPage(secUid, 0L)
                }
                if (response.statusCode() != 0) {
                    error = "作者作品接口返回 status_code=${response.statusCode()}"
                    loading = false
                    return@LaunchedEffect
                }
                loaded = response.works()
                AuthorRepository.cacheFirst(
                    secUid,
                    loaded,
                    response.maxCursor(),
                    response.hasMore(),
                )
            }
            if (loaded.isEmpty()) {
                queue = emptyList()
                startIndex = 0
                error = "该作者暂无可播放作品"
            } else {
                val index = loaded.indexOfFirst { it.awemeId() == awemeId }
                if (awemeId.isNotBlank() && index < 0) {
                    // 深链目标不在已加载页：单独取详情放在队首，随后接作者作品列表。
                    val target = withContext(Dispatchers.IO) {
                        AppRuntime.api?.let { VideoApi(it).detail(awemeId).awemeDetail() }
                    }
                    queue = if (target != null) listOf(target) + loaded else loaded
                    startIndex = 0
                } else {
                    queue = loaded
                    startIndex = index.coerceAtLeast(0)
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            error = t.message ?: t.javaClass.simpleName
        }
        loading = false
    }

    // 翻页只追加到 provider，不改 queue：避免播放中途因重组把 PlayerSession 重建掉。
    var entrySeq by remember(queue) { mutableIntStateOf(queue.size) }
    val knownIds = remember(queue) {
        queue.mapNotNull { it.awemeId().takeIf(String::isNotEmpty) }.toMutableSet()
    }
    val loadMore: suspend () -> List<VideoPlaylistProvider.Entry> = {
        val fresh = AuthorRepository.loadMore(secUid).filter { aweme ->
            val id = aweme.awemeId()
            id.isNotEmpty() && knownIds.add(id)
        }
        val base = entrySeq
        entrySeq += fresh.size
        fresh.mapIndexed { index, aweme -> awemePlaylistEntry(aweme, base + index) }
    }

    when {
        queue.isNotEmpty() -> PlayerOverlayScreen(
            modifier = modifier,
            playlistProvider = remember(queue, startIndex) {
                awemePlaylistProvider(queue, startIndex, wrap = false)
            },
            playlistMore = loadMore,
            onOpenAuthor = onOpenAuthor,
        )

        loading -> NavMessageScreen(
            title = "作者频道",
            message = "正在加载该作者的作品…",
            onBack = onBack,
            modifier = modifier,
        )

        else -> NavMessageScreen(
            title = "作者频道",
            message = error ?: "该作者暂无可播放作品",
            onBack = onBack,
            modifier = modifier,
            retryLabel = "重试",
            onRetry = { attempt += 1 },
        )
    }
}

/** 单作品解析结果（详情接口 + 可重试），供 [PlayerFromAwemeScreen] 与 [PlayerFromMixScreen] 共用。 */
private data class PlaybackLoad(
    val aweme: Aweme?,
    val loading: Boolean,
    val error: String?,
    val retry: () -> Unit,
)

/** 按 awemeId 走 VideoApi.detail 解析作品；切 id 时自动重解析。 */
@Composable
private fun rememberAwemePlayback(awemeId: String): PlaybackLoad {
    var aweme by remember(awemeId) { mutableStateOf<Aweme?>(null) }
    var loading by remember(awemeId) { mutableStateOf(true) }
    var error by remember(awemeId) { mutableStateOf<String?>(null) }
    var attempt by remember(awemeId) { mutableIntStateOf(0) }

    LaunchedEffect(awemeId, attempt) {
        loading = true
        error = null
        val api = AppRuntime.api
        if (api == null) {
            error = "接口尚未装配，请稍后重试。"
            loading = false
            return@LaunchedEffect
        }
        try {
            val detail = withContext(Dispatchers.IO) { VideoApi(api).detail(awemeId) }
            val loaded = detail.awemeDetail()
            aweme = loaded
            if (loaded == null) {
                error = "未找到作品 $awemeId"
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            error = t.message ?: t.javaClass.simpleName
        }
        loading = false
    }
    return PlaybackLoad(aweme, loading, error) { attempt += 1 }
}

/** 按 awemeId 拉取作品并单条播放（详情 / 历史 / 稍后再看 / 合集单集共用）。 */
@Composable
fun PlayerFromAwemeScreen(
    awemeId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenAuthor: (String) -> Unit = {},
) {
    val load = rememberAwemePlayback(awemeId)
    val loadedAweme = load.aweme
    when {
        loadedAweme != null -> PlayerOverlayScreen(
            modifier = modifier,
            playlistProvider = remember(loadedAweme) {
                awemePlaylistProvider(listOf(loadedAweme), 0)
            },
            onOpenAuthor = onOpenAuthor,
        )

        load.loading -> NavMessageScreen(
            title = "播放器",
            message = "正在解析作品 $awemeId …",
            onBack = onBack,
            modifier = modifier,
        )

        else -> NavMessageScreen(
            title = "播放器",
            message = load.error ?: "无法播放作品 $awemeId",
            onBack = onBack,
            modifier = modifier,
            retryLabel = "重试",
            onRetry = load.retry,
        )
    }
}

/**
 * 合集内播放：整份已加载剧集构成播放队列，按顺序连播；到队尾继续拉下一页剧集。
 *
 * 保留原来的「合集分集」面板：选中任意一集 = 以该集重建队列起播（不回头、不压导航栈）。
 * 合集详情加载失败时仍尝试单集播放，与旧行为一致。
 */
private sealed interface MixPlaybackState {
    data object Loading : MixPlaybackState
    data class Failed(val message: String) : MixPlaybackState
    data class Ready(
        val detail: CollectionDetail?,
        val episodes: List<CollectionEpisode>,
        val queue: List<Aweme>,
        val startIndex: Int,
    ) : MixPlaybackState
}

@Composable
fun PlayerFromMixScreen(
    mixId: String,
    awemeId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenAuthor: (String) -> Unit = {},
) {
    var reloadToken by remember(mixId) { mutableIntStateOf(0) }
    var state by remember(mixId) { mutableStateOf<MixPlaybackState>(MixPlaybackState.Loading) }

    LaunchedEffect(mixId, awemeId, reloadToken) {
        state = MixPlaybackState.Loading
        state = try {
            val detail = CollectionRepository.loadDetail(mixId)
            val queue = detail.episodes.map { it.aweme }
            val index = queue.indexOfFirst { it.awemeId() == awemeId }
            if (index >= 0) {
                MixPlaybackState.Ready(detail, detail.episodes, queue, index)
            } else {
                // 深链指向的集不在已加载页：单独取一次详情放到队首，后续接已加载剧集。
                val api = AppRuntime.api ?: error("接口尚未装配，请稍后重试")
                val target = withContext(Dispatchers.IO) { VideoApi(api).detail(awemeId).awemeDetail() }
                if (target == null) {
                    MixPlaybackState.Failed("未找到作品 $awemeId")
                } else {
                    val merged = listOf(target) + queue.filterNot { it.awemeId() == awemeId }
                    MixPlaybackState.Ready(detail, detail.episodes, merged, 0)
                }
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            // 合集信息失败不影响单集播放（与旧实现的兜底一致）。
            val fallback = runCatching {
                val api = AppRuntime.api ?: error("接口尚未装配，请稍后重试")
                withContext(Dispatchers.IO) { VideoApi(api).detail(awemeId).awemeDetail() }
            }.getOrNull()
            if (fallback != null) {
                MixPlaybackState.Ready(
                    detail = null,
                    episodes = emptyList(),
                    queue = listOf(fallback),
                    startIndex = 0,
                )
            } else {
                MixPlaybackState.Failed(t.collectionMessage())
            }
        }
    }

    when (val current = state) {
        MixPlaybackState.Loading -> NavMessageScreen(
            title = "合集播放",
            message = "正在加载合集…",
            onBack = onBack,
            modifier = modifier,
        )

        is MixPlaybackState.Failed -> NavMessageScreen(
            title = "合集播放",
            message = current.message,
            onBack = onBack,
            modifier = modifier,
            retryLabel = "重试",
            onRetry = { reloadToken += 1 },
        )

        is MixPlaybackState.Ready -> MixPlayer(current, onBack, modifier, onOpenAuthor)
    }
}

@Composable
private fun MixPlayer(
    initial: MixPlaybackState.Ready,
    onBack: () -> Unit,
    modifier: Modifier,
    onOpenAuthor: (String) -> Unit,
) {
    if (initial.queue.isEmpty()) {
        NavMessageScreen(
            title = "合集播放",
            message = "该合集暂无可播放剧集",
            onBack = onBack,
            modifier = modifier,
        )
        return
    }

    // queue/startIndex 只在「换集」时变化（重建 provider 起播）；翻页只更新面板与追加条目，
    // 不改 queue，避免播放到一半因重组把 PlayerSession 重建掉。
    var queue by remember(initial) { mutableStateOf(initial.queue) }
    var startIndex by remember(initial) { mutableIntStateOf(initial.startIndex) }
    var panelEpisodes by remember(initial) { mutableStateOf(initial.episodes) }
    var currentEpisodeId by remember(initial) {
        mutableStateOf(initial.queue.getOrNull(initial.startIndex)?.awemeId().orEmpty())
    }
    var hasMore by remember(initial) { mutableStateOf(initial.detail?.hasMore == true) }
    var cursor by remember(initial) { mutableStateOf(initial.detail?.cursor ?: 0L) }
    var loadingMore by remember(initial) { mutableStateOf(false) }

    val loadMore: (suspend () -> List<VideoPlaylistProvider.Entry>)? = initial.detail?.let { detail ->
        val load: suspend () -> List<VideoPlaylistProvider.Entry> = {
            if (!hasMore || loadingMore) {
                emptyList()
            } else {
                loadingMore = true
                try {
                    val page = CollectionRepository.loadMoreEpisodes(
                        detail.copy(episodes = panelEpisodes, hasMore = hasMore, cursor = cursor),
                    )
                    val base = panelEpisodes.size
                    panelEpisodes = panelEpisodes + page.episodes
                    hasMore = page.hasMore
                    cursor = page.cursor
                    page.episodes
                        .mapNotNull { it.aweme }
                        .mapIndexed { index, aweme -> awemePlaylistEntry(aweme, base + index) }
                } catch (ce: CancellationException) {
                    throw ce
                } catch (t: Throwable) {
                    // 保持当前队列；播放器下次靠近队尾会再试。
                    emptyList()
                } finally {
                    loadingMore = false
                }
            }
        }
        load
    }

    // 连播自动切条后同步面板高亮，并按当前集写追更（mix 有对应端点，series 不写）。
    LaunchedEffect(currentEpisodeId) {
        val detail = initial.detail ?: return@LaunchedEffect
        if (detail.source != CollectionSource.MIX || currentEpisodeId.isBlank()) return@LaunchedEffect
        val episode = panelEpisodes.firstOrNull { it.awemeId == currentEpisodeId } ?: return@LaunchedEffect
        runCatching {
            CollectionRepository.recordProgress(detail.canonicalId, episode.awemeId, episode.number)
        }
    }

    PlayerOverlayScreen(
        modifier = modifier,
        playlistProvider = remember(queue, startIndex) {
            // 从选中集播到队尾，不回到第 1 集。
            awemePlaylistProvider(queue, startIndex, wrap = false)
        },
        playlistMore = loadMore,
        mixEpisodes = panelEpisodes,
        mixCurrentEpisodeId = currentEpisodeId,
        onMixEpisodeSelected = { episode ->
            val all = panelEpisodes.mapNotNull { it.aweme }
            val index = all.indexOfFirst { it.awemeId() == episode.awemeId }
            if (index >= 0 && episode.awemeId != currentEpisodeId) {
                queue = all
                startIndex = index
                currentEpisodeId = episode.awemeId
            }
        },
        onCurrentItemChanged = { id -> currentEpisodeId = id },
        onOpenAuthor = onOpenAuthor,
    )
}
