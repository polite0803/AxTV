/*
 * 来源: src/app/com/github/mytv/p004dv/model/DataModelKt.java
 * 原职责: 模型层的公共工具集 ——
 *         1) 计数格式化（亿/万/k）
 *         2) aweme 过滤（只要普通视频 / 只要可播的普通视频）
 *         3) 码率画质排序（选「最高画质」的唯一口径）
 *         4) 直播流的档位选择与回退（flv/hls 双协议、SDK 直出档位）
 * 重构说明: 拆成 DataModels（通用工具 + 直播选档）。kotlinx JsonElement 相关的
 *           「embedded JSON 字符串解包 / qualities 解析」依赖具体 JSON 库，核心层不绑定，
 *           改为由调用方把已解析好的结构传进来（见 NOTES.md「待迁移」）。
 */
package axtv.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 模型层公共工具。全部为无状态静态方法，与原 {@code DataModelKt} 的顶层函数一一对应。
 */
public final class DataModels {

    /** 直播拉流档位从高到低的固定顺序（原 {@code PULL_URL_MAP_ORDER_HIGH_TO_LOW}）。 */
    public static final List<String> PULL_URL_MAP_ORDER_HIGH_TO_LOW =
            List.of("FULL_HD1", "HD1", "SD2", "SD1");

    /** 档位 → 中文名（原 {@code PULL_URL_MAP_QUALITY_NAMES}）。 */
    public static final Map<String, String> PULL_URL_MAP_QUALITY_NAMES = qualityNames();

    private DataModels() {
    }

