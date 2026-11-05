/*
 * 来源: src/app/p000/n11.java —— aweme/detail、aweme/related、danmaku/get_v2、
 *       v2/web/aweme/stats、history/read、history/write、watchlater/list
 * 原职责: 视频详情、相关推荐、弹幕、播放埋点、观看历史、稍后再看
 * 重构说明: 参数键取自 n11 调用点字面量；返回体交给调用方解析
 */
package axtv.api;

/** 视频 / 弹幕 / 历史 / 稍后再看。 */
public final class VideoApi {

    private final DouyinApi api;

    public VideoApi(DouyinApi api) {
        this.api = api;
    }

    /**
     * 视频详情。`/aweme/v1/web/aweme/detail/`
     *
     * @param awemeId 作品 id
     */
    public String detail(String awemeId) throws ApiException {
        return api.call(ApiRequest.get(DouyinEndpoints.AWEME_DETAIL)
                .param("aweme_id", awemeId)
                .param("origin_type", "0")
                .param("request_source", "0")
                .param("support_dash", "1")
                .param("support_h265", "1")
                .commonParams()
                .sign()
                .uifidHeader()
                .ticketGuardHeaders());
    }

    /** 相关推荐。`/aweme/v1/web/aweme/related/` */
    public String related(String awemeId, int count, int refreshIndex) throws ApiException {
        return api.call(ApiRequest.get(DouyinEndpoints.AWEME_RELATED)
                .param("aweme_id", awemeId)
                .param("count", String.valueOf(count))
                .param("refresh_index", String.valueOf(refreshIndex))
                .param("filterGids", "")
                .param("sub_channel_id", "")
                .commonParams()
                .sign()
                .uifidHeader());
    }

    /**
     * 弹幕列表。`/aweme/v1/web/danmaku/get_v2/`
     *
     * @param itemId   作品 id
     * @param groupId  弹幕分组 id（详情接口返回的 danmaku_group_id，缺省与 itemId 相同）
     * @param duration 视频时长（毫秒）
     */
    public String danmaku(String itemId, String groupId, long duration) throws ApiException {
        return api.call(ApiRequest.get(DouyinEndpoints.DANMAKU)
                .param("item_id", itemId)
                .param("group_id", groupId == null ? itemId : groupId)
                .param("duration", String.valueOf(duration))
                .param("start_time", "0")
                .param("end_time", String.valueOf(duration))
                .param("format", "1")
                .param("app_name", "douyin_web")
                .param("pc_libra_divert", "1")
                .param("support_dash", "1")
                .param("support_h265", "1")
                .commonParams()
                .sign()
                .uifidHeader());
    }

    /** 播放埋点。`/aweme/v2/web/aweme/stats/`（POST 表单） */
    public String stats(String awemeId, String itemType, long playDeltaMillis) throws ApiException {
        return api.call(ApiRequest.postForm(DouyinEndpoints.AWEME_STATS)
                .body("aweme_id", awemeId)
                .body("item_type", itemType)
                .body("play_delta", String.valueOf(playDeltaMillis / 1000L))
                .commonParams()
                .sign()
                .uifidHeader());
    }

    /** 观看历史。`/aweme/v1/web/history/read/` */
    public String historyRead(long maxCursor, int count, int status) throws ApiException {
        return api.call(ApiRequest.get(DouyinEndpoints.HISTORY_READ)
                .param("max_cursor", String.valueOf(maxCursor))
                .param("count", String.valueOf(count))
                .param("status", String.valueOf(status))
                .param("category", "0")
                .commonParams()
                .sign()
                .uifidHeader()
                .userCenterSignChain());
    }

    /**
     * 写观看历史。`/aweme/v1/web/history/write/`
     *
     * <p>原实现里参数很多（app_name / enter_from / keyword / language / publish_time …），
     * 由 helper 拼装，这里只保留可核实的关键项，其余标「存疑」。
     */
    public String historyWrite(String awemeId, String enterFrom, long publishTime) throws ApiException {
        return api.call(ApiRequest.postForm(DouyinEndpoints.HISTORY_WRITE)
                .body("aweme_id", awemeId)
                .body("enter_from", enterFrom)
                .body("publish_time", String.valueOf(publishTime))
                .body("enable_history", "1")
                .body("app_name", "douyin_web")
                .commonParams()
                .sign()
                .userCenterSignChain());
    }

    /** 稍后再看列表。`/aweme/v1/web/watchlater/list/` */
    public String watchLater(int offset, int count, int operateType) throws ApiException {
        return api.call(ApiRequest.get(DouyinEndpoints.WATCH_LATER)
                .param("offset", String.valueOf(offset))
                .param("count", String.valueOf(count))
                .param("operate_type", String.valueOf(operateType))
                .param("list_type", "0")
                .commonParams()
                .sign()
                .userCenterSignChain());
    }
}
