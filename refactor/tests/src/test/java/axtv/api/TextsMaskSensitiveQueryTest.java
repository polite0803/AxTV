package axtv.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** {@link Texts#maskSensitiveQuery} 的掩码边界：msToken/uifid 掩值，a_bogus 与其余参数保留。 */
class TextsMaskSensitiveQueryTest {

    @Test
    void masksMsTokenAndUifidValues() {
        String url = "https://www.douyin.com/aweme/v1/web/tab/feed/"
                + "?device_platform=webapp&aid=6383"
                + "&msToken=abc123secrettokenvalue&uifid=ANDROID_PKG%3A1.2.3"
                + "&a_bogus=AbCdEf123456";
        String masked = Texts.maskSensitiveQuery(url);
        assertEquals(
                "https://www.douyin.com/aweme/v1/web/tab/feed/"
                        + "?device_platform=webapp&aid=6383"
                        + "&msToken=[REDACTED]&uifid=[REDACTED]"
                        + "&a_bogus=AbCdEf123456",
                masked);
    }

    @Test
    void masksTrailingMsTokenWithoutFollowingParam() {
        String url = "https://www.douyin.com/path?a=1&msToken=tokengoeshere";
        assertEquals("https://www.douyin.com/path?a=1&msToken=[REDACTED]",
                Texts.maskSensitiveQuery(url));
    }

    @Test
    void masksCaseInsensitiveKeyNames() {
        // 掩值但保留键名原样
        assertEquals("https://x/p?MSTOKEN=[REDACTED]", Texts.maskSensitiveQuery("https://x/p?MSTOKEN=x"));
        assertEquals("https://x/p?UIFID=[REDACTED]", Texts.maskSensitiveQuery("https://x/p?UIFID=y"));
    }

    @Test
    void keepsUrlWithoutQueryAndHandlesNull() {
        assertEquals("https://www.douyin.com/path", Texts.maskSensitiveQuery("https://www.douyin.com/path"));
        assertNull(Texts.maskSensitiveQuery(null));
    }

    @Test
    void keepsNonSensitiveKeysWithSimilarNames() {
        // token 包含在别的键名里（如 deviceToken）不能被误掩
        assertEquals("https://x/p?deviceToken=keepme",
                Texts.maskSensitiveQuery("https://x/p?deviceToken=keepme"));
    }
}
