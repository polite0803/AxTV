/*
 * 统一导航壳：手写返回栈（不引入 navigation-compose）。
 * 路由来源：MainActivity 消费 EXTRA_ROUTE / awemeId 深链后经 RouteRequest 送入；
 * 字符串解析集中在 ui/nav/NavRoutes，真实页面装配集中在 ui/nav/NavScreens。
 * BACK 语义：逐级弹栈；栈底是首页时不拦截，交给系统退到 TV 桌面。
 * 首页与播放器共用 HomeFeedRepository 的真实 aweme 列表（播放队列由 ui/nav/awemePlaylistProvider 构造）。
 */
package com.github.axtv.dv.ui

import android.content.pm.ActivityInfo
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import axtv.settings.Settings
import axtv.settings.SettingsTypes
import axtv.ui.HomeTabsSpec
import com.github.axtv.dv.adapter.AppRuntime
import com.github.axtv.dv.ui.author.AuthorChannelScreen
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
import com.github.axtv.dv.ui.iptv.IptvListScreen
import com.github.axtv.dv.ui.iptv.IptvPlayerScreen
import com.github.axtv.dv.ui.login.LoginScreen
import com.github.axtv.dv.ui.login.qr.findActivity
import com.github.axtv.dv.ui.nav.NavDestination
import com.github.axtv.dv.ui.nav.NavRoutes
import com.github.axtv.dv.ui.nav.PlayerFromAuthorScreen
import com.github.axtv.dv.ui.nav.PlayerFromAwemeScreen
import com.github.axtv.dv.ui.nav.PlayerFromFeaturedScreen
import com.github.axtv.dv.ui.nav.PlayerFromFeedScreen
import com.github.axtv.dv.ui.nav.PlayerFromMixScreen
import com.github.axtv.dv.ui.nav.PlayerFromSearchScreen
import com.github.axtv.dv.ui.nav.RouteRequest
import com.github.axtv.dv.ui.profile.ProfileScreen
import com.github.axtv.dv.ui.search.SearchPage
import com.github.axtv.dv.ui.search.SearchPlaybackRepository
import com.github.axtv.dv.ui.search.SearchResultsPage
import com.github.axtv.dv.ui.settings.SettingsScreen

private data class TopTab(val label: String, val route: String)

/**
 * 『UI 形态』的消费点。
 *
 * 三件事：向系统请求屏幕方向、决定顶栏是否横向可滚、决定非播放页的内容最大宽度。
 * AUTO 时按设备配置推导 —— 原版也是把设置值与 smallestScreenWidthDp 放在一起判的
 *（src/app/p000/qm4.java:1532 读 Configuration.smallestScreenWidthDp）。
 * 电视大多忽略竖屏请求，选「手机竖屏」在 TV 上表现为「仍是横屏 + 顶栏可滚」，这是平台限制。
 * 本应用清单里 leanback 不是 required、MainActivity 方向是 fullSensor，所以这一档不是空设置。
 */
private fun effectiveFormFactor(context: android.content.Context): SettingsTypes.UiFormFactor {
    val stored = AppRuntime.settings?.formFactor() ?: return SettingsTypes.UiFormFactor.TV_LANDSCAPE
    if (stored != SettingsTypes.UiFormFactor.AUTO) return stored
    val configuration = context.resources.configuration
    val isTelevision = (configuration.uiMode and android.content.res.Configuration.UI_MODE_TYPE_MASK) ==
        android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
    return when {
        isTelevision -> SettingsTypes.UiFormFactor.TV_LANDSCAPE
        configuration.smallestScreenWidthDp < 600 -> SettingsTypes.UiFormFactor.PHONE_PORTRAIT
        else -> SettingsTypes.UiFormFactor.TABLET_LANDSCAPE
    }
}

/** 平板横屏下正文列的侧边留白（TV 与手机保持原样，播放页不吃这条以保持全屏）。 */
private val TABLET_SIDE_PADDING = 48.dp

/** 栈底「再按一次返回键退出」的两次按键间隔上限。 */
private const val ROOT_BACK_EXIT_WINDOW_MS = 2_000L

