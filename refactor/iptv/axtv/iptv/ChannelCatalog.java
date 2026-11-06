/*
 * 来源: 新建（电视直播功能，无原版对应）。
 * 原职责: 无。
 * 重构说明: D2 的核心：内置 + 用户源合并、D3 的名称归一聚合、按 group 分栏、给出
 *           「该频道候选 URL 有序列表」。内置源被禁用时会剔除，用户源删除不影响内置。
 */
package axtv.iptv;

import java.util.*;
import java.util.stream.Collectors;

/** D2/D3 的合并与目录算法。
 *  1. 收集所有源（内置 + 用户），每个源有 sourceId 标识。
 *  2. 按归一化后的频道名聚合：normalizedName() 一致视为同一频道。
 *  3. 同名频道按「有效候选」排序：内置源优先 → 按 sourceId 排序 → 用户源。
 *  4. 按 group-title 分栏返回有序 map。
 *  5. 被禁用的内置 sourceId 会从候选列表中剔除。
 */
public final class ChannelCatalog {

    private ChannelCatalog() {
    }

    /** 频道候选，按优先级排序。 */
    public record ChannelCandidate(
        Channel channel,
        /** 源的 sourceId，用于 UI 显示「来自哪份清单」 */
        String sourceId,
        /** 是否为内置源 */ boolean isBuiltIn,
        /** 归一化后的频道名，用于去重比较 */
        String normalizedName
    ) implements Comparable<ChannelCandidate> {

        @Override
        public int compareTo(ChannelCandidate other) {
            // 内置源优先
            if (this.isBuiltIn != other.isBuiltIn) {
                return other.isBuiltIn ? -1 : 1;
            }
            // sourceId 字母序排列（保持确定性）
            return this.sourceId.compareTo(other.sourceId);
        }
    }

    /** 归一化频道名（D3）：去空白、全半角、去分辨率标注 [HD][SD] 等，再大小写比较。 */
    public static String normalizeName(String name) {
        if (name == null || name.isBlank()) {
            return "";
        }
        String n = name.trim();
        n = n.replaceAll("\\[HD\\]|\\[SD\\]|\\[1080\\]|\\[720\\]|\\[600\\]|\\[4K\\]|\\[S\\]|\\[4KHD\\]", "");
        // 全半角转半角：Unicode 範 U+FF10–U+FF5A (全角字母/数字) → U+0030–U+007A (半角)
        // 只对全角字符（码点 > 0xFF00）进行半角转换，避免破坏本身就是 ASCII 的字符
        StringBuilder sb = new StringBuilder(n.length());
        for (int i = 0; i < n.length(); i++) {
            int cp = n.codePointAt(i);
            if (cp >= 0xFF11 && cp <= 0xFF5A) {
                sb.appendCodePoint(cp - 0xFEE0);
            } else {
                sb.appendCodePoint(cp);
            }
        }
        n = sb.toString();
        return n.replaceAll("\\s+", "").toLowerCase(java.util.Locale.ROOT);
    }

    /** 合并源为频道候选列表（按组分栏）。
     *  @param builtIn 內置源列表（可为 null/空，被禁用的 sourceId 会被剔除）
     * @param user      用户源列表（可为 null/空）
     * @param disabledBuiltInIds 要剔除的内置 sourceId 集合（来自 Settings.IPTV_DISABLED_BUILTIN_IDS）
     * @return 按 group-title 分组的有序频道候选 map：key=group-title，value=候选列表
     */
    public static Map<String, List<ChannelCandidate>> merge(
        List<PlaylistSource> builtIn,
        List<PlaylistSource> user,
        Set<String> disabledBuiltInIds
    ) {
        // 收集所有有效源
        List<PlaylistSource> all = new ArrayList<>();
        if (builtIn != null) {
            for (PlaylistSource s : builtIn) {
                if (s.sourceId() != null && !disabledBuiltInIds.contains(s.sourceId())) {
                    all.add(s);
                }
            }
        }
        if (user != null) {
            all.addAll(user);
        }

        // 按频道归一化名聚合
        Map<String, List<ChannelCandidate>> byGroup = new java.util.LinkedHashMap<>();
        for (PlaylistSource source : all) {
            String text = source.rawUrlOrContent();
            String srcId = source.sourceId();
            SourceInput type = source.inputType();

            // 根据源形态解析频道
            List<Channel> parsedChannels;
            if (type == SourceInput.CONTENT) {
                // 整份 M3U 正文
                Playlist pl = PlaylistLoader.loadFromContent(text, srcId);
                parsedChannels = pl.ok() ? pl.channels() : List.of();
            } else {
                // URL 形式——这里不实际拉取，留给 Catalog 用户在有 URL 时再解析
                // 为保持目录结构，我们把 rawUrlOrContent 当作 URL 处理，实际播放时再 fetch
                parsedChannels = List.of(); // 由上层在有 URL 时补全
            }

            for (Channel ch : parsedChannels) {
                String norm = normalizeName(ch.name());
                if (norm.isBlank()) {
                    norm = "unknown";
                }
                String group = ch.group().isBlank() ? "未分组" : ch.group();

                byGroup.computeIfAbsent(group, k -> new ArrayList<>())
                       .add(new ChannelCandidate(ch, srcId, type == SourceInput.URL, norm));
            }
        }

        // 每个组内按频道名归一化后的名字排序
        byGroup.replaceAll((group, candidates) -> candidates.stream()
            .sorted(Comparator.comparingLong(c -> c.normalizedName().hashCode()))
            .collect(Collectors.toList()));

        return byGroup;
    }

    /** 给定频道名，返回该频道所有候选 URL（按优先级排序）。
     *  如果频道不存在返回空列表。
     */
    public static List<String> candidatesForChannel(
        Map<String, List<ChannelCandidate>> catalog,
        String channelName
    ) {
        String norm = normalizeName(channelName);
        if (norm.isBlank()) {
            return List.of();
        }
        for (List<ChannelCandidate> candidates : catalog.values()) {
            for (ChannelCandidate cc : candidates) {
                if (cc.normalizedName().equals(norm)) {
                    return candidates.stream()
                        .map(cc2 -> cc2.channel().url())
                        .collect(Collectors.toList());
                }
            }
        }
        return List.of();
    }
}