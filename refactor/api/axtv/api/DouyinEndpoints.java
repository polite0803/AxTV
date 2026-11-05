/*
 * 来源: src/app/p000/n11.java
 *       f14561n（默认请求头 Map）、f14562o（接口路径 → Referer 映射，40 条）、
 *       m7993X（Referer 动态拼接）、m7996d（搜索页 Referer）
 * 原职责: 集中定义所有端点、默认请求头与 Referer
 * 重构说明: 拆成常量表 + refererFor()；字面量与反编译源逐字一致
 */
package axtv.api;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

final class DouyinEndpoints {

    static final String WEB_HOST = "https://www.douyin.com";
    static final String LIVE_HOST = "https://live.douyin.com";
    static final String MOBILE_HOST = "https://aweme.snssdk.com";
    static final String DEFAULT_REFERER = "https://www.douyin.com/?recommend=1";

    /** f14561n：所有 www.douyin.com 请求都会带的头。 */
    static final Map<String, String> DEFAULT_HEADERS;

    /** f14562o：接口路径 → Referer 基址。 */
    static final Map<String, String> REFERER;

    /** Webcast 分支的浏览器头；sec-ch-ua 版本与 QQBrowser 的 Chromium 116 对齐。 */
    static final Map<String, String> WEBCAST_HEADERS;

    /** App Feed 分支只发 App 场景需要的头，不含任何桌面指纹。 */
    static final Map<String, String> APP_FEED_HEADERS;

    /** zy0.m12473a()：所有 www.douyin.com 接口的 26 个基础 query 参数，逐字保留。 */
    static final Map<String, String> WEB_BASE_PARAMS;

    /** n11.m7994b()：live.douyin.com 在 26 个基础参数之上的 6 个覆盖/新增。 */
    static final Map<String, String> LIVE_BASE_PARAMS;

    // ---- 端点常量 ----
    static final String TAB_FEED = "/aweme/v1/web/tab/feed/";
    static final String MODULE_FEED = "/aweme/v2/web/module/feed/";
    static final String MOBILE_FEED = "/aweme/v2/feed/";
    static final String AWEME_DETAIL = "/aweme/v1/web/aweme/detail/";
    static final String AWEME_RELATED = "/aweme/v1/web/aweme/related/";
    static final String AWEME_POST = "/aweme/v1/web/aweme/post/";
    static final String AWEME_FAVORITE = "/aweme/v1/web/aweme/favorite/";
    static final String AWEME_LISTCOLLECTION = "/aweme/v1/web/aweme/listcollection/";
    static final String AWEME_COLLECT = "/aweme/v1/web/aweme/collect/";
    static final String AWEME_STATS = "/aweme/v2/web/aweme/stats/";
    static final String COMMENT_LIST = "/aweme/v1/web/comment/list/";
    static final String COMMENT_REPLY = "/aweme/v1/web/comment/list/reply/";
    static final String COMMIT_DIGG = "/aweme/v1/web/commit/item/digg/";
    static final String COMMIT_DISLIKE = "/aweme/v1/web/commit/dislike/item/";
    static final String USER_PROFILE_SELF = "/aweme/v1/web/user/profile/self/";
    static final String USER_PROFILE_OTHER = "/aweme/v1/web/user/profile/other/";
    static final String USER_FOLLOWER = "/aweme/v1/web/user/follower/list/";
    static final String USER_FOLLOWING = "/aweme/v1/web/user/following/list/";
    static final String SEARCH_ITEM = "/aweme/v1/web/search/item/";
    static final String SEARCH_SUG = "/aweme/v1/web/search/sug/";
    static final String SEARCH_DISCOVER = "/aweme/v1/web/discover/search";
    static final String SEARCH_LIVE = "/aweme/v1/web/live/search/";
    static final String SUGGEST_WORDS = "/aweme/v1/web/api/suggest_words/";
    static final String HOT_SEARCH = "/aweme/v1/web/hot/search/list/";
    static final String HOME_CHANNEL_SETTING = "/aweme/v1/web/home/channel/setting/";
    static final String MIX_LISTCOLLECTION = "/aweme/v1/web/mix/listcollection/";
    static final String MIX_WATCH_RECORD = "/aweme/v1/web/mix/watch/record/";
    static final String SERIES_LIST = "/aweme/v1/web/series/list/";
    static final String SERIES_DETAIL = "/aweme/v1/web/series/detail/";
    static final String SERIES_AWEME = "/aweme/v1/web/series/aweme/";
    static final String HISTORY_READ = "/aweme/v1/web/history/read/";
    static final String HISTORY_WRITE = "/aweme/v1/web/history/write/";
    static final String WATCH_LATER = "/aweme/v1/web/watchlater/list/";
    static final String FOLLOW_FEED = "/aweme/v1/web/follow/feed/";
    static final String DANMAKU = "/aweme/v1/web/danmaku/get_v2/";
    static final String EMOJI_LIST = "/aweme/v1/web/emoji/list";
    static final String SEO_INNER_LINK = "/aweme/v1/web/seo/inner/link/";
    static final String LIVE_ENTER = "/webcast/room/web/enter/";
    static final String LIVE_FEED = "/webcast/feed/";
    static final String LIVE_FOLLOW_FEED = "/webcast/web/feed/follow/";
    static final String LIVE_INFO_BY_SCENE = "/webcast/room/info_by_scene/";
    static final String LIVE_PARTITION = "/webcast/web/partition/detail/room/v2/";
    /** 搜索页 Referer 前缀。 */
    static final String SEARCH_REFERER = "https://www.douyin.com/search/";
    static final String VIDEO_REFERER = "https://www.douyin.com/video/";
    static final String COLLECTION_REFERER = "https://www.douyin.com/collection/";
    static final String USER_REFERER = "https://www.douyin.com/user/";

