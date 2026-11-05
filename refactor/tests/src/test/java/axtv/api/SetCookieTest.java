/*
 * Set-Cookie 解析：按 ";" 切分后取【第一段】（对应 n11.m8003r0 / o70.m8483U0 = list.get(0)）。
 * 放在 axtv.api 包下，因为 SetCookie 是包私有。
 */
package axtv.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Map;

import org.junit.jupiter.api.Test;

class SetCookieTest {

    @Test
    void takesFirstSegmentNotCookieAttributes() {
        Map.Entry<String, String> pair = SetCookie.parse(
                "ttwid=1%7C0K6gi1bGEurKVfszsNIpdAtcX8r0VDwSSIMv7Zex-II%7C1793865734%7C06f5a961d941e5cb"
                        + "; Path=/; Domain=bytedance.com; Max-Age=31536000; HttpOnly; Secure; SameSite=None");

        assertEquals("ttwid", pair.getKey());
        assertEquals("1%7C0K6gi1bGEurKVfszsNIpdAtcX8r0VDwSSIMv7Zex-II%7C1793865734%7C06f5a961d941e5cb",
                pair.getValue(), "必须是第一段的 name=value，不能是 SameSite/HttpOnly 这类属性");
    }

    @Test
    void plainPairWithoutAttributesIsAccepted() {
        Map.Entry<String, String> pair = SetCookie.parse("s_v_web_id=verify_abc123");
        assertEquals("s_v_web_id", pair.getKey());
        assertEquals("verify_abc123", pair.getValue());
    }

    @Test
    void trimsSurroundingWhitespace() {
        Map.Entry<String, String> pair = SetCookie.parse("  msToken=abc==  ; Path=/");
        assertEquals("msToken", pair.getKey());
        assertEquals("abc==", pair.getValue());
    }

    @Test
    void keepsEqualsInsideValue() {
        Map.Entry<String, String> pair = SetCookie.parse("msToken=AbC==; Path=/");
        assertEquals("msToken", pair.getKey(), "首个 '=' 拆 name/value，值里的 '=' 原样保留");
        assertEquals("AbC==", pair.getValue());
    }

    @Test
    void rejectsLinesWithoutNameValuePair() {
        assertNull(SetCookie.parse(null));
        assertNull(SetCookie.parse(""));
        assertNull(SetCookie.parse("; Path=/"));
        assertNull(SetCookie.parse("=novalue"));
    }

    @Test
    void doesNotFilterCookieAttributes() {
        // 原算法不做属性名过滤：单独一行 "Path=/" 仍会被解析出来，调用方靠名字匹配忽略它。
        Map.Entry<String, String> pair = SetCookie.parse("Path=/");
        assertEquals("Path", pair.getKey());
        assertEquals("/", pair.getValue());
    }
}
