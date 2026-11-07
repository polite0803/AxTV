/*
 * 统一路由表：把 widget 端与本应用内已有的 route 字符串收敛到同一处解析。
 *
 * 规则来源：refactor/app/axtv/app/AppRoutes.java（EXTRA_ROUTE / parse / encode）。
 * 已接线页面：home / featured / featured/play / detail / player / play/aweme / search / search/results /
 *            live / live/room / mix/list / mix/detail / mix/play / profile(history|favorites|later) /
 *            settings / login。
 * 未识别 route 一律落首页（MainActivity 额外记一条日志）。
 * 各目标页面的真实签名见同目录 CONTRACT.md。
 */
package com.github.axtv.dv.ui.nav

import axtv.app.AppRoutes

/** 一次外部路由请求（携带自增序号，保证同一条 route 重复下发也能再次触发导航）。 */
data class RouteRequest(val raw: String, val seq: Long)

// 播放队列来源：首页第二排标签。精选走独立路由（featured/play），不在此列。
const val PLAYER_SOURCE_RECOMMEND = "recommend"
const val PLAYER_SOURCE_FOLLOW = "follow"
const val PLAYER_SOURCE_MOBILE = "mobile"

/** 路由解析后的目标。 */
sealed interface NavDestination {

    /**
     * 首页。
     *
     * @param tab 落地时选中的第二排标签（推荐/关注/移动推荐）；null = 默认「推荐」。
     *            设置项「默认首页 → 移动推荐」用它把首页直接开在那个标签上。
     */
    data class Home(val tab: String? = null) : NavDestination

    /** 精选频道页（分区内容列表）。 */
    data object Featured : NavDestination

    /** 精选播放：startIndex 指向 FeaturedRepository.cachedAwemes 的条目序号。 */
    data class FeaturedPlay(val startIndex: Int = 0) : NavDestination

    /**
     * 详情页。
     *
     * @param awemeId 作品 id
     * @param searchIndex 来源是搜索结果时的列表下标：详情页点「播放」会切到 search/play
     *                    按搜索顺序连播；其它来源为 null（单条播放）。
     */
    data class Detail(val awemeId: String, val searchIndex: Int? = null) : NavDestination

    /**
     * 播放器。
     *
     * @param startIndex 队列中的条目序号
     * @param source     队列来源（首页哪个标签页）：[PLAYER_SOURCE_RECOMMEND] /
     *                   [PLAYER_SOURCE_FOLLOW] / [PLAYER_SOURCE_MOBILE]；决定连播队列与队尾续页走哪条数据源
     */
    data class Player(
        val startIndex: Int = 0,
        val source: String = PLAYER_SOURCE_RECOMMEND,
    ) : NavDestination

    /** 单个作品的播放器（详情页 / 历史 / 稍后再看 / 合集单集）；按 awemeId 拉取后可播。 */
    data class PlayerAweme(val awemeId: String) : NavDestination

    /**
     * 发布者频道（他人主页 + 作品列表）。
     *
     * @param secUid   作者 sec_user_id（aweme/post 的唯一凭据）
     * @param nickname 来源页带过来的昵称；资料接口返回前用于标题兜底
     */
    data class AuthorChannel(val secUid: String, val nickname: String = "") : NavDestination

    /**
     * 作者作品连播：以作者全部作品为队列，从 [awemeId] 播到队尾，并自动续页。
     * [awemeId] 为空表示从第一条（最新）开始。
     */
    data class AuthorPlay(val secUid: String, val awemeId: String = "") : NavDestination

    data object Search : NavDestination

    data class SearchResults(val query: String) : NavDestination

    /** 搜索结果的连播队列；startIndex 指向 SearchPlaybackRepository.cachedAwemes。 */
    data class SearchPlay(val startIndex: Int = 0) : NavDestination

    data object Live : NavDestination

    /**
     * 直播间。
     *
     * @param roomId 解析后的房间 id（room_id → id_str → enter_room_id → web_rid）
     * @param webRid 列表下发的 web_rid；进房接口只用它，为空时只能走 info_by_scene
     */
    data class LiveRoom(val roomId: String, val webRid: String = "") : NavDestination

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

    /** 热榜列表。 */
    data object Hot : NavDestination

    /** IPTV 电视台列表。 */
    data object Iptv : NavDestination

    /** IPTV 单频道播放。 */
    data class IptvPlay(val channelId: String) : NavDestination
}

object NavRoutes {

