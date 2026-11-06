/*
 * 来源: 新建（电视直播功能，无原版对应）。
 * 原职责: 无。
 * 重构说明: 把用户推送的文本判成 URL 还是整份 M3U 正文（D1），并提供两种形态的解析入口。
 *           纯函数，不依赖网络，单测可覆盖。
 */
package axtv.iptv;

/**
 * 源形态判定器。
 *
 * <p>判定规则（对齐实测到的社区源格式）：
 * <pre>
 *   去掉前导空白后：
 *     以 "#EXTM3U" 或 "#EXTINF" 开头  →  CONTENT（整份 m3u 正文）
 *     其它                            →  URL
 * </pre>
 * 空串与纯空白一律判 URL（后续由 URL 拉取失败给出显式错误，不在此处静默吞掉）。
 */
public final class SourceInputDetector {

    private SourceInputDetector() {
    }

    private static final String EXT_M3U = "#EXTM3U";
    private static final String EXT_INF = "#EXTINF";

    /** 判定。 */
    public static SourceInput detect(String text) {
        if (text == null) {
            return SourceInput.URL;
        }
        String stripped = text.replaceFirst("^\\s+", "");
        if (stripped.startsWith(EXT_M3U) || stripped.startsWith(EXT_INF)) {
            return SourceInput.CONTENT;
        }
        return SourceInput.URL;
    }

    /** 是否整份正文形态。 */
    public static boolean isContent(String text) {
        return detect(text) == SourceInput.CONTENT;
    }
}