/*
 * 来源: 新建（电视台连通性探测，无原版对应）。
 * 原职责: 无。
 * 重构说明: 对单个候选地址做「能连上 + 能出字节」的轻量探测（PLAN R3/R4）。
 *           不做完整播放、不下载整段流——只读有限字节确认链路活着：
 *             - m3u8：读正文（通常 < 几 KB），取第一个分片地址再读一小块字节；
 *             - 直连流：读一小块字节确认不是死链。
 *           探测用生产播放同款中性 UA，不伪造 Referer（R5）。
 *           剂量常量集中在此处，调度器（G4）与 Android 侧都从此取，避免各处拍脑袋。
 */
package axtv.iptv;

import axtv.settings.HttpFetcher;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;

/** 单地址探测器。纯函数式（不持有状态），单测可注入假 HttpFetcher。 */
public final class LivenessProbe {

    /** 同时探测的最大并发数（PLAN R3）。大到不会拖慢首次探测，小到不打死弱网。 */
    public static final int MAX_CONCURRENCY = 4;

    /** 一批最多探测多少个地址（PLAN R3）。超过的留给下一轮，避免一次铺太多。 */
    public static final int BATCH_SIZE = 20;

    /** 单次读字节上限：够确认链路出字、够解析 m3u8 头部分片，不多占带宽。 */
    public static final int BYTE_CAP = 64 * 1024;

    /** 分片探读上限：只读第一块，不下载整个 TS。 */
    public static final int SEGMENT_BYTE_CAP = 32 * 1024;

    /** 探测用的中性 UA，与播放器一致（R5：不伪造）。 */
    public static final String NEUTRAL_UA =
        "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private LivenessProbe() {
    }

    /** 探测结果。 */
    public record ProbeResult(boolean ok, long elapsedMs, String error) {
        public static ProbeResult ok(long elapsedMs) {
            return new ProbeResult(true, elapsedMs, "");
        }

        public static ProbeResult fail(long elapsedMs, String error) {
            return new ProbeResult(false, elapsedMs, error == null ? "" : error);
        }
    }

    /**
     * 探测一个地址。
     *
     * @param url 候选播放地址（m3u8 或直连流）
     * @param fetcher 网络取数（Android 侧注入 OkHttp 实现）
     * @return 探测结果（成功带耗时；失败带原因）。不抛异常——失败也是合法结果。
     */
    public static ProbeResult probe(String url, HttpFetcher fetcher) {
        if (url == null || url.isBlank()) {
            return ProbeResult.fail(0L, "empty url");
        }
        if (fetcher == null) {
            return ProbeResult.fail(0L, "no fetcher");
        }
        long start = System.currentTimeMillis();
        HttpFetcher.StreamResponse res;
        try {
            res = fetcher.getStream(url, NEUTRAL_UA);
        } catch (Exception e) {
            return ProbeResult.fail(System.currentTimeMillis() - start, describe(e));
        }
        if (res.statusCode() < 200 || res.statusCode() >= 300) {
            closeQuiet(res.stream());
            return ProbeResult.fail(System.currentTimeMillis() - start, "HTTP " + res.statusCode());
        }
        try (InputStream in = res.stream()) {
            byte[] head = readBounded(in, BYTE_CAP);
            if (head.length == 0) {
                return ProbeResult.fail(System.currentTimeMillis() - start, "empty body");
            }
            String text = new String(head, 0, Math.min(head.length, 512), StandardCharsets.UTF_8);
            if (text.startsWith("#EXTM3U")) {
                String segment = firstSegmentUrl(head, url);
                if (segment != null) {
                    return probeSegment(segment, fetcher, start);
                }
                // 没解析到分片行（可能是 master playlist 只有流子清单），仍算链路通
            }
            return ProbeResult.ok(System.currentTimeMillis() - start);
        } catch (Exception e) {
            return ProbeResult.fail(System.currentTimeMillis() - start, describe(e));
        }
    }

    private static ProbeResult probeSegment(String segmentUrl, HttpFetcher fetcher, long startMs) {
        try {
            HttpFetcher.StreamResponse seg = fetcher.getStream(segmentUrl, NEUTRAL_UA);
            if (seg.statusCode() < 200 || seg.statusCode() >= 300) {
                closeQuiet(seg.stream());
                return ProbeResult.fail(System.currentTimeMillis() - startMs, "segment HTTP " + seg.statusCode());
            }
            try (InputStream in = seg.stream()) {
                byte[] head = readBounded(in, SEGMENT_BYTE_CAP);
                return head.length > 0
                    ? ProbeResult.ok(System.currentTimeMillis() - startMs)
                    : ProbeResult.fail(System.currentTimeMillis() - startMs, "segment empty");
            }
        } catch (Exception e) {
            return ProbeResult.fail(System.currentTimeMillis() - startMs, "segment: " + describe(e));
        }
    }

    /** 最多读 maxBytes 字节，返回实际读到的（可能更少）。 */
    private static byte[] readBounded(InputStream in, int maxBytes) throws IOException {
        byte[] buf = new byte[Math.min(maxBytes, 8192)];
        int total = 0;
        int n;
        while (total < maxBytes && (n = in.read(buf, total, Math.min(buf.length - total, maxBytes - total))) > 0) {
            total += n;
            if (total == buf.length && total < maxBytes) {
                byte[] grown = new byte[Math.min(maxBytes, buf.length * 2)];
                System.arraycopy(buf, 0, grown, 0, total);
                buf = grown;
            }
        }
        if (total == buf.length) {
            return buf;
        }
        byte[] exact = new byte[total];
        System.arraycopy(buf, 0, exact, 0, total);
        return exact;
    }

    /** 从 m3u8 正文里取第一个分片地址（非 # 开头、非空行），相对 baseUrl 解析成绝对。 */
    private static String firstSegmentUrl(byte[] m3u8Bytes, String baseUrl) {
        String text = new String(m3u8Bytes, StandardCharsets.UTF_8);
        for (String line : text.split("\n", -1)) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            try {
                return URI.create(baseUrl).resolve(trimmed).toString();
            } catch (IllegalArgumentException ignored) {
                return trimmed;
            }
        }
        return null;
    }

    private static void closeQuiet(InputStream in) {
        if (in != null) {
            try {
                in.close();
            } catch (IOException ignored) {
            }
        }
    }

    private static String describe(Throwable e) {
        String msg = e.getMessage();
        return msg == null || msg.isBlank() ? e.getClass().getSimpleName() : msg;
    }
}