/*
 * 扫码登录的纯 HTTP 会话（协议参考实现，**未接入登录流程**）。
 *
 * 为什么没接：2026-11-06 用真实接口验证过 —— get_qrcode 能正常出码，但 check_qrconnect
 * 裸请求一律返回 error_code=4031（"网站存在安全风险，已阻止此次访问"），补 a_bogus 也不通；
 * 轮询必须带网页 SDK（webmssdk）生成的 msToken 等反爬令牌，裸 HTTP 补不齐。
 * 因此扫码登录必须由页面内 SDK 驱动（见 QrLoginBridge.run），本类保留作为协议证据：
 * 若将来能自行生成 msToken，接上这个类就能实现"无 WebView 扫码登录"。
 *
 * 背景：电视盒子 / 定制 ROM 上 WebView 可能被裁掉或不可用，登录不能再依赖隐藏 WebView。
 * 扫码登录本来就是三个普通 HTTP 接口（与页面内自建会话脚本一致，该脚本已实测无需 a_bogus）：
 *   1) GET  {origin}/passport/web/get_qrcode/?{params}       → data.qrcode（base64 PNG）/ data.token
 *   2) POST {origin}/passport/web/check_qrconnect/?{params}  → data.status / data.redirect_url
 *      （body: token=…，application/x-www-form-urlencoded）
 *   3) GET  redirect_url（含重定向链）                       → Set-Cookie: sessionid
 * 第 3 步网页自己也是这么做的（自建脚本在 confirmed 后执行 window.location.href = redirect_url），
 * 跳过它就会出现「手机已确认、Cookie 永远不落地」。
 *
 * 只用 java.net.HttpURLConnection：Android 与 JVM 单测都能用，不需要额外的适配层。
 */
