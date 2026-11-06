/*
 * 线 E：历史 / 收藏 / 稍后再看。
 *
 * 三个页面共用同一套「用户作品列表」骨架：
 *  - 请求统一经 AppRuntime.api → axtv.api.VideoApi / UserApi，UI 不自建 HTTP 客户端；
 *  - 加载 / 失败（区分未登录）/ 空态三态齐全，失败不折叠为空列表；
 *  - 点击条目只回调调用方（历史/稍后再看 → onPlay，收藏 → onOpenDetail）；
 *    观看历史与播放埋点统一由播放器在真正起播时上报，见 ui/player/PlayerOverlayScreen。
 *
 * 固定对外的 Composable 签名：
 *   HistoryScreen(onBack, onPlay)
 *   FavoritesScreen(onBack, onOpenDetail)
 *   WatchLaterScreen(onBack, onPlay)
 */
package com.github.axtv.dv.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.foundation.ExperimentalTvFoundationApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.tv.material3.Text
import axtv.api.ApiException
import axtv.api.DouyinApi
import axtv.api.UserApi
import axtv.api.VideoApi
import axtv.model.Aweme
import axtv.model.UserWorksResponse
import axtv.ui.HistoryGroupSpec
import com.github.axtv.dv.adapter.AppRuntime
import com.github.axtv.dv.ui.AxTvColors
import com.github.axtv.dv.ui.FocusMemory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 每页条数；本轮只取第一页，超过一页时在列表尾部明确提示。 */
private const val PAGE_SIZE = 18

@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    onPlay: (String) -> Unit,
    onLogin: (() -> Unit)? = null,
) {
    UserWorksListScreen(
        title = "历史记录",
        subtitle = "最近观看",
        pageLabel = "历史记录",
        emptyText = HistoryGroupSpec.LABEL_EMPTY,
        load = { api -> VideoApi(api).historyRead(0L, PAGE_SIZE, 0) },
        onBack = onBack,
        onOpen = { aweme -> onPlay(aweme.awemeId()) },
        onLogin = onLogin,
    )
}

@Composable
fun FavoritesScreen(
    onBack: () -> Unit,
    onOpenDetail: (String) -> Unit,
    onLogin: (() -> Unit)? = null,
) {
    UserWorksListScreen(
        title = "收藏",
        subtitle = "我的收藏",
        pageLabel = "收藏",
        emptyText = "暂无收藏",
        load = { api ->
            // userFavorites 需要 sec_user_id：先取自己主页，未登录直接按登录态抛错。
            val profile = UserApi(api).profileSelf()
            val user = profile.user()
            if (profile.statusCode() == 8 || user == null) {
                throw ApiException.loginRequired()
            }
            if (profile.statusCode() != 0) {
                throw ApiException.apiStatus(
                    profile.statusCode(),
                    "收藏：读取个人主页返回 status_code=${profile.statusCode()}",
                )
            }
            UserApi(api).userFavorites(user.secUid(), 0L, 0L, PAGE_SIZE)
        },
        onBack = onBack,
        onOpen = { aweme -> onOpenDetail(aweme.awemeId()) },
        onLogin = onLogin,
    )
}

@Composable
fun WatchLaterScreen(
    onBack: () -> Unit,
    onPlay: (String) -> Unit,
    onLogin: (() -> Unit)? = null,
) {
    UserWorksListScreen(
        title = "稍后再看",
        subtitle = "稍后再看",
        pageLabel = "稍后再看",
        emptyText = "暂无稍后再看",
        // operate_type=0 为读取列表（原实现参数由 helper 拼装，标存疑）。
        load = { api -> VideoApi(api).watchLater(0, PAGE_SIZE, 0) },
        onBack = onBack,
        onOpen = { aweme -> onPlay(aweme.awemeId()) },
        onLogin = onLogin,
    )
}

