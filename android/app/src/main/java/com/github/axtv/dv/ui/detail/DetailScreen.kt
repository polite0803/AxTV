/*
 * 视频详情页（信息展示 + 点赞/收藏/不喜欢 + 评论楼中楼）。
 *
 * 数据来源：
 * - 详情 / 相关推荐：axtv.api.VideoApi.detail / related（refactor/api/axtv/api/VideoApi.java:34-58）
 * - 点赞 / 收藏 / 不喜欢 / 评论 / 回复：axtv.api.InteractionApi（refactor/api/axtv/api/InteractionApi.java:32-92）
 *
 * 文案与结构依据：
 * - refactor/ui/axtv/ui/DetailPageSpec.java：标题兜底、封面 URL、时长、空态文案、分隔符
 * - refactor/ui/axtv/ui/InteractionBarSpec.java：点赞/收藏文案与计数、静态统计行顺序
 * - refactor/ui/axtv/ui/CommentUiSpec.java：昵称兜底、评论计数、楼中楼展开与 LazyList key
 * - refactor/ui/axtv/ui/FeedPageSpec.java:51："相关推荐"标题
 *
 * 弹幕不在本页拉取：播放器侧由 ui/player/DanmakuInjection.kt 的 loadDanmakuComments 负责，
 * 通过 PlayerOverlayScreen(danmakuCommentsLoader = ...) 注入 DanmakuOverlayHost。
 *
 * 相关推荐点击语义（取证结论：同页切换到该条详情，不新开播放页/不新建详情栈）：
 * - src/app/p000/ml4.java:112-123（Modifier 点击分支 byte=3）：无合集信息时回调
 *   `tm1.invoke(aweme)`，携带整条 Aweme；有合集信息时回调
 *   `in1.invoke(list, aweme, pagination)`，即在当前页内换集。
 * - src/app/p000/on5.java:281：网格项用上面这条点击修饰器；同一张卡在
 *   on5.java:10200-10204 按「正在播放 / 立即播放 / 相关推荐」切换前缀，说明选中项
 *   就是当前展示项，属于页内替换。
 * - 未找到任何从相关卡跳到播放页/新建详情页的调用点，故实现为替换 activeAwemeId。
 */
package com.github.axtv.dv.ui.detail

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.foundation.ExperimentalTvFoundationApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.tv.material3.Text
import axtv.api.ApiException
import axtv.api.InteractionApi
import axtv.api.TicketGuardException
import axtv.api.VideoApi
import axtv.model.Author
import axtv.model.Aweme
import axtv.model.Comment
import axtv.model.DataModels
import axtv.model.InteractionMutationResponse
import axtv.ui.CommentUiSpec
import axtv.ui.DateLabels
import axtv.ui.DetailPageSpec
import axtv.ui.FeedPageSpec
import axtv.ui.InteractionBarSpec
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import com.github.axtv.dv.adapter.AppRuntime
import com.github.axtv.dv.player.Media3Engine
import com.github.axtv.dv.ui.AxTvColors
import com.github.axtv.dv.ui.appearance.AmbientBackdrop
import com.github.axtv.dv.ui.appearance.AxAppearance
import com.github.axtv.dv.ui.focusRing
import com.github.axtv.dv.ui.nav.PlaybackUrlResolver
import com.github.axtv.dv.ui.settings.InteractionCredentialQrDialog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.HashMap

/** 详情页加载状态。 */
private sealed interface DetailUiState {
    object Loading : DetailUiState
    data class Failed(val message: String) : DetailUiState
    data class Ready(val aweme: Aweme) : DetailUiState
}

/** 评论列表展平后的一行（顶层评论或内嵌回复）。 */
private data class DetailCommentRow(val key: String, val comment: Comment, val isReply: Boolean)

private const val RELATED_COUNT = 20
private const val COMMENT_PAGE_SIZE = 20
private const val REPLY_PAGE_SIZE = 20

/** 当前条目在连播队列中的下标；不在队列里返回 -1（与原实现一致：未入队时无上一条/下一条）。 */
private fun queueIndexFor(awemeId: String, queueIds: List<String>): Int = queueIds.indexOf(awemeId)

