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

    /** 关注的主播谁在直播。`/webcast/web/feed/follow/` */
    public LiveFeedResponse followFeed(String scene) throws ApiException {
        return api.callFor(liveRequest(DouyinEndpoints.LIVE_FOLLOW_FEED)
                .param("scene", scene)
                .param("count", "20"), LiveFeedResponse.class);
    }

    /**
     * 进房。`/webcast/room/web/enter/`
     *
     * <p>参数由 helper 拼装（含 aid / device_platform / webcast_sdk_version 等），
     * 这里只保留可核实项（见 NOTES.md「存疑」）。
     */
    public LiveRoomEnterResponse enterRoom(String roomId) throws ApiException {
        return api.callFor(liveRequest(DouyinEndpoints.LIVE_ENTER)
                .param("room_id", roomId)
                .param("webcast_sdk_version", "1.3.0")
                .param("device_platform", "web"), LiveRoomEnterResponse.class);
    }

    /** 按场景取房间信息。`/webcast/room/info_by_scene/` */
    public LiveRoomResponse roomInfoByScene(String roomId, String scene) throws ApiException {
        return api.callFor(liveRequest(DouyinEndpoints.LIVE_INFO_BY_SCENE)
                .param("room_id", roomId)
                .param("scene", scene), LiveRoomResponse.class);
    }

    /** 分区直播列表。`/webcast/web/partition/detail/room/v2/` */
    public LiveFeedResponse partitionRooms(String partition, int partitionType, int offset, int count) throws ApiException {
        return api.callFor(liveRequest(DouyinEndpoints.LIVE_PARTITION)
                .param("partition", partition)
                .param("partition_type", String.valueOf(partitionType))
                .param("offset", String.valueOf(offset))
                .param("count", String.valueOf(count)), LiveFeedResponse.class);
    }

    private ApiRequest liveRequest(String path) {
        return ApiRequest.get(path)
                .base(DouyinEndpoints.LIVE_HOST)
                .param("aid", "6383")
                .param("app_name", "douyin_web")
                .param("device_platform", "web")
                .msTokenOnly()
                .sign();
    }
}
