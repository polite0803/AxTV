/*
 * 来源: src/app/p000/n11.java —— webcast/feed、webcast/web/feed/follow、
 *       webcast/room/web/enter、webcast/room/info_by_scene、webcast/web/partition/detail/room/v2
 * 原职责: 直播列表、关注直播、进房、按场景取房间信息、分区直播列表
 * 重构说明: 全部走 live.douyin.com，UA 用 QQBrowser（见 DouyinEndpoints.LIVE_USER_AGENT）；
 *           进房与分区接口的参数由 helper 构造，保留可核实项并标「存疑」
 */
package axtv.api;

/** 直播 HTTP 接口。WebSocket 弹幕见 axtv.live。 */
public final class LiveApi {

    private final DouyinApi api;

    public LiveApi(DouyinApi api) {
        this.api = api;
    }

    /** 直播推荐流。`/webcast/feed/` */
    public String feed(int count, long maxTime, String liveId) throws ApiException {
        return api.call(liveRequest(DouyinEndpoints.LIVE_FEED)
                .param("count", String.valueOf(count))
                .param("maxtime", String.valueOf(maxTime))
                .param("liveid", liveId == null ? "" : liveId)
                .param("is_ssr", "0")
                .param("need_map", "1")
                .param("action", "0")
                .param("action_type", "0"));
    }

    /** 关注的主播谁在直播。`/webcast/web/feed/follow/` */
    public String followFeed(String scene) throws ApiException {
        return api.call(liveRequest(DouyinEndpoints.LIVE_FOLLOW_FEED)
                .param("scene", scene)
                .param("count", "20"));
    }

    /**
     * 进房。`/webcast/room/web/enter/`
     *
     * <p>参数由 helper 拼装（含 aid / device_platform / webcast_sdk_version 等），
     * 这里只保留可核实项（见 NOTES.md「存疑」）。
     */
    public String enterRoom(String roomId) throws ApiException {
        return api.call(liveRequest(DouyinEndpoints.LIVE_ENTER)
                .param("room_id", roomId)
                .param("webcast_sdk_version", "1.3.0")
                .param("device_platform", "web"));
    }

    /** 按场景取房间信息。`/webcast/room/info_by_scene/` */
    public String roomInfoByScene(String roomId, String scene) throws ApiException {
        return api.call(liveRequest(DouyinEndpoints.LIVE_INFO_BY_SCENE)
                .param("room_id", roomId)
                .param("scene", scene));
    }

    /** 分区直播列表。`/webcast/web/partition/detail/room/v2/` */
    public String partitionRooms(String partition, int partitionType, int offset, int count) throws ApiException {
        return api.call(liveRequest(DouyinEndpoints.LIVE_PARTITION)
                .param("partition", partition)
                .param("partition_type", String.valueOf(partitionType))
                .param("offset", String.valueOf(offset))
                .param("count", String.valueOf(count)));
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
