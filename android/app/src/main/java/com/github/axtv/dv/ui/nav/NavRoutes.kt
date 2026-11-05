/*
 * 统一路由表：把 widget 端与本应用内已有的 route 字符串收敛到同一处解析。
 *
 * 规则来源：refactor/app/axtv/app/AppRoutes.java（EXTRA_ROUTE / parse / encode）。
 * 已接线页面：home / detail / player / play/aweme / search / search/results / live /
 *            live/room / mix/list / mix/detail / mix/play / profile(history|favorites|later) /
 *            settings / login。
 * 未识别 route 一律落首页（MainActivity 额外记一条日志）。
 * 各目标页面的真实签名见同目录 CONTRACT.md。
 */
package com.github.axtv.dv.ui.nav

import axtv.app.AppRoutes

/** 一次外部路由请求（携带自增序号，保证同一条 route 重复下发也能再次触发导航）。 */
data class RouteRequest(val raw: String, val seq: Long)

/** 路由解析后的目标。 */
sealed interface NavDestination {

    data object Home : NavDestination

    data class Detail(val awemeId: String) : NavDestination

    /** 播放器；startIndex 指向首页信息流队列中的条目序号。 */
    data class Player(val startIndex: Int = 0) : NavDestination

    /** 单个作品的播放器（详情页 / 历史 / 稍后再看 / 合集单集）；按 awemeId 拉取后可播。 */
    data class PlayerAweme(val awemeId: String) : NavDestination

    data object Search : NavDestination

    data class SearchResults(val query: String) : NavDestination

    data object Live : NavDestination

    data class LiveRoom(val roomId: String) : NavDestination

    data object MixList : NavDestination

    data class MixDetail(val mixId: String) : NavDestination

    /** 合集单集播放：mixId 仅用于记录来源，实际播放由 awemeId 决定。 */
    data class MixPlay(val mixId: String, val awemeId: String) : NavDestination

    data object Profile : NavDestination

    data object ProfileHistory : NavDestination

    data object ProfileFavorites : NavDestination

    data object ProfileLater : NavDestination

    data object Settings : NavDestination

    data object Login : NavDestination
}

/** 顶栏「统一路由」标签：release 下类名会被混淆，不能用 simpleName。 */
val NavDestination.debugLabel: String
    get() = when (this) {
        NavDestination.Home -> "Home"
        is NavDestination.Detail -> "Detail"
        is NavDestination.Player -> "Player"
        is NavDestination.PlayerAweme -> "PlayerAweme"
        NavDestination.Search -> "Search"
        is NavDestination.SearchResults -> "SearchResults"
        NavDestination.Live -> "Live"
        is NavDestination.LiveRoom -> "LiveRoom"
        NavDestination.MixList -> "MixList"
        is NavDestination.MixDetail -> "MixDetail"
        is NavDestination.MixPlay -> "MixPlay"
        NavDestination.Profile -> "Profile"
        NavDestination.ProfileHistory -> "History"
        NavDestination.ProfileFavorites -> "Favorites"
        NavDestination.ProfileLater -> "Later"
        NavDestination.Settings -> "Settings"
        NavDestination.Login -> "Login"
    }

object NavRoutes {

    const val HOME = "home"
    const val DETAIL = "detail"
    const val PLAYER = "player"
    const val PLAY_AWEME = "play/aweme"
    const val SEARCH = "search"
    const val SEARCH_RESULTS = "search/results"
    const val LIVE = "live"
    const val LIVE_ROOM = "live/room"
    const val MIX_LIST = "mix/list"
    const val MIX_DETAIL = "mix/detail"
    const val MIX_PLAY = "mix/play"
    const val PROFILE = "profile"
    const val PROFILE_HISTORY = "profile/history"
    const val PROFILE_FAVORITES = "profile/favorites"
    const val PROFILE_LATER = "profile/later"
    const val SETTINGS = "settings"
    const val LOGIN = "login"

    /** widget 侧旧别名：历史/稍后再看（带 id 时 id 被忽略，只落列表页）。 */
    const val WIDGET_PLAY_HISTORY = "play/history"
    const val WIDGET_PLAY_LATER = "play/later"

    /** 已知页面集合（供 MainActivity 判断「未识别 route」并记日志）。 */
    private val KNOWN_PAGES = setOf(
        HOME, DETAIL, PLAYER, PLAY_AWEME,
        SEARCH, SEARCH_RESULTS,
        LIVE, LIVE_ROOM,
        MIX_LIST, MIX_DETAIL, MIX_PLAY,
        PROFILE, PROFILE_HISTORY, PROFILE_FAVORITES, PROFILE_LATER,
        SETTINGS, LOGIN,
        WIDGET_PLAY_HISTORY, WIDGET_PLAY_LATER,
    )

    // ---- route 生成侧 ----

    fun detail(awemeId: String): String = "$DETAIL?awemeId=${AppRoutes.encode(awemeId)}"

