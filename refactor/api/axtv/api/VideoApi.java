/*
 * 来源: src/app/p000/n11.java —— aweme/detail、aweme/related、danmaku/get_v2、
 *       v2/web/aweme/stats、history/read、history/write、watchlater/list
 * 原职责: 视频详情、相关推荐、弹幕、播放埋点、观看历史、稍后再看
 * 重构说明: 参数键取自 n11 调用点字面量；返回体交给调用方解析
 */
package axtv.api;

import axtv.model.DanmakuResponse;
import axtv.model.FeedResponse;
import axtv.model.UserWorksResponse;
import axtv.model.WorkDetailResponse;

/**
 * 视频 / 弹幕 / 历史 / 稍后再看。
 *
 * <p>返回类型以原实现的解析协程为准（gz0 byte 5、ez0 byte 6/12/25/27）：
 * 详情/相关/弹幕/历史/稍后再看均返回强类型；`stats` 与 `history/write` 原实现只取字符串、
 * 不解析响应体，因此保留 {@code String}。
 */
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
    public WorkDetailResponse detail(String awemeId) throws ApiException {
        return api.callFor(ApiRequest.get(DouyinEndpoints.AWEME_DETAIL)
                .params(DouyinEndpoints.WEB_BASE_PARAMS)
                .param("aweme_id", awemeId)
                .param("origin_type", "0")
                .param("request_source", "0")
                .param("support_dash", "1")
                .param("support_h265", "1")
                // 详情接口要求用户中心签名链（a_bogus + uifid + timestamp + x-secsdk-web-signature）。
                // 实测：只带 a_bogus 会 403 Argus；补齐链后 200。ticket-guard 凭据为空时无需附加。
                .commonParams()
                .userCenterSignChain(), WorkDetailResponse.class);
    }

    /** 相关推荐。`/aweme/v1/web/aweme/related/` */
    public FeedResponse related(String awemeId, int count, int refreshIndex) throws ApiException {
        return api.callFor(ApiRequest.get(DouyinEndpoints.AWEME_RELATED)
                .params(DouyinEndpoints.WEB_BASE_PARAMS)
                .param("aweme_id", awemeId)
                .param("count", String.valueOf(count))
                .param("refresh_index", String.valueOf(refreshIndex))
                .param("filterGids", "")
                .param("sub_channel_id", "")
                .commonParams()
                .sign()
                .uifidHeader(), FeedResponse.class);
    }

    /**
     * 弹幕列表。`/aweme/v1/web/danmaku/get_v2/`
     *
     * @param itemId   作品 id
     * @param groupId  弹幕分组 id（详情接口返回的 danmaku_group_id，缺省与 itemId 相同）
     * @param duration 视频时长（毫秒）
     */
    public DanmakuResponse danmaku(String itemId, String groupId, long duration) throws ApiException {
        return api.callFor(ApiRequest.get(DouyinEndpoints.DANMAKU)
                .params(DouyinEndpoints.WEB_BASE_PARAMS)
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
                .uifidHeader(), DanmakuResponse.class);
    }

    /**
     * 播放埋点。`/aweme/v2/web/aweme/stats/`（POST 表单）。原实现丢弃响应体，保留 String 仅用于排障。
     *
     * <p>表单体逐字对齐 n11.java:4635：只有 {@code aweme_type / item_id / play_delta / source} 四项，
     * 且 {@code play_delta} 是常量字符串 "1"（不是观看秒数）、{@code source} 是常量 "0"。
     * 签名链对齐 m8004t(..., 56) → nz0.java:121 只算 a_bogus、nz0.java:173 的 ticket-guard 分支不进入。
     *
     * @param itemId    作品 id（原版字段名是 item_id，不是 aweme_id）
     * @param awemeType 作品类型（原版由调用方传 aweme.aweme_type）
     */
    public String stats(String itemId, int awemeType) throws ApiException {
        return api.call(ApiRequest.postForm(DouyinEndpoints.AWEME_STATS)
                .params(DouyinEndpoints.WEB_BASE_PARAMS)
                .body("aweme_type", String.valueOf(awemeType))
                .body("item_id", itemId)
                .body("play_delta", "1")
                .body("source", "0")
                .commonParams()
                .sign()
                .uifidHeader());
    }

    /** 观看历史。`/aweme/v1/web/history/read/` */
    public UserWorksResponse historyRead(long maxCursor, int count, int status) throws ApiException {
        return api.callFor(ApiRequest.get(DouyinEndpoints.HISTORY_READ)
                .params(DouyinEndpoints.WEB_BASE_PARAMS)
                .param("max_cursor", String.valueOf(maxCursor))
                .param("count", String.valueOf(count))
                .param("status", String.valueOf(status))
                .param("category", "0")
                .param("directory", "0")
                // 原版 m8047j0 的 history/read：m8002r(..., 252)，只算 a_bogus。
                .commonParams()
                .sign(), UserWorksResponse.class);
    }

    /**
     * 写观看历史。`/aweme/v1/web/history/write/`
     *
     * <p>表单体逐字对齐 n11.java:620：只有 {@code author_id / aweme_id} 两项
     * （此前记的「参数很多、由 helper 拼装」是取证不足的结论，已按调用点更正）。
     * 签名链与 {@link #stats} 同为 m8004t(..., 56)：nz0.java:121 只算 a_bogus，
     * nz0.java:173 的 ticket-guard 分支因 {@code (56 & 32) != 0} 不进入，
     * 所以这里不再挂 {@code userCenterSignChain()}。
     * <p>原实现不解析响应体，保留 {@code String}。
     *
     * @param awemeId  作品 id
     * @param authorId 发布者 id（取 {@code aweme.author.uid}）
     */
    public String historyWrite(String awemeId, String authorId) throws ApiException {
        return api.call(ApiRequest.postForm(DouyinEndpoints.HISTORY_WRITE)
                .params(DouyinEndpoints.WEB_BASE_PARAMS)
                .body("author_id", authorId)
                .body("aweme_id", awemeId)
                .commonParams()
                .sign()
                .uifidHeader());
    }

    /** 稍后再看列表。`/aweme/v1/web/watchlater/list/` */
    public UserWorksResponse watchLater(int offset, int count, int operateType) throws ApiException {
        return api.callFor(ApiRequest.get(DouyinEndpoints.WATCH_LATER)
                .params(DouyinEndpoints.WEB_BASE_PARAMS)
                .param("offset", String.valueOf(offset))
                .param("operate_type", String.valueOf(operateType))
                .param("list_type", "0")
                // 原版 m8054n0：m8002r(..., 252)，只带 offset/list_type/operate_type + a_bogus。
                .commonParams()
                .sign(), UserWorksResponse.class);
    }
}
