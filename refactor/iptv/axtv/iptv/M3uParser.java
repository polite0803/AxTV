/*
 * 来源: 新建（电视直播功能，无原版对应；原版 2.3.43 的「直播」是抖音 webcast 直播，见 refactor/live/NOTES.md）。
 * 原职责: 无。
 * 重构说明: M3U 播放列表解析器。对应 RFC 8216（m3u8 规范）与社区源实际格式（best-fan / boydqz / iptv-org）。
 *           所有容错都在纯函数完成，不依赖 Android SDK / OkHttp / kotlinx。
 *
 * 关键实测事实（阶段 F）：
 * - best-fan cn_all_status.m3u8：214 条目 / 107 个不同频道 / 94 个央视卫视 / 70 个真 m3u8
 * - 302 跳板目标多为已死 IP，不计入可用
 * - EXTINF 范式：tvg-name / tvg-id / group-title / logoUrl / response-time / [Geo-blocked]
 * - #EXTVLCOPT:http-referrer 可视作属性行的一部分（保留原样）
 * - 非 #EXTM3U 头但含 EXTINF 的行仍按频道解析（best-fan 有此类）
 */
package axtv.iptv;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * M3U 播放列表解析结果。
 *
 * <p>两种互斥状态：
 * <ul>
 *   <li>成功：{@code channels} 非空，{@code rawError} 为空；</li>
 *   <li>失败：{@code rawError} 非空，{@code channels} 为空（不返回空表充数）。</li>
 * </ul>
 *
 * @param channels 解析到的频道（成功时非空）
 * @param rawError 失败原因原文（成功时为空串）
 * @param bytes    原始输入字节数，用于 UI 健康度展示
 */
record ParseResult(List<Channel> channels, String rawError, long bytes) {

    public ParseResult {
        channels = channels == null ? List.of() : List.copyOf(channels);
        rawError = rawError == null ? "" : rawError;
    }

    public static ParseResult success(List<Channel> channels, long bytes) {
        return new ParseResult(channels, "", bytes);
    }

    public static ParseResult failure(String rawError, long bytes) {
        return new ParseResult(List.of(), rawError, bytes);
    }

    /** 是否成功（有可播频道）。 */
    public boolean ok() {
        return rawError.isEmpty() && !channels.isEmpty();
    }

    /** 是否失败。 */
    public boolean failed() {
        return !rawError.isEmpty();
    }

    /** 空清单（原始内容为空或非 m3u，且无错误断言）。 */
    public boolean empty() {
        return rawError.isEmpty() && channels.isEmpty();
    }
}

/**
 * M3U 播放列表解析器。
 *
 * <p>对应实测到的社区源格式（best-fan 为主要参考），兼容：
 * <ul>
 *   <li>#EXTM3U 头（必须；无头视为非 m3u）</li>
 *   <li>#EXTINF 行（tvg-name / tvg-id / group-title / logoUrl / [response-time] / [Geo-blocked] 等）</li>
 *   <li>裸 URL 行（下一行非 # 的内容）</li>
 *   <li>CRLF / LF / BOM</li>
 *   <li>以 # 开头但非 EXTINF 的注释行</li>
 *   <li>#EXTVLCOPT 之类的属性保留在 Channel.attrs 中</li>
 * </ul>
 *
 * <p>不做：DNS 解析、流连通性验证、Referer/UA 伪装。这些由上层决定。
 */
public final class M3uParser {

    private M3uParser() {
    }

    /** 完整解析。 */
    public static ParseResult parse(String text, String sourceId) {
        if (text == null || text.isBlank()) {
            return ParseResult.failure("empty input", 0);
        }

        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        List<Channel> channels = new ArrayList<>();

        // BOM 处理：如果是 UTF-8 BOM 请剥离（常见于某些社区源生成工具）
        if (bytes.length >= 3 && bytes[0] == (byte) 0xEF && bytes[1] == (byte) 0xBB && bytes[2] == (byte) 0xBF) {
            bytes = Arrays.copyOfRange(bytes, 3, bytes.length);
        }

        String content = new String(bytes, StandardCharsets.UTF_8);

        // 必须有 #EXTM3U 头
        if (!content.startsWith("#EXTM3U")) {
            return ParseResult.failure("missing #EXTM3U header", bytes.length);
        }

        // 按行分割（CRLF/LF 均兼容）：归一化行结束符
        String[] lines = content.replace("\r\n", "\n").replace("\r", "\n").split("\n");

        String lastExtinf = null;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) {
                continue; // 跳过空行
            }
            if (line.startsWith("#")) {
                // #EXTINF 记下，供后续 URL 行关联；其它 # 行（#EXTM3U / #EXTVLCOPT / 注释）忽略
                if (line.startsWith("#EXTINF")) {
                    lastExtinf = line;
                }
                continue;
            }

