/*
 * 作者频道页：查看发布者资料与其全部作品，并从任意一条开始连续播放。
 *
 * 入口：
 *  - 详情页的作者行（点击进入）；
 *  - 播放器底部控制栏的「作者」按钮（观看中直接跳转）。
 *
 * 「持续观看发布者的所有视频」由两段接力完成：
 *  - 本页负责首屏 + 手动「加载更多」，结果写入 AuthorRepository；
 *  - ui/nav/PlayerFromAuthorScreen 用该快照组成连播队列，播放到队尾自动按 max_cursor 续页。
 *
 * 数据接口：UserApi.profileOther（他人主页）+ UserApi.userPosts（aweme/post）。
 */
package com.github.axtv.dv.ui.author

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.foundation.ExperimentalTvFoundationApi
import androidx.tv.material3.Text
import axtv.model.Author
import axtv.model.Aweme
import axtv.model.DataModels
import axtv.model.UserWorksResponse
import com.github.axtv.dv.adapter.AppRuntime
import com.github.axtv.dv.ui.AxTvColors
import com.github.axtv.dv.ui.FocusMemory
import com.github.axtv.dv.ui.library.ActionButton
import com.github.axtv.dv.ui.library.AwemeRow
import com.github.axtv.dv.ui.library.BackButton
import com.github.axtv.dv.ui.library.EmptyPane
import com.github.axtv.dv.ui.library.LoadState
import com.github.axtv.dv.ui.library.LoadingPane
import com.github.axtv.dv.ui.library.StatusPane
import com.github.axtv.dv.ui.library.UserAvatar
import com.github.axtv.dv.ui.library.statusFailure
import com.github.axtv.dv.ui.library.toFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalTvFoundationApi::class)
@Composable
fun AuthorChannelScreen(
    secUid: String,
    nickname: String = "",
    onBack: () -> Unit,
    /** 从作品 id 开始连播该作者的作品；空串表示从第一条开始（播放全部）。 */
    onPlay: (startAwemeId: String) -> Unit,
    modifier: Modifier = Modifier,
    onLogin: (() -> Unit)? = null,
) {
    var attempt by rememberSaveable(secUid) { mutableStateOf(0) }
    var loading by remember(secUid) { mutableStateOf(true) }
    var failure by remember(secUid) { mutableStateOf<LoadState.Failed?>(null) }
    var profile by remember(secUid) { mutableStateOf<Author?>(null) }
    var profileError by remember(secUid) { mutableStateOf<String?>(null) }
    val works = remember(secUid) { mutableStateListOf<Aweme>() }
    var hasMore by remember(secUid) { mutableStateOf(false) }
    var cursor by remember(secUid) { mutableStateOf(0L) }
    var loadingMore by remember(secUid) { mutableStateOf(false) }
    var moreError by remember(secUid) { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val backFocus = remember { FocusRequester() }
    val playAllFocus = remember { FocusRequester() }
    val rowRequesters = remember(secUid) { mutableMapOf<String, FocusRequester>() }
    val focusScope = "author:$secUid"
    var focusRestored by remember(secUid) { mutableStateOf(false) }

    // 加载期间先把焦点放在「返回」，避免遥控器按键落进焦点空洞。
    LaunchedEffect(secUid) {
        withFrameNanos { }
        runCatching { backFocus.requestFocus() }
    }

    LaunchedEffect(secUid, attempt) {
        loading = true
        failure = null
        moreError = null
        profileError = null
        works.clear()
        hasMore = false
        cursor = 0L
        val api = AppRuntime.api
        if (api == null) {
            loading = false
            failure = LoadState.Failed(false, "服务未就绪", "API 运行时尚未初始化，请重启应用后重试。")
            return@LaunchedEffect
        }
        if (secUid.isBlank()) {
            loading = false
            failure = LoadState.Failed(false, "作者信息不完整", "该视频没有返回作者 id，无法打开作者频道。")
            return@LaunchedEffect
        }

        // 资料失败不折叠整页：作品列表仍可展示，标题用作品自带的作者信息兜底。
        val loadedProfile = withContext(Dispatchers.IO) {
            try {
                val response = AuthorRepository.fetchProfile(secUid)
                val status = statusFailure(response.statusCode(), "作者主页")
                if (status != null) {
                    null to status.detail
                } else {
                    response.user() to null
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                null to error.toFailure("作者主页").detail
            }
        }
        profile = loadedProfile.first
        profileError = loadedProfile.second

        val page: LoadState<UserWorksResponse> = withContext(Dispatchers.IO) {
            try {
                val response = AuthorRepository.fetchPage(secUid, 0L)
                statusFailure(response.statusCode(), "作者作品") ?: LoadState.Ready(response)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                error.toFailure("作者作品")
            }
        }
        when (page) {
            is LoadState.Ready -> {
                val loaded = page.value.works()
                works.addAll(loaded)
                cursor = page.value.maxCursor()
                hasMore = page.value.hasMore()
                AuthorRepository.cacheFirst(secUid, loaded, cursor, hasMore)
            }

            is LoadState.Failed -> failure = page
            LoadState.Idle, LoadState.Loading -> Unit
        }
        loading = false
    }

    // 数据到位后：优先恢复上次停留的作品，否则把焦点交给「播放全部」。
    LaunchedEffect(loading, works.size, attempt) {
        if (loading || focusRestored) return@LaunchedEffect
        if (works.isEmpty()) {
            if (failure == null) {
                withFrameNanos { }
                runCatching { backFocus.requestFocus() }
            }
            return@LaunchedEffect
        }
        val remembered = FocusMemory.last(focusScope)?.toIntOrNull()
        if (remembered != null) {
            val index = remembered.coerceIn(0, works.lastIndex)
            // 列表前有两个头部 item：返回按钮、作者资料头。
            runCatching { listState.scrollToItem(index + 2) }
            withFrameNanos { }
            withFrameNanos { }
            runCatching { rowRequesters[works[index].awemeId()]?.requestFocus() }
        } else {
            withFrameNanos { }
            runCatching { playAllFocus.requestFocus() }
        }
        focusRestored = true
    }

    val loadMore: () -> Unit = {
        if (!loadingMore && hasMore && !loading) {
            loadingMore = true
            moreError = null
            scope.launch {
                try {
                    val response = withContext(Dispatchers.IO) {
                        AuthorRepository.fetchPage(secUid, cursor)
                    }
                    if (response.statusCode() != 0) {
                        moreError = "加载更多失败（status_code=${response.statusCode()}）"
                    } else {
                        val known = works.mapNotNullTo(HashSet()) {
                            it.awemeId().takeIf(String::isNotEmpty)
                        }
                        val fresh = response.works().filter { aweme ->
                            val id = aweme.awemeId()
                            id.isNotEmpty() && known.add(id)
                        }
                        works.addAll(fresh)
                        cursor = response.maxCursor()
                        hasMore = response.hasMore() && fresh.isNotEmpty()
                        AuthorRepository.cacheMore(secUid, works.toList(), cursor, hasMore)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Throwable) {
                    moreError = error.toFailure("作者作品").detail
                } finally {
                    loadingMore = false
                }
            }
        }
    }

    val displayAuthor = profile ?: works.firstOrNull()?.author()
    val displayName = displayAuthor?.nickname()?.takeIf { it.isNotBlank() }
        ?: nickname.takeIf { it.isNotBlank() }
        ?: "该发布者"

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 40.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { BackButton(onBack = onBack, modifier = Modifier.focusRequester(backFocus)) }

        item {
            AuthorHeader(
                author = displayAuthor,
                fallbackName = displayName,
                loadedCount = works.size,
                playAllFocus = playAllFocus,
                playEnabled = !loading && works.isNotEmpty(),
                onPlayAll = {
                    val firstId = works.firstOrNull()?.awemeId().orEmpty()
                    if (firstId.isNotBlank()) onPlay(firstId)
                },
            )
        }

        when {
            loading -> item { LoadingPane() }

            failure != null -> item {
                StatusPane(
                    failed = failure!!,
                    onAction = { attempt += 1 },
                    onLogin = onLogin,
                )
            }

            works.isEmpty() -> item { EmptyPane("该作者还没有发布作品") }

            else -> {
                items(
                    count = works.size,
                    key = { index -> "author-${works[index].awemeId()}#$index" },
                ) { index ->
                    val aweme = works[index]
                    val id = aweme.awemeId()
                    val requester = remember(id) { FocusRequester() }
                    rowRequesters[id] = requester
                    AwemeRow(
                        aweme = aweme,
                        onClick = { if (it.awemeId().isNotBlank()) onPlay(it.awemeId()) },
                        modifier = Modifier.focusRequester(requester),
                        onFocused = { FocusMemory.remember(focusScope, index.toString()) },
                    )
                }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (hasMore) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                ActionButton(
                                    label = if (loadingMore) "加载中…" else "加载更多作品",
                                    onClick = loadMore,
                                )
                                Text(
                                    text = "已加载 ${works.size} 条；播放时会在队尾自动续页。",
                                    color = AxTvColors.TextSecondary,
                                    style = TextStyle(fontSize = 12.sp),
                                )
                            }
                        } else {
                            Text(
                                text = "已加载全部 ${works.size} 条作品。",
                                color = AxTvColors.TextSecondary,
                                style = TextStyle(fontSize = 12.sp),
                            )
                        }
                        moreError?.let { message ->
                            Text(
                                text = message,
                                color = AxTvColors.AccentWarm,
                                style = TextStyle(fontSize = 12.sp),
                            )
                        }
                        profileError?.let { message ->
                            Text(
                                text = "作者资料加载失败：$message",
                                color = AxTvColors.TextSecondary,
                                style = TextStyle(fontSize = 12.sp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 作者资料头 + 「播放全部」入口。 */
@Composable
private fun AuthorHeader(
    author: Author?,
    fallbackName: String,
    loadedCount: Int,
    playAllFocus: FocusRequester,
    playEnabled: Boolean,
    onPlayAll: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            if (author != null) {
                UserAvatar(author = author, size = 88.dp)
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = fallbackName,
                    color = AxTvColors.TextPrimary,
                    style = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (author != null) {
                    if (author.signature().isNotBlank()) {
                        Text(
                            text = author.signature(),
                            color = AxTvColors.TextSecondary,
                            style = TextStyle(fontSize = 14.sp),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        text = "关注 ${DataModels.formatCount(author.followingCount())}" +
                            " · 粉丝 ${DataModels.formatCount(author.followerCount())}" +
                            " · 作品 ${DataModels.formatCount(author.awemeCount())}",
                        color = AxTvColors.TextSecondary,
                        style = TextStyle(fontSize = 13.sp),
                    )
                } else {
                    Text(
                        text = "作者频道",
                        color = AxTvColors.TextSecondary,
                        style = TextStyle(fontSize = 13.sp),
                    )
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (playEnabled) {
                ActionButton(
                    label = "播放全部",
                    onClick = onPlayAll,
                    modifier = Modifier.focusRequester(playAllFocus),
                )
            }
            Text(
                text = "按列表顺序连续播放该作者的作品，播完自动加载下一页。",
                color = AxTvColors.TextSecondary,
                style = TextStyle(fontSize = 12.sp),
            )
        }

        if (loadedCount > 0) {
            Text(
                text = "作品列表（已加载 $loadedCount 条）",
                color = AxTvColors.TextPrimary,
                style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
            )
        }
    }
}
