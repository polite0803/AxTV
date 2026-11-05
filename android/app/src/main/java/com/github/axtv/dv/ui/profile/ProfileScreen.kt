/*
 * 线 E：个人中心（我的主页）。
 *
 * 数据链路：
 *   UserApi.profileSelf()        → UserProfileResponse.user（头像/昵称/签名/关注·粉丝·作品数）
 *   UserApi.userPosts(secUid)    → UserWorksResponse（作品网格）
 *   UserApi.following / followers → UserListResponse（关注 / 粉丝列表）
 *
 * 三块数据都有独立的加载/失败/空态；未登录时整页明确提示「去登录」而不是留白。
 * 固定对外签名：ProfileScreen(onBack, onOpenDetail, onLogin?, onOpenRoute?)。
 */
package com.github.axtv.dv.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.foundation.ExperimentalTvFoundationApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.tv.material3.Text
import axtv.api.UserApi
import axtv.api.VideoApi
import axtv.model.Author
import axtv.model.DataModels
import axtv.model.UserListResponse
import axtv.model.UserWorksResponse
import axtv.ui.HistoryGroupSpec
import com.github.axtv.dv.adapter.AppRuntime
import com.github.axtv.dv.ui.AxTvColors
import com.github.axtv.dv.ui.focusRing
import com.github.axtv.dv.ui.library.AwemeCard
import com.github.axtv.dv.ui.library.AuthorRow
import com.github.axtv.dv.ui.library.BackButton
import com.github.axtv.dv.ui.library.EmptyPane
import com.github.axtv.dv.ui.library.LOGIN_DETAIL
import com.github.axtv.dv.ui.library.LOGIN_TITLE
import com.github.axtv.dv.ui.library.LoadState
import com.github.axtv.dv.ui.library.rememberWorksPage
import com.github.axtv.dv.ui.library.userWorksListItems
import com.github.axtv.dv.ui.library.LoadingPane
import com.github.axtv.dv.ui.library.StatusPane
import com.github.axtv.dv.ui.library.UserAvatar
import com.github.axtv.dv.ui.library.isLoggedIn
import com.github.axtv.dv.ui.library.statusFailure
import com.github.axtv.dv.ui.library.toFailure
import com.github.axtv.dv.ui.nav.NavRoutes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val PAGE_SIZE = 18
private const val GRID_COLUMNS = 4

private enum class ProfileTab(val label: String) {
    WORKS("作品"),
    FOLLOWING("关注"),
    FOLLOWERS("粉丝"),

    /** 历史：复用 HistoryScreen 那条 VideoApi.historyRead 路径（见 rememberWorksPage）。 */
    HISTORY("历史"),
}

/** 『个人页默认历史标签』的消费点：开启后进入个人页直接落在「历史」。 */
private fun initialProfileTab(): ProfileTab =
    if (AppRuntime.settings?.openOwnProfileOnHistoryTab() == true) ProfileTab.HISTORY else ProfileTab.WORKS

