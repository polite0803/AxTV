/*
 * 匿名访问身份引导：ttwid（union/register）与 UIFID_TEMP（首页 HEAD）缺哪补哪，
 * 已有的不重复请求；失败静默返回 false 且不污染 cookie。
 */
package axtv.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import axtv.api.AnonymousIdentity;
import axtv.api.ApiResponse;
import axtv.api.CookieStore;
import axtv.api.HttpEngine;
import axtv.api.TicketGuardCredentials;

import org.junit.jupiter.api.Test;

class AnonymousIdentityTest {

    private static final String TTWID =
            "1%7C0K6gi1bGEurKVfszsNIpdAtcX8r0VDwSSIMv7Zex-II%7C1793865734%7C06f5a961d941e5cb";
    private static final String UIFID_TEMP = "b684c79658e5909c916769b2e80c406ed39ea89d6c7cce823";

    @Test
    void completeIdentitySkipsAllNetworkCalls() {
        FakeHttp http = new FakeHttp();
        FakeCookies cookies = new FakeCookies();
        cookies.jar.put("ttwid", TTWID);
        cookies.jar.put("UIFID_TEMP", UIFID_TEMP);
        cookies.uniqueId = "1000000000000001";

        assertTrue(AnonymousIdentity.ensure(http, cookies));
        assertEquals(0, http.calls.size(), "jar 里已有 ttwid + UIFID_TEMP + 全数字 user_unique_id 时不应再发任何请求");
    }

    @Test
    void completeIdentityStillFetchesMissingUniqueId() {
        FakeHttp http = new FakeHttp();
        http.route("www.douyin.com", new ApiResponse(200,
                ("<html><script>\"user_unique_id\":\"7300000000000000009\"</script></html>")
                        .getBytes(StandardCharsets.UTF_8), Map.of()));
        FakeCookies cookies = new FakeCookies();
        cookies.jar.put("ttwid", TTWID);
        cookies.jar.put("UIFID_TEMP", UIFID_TEMP);

        assertTrue(AnonymousIdentity.ensure(http, cookies));
        // ttwid / UIFID_TEMP 已齐，但 user_unique_id 缺失：仍要发一次 GET 首页（m8071z）。
        assertEquals(1, http.calls.size());
        assertEquals("GET", http.calls.get(0).method);
        assertEquals("https://www.douyin.com/?recommend=1", http.calls.get(0).url);
        assertTrue(http.calls.get(0).followRedirects, "首页取 user_unique_id 跟随重定向（m8071z 循环）");
        assertEquals("7300000000000000009", cookies.lastSetUniqueId);
    }

    @Test
    void acquiresMissingTtwidAndUifidTemp() {
        FakeHttp http = new FakeHttp();
        http.route("ttwid.bytedance.com", new ApiResponse(200,
                "{\"status_code\":0,\"message\":\"union register success\"}".getBytes(StandardCharsets.UTF_8),
                Map.of("Set-Cookie", List.of(
                        "ttwid=" + TTWID + "; Path=/; Domain=bytedance.com; Max-Age=31536000; "
                                + "HttpOnly; Secure; SameSite=None"))));
        http.route("www.douyin.com", new ApiResponse(404, new byte[0], Map.of("Set-Cookie", List.of(
                "ttwid=" + TTWID + "; Domain=.douyin.com; Path=/; HttpOnly",
                "UIFID_TEMP=" + UIFID_TEMP + "; path=/; expires=Sun, 19 Mar 2028 08:18:22 GMT; "
                        + "domain=douyin.com; secure"))));
        FakeCookies cookies = new FakeCookies();

        assertTrue(AnonymousIdentity.ensure(http, cookies));
        assertEquals(TTWID, cookies.jar.get("ttwid"));
        assertEquals(UIFID_TEMP, cookies.jar.get("UIFID_TEMP"));
        // 0=ttwid 注册，1=首页 HEAD 取 UIFID_TEMP，2=首页 GET 取 user_unique_id。
        assertEquals(3, http.calls.size());
        assertEquals("POST", http.calls.get(0).method);
        assertEquals("https://ttwid.bytedance.com/ttwid/union/register/", http.calls.get(0).url);
        assertTrue(http.calls.get(0).headers.get("User-Agent").contains("Chrome/147"),
                "申领请求固定使用 Chrome/147 UA（与原实现一致）");
        assertTrue(http.calls.get(0).body.contains("\"union\":true"));
        assertEquals("HEAD", http.calls.get(1).method);
        assertEquals("https://www.douyin.com/", http.calls.get(1).url);
        assertFalse(http.calls.get(1).followRedirects, "首页取 UIFID_TEMP 用不跟随重定向的客户端");
        assertEquals("GET", http.calls.get(2).method);
        assertEquals("https://www.douyin.com/?recommend=1", http.calls.get(2).url);
    }

