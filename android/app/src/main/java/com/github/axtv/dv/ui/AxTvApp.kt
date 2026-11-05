/*
 * 统一导航壳：手写返回栈（不引入 navigation-compose）。
 * 路由来源：MainActivity 消费 EXTRA_ROUTE / awemeId 深链后经 RouteRequest 送入；
 * 字符串解析集中在 ui/nav/NavRoutes，真实页面装配集中在 ui/nav/NavScreens。
 * BACK 语义：逐级弹栈；栈底是首页时不拦截，交给系统退到 TV 桌面。
 * 首页与播放器共用 HomeFeedRepository 的真实 aweme 列表（播放队列由 ui/nav/awemePlaylistProvider 构造）。
 */
package com.github.axtv.dv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import axtv.settings.Settings
import com.github.axtv.dv.adapter.AppRuntime
import com.github.axtv.dv.ui.collection.MixDetailScreen
import com.github.axtv.dv.ui.collection.MixListScreen
import com.github.axtv.dv.ui.detail.DetailScreen
import com.github.axtv.dv.ui.featured.FeaturedScreen
import com.github.axtv.dv.ui.home.HomeFeedScreen
import com.github.axtv.dv.ui.library.FavoritesScreen
import com.github.axtv.dv.ui.library.HistoryScreen
import com.github.axtv.dv.ui.library.WatchLaterScreen
import com.github.axtv.dv.ui.live.LiveListScreen
import com.github.axtv.dv.ui.live.LiveRoomScreen
import com.github.axtv.dv.ui.login.LoginScreen
import com.github.axtv.dv.ui.nav.NavDestination
import com.github.axtv.dv.ui.nav.NavRoutes
import com.github.axtv.dv.ui.nav.PlayerFromAwemeScreen
import com.github.axtv.dv.ui.nav.PlayerFromFeaturedScreen
import com.github.axtv.dv.ui.nav.PlayerFromFeedScreen
import com.github.axtv.dv.ui.nav.PlayerFromMixScreen
import com.github.axtv.dv.ui.nav.RouteRequest
import com.github.axtv.dv.ui.nav.debugLabel
import com.github.axtv.dv.ui.profile.ProfileScreen
import com.github.axtv.dv.ui.search.SearchResultsPage
import com.github.axtv.dv.ui.search.SearchScreen
import com.github.axtv.dv.ui.settings.SettingsScreen

private data class TopTab(val label: String, val route: String)

private val TOP_TABS = listOf(
    TopTab("首页", NavRoutes.HOME),
    TopTab("精选", NavRoutes.FEATURED),
    TopTab("搜索", NavRoutes.SEARCH),
    TopTab("直播", NavRoutes.LIVE),
    TopTab("合集", NavRoutes.MIX_LIST),
    TopTab("个人", NavRoutes.PROFILE),
    TopTab("设置", NavRoutes.SETTINGS),
)

private fun TopTab.isSelected(destination: NavDestination): Boolean = when (route) {
    NavRoutes.HOME -> destination is NavDestination.Home
    NavRoutes.FEATURED -> destination is NavDestination.Featured || destination is NavDestination.FeaturedPlay
    NavRoutes.SEARCH -> destination is NavDestination.Search
    NavRoutes.LIVE -> destination is NavDestination.Live
    NavRoutes.MIX_LIST -> destination is NavDestination.MixList
    NavRoutes.PROFILE -> destination is NavDestination.Profile
    NavRoutes.SETTINGS -> destination is NavDestination.Settings
    else -> false
}

/** 返回栈持久化：List<String> ↔ SnapshotStateList<String>。 */
private val RouteStackSaver = listSaver<SnapshotStateList<String>, String>(
    save = { it.toList() },
    restore = { it.toMutableStateList() },
)

/**
 * 设置项『默认首页』的消费点：冷启动（无深链 / 无外部 route）时落在哪一页。
 *
 * 原版四档里「移动推荐」是另一套信息流接口，本应用未实现，按首页处理；
 * 设置页该档文案已标注这一点，避免出现"选了却静默变首页"的歧义。
 */
private fun defaultHomeRoute(): String = when (AppRuntime.settings?.defaultHomeScreen()) {
    Settings.HomeScreen.FEATURED -> NavRoutes.FEATURED
    Settings.HomeScreen.FOLLOWING -> NavRoutes.LIVE
    else -> NavRoutes.HOME
}