    static {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("accept", "application/json, text/plain, */*");
        headers.put("accept-language", "zh-CN,zh;q=0.9,en;q=0.8");
        headers.put("cache-control", "no-cache");
        headers.put("pragma", "no-cache");
        headers.put("priority", "u=1, i");
        headers.put("sec-ch-ua", "\"Not/A)Brand\";v=\"8\", \"Chromium\";v=\"147\", \"Google Chrome\";v=\"147\"");
        headers.put("sec-ch-ua-mobile", "?0");
        headers.put("sec-ch-ua-platform", "\"Windows\"");
        headers.put("sec-fetch-dest", "empty");
        headers.put("sec-fetch-mode", "cors");
        headers.put("sec-fetch-site", "same-origin");
        DEFAULT_HEADERS = Collections.unmodifiableMap(headers);

        Map<String, String> webcast = new LinkedHashMap<>();
        webcast.put("accept", "application/json, text/plain, */*");
        webcast.put("accept-language", "zh-CN");
        webcast.put("cache-control", "no-cache");
        webcast.put("pragma", "no-cache");
        webcast.put("priority", "u=1, i");
        webcast.put("sec-ch-ua", "\"Not/A)Brand\";v=\"8\", \"Chromium\";v=\"116\", \"Google Chrome\";v=\"116\"");
        webcast.put("sec-ch-ua-mobile", "?0");
        webcast.put("sec-ch-ua-platform", "\"Windows\"");
        webcast.put("sec-fetch-dest", "empty");
        webcast.put("sec-fetch-mode", "cors");
        webcast.put("sec-fetch-site", "same-origin");
        WEBCAST_HEADERS = Collections.unmodifiableMap(webcast);

        Map<String, String> appFeed = new LinkedHashMap<>();
        appFeed.put("accept", "*/*");
        appFeed.put("accept-language", "zh-CN,zh;q=0.9");
        APP_FEED_HEADERS = Collections.unmodifiableMap(appFeed);

        // zy0.m12473a()：26 个桌面基础参数，逐字照抄（含 2560/1440 的屏幕兜底）。
        Map<String, String> webBase = new LinkedHashMap<>();
        webBase.put("device_platform", "webapp");
        webBase.put("aid", "6383");
        webBase.put("channel", "channel_pc_web");
        webBase.put("update_version_code", "170400");
        webBase.put("pc_client_type", "1");
        webBase.put("pc_libra_divert", "Windows");
        webBase.put("support_h265", "1");
        webBase.put("support_dash", "1");
        webBase.put("version_code", "170400");
        webBase.put("version_name", "17.4.0");
        webBase.put("cookie_enabled", "true");
        webBase.put("screen_width", "2560");
        webBase.put("screen_height", "1440");
        webBase.put("browser_language", "zh-CN");
        webBase.put("browser_platform", "Win32");
        webBase.put("browser_name", "Chrome");
        webBase.put("browser_version", "147.0.0.0");
        webBase.put("browser_online", "true");
        webBase.put("engine_name", "Blink");
        webBase.put("engine_version", "147.0.0.0");
        webBase.put("os_name", "Windows");
        webBase.put("os_version", "10");
        webBase.put("platform", "PC");
        webBase.put("downlink", "10");
        webBase.put("effective_type", "4g");
        webBase.put("round_trip_time", "50");
        WEB_BASE_PARAMS = Collections.unmodifiableMap(webBase);

        // n11.m7994b()：live 端把 device_platform 改成 web，并补 5 个直播侧参数。
        Map<String, String> liveBase = new LinkedHashMap<>(WEB_BASE_PARAMS);
        liveBase.put("device_platform", "web");
        liveBase.put("app_name", "douyin_web");
        liveBase.put("live_id", "1");
        liveBase.put("enter_from", "web_live");
        liveBase.put("language", "zh-CN");
        liveBase.put("tz_name", "Asia/Shanghai");
        LIVE_BASE_PARAMS = Collections.unmodifiableMap(liveBase);

        Map<String, String> referers = new LinkedHashMap<>();
        referers.put(TAB_FEED, DEFAULT_REFERER);
        referers.put(MODULE_FEED, "https://www.douyin.com/channel/300203");
        referers.put("/aweme/v1/web/general/search/single/", SEARCH_REFERER);
        referers.put(SEARCH_ITEM, SEARCH_REFERER);
        referers.put(SUGGEST_WORDS, SEARCH_REFERER);
        referers.put(SEARCH_SUG, SEARCH_REFERER);
        referers.put(HOT_SEARCH, SEARCH_REFERER);
        referers.put(HOME_CHANNEL_SETTING, DEFAULT_REFERER);
        referers.put(AWEME_DETAIL, "https://www.douyin.com/");
        referers.put(AWEME_RELATED, VIDEO_REFERER);
        referers.put(COMMENT_LIST, VIDEO_REFERER);
        referers.put(COMMENT_REPLY, VIDEO_REFERER);
        referers.put(USER_PROFILE_SELF, "https://www.douyin.com/user/self");
        referers.put(USER_PROFILE_OTHER, USER_REFERER);
        referers.put(AWEME_POST, USER_REFERER);
        referers.put(AWEME_FAVORITE, USER_REFERER);
        referers.put(AWEME_LISTCOLLECTION, "https://www.douyin.com/user/self?showTab=favorite_collection");
        referers.put(HISTORY_READ, "https://www.douyin.com/user/self?showTab=history");
        referers.put(FOLLOW_FEED, "https://www.douyin.com/follow");
        referers.put(LIVE_FOLLOW_FEED, "https://live.douyin.com/follow");
        referers.put(LIVE_INFO_BY_SCENE, "https://live.douyin.com/");
        referers.put(LIVE_ENTER, "https://live.douyin.com/");
        referers.put(LIVE_PARTITION, "https://live.douyin.com/");
        referers.put(LIVE_FEED, "https://live.douyin.com/");
        referers.put(SEO_INNER_LINK, SEARCH_REFERER);
        referers.put(WATCH_LATER, "https://www.douyin.com/user/self?showTab=watchlater");
        referers.put(USER_FOLLOWING, "https://www.douyin.com/user/self?showTab=following");
        referers.put(USER_FOLLOWER, "https://www.douyin.com/user/self?showTab=follower");
        referers.put("/aweme/v1/web/mix/detail/", COLLECTION_REFERER);
        referers.put("/aweme/v1/web/mix/aweme/", COLLECTION_REFERER);
        referers.put(SERIES_DETAIL, COLLECTION_REFERER);
        referers.put(SERIES_AWEME, COLLECTION_REFERER);
        referers.put(SERIES_LIST, USER_REFERER);
        referers.put(MIX_LISTCOLLECTION, "https://www.douyin.com/user/self?showTab=favorite_collection");
        referers.put(DANMAKU, VIDEO_REFERER);
        referers.put(HISTORY_WRITE, "https://www.douyin.com/");
        referers.put(AWEME_STATS, "https://www.douyin.com/");
        referers.put(COMMIT_DISLIKE, DEFAULT_REFERER);
        referers.put(COMMIT_DIGG, "https://www.douyin.com/");
        referers.put(AWEME_COLLECT, "https://www.douyin.com/");
        REFERER = Collections.unmodifiableMap(referers);
    }

