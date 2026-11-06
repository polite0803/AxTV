/*
 * 来源: src/app/p000/n11.java —— webcast/feed、webcast/web/feed/follow、
 *       webcast/room/web/enter、webcast/room/info_by_scene、webcast/web/partition/detail/room/v2
 * 原职责: 直播列表、关注直播、进房、按场景取房间信息、分区直播列表
 * 重构说明: 全部走 live.douyin.com；UA 由启动时所选分支统一决定（原版 live 用 QQBrowser，
 *           见 UserAgentProvider.WEBCAST_USER_AGENT）；
 *           进房与分区接口的参数由 helper 构造，保留可核实项并标「存疑」
 */
package axtv.api;

import axtv.model.LiveFeedResponse;
import axtv.model.LiveFeedResponseHome;
import axtv.model.LiveRoomEnterResponse;
import axtv.model.LiveRoomResponse;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 直播 HTTP 接口。WebSocket 弹幕见 axtv.live。
 *
 * <p>返回类型以原实现的解析协程为准（ez0 的 byte 分支）：
 * 推荐流 {@code /webcast/feed/} 是 byte 9 → {@link LiveFeedResponseHome}；
 * 关注直播 {@code /webcast/web/feed/follow/} 是 byte 8 → {@link LiveFeedResponse}；
 * 分区列表是 byte 10 → {@link LiveFeedResponse}；进房是 byte 11 → {@link LiveRoomEnterResponse}；
 * 按场景取房间信息是 byte 12 → {@link LiveRoomResponse}。
 */
public final class LiveApi {

    /** n11.m8027S 里的固定场景值（原版唯一取值）。 */
    private static final String INFO_BY_SCENE = "aweme_video_feed_pc";

    private final DouyinApi api;

    public LiveApi(DouyinApi api) {
        this.api = api;
    }

    /**
     * 直播推荐流（首页热榜直播卡片）。`/webcast/feed/`
     *
     * <p>参数逐键对齐 n11.m8024P（反编译源 1730-1748 行）：live 基础参数 + 9 个直播专用键，
     * 再走 m8011D0 公共参数（msToken / 屏幕 / CPU / 内存 / verifyFp / fp / uifid / webid）。
     * `liveid` 在原版里是固定 `"1"` 开关，**不是**房间 id，因此 {@code liveId} 参数保留只为兼容调用方，不发送。
     *
     * @param count   首屏条数（原版 15/8 两档，由调用方决定）
     * @param maxTime 翻页时间戳；首屏传 0（原版此时额外加 `is_ssr=true`）
     * @param liveId  历史参数，原版无对应 query 键，当前不发送
     */
    public LiveFeedResponseHome feed(int count, long maxTime, String liveId) throws ApiException {
        ApiRequest request = ApiRequest.get(DouyinEndpoints.LIVE_FEED)
                .base(DouyinEndpoints.LIVE_HOST)
                .params(DouyinEndpoints.LIVE_BASE_PARAMS)
                .param("need_map", "1")
                .param("liveid", "1")
                .param("is_draw", "1")
                .param("inner_from_drawer", "0")
                .param("custom_count", String.valueOf(count))
                .param("action", "load_more")
                .param("action_type", "loadmore")
                .param("enter_source", "web_homepage_hot_web_live_card")
                .param("source_key", "web_homepage_hot_web_live_card");
        if (maxTime <= 0) {
            request.param("is_ssr", "true").param("maxtime", "0");
        } else {
            request.param("maxtime", String.valueOf(maxTime));
        }
        return api.callFor(request.commonParams().sign().uifidHeader(), LiveFeedResponseHome.class);
    }

    /**
     * 关注的主播谁在直播。`/webcast/web/feed/follow/`
     *
     * <p>与推荐流同一条 m8002r 通路（i=216）：live 基础层 + m8011D0 公共参数。
     */
    public LiveFeedResponse followFeed(String scene) throws ApiException {
        return api.callFor(liveRequest(DouyinEndpoints.LIVE_FOLLOW_FEED)
                .param("scene", scene)
                .sign()
                .uifidHeader(), LiveFeedResponse.class);
    }