private val TOP_TABS = listOf(
    TopTab("首页", NavRoutes.HOME),
    TopTab("精选", NavRoutes.FEATURED),
    TopTab("搜索", NavRoutes.SEARCH),
    TopTab("直播", NavRoutes.LIVE),
    TopTab("电视台", NavRoutes.IPTV),
    TopTab("合集", NavRoutes.MIX_LIST),
    TopTab("个人", NavRoutes.PROFILE),
    TopTab("设置", NavRoutes.SETTINGS),
)

private fun TopTab.isSelected(destination: NavDestination): Boolean = when (route) {
    NavRoutes.HOME -> destination is NavDestination.Home
    NavRoutes.FEATURED -> destination is NavDestination.Featured || destination is NavDestination.FeaturedPlay
    NavRoutes.SEARCH -> destination is NavDestination.Search
    NavRoutes.LIVE -> destination is NavDestination.Live
    NavRoutes.IPTV -> destination is NavDestination.Iptv || destination is NavDestination.IptvPlay
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
 * 四档里「移动推荐」现在是首页的一个真实标签页（第二排第 4 个），
 * 所以不另开路由，而是让首页直接以该标签落地（`home?tab=移动推荐`）。
 */
private fun defaultHomeRoute(): String = when (AppRuntime.settings?.defaultHomeScreen()) {
    SettingsTypes.HomeScreen.FEATURED -> NavRoutes.FEATURED
    SettingsTypes.HomeScreen.FOLLOWING -> NavRoutes.LIVE
    SettingsTypes.HomeScreen.MOBILE_RECOMMEND -> NavRoutes.home(HomeTabsSpec.TAB_MOBILE)
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

    /** 替换栈顶：搜索结果页内重新搜索时用，避免搜索历史把 BACK 变成多级返回。 */
    fun replaceTop(route: String) {
        val normalized = NavRoutes.normalize(route)
        if (backStack.isEmpty()) {
            backStack.add(normalized)
        } else {
            backStack[backStack.lastIndex] = normalized
        }
    }

    /** 顶栏切页：标签平级，栈内只保留「首页 + 当前标签」，BACK 一下回首页。 */
    fun navigateTab(route: String) {
        val normalized = NavRoutes.normalize(route)
        if (backStack.lastOrNull() == normalized) return
        while (backStack.size > 1) {
            backStack.removeAt(backStack.lastIndex)
        }
        if (backStack.isEmpty()) {
            backStack.add(NavRoutes.HOME)
        }
        if (normalized != NavRoutes.HOME) {
            backStack.add(normalized)
        }
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
    // 按路由保留屏内 rememberSaveable 状态（首页标签/精选分区/直播页签等）：
    // 返回栈切页只换栈顶，被覆盖页面的状态不丢，看片返回不再重置。
    val saveableStateHolder = rememberSaveableStateHolder()

    // 播放页与直播间要真正全屏：顶部 tab 栏不渲染（画面被顶栏挤占就不满屏）。
    // 直播间在 0.2.x 重设计成满屏播放窗口（信息/弹幕都是覆盖层），与点播播放页同一套沉浸语义。
    val immersive = destination is NavDestination.Player ||
        destination is NavDestination.PlayerAweme ||
        destination is NavDestination.FeaturedPlay ||
        destination is NavDestination.MixPlay ||
        destination is NavDestination.SearchPlay ||
        destination is NavDestination.AuthorPlay ||
        destination is NavDestination.LiveRoom ||
        destination is NavDestination.IptvPlay

    val shellContext = LocalContext.current
    var lastRootBackAt by remember { mutableStateOf(0L) }
    AxTvTheme {
        BackHandler(enabled = backStack.size > 1) { goBack() }
        // 栈底「再按一次返回键退出」：第一次提示，2 秒内再按才退出；
        // 「禁用双击返回退出」开启时吞掉 BACK。开关每次按键现读（非快照状态）。
        BackHandler(enabled = backStack.size == 1) {
            if (AppRuntime.settings?.disableDoubleBackExit() == true) return@BackHandler
            val now = SystemClock.elapsedRealtime()
            if (now - lastRootBackAt <= ROOT_BACK_EXIT_WINDOW_MS) {
                shellContext.findActivity()?.finish()
            } else {
                lastRootBackAt = now
                Toast.makeText(shellContext, "再按一次返回键退出", Toast.LENGTH_SHORT).show()
            }
        }
        // 『反向导航』只在根节点接管一次 DPAD 上/下；开关值改为每次按键现读（见 ReverseNavigation），
        // 这里只传「当前页面是否参与」：播放页的上下键是上一条/下一条（PlayerSession.handleRemoteKey），不能反转。
        val storedFactor = AppRuntime.settings?.formFactor() ?: SettingsTypes.UiFormFactor.AUTO
        val formFactor = effectiveFormFactor(shellContext)
        // 『UI 形态』→ 屏幕方向：只看用户显式选的那一档。
        // AUTO 必须保持清单里的 fullSensor，否则在手机上会把「跟随重力」锁死成单一方向（回归）。
        LaunchedEffect(storedFactor) {
            val activity = shellContext.findActivity() ?: return@LaunchedEffect
            runCatching {
                activity.requestedOrientation = when (storedFactor) {
                    SettingsTypes.UiFormFactor.PHONE_PORTRAIT ->
                        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

                    SettingsTypes.UiFormFactor.TABLET_LANDSCAPE,
                    SettingsTypes.UiFormFactor.TV_LANDSCAPE ->
                        ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

                    SettingsTypes.UiFormFactor.AUTO ->
                        ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
                }
            }
        }
        val tabletWide = formFactor == SettingsTypes.UiFormFactor.TABLET_LANDSCAPE && !immersive
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(AxTvColors.Bg)
                .reverseDpadUpDown(!immersive)
                .then(if (tabletWide) Modifier.padding(horizontal = TABLET_SIDE_PADDING) else Modifier),
        ) {
            if (!immersive) {
                TopTabBar(
                    current = destination,
                    compact = formFactor == SettingsTypes.UiFormFactor.PHONE_PORTRAIT,
                    onSelect = { navigateTab(it.route) },
                )
            }
            saveableStateHolder.SaveableStateProvider(currentRoute) {
                when (val target = destination) {
                    is NavDestination.Home -> HomeFeedScreen(
                        rememberedFocusIndex = homeFocusIndex,
                        onFocusIndexChange = { homeFocusIndex = it },
                        onOpenPlayer = { startIndex ->
                            navigate(NavRoutes.player(startIndex))
                        },
                        onOpenRoute = { navigate(it) },
                        initialTab = target.tab,
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
                        onOpenAuthor = { secUid -> navigate(NavRoutes.author(secUid)) },
                    )
    
                    is NavDestination.Detail -> Box(modifier = Modifier.weight(1f)) {
                        // 从搜索结果进入时把搜索队列的 id 顺序带给详情页：上一条/下一条和全屏连播都用它。
                        val queueIds = remember(target.awemeId, target.searchIndex) {
                            if (target.searchIndex == null) {
                                emptyList()
                            } else {
                                SearchPlaybackRepository.cachedAwemes.map { it.awemeId() }
                            }
                        }
                        DetailScreen(
                            awemeId = target.awemeId,
                            onBack = { goBack() },
                            onPlay = { awemeId, queueIndex ->
                                if (queueIndex != null) {
                                    navigate(NavRoutes.searchPlay(queueIndex))
                                } else {
                                    navigate(NavRoutes.playerAweme(awemeId))
                                }
                            },
                            queueIds = queueIds,
                            onOpenAuthor = { secUid -> navigate(NavRoutes.author(secUid)) },
                        )
                    }
    
                    is NavDestination.AuthorChannel -> Box(modifier = Modifier.weight(1f)) {
                        AuthorChannelScreen(
                            secUid = target.secUid,
                            nickname = target.nickname,
                            onBack = { goBack() },
                            onPlay = { startAwemeId ->
                                navigate(NavRoutes.authorPlay(target.secUid, startAwemeId))
                            },
                            onLogin = { navigate(NavRoutes.LOGIN) },
                        )
                    }
    
                    is NavDestination.AuthorPlay -> PlayerFromAuthorScreen(
                        secUid = target.secUid,
                        awemeId = target.awemeId,
                        onBack = { goBack() },
                        modifier = Modifier.weight(1f),
                        onOpenAuthor = { secUid -> navigate(NavRoutes.author(secUid)) },
                    )
    
                    is NavDestination.Player -> PlayerFromFeedScreen(
                        startIndex = target.startIndex,
                        onBack = { goBack() },
                        modifier = Modifier.weight(1f),
                        source = target.source,
                        onOpenAuthor = { secUid -> navigate(NavRoutes.author(secUid)) },
                    )
    
                    is NavDestination.PlayerAweme -> PlayerFromAwemeScreen(
                        awemeId = target.awemeId,
                        onBack = { goBack() },
                        modifier = Modifier.weight(1f),
                        onOpenAuthor = { secUid -> navigate(NavRoutes.author(secUid)) },
                    )
    
                    NavDestination.Search -> Box(modifier = Modifier.weight(1f)) {
                        // 双路由：输入页只负责输入/热搜/联想，提交后进入独立的 search/results 路由。
                        // 这样从详情页返回会回到结果页，而不是回到输入页重搜一次。
                        SearchPage(
                            query = null,
                            onOpenResults = { keyword -> navigate(NavRoutes.searchResults(keyword)) },
                            onBack = { goBack() },
                        )
                    }
    
                    is NavDestination.SearchResults -> Box(modifier = Modifier.weight(1f)) {
                        SearchResultsPage(
                            query = target.query,
                            onOpenDetail = { awemeId -> navigate(NavRoutes.detail(awemeId)) },
                            onOpenVideo = { awemeId, index ->
                                navigate(NavRoutes.detail(awemeId, index))
                            },
                            onSearchCommitted = { keyword ->
                                replaceTop(NavRoutes.searchResults(keyword))
                            },
                            onBack = { goBack() },
                        )
                    }
    
                    is NavDestination.SearchPlay -> PlayerFromSearchScreen(
                        startIndex = target.startIndex,
                        onBack = { goBack() },
                        modifier = Modifier.weight(1f),
                        onOpenAuthor = { secUid -> navigate(NavRoutes.author(secUid)) },
                    )
    
                    NavDestination.Live -> Box(modifier = Modifier.weight(1f)) {
                        LiveListScreen(
                            onBack = { goBack() },
                            onOpenRoom = { roomId, webRid -> navigate(NavRoutes.liveRoom(roomId, webRid)) },
                            onOpenIptv = { navigate(NavRoutes.IPTV) },
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
                        onOpenAuthor = { secUid -> navigate(NavRoutes.author(secUid)) },
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
    
                    NavDestination.Iptv -> Box(modifier = Modifier.weight(1f)) {
                        IptvListScreen(
                            onBack = { goBack() },
                            onPlay = { channelId -> navigate(NavRoutes.iptvPlay(channelId)) },
                        )
                    }
    
                    is NavDestination.IptvPlay -> Box(modifier = Modifier.weight(1f)) {
                        IptvPlayerScreen(
                            channelId = target.channelId,
                            onBack = { goBack() },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TopTabBar(current: NavDestination, compact: Boolean, onSelect: (TopTab) -> Unit) {
    Row(
        // 窄屏（手机竖屏）时 7 个页签放不下，改成横向可滚，并收起右下角的路由调试文案。
        modifier = if (compact) {
            Modifier
                .fillMaxWidth()
                .background(AxTvColors.Surface)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp)
        } else {
            Modifier
                .fillMaxWidth()
                .background(AxTvColors.Surface)
                .padding(horizontal = 40.dp, vertical = 12.dp)
        },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "AxTV",
            color = AxTvColors.Accent,
            style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold),
        )
        Spacer(Modifier.width(if (compact) 12.dp else 24.dp))
        TOP_TABS.forEach { item ->
            TopTabItem(
                label = item.label,
                selected = item.isSelected(current),
                onClick = { onSelect(item) },
            )
        }
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