@OptIn(ExperimentalTvFoundationApi::class)
@Composable
private fun UserWorksListScreen(
    title: String,
    subtitle: String,
    pageLabel: String,
    emptyText: String,
    load: suspend (DouyinApi) -> UserWorksResponse,
    onBack: () -> Unit,
    onOpen: (Aweme) -> Unit,
    onLogin: (() -> Unit)? = null,
) {
    var attempt by rememberSaveable { mutableStateOf(0) }
    val state = rememberWorksPage(pageLabel = pageLabel, reloadKey = attempt, load = load)
    val backFocus = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val rowRequesters = remember { mutableMapOf<String, FocusRequester>() }
    val focusScope = "works:$pageLabel"
    var focusRestored by remember { mutableStateOf(false) }

    // 加载期间先把焦点放在「返回」上，避免焦点悬空。
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { backFocus.requestFocus() }
    }

    // 数据到位后回到上次聚焦的条目（从播放器/详情返回），没有记忆则落第一条。
    LaunchedEffect(state) {
        if (focusRestored) return@LaunchedEffect
        val works = (state as? LoadState.Ready)?.value?.works() ?: return@LaunchedEffect
        if (works.isEmpty()) {
            focusRestored = true
            return@LaunchedEffect
        }
        val remembered = FocusMemory.last(focusScope)?.toIntOrNull() ?: 0
        val index = remembered.coerceIn(0, works.size - 1)
        // 列表前有两个头部 item：返回按钮、标题。
        runCatching { listState.scrollToItem(index + 2) }
        withFrameNanos { }
        withFrameNanos { }
        runCatching { rowRequesters[works[index].awemeId()]?.requestFocus() }
        focusRestored = true
    }

    // 点击条目：只做页面行为（播放 / 打开详情）。
    // 观看历史与播放埋点统一由播放器在真正起播时上报（见 ui/player/PlayerOverlayScreen 的
    // PlaybackFeedback 接线），这里不再按「点击」抢先写一条——那会把「没看过的条目」
    // 记进服务端历史，越看越准 learns 到的就是假信号。
    val openItem: (Aweme) -> Unit = { aweme -> onOpen(aweme) }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 40.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            BackButton(onBack = onBack, modifier = Modifier.focusRequester(backFocus))
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = title,
                    color = AxTvColors.TextPrimary,
                    style = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold),
                )
                Text(
                    text = subtitle,
                    color = AxTvColors.TextSecondary,
                    style = TextStyle(fontSize = 13.sp),
                )
            }
        }
        userWorksListItems(
            state = state,
            emptyText = emptyText,
            onRetry = { attempt += 1 },
            onOpen = openItem,
            onLogin = onLogin,
            focusRequesters = rowRequesters,
            focusScope = focusScope,
        )
    }
}

/**
 * 用户作品分页（历史 / 收藏 / 稍后再看）的加载状态。
 * 个人页的「历史」标签与 HistoryScreen 走同一个 helper，保证两条入口的失败语义一致。
 *
 * @param enabled 关闭时不发请求、返回 Idle —— 个人页只在停在「历史」标签时才拉。
 */
@Composable
internal fun rememberWorksPage(
    pageLabel: String,
    reloadKey: Any?,
    enabled: Boolean = true,
    load: suspend (DouyinApi) -> UserWorksResponse,
): LoadState<UserWorksResponse> {
    var state by remember(reloadKey, enabled) {
        mutableStateOf<LoadState<UserWorksResponse>>(if (enabled) LoadState.Loading else LoadState.Idle)
    }
    LaunchedEffect(reloadKey, enabled) {
        if (!enabled) {
            state = LoadState.Idle
            return@LaunchedEffect
        }
        val api = AppRuntime.api
        if (api == null) {
            state = LoadState.Failed(false, "服务未就绪", "API 运行时尚未初始化，请重启应用后重试。")
            return@LaunchedEffect
        }
        if (!isLoggedIn()) {
            state = LoadState.Failed(true, LOGIN_TITLE, "「$pageLabel」需要登录。$LOGIN_DETAIL")
            return@LaunchedEffect
        }
        state = LoadState.Loading
        state = withContext(Dispatchers.IO) {
            try {
                val response = load(api)
                statusFailure(response.statusCode(), pageLabel) ?: LoadState.Ready(response)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                error.toFailure(pageLabel)
            }
        }
    }
    return state
}

/** 用户作品列表的渲染段（加载 / 失败 / 空态 / 行），供整页与个人页标签复用；须在 LazyListScope 内调用。 */
internal fun LazyListScope.userWorksListItems(
    state: LoadState<UserWorksResponse>,
    emptyText: String,
    onRetry: () -> Unit,
    onOpen: (Aweme) -> Unit,
    onLogin: (() -> Unit)? = null,
    /** 非空时按条目 id 注册焦点请求器，并记下最后聚焦下标（从播放/详情返回时恢复）。 */
    focusRequesters: MutableMap<String, FocusRequester>? = null,
    focusScope: String? = null,
) {
    when (val current = state) {
        LoadState.Idle, LoadState.Loading -> item { LoadingPane() }
        is LoadState.Failed -> item {
            StatusPane(failed = current, onAction = onRetry, onLogin = onLogin)
        }

        is LoadState.Ready -> {
            val works = current.value.works()
            if (works.isEmpty()) {
                item { EmptyPane(emptyText) }
            } else {
                items(
                    count = works.size,
                    key = { index -> "works-${works[index].awemeId()}#$index" },
                ) { index ->
                    val aweme = works[index]
                    val id = aweme.awemeId()
                    val requester = remember(id) { FocusRequester() }
                    if (focusRequesters != null) {
                        focusRequesters[id] = requester
                    }
                    AwemeRow(
                        aweme = aweme,
                        onClick = onOpen,
                        modifier = if (focusRequesters != null) {
                            Modifier.focusRequester(requester)
                        } else {
                            Modifier
                        },
                        onFocused = {
                            if (focusScope != null) {
                                FocusMemory.remember(focusScope, index.toString())
                            }
                        },
                    )
                }
            }
            if (current.value.hasMore()) {
                item {
                    Text(
                        text = "仅展示第一页（$PAGE_SIZE 条），更多内容待分页接入。",
                        color = AxTvColors.TextSecondary,
                        style = TextStyle(fontSize = 12.sp),
                    )
                }
            }
        }
    }
}
