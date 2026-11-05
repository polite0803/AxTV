/*
 * 来源: src/app/p000/n11.java —— 信息流相关接口方法（tab/feed、module/feed、follow/feed、v2/feed）
 * 原职责: 首页多 tab 推荐流、频道推荐流、关注流、移动端推荐流
 * 重构说明: 参数键取自 n11 调用点的 map.put 字面量；返回体交给 JsonCodec / 调用方解析
 *          （原实现用 kotlinx.serialization 解成 FeedResponse / LiveFeedResponse）
 */
package axtv.api;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import axtv.model.FeedResponse;

/**
 * 信息流。对应 n11 里 tab/feed、module/feed、follow/feed、v2/feed 四个端点。
 *
 * <p>返回类型以原实现的解析协程为准（ez0 byte 6/14/16、gz0 byte 3）：JSON 端点返回
 * {@link FeedResponse}；移动端形态仍是 Java 侧无法解析的 protobuf，保留 {@code String}。
 */
public final class FeedApi {

    private final DouyinApi api;

    public FeedApi(DouyinApi api) {
        this.api = api;
    }

    /**
     * 首页单 tab 推荐流。`/aweme/v1/web/tab/feed/`
     *
     * <p>参数逐键对齐 n11.m8036b0（反编译源 2788-2856 行）：
     * 26 个桌面基础参数 + 20 个 tab 专用参数 + 屏幕/CPU/内存四项（取自 cookie），
     * `globalwid`/`webid` 仅在 user_unique_id 全数字时写入；
     * 原版此处**不注入 msToken/verifyFp/fp**，故这里也不走 {@code commonParams()}。
     *
     * @param count        拉取条数（原实现默认 10~20，由页面决定）
     * @param refreshIndex 翻页序号，首页首次为 1
     * @param pullType     1=刷新 2=加载更多（原实现两个分支）
     */
    public FeedResponse tabFeed(int count, int refreshIndex, int pullType) throws ApiException {
        return tabFeed(count, refreshIndex, pullType, List.of());
    }

    /**
     * 带"最近已看作品"的 tab 推荐流；原版用这些 id 构造 `aweme_pc_rec_raw_data.videoPrefer`。
     *
     * @param recentAwemeIds 最近已展示的 aweme id，原版取前 10 个；首页刷新时可传空列表
     */
    public FeedResponse tabFeed(int count, int refreshIndex, int pullType,
                                List<String> recentAwemeIds) throws ApiException {
        ApiRequest request = ApiRequest.get(DouyinEndpoints.TAB_FEED)
                .params(DouyinEndpoints.WEB_BASE_PARAMS)
                .param("filterGids", "")
                .param("tag_id", "")
                .param("share_aweme_id", "")
                .param("live_insert_type", "")
                .param("count", String.valueOf(count))
                .param("refresh_index", String.valueOf(refreshIndex))
                .param("video_type_select", "1")
                .param("aweme_pc_rec_raw_data", rawData(refreshIndex, recentAwemeIds))
                .param("pull_type", String.valueOf(pullType))
                .param("min_window", "0")
                .param("free_right", "0")
                // n11.m7995c0：view_count = max(0, (refreshIndex - 1) * count)
                .param("view_count", String.valueOf(Math.max(0, (refreshIndex - 1) * count)))
                .param("plug_block", "0")
                .param("ug_source", "")
                .param("creative_id", "")
                .param("pc_libra_divert", "Windows")
                .param("support_h265", "1")
                .param("support_dash", "1")
                .param("webcast_sdk_version", "170400")
                .param("webcast_version_code", "170400");
        api.injectDeviceParams(request.query());
        String uniqueId = api.cookies().userUniqueId();
        if (!Texts.isBlank(uniqueId) && Texts.allDigits(uniqueId)) {
            request.param("globalwid", uniqueId).param("webid", uniqueId);
        }
        return api.callFor(request
                .secsdkSignatureOnly(), FeedResponse.class);
    }

