/*
 * 来源: 新建（电视直播功能，无原版对应；原版 2.3.43 的「直播」是抖音 webcast，见 refactor/live/NOTES.md，
 *       与本模块的电视台 IPTV 流无关）。
 * 原职责: 无。
 * 重构说明: 一个 M3U 播放列表里的单个频道。字段名对齐 M3U8 规范（RFC 8216 的 EXTINF 属性）与
 *           实测到的社区源格式（iptv-org / best-fan / boydqz）。
 */
package axtv.iptv;

/**
 * 一个频道。
 *
 * <p>同一频道可能在多个源里各出现一次，{@link #sourceId} 用于把候选 URL 还原回来源，
 * 供 UI 显示「本频道来自哪份源」（D2：内置与用户源分组共存，不混排）。
 *
 * @param name     频道显示名（已做 D3 归一：去空白/全半角/大小写）
 * @param logoUrl  tvg-logo，可能为空
 * @param group    group-title（如「央视台」「News」），可能为空
 * @param url      播放地址（.m3u8）
 * @param attrs    EXTINF 上的其它属性（tvg-id / response-time / [Geo-blocked] 之类），保留原样不丢弃
 * @param sourceId 来源标识（内置源 id 或用户源 id）
 */
public record Channel(String name, String logoUrl, String group, String url, java.util.Map<String, String> attrs, String sourceId) {

    public Channel {
        name = name == null ? "" : name.trim();
        logoUrl = logoUrl == null ? "" : logoUrl.trim();
        group = group == null ? "" : group.trim();
        url = url == null ? "" : url.trim();
        attrs = attrs == null ? java.util.Map.of() : java.util.Map.copyOf(attrs);
        sourceId = sourceId == null ? "" : sourceId.trim();
    }

    /** 空频道（解析失败时的占位，由调用方决定是否丢弃）。 */
    public static Channel empty() {
        return new Channel("", "", "", "", java.util.Map.of(), "");
    }

    /** 去掉 EXTINF 里常见的分辨率/状态标注，得到可比对的频道名（D3 归一的一步）。 */
    public String normalizedName() {
        String n = name;
        n = n.replaceAll("\\[HD\\]|\\[SD\\]|\\[1080\\]|\\[720\\]|\\[600\\]|\\[4K\\]|\\[S\\]|\\[4KHD\\]", "");
        return n.trim();
    }
}