/*
 * 线 E：历史 / 收藏 / 稍后再看。
 *
 * 三个页面共用同一套「用户作品列表」骨架：
 *  - 请求统一经 AppRuntime.api → axtv.api.VideoApi / UserApi，UI 不自建 HTTP 客户端；
 *  - 加载 / 失败（区分未登录）/ 空态三态齐全，失败不折叠为空列表；
 *  - 点击条目先回调调用方（历史/稍后再看 → onPlay，收藏 → onOpenDetail），
 *    再异步调用 VideoApi.historyWrite 记录观看行为（返回 String，按原实现忽略响应体）。
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
import androidx.compose.runtime.rememberCoroutineScope
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
        subtitle = "最近观看 · VideoApi.historyRead",
        pageLabel = "历史记录",
        emptyText = HistoryGroupSpec.LABEL_EMPTY,
        enterFrom = "personal_history",
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
        subtitle = "我的收藏 · UserApi.userFavorites",
        pageLabel = "收藏",
        emptyText = "暂无收藏",
        enterFrom = "personal_favorite",
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
        subtitle = "VideoApi.watchLater",
        pageLabel = "稍后再看",
        emptyText = "暂无稍后再看",
        enterFrom = "personal_watch_later",
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
    enterFrom: String,
    load: suspend (DouyinApi) -> UserWorksResponse,
    onBack: () -> Unit,
    onOpen: (Aweme) -> Unit,
    onLogin: (() -> Unit)? = null,
) {
    var attempt by rememberSaveable { mutableStateOf(0) }
    var state by remember { mutableStateOf<LoadState<UserWorksResponse>>(LoadState.Loading) }
    val scope = rememberCoroutineScope()
    val backFocus = remember { FocusRequester() }

    LaunchedEffect(attempt) {
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
        val loaded: LoadState<UserWorksResponse> = withContext(Dispatchers.IO) {
            try {
                val response = load(api)
                statusFailure(response.statusCode(), pageLabel) ?: LoadState.Ready(response)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                error.toFailure(pageLabel)
            }
        }
        state = loaded
    }

    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { backFocus.requestFocus() }
    }

    // 点击条目：先回调页面行为，再异步记一条观看历史；record 失败不影响播放/详情打开。
    val openItem: (Aweme) -> Unit = { aweme ->
        val id = aweme.awemeId()
        val api = AppRuntime.api
        if (api != null && id.isNotEmpty()) {
            scope.launch(Dispatchers.IO) {
                runCatching { VideoApi(api).historyWrite(id, enterFrom, aweme.createTime()) }
            }
        }
        onOpen(aweme)
    }

    LazyColumn(
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

        when (val current = state) {
            LoadState.Idle, LoadState.Loading -> item { LoadingPane() }
            is LoadState.Failed -> item {
                StatusPane(failed = current, onAction = { attempt += 1 }, onLogin = onLogin)
            }

            is LoadState.Ready -> {
                val works = current.value.works()
                if (works.isEmpty()) {
                    item { EmptyPane(emptyText) }
                } else {
                    items(
                        count = works.size,
                        key = { index -> "${works[index].awemeId()}#$index" },
                    ) { index ->
                        AwemeRow(aweme = works[index], onClick = openItem)
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
}