    const val HOME = "home"
    const val FEATURED = "featured"
    const val FEATURED_PLAY = "featured/play"
    const val DETAIL = "detail"
    const val PLAYER = "player"
    const val PLAY_AWEME = "play/aweme"
    const val AUTHOR = "author"
    const val AUTHOR_PLAY = "author/play"
    const val SEARCH = "search"
    const val SEARCH_RESULTS = "search/results"
    const val SEARCH_PLAY = "search/play"
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

    /** 热榜（全站热搜词榜）。 */
    const val HOT = "hot"

    /** IPTV 电视台。 */
    const val IPTV = "iptv"
    /** IPTV 单频道播放。 */
    const val IPTV_PLAY = "iptv/play"

    /** 已知页面集合（供 MainActivity 判断「未识别 route」并记日志）。 */
    private val KNOWN_PAGES = setOf(
        HOME, FEATURED, FEATURED_PLAY, DETAIL, PLAYER, PLAY_AWEME,
        AUTHOR, AUTHOR_PLAY,
        HOT,
        SEARCH, SEARCH_RESULTS, SEARCH_PLAY,
        LIVE, LIVE_ROOM,
        MIX_LIST, MIX_DETAIL, MIX_PLAY,
        PROFILE, PROFILE_HISTORY, PROFILE_FAVORITES, PROFILE_LATER,
        SETTINGS, LOGIN,
        IPTV, IPTV_PLAY,
        WIDGET_PLAY_HISTORY, WIDGET_PLAY_LATER,
    )

    // ---- route 生成侧 ----

    fun home(tab: String? = null): String =
        if (tab.isNullOrBlank()) HOME else "$HOME?tab=${AppRoutes.encode(tab)}"

    fun detail(awemeId: String, searchIndex: Int? = null): String {
        val base = "$DETAIL?awemeId=${AppRoutes.encode(awemeId)}"
        return if (searchIndex == null) base else base + "&searchIndex=$searchIndex"
    }

    fun player(startIndex: Int, source: String = PLAYER_SOURCE_RECOMMEND): String {
        val base = if (startIndex <= 0) PLAYER else "$PLAYER?index=$startIndex"
        if (source == PLAYER_SOURCE_RECOMMEND) return base
        val sep = if (base == PLAYER) "?" else "&"
        return base + sep + "source=$source"
    }

    fun featuredPlay(startIndex: Int): String =
        if (startIndex <= 0) FEATURED_PLAY else "$FEATURED_PLAY?index=$startIndex"

    fun playerAweme(awemeId: String): String =
        "$PLAY_AWEME?awemeId=${AppRoutes.encode(awemeId)}"

    fun author(secUid: String, nickname: String = ""): String {
        val base = "$AUTHOR?secUid=${AppRoutes.encode(secUid)}"
        return if (nickname.isBlank()) base else base + "&name=${AppRoutes.encode(nickname)}"
    }

    fun authorPlay(secUid: String, awemeId: String = ""): String {
        val base = "$AUTHOR_PLAY?secUid=${AppRoutes.encode(secUid)}"
        return if (awemeId.isBlank()) base else base + "&aweme=${AppRoutes.encode(awemeId)}"
    }

    fun searchResults(query: String): String =
        "$SEARCH_RESULTS?query=${AppRoutes.encode(query)}"

    fun searchPlay(startIndex: Int): String =
        if (startIndex <= 0) SEARCH_PLAY else "$SEARCH_PLAY?index=$startIndex"

    fun liveRoom(roomId: String, webRid: String = ""): String {
        val base = "$LIVE_ROOM?id=${AppRoutes.encode(roomId)}"
        return if (webRid.isBlank()) base else base + "&webRid=${AppRoutes.encode(webRid)}"
    }

    fun mixDetail(mixId: String): String =
        "$MIX_DETAIL?id=${AppRoutes.encode(mixId)}"

    fun mixPlay(mixId: String, awemeId: String): String =
        "$MIX_PLAY?id=${AppRoutes.encode(mixId)}&aweme=${AppRoutes.encode(awemeId)}"

    /** 电视台单频道播放（channelId = 归一化频道名）。 */
    fun iptvPlay(channelId: String): String =
        "$IPTV_PLAY?channelId=${AppRoutes.encode(channelId)}"

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
            HOME, "" -> NavDestination.Home(args["tab"]?.takeIf { it.isNotBlank() })

            FEATURED -> NavDestination.Featured

