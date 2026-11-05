/*
 * 来源: src/app/p000/e63.java（请求）、h63.java（响应）、k63.java（路由分发）
 *       路由表分别来自 src/app/p000/qj0.java 与 src/app/p000/vc4.java
 * 原职责: NanoHTTPD 的请求/响应模型与 URI 匹配
 * 重构说明: 用 record + 函数式 Handler 重建；原实现是「每台服务器一个 serve(Request) 大 if-else」，
 *           这里改成显式路由表，路径与顺序保持与原 if-else 完全一致。
 */
package axtv.panel;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 路由表：路径 + 方法 → 处理函数。
 *
 * <p>路径匹配是**精确匹配**（原实现用 {@code str.equals(uri)} 比较，不做前后缀匹配，
 * 也不处理末尾斜杠）。{@link #WILDCARD} 只用于 OPTIONS 这类跨路径规则。
 */
public final class PanelRoutes {

    /** 匹配任意路径。原实现里 OPTIONS 对所有路径都返回 CORS 预检响应。 */
    public static final String WILDCARD = "*";

    /** 匹配任意方法。搜索面板页对 {@code /}、{@code /search} 不校验方法，任何方法都返回页面。 */
    public static final String ANY_METHOD = "*";

    public static final String METHOD_GET = "GET";
    public static final String METHOD_POST = "POST";
    public static final String METHOD_OPTIONS = "OPTIONS";

    /**
     * 一次请求。
     *
     * @param method        HTTP 方法（大写）
     * @param path          已 URL 解码的路径
     * @param query         原始查询串（未解码，可能为 null）
     * @param headers       头字段，键为小写
     * @param body          请求体（已按 Content-Length 读完，可能为空数组）
     * @param remoteAddress 对端 IP；环回/任意本地地址统一记为 127.0.0.1
     */
    public record Request(
            String method,
            String path,
            String query,
            Map<String, String> headers,
            byte[] body,
            String remoteAddress) {

        public String header(String name) {
            return this.headers.get(name.toLowerCase(Locale.ROOT));
        }

        public String bodyAsString() {
            return new String(this.body, StandardCharsets.UTF_8);
        }
    }

    /** 一次响应。{@code body} 为 null 时按空体发送（对应原 h63 的 null 分支）。 */
    public record Response(int status, String contentType, byte[] body, Map<String, String> headers) {

        public Response {
            headers = headers == null ? Map.of() : Map.copyOf(headers);
        }

        public static Response text(int status, String contentType, String body) {
            return new Response(status, contentType, body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8), Map.of());
        }

        public static Response binary(int status, String contentType, byte[] body) {
            return new Response(status, contentType, body == null ? new byte[0] : body, Map.of());
        }

        public Response withHeader(String name, String value) {
            Map<String, String> merged = new LinkedHashMap<>(this.headers);
            merged.put(name, value);
            return new Response(this.status, this.contentType, this.body, merged);
        }

        /** 原 qj0 对每个响应都追加的 CORS 头；OPTIONS 预检额外带 Max-Age。 */
        public Response withCors() {
            return withHeader("Access-Control-Allow-Origin", "*")
                    .withHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
                    .withHeader("Access-Control-Allow-Headers", "Content-Type, " + PanelAuth.HEADER);
        }

        public String bodyAsString() {
            return this.body == null ? "" : new String(this.body, StandardCharsets.UTF_8);
        }
    }

    @FunctionalInterface
    public interface Handler {
        Response handle(Request request) throws Exception;
    }

    public record Route(String method, String path, Handler handler) {
    }

    public static final String TEXT_PLAIN = "text/plain";
    public static final String TEXT_PLAIN_UTF8 = "text/plain; charset=utf-8";
    public static final String TEXT_HTML_UTF8 = "text/html; charset=utf-8";
    public static final String APPLICATION_JSON = "application/json";

    public static final int SC_OK = 200;
    public static final int SC_BAD_REQUEST = 400;
    public static final int SC_UNAUTHORIZED = 401;
    public static final int SC_NOT_FOUND = 404;
    public static final int SC_INTERNAL_ERROR = 500;

    private final List<Route> routes;
    private final Handler fallback;

    public PanelRoutes(List<Route> routes, Handler fallback) {
        this.routes = List.copyOf(routes);
        this.fallback = fallback;
    }

    /** 只有「404 纯文本」这一条兜底的服务器（搜索面板）。 */
    public static PanelRoutes withNotFound() {
        return new PanelRoutes(List.of(), request -> Response.text(SC_NOT_FOUND, TEXT_PLAIN, "404"));
    }

    public PanelRoutes with(Route... more) {
        List<Route> merged = new java.util.ArrayList<>(this.routes);
        merged.addAll(List.of(more));
        return new PanelRoutes(merged, this.fallback);
    }

    /**
     * 给所有路由包一层令牌校验。OPTIONS 预检放行（浏览器预检不会带自定义头），
     * 其余请求（含兜底 404）缺少/错误的令牌一律 401。
     */
    public PanelRoutes requireAuth(PanelAuth auth) {
        if (auth == null) {
            return this;
        }
        List<Route> guarded = new java.util.ArrayList<>(this.routes.size());
        for (Route route : this.routes) {
            if (METHOD_OPTIONS.equals(route.method())) {
                guarded.add(route);
                continue;
            }
            guarded.add(new Route(route.method(), route.path(), request -> {
                if (!auth.allows(request)) {
                    return unauthorized();
                }
                return route.handler().handle(request);
            }));
        }
        Handler guardedFallback = request -> {
            if (!auth.allows(request)) {
                return unauthorized();
            }
            return this.fallback.handle(request);
        };
        return new PanelRoutes(guarded, guardedFallback);
    }

    private static Response unauthorized() {
        return Response.text(SC_UNAUTHORIZED, TEXT_PLAIN, "unauthorized");
    }

    /**
     * 分发。查找顺序：精确 (方法, 路径) → 通配 (方法, *) → 兜底。
     * 处理函数抛异常时返回 500，正文为异常信息——与原 k63 的异常兜底一致。
     */
    public Response dispatch(Request request) {
        Handler handler = resolve(request.method(), request.path());
        try {
            Response response = handler.handle(request);
            if (response == null) {
                return Response.text(SC_INTERNAL_ERROR, TEXT_PLAIN, "SERVER INTERNAL ERROR: handle() returned a null response.");
            }
            return response;
        } catch (Exception e) {
            return Response.text(SC_INTERNAL_ERROR, TEXT_PLAIN,
                    "SERVER INTERNAL ERROR: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private Handler resolve(String method, String path) {
        for (Route route : this.routes) {
            if (matchesMethod(route.method(), method) && route.path().equals(path)) {
                return route.handler();
            }
        }
        for (Route route : this.routes) {
            if (matchesMethod(route.method(), method) && WILDCARD.equals(route.path())) {
                return route.handler();
            }
        }
        return this.fallback;
    }

    private static boolean matchesMethod(String declared, String actual) {
        return ANY_METHOD.equals(declared) || declared.equals(actual);
    }

    /** 读取请求体：按 Content-Length 读满，读不满则以实际读到为准（原实现同）。 */
    public static byte[] readBody(InputStream in, int contentLength, int maxBytes) throws IOException {
        if (contentLength <= 0) {
            return new byte[0];
        }
        if (contentLength > maxBytes) {
            throw new IOException("请求体过大: " + contentLength + " > " + maxBytes);
        }
        byte[] buffer = new byte[contentLength];
        ByteArrayOutputStream collected = new ByteArrayOutputStream(Math.min(contentLength, 8192));
        int read = 0;
        while (read < contentLength) {
            int n = in.read(buffer, 0, contentLength - read);
            if (n < 0) {
                break;
            }
            collected.write(buffer, 0, n);
            read += n;
        }
        return collected.toByteArray();
    }
}
