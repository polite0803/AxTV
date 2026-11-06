package axtv.test;

import axtv.panel.PanelAuth;
import axtv.panel.PanelRoutes;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 面板令牌：查询参数/请求头两种入口，以及路由守卫对 OPTIONS 的放行。 */
class PanelAuthTest {

    private static PanelRoutes.Request request(String method, String query, Map<String, String> headers) {
        return new PanelRoutes.Request(method, "/submit", query, headers, new byte[0], "127.0.0.1");
    }

    @Test
    void tokenFromQueryOrHeaderIsAccepted() {
        PanelAuth auth = new PanelAuth("abc123");
        assertTrue(auth.allows(request("GET", "token=abc123", Map.of())));
        assertTrue(auth.allows(request("GET", null, Map.of("x-axtv-token", "abc123"))));
        assertFalse(auth.allows(request("GET", null, Map.of())));
        assertFalse(auth.allows(request("GET", "token=wrong", Map.of())));
    }

    @Test
    void tokenExpiresAfterTokenValidPeriod() {
        // 30 分钟有效期：构造一个 31 分钟前发行的 token，应判定为已过期
        long issuedAt = System.currentTimeMillis() - (31L * 60L * 1000L);
        PanelAuth expiredAuth = new PanelAuth("abc123", issuedAt);
        assertTrue(expiredAuth.isExpired());

        // 刚发出的 token 应未过期
        PanelAuth freshAuth = new PanelAuth("abc123", System.currentTimeMillis());
        assertFalse(freshAuth.isExpired());
    }

    @Test
    void routesRejectMissingTokenButAllowPreflight() {
        PanelRoutes routes = new PanelRoutes(
                List.of(
                        new PanelRoutes.Route(PanelRoutes.METHOD_GET, "/submit",
                                r -> PanelRoutes.Response.text(200, PanelRoutes.TEXT_PLAIN, "ok")),
                        new PanelRoutes.Route(PanelRoutes.METHOD_OPTIONS, PanelRoutes.WILDCARD,
                                r -> PanelRoutes.Response.text(200, PanelRoutes.TEXT_PLAIN, ""))),
                r -> PanelRoutes.Response.text(404, PanelRoutes.TEXT_PLAIN, "404"))
                .requireAuth(new PanelAuth("abc123"));

        assertEquals(401, routes.dispatch(request("GET", null, Map.of())).status());
        assertEquals(401, routes.dispatch(request("GET", null, Map.of("x-axtv-token", "wrong"))).status());
        assertEquals(200, routes.dispatch(request("GET", "token=abc123", Map.of())).status());
        assertEquals(200, routes.dispatch(request("OPTIONS", null, Map.of())).status());
    }
}
