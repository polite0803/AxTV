/*
 * 面板服务的访问令牌。
 *
 * 原实现（qj0 / vc4 / wc4）对局域网完全开放：任何同网段设备都能改登录态、读日志、
 * 注入搜索输入。这里补一层一次性令牌：电视启动时生成随机值，手机通过电视上展示的
 * 带 token 的二维码/地址接入；HTTP 与 WebSocket 都校验同一个令牌。
 */
package axtv.panel;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

/**
 * 面板访问令牌。
 *
 * <p>令牌通过查询参数 {@code ?token=...} 或请求头 {@code X-AxTV-Token} 传入。
 * 页面地址由电视端生成并展示，手机扫码即可带上令牌。
 */
public final class PanelAuth {

    public static final String HEADER = "X-AxTV-Token";
    public static final String QUERY_PARAM = "token";

    private final String token;

    public PanelAuth(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("面板令牌不能为空");
        }
        this.token = token;
    }

    /** 生成 128 位随机令牌（32 位十六进制）。 */
    public static PanelAuth random() {
        byte[] bytes = new byte[16];
        new SecureRandom().nextBytes(bytes);
        char[] hex = new char[bytes.length * 2];
        final char[] digits = "0123456789abcdef".toCharArray();
        for (int i = 0; i < bytes.length; i++) {
            int value = bytes[i] & 0xFF;
            hex[i * 2] = digits[value >>> 4];
            hex[i * 2 + 1] = digits[value & 0x0F];
        }
        return new PanelAuth(new String(hex));
    }

    public String token() {
        return this.token;
    }

    /** 校验一次 HTTP 请求。 */
    public boolean allows(PanelRoutes.Request request) {
        return request != null && allows(request.query(), request.header(HEADER));
    }

    /** 校验 WebSocket 握手（查询串 + 请求头；请求头优先）。 */
    public boolean allows(String query, String headerValue) {
        String supplied = headerValue;
        if (supplied == null || supplied.isBlank()) {
            supplied = queryParam(query, QUERY_PARAM);
        }
        if (supplied == null) {
            return false;
        }
        return MessageDigest.isEqual(
                this.token.getBytes(StandardCharsets.UTF_8),
                supplied.trim().getBytes(StandardCharsets.UTF_8));
    }

    /** 把令牌拼到地址上（已有查询串时用 &）。 */
    public String appendTo(String url) {
        if (url == null) {
            return null;
        }
        return url + (url.indexOf('?') >= 0 ? "&" : "?") + QUERY_PARAM + "=" + this.token;
    }

    /** 从原始查询串里取参数（不做 URL 解码：令牌是十六进制，无需转义）。 */
    static String queryParam(String query, String name) {
        if (query == null || query.isEmpty()) {
            return null;
        }
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            String key = eq < 0 ? pair : pair.substring(0, eq);
            if (name.equals(key)) {
                return eq < 0 ? "" : pair.substring(eq + 1);
            }
        }
        return null;
    }

}