    @Test
    void existingTtwidOnlyFetchesUifidTemp() {
        FakeHttp http = new FakeHttp();
        http.route("www.douyin.com", new ApiResponse(404, new byte[0], Map.of("Set-Cookie", List.of(
                "UIFID_TEMP=" + UIFID_TEMP + "; path=/; domain=douyin.com; secure"))));
        FakeCookies cookies = new FakeCookies();
        cookies.jar.put("ttwid", TTWID);

        assertTrue(AnonymousIdentity.ensure(http, cookies));
        assertEquals(2, http.calls.size(), "ttwid 已存在时只补 UIFID_TEMP 与 user_unique_id");
        assertEquals("HEAD", http.calls.get(0).method);
        assertEquals(UIFID_TEMP, cookies.jar.get("UIFID_TEMP"));
    }

    @Test
    void fallsBackToTtwidInResponseBody() {
        FakeHttp http = new FakeHttp();
        http.route("ttwid.bytedance.com", new ApiResponse(200,
                ("{\"ttwid\":\"" + TTWID + "\",\"status_code\":0}").getBytes(StandardCharsets.UTF_8),
                Map.of()));
        FakeCookies cookies = new FakeCookies();
        cookies.jar.put("UIFID_TEMP", UIFID_TEMP);

        assertTrue(AnonymousIdentity.ensure(http, cookies));
        assertEquals(TTWID, cookies.jar.get("ttwid"));
    }

    @Test
    void failuresLeaveCookieJarUntouched() {
        FakeHttp http = new FakeHttp();
        http.failure = new java.io.IOException("no route");
        FakeCookies cookies = new FakeCookies();

        assertFalse(AnonymousIdentity.ensure(http, cookies), "全部失败应返回 false");
        assertFalse(cookies.jar.containsKey("ttwid"), "失败时不得写入空值或占位值");
        assertFalse(cookies.jar.containsKey("UIFID_TEMP"));
    }

    @Test
    void responseWithoutIdentityCookiesIsTreatedAsFailure() {
        FakeHttp http = new FakeHttp();
        http.route("ttwid.bytedance.com", new ApiResponse(200,
                "{\"status_code\":0}".getBytes(StandardCharsets.UTF_8), Map.of()));
        http.route("www.douyin.com", new ApiResponse(404,
                "{}".getBytes(StandardCharsets.UTF_8), Map.of()));
        FakeCookies cookies = new FakeCookies();

        assertFalse(AnonymousIdentity.ensure(http, cookies));
        assertFalse(cookies.jar.containsKey("ttwid"));
        assertFalse(cookies.jar.containsKey("UIFID_TEMP"));
    }

    // ---------------------------------------------------------------- 测试替身

    private static final class Call {
        final String method;
        final String url;
        final Map<String, String> headers;
        final String body;
        final boolean followRedirects;

        Call(String method, String url, Map<String, String> headers, String body, boolean followRedirects) {
            this.method = method;
            this.url = url;
            this.headers = headers;
            this.body = body;
            this.followRedirects = followRedirects;
        }
    }

    private static final class FakeHttp implements HttpEngine {
        final List<Call> calls = new ArrayList<>();
        final Map<String, ApiResponse> routes = new LinkedHashMap<>();
        java.io.IOException failure;

        void route(String host, ApiResponse response) {
            routes.put(host, response);
        }

        @Override
        public ApiResponse execute(String method, String url, Map<String, String> headers,
                                   byte[] body, String contentType, boolean followRedirects)
                throws java.io.IOException {
            calls.add(new Call(method, url, new LinkedHashMap<>(headers),
                    body == null ? "" : new String(body, StandardCharsets.UTF_8), followRedirects));
            if (failure != null) {
                throw failure;
            }
            for (Map.Entry<String, ApiResponse> e : routes.entrySet()) {
                if (url.contains(e.getKey())) {
                    return e.getValue();
                }
            }
            return new ApiResponse(200, new byte[0], Map.of());
        }
    }

    private static final class FakeCookies implements CookieStore {
        final Map<String, String> jar = new LinkedHashMap<>();
        String uniqueId = "";
        String lastSetUniqueId = null;

        @Override
        public String cookieHeader() {
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, String> e : jar.entrySet()) {
                if (sb.length() > 0) {
                    sb.append("; ");
                }
                sb.append(e.getKey()).append('=').append(e.getValue());
            }
            return sb.toString();
        }

        @Override
        public void setCookieHeader(String cookieHeader) {
            jar.clear();
            if (cookieHeader == null) {
                return;
            }
            for (String part : cookieHeader.split(";")) {
                int eq = part.indexOf('=');
                if (eq <= 0) {
                    continue;
                }
                jar.put(part.substring(0, eq).trim(), part.substring(eq + 1).trim());
            }
        }

        @Override
        public Map<String, String> cookies() {
            return jar;
        }

        @Override
        public String persistentDeviceId(String key) {
            return "1000000000000000";
        }

        @Override
        public String userUniqueId() {
            return uniqueId;
        }

        @Override
        public void setUserUniqueId(String value) {
            lastSetUniqueId = value;
            uniqueId = value == null ? "" : value;
        }

        @Override
        public String selfUserId() {
            return "";
        }

        @Override
        public void applySelfUserId(String userId) {
        }

        @Override
        public TicketGuardCredentials ticketGuard() {
            return null;
        }
    }
}