    /**
     * 进房。`/webcast/room/web/enter/`
     *
     * <p>参数不是 live 基础层，而是 n11.m8057p 用 k04.m6904p(webRid) 拼的 11 个键；
     * webRid 为空时原版直接抛 {@code IllegalArgumentException("webRid 不能为空")}。
     * 发 room_id 会被服务端判成 {@code status_code=10011 Request params error}。
     *
     * @param webRid 房间的 web 号（列表接口的 {@code web_rid}），不是数字 room_id
     */
    public LiveRoomEnterResponse enterRoom(String webRid) throws ApiException {
        if (Texts.isBlank(webRid)) {
            throw new IllegalArgumentException("webRid 不能为空");
        }
        return api.callFor(ApiRequest.get(DouyinEndpoints.LIVE_ENTER)
                .base(DouyinEndpoints.LIVE_HOST)
                .params(k04EnterParams(webRid))
                // k04.m6901m：进房用 107 位随机 msToken（k04.p 里先占位空串）
                .param("msToken", Texts.randomLiveMsToken())
                // k04.m6910v：Referer 是 https://live.douyin.com/{webRid}
                .referer(DouyinEndpoints.liveRoomReferer(webRid))
                // m8002r 的 z=true：算 a_bogus；i=208 的 bit7 置位 → 不带 uifid 头
                .sign(), LiveRoomEnterResponse.class);
    }

    /**
     * 按场景取房间信息。`/webcast/room/info_by_scene/`
     *
     * <p>n11.m8027S：live 基础层 + room_id + 固定 scene=aweme_video_feed_pc + region=cn；
     * 原版此处不算 a_bogus（m8002r 的 z=false）。
     *
     * <p>实测（2026-11-06）：{@code room_id} 必须传列表里的数字 id（{@code id_str}），传 web_rid 会返回
     * {@code status_code=30001 room doesn't exist}；响应 {@code data} 直接是房间对象。
     * 旧的 {@code /webcast/room/web/enter/} 目前返回空响应体，只作为兜底。
     */
    public LiveRoomResponse roomInfoByScene(String roomId) throws ApiException {
        return api.callFor(liveRequest(DouyinEndpoints.LIVE_INFO_BY_SCENE)
                .param("room_id", roomId)
                .param("scene", INFO_BY_SCENE)
                .param("region", "cn"), LiveRoomResponse.class);
    }

    /** 分区直播列表。`/webcast/web/partition/detail/room/v2/` */
    public LiveFeedResponse partitionRooms(String partition, int partitionType, int offset, int count) throws ApiException {
        return api.callFor(liveRequest(DouyinEndpoints.LIVE_PARTITION)
                .param("partition", partition)
                .param("partition_type", String.valueOf(partitionType))
                .param("count", "15")
                .param("offset", String.valueOf(offset))
                .sign(), LiveFeedResponse.class);
    }

    /**
     * n11.m7994b：live 侧基础参数 = zy0.m12473a() 的 26 项再覆盖 device_platform/app_name/
     * live_id/enter_from/language/tz_name，随后走 m8011D0 公共参数。
     * 原版每个 webcast 接口都从这里起手，缺了会被服务端判成
     * `status_code=10011 Request params error`。
     *
     * <p>签名不在这里：m8002r 的 z 位由各调用点决定，只有分区列表与进房传 true。
     */
    private ApiRequest liveRequest(String path) {
        return ApiRequest.get(path)
                .base(DouyinEndpoints.LIVE_HOST)
                .params(DouyinEndpoints.LIVE_BASE_PARAMS)
                .commonParams();
    }

    /** k04.m6904p：进房专用 11 键。msToken 先占空串，调用方随后覆盖成 107 位随机串。 */
    private static Map<String, String> k04EnterParams(String webRid) {
        LinkedHashMap<String, String> params = new LinkedHashMap<>();
        params.put("aid", "6383");
        params.put("app_name", "douyin_web");
        params.put("live_id", "1");
        params.put("device_platform", "web");
        params.put("language", "zh-CN");
        params.put("browser_language", "zh-CN");
        params.put("browser_platform", "Win32");
        params.put("browser_name", "Chrome");
        params.put("browser_version", "125.0.0.0");
        params.put("web_rid", webRid);
        params.put("msToken", "");
        return params;
    }
}
