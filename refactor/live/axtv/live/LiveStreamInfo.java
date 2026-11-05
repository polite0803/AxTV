/*
 * 来源: src/app/com/github/mytv/dv/model/StreamUrl.java + LiveCoreSdkData.java + LiveCorePullData.java
 * 原职责: 拉流地址集合：flvPullUrl / hlsPullUrlMap / liveCoreSdkData（内含 options.qualities 与 stream_data）
 * 重构说明:
 *   - 保留 flv/hls 两个 map 与「清晰度列表 + 每路 url」的可选 SDK 数据；
 *   - JSON 解析（options 可能是被转义的 JSON 字符串，需二次解包）属于 model 层，
 *     这里只承载解析结果：qualities 与 sdkUrls（sdkKey -> [flv, hls]），见 NOTES「待迁移」
 */
package axtv.live;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 直播拉流信息。
 *
 * <p>{@code sdkUrls} 的键是 {@link LiveStreamQuality#sdkKey()}，值是该清晰度的拉流地址，
 * 顺序固定为 flv 在前、hls 在后（原 {@code extractMainLiveUrls} 的顺序）。
 */
public final class LiveStreamInfo {

    private final Map<String, String> flvPullUrl;
    private final Map<String, String> hlsPullUrl;
    private final List<LiveStreamQuality> qualities;
    private final Map<String, List<String>> sdkUrls;

    public LiveStreamInfo(Map<String, String> flvPullUrl,
                          Map<String, String> hlsPullUrl,
                          List<LiveStreamQuality> qualities,
                          Map<String, List<String>> sdkUrls) {
        this.flvPullUrl = copy(flvPullUrl);
        this.hlsPullUrl = copy(hlsPullUrl);
        this.qualities = qualities == null ? List.of() : List.copyOf(qualities);
        this.sdkUrls = sdkUrls == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(sdkUrls));
    }

    public static LiveStreamInfo empty() {
        return new LiveStreamInfo(Map.of(), Map.of(), List.of(), Map.of());
    }

    public Map<String, String> flvPullUrl() {
        return flvPullUrl;
    }

    public Map<String, String> hlsPullUrl() {
        return hlsPullUrl;
    }

    /** SDK 数据里的清晰度列表；为空表示只能走 flv/hls map 的键名回退。 */
    public List<LiveStreamQuality> qualities() {
        return qualities;
    }

    /** {@code stream_data.data[sdkKey].main.{flv,hls}} 的抽取结果。 */
    public Map<String, List<String>> sdkUrls() {
        return sdkUrls;
    }

    public boolean hasSdkData() {
        return !qualities.isEmpty();
    }

    private static Map<String, String> copy(Map<String, String> source) {
        return source == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }

    @Override
    public String toString() {
        return "LiveStreamInfo(flv=" + flvPullUrl.keySet() + ", hls=" + hlsPullUrl.keySet()
                + ", qualities=" + qualities.size() + ", sdkUrls=" + sdkUrls.keySet() + ")";
    }
}
