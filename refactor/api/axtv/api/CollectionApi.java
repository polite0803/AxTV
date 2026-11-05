/*
 * 来源: src/app/p000/n11.java —— mix/detail、mix/aweme、mix/listcollection、mix/watch/record、
 *       series/list、series/detail、series/aweme、home/channel/setting、emoji/list
 * 原职责: 合集（mix）/系列（series）浏览与追更记录、首页频道设置、弹幕表情
 * 重构说明: mix/series 的部分参数由 helper 构造，保留可核实项并标「存疑」
 */
package axtv.api;

/** 合集 / 系列 / 频道设置 / 表情。 */
public final class CollectionApi {

    private final DouyinApi api;

    public CollectionApi(DouyinApi api) {
        this.api = api;
    }

    /** 合集详情。`/aweme/v1/web/mix/detail/` */
    public String mixDetail(String mixId) throws ApiException {
        return api.call(ApiRequest.get("/aweme/v1/web/mix/detail/")
                .param("mix_id", mixId)
                .commonParams()
                .sign()
                .uifidHeader());
    }

    /** 合集内视频。`/aweme/v1/web/mix/aweme/` */
    public String mixAwemes(String mixId, long cursor, int count) throws ApiException {
        return api.call(ApiRequest.get("/aweme/v1/web/mix/aweme/")
                .param("mix_id", mixId)
                .param("cursor", String.valueOf(cursor))
                .param("count", String.valueOf(count))
                .commonParams()
                .sign()
                .uifidHeader());
    }

    /** 收藏的合集。`/aweme/v1/web/mix/listcollection/` */
    public String mixListCollection(long cursor, int count) throws ApiException {
        return api.call(ApiRequest.get(DouyinEndpoints.MIX_LISTCOLLECTION)
                .param("cursor", String.valueOf(cursor))
                .param("count", String.valueOf(count))
                .commonParams()
                .sign()
                .userCenterSignChain());
    }

    /**
     * 追更记录（看到第几集）。`/aweme/v1/web/mix/watch/record/`（POST 表单）
     *
     * @param scene 原实现按入口区分（合集面板 / 连播），取值见调用方
     */
    public String recordMixProgress(String mixId, String itemId, int episode, String scene) throws ApiException {
        return api.call(ApiRequest.postForm(DouyinEndpoints.MIX_WATCH_RECORD)
                .body("mix_id", mixId)
                .body("item_id", itemId)
                .body("episode", String.valueOf(episode))
                .body("scene", scene)
                .commonParams()
                .sign()
                .uifidHeader());
    }

    /** 用户的系列列表。`/aweme/v1/web/series/list/` */
    public String seriesList(String secUserId, long cursor, int count) throws ApiException {
        return api.call(ApiRequest.get(DouyinEndpoints.SERIES_LIST)
                .param("sec_user_id", secUserId)
                .param("cursor", String.valueOf(cursor))
                .param("count", String.valueOf(count))
                .param("req_from", "user_page")
                .param("read_new_mix", "1")
                .commonParams()
                .sign()
                .uifidHeader());
    }

    /** 系列详情。`/aweme/v1/web/series/detail/` */
    public String seriesDetail(String seriesId) throws ApiException {
        return api.call(ApiRequest.get(DouyinEndpoints.SERIES_DETAIL)
                .param("series_id", seriesId)
                .param("req_from", "collection")
                .commonParams()
                .sign()
                .uifidHeader());
    }

    /** 系列内视频。`/aweme/v1/web/series/aweme/` */
    public String seriesAwemes(String seriesId, long cursor, int count, int pullType) throws ApiException {
        return api.call(ApiRequest.get(DouyinEndpoints.SERIES_AWEME)
                .param("series_id", seriesId)
                .param("cursor", String.valueOf(cursor))
                .param("count", String.valueOf(count))
                .param("pull_type", String.valueOf(pullType))
                .param("source", "0")
                .commonParams()
                .sign()
                .uifidHeader());
    }

    /** 首页频道设置。`/aweme/v1/web/home/channel/setting/` */
    public String channelSetting(String channel) throws ApiException {
        return api.call(ApiRequest.get(DouyinEndpoints.HOME_CHANNEL_SETTING)
                .param("channel", channel)
                .commonParams()
                .sign()
                .uifidHeader());
    }

    /** 弹幕表情。`/aweme/v1/web/emoji/list` */
    public String emojiList() throws ApiException {
        return api.call(ApiRequest.get(DouyinEndpoints.EMOJI_LIST)
                .commonParams()
                .sign()
                .uifidHeader());
    }
}