    fun player(startIndex: Int): String =
        if (startIndex <= 0) PLAYER else "$PLAYER?index=$startIndex"

    fun playerAweme(awemeId: String): String =
        "$PLAY_AWEME?awemeId=${AppRoutes.encode(awemeId)}"

    fun searchResults(query: String): String =
        "$SEARCH_RESULTS?query=${AppRoutes.encode(query)}"

    fun liveRoom(roomId: String): String =
        "$LIVE_ROOM?id=${AppRoutes.encode(roomId)}"

    fun mixDetail(mixId: String): String =
        "$MIX_DETAIL?id=${AppRoutes.encode(mixId)}"

    fun mixPlay(mixId: String, awemeId: String): String =
        "$MIX_PLAY?id=${AppRoutes.encode(mixId)}&aweme=${AppRoutes.encode(awemeId)}"

    // ---- 解析 / 归一化 ----

    fun isKnown(raw: String?): Boolean {
        if (raw.isNullOrBlank()) return true
        return AppRoutes.parse(raw).page() in KNOWN_PAGES
    }

    /** 把任意 route（含 widget 别名/未知）解析成目的地；未知落首页。 */
    fun parse(raw: String?): NavDestination {
        val target = AppRoutes.parse(raw)
        val page = target.page()
        val args = target.args()
        return when (page) {
            HOME, "" -> NavDestination.Home

            DETAIL -> args["awemeId"].orEmpty()
                .takeIf { it.isNotEmpty() }
                ?.let { NavDestination.Detail(it) }
                ?: NavDestination.Home

            PLAYER -> NavDestination.Player(args["index"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0)

            PLAY_AWEME -> args["awemeId"].orEmpty()
                .takeIf { it.isNotEmpty() }
                ?.let { NavDestination.PlayerAweme(it) }
                ?: NavDestination.Home

            SEARCH -> NavDestination.Search

            SEARCH_RESULTS -> args["query"].orEmpty()
                .takeIf { it.isNotEmpty() }
                ?.let { NavDestination.SearchResults(it) }
                ?: NavDestination.Search

            LIVE -> NavDestination.Live

            LIVE_ROOM -> args["id"].orEmpty()
                .takeIf { it.isNotEmpty() }
                ?.let { NavDestination.LiveRoom(it) }
                ?: NavDestination.Live

            MIX_LIST -> NavDestination.MixList

            MIX_DETAIL -> args["id"].orEmpty()
                .takeIf { it.isNotEmpty() }
                ?.let { NavDestination.MixDetail(it) }
                ?: NavDestination.MixList

            MIX_PLAY -> {
                val mixId = args["id"].orEmpty()
                val awemeId = args["aweme"].orEmpty()
                when {
                    awemeId.isNotEmpty() -> NavDestination.MixPlay(mixId, awemeId)
                    mixId.isNotEmpty() -> NavDestination.MixDetail(mixId)
                    else -> NavDestination.MixList
                }
            }

            PROFILE -> NavDestination.Profile
            PROFILE_HISTORY -> NavDestination.ProfileHistory
            PROFILE_FAVORITES -> NavDestination.ProfileFavorites
            PROFILE_LATER -> NavDestination.ProfileLater
            SETTINGS -> NavDestination.Settings
            LOGIN -> NavDestination.Login

            // widget 旧别名：带 id 也只落列表页（页面无按 id 起播入参）。
            WIDGET_PLAY_HISTORY -> NavDestination.ProfileHistory
            WIDGET_PLAY_LATER -> NavDestination.ProfileLater

            else -> NavDestination.Home
        }
    }

    /** 目的地的规范 route（用于返回栈持久化与去重）。 */
    fun routeOf(destination: NavDestination): String = when (destination) {
        NavDestination.Home -> HOME
        is NavDestination.Detail -> detail(destination.awemeId)
        is NavDestination.Player -> player(destination.startIndex)
        is NavDestination.PlayerAweme -> playerAweme(destination.awemeId)
        NavDestination.Search -> SEARCH
        is NavDestination.SearchResults -> searchResults(destination.query)
        NavDestination.Live -> LIVE
        is NavDestination.LiveRoom -> liveRoom(destination.roomId)
        NavDestination.MixList -> MIX_LIST
        is NavDestination.MixDetail -> mixDetail(destination.mixId)
        is NavDestination.MixPlay -> mixPlay(destination.mixId, destination.awemeId)
        NavDestination.Profile -> PROFILE
        NavDestination.ProfileHistory -> PROFILE_HISTORY
        NavDestination.ProfileFavorites -> PROFILE_FAVORITES
        NavDestination.ProfileLater -> PROFILE_LATER
        NavDestination.Settings -> SETTINGS
        NavDestination.Login -> LOGIN
    }

    /** 外部 route → 规范 route（widget 别名与未知 route 都归一化）。 */
    fun normalize(raw: String?): String = routeOf(parse(raw))
}
