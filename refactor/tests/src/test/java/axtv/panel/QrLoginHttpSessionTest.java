/*
 * 锁定「没有 WebView 也能扫码登录」的纯 HTTP 通道。
 *
 * 这是电视盒子（WebView 被裁掉）的唯一登录路径，必须能完整走完：
 *   出码 → 轮询状态 → confirmed 跟随 redirect_url → 收下 Set-Cookie → 判定 sessionid。
 * 用本地 HttpServer 模拟 passport 的三个响应，避免依赖真实接口与网络环境。
 */
package axtv.panel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class QrLoginHttpSessionTest {

    private HttpServer server;
    private String origin;
    private final AtomicReference<String> landingCookie = new AtomicReference<>("");

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        origin = "http://127.0.0.1:" + server.getAddress().getPort();

        server.createContext("/passport/web/get_qrcode/", exchange -> {
            byte[] body = ("{\"data\":{\"qrcode\":\"data:image/png;base64,iVBORw0KGgo=\","
                    + "\"token\":\"tok-1\"},\"message\":\"success\"}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });

        server.createContext("/passport/web/check_qrconnect/", exchange -> {
            // 轮询接口是 POST token=…：这里顺便校验编码后的 body 里确实带了 token。
            String form = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String status = form.contains("token=tok-1") ? "confirmed" : "scan";
            byte[] body = ("{\"data\":{\"status\":\"" + status + "\","
                    + "\"redirect_url\":\"" + origin + "/landing\"}}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });

        server.createContext("/landing", exchange -> {
            exchange.getResponseHeaders().add("Location", "/home");
            exchange.getResponseHeaders().add("Set-Cookie", "sessionid=abc123; Path=/; HttpOnly");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });

        server.createContext("/home", exchange -> {
            landingCookie.set(exchange.getRequestHeaders().getFirst("Cookie") == null
                    ? "" : exchange.getRequestHeaders().getFirst("Cookie"));
            exchange.getResponseHeaders().add("Set-Cookie", "sessionid_ss=def456; Path=/");
            byte[] body = "ok".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });

        server.start();
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("无 WebView 也能走完：出码 → 轮询 confirmed → 跟随 redirect 收 sessionid")
    void completesLoginWithoutWebView() throws Exception {
        QrLoginHttpSession session = new QrLoginHttpSession(origin, "aid=2906", "test-agent");

        QrLoginHttpSession.QrCode qr = session.fetchQr();
        assertEquals("tok-1", qr.token());
        assertNotNull(QrLoginBridge.parseQrImage(qr.raw()), "响应里的 qrcode 必须能解出 PNG 字节");

        String poll = session.poll(qr.token());
        assertEquals("confirmed", QrLoginBridge.pollStatus(poll));
        assertEquals(origin + "/landing", QrLoginBridge.confirmRedirectUrl(poll));

        session.completeLogin(QrLoginBridge.confirmRedirectUrl(poll));

        String cookie = session.cookieHeader();
        assertTrue(cookie.contains("sessionid=abc123"), "必须收下 302 上的 Set-Cookie：" + cookie);
        assertTrue(cookie.contains("sessionid_ss=def456"), "重定向后的 Cookie 也要累积：" + cookie);
        assertTrue(landingCookie.get().contains("sessionid=abc123"),
                "第二跳必须带上第一跳拿到的 Cookie：" + landingCookie.get());
        assertTrue(AuthStore.hasSessionId(cookie));
    }

    @Test
    @DisplayName("接口响应缺 qrcode/token 时明确失败，不静默返回空码")
    void rejectsInvalidQrResponse() throws Exception {
        HttpServer broken = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        broken.createContext("/passport/web/get_qrcode/", exchange -> {
            byte[] body = "{\"data\":{}}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        broken.start();
        try {
            QrLoginHttpSession session = new QrLoginHttpSession(
                    "http://127.0.0.1:" + broken.getAddress().getPort(), "aid=2906", "test-agent");
            assertNotNull(catchQrFailure(session), "响应缺 qrcode/token 时必须抛 IOException，不能当成功");
        } finally {
            broken.stop(0);
        }
    }

    private static String catchQrFailure(QrLoginHttpSession session) {
        try {
            session.fetchQr();
            return null;
        } catch (java.io.IOException e) {
            return e.getMessage();
        }
    }
}
