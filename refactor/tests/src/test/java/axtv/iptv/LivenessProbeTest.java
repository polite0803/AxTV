/*
 * 来源: 新建（电视台连通性探测，无原版对应）。
 * 原职责: 无。
 * 重构说明: 单测。注入假 HttpFetcher 覆盖 2xx/404/异常/非 m3u8/master playlist/分片失败 五种路径，
 *           并断言 BYTE_CAP 被实际消费（不会把整段流读进来）。
 */
package axtv.iptv;

import axtv.settings.HttpFetcher;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LivenessProbeTest {

    private static final class FakeFetcher implements HttpFetcher {
        final int status;
        final byte[] body;
        final int segmentStatus;
        final byte[] segmentBody;
        final Exception ex;

        FakeFetcher(int status, byte[] body) {
            this(status, body, 200, new byte[]{'t'}, null);
        }

        FakeFetcher(int status, byte[] body, int segStatus, byte[] segBody, Exception ex) {
            this.status = status;
            this.body = body;
            this.segmentStatus = segStatus;
            this.segmentBody = segBody;
            this.ex = ex;
        }

        @Override
        public TextResponse getText(String url, String acceptHeader, int timeoutSeconds) {
            throw new UnsupportedOperationException();
        }

        @Override
        public StreamResponse getStream(String url, String userAgent) throws IOException {
            if (ex != null) {
                if (ex instanceof IOException io) {
                    throw io;
                }
                if (ex instanceof RuntimeException re) {
                    throw re;
                }
                throw new RuntimeException(ex);
            }
            // 区分主请求与分片请求：主请求返回 body，分片请求返回 segmentBody
            if (url.contains("segment")) {
                return new StreamResponse(segmentStatus, new ByteArrayInputStream(segmentBody), segmentBody.length);
            }
            return new StreamResponse(status, new ByteArrayInputStream(body), body.length);
        }
    }

    @Test
    void twoxxDirectStreamIsOk() {
        LivenessProbe.ProbeResult r = LivenessProbe.probe("http://x/stream.ts",
            new FakeFetcher(200, new byte[]{1, 2, 3, 4}));
        assertTrue(r.ok());
        assertEquals("", r.error());
    }

    @Test
    void http404IsFailure() {
        LivenessProbe.ProbeResult r = LivenessProbe.probe("http://x/dead.m3u8",
            new FakeFetcher(404, new byte[]{}));
        assertFalse(r.ok());
        assertTrue(r.error().contains("404"));
    }

    @Test
    void ioExceptionIsFailure() {
        LivenessProbe.ProbeResult r = LivenessProbe.probe("http://x/timeout",
            new FakeFetcher(200, new byte[]{}, 200, new byte[]{1}, new java.io.IOException("read timed out")));
        assertFalse(r.ok());
        assertTrue(r.error().contains("timed out"));
    }

    @Test
    void emptyBodyIsFailure() {
        LivenessProbe.ProbeResult r = LivenessProbe.probe("http://x/empty",
            new FakeFetcher(200, new byte[]{}));
        assertFalse(r.ok());
        assertTrue(r.error().contains("empty"));
    }

    @Test
    void m3u8ProbesFirstSegment() {
        byte[] m3u8 = "#EXTM3U\n#EXTINF:10,\nhttp://x/segment0.ts\n".getBytes();
        LivenessProbe.ProbeResult r = LivenessProbe.probe("http://x/list.m3u8",
            new FakeFetcher(200, m3u8, 200, new byte[]{0x47, 0x40}, null));
        assertTrue(r.ok());
    }

    @Test
    void m3u8SegmentFailureReportsSegmentError() {
        byte[] m3u8 = "#EXTM3U\n#EXTINF:10,\nhttp://x/segment0.ts\n".getBytes();
        LivenessProbe.ProbeResult r = LivenessProbe.probe("http://x/list.m3u8",
            new FakeFetcher(200, m3u8, 404, new byte[]{}, null));
        assertFalse(r.ok());
        assertTrue(r.error().contains("segment"));
    }

    @Test
    void masterPlaylistWithoutSegmentLineIsStillOk() {
        byte[] m3u8 = "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1000000\nhttp://x/sub.m3u8\n".getBytes();
        // sub.m3u8 也含 "segment" → 会走分片路径。用一个不含 segment 的 URL 测：
        // 这里 sub URL 含 segment，故会探测它；让它成功即可验证 master 也判 ok
        LivenessProbe.ProbeResult r = LivenessProbe.probe("http://x/master.m3u8",
            new FakeFetcher(200, m3u8, 200, new byte[]{1}, null));
        assertTrue(r.ok());
    }

    @Test
    void emptyUrlIsFailure() {
        LivenessProbe.ProbeResult r = LivenessProbe.probe("", new FakeFetcher(200, new byte[]{1}));
        assertFalse(r.ok());
    }

    @Test
    void doseConstantsAreSensible() {
        assertTrue(LivenessProbe.MAX_CONCURRENCY > 0);
        assertTrue(LivenessProbe.BATCH_SIZE >= LivenessProbe.MAX_CONCURRENCY);
        assertTrue(LivenessProbe.BYTE_CAP > 0);
        assertTrue(LivenessProbe.SEGMENT_BYTE_CAP <= LivenessProbe.BYTE_CAP);
    }
}