    /**
     * n11.m8036b0 的 `aweme_pc_rec_raw_data`：首页刷新用短 JSON，加载更多用 videoPrefer 长 JSON。
     * `halfMin`/`min` 是「把最近 10 个 aweme id 用逗号拼起来」的单元素数组，逐字保留。
     */
    private static String rawData(int refreshIndex, List<String> recentAwemeIds) {
        if (refreshIndex == 1) {
            return "{\"is_client\":false,\"ff_danmaku_status\":1,\"danmaku_switch_status\":0,"
                    + "\"is_dash_user\":1,\"related_recommend\":1,\"is_xigua_user\":0}";
        }
        List<String> ids = recentAwemeIds == null ? List.of() : recentAwemeIds;
        String joined = String.join(",", ids.subList(0, Math.min(10, ids.size())));
        return "{\"videoPrefer\":{\"fsn\":[],\"like\":[],\"halfMin\":[\"" + joined + "\"],\"min\":[\""
                + joined + "\"]},\"seo_info\":\"\",\"is_client\":false,\"ff_danmaku_status\":1,"
                + "\"danmaku_switch_status\":1,\"is_dash_user\":1,\"is_auto_play\":0,"
                + "\"is_full_screen\":0,\"is_full_webscreen\":0,\"is_mute\":0,\"is_speed\":1,"
                + "\"is_visible\":1,\"related_recommend\":1,\"is_xigua_user\":0}";
    }

    /**
     * 频道/模块推荐流。`/aweme/v2/web/module/feed/`
     *
     * @param moduleId 频道 id（`/aweme/v1/web/home/channel/setting/` 返回的 channel id）
     */
    public FeedResponse moduleFeed(String moduleId, int count, int refreshIndex, int pullType) throws ApiException {
        return api.callFor(ApiRequest.get(DouyinEndpoints.MODULE_FEED)
                .param("module_id", moduleId)
                .param("count", String.valueOf(count))
                .param("refresh_index", String.valueOf(refreshIndex))
                .param("pull_type", String.valueOf(pullType))
                .param("is_active_tab", "1")
                .param("pc_libra_divert", "1")
                .param("support_dash", "1")
                .commonParams()
                .sign()
                .uifidHeader(), FeedResponse.class);
    }

    /** 关注流。`/aweme/v1/web/follow/feed/` */
    public FeedResponse followFeed(int count, long cursor, String level) throws ApiException {
        return api.callFor(ApiRequest.get(DouyinEndpoints.FOLLOW_FEED)
                .param("count", String.valueOf(count))
                .param("cursor", String.valueOf(cursor))
                .param("level", level)
                .param("pull_type", "0")
                .commonParams()
                .sign()
                .uifidHeader(), FeedResponse.class);
    }

    /**
     * 移动端形态推荐流（`aweme.snssdk.com` + 移动 UA）。
     * `/aweme/v2/feed/`
     *
     * <p>响应为 protobuf（原实现 g13.m4375f），由 {@link MobileFeedProtoCodec} 零依赖解码；
     * 若服务端退回 JSON（首字节 '{'），走 {@code ModelJsonCodec} 回退——与原版一致。
     */
    public FeedResponse mobileFeed(int count, long maxCursor) throws ApiException {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("count", String.valueOf(count));
        params.put("max_cursor", String.valueOf(maxCursor));
        params.put("aid", "1128");
        params.put("device_id", api.cookies().persistentDeviceId("mobile_feed_device_id"));
        params.put("iid", api.cookies().persistentDeviceId("mobile_feed_install_id"));
        params.put("device_type", api.device().deviceType());
        params.put("os_version", api.device().osVersion());
        byte[] body = api.callBytes(ApiRequest.get(DouyinEndpoints.MOBILE_FEED)
                .base(DouyinEndpoints.MOBILE_HOST)
                .params(params)
                .mobile()
                .sign());
        String path = DouyinEndpoints.MOBILE_FEED;
        try {
            if (MobileFeedProtoCodec.looksLikeJson(body)) {
                return api.decodeBody(new String(body, java.nio.charset.StandardCharsets.UTF_8),
                        FeedResponse.class, path);
            }
            FeedResponse response = MobileFeedProtoCodec.decode(body);
            if (response == null) {
                // 原版 g13.m4375f L472-478：status_code != 0 时 return null
                throw ApiException.apiStatus(-1, "移动推荐 status_code != 0（原版返回 null）");
            }
            return response;
        } catch (MobileFeedProtoException e) {
            throw ApiException.decode(path, e);
        }
    }
}