@OptIn(ExperimentalTvFoundationApi::class)
@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    onOpenDetail: (String) -> Unit,
    onLogin: (() -> Unit)? = null,
    onOpenRoute: (String) -> Unit = {},
) {
    var attempt by rememberSaveable { mutableStateOf(0) }
    var profileState by remember { mutableStateOf<LoadState<Author>>(LoadState.Loading) }
    var postsState by remember { mutableStateOf<LoadState<UserWorksResponse>>(LoadState.Idle) }
    var tab by rememberSaveable { mutableStateOf(initialProfileTab()) }

    var historyAttempt by rememberSaveable { mutableStateOf(0) }
    // 个人页「历史」标签：与 HistoryScreen 同一条接口，只在停在该标签时才请求。
    val historyState = rememberWorksPage(
        pageLabel = "历史记录",
        reloadKey = historyAttempt,
        enabled = tab == ProfileTab.HISTORY,
    ) { api -> VideoApi(api).historyRead(0L, PAGE_SIZE, 0) }

    var followingAttempt by rememberSaveable { mutableStateOf(0) }
    var followersAttempt by rememberSaveable { mutableStateOf(0) }
    var followingLoadedAttempt by remember { mutableStateOf(-1) }
    var followersLoadedAttempt by remember { mutableStateOf(-1) }
    var followingState by remember { mutableStateOf<LoadState<UserListResponse>>(LoadState.Idle) }
    var followersState by remember { mutableStateOf<LoadState<UserListResponse>>(LoadState.Idle) }

    val backFocus = remember { FocusRequester() }

    LaunchedEffect(attempt) {
        val api = AppRuntime.api
        if (api == null) {
            profileState = LoadState.Failed(false, "服务未就绪", "API 运行时尚未初始化，请重启应用后重试。")
            postsState = LoadState.Idle
            return@LaunchedEffect
        }
        if (!isLoggedIn()) {
            profileState = LoadState.Failed(true, LOGIN_TITLE, "「个人中心」需要登录。$LOGIN_DETAIL")
            postsState = LoadState.Idle
            return@LaunchedEffect
        }

        profileState = LoadState.Loading
        postsState = LoadState.Loading
        val loaded: LoadState<Author> = withContext(Dispatchers.IO) {
            try {
                val response = UserApi(api).profileSelf()
                val failure = statusFailure(response.statusCode(), "个人主页")
                val user = response.user()
                when {
                    failure != null -> failure
                    user == null -> LoadState.Failed(
                        true,
                        LOGIN_TITLE,
                        "接口未返回登录用户资料，可能 Cookie 已失效。$LOGIN_DETAIL",
                    )

                    else -> LoadState.Ready(user)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                error.toFailure("个人主页")
            }
        }
        profileState = loaded

        if (loaded !is LoadState.Ready) {
            postsState = LoadState.Idle
            return@LaunchedEffect
        }

        val secUid = loaded.value.secUid()
        if (secUid.isBlank()) {
            postsState = LoadState.Failed(false, "资料不完整", "接口未返回 sec_uid，无法拉取作品列表。")
            return@LaunchedEffect
        }

        val posts: LoadState<UserWorksResponse> = withContext(Dispatchers.IO) {
            try {
                val response = UserApi(api).userPosts(secUid, 0L, PAGE_SIZE)
                statusFailure(response.statusCode(), "作品") ?: LoadState.Ready(response)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                error.toFailure("作品")
            }
        }
        postsState = posts
    }

    // 关注 / 粉丝按需加载：切到对应标签时取一次；重试按钮递增 attempt 强制重取。
    LaunchedEffect(tab, followingAttempt, profileState) {
        val user = (profileState as? LoadState.Ready<Author>)?.value ?: return@LaunchedEffect
        if (tab != ProfileTab.FOLLOWING || followingLoadedAttempt == followingAttempt) {
            return@LaunchedEffect
        }
        val api = AppRuntime.api
        if (api == null) {
            followingState = LoadState.Failed(false, "服务未就绪", "API 运行时尚未初始化，请重启应用后重试。")
            followingLoadedAttempt = followingAttempt
            return@LaunchedEffect
        }
        if (!isLoggedIn()) {
            followingState = LoadState.Failed(true, LOGIN_TITLE, "「关注列表」需要登录。$LOGIN_DETAIL")
            followingLoadedAttempt = followingAttempt
            return@LaunchedEffect
        }
        followingState = LoadState.Loading
        val loaded: LoadState<UserListResponse> = withContext(Dispatchers.IO) {
            try {
                val response = UserApi(api).following(user.secUid(), 0, PAGE_SIZE)
                statusFailure(response.statusCode(), "关注列表") ?: LoadState.Ready(response)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                error.toFailure("关注列表")
            }
        }
        followingState = loaded
        followingLoadedAttempt = followingAttempt
    }

    LaunchedEffect(tab, followersAttempt, profileState) {
        val user = (profileState as? LoadState.Ready<Author>)?.value ?: return@LaunchedEffect
        if (tab != ProfileTab.FOLLOWERS || followersLoadedAttempt == followersAttempt) {
            return@LaunchedEffect
        }
        val api = AppRuntime.api
        if (api == null) {
            followersState = LoadState.Failed(false, "服务未就绪", "API 运行时尚未初始化，请重启应用后重试。")
            followersLoadedAttempt = followersAttempt
            return@LaunchedEffect
        }
        if (!isLoggedIn()) {
            followersState = LoadState.Failed(true, LOGIN_TITLE, "「粉丝列表」需要登录。$LOGIN_DETAIL")
            followersLoadedAttempt = followersAttempt
            return@LaunchedEffect
        }
        followersState = LoadState.Loading
        val loaded: LoadState<UserListResponse> = withContext(Dispatchers.IO) {
            try {
                val response = UserApi(api).followers(user.secUid(), 0, PAGE_SIZE)
                statusFailure(response.statusCode(), "粉丝列表") ?: LoadState.Ready(response)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                error.toFailure("粉丝列表")
            }
        }
        followersState = loaded
        followersLoadedAttempt = followersAttempt
    }

    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { backFocus.requestFocus() }
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
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "个人中心",
                    color = AxTvColors.TextPrimary,
                    style = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold),
                )
                Text(
                    text = "UserApi.profileSelf() · 作品 / 关注 / 粉丝",
                    color = AxTvColors.TextSecondary,
                    style = TextStyle(fontSize = 13.sp),
                )
            }
        }
        // 个人中心的三个子页面入口：未登录也能进入，由子页面统一给出「去登录」提示。
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ProfileTabChip(
                    label = "历史",
                    selected = false,
                    onClick = { onOpenRoute(NavRoutes.PROFILE_HISTORY) },
                )
                ProfileTabChip(
                    label = "收藏",
                    selected = false,
                    onClick = { onOpenRoute(NavRoutes.PROFILE_FAVORITES) },
                )
                ProfileTabChip(
                    label = "稍后再看",
                    selected = false,
                    onClick = { onOpenRoute(NavRoutes.PROFILE_LATER) },
                )
            }
        }

        when (val profile = profileState) {
            LoadState.Idle, LoadState.Loading -> item { LoadingPane() }
            is LoadState.Failed -> item {
                StatusPane(failed = profile, onAction = { attempt += 1 }, onLogin = onLogin)
            }

            is LoadState.Ready -> {
                item { ProfileHeader(profile.value) }
                item { ProfileTabs(current = tab, onSelect = { tab = it }) }

                when (tab) {
                    ProfileTab.WORKS -> when (val posts = postsState) {
                        LoadState.Idle, LoadState.Loading -> item { LoadingPane() }
                        is LoadState.Failed -> item {
                            StatusPane(failed = posts, onAction = { attempt += 1 }, onLogin = onLogin)
                        }

                        is LoadState.Ready -> {
                            // 『个人页倒序播放列表』的消费点：开启后作品网格从旧到新排列。
                            val reversed = AppRuntime.settings?.reversePlaylistInProfile() == true
                            val works = if (reversed) posts.value.works().reversed() else posts.value.works()
                            if (works.isEmpty()) {
                                item { EmptyPane("还没有作品") }
                            } else {
                                val rowCount = (works.size + GRID_COLUMNS - 1) / GRID_COLUMNS
                                items(count = rowCount) { rowIndex ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    ) {
                                        for (column in 0 until GRID_COLUMNS) {
                                            val index = rowIndex * GRID_COLUMNS + column
                                            if (index < works.size) {
                                                val aweme = works[index]
                                                AwemeCard(
                                                    aweme = aweme,
                                                    onClick = { onOpenDetail(aweme.awemeId()) },
                                                    modifier = Modifier.weight(1f),
                                                )
                                            } else {
                                                Spacer(Modifier.weight(1f))
                                            }
                                        }
                                    }
                                }
                            }
                            if (posts.value.hasMore()) {
                                item {
                                    Text(
                                        text = "仅展示第一页（$PAGE_SIZE 条），更多作品待分页接入。",
                                        color = AxTvColors.TextSecondary,
                                        style = TextStyle(fontSize = 12.sp),
                                    )
                                }
                            }
                        }
                    }

                    ProfileTab.FOLLOWING -> when (val list = followingState) {
                        LoadState.Idle, LoadState.Loading -> item { LoadingPane() }
                        is LoadState.Failed -> item {
                            StatusPane(
                                failed = list,
                                onAction = { followingAttempt += 1 },
                                onLogin = onLogin,
                            )
                        }

                        is LoadState.Ready -> {
                            val users = list.value.followings().orEmpty()
                            if (users.isEmpty()) {
                                item { EmptyPane("还没有关注任何人") }
                            } else {
                                items(
                                    count = users.size,
                                    key = { index -> "${users[index].secUid()}#$index" },
                                ) { index ->
                                    AuthorRow(author = users[index])
                                }
                            }
                            if (list.value.hasMore()) {
                                item {
                                    Text(
                                        text = "仅展示第一页（$PAGE_SIZE 个），更多关注待分页接入。",
                                        color = AxTvColors.TextSecondary,
                                        style = TextStyle(fontSize = 12.sp),
                                    )
                                }
                            }
                        }
                    }

                    ProfileTab.HISTORY -> userWorksListItems(
                        state = historyState,
                        emptyText = HistoryGroupSpec.LABEL_EMPTY,
                        onRetry = { historyAttempt += 1 },
                        // 历史条目点击即起播：走 player/aweme 路由，与 HistoryScreen 的行为一致。
                        onOpen = { aweme -> onOpenRoute(NavRoutes.playerAweme(aweme.awemeId())) },
                        onLogin = onLogin,
                    )

                    ProfileTab.FOLLOWERS -> when (val list = followersState) {
                        LoadState.Idle, LoadState.Loading -> item { LoadingPane() }
                        is LoadState.Failed -> item {
                            StatusPane(
                                failed = list,
                                onAction = { followersAttempt += 1 },
                                onLogin = onLogin,
                            )
                        }

                        is LoadState.Ready -> {
                            val users = list.value.followers().orEmpty()
                            if (users.isEmpty()) {
                                item { EmptyPane("还没有粉丝") }
                            } else {
                                items(
                                    count = users.size,
                                    key = { index -> "${users[index].secUid()}#$index" },
                                ) { index ->
                                    AuthorRow(author = users[index])
                                }
                            }
                            if (list.value.hasMore()) {
                                item {
                                    Text(
                                        text = "仅展示第一页（$PAGE_SIZE 人），更多粉丝待分页接入。",
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
    }
}

@Composable
private fun ProfileHeader(user: Author) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AxTvColors.Surface, RoundedCornerShape(12.dp))
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        UserAvatar(author = user, size = 96.dp)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = user.nickname().ifBlank { HistoryGroupSpec.LABEL_ANONYMOUS_USER },
                color = AxTvColors.TextPrimary,
                style = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = user.signature().ifBlank { "暂无签名" },
                color = AxTvColors.TextSecondary,
                style = TextStyle(fontSize = 14.sp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "关注 ${DataModels.formatCount(user.followingCount())}" +
                    " · 粉丝 ${DataModels.formatCount(user.followerCount())}" +
                    " · 作品 ${DataModels.formatCount(user.awemeCount())}",
                color = AxTvColors.TextSecondary,
                style = TextStyle(fontSize = 13.sp),
            )
        }
    }
}

@Composable
private fun ProfileTabs(current: ProfileTab, onSelect: (ProfileTab) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ProfileTab.entries.forEach { item ->
            ProfileTabChip(
                label = item.label,
                selected = item == current,
                onClick = { onSelect(item) },
            )
        }
    }
}

@Composable
private fun ProfileTabChip(label: String, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .background(
                color = if (selected) AxTvColors.Accent.copy(alpha = 0.16f) else AxTvColors.Surface,
                shape = RoundedCornerShape(8.dp),
            )
            .focusRing(focused, RoundedCornerShape(8.dp))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = label,
            color = if (selected || focused) AxTvColors.TextPrimary else AxTvColors.TextSecondary,
            style = TextStyle(
                fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            ),
        )
    }
}