@Composable
fun AxTvApp(
    initialRoute: String? = null,
    routeRequest: RouteRequest? = null,
    onRouteHandled: (RouteRequest) -> Unit = {},
) {
    val initial = initialRoute
        ?.takeIf { it.isNotBlank() }
        ?.let { NavRoutes.normalize(it) }
        ?: defaultHomeRoute()

    val backStack = rememberSaveable(saver = RouteStackSaver) {
        if (initial == NavRoutes.HOME) {
            mutableStateListOf(NavRoutes.HOME)
        } else {
            mutableStateListOf(NavRoutes.HOME, initial)
        }
    }
    // 焦点记忆：离开首页再回来时恢复到上次停留的信息流条目。
    var homeFocusIndex by rememberSaveable { mutableStateOf(0) }

    fun navigate(route: String) {
        val normalized = NavRoutes.normalize(route)
        if (backStack.lastOrNull() == normalized) return
        backStack.add(normalized)
    }

    /** 顶栏切页：首页清空回栈底，其它页序贯压栈（BACK 逐级返回）。 */
    fun navigateTab(route: String) {
        if (route == NavRoutes.HOME) {
            backStack.clear()
            backStack.add(NavRoutes.HOME)
            return
        }
        navigate(route)
    }

    fun goBack() {
        if (backStack.size > 1) {
            backStack.removeAt(backStack.lastIndex)
        }
    }

    /** 外部路由（widget / 深链）：从首页重新开始，再压入目标页。 */
    fun openExternal(raw: String) {
        val normalized = NavRoutes.normalize(raw)
        backStack.clear()
        backStack.add(NavRoutes.HOME)
        if (normalized != NavRoutes.HOME) {
            backStack.add(normalized)
        }
    }

    // 外部路由到达后切页；消费后回执，避免重复导航。
    LaunchedEffect(routeRequest) {
        val request = routeRequest ?: return@LaunchedEffect
        openExternal(request.raw)
        onRouteHandled(request)
    }

    val currentRoute = backStack.lastOrNull() ?: NavRoutes.HOME
    val destination = remember(currentRoute) { NavRoutes.parse(currentRoute) }

    // 播放页要真正全屏：顶部 tab 栏不渲染（画面被顶栏挤占就不满屏）；直播页保留顶栏（侧栏布局另算）。
    val immersive = destination is NavDestination.Player ||
        destination is NavDestination.PlayerAweme ||
        destination is NavDestination.FeaturedPlay ||
        destination is NavDestination.MixPlay

    AxTvTheme {
        BackHandler(enabled = backStack.size > 1) { goBack() }
        // 『禁用双击返回退出』的消费点：开启后在栈底吞掉 BACK，不退出应用。
        BackHandler(enabled = backStack.size == 1 && AppRuntime.settings?.disableDoubleBackExit() == true) { }
        // 『反向导航』只在根节点接管一次 DPAD 上/下；播放页的上下键是「上一条/下一条」
        // （原版语义，见 PlayerSession.handleRemoteKey），所以沉浸页不参与反转。
        val reverseNavigation = AppRuntime.settings?.reverseNavigation() == true && !immersive
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(AxTvColors.Bg)
                .reverseDpadUpDown(reverseNavigation),
        ) {
            if (!immersive) {
                TopTabBar(current = destination, onSelect = { navigateTab(it.route) })
            }
            when (val target = destination) {
                NavDestination.Home -> HomeFeedScreen(
                    rememberedFocusIndex = homeFocusIndex,
                    onFocusIndexChange = { homeFocusIndex = it },
                    onOpenPlayer = { startIndex ->
                        navigate(NavRoutes.player(startIndex))
                    },
                    onOpenRoute = { navigate(it) },
                    modifier = Modifier.weight(1f),
                )

                NavDestination.Featured -> FeaturedScreen(
                    onOpenPlayer = { startIndex -> navigate(NavRoutes.featuredPlay(startIndex)) },
                    onOpenSettings = { navigate(NavRoutes.SETTINGS) },
                    modifier = Modifier.weight(1f),
                )

                is NavDestination.FeaturedPlay -> PlayerFromFeaturedScreen(
                    startIndex = target.startIndex,
                    onBack = { goBack() },
                    modifier = Modifier.weight(1f),
                )

                is NavDestination.Detail -> Box(modifier = Modifier.weight(1f)) {
                    DetailScreen(
                        awemeId = target.awemeId,
                        onBack = { goBack() },
                        onPlay = { awemeId -> navigate(NavRoutes.playerAweme(awemeId)) },
                    )
                }

                is NavDestination.Player -> PlayerFromFeedScreen(
                    startIndex = target.startIndex,
                    onBack = { goBack() },
                    modifier = Modifier.weight(1f),
                )

                is NavDestination.PlayerAweme -> PlayerFromAwemeScreen(
                    awemeId = target.awemeId,
                    onBack = { goBack() },
                    modifier = Modifier.weight(1f),
                )

                NavDestination.Search -> Box(modifier = Modifier.weight(1f)) {
                    SearchScreen(
                        onBack = { goBack() },
                        onOpenDetail = { awemeId -> navigate(NavRoutes.detail(awemeId)) },
                    )
                }

                is NavDestination.SearchResults -> Box(modifier = Modifier.weight(1f)) {
                    SearchResultsPage(
                        query = target.query,
                        onOpenDetail = { awemeId -> navigate(NavRoutes.detail(awemeId)) },
                        onBack = { goBack() },
                    )
                }

                NavDestination.Live -> Box(modifier = Modifier.weight(1f)) {
                    LiveListScreen(
                        onBack = { goBack() },
                        onOpenRoom = { roomId, webRid -> navigate(NavRoutes.liveRoom(roomId, webRid)) },
                    )
                }

                is NavDestination.LiveRoom -> Box(modifier = Modifier.weight(1f)) {
                    LiveRoomScreen(
                        roomId = target.roomId,
                        webRid = target.webRid,
                        onBack = { goBack() },
                    )
                }

                NavDestination.MixList -> Box(modifier = Modifier.weight(1f)) {
                    MixListScreen(
                        onBack = { goBack() },
                        onOpenMix = { mixId -> navigate(NavRoutes.mixDetail(mixId)) },
                    )
                }

                is NavDestination.MixDetail -> Box(modifier = Modifier.weight(1f)) {
                    MixDetailScreen(
                        mixId = target.mixId,
                        onBack = { goBack() },
                        onPlay = { mixId, awemeId ->
                            navigate(NavRoutes.mixPlay(mixId, awemeId))
                        },
                    )
                }

                is NavDestination.MixPlay -> PlayerFromMixScreen(
                    mixId = target.mixId,
                    awemeId = target.awemeId,
                    onBack = { goBack() },
                    modifier = Modifier.weight(1f),
                )

                NavDestination.Profile -> Box(modifier = Modifier.weight(1f)) {
                    ProfileScreen(
                        onBack = { goBack() },
                        onOpenDetail = { awemeId -> navigate(NavRoutes.detail(awemeId)) },
                        onLogin = { navigate(NavRoutes.LOGIN) },
                        onOpenRoute = { navigate(it) },
                    )
                }

                NavDestination.ProfileHistory -> Box(modifier = Modifier.weight(1f)) {
                    HistoryScreen(
                        onBack = { goBack() },
                        onPlay = { awemeId -> navigate(NavRoutes.playerAweme(awemeId)) },
                        onLogin = { navigate(NavRoutes.LOGIN) },
                    )
                }

                NavDestination.ProfileFavorites -> Box(modifier = Modifier.weight(1f)) {
                    FavoritesScreen(
                        onBack = { goBack() },
                        onOpenDetail = { awemeId -> navigate(NavRoutes.detail(awemeId)) },
                        onLogin = { navigate(NavRoutes.LOGIN) },
                    )
                }

                NavDestination.ProfileLater -> Box(modifier = Modifier.weight(1f)) {
                    WatchLaterScreen(
                        onBack = { goBack() },
                        onPlay = { awemeId -> navigate(NavRoutes.playerAweme(awemeId)) },
                        onLogin = { navigate(NavRoutes.LOGIN) },
                    )
                }

                NavDestination.Settings -> SettingsScreen(
                    modifier = Modifier.weight(1f),
                    onOpenRoute = { navigate(it) },
                )

                NavDestination.Login -> Box(modifier = Modifier.weight(1f)) {
                    LoginScreen(
                        onBack = { goBack() },
                        onLoggedIn = { goBack() },
                    )
                }
            }
        }
    }
}

@Composable
private fun TopTabBar(current: NavDestination, onSelect: (TopTab) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AxTvColors.Surface)
            .padding(horizontal = 40.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "AxTV",
            color = AxTvColors.Accent,
            style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold),
        )
        Spacer(Modifier.width(24.dp))
        TOP_TABS.forEach { item ->
            TopTabItem(
                label = item.label,
                selected = item.isSelected(current),
                onClick = { onSelect(item) },
            )
        }
        Spacer(Modifier.weight(1f))
        Text(
            text = "统一路由 · ${current.debugLabel}",
            color = AxTvColors.TextSecondary,
            style = TextStyle(fontSize = 13.sp),
        )
    }
}

@Composable
private fun TopTabItem(label: String, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .background(
                color = if (selected) AxTvColors.Accent.copy(alpha = 0.16f) else Color.Transparent,
                shape = RoundedCornerShape(8.dp),
            )
            .focusRing(focused, RoundedCornerShape(8.dp))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = if (selected || focused) AxTvColors.TextPrimary else AxTvColors.TextSecondary,
            style = TextStyle(
                fontSize = 15.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            ),
        )
    }
}
