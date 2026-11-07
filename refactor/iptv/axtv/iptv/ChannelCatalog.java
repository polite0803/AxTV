/*
 * 来源: 新建（电视直播功能，无原版对应）。
 * 原职责: 无。
 * 重构说明: 目录聚合的唯一权威。原先这里是一份零调用方的半成品（URL 源直接产出空表、
 *           isBuiltIn 由输入形态推断、按 hashCode 排序、candidatesForChannel 返回整组地址），
 *           Android 侧另写了一份聚合把它绕过。本轮改为接收【已解析】的源，
 *           修掉上述四处缺陷，并让 Android 侧只负责取数、把聚合/分组/可见性判定全部交回这里。
 */
package axtv.iptv;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 合并、分组、活性标注与可见性判定。全部纯函数。 */
public final class ChannelCatalog {

    private ChannelCatalog() {
    }

    /**
     * 一个已解析完的源。网络拉取由调用方做完再交进来——本类不碰网络，
     * 这样内置 URL 源与用户正文源走同一条聚合路径，不会再出现「URL 源产出空表」。
     *
     * @param builtIn 是否内置源。由调用方按来源声明，绝不从 {@link SourceInput} 推断
     *                （用户也可以推 URL，内置也可以是正文，形态与归属无关）
     */
    public record ResolvedSource(String sourceId, String displayName, boolean builtIn, Playlist playlist) {
        public ResolvedSource {
            sourceId = sourceId == null ? "" : sourceId;
            displayName = displayName == null ? "" : displayName;
        }
    }

    /** 频道级可见性计数。与「源健康度」是两个计数域，不得并排成可比数字（PLAN R2）。 */
    public record Visibility(int total, int probed, int usable, int hidden) {
    }

    /** 归一化频道名（D3）：去分辨率标注、全角转半角、去空白、转小写。 */
    public static String normalizeName(String name) {
        if (name == null || name.isBlank()) {
            return "";
        }
        String n = name.trim()
            .replaceAll("\\[HD\\]|\\[SD\\]|\\[1080\\]|\\[720\\]|\\[600\\]|\\[4K\\]|\\[S\\]|\\[4KHD\\]", "");
        StringBuilder sb = new StringBuilder(n.length());
        for (int i = 0; i < n.length(); i++) {
            int cp = n.codePointAt(i);
            // 全角数字/字母 U+FF10–U+FF5A → 半角，差值恒为 0xFEE0；其余字符原样保留
            sb.appendCodePoint(cp >= 0xFF10 && cp <= 0xFF5A ? cp - 0xFEE0 : cp);
        }
        return sb.toString().replaceAll("\\s+", "").toLowerCase(java.util.Locale.ROOT);
    }

    /**
     * 合并已解析的源为目录快照。
     *
     * <p>同名频道（归一化名一致）聚合为一条，候选地址按「内置优先 → 源的传入顺序」排列，
     * 展示名/logo/原始分组取优先级最高的那个源。传入顺序即优先级，调用方必须用有序表
     * （{@link BuiltInSources#URLS} 是 LinkedHashMap，顺序已按实测质量排好）。
     *
     * @param disabledBuiltInIds 用户禁用的内置 sourceId；只作用于内置源，用户源不受影响
     */
    public static CatalogSnapshot merge(List<ResolvedSource> sources, Set<String> disabledBuiltInIds) {
        Set<String> disabled = disabledBuiltInIds == null ? Set.of() : disabledBuiltInIds;
        List<ResolvedSource> ordered = new ArrayList<>();
        if (sources != null) {
            // 稳定排序：内置整体提到用户源之前，各自内部保持传入顺序
            for (ResolvedSource s : sources) {
                if (s != null && s.builtIn() && !disabled.contains(s.sourceId())) {
                    ordered.add(s);
                }
            }
            for (ResolvedSource s : sources) {
                if (s != null && !s.builtIn()) {
                    ordered.add(s);
                }
            }
        }

        Map<String, List<String>> candidates = new LinkedHashMap<>();
        Map<String, Channel> display = new LinkedHashMap<>();
        Map<String, String> displaySource = new LinkedHashMap<>();

        for (ResolvedSource source : ordered) {
            Playlist pl = source.playlist();
            if (pl == null || !pl.ok()) {
                continue;
            }
            for (Channel ch : pl.channels()) {
                String key = normalizeName(ch.name());
                if (key.isBlank()) {
                    continue;
                }
                String url = ch.url();
                if (url != null && !url.isBlank()) {
                    List<String> urls = candidates.computeIfAbsent(key, k -> new ArrayList<>());
                    if (!urls.contains(url)) {
                        urls.add(url);
                    }
                }
                display.putIfAbsent(key, ch);
                displaySource.putIfAbsent(key, source.displayName());
            }
        }

        List<CatalogSnapshot.Entry> entries = new ArrayList<>(display.size());
        for (Map.Entry<String, Channel> e : display.entrySet()) {
            Channel ch = e.getValue();
            List<String> urls = candidates.getOrDefault(e.getKey(), List.of());
            entries.add(new CatalogSnapshot.Entry(
                e.getKey(),
                ch.normalizedName().isBlank() ? ch.name() : ch.normalizedName(),
                ch.logoUrl(),
                ch.group(),
                displaySource.getOrDefault(e.getKey(), ""),
                urls
            ));
        }
        return CatalogSnapshot.of(System.currentTimeMillis(), entries);
    }

