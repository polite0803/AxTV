package axtv.panel;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@link LogcatExportHandler#sanitizeLogLine} 的脱敏边界：掩 msToken/uifid 值，保留 a_bogus 与行结构。 */
class LogcatSanitizeTest {

    @Test
    void masksMsTokenAndUifidValuesInSignedUrlLine() {
        String line = "11-08 10:00:00.000 I/System.out: I AxTV_SIGNED_URL: https://www.douyin.com/aweme/v1/web/tab/feed/"
                + "?aid=6383&msToken=tok1234567890&uifid=ABC%2Bdef&a_bogus=E9gW8xYz123";
        String out = LogcatExportHandler.sanitizeLogLine(line);
        assertFalse(out.contains("tok1234567890"));
        assertFalse(out.contains("ABC%2Bdef"));
        assertTrue(out.contains("msToken=[REDACTED]"));
        assertTrue(out.contains("uifid=[REDACTED]"));
        // a_bogus 是签名对比诊断的依据，必须保留
        assertTrue(out.contains("a_bogus=E9gW8xYz123"));
        assertTrue(out.contains("AxTV_SIGNED_URL"));
    }

    @Test
    void masksStandaloneMstokenValue() {
        String out = LogcatExportHandler.sanitizeLogLine("I/System.out: msToken=plainvalue123 msToken=second1");
        assertFalse(out.contains("plainvalue123"));
        assertFalse(out.contains("second1"));
        assertEquals(2, out.split("msToken=\\[REDACTED]", -1).length - 1);
    }

    @Test
    void stillRedactsCookieAndSessionidLines() {
        assertEquals("Cookie: [REDACTED]",
                LogcatExportHandler.sanitizeLogLine("Cookie: sessionid=abc; ttwid=xyz"));
        assertEquals("sessionid: [REDACTED]",
                LogcatExportHandler.sanitizeLogLine("sessionid=leak"));
    }

    @Test
    void leavesOrdinaryLinesUntouched() {
        String line = "11-08 10:00:00.000 D/AxTV: 普通     日志 line, path=/aweme/v1/feed";
        assertEquals(line, LogcatExportHandler.sanitizeLogLine(line));
    }

    @Test
    void handlesNull() {
        assertNull(LogcatExportHandler.sanitizeLogLine(null));
    }
}
