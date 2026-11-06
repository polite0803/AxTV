/*
 * 来源: 新建（电视直播功能，无原版对应）。
 * 原职责: 无。
 * 重构说明: 一个电视源的标识与拉取方式。D2 要求内置与用户源分组共存，
 *           通过 sourceId 将频道关联回其来源， UI 显示「来自哪份清单」。
 */
package axtv.iptv;

/** 电视源的形态与来源标识。 */
public record PlaylistSource(
    /** 源标识（内置 id 如 "best-fan"，用户 id 如 "user:123" */ String sourceId,
    /** 显示名称（如 "best-fan 内置清单" / "自定义源" */ String displayName,
    /** 拉取方式：内置 URL / 内置正文 / 用户 URL / 用户正文 */ SourceInput inputType,
    /** 对应的原始 URL 或 M3U 正文 */ String rawUrlOrContent,
    /** 最后拉取时间戳，用于 UI 健康度展示 */ long lastFetchedAt
) {
    /** 空源（初始化用，需后续填充真实值）。 */
    public static PlaylistSource empty() {
        return new PlaylistSource("", "", SourceInput.URL, "", 0L);
    }
}