@OptIn(ExperimentalTvFoundationApi::class)
@Composable
fun DetailScreen(
    awemeId: String,
    onBack: () -> Unit,
    /** 全屏播放；queueIndex 非空表示该条在搜索/合集队列中的下标，播放器可据此连播。 */
    onPlay: (awemeId: String, queueIndex: Int?) -> Unit,
    /** 当前详情所属的连播队列（搜索结果为搜索列表顺序）；为空时隐藏上一条/下一条。 */
    queueIds: List<String> = emptyList(),
    /** 进入发布者频道（作者频道页）；参数为 author.sec_uid。 */
    onOpenAuthor: (String) -> Unit = {},
) {
    val douyinApi = AppRuntime.api
    val videoApi = remember(douyinApi) { douyinApi?.let { VideoApi(it) } }
    val interactionApi = remember(douyinApi) { douyinApi?.let { InteractionApi(it) } }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val playRequester = remember { FocusRequester() }
    val relatedFirstRequester = remember { FocusRequester() }
    /** 「下一条」按钮的焦点请求器：切条后自动把焦点留在操作行，避免遥控器下一次按键落空。 */
    val nextRequester = remember { FocusRequester() }
    /** 首次进入只聚焦一次「全屏播放」；相关推荐切条时焦点改回相关卡，不再甩回顶部。 */
    var initialFocusGiven by remember { mutableStateOf(false) }
    var focusRelatedOnNextReady by remember { mutableStateOf(false) }

    // 相关推荐点击 = 同页切换到该条详情（取证见文件头「相关推荐点击语义」）。
    // activeAwemeId 是当前展示条目的 id：切换时所有分区状态按新 id 重置并重新拉取。
    // rememberSaveable：经播放页返回时保留换集后的 id，不回退到路由原始条目。
    var activeAwemeId by rememberSaveable(awemeId) { mutableStateOf(awemeId) }

    var state by remember(activeAwemeId) { mutableStateOf<DetailUiState>(DetailUiState.Loading) }
    var detailAttempt by remember(activeAwemeId) { mutableStateOf(0) }

    var related by remember(activeAwemeId) { mutableStateOf(emptyList<Aweme>()) }
    var relatedLoading by remember(activeAwemeId) { mutableStateOf(false) }
    var relatedError by remember(activeAwemeId) { mutableStateOf<String?>(null) }
    /** 相关推荐已经为哪个条目拉取完成（用于切条后等新列表就绪再落焦）。 */
    var relatedLoadedForId by remember(activeAwemeId) { mutableStateOf<String?>(null) }

    var comments by remember(activeAwemeId) { mutableStateOf(emptyList<Comment>()) }
    var commentsCursor by remember(activeAwemeId) { mutableStateOf(0L) }
    var commentsHasMore by remember(activeAwemeId) { mutableStateOf(false) }
    var commentsTotal by remember(activeAwemeId) { mutableStateOf(0L) }
    var commentsLoading by remember(activeAwemeId) { mutableStateOf(false) }
    var commentsError by remember(activeAwemeId) { mutableStateOf<String?>(null) }
    var commentsAttempt by remember(activeAwemeId) { mutableStateOf(0) }
    var expandedCid by remember(activeAwemeId) { mutableStateOf<String?>(null) }
    var repliesByCid by remember(activeAwemeId) { mutableStateOf<Map<String, List<Comment>>>(emptyMap()) }
    var replyLoadingCid by remember(activeAwemeId) { mutableStateOf<String?>(null) }

    var digged by remember(activeAwemeId) { mutableStateOf(false) }
    var diggCount by remember(activeAwemeId) { mutableStateOf(0L) }
    var busyDigg by remember(activeAwemeId) { mutableStateOf(false) }
    var collected by remember(activeAwemeId) { mutableStateOf(false) }
    var collectCount by remember(activeAwemeId) { mutableStateOf(0L) }
    var busyCollect by remember(activeAwemeId) { mutableStateOf(false) }
    var disliked by remember(activeAwemeId) { mutableStateOf(false) }
    var busyDislike by remember(activeAwemeId) { mutableStateOf(false) }
    var notice by remember(activeAwemeId) { mutableStateOf<String?>(null) }
    /** 点赞/收藏因缺少互动凭据失败：直接弹手机扫码补充，而不是只闪一句提示。 */
    var credentialHelp by remember { mutableStateOf(false) }
    /** 详情页内的小窗播放：不离开本页，直接在封面上方播放。 */
    var inlinePlaying by remember { mutableStateOf(false) }

    // 详情：同时把互动初始状态/计数落到本地，后续操作做局部更新。
    LaunchedEffect(activeAwemeId, videoApi, detailAttempt) {
        state = DetailUiState.Loading
        if (videoApi == null) {
            state = DetailUiState.Failed("应用尚未初始化，无法加载详情")
            return@LaunchedEffect
        }
        try {
            val detail = withContext(Dispatchers.IO) { videoApi.detail(activeAwemeId) }
            val aweme = detail.awemeDetail()
            if (aweme == null) {
                state = DetailUiState.Failed(DetailPageSpec.TEXT_NO_PLAYABLE)
            } else {
                digged = aweme.isDigged()
                collected = aweme.isCollected()
                diggCount = aweme.statistics().diggCount()
                collectCount = aweme.statistics().collectCount()
                state = DetailUiState.Ready(aweme)
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            state = DetailUiState.Failed(e.message ?: "详情加载失败")
        }
    }

    LaunchedEffect(activeAwemeId, videoApi) {
        if (videoApi == null) return@LaunchedEffect
        val id = activeAwemeId
        relatedLoading = true
        relatedError = null
        try {
            related = withContext(Dispatchers.IO) {
                videoApi.related(id, RELATED_COUNT, 0).flatAwemes()
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            relatedError = e.message ?: "相关推荐加载失败"
        } finally {
            relatedLoading = false
            relatedLoadedForId = id
        }
    }

    LaunchedEffect(activeAwemeId, interactionApi, commentsAttempt) {
        if (interactionApi == null) {
            commentsError = "应用尚未初始化，无法加载评论"
            return@LaunchedEffect
        }
        commentsLoading = true
        commentsError = null
        try {
            val response = withContext(Dispatchers.IO) {
                interactionApi.comments(activeAwemeId, 0L, COMMENT_PAGE_SIZE)
            }
            comments = response.comments().orEmpty()
            commentsCursor = response.cursor()
            commentsHasMore = response.hasMorePages()
            commentsTotal = response.total()
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            commentsError = e.message ?: "评论加载失败"
        } finally {
            commentsLoading = false
        }
    }

    // 提示条 3 秒自动消失。
    LaunchedEffect(notice) {
        val current = notice ?: return@LaunchedEffect
        delay(3_000)
        if (notice == current) notice = null
    }

    // 首屏把焦点给「全屏播放」，避免 TV 遥控器首次按键落空；只做一次。
    LaunchedEffect(state) {
        if (state is DetailUiState.Ready && !initialFocusGiven) {
            withFrameNanos { }
            runCatching { playRequester.requestFocus() }
            initialFocusGiven = true
        }
    }

    // 点击相关推荐后：等新详情与新相关列表都就绪，把焦点留在相关区（没有相关项才回播放按钮）。
    LaunchedEffect(
        focusRelatedOnNextReady,
        activeAwemeId,
        relatedLoadedForId,
        related.size,
        state,
    ) {
        if (!focusRelatedOnNextReady || state !is DetailUiState.Ready ||
            relatedLoadedForId != activeAwemeId
        ) {
            return@LaunchedEffect
        }
        withFrameNanos { }
        withFrameNanos { }
        if (related.isNotEmpty()) {
            runCatching { relatedFirstRequester.requestFocus() }
        } else {
            runCatching { playRequester.requestFocus() }
        }
        focusRelatedOnNextReady = false
    }

    /**
     * 「下一条」/「上一条」切条后焦点固定在操作行：不甩回顶部、也不留在已消失的相关卡上。
     *
     * 详情与相关推荐的拉取都以 activeAwemeId 为 key 重建协程，切条后 state 会先回到 Loading，
     * 本 effect 在 Ready 且队列仍有效时把焦点交给 nextRequester（下一条按钮）；
     * 队列已耗尽（下一条为 null）时退回 playRequester。
     */
    LaunchedEffect(activeAwemeId, state, queueIds, queueIndexFor(activeAwemeId, queueIds)) {
        val s = state
        if (s !is DetailUiState.Ready) return@LaunchedEffect
        // 首屏让位给「全屏播放」首焦 effect；本 effect 只负责切条后的焦点安置，
        // 否则从搜索进详情时后声明的它会把首焦抢到「下一条」。
        if (!initialFocusGiven) return@LaunchedEffect
        val idx = queueIndexFor(s.aweme.awemeId(), queueIds)
        val nextId = queueIds.getOrNull(idx + 1)
        withFrameNanos { }
        if (nextId != null) {
            runCatching { nextRequester.requestFocus() }
        } else {
            runCatching { playRequester.requestFocus() }
        }
    }

    /**
     * 互动写接口的统一收口：先乐观更新，请求失败或业务码非 0 时回滚并提示。
     * status_code == 0 视为成功；is_digg / is_collect 为 null 表示接口没回状态，保留乐观值。
     */
    suspend fun mutate(
        request: suspend () -> InteractionMutationResponse,
        optimistic: () -> Unit,
        rollback: () -> Unit,
        apply: (InteractionMutationResponse) -> Unit,
    ) {
        optimistic()
        try {
            val result = withContext(Dispatchers.IO) { request() }
            if (result.statusCode() != 0) {
                rollback()
                notice = result.statusMsg().ifBlank { "操作失败（${result.statusCode()}）" }
                return
            }
            apply(result)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            rollback()
            notice = e.message ?: "操作失败"
            if ((e is ApiException && e.kind() == ApiException.Kind.MISSING_CREDENTIAL) ||
                e is TicketGuardException
            ) {
                credentialHelp = true
            }
        }
    }

    fun toggleDigg() {
        if (busyDigg) return
        val api = interactionApi
        if (api == null) {
            notice = "应用尚未初始化，无法点赞"
            return
        }
        val target = !digged
        val previousDigged = digged
        val previousCount = diggCount
        busyDigg = true
        scope.launch {
            try {
                mutate(
                    request = { api.digg(activeAwemeId, if (target) 1 else 0, "0") },
                    optimistic = {
                        digged = target
                        diggCount = (diggCount + if (target) 1L else -1L).coerceAtLeast(0L)
                    },
                    rollback = {
                        digged = previousDigged
                        diggCount = previousCount
                    },
                    apply = { result ->
                        val flag = result.isDigg()
                        if (flag != null) digged = flag != 0
                    },
                )
            } finally {
                busyDigg = false
            }
        }
    }

    fun toggleCollect() {
        if (busyCollect) return
        val api = interactionApi
        if (api == null) {
            notice = "应用尚未初始化，无法收藏"
            return
        }
        val target = !collected
        val previousCollected = collected
        val previousCount = collectCount
        busyCollect = true
        scope.launch {
            try {
                mutate(
                    request = { api.collect(activeAwemeId, if (target) 1 else 0) },
                    optimistic = {
                        collected = target
                        collectCount = (collectCount + if (target) 1L else -1L).coerceAtLeast(0L)
                    },
                    rollback = {
                        collected = previousCollected
                        collectCount = previousCount
                    },
                    apply = { result ->
                        val flag = result.isCollect()
                        if (flag != null) collected = flag != 0
                    },
                )
            } finally {
                busyCollect = false
            }
        }
    }

    fun markDisliked() {
        if (busyDislike) return
        if (disliked) {
            notice = "已标记为不喜欢"
            return
        }
        val api = interactionApi
        if (api == null) {
            notice = "应用尚未初始化，无法标记"
            return
        }
        busyDislike = true
        scope.launch {
            try {
                mutate(
                    request = { api.dislike(activeAwemeId) },
                    optimistic = { disliked = true },
                    rollback = { disliked = false },
                    apply = { notice = "已标记为不喜欢" },
                )
            } finally {
                busyDislike = false
            }
        }
    }

    fun loadMoreComments() {
        if (commentsLoading || !commentsHasMore) return
        val api = interactionApi
        if (api == null) {
            notice = "应用尚未初始化，无法加载评论"
            return
        }
        commentsLoading = true
        scope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    api.comments(activeAwemeId, commentsCursor, COMMENT_PAGE_SIZE)
                }
                comments = comments + response.comments().orEmpty()
                commentsCursor = response.cursor()
                commentsHasMore = response.hasMorePages()
                commentsTotal = response.total()
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                commentsError = e.message ?: "加载更多评论失败"
            } finally {
                commentsLoading = false
            }
        }
    }

    fun toggleReplies(comment: Comment) {
        val cid = comment.cid()
        if (expandedCid == cid) {
            expandedCid = null
            return
        }
        if (cid.isBlank()) {
            notice = "该评论缺少 id，无法展开回复"
            return
        }
        expandedCid = cid
        if (repliesByCid.containsKey(cid)) return
        val inline = comment.replyComments()
        if (!inline.isNullOrEmpty()) {
            repliesByCid = repliesByCid + (cid to inline)
            return
        }
        val api = interactionApi ?: return
        replyLoadingCid = cid
        scope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    api.replies(activeAwemeId, cid, 0L, REPLY_PAGE_SIZE)
                }
                repliesByCid = repliesByCid + (cid to response.comments().orEmpty())
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                notice = e.message ?: "加载回复失败"
            } finally {
                replyLoadingCid = null
            }
        }
    }

    BackHandler(onBack = onBack)

    // 氛围背景 / 画面取色的详情页宿主：当前条目的封面（与首页口径一致）。
    // version 进 key：切到画面取色（或改其它外观项）后本页也会重取一次。
    val ambientCoverUrl = (state as? DetailUiState.Ready)
        ?.let { DetailPageSpec.coverUrl(it.aweme.video()) }
        .orEmpty()
    LaunchedEffect(ambientCoverUrl, AxAppearance.version.intValue) {
        AxAppearance.sampleCoverSeed(context, ambientCoverUrl)
    }

    Box(Modifier.fillMaxSize().background(AxTvColors.Bg)) {
        AmbientBackdrop(ambientCoverUrl)
        when (val current = state) {
            DetailUiState.Loading -> DetailStateMessage(title = "正在加载详情…")

            is DetailUiState.Failed -> DetailStateMessage(
                title = current.message,
                actionLabel = "重试",
                onAction = { detailAttempt++ },
                secondaryLabel = "返回",
                onSecondary = onBack,
            )

            is DetailUiState.Ready -> {
                val aweme = current.aweme
                val video = aweme.video()
                val author = aweme.author()
                val statistics = aweme.statistics()
                val playTarget = aweme.awemeId().ifBlank { activeAwemeId }
                val coverUrl = DetailPageSpec.coverUrl(video)
                val durationText = DetailPageSpec.durationText(video.duration().toLong())
                val dateText = DateLabels.medium(aweme.createTime())
                val metaLine = listOfNotNull(durationText, dateText).joinToString(DetailPageSpec.TEXT_DOT)
                val title = DetailPageSpec.cardTitle(aweme.desc(), author.nickname())
                val authorName = CommentUiSpec.displayName(author.nickname())
                val authorAvatar = authorAvatarUrl(author)

                // 展平可见评论：顶层全部展示；仅当前展开的评论才展开其回复。
                val rows = remember(comments, expandedCid, repliesByCid) {
                    val counter = HashMap<String, Int>()
                    buildList {
                        comments.forEach { comment ->
                            val topKey = CommentUiSpec.lazyKey(CommentUiSpec.TOP_PREFIX, comment, counter)
                            add(DetailCommentRow(topKey, comment, isReply = false))
                            if (expandedCid == comment.cid()) {
                                val replies = repliesByCid[comment.cid()]
                                    ?: comment.replyComments().orEmpty()
                                val prefix = CommentUiSpec.replyPrefix(topKey)
                                replies.forEach { reply ->
                                    add(DetailCommentRow(
                                        key = CommentUiSpec.lazyKey(prefix, reply, counter),
                                        comment = reply,
                                        isReply = true,
                                    ))
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 28.dp),
                    horizontalArrangement = Arrangement.spacedBy(32.dp),
                ) {
                    // 左列：视频小窗/主操作/统计/相关推荐；右列评论区收窄，给画面让空间。
                    Column(
                        modifier = Modifier.weight(1.2f).fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        val queueIndex = queueIds.indexOf(playTarget)
                        val prevId = queueIds.getOrNull(queueIndex - 1)
                        val nextId = queueIds.getOrNull(queueIndex + 1)

                        Box(Modifier.fillMaxWidth().height(360.dp)) {
                            if (inlinePlaying) {
                                DetailInlinePlayer(
                                    aweme = aweme,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            } else {
                                DetailRemoteImage(
                                    url = coverUrl,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    errorLabel = "封面加载失败",
                                )
                            }
                        }

                        // 六个操作只占一排：使用紧凑内边距，搜索结果来源也不换行。
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DetailButton(
                                label = "全屏播放",
                                compact = true,
                                active = true,
                                modifier = Modifier.focusRequester(playRequester),
                                onClick = {
                                    onPlay(playTarget, queueIndex.takeIf { it >= 0 })
                                },
                            )
                            DetailButton(
                                label = if (inlinePlaying) "停止小窗" else "小窗播放",
                                compact = true,
                                active = inlinePlaying,
                                onClick = { inlinePlaying = !inlinePlaying },
                            )
                            if (queueIndex >= 0) {
                                DetailButton(
                                    label = "上一条",
                                    compact = true,
                                    enabled = prevId != null,
                                    onClick = { prevId?.let { activeAwemeId = it } },
                                )
                                DetailButton(
                                    label = "下一条",
                                    compact = true,
                                    enabled = nextId != null,
                                    modifier = Modifier.focusRequester(nextRequester),
                                    onClick = { nextId?.let { activeAwemeId = it } },
                                )
                            }
                            DetailButton(label = "返回", compact = true, onClick = onBack)
                            DetailButton(
                                label = if (disliked) "已标记不喜欢" else "不喜欢",
                                compact = true,
                                enabled = !busyDislike,
                                onClick = ::markDisliked,
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(26.dp)) {
                            // 「播放」文案无原版证据（原版详情页未见该行），属实现补充；
                            // 点赞 / 评论沿用 InteractionBarSpec 的恒定标签。
                            //
                            // 原版回退：src/app/p000/mw3.java:614-618 —— playCount <= 0 时改取 diggCount，
                            // 因为服务端对未登录/未统计播放的条目常常不回 play_count，硬显示 0 会误导。
                            val playCount = if (statistics.playCount() > 0) statistics.playCount() else statistics.diggCount()
                            DetailStat("播放", DataModels.formatCount(playCount))
                            DetailStat(InteractionBarSpec.LABEL_LIKE, DataModels.formatCount(diggCount))
                            DetailStat(InteractionBarSpec.LABEL_COMMENT, DataModels.formatCount(statistics.commentCount()))
                        }

                        Text(
                            text = FeedPageSpec.TEXT_RELATED,
                            color = AxTvColors.TextPrimary,
                            style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                        )
                        when {
                            relatedLoading && related.isEmpty() -> HintText("正在加载相关推荐…")
                            relatedError != null && related.isEmpty() -> HintText(relatedError!!)
                            related.isEmpty() -> HintText("暂无相关推荐")
                            else -> LazyRow(
                                modifier = Modifier.fillMaxWidth().height(168.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                items(count = related.size, key = { index -> "related-$index" }) { index ->
                                    val item = related[index]
                                    RelatedCard(
                                        item = item,
                                        modifier = if (index == 0) {
                                            Modifier.focusRequester(relatedFirstRequester)
                                        } else {
                                            Modifier
                                        },
                                        // 原版是「同页切换到该条详情」而非直接播放：
                                        // 点击只替换当前详情，播放仍由页面上的「播放」按钮触发。
                                        onClick = {
                                            val next = item.awemeId()
                                            if (next.isNotBlank() && next != activeAwemeId) {
                                                focusRelatedOnNextReady = true
                                                activeAwemeId = next
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }

                    // 右列：标题、作者、简介、互动按钮、评论
                    Column(
                        modifier = Modifier.weight(0.8f).fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = title,
                            color = AxTvColors.TextPrimary,
                            style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )

                        // 作者行 = 「进入发布者频道」入口：可聚焦点击，落地后可从「播放全部」连续看完其作品。
                        val authorSecUid = author.secUid()
                        var authorFocused by remember(authorSecUid) { mutableStateOf(false) }
                        Row(
                            modifier = Modifier
                                .then(
                                    if (authorSecUid.isNotBlank()) {
                                        Modifier
                                            .onFocusChanged { authorFocused = it.isFocused }
                                            .clickable { onOpenAuthor(authorSecUid) }
                                            .background(
                                                if (authorFocused) AxTvColors.SurfaceHigh
                                                else Color.Transparent,
                                                RoundedCornerShape(10.dp),
                                            )
                                            .focusRing(authorFocused, RoundedCornerShape(10.dp))
                                    } else {
                                        Modifier
                                    },
                                )
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            DetailRemoteImage(
                                url = authorAvatar,
                                contentDescription = null,
                                modifier = Modifier.size(34.dp),
                                shape = CircleShape,
                            )
                            Text(
                                text = authorName,
                                color = AxTvColors.TextPrimary,
                                style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
                            )
                            if (authorSecUid.isNotBlank()) {
                                Text(
                                    text = "进入频道 ›",
                                    color = AxTvColors.Accent,
                                    style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                                )
                            }
                            if (author.signature().isNotBlank()) {
                                Text(
                                    text = CommentUiSpec.SEPARATOR + author.signature(),
                                    color = AxTvColors.TextSecondary,
                                    style = TextStyle(fontSize = 12.sp),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }

                        if (metaLine.isNotEmpty()) {
                            HintText(metaLine)
                        }
                        // 『进详情页显示简介』的消费点：关闭时只显示标题/作者/元信息，不铺简介正文。
                        if (aweme.desc().isNotBlank() && AppRuntime.settings?.showDetailsOnStart() != false) {
                            Text(
                                text = aweme.desc(),
                                color = AxTvColors.TextSecondary,
                                style = TextStyle(fontSize = 13.sp),
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            val like = InteractionBarSpec.like(digged, diggCount)
                            DetailButton(
                                label = like.label(),
                                count = like.count(),
                                active = like.active(),
                                enabled = !busyDigg,
                                onClick = ::toggleDigg,
                            )
                            val collect = InteractionBarSpec.collect(collected, collectCount)
                            DetailButton(
                                label = collect.label(),
                                count = collect.count(),
                                active = collect.active(),
                                enabled = !busyCollect,
                                onClick = ::toggleCollect,
                            )
                        }

                        Box(Modifier.fillMaxWidth().height(1.dp).background(AxTvColors.Divider))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = CommentUiSpec.HEADER_LABEL,
                                color = AxTvColors.TextPrimary,
                                style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                            )
                            CommentUiSpec.headerCount(commentsTotal)?.let { count ->
                                Text(
                                    text = "  $count",
                                    color = AxTvColors.TextSecondary,
                                    style = TextStyle(fontSize = 12.sp),
                                )
                            }
                        }

                        if (commentsLoading && comments.isEmpty()) {
                            HintText("评论加载中…")
                        }
                        if (commentsError != null) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                HintText(commentsError!!)
                                DetailButton(label = "重试", onClick = { commentsAttempt++ })
                            }
                        }

                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(count = rows.size, key = { index -> rows[index].key }) { index ->
                                val row = rows[index]
                                CommentItem(
                                    comment = row.comment,
                                    isReply = row.isReply,
                                    expanded = expandedCid == row.comment.cid(),
                                    loadingReplies = replyLoadingCid == row.comment.cid(),
                                    onToggleReplies = {
                                        if (!row.isReply) toggleReplies(row.comment)
                                    },
                                )
                            }
                        }

                        if (comments.isEmpty() && !commentsLoading && commentsError == null) {
                            HintText("暂无评论")
                        }
                        if (commentsHasMore) {
                            DetailButton(
                                label = "加载更多评论",
                                enabled = !commentsLoading,
                                onClick = ::loadMoreComments,
                            )
                        }
                    }
                }
            }
        }

        notice?.let { message ->
            Text(
                text = message,
                color = AxTvColors.TextPrimary,
                style = TextStyle(fontSize = 14.sp),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 28.dp)
                    .background(AxTvColors.SurfaceHigh, RoundedCornerShape(10.dp))
                    .padding(horizontal = 20.dp, vertical = 10.dp),
            )
        }

        if (credentialHelp) {
            InteractionCredentialQrDialog(onDismiss = { credentialHelp = false })
        }
    }
}

/**
 * 详情页内的小窗播放器：只在封面区域播放，不接管全屏、不进导航。
 *
 * 使用独立的 Media3Engine（与全屏播放页的 PlayerSession 无关），
 * 离开本页/切换条目时立即释放，避免后台继续出声。
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun DetailInlinePlayer(
    aweme: Aweme,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val engine = remember(aweme.awemeId()) { Media3Engine(context.applicationContext) }
    val lifecycleOwner = context as? LifecycleOwner

    LaunchedEffect(engine, aweme.awemeId()) {
        val url = PlaybackUrlResolver.playableUrl(aweme)
        if (url.isBlank()) return@LaunchedEffect
        engine.stopAndClear()
        engine.setMediaItem(url, false)
        engine.prepare()
        engine.play()
    }

    DisposableEffect(lifecycleOwner, engine) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> engine.pause()
                Lifecycle.Event.ON_START -> engine.play()
                else -> Unit
            }
        }
        lifecycleOwner?.lifecycle?.addObserver(observer)
        onDispose {
            lifecycleOwner?.lifecycle?.removeObserver(observer)
            engine.setVideoSurface(null)
            engine.release()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                setBackgroundColor(android.graphics.Color.BLACK)
                keepScreenOn = true
                engine.setVideoSurface(this)
            }
        },
    )
}
