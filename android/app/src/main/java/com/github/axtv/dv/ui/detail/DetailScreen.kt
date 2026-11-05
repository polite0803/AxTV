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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.foundation.ExperimentalTvFoundationApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.tv.material3.Text
import axtv.api.InteractionApi
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
import com.github.axtv.dv.ui.AxTvColors
import com.github.axtv.dv.ui.focusRing
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

@OptIn(ExperimentalTvFoundationApi::class)
@Composable
fun DetailScreen(awemeId: String, onBack: () -> Unit, onPlay: (String) -> Unit) {
    val douyinApi = AppRuntime.api
    val videoApi = remember(douyinApi) { douyinApi?.let { VideoApi(it) } }
    val interactionApi = remember(douyinApi) { douyinApi?.let { InteractionApi(it) } }
    val scope = rememberCoroutineScope()
    val playRequester = remember { FocusRequester() }

    // 相关推荐点击 = 同页切换到该条详情（取证见文件头「相关推荐点击语义」）。
    // activeAwemeId 是当前展示条目的 id：切换时所有分区状态按新 id 重置并重新拉取。
    var activeAwemeId by remember(awemeId) { mutableStateOf(awemeId) }

    var state by remember(activeAwemeId) { mutableStateOf<DetailUiState>(DetailUiState.Loading) }
    var detailAttempt by remember(activeAwemeId) { mutableStateOf(0) }

    var related by remember(activeAwemeId) { mutableStateOf(emptyList<Aweme>()) }
    var relatedLoading by remember(activeAwemeId) { mutableStateOf(false) }
    var relatedError by remember(activeAwemeId) { mutableStateOf<String?>(null) }

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
        relatedLoading = true
        relatedError = null
        try {
            related = withContext(Dispatchers.IO) {
                videoApi.related(activeAwemeId, RELATED_COUNT, 0).flatAwemes()
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            relatedError = e.message ?: "相关推荐加载失败"
        } finally {
            relatedLoading = false
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

    // 首屏把焦点给「播放」，避免 TV 遥控器首次按键落空。
    LaunchedEffect(state) {
        if (state is DetailUiState.Ready) {
            withFrameNanos { }
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

    Box(Modifier.fillMaxSize().background(AxTvColors.Bg)) {
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
                    // 左列：封面、主操作、统计、相关推荐
                    Column(
                        modifier = Modifier.weight(0.9f).fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        DetailRemoteImage(
                            url = coverUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxWidth().height(300.dp),
                            errorLabel = "封面加载失败",
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            DetailButton(
                                label = "播放",
                                active = true,
                                modifier = Modifier.focusRequester(playRequester),
                                onClick = { onPlay(playTarget) },
                            )
                            DetailButton(label = "返回", onClick = onBack)
                            DetailButton(
                                label = if (disliked) "已标记不喜欢" else "不喜欢",
                                enabled = !busyDislike,
                                onClick = ::markDisliked,
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(26.dp)) {
                            // 「播放」文案无原版证据（原版详情页未见该行），属实现补充；
                            // 点赞 / 评论沿用 InteractionBarSpec 的恒定标签。
                            DetailStat("播放", DataModels.formatCount(statistics.playCount()))
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
                                        // 原版是「同页切换到该条详情」而非直接播放：
                                        // 点击只替换当前详情，播放仍由页面上的「播放」按钮触发。
                                        onClick = {
                                            val next = item.awemeId()
                                            if (next.isNotBlank() && next != activeAwemeId) {
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
                        modifier = Modifier.weight(1.1f).fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = title,
                            color = AxTvColors.TextPrimary,
                            style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )

                        Row(
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
                        if (aweme.desc().isNotBlank()) {
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
    }
}

/** 作者头像：取缩略图首 URL，缺失时为空串（Avatar.firstUrl 已做空列表兜底）。 */
private fun authorAvatarUrl(author: Author): String = author.avatarThumb().firstUrl()

@Composable
private fun DetailStateMessage(
    title: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    Box(Modifier.fillMaxSize().padding(40.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(title, color = AxTvColors.TextPrimary, style = TextStyle(fontSize = 18.sp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (actionLabel != null && onAction != null) {
                    DetailButton(label = actionLabel, onClick = onAction)
                }
                if (secondaryLabel != null && onSecondary != null) {
                    DetailButton(label = secondaryLabel, onClick = onSecondary)
                }
            }
        }
    }
}

@Composable
private fun DetailButton(
    label: String,
    modifier: Modifier = Modifier,
    count: String? = null,
    active: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .focusRing(focused)
            .clip(RoundedCornerShape(12.dp))
            .background(if (active) AxTvColors.Accent.copy(alpha = 0.18f) else AxTvColors.Surface)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = label,
            color = if (active) AxTvColors.Accent else AxTvColors.TextPrimary,
            style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
        )
        if (count != null) {
            Text(
                text = count,
                color = AxTvColors.TextSecondary,
                style = TextStyle(fontSize = 12.sp),
            )
        }
    }
}

@Composable
private fun DetailStat(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = value,
            color = AxTvColors.TextPrimary,
            style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
        )
        Text(
            text = label,
            color = AxTvColors.TextSecondary,
            style = TextStyle(fontSize = 11.sp),
        )
    }
}

@Composable
private fun HintText(text: String) {
    Text(
        text = text,
        color = AxTvColors.TextSecondary,
        style = TextStyle(fontSize = 12.sp),
    )
}

/** 远程图片的加载状态（仅服务详情页占位/兜底）。 */
private enum class DetailImageState { Loading, Success, Error }

/**
 * 详情页图片统一入口：封面、作者头像、相关推荐封面都走这里。
 * 加载中与加载失败都退回 SurfaceHigh 圆角块——颜色/圆角与 ui/home 的卡片占位块一致，
 * 不引入新配色；仅在尺寸足够时叠一行 TextSecondary 小字区分「加载中 / 加载失败」。
 */
@Composable
private fun DetailRemoteImage(
    url: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(8.dp),
    errorLabel: String? = null,
) {
    var state by remember(url) {
        mutableStateOf(if (url.isEmpty()) DetailImageState.Error else DetailImageState.Loading)
    }
    Box(
        modifier = modifier.clip(shape).background(AxTvColors.SurfaceHigh),
        contentAlignment = Alignment.Center,
    ) {
        if (url.isNotEmpty()) {
            AsyncImage(
                model = url,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                onState = { painterState ->
                    state = when (painterState) {
                        is AsyncImagePainter.State.Success -> DetailImageState.Success
                        is AsyncImagePainter.State.Error -> DetailImageState.Error
                        else -> DetailImageState.Loading
                    }
                },
            )
        }
        if (state != DetailImageState.Success && errorLabel != null) {
            Text(
                text = if (state == DetailImageState.Loading) "加载中…" else errorLabel,
                color = AxTvColors.TextSecondary,
                style = TextStyle(fontSize = 12.sp),
            )
        }
    }
}

@Composable
private fun RelatedCard(item: Aweme, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val coverUrl = DetailPageSpec.coverUrl(item.video())
    Column(
        modifier = Modifier
            .width(176.dp)
            .onFocusChanged { focused = it.isFocused }
            .focusRing(focused)
            .clip(RoundedCornerShape(12.dp))
            .background(AxTvColors.Surface)
            .clickable(onClick = onClick)
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        DetailRemoteImage(
            url = coverUrl,
            contentDescription = null,
            modifier = Modifier.fillMaxWidth().height(96.dp),
            shape = RoundedCornerShape(8.dp),
            errorLabel = "封面加载失败",
        )
        Text(
            text = DetailPageSpec.cardTitle(item.desc(), item.author().nickname()),
            color = AxTvColors.TextPrimary,
            style = TextStyle(fontSize = 12.sp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CommentItem(
    comment: Comment,
    isReply: Boolean,
    expanded: Boolean,
    loadingReplies: Boolean,
    onToggleReplies: () -> Unit,
) {
    var focused by remember(comment.cid(), comment.text()) { mutableStateOf(false) }
    val diggLabel = CommentUiSpec.diggLabel(comment)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = if (isReply) 28.dp else 0.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (isReply) AxTvColors.Surface else AxTvColors.SurfaceHigh)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = CommentUiSpec.displayName(comment.user().nickname()),
            color = AxTvColors.Accent,
            style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
        )
        Text(
            text = comment.text(),
            color = AxTvColors.TextPrimary,
            style = TextStyle(fontSize = 13.sp),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (diggLabel != null) HintText("赞 $diggLabel")
            if (comment.ipLabel().isNotBlank()) HintText(comment.ipLabel())
            if (!isReply && comment.replyCommentTotal() > 0) {
                Text(
                    text = if (expanded) "收起回复" else "展开 ${comment.replyCommentTotal()} 条回复",
                    color = AxTvColors.Accent,
                    style = TextStyle(fontSize = 11.sp),
                    modifier = Modifier
                        .onFocusChanged { focused = it.isFocused }
                        .focusRing(focused, RoundedCornerShape(6.dp))
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(onClick = onToggleReplies)
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
            if (loadingReplies) HintText("正在加载回复…")
        }
    }
}