    private DouyinEndpoints() {
    }

    /** 按所选身份分支返回请求头模板，并写入该分支的唯一 UA。 */
    static Map<String, String> headersFor(ClientIdentityBranch branch, String userAgent) {
        Map<String, String> template;
        switch (branch) {
            case WEBCAST:
                template = WEBCAST_HEADERS;
                break;
            case APP_FEED:
                template = APP_FEED_HEADERS;
                break;
            default:
                template = DEFAULT_HEADERS;
        }
        LinkedHashMap<String, String> headers = new LinkedHashMap<>(template);
        headers.put("user-agent", userAgent);
        return headers;
    }

    /**
     * 对应 m7993X：按接口路径与参数推导 Referer。
     *
     * 原实现是一个被 jadx 破坏的 switch（"Failed to restore switch over string"），
     * 可确证的分支（aweme/favorite、comment/list/reply、aweme/related）已逐字还原；
     * 其余分支按 map 基址 + 语义 id 拼接（详见 NOTES.md「存疑」）。
     */
    static String refererFor(String path, Map<String, String> params) {
        String base = REFERER.get(path);
        if (base == null) {
            return DEFAULT_REFERER;
        }
        switch (path) {
            case AWEME_FAVORITE:
                return base + Texts.nvl(params.get("sec_user_id")) + "?showTab=like";
            case COMMENT_REPLY:
                return base + Texts.nvl(params.get("item_id"));
            case AWEME_RELATED:
                return base + Texts.nvl(params.get("aweme_id"));
            case USER_PROFILE_OTHER:
            case AWEME_POST:
            case SERIES_LIST:
                return base + Texts.nvl(params.get("sec_user_id"));
            case SERIES_AWEME:
                return base + Texts.nvl(params.get("series_id"));
            case SERIES_DETAIL:
                return base + firstNonBlank(params.get("series_id"), params.get("mix_id"));
            default:
                return base;
        }
    }

    /** 对应 m7996d：搜索页 Referer（带随机 aid 与搜索类型）。 */
    static String searchReferer(String keyword, String type) {
        if (Texts.isBlank(keyword)) {
            return SEARCH_REFERER;
        }
        return SEARCH_REFERER + Texts.urlEncode(keyword) + "?aid=" + UUID.randomUUID() + "&type=" + type;
    }

    private static String firstNonBlank(String a, String b) {
        if (!Texts.isBlank(a)) {
            return a;
        }
        return Texts.isBlank(b) ? "" : b;
    }
}
