/*
 * 来源: src/app/p000/n11.java —— 信息流相关接口方法（tab/feed、module/feed、follow/feed、v2/feed）
 * 原职责: 首页多 tab 推荐流、频道推荐流、关注流、移动端推荐流
 * 重构说明: 参数键取自 n11 调用点的 map.put 字面量；返回体交给 JsonCodec / 调用方解析
 *          （原实现用 kotlinx.serialization 解成 FeedResponse / LiveFeedResponse）
 */
package axtv.api;

import java.util.LinkedHashMap;
import java.util.Map;

/** 信息流。对应 n11 里 tab/feed、module/feed、follow/feed、v2/feed 四个端点。 */
public final class FeedApi {

    private final DouyinApi api;

    public FeedApi(DouyinApi api) {
        this.api = api;
    }

    /**
     * 首页单 tab 推荐流。`/aweme/v1/web/tab/feed/`
     *
     * @param count        拉取条数（原实现默认 10~20，由页面决定）
     * @param refreshIndex 翻页序号，首页首次为 1
     * @param pullType     1=刷新 2=加载更多（原实现两个分支）
     */
    public String tabFeed(int count, int refreshIndex, int pullType) throws ApiException {
        return api.call(ApiRequest.get(DouyinEndpoints.TAB_FEED)
                .param("count", String.valueOf(count))
                .param("refresh_index", String.valueOf(refreshIndex))
                .param("pull_type", String.valueOf(pullType))
                .param("aweme_pc_rec_raw_data", "")
                .param("filterGids", "")
                .param("min_window", "0")
                .param("live_insert_type", "0")
                .param("pc_libra_divert", "1")
                .param("plug_block", "0")
                .commonParams()
                .sign()
                .uifidHeader()
                .ticketGuardHeaders());
    }

    /**
     * 频道/模块推荐流。`/aweme/v2/web/module/feed/`
     *
     * @param moduleId 频道 id（`/aweme/v1/web/home/channel/setting/` 返回的 channel id）
     */
    public String moduleFeed(String moduleId, int count, int refreshIndex, int pullType) throws ApiException {
        return api.call(ApiRequest.get(DouyinEndpoints.MODULE_FEED)
                .param("module_id", moduleId)
                .param("count", String.valueOf(count))
                .param("refresh_index", String.valueOf(refreshIndex))
                .param("pull_type", String.valueOf(pullType))
                .param("is_active_tab", "1")
                .param("pc_libra_divert", "1")
                .param("support_dash", "1")
                .commonParams()
                .sign()
                .uifidHeader());
    }

    /** 关注流。`/aweme/v1/web/follow/feed/` */
    public String followFeed(int count, long cursor, String level) throws ApiException {
        return api.call(ApiRequest.get(DouyinEndpoints.FOLLOW_FEED)
                .param("count", String.valueOf(count))
                .param("cursor", String.valueOf(cursor))
                .param("level", level)
                .param("pull_type", "0")
                .commonParams()
                .sign()
                .uifidHeader());
    }

    /**
     * 移动端形态推荐流（`aweme.snssdk.com` + 移动 UA）。
     * `/aweme/v2/feed/`
     *
     * <p>参数由原实现的 helper 构造，未逐字核实（见 NOTES.md「存疑」）。
     */
    public String mobileFeed(int count, long maxCursor) throws ApiException {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("count", String.valueOf(count));
        params.put("max_cursor", String.valueOf(maxCursor));
        params.put("aid", "1128");
        params.put("device_id", api.cookies().persistentDeviceId("mobile_feed_device_id"));
        params.put("iid", api.cookies().persistentDeviceId("mobile_feed_install_id"));
        params.put("device_type", api.device().deviceType());
        params.put("os_version", api.device().osVersion());
        return api.call(ApiRequest.get(DouyinEndpoints.MOBILE_FEED)
                .base(DouyinEndpoints.MOBILE_HOST)
                .params(params)
                .mobile()
                .sign());
    }
}