    private static Map<String, String> qualityNames() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("FULL_HD1", "蓝光");
        m.put("HD1", "超清");
        m.put("SD2", "高清");
        m.put("SD1", "标清");
        return Map.copyOf(m);
    }

    // ---------------------------------------------------------------- 计数格式化

    /**
     * 原 {@code formatCount(long)}。
     *
     * <ul>
     *   <li>&ge; 1 亿 → {@code "%.1f亿"}</li>
     *   <li>&ge; 1 万 → {@code "%.1f万"}</li>
     *   <li>&ge; 1000 → {@code "%.1fk"}</li>
     *   <li>否则原样数字</li>
     * </ul>
     * 与原实现一致使用默认 Locale（中文环境下小数点为 {@code .}）；不要改成 Locale.ROOT，
     * 否则在部分区域设置下输出会与原 App 不同。
     */
    public static String formatCount(long value) {
        if (value >= 100_000_000L) {
            return String.format("%.1f亿", value / 100_000_000.0d);
        }
        if (value >= 10_000L) {
            return String.format("%.1f万", value / 10_000.0d);
        }
        if (value >= 1000L) {
            return String.format("%.1fk", value / 1000.0d);
        }
        return String.valueOf(value);
    }

    // ---------------------------------------------------------------- aweme 过滤

    /** 原 {@code isPrimaryAweme}：{@code aweme_type == 0} 才是可播的普通视频（1/2 为图文、直播预告等）。 */
    public static boolean isPrimaryAweme(Aweme aweme) {
        return aweme != null && aweme.awemeType() == 0;
    }

    /** 原 {@code takeIfPrimaryAweme}：不是普通视频就返回 null。 */
    public static Aweme takeIfPrimaryAweme(Aweme aweme) {
        return isPrimaryAweme(aweme) ? aweme : null;
    }

    /** 原 {@code filterPrimaryAwemes}：null 入参返回空表，过滤出 awemeType == 0 的项。 */
    public static List<Aweme> filterPrimaryAwemes(List<Aweme> list) {
        if (list == null) {
            return List.of();
        }
        List<Aweme> out = new ArrayList<>();
        for (Aweme a : list) {
            if (isPrimaryAweme(a)) {
                out.add(a);
            }
        }
        return out;
    }

    /**
     * 原 {@code filterPrimaryPlayableAwemes}：在 {@link #filterPrimaryAwemes} 基础上
     * 再要求 {@code video.play_addr.url_list} 非空 —— 有些作品只有封面没有播放地址。
     */
    public static List<Aweme> filterPrimaryPlayableAwemes(List<Aweme> list) {
        if (list == null) {
            return List.of();
        }
        List<Aweme> out = new ArrayList<>();
        for (Aweme a : list) {
            if (isPrimaryAweme(a) && a.video().playAddr().hasUrls()) {
                out.add(a);
            }
        }
        return out;
    }

    // ---------------------------------------------------------------- 码率排序

    /**
     * 原 {@code highestQualityBitRate(List<BitRate>)}：取画质最高的一路码率。
     *
     * <p>先剔除 {@code play_addr.url_list} 为空的码率，再按以下顺序**逐级比较**取最大值：
     * <ol>
     *   <li>像素数 {@code play_addr.width * play_addr.height}</li>
     *   <li>帧率 {@code FPS}</li>
     *   <li>编码分（HEVC 记 1，H.264 记 0）</li>
     *   <li>码率值 {@code bit_rate}</li>
     *   <li>档位名含 {@code adapt_lowest} 的排后面（同分时优先具体档位）</li>
     * </ol>
     * 列表为空或无可用码率时返回 null。
     */
    public static BitRate highestQualityBitRate(List<BitRate> list) {
        BitRate best = null;
        for (BitRate br : list) {
            if (!br.hasUrls()) {
                continue;
            }
            if (best == null || compareQuality(br, best) > 0) {
                best = br;
            }
        }
        return best;
    }

    /** 画质比较器：正数表示 a 更高。与原四级 thenBy 比较器等价。 */
    static int compareQuality(BitRate a, BitRate b) {
        int c = Integer.compare(a.playAddr().pixelCount(), b.playAddr().pixelCount());
        if (c != 0) {
            return c;
        }
        c = Integer.compare(a.fps(), b.fps());
        if (c != 0) {
            return c;
        }
        c = Integer.compare(encodingScore(a), encodingScore(b));
        if (c != 0) {
            return c;
        }
        c = Integer.compare(a.bitRateValue(), b.bitRateValue());
        if (c != 0) {
            return c;
        }
        return Integer.compare(isAdaptLowest(a) ? 0 : 1, isAdaptLowest(b) ? 0 : 1);
    }

    private static int encodingScore(BitRate br) {
        return br.isHevc() ? 1 : 0;
    }

    private static boolean isAdaptLowest(BitRate br) {
        return br.gearName().toLowerCase(Locale.ROOT).contains("adapt_lowest");
    }

    // ---------------------------------------------------------------- 直播选档

    /**
     * 原 {@code selectQualityIndex(int size, lq5)}：在「按档位从高到低排好序」的列表里，
     * 把用户选的清晰度映射成下标。
     *
     * <ul>
     *   <li>自动 / 最高画质 → 0（最高档）</li>
     *   <li>1080p → {@code round((size-1) * 1/3)}</li>
     *   <li>720p → {@code round((size-1) * 1/2)}</li>
     *   <li>540p → {@code size-1}（最低档）</li>
     * </ul>
     * 结果再夹到 {@code [0, size-1]}。
     */
    public static int selectQualityIndex(int size, VideoPlayUrlResolver.TargetResolution target) {
        int last = size - 1;
        if (last < 0) {
            last = 0;
        }
        int index;
        switch (target) {
            case HIGHEST -> index = 0;
            case P1080 -> index = (int) Math.round(last * (1.0d / 3.0d));
            case P720 -> index = (int) Math.round(last * 0.5d);
            case P540 -> index = last;
            default -> index = 0;
        }
        return Math.min(Math.max(index, 0), last);
    }

    /**
     * 原 {@code resolveLiveStreamBySdkData(StreamUrl)}：SDK 直出档位里取最高档。
     * 没有可用档位返回 null。
     */
    public static ResolvedLiveStream resolveLiveStreamBySdkData(List<ResolvedStreamQuality> qualities) {
        if (qualities == null || qualities.isEmpty()) {
            return null;
        }
        return qualities.get(0).toResolvedLiveStream();
    }

    /** 原 {@code resolveLiveStreamForQuality}：有 SDK 档位就按下标挑，否则走 flv/hls 回退。 */
    public static ResolvedLiveStream resolveLiveStreamForQuality(StreamUrl streamUrl,
                                                                 VideoPlayUrlResolver.TargetResolution target,
                                                                 List<ResolvedStreamQuality> sdkQualities,
                                                                 List<LiveStreamQuality> pullQualities) {
        if (sdkQualities != null && !sdkQualities.isEmpty()) {
            int index = selectQualityIndex(sdkQualities.size(), target);
            return sdkQualities.get(index).toResolvedLiveStream();
        }
        return resolveLiveStreamWithFallback(streamUrl, target, pullQualities);
    }

    /**
     * 原 {@code resolveLiveStreamWithFallback(StreamUrl, lq5)}。
     *
     * <p>两段逻辑：
     * <ol>
     *   <li>先只看标准档位（FULL_HD1 / HD1 / SD2 / SD1），flv 与 hls 任一存在即视为该档可选，
     *       按下标选一档，同档 flv 优先、hls 其次，档位名用中文映射表</li>
     *   <li>标准档位一个都没有时，退化为「把所有 flv/hls 的 key 合起来按任意顺序排」，
     *       同样按下标选一个 key，qualityName 取 qualities 里 level 最大那条的 name，缺省「默认」</li>
     * </ol>
     *
     * @param pullQualities SDK options 里解析出的档位列表（用于取名），可为空
     * @return 无任何可用地址时返回 null
     */
    public static ResolvedLiveStream resolveLiveStreamWithFallback(StreamUrl streamUrl,
                                                                   VideoPlayUrlResolver.TargetResolution target,
                                                                   List<LiveStreamQuality> pullQualities) {
        Map<String, String> flv = streamUrl.flvPullUrl() == null ? Map.of() : streamUrl.flvPullUrl();
        Map<String, String> hls = streamUrl.hlsPullUrlMap() == null ? Map.of() : streamUrl.hlsPullUrlMap();

        List<String> standard = new ArrayList<>();
        for (String key : PULL_URL_MAP_ORDER_HIGH_TO_LOW) {
            if (flv.containsKey(key) || hls.containsKey(key)) {
                standard.add(key);
            }
        }

        if (!standard.isEmpty()) {
            String key = standard.get(selectQualityIndex(standard.size(), target));
            List<String> urls = pickUrls(flv, hls, key);
            String name = PULL_URL_MAP_QUALITY_NAMES.get(key);
            return new ResolvedLiveStream(urls, name == null ? "默认" : name);
        }

        // 非标准档位：key 取并集（原实现用 LinkedHashSet 合并后转 List）
        List<String> keys = new ArrayList<>();
        for (String k : flv.keySet()) {
            if (!keys.contains(k)) {
                keys.add(k);
            }
        }
        for (String k : hls.keySet()) {
            if (!keys.contains(k)) {
                keys.add(k);
            }
        }
        if (keys.isEmpty()) {
            return null;
        }
        String key = keys.get(selectQualityIndex(keys.size(), target));
        List<String> urls = pickUrls(flv, hls, key);
        if (urls.isEmpty()) {
            return null;
        }
        String name = highestLevelQualityName(pullQualities);
        return new ResolvedLiveStream(urls, name);
    }

    /** 同档位下 flv 优先、hls 其次，各自剔除空白串。 */
    private static List<String> pickUrls(Map<String, String> flv, Map<String, String> hls, String key) {
        List<String> urls = new ArrayList<>(2);
        String flvUrl = flv.get(key);
        if (flvUrl != null && !isBlank(flvUrl)) {
            urls.add(flvUrl);
        }
        String hlsUrl = hls.get(key);
        if (hlsUrl != null && !isBlank(hlsUrl)) {
            urls.add(hlsUrl);
        }
        return urls;
    }

    /** 取 level 最大的档位名，空白时用「默认」。 */
    private static String highestLevelQualityName(List<LiveStreamQuality> qualities) {
        if (qualities == null || qualities.isEmpty()) {
            return "默认";
        }
        LiveStreamQuality best = null;
        for (LiveStreamQuality q : qualities) {
            if (best == null || q.level() > best.level()) {
                best = q;
            }
        }
        if (best == null || best.name() == null || isBlank(best.name())) {
            return "默认";
        }
        return best.name();
    }

    /**
     * 原 {@code resolveSdkQualities(StreamUrl)} 的纯计算部分。
     *
     * <p>原实现要从 {@code live_core_sdk_data.pull_data.stream_data} 这个「可能是 JSON 字符串、
     * 也可能是 JSON 对象」的嵌套结构里，按 {@code data[quality.sdk_key].main.flv|hls} 取地址；
     * JSON 导航依赖具体 JsonCodec，本层不做。调用方只需先取出两张表：
     * <ul>
     *   <li>{@code mainUrlsBySdkKey}：sdk_key → 该档位的主地址列表（flv 在前、hls 在后）</li>
     *   <li>取不到 {@code data} 节点时传 null，方法会退化为「按 level 从 flv/hls 的 values 里倒序取」</li>
     * </ul>
     * 返回按 level 降序排好的档位列表（只保留有地址的）。
     */
    public static List<ResolvedStreamQuality> resolveSdkQualities(List<LiveStreamQuality> qualities,
                                                                  Map<String, List<String>> mainUrlsBySdkKey,
                                                                  List<String> flvUrls,
                                                                  List<String> hlsUrls) {
        if (qualities == null || qualities.isEmpty()) {
            return List.of();
        }
        List<ResolvedStreamQuality> out = new ArrayList<>();
        for (LiveStreamQuality q : qualities) {
            List<String> urls;
            if (mainUrlsBySdkKey != null) {
                urls = mainUrlsBySdkKey.get(q.sdkKey());
            } else {
                // 退化：flv/hls 的 values 按 level 从末尾往前取（原实现 size - level 的语义）
                urls = new ArrayList<>(2);
                String flv = elementAt(flvUrls, (flvUrls == null ? 0 : flvUrls.size()) - q.level());
                if (flv != null && !isBlank(flv)) {
                    urls.add(flv);
                }
                String hls = elementAt(hlsUrls, (hlsUrls == null ? 0 : hlsUrls.size()) - q.level());
                if (hls != null && !isBlank(hls)) {
                    urls.add(hls);
                }
            }
            if (urls == null || urls.isEmpty()) {
                continue;
            }
            out.add(new ResolvedStreamQuality(q.name(), q.level(), urls));
        }
        // 按 level 降序
        out.sort((a, b) -> Integer.compare(b.level(), a.level()));
        return out;
    }

    private static String elementAt(List<String> list, int index) {
        if (list == null || index < 0 || index >= list.size()) {
            return null;
        }
        return list.get(index);
    }

    /** 对应 kotlin {@code CharSequence.isBlank()}（全空白视为空）。 */
    private static boolean isBlank(String s) {
        if (s == null) {
            return true;
        }
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isWhitespace(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