    /** 取某频道的候选地址；id 不存在返回空表（只返回该频道自己的，不是整组）。 */
    public static List<String> candidatesForChannel(CatalogSnapshot snapshot, String channelId) {
        if (snapshot == null || channelId == null) {
            return List.of();
        }
        CatalogSnapshot.Entry entry = snapshot.byId().get(channelId);
        return entry == null ? List.of() : entry.candidates();
    }

    /**
     * 频道级档位：任一候选 OK 就取其中最好的质量档；全部候选确证死亡才 DEAD；
     * 其余（含从未探测、部分失败）一律 UNKNOWN。
     */
    public static LivenessRecord.Tier channelTier(List<String> candidateUrls, Map<String, LivenessRecord> liveness) {
        if (candidateUrls == null || candidateUrls.isEmpty()) {
            return LivenessRecord.Tier.UNKNOWN;
        }
        Map<String, LivenessRecord> table = liveness == null ? Map.of() : liveness;
        LivenessRecord.Tier best = null;
        boolean allDead = true;
        for (String url : candidateUrls) {
            LivenessRecord r = table.get(url);
            if (r == null) {
                allDead = false;
                continue;
            }
            if (r.state() == LivenessRecord.State.OK) {
                allDead = false;
                LivenessRecord.Tier t = r.tier();
                if (best == null || t.ordinal() < best.ordinal()) {
                    best = t;
                }
            } else if (r.state() != LivenessRecord.State.DEAD) {
                allDead = false;
            }
        }
        if (best != null) {
            return best;
        }
        return allDead ? LivenessRecord.Tier.DEAD : LivenessRecord.Tier.UNKNOWN;
    }

    /**
     * 是否隐藏：只有【每个】候选都确证死亡才隐藏（PLAN R1）。
     * 没有候选地址时不隐藏——无地址不等于连不上，隐藏它会让用户永远看不到这条目。
     */
    public static boolean channelHidden(List<String> candidateUrls, Map<String, LivenessRecord> liveness) {
        return channelTier(candidateUrls, liveness) == LivenessRecord.Tier.DEAD;
    }

    /** 频道级计数，供列表页顶部单独一行展示（不与源健康度混排）。 */
    public static Visibility countVisibility(CatalogSnapshot snapshot, Map<String, LivenessRecord> liveness) {
        if (snapshot == null) {
            return new Visibility(0, 0, 0, 0);
        }
        int probed = 0;
        int usable = 0;
        int hidden = 0;
        for (CatalogSnapshot.Entry e : snapshot.entries()) {
            LivenessRecord.Tier tier = channelTier(e.candidates(), liveness);
            if (tier == LivenessRecord.Tier.DEAD) {
                hidden++;
                probed++;
            } else if (tier != LivenessRecord.Tier.UNKNOWN) {
                usable++;
                probed++;
            }
        }
        return new Visibility(snapshot.totalChannels(), probed, usable, hidden);
    }

    /**
     * 套用户分组修正 + 过滤已隐藏 + 分栏排序。
     * 排序口径与原 Android 侧一致：组内按展示名，组间按频道数降序、同数按标题。
     *
     * @param hideDead false 时连已判死的频道一并返回（「显示已隐藏」入口）
     */
    public static Map<String, List<CatalogSnapshot.Entry>> groupBy(
        CatalogSnapshot snapshot,
        GroupOverrides overrides,
        Map<String, LivenessRecord> liveness,
        boolean hideDead
    ) {
        Map<String, List<CatalogSnapshot.Entry>> byGroup = new LinkedHashMap<>();
        if (snapshot == null) {
            return byGroup;
        }
        GroupOverrides ov = overrides == null ? GroupOverrides.empty() : overrides;
        for (CatalogSnapshot.Entry e : snapshot.entries()) {
            if (hideDead && channelHidden(e.candidates(), liveness)) {
                continue;
            }
            String group = ov.groupFor(e.id(), e.rawGroup());
            byGroup.computeIfAbsent(group, k -> new ArrayList<>()).add(e);
        }
        Comparator<CatalogSnapshot.Entry> byName = Comparator.comparing(CatalogSnapshot.Entry::name);
        List<Map.Entry<String, List<CatalogSnapshot.Entry>>> groups = new ArrayList<>(byGroup.entrySet());
        for (Map.Entry<String, List<CatalogSnapshot.Entry>> g : groups) {
            g.getValue().sort(byName);
        }
        groups.sort(Comparator
            .comparingInt((Map.Entry<String, List<CatalogSnapshot.Entry>> g) -> g.getValue().size()).reversed()
            .thenComparing(Map.Entry::getKey));
        Map<String, List<CatalogSnapshot.Entry>> sorted = new LinkedHashMap<>();
        for (Map.Entry<String, List<CatalogSnapshot.Entry>> g : groups) {
            sorted.put(g.getKey(), List.copyOf(g.getValue()));
        }
        return sorted;
    }
}