            FEATURED_PLAY -> NavDestination.FeaturedPlay(
                args["index"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0,
            )

            DETAIL -> args["awemeId"].orEmpty()
                .takeIf { it.isNotEmpty() }
                ?.let {
                    NavDestination.Detail(
                        awemeId = it,
                        searchIndex = args["searchIndex"]?.toIntOrNull()?.coerceAtLeast(0),
                    )
                }
                ?: NavDestination.Home()

            PLAYER -> NavDestination.Player(
                startIndex = args["index"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0,
                source = args["source"]?.takeIf { it.isNotBlank() } ?: PLAYER_SOURCE_RECOMMEND,
            )

            PLAY_AWEME -> args["awemeId"].orEmpty()
                .takeIf { it.isNotEmpty() }
                ?.let { NavDestination.PlayerAweme(it) }
                ?: NavDestination.Home()

            AUTHOR -> args["secUid"].orEmpty()
                .takeIf { it.isNotEmpty() }
                ?.let { NavDestination.AuthorChannel(it, args["name"].orEmpty()) }
                ?: NavDestination.Home()

            AUTHOR_PLAY -> args["secUid"].orEmpty()
                .takeIf { it.isNotEmpty() }
                ?.let { NavDestination.AuthorPlay(it, args["aweme"].orEmpty()) }
                ?: NavDestination.Home()

            HOT -> NavDestination.Hot

            SEARCH -> NavDestination.Search

            SEARCH_RESULTS -> args["query"].orEmpty()
                .takeIf { it.isNotEmpty() }
                ?.let { NavDestination.SearchResults(it) }
                ?: NavDestination.Search

            SEARCH_PLAY -> NavDestination.SearchPlay(
                args["index"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0,
            )

            LIVE -> NavDestination.Live

            LIVE_ROOM -> args["id"].orEmpty()
                .takeIf { it.isNotEmpty() }
                ?.let { NavDestination.LiveRoom(it, args["webRid"].orEmpty()) }
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

            IPTV -> NavDestination.Iptv

            IPTV_PLAY -> {
                val channelId = args["channelId"].orEmpty()
                when {
                    channelId.isNotEmpty() -> NavDestination.IptvPlay(channelId)
                    else -> NavDestination.Iptv
                }
            }

            // widget 旧别名：带 id 也只落列表页（页面无按 id 起播入参）。
            WIDGET_PLAY_HISTORY -> NavDestination.ProfileHistory
            WIDGET_PLAY_LATER -> NavDestination.ProfileLater

            else -> NavDestination.Home()
        }
    }

    /** 目的地的规范 route（用于返回栈持久化与去重）。 */
    fun routeOf(destination: NavDestination): String = when (destination) {
        is NavDestination.Home -> home(destination.tab)
        NavDestination.Featured -> FEATURED
        is NavDestination.FeaturedPlay -> featuredPlay(destination.startIndex)
        is NavDestination.Detail -> detail(destination.awemeId, destination.searchIndex)
        is NavDestination.Player -> player(destination.startIndex, destination.source)
        is NavDestination.PlayerAweme -> playerAweme(destination.awemeId)
        is NavDestination.AuthorChannel -> author(destination.secUid, destination.nickname)
        is NavDestination.AuthorPlay -> authorPlay(destination.secUid, destination.awemeId)
        is NavDestination.Hot -> HOT
        NavDestination.Search -> SEARCH
        is NavDestination.SearchResults -> searchResults(destination.query)
        is NavDestination.SearchPlay -> searchPlay(destination.startIndex)
        NavDestination.Live -> LIVE
        is NavDestination.LiveRoom -> liveRoom(destination.roomId, destination.webRid)
        NavDestination.MixList -> MIX_LIST
        is NavDestination.MixDetail -> mixDetail(destination.mixId)
        is NavDestination.MixPlay -> mixPlay(destination.mixId, destination.awemeId)
        NavDestination.Profile -> PROFILE
        NavDestination.ProfileHistory -> PROFILE_HISTORY
        NavDestination.ProfileFavorites -> PROFILE_FAVORITES
        NavDestination.ProfileLater -> PROFILE_LATER
        NavDestination.Settings -> SETTINGS
        NavDestination.Login -> LOGIN
        NavDestination.Iptv -> IPTV
        is NavDestination.IptvPlay -> "$IPTV_PLAY?channelId=${AppRoutes.encode(destination.channelId)}"
    }

    /** 外部 route → 规范 route（widget 别名与未知 route 都归一化）。 */
    fun normalize(raw: String?): String = routeOf(parse(raw))
}