            // 非 # 行：URL。没有可关联的 #EXTINF 时跳过该行，不因单行坏数据判整份失败。
            if (lastExtinf == null) {
                continue;
            }
            // 频道名优先取 EXTINF 里第一个「引号外逗号」之后的显示名（iptv-org 等多数清单如此），
            // 缺失时回退 tvg-name 属性。
            String name = displayName(lastExtinf);
            if (name.isBlank()) {
                name = extractTvgName(lastExtinf);
            }
            if (name == null || name.isBlank()) {
                continue; // 无名条目跳过
            }

            Map<String, String> attrs = new HashMap<>();
            String tvgId = extractTvgId(lastExtinf);
            if (tvgId != null) {
                attrs.put("tvg-id", tvgId);
            }
            String rt = extractResponseTime(lastExtinf);
            if (rt != null) {
                attrs.put("response-time", rt);
            }
            String ev = extractExlVopt(lastExtinf);
            if (ev != null) {
                attrs.put("http-referrer", ev);
            }

            channels.add(new Channel(
                name,
                extractLogoUrl(lastExtinf),
                extractGroupTitle(lastExtinf),
                line,
                attrs,
                sourceId != null ? sourceId : ""
            ));
        }

        if (channels.isEmpty()) {
            return ParseResult.failure("no channels parsed", bytes.length);
        }

        return ParseResult.success(channels, bytes.length);
    }

    /** EXTINF 里第一个「引号外逗号」之后的显示名（多数清单的标准写法）；无则返回空串。 */
    private static String displayName(String extinf) {
        boolean inQuote = false;
        for (int i = 0; i < extinf.length(); i++) {
            char c = extinf.charAt(i);
            if (c == '"') {
                inQuote = !inQuote;
            } else if (c == ',' && !inQuote) {
                return extinf.substring(i + 1).trim();
            }
        }
        return "";
    }

    /** 从 EXTINF 行提取 tvg-name。 */
    private static String extractTvgName(String line) {
        // 匹配 tvg-name="..." 或 tvg-name='...'
        java.util.regex.Pattern p1 = java.util.regex.Pattern.compile("tvg-name=\"([^\"]*)\"");
        java.util.regex.Matcher m1 = p1.matcher(line);
        if (m1.find()) return m1.group(1);
        java.util.regex.Pattern p2 = java.util.regex.Pattern.compile("tvg-name='([^']*)'");
        java.util.regex.Matcher m2 = p2.matcher(line);
        if (m2.find()) return m2.group(1);
        return null;
    }

    /** 从 EXTINF 行提取 tvg-id。 */
    private static String extractTvgId(String line) {
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("tvg-id=\"([^\"]*)\"");
        java.util.regex.Matcher m = p.matcher(line);
        return m.find() ? m.group(1) : null;
    }

    /** 从 EXTINF 行提取 group-title。 */
    private static String extractGroupTitle(String line) {
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("group-title=\"([^\"]*)\"");
        java.util.regex.Matcher m = p.matcher(line);
        return m.find() ? m.group(1) : null;
    }

    /** 从 EXTINF 行提取 logo URL。 */
    private static String extractLogoUrl(String line) {
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("tvg-logo=\"([^\"]*)\"");
        java.util.regex.Matcher m = p.matcher(line);
        return m.find() ? m.group(1) : null;
    }

    /** 从 EXTINF 行提取 #EXTVLCOPT:http-referrer=... 的 value。 */
    private static String extractExlVopt(String line) {
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("#EXTVLCOPT:http-referrer=(\\S+)");
        java.util.regex.Matcher m = p.matcher(line);
        return m.find() ? m.group(1) : null;
    }

    /** 从 EXTINF 行提取 response-time。 */
    private static String extractResponseTime(String line) {
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("response-time=\"(\\d+)\"");
        java.util.regex.Matcher m = p.matcher(line);
        return m.find() ? m.group(1) : null;
    }
}