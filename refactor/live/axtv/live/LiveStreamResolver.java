/*
 * 来源: src/app/com/github/mytv/dv/model/DataModelKt.java
 *       selectQualityIndex / resolveLiveStreamForQuality / resolveLiveStreamWithFallback /
 *       resolveSdkQualities / resolveLiveStreamBySdkData / extractMainLiveUrls（JSON 部分除外）
 * 原职责: 直播清晰度选择：SDK 数据优先，其次 flv/hls map 的键名回退
 * 重构说明:
 *   - 纯算法，不依赖 kotlinx.serialization；JSON 解包（options 可能是二次转义字符串、
 *     stream_data.data[key].main.{flv,hls}）由 model 层预解析后填进 LiveStreamInfo
 *   - 常量顺序、坐标公式、clamp 区间全部原样
 */
package axtv.live;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 直播选流。
 *
 * <h2>优先级</h2>
 * <ol>
 *   <li>{@code stream_url.live_core_sdk_data.pull_data.options.qualities} 非空 → 按偏好取下标
 *       （列表按 level 降序），url 来自 {@code stream_data.data[sdkKey].main.{flv,hls}}；
 *       取不到时用「同下标」的 flv/hls map 值兜底；</li>
 *   <li>否则走 {@link #resolveWithFallback}：先按 {@code FULL_HD1/HD1/SD2/SD1} 顺序过滤，
 *       再退化为 flv/hls 键的并集。</li>
 * </ol>
 */
public final class LiveStreamResolver {

    /** 原 {@code PULL_URL_MAP_ORDER_HIGH_TO_LOW}。 */
    public static final List<String> PULL_URL_ORDER_HIGH_TO_LOW = List.of("FULL_HD1", "HD1", "SD2", "SD1");

    /** 原 {@code PULL_URL_MAP_QUALITY_NAMES}。 */
    public static final Map<String, String> PULL_URL_QUALITY_NAMES = Map.of(
            "FULL_HD1", "蓝光",
            "HD1", "超清",
            "SD2", "高清",
            "SD1", "标清");

    /** 无可用清晰度名时的兜底（原 {@code str2 = "默认"}）。 */
    public static final String DEFAULT_QUALITY_NAME = "默认";

    private LiveStreamResolver() {
    }

    /** 原 {@code resolveLiveStreamBySdkData}：直接取 SDK 数据里最清晰的一档。 */
    public static ResolvedStream resolveBySdkData(LiveStreamInfo info) {
        List<ResolvedStream.Candidate> candidates = sdkCandidates(info);
        return candidates.isEmpty() ? null : candidates.get(0).toResolvedStream();
    }

    /** 原 {@code resolveLiveStreamForQuality}。找不到返回 null。 */
    public static ResolvedStream resolveForQuality(LiveStreamInfo info, LiveQualityPreference preference) {
        LiveQualityPreference pref = preference == null ? LiveQualityPreference.AUTO : preference;
        List<ResolvedStream.Candidate> candidates = sdkCandidates(info);
        if (!candidates.isEmpty()) {
            return candidates.get(selectQualityIndex(candidates.size(), pref)).toResolvedStream();
        }
        return resolveWithFallback(info, pref);
    }

    /**
     * 原 {@code resolveLiveStreamWithFallback}。
     *
     * <p>分支 1：{@code PULL_URL_ORDER_HIGH_TO_LOW} 中在 flv 或 hls map 存在的键，按该顺序过滤后取下标；
     * 命中即返回（即使 url 列表为空）。<br>
     * 分支 2：flv/hls 键的并集（保持插入顺序）取下标；url 为空则返回 null。
     */
    public static ResolvedStream resolveWithFallback(LiveStreamInfo info, LiveQualityPreference preference) {
        LiveQualityPreference pref = preference == null ? LiveQualityPreference.AUTO : preference;
        Map<String, String> flv = info.flvPullUrl();
        Map<String, String> hls = info.hlsPullUrl();

        List<String> ordered = new ArrayList<>();
        for (String key : PULL_URL_ORDER_HIGH_TO_LOW) {
            if (flv.containsKey(key) || hls.containsKey(key)) {
                ordered.add(key);
            }
        }
        if (!ordered.isEmpty()) {
            String key = ordered.get(selectQualityIndex(ordered.size(), pref));
            List<String> urls = urlsFor(key, flv, hls);
            String name = PULL_URL_QUALITY_NAMES.get(key);
            return new ResolvedStream(urls, name != null ? name : DEFAULT_QUALITY_NAME);
        }

        List<String> union = unionKeys(flv, hls);
        if (!union.isEmpty()) {
            String key = union.get(selectQualityIndex(union.size(), pref));
            List<String> urls = urlsFor(key, flv, hls);
            if (!urls.isEmpty()) {
                return new ResolvedStream(urls, highestQualityName(info));
            }
        }
        return null;
    }

    /**
     * 原 {@code resolveSdkQualities}：把 SDK 清晰度列表转成「带 url 的候选」，按 level 降序。
     *
     * <p>url 取值顺序：优先 {@code sdkUrls[sdkKey]}；没有则用
     * {@code flv.values[size-level]} 与 {@code hls.values[size-level]} 兜底（原实现的倒数取法）。
     */
    public static List<ResolvedStream.Candidate> sdkCandidates(LiveStreamInfo info) {
        if (info == null || !info.hasSdkData()) {
            return List.of();
        }
        List<LiveStreamQuality> qualities = info.qualities();
        List<String> flvValues = new ArrayList<>(info.flvPullUrl().values());
        List<String> hlsValues = new ArrayList<>(info.hlsPullUrl().values());
        Map<String, List<String>> sdkUrls = info.sdkUrls();

        List<ResolvedStream.Candidate> candidates = new ArrayList<>();
        for (LiveStreamQuality quality : qualities) {
            List<String> urls = sdkUrls.get(quality.sdkKey());
            if (urls == null || urls.isEmpty()) {
                urls = new ArrayList<>(2);
                String fromFlv = valueAtFromEnd(flvValues, flvValues.size() - quality.level());
                if (!LiveLogFormat.isBlank(fromFlv)) {
                    urls.add(fromFlv);
                }
                String fromHls = valueAtFromEnd(hlsValues, hlsValues.size() - quality.level());
                if (!LiveLogFormat.isBlank(fromHls)) {
                    urls.add(fromHls);
                }
            }
            if (!urls.isEmpty()) {
                candidates.add(new ResolvedStream.Candidate(quality.name(), quality.level(), urls));
            }
        }
        candidates.sort(Comparator.comparingInt(ResolvedStream.Candidate::level).reversed());
        return candidates;
    }

    /**
     * 原 {@code selectQualityIndex}：把偏好映射到候选下标。
     *
     * <p>候选按清晰度降序，因此：
     * AUTO / HIGHEST → 0；1080p → round(0.333×(n-1))；720p → round(0.5×(n-1))；
     * 540p → n-1。最后 clamp 到 [0, n-1]。
     */
    public static int selectQualityIndex(int size, LiveQualityPreference preference) {
        int last = size - 1;
        if (last < 0) {
            last = 0;
        }
        int index;
        switch (preference == null ? LiveQualityPreference.AUTO : preference) {
            case AUTO, HIGHEST -> index = 0;
            case P1080 -> index = (int) Math.round(last * 0.3333333333333333d);
            case P720 -> index = (int) Math.round(last * 0.5d);
            case P540 -> index = last;
            default -> index = 0;
        }
        return clamp(index, 0, last);
    }

    private static List<String> urlsFor(String key, Map<String, String> flv, Map<String, String> hls) {
        List<String> urls = new ArrayList<>(2);
        String fromFlv = blankToNull(flv.get(key));
        if (fromFlv != null) {
            urls.add(fromFlv);
        }
        String fromHls = blankToNull(hls.get(key));
        if (fromHls != null) {
            urls.add(fromHls);
        }
        return urls;
    }

    private static List<String> unionKeys(Map<String, String> flv, Map<String, String> hls) {
        LinkedHashSet<String> keys = new LinkedHashSet<>(flv.keySet());
        keys.addAll(hls.keySet());
        return new ArrayList<>(keys);
    }

    private static String highestQualityName(LiveStreamInfo info) {
        LiveStreamQuality highest = null;
        for (LiveStreamQuality quality : info.qualities()) {
            if (highest == null || quality.level() > highest.level()) {
                highest = quality;
            }
        }
        if (highest != null && !LiveLogFormat.isBlank(highest.name())) {
            return highest.name();
        }
        return DEFAULT_QUALITY_NAME;
    }

    private static String valueAtFromEnd(List<String> values, int index) {
        return index >= 0 && index < values.size() ? blankToNull(values.get(index)) : null;
    }

    private static String blankToNull(String value) {
        return LiveLogFormat.isBlank(value) ? null : value;
    }

    private static int clamp(int value, int min, int max) {
        if (min > max) {
            throw new IllegalArgumentException("Cannot coerce value to an empty range: maximum " + max
                    + " is less than minimum " + min + '.');
        }
        if (value < min) {
            return min;
        }
        return Math.min(value, max);
    }
}
