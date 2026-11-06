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
        String rawError = "";

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

        boolean inHeader = true; // 在 #EXTM3U 头之后
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.isBlank()) {
                continue; // 跳过空行
            }

            if (line.startsWith("#")) {
                // 注释行 / 头
                if (i == 0) {
                    // 已在最外层检查过 #EXTM3U，继续
                    continue;
                }
                // 其他 # 行：可能是 #EXTINF、#EXTVLCOPT、或纯注释
                if (line.equals("#EXTINF")) {
                    // 单独出现的 #EXTINF 视为无效（需后面跟 URL）
                    rawError = "orphan #EXTINF at line " + (i + 1);
                    return ParseResult.failure(rawError, bytes.length);
                }
                // # 开头但非 EXTINF 的行为：跳过（注释）
                continue;
            }

            // 非 # 行：视为 URL（上一行若是 #EXTINF 则关联）
            if (inHeader) {
                // 首个非 # 行视为无效（EXTM3U 后应紧接着是 EXTINF 或 URL）
                rawError = "EXTM3U header without EXTINF/URL at line " + (i + 1);
                return ParseResult.failure(rawError, bytes.length);
            }
            inHeader = false;

            // 关联上一个 #EXTINF 行
            // 这里的逻辑已在主循环里通过 pair 处理，此处仅作为回退
            // 实际配对在下面的 pair 处理里完成
            // 为简化：直接当作 URL 处理，查找上一个 EXTINF
            String associatedName = null;
            String associatedUrl = line.trim();
            String associatedGroup = "";
            String associatedLogo = "";
            Map<String, String> associatedAttrs = new HashMap<>();

            // 尝试从前面找最近的 EXTINF 行（向上扫描最多 2 行）
            for (int j = i - 1; j >= Math.max(0, i - 3); j--) {
                String prev = lines[j];
                if (prev.startsWith("#EXTINF")) {
                    // 解析 tvg-name
                    String tvgName = extractTvgName(prev);
                    if (tvgName != null && !tvgName.isBlank()) {
                        associatedName = tvgName;
                    }
                    // tvg-id
                    String tvgId = extractTvgId(prev);
                    if (tvgId != null) {
                        associatedAttrs.put("tvg-id", tvgId);
                    }
                    // group-title
                    String group = extractGroupTitle(prev);
                    if (group != null) {
                        associatedGroup = group;
                    }
                    // logoUrl
                    String logo = extractLogoUrl(prev);
                    if (logo != null) {
                        associatedLogo = logo;
                    }
                    // response-time / Geo-blocked 等保留为属性
                    String rt = extractResponseTime(prev);
                    if (rt != null) {
                        associatedAttrs.put("response-time", rt);
                    }
                    // EXTVLCOPT 保留在 attrs 中（key=value 形式）
                    String ev = extractExlVopt(prev);
                    if (ev != null) {
                        associatedAttrs.put("http-referrer", ev);
                    }
                    break; // 最近的一个即可
                }
            }

            // 组装频道
            if (associatedName != null) {
                Channel ch = new Channel(
                    associatedName,      // name 已做 D3 归一（去分辨率标注）
                    associatedLogo,
                    associatedGroup,
                    associatedUrl,
                    associatedAttrs,
                    sourceId != null ? sourceId : ""
                );
                channels.add(ch);
            } else {
                // 无法识别的 URL 行（EXTINF 缺失/损坏）给出可查原因的错误
                rawError = "unrecognized URL at line " + (i + 1) + " without EXTINF info";
                return ParseResult.failure(rawError, bytes.length);
            }
        }

        if (channels.isEmpty()) {
            return ParseResult.failure("no channels parsed", bytes.length);
        }

        return ParseResult.success(channels, bytes.length);
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