package axtv.panel;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class QrLoginHttpSession {

    public static final String DEFAULT_ORIGIN = "https://creator.douyin.com";
    public static final String DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/113.0.0.0 Safari/537.36";

    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 15_000;
    private static final int MAX_REDIRECTS = 8;

    /** get_qrcode 的原始响应与轮询令牌。 */
    public record QrCode(String raw, String token) {
    }

    private final String origin;
    private final String params;
    private final String userAgent;
    /** 名称 → 值（这些域下的 Cookie 合并发送；登录态只认 sessionid/sessionid_ss）。 */
    private final Map<String, String> cookies = new LinkedHashMap<>();
    /** 首访预热只做一次：拿 ttwid 之类的首访 Cookie，后续请求才不会被当无状态客户端。 */
    private boolean warmedUp;

    public QrLoginHttpSession() {
        this(DEFAULT_ORIGIN, QrLoginBridge.selfQrParams(), DEFAULT_USER_AGENT);
    }

    /** 测试 / 自建站用：origin 可以是 http://127.0.0.1:port。 */
    public QrLoginHttpSession(String origin, String params, String userAgent) {
        String o = origin == null || origin.trim().isEmpty() ? DEFAULT_ORIGIN : origin.trim();
        this.origin = o.endsWith("/") ? o.substring(0, o.length() - 1) : o;
        this.params = params == null ? "" : params;
        this.userAgent = userAgent == null || userAgent.isEmpty() ? DEFAULT_USER_AGENT : userAgent;
    }

    public String origin() {
        return origin;
    }

    /** 取二维码：返回原始响应（交给 QrLoginBridge.parseQrImage 解 PNG）与轮询 token。 */
    public QrCode fetchQr() throws IOException {
        warmUp();
        String url = origin + "/passport/web/get_qrcode/?" + params;
        String body = request("GET", url, null, null);
        Map<String, Object> root = Json.parseObject(body);
        Object data = root.get("data");
        if (!(data instanceof Map<?, ?> map)) {
            throw new IOException("get_qrcode 响应缺少 data: " + abbreviate(body));
        }
        Object qrcode = map.get("qrcode");
        Object token = map.get("token");
        if (qrcode == null || token == null) {
            throw new IOException("get_qrcode 响应缺少 qrcode/token: " + abbreviate(body));
        }
        return new QrCode(body, String.valueOf(token));
    }

    /** 轮询扫码状态：返回 check_qrconnect 原始响应（状态/redirect_url 由调用方解析）。 */
    public String poll(String token) throws IOException {
        String url = origin + "/passport/web/check_qrconnect/?" + params;
        return request("POST", url, "token=" + encode(token), "application/x-www-form-urlencoded");
    }

    /** 跟随登录完成地址（含重定向链），沿途 Set-Cookie 全部收进 jar。 */
    public void completeLogin(String url) throws IOException {
        request("GET", url, null, null);
    }

    /** 当前累积的 Cookie 请求头（name=value; ...）。空串表示还没有任何 Cookie。 */
    public String cookieHeader() {
        if (cookies.isEmpty()) {
            return "";
        }
        StringBuilder header = new StringBuilder();
        for (Map.Entry<String, String> entry : cookies.entrySet()) {
            if (header.length() > 0) {
                header.append("; ");
            }
            header.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return header.toString();
    }

    /**
     * 首访预热：先 GET 一次首页，让服务端下发 ttwid 等首访 Cookie。
     *
     * <p>页面内的自建会话脚本是在已加载过登录页的上下文里跑的（那些 Cookie 天然存在），
     * 原生通道如果一上来就调接口，会被当成无状态客户端。预热失败不阻塞出码。
     */
    private void warmUp() {
        if (warmedUp) {
            return;
        }
        warmedUp = true;
        try {
            request("GET", origin + "/", null, null);
        } catch (Exception e) {
            // 首页可能 404 / 被限流，目的只是拿首访 Cookie，拿不到也继续出码。
        }
    }

    /**
     * 手动跟随重定向：HttpURLConnection 自动跟随时不会带 Cookie 到下一跳，
     * 而登录完成恰恰靠「跳转响应里的 Set-Cookie」，所以这里逐跳自己处理。
     */
    private String request(String method, String url, String body, String contentType) throws IOException {
        String currentUrl = url;
        String currentMethod = method;
        String currentBody = body;
        for (int hop = 0; hop < MAX_REDIRECTS; hop++) {
            HttpURLConnection conn = (HttpURLConnection) new URL(currentUrl).openConnection();
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setInstanceFollowRedirects(false);
            conn.setRequestMethod(currentMethod);
            conn.setRequestProperty("User-Agent", userAgent);
            conn.setRequestProperty("Accept", "application/json, text/plain, */*");
            conn.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9");
            conn.setRequestProperty("Referer", origin + "/");
            if (currentBody != null) {
                conn.setRequestProperty("Content-Type", contentType);
                conn.setRequestProperty("Origin", origin);
                conn.setDoOutput(true);
            }
            String cookie = cookieHeader();
            if (!cookie.isEmpty()) {
                conn.setRequestProperty("Cookie", cookie);
            }
            try {
                if (currentBody != null) {
                    try (OutputStream out = conn.getOutputStream()) {
                        out.write(currentBody.getBytes(StandardCharsets.UTF_8));
                    }
                }
                int code = conn.getResponseCode();
                absorbCookies(conn.getHeaderFields());
                if (code >= 300 && code < 400) {
                    String location = conn.getHeaderField("Location");
                    if (location == null || location.isEmpty()) {
                        throw new IOException("重定向缺少 Location: " + currentUrl);
                    }
                    // 让重定向目标继承当前地址的 scheme/host 解析（Location 可能是相对路径）。
                    String next = new URL(new URL(currentUrl), location).toString();
                    if (code != 307 && code != 308) {
                        currentMethod = "GET";
                        currentBody = null;
                    }
                    currentUrl = next;
                    continue;
                }
                String text = readBody(conn, code);
                if (code >= 400) {
                    throw new IOException("HTTP " + code + ": " + abbreviate(text));
                }
                return text;
            } finally {
                conn.disconnect();
            }
        }
        throw new IOException("重定向次数过多: " + url);
    }

    private static String readBody(HttpURLConnection conn, int code) throws IOException {
        InputStream in = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
        if (in == null) {
            return "";
        }
        try (InputStream input = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int read;
            while ((read = input.read(chunk)) != -1) {
                out.write(chunk, 0, read);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private void absorbCookies(Map<String, List<String>> headers) {
        if (headers == null) {
            return;
        }
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            String name = entry.getKey();
            if (name == null || !"set-cookie".equalsIgnoreCase(name) || entry.getValue() == null) {
                continue;
            }
            for (String raw : entry.getValue()) {
                if (raw == null) {
                    continue;
                }
                String pair = raw.split(";", 2)[0].trim();
                int equals = pair.indexOf('=');
                if (equals <= 0) {
                    continue;
                }
                String key = pair.substring(0, equals).trim();
                String value = pair.substring(equals + 1).trim();
                if (value.isEmpty()) {
                    cookies.remove(key);
                } else {
                    cookies.put(key, value);
                }
            }
        }
    }

    private static String encode(String text) {
        try {
            return URLEncoder.encode(text == null ? "" : text, "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            // UTF-8 是 Android/JVM 必带字符集，走不到这里。
            return text == null ? "" : text;
        }
    }

    private static String abbreviate(String text) {
        if (text == null) {
            return "";
        }
        String compact = text.replaceAll("\\s+", " ").trim();
        return compact.length() > 200 ? compact.substring(0, 200) + "…" : compact;
    }
}
