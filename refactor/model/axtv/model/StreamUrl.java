/*
 * 来源: src/app/com/github/mytv/p004dv/model/StreamUrl.java + StreamUrl$$serializer.java
 * 原职责: 直播间拉流地址集合：flv 与 hls 两套「档位 → 地址」映射，外加 SDK 直出数据
 * 重构说明: 数据类 → record；两个 Map 在原实现里允许为 null（默认就是 null），保留可空语义
 */
package axtv.model;

import java.util.Map;

/**
 * @param flvPullUrl    flv 地址，key 为档位（FULL_HD1/HD1/SD2/SD1 或任意档位名）（{@code flv_pull_url}）；
 *                      <b>可空</b>
 * @param hlsPullUrlMap hls 地址，key 同上（{@code hls_pull_url_map}）；<b>可空</b>
 * @param liveCoreSdkData SDK 直出的嵌套拉流数据（{@code live_core_sdk_data}）；<b>可空</b>
 */
public record StreamUrl(
        @JsonKey("flv_pull_url") Map<String, String> flvPullUrl,
        @JsonKey("hls_pull_url_map") Map<String, String> hlsPullUrlMap,
        @JsonKey("live_core_sdk_data") LiveCoreSdkData liveCoreSdkData) {

    public StreamUrl {
        flvPullUrl = flvPullUrl == null ? null : Map.copyOf(flvPullUrl);
        hlsPullUrlMap = hlsPullUrlMap == null ? null : Map.copyOf(hlsPullUrlMap);
    }

    /** 反编译默认值：三者皆 null。 */
    public StreamUrl() {
        this(null, null, null);
    }

    /** 是否完全没有可用地址（两个 Map 都空或 null，且无 SDK 数据）。 */
    public boolean isEmpty() {
        boolean noFlv = flvPullUrl == null || flvPullUrl.isEmpty();
        boolean noHls = hlsPullUrlMap == null || hlsPullUrlMap.isEmpty();
        return noFlv && noHls && liveCoreSdkData == null;
    }
}
