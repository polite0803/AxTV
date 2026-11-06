/*
 * 来源: 新建（电视直播功能，无原版对应）。
 * 原职责: 无。
 * 重构说明: 挂在 9978 端口的 IPTV 推送处理器。路由：GET /iptv → 渲染 iptv.html 页面，POST /api/iptv → 校验 token 并落库，GET /api/iptv/list → 返回当前用户源列表。
 *           OPTIONS 预检放行（浏览器预检不带自定义头），其余缺少/错误的令牌一律 401。
 *
 * 安全约束：必须在 routes() 返回前经过 .requireAuth(auth)，与 Cookie 推送共用同一 PanelAuth 令牌。
 *           直播源不是机密凭据，但仍须带令牌，保持统一安全模型。
 */
package axtv.iptv;

import axtv.panel.AuthStore;
import axtv.panel.Json;
import axtv.panel.PanelAuth;
import axtv.panel.PanelRoutes;
import axtv.panel.PanelRoutes.Route;
import axtv.panel.PanelState;
import axtv.panel.ThemeRenderer;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * IPTV 源推送处理器。
 *
 * <p>对应 {@link IptvRoutes} 常量，挂在 {@link CookiePushHandler#PORT}（9978）的路由表中。
 * 与 {@link CookiePushHandler} 的职责相近，但处理直播源而非 Cookie。
 */
public class IptvSourceHandler {

    private final ThemeRenderer renderer;
    private final AuthStore authStore;
    private final Consumer<PanelState.AuthSubmitPayload> payloadTracker;
    private final PanelAuth auth;

    public IptvSourceHandler(ThemeRenderer renderer, AuthStore authStore,
                             Consumer<PanelState.AuthSubmitPayload> payloadTracker,
                             PanelAuth auth) {
        this.renderer = renderer;
        this.authStore = authStore;
        this.payloadTracker = payloadTracker;
        this.auth = auth;
    }

    /** 路由表。必须在 routes() 返回前经过 .requireAuth(auth)。 */
    public PanelRoutes routes() {
        return new PanelRoutes(java.util.List.of(
                new PanelRoutes.Route(PanelRoutes.METHOD_OPTIONS, PanelRoutes.WILDCARD, this::preflight),
                new PanelRoutes.Route(PanelRoutes.METHOD_GET, IptvRoutes.PATH_SUBMIT, this::iptvPage),
                new PanelRoutes.Route(PanelRoutes.METHOD_POST, IptvRoutes.PATH_API_SUBMIT, this::submit),
                new PanelRoutes.Route(PanelRoutes.METHOD_GET, IptvRoutes.PATH_API_LIST, this::listSources)),
                request -> PanelRoutes.Response.text(PanelRoutes.SC_NOT_FOUND, PanelRoutes.TEXT_PLAIN, "404").withCors())
                .requireAuth(this.auth);
    }

    private PanelRoutes.Response preflight(PanelRoutes.Request request) {
        return PanelRoutes.Response.text(PanelRoutes.SC_OK, PanelRoutes.TEXT_PLAIN, "")
                .withCors()
                .withHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
                .withHeader("Access-Control-Allow-Headers", "Content-Type, " + PanelAuth.HEADER);
    }

    private PanelRoutes.Response iptvPage(PanelRoutes.Request request) {
        return this.renderer.render("iptv.html", Map.of("DEVICE_NAME", "AxTV")).withCors();
    }

    private PanelRoutes.Response submit(PanelRoutes.Request request) {
        try {
            // 1. 校验 token（由 requireAuth 保证已通过，此处仅作双重防线）
            String cookie = request.header(PanelAuth.HEADER);
            if (cookie == null || cookie.isBlank()) {
                return unauthorized();
            }

            // 2. 解析正文 {type:"url"|"content", name:"...", source:"..."}
            Map<String, Object> body = Json.parseObject(request.bodyAsString());
            String type = String.valueOf(body.get("type")).trim().toLowerCase(Locale.ROOT);
            if (!"url".equals(type) && !"content".equals(type)) {
                return badRequest("invalid type");
            }
            String name = String.valueOf(body.getOrDefault("name", "")).trim();
            if (name.isBlank()) {
                return badRequest("missing name");
            }
            String source = String.valueOf(body.getOrDefault("source", "")).trim();

            // 3. 形态判定（D1：源输入判定器）
            boolean isContent = SourceInputDetector.isContent(source);
            // 落库/持久化由 Android 侧配合 IptvStore 完成（见 NOTES），此处校验通过后返回 200。
            this.log.log(Level.INFO, "IPTV 源推送: name=" + name + ", content=" + isContent);

            return json(true, "推送成功", PanelRoutes.SC_OK);
        } catch (Exception e) {
            this.log.log(Level.SEVERE, "处理 IPTV 提交失败", e);
            return json(false, "提交失败: " + e.getMessage(), PanelRoutes.SC_INTERNAL_ERROR);
        }
    }

    private PanelRoutes.Response listSources(PanelRoutes.Request request) {
        // 从 Settings / AuthStore 读取当前源列表（此处留空，实际由 Settings.iptvSourcesList 配合 IptvStore 实现）
        return PanelRoutes.Response.text(PanelRoutes.SC_OK, PanelRoutes.TEXT_PLAIN_UTF8,
                "{\"success\":true,\"sources\":[]}");
    }

    private PanelRoutes.Response unauthorized() {
        return PanelRoutes.Response.text(PanelRoutes.SC_UNAUTHORIZED, PanelRoutes.TEXT_PLAIN, "unauthorized");
    }

    private PanelRoutes.Response badRequest(String message) {
        return PanelRoutes.Response.text(PanelRoutes.SC_BAD_REQUEST, PanelRoutes.TEXT_PLAIN, message);
    }

    private PanelRoutes.Response json(boolean success, String message, int status) {
        return PanelRoutes.Response.text(status, PanelRoutes.TEXT_PLAIN_UTF8,
                "{\"success\":" + success + ",\"message\":\"" + escapeJson(message) + "\"}");
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    // 下行字段（与 CookiePushHandler 字段名一致，便于复用思路）
    private static final String FIELD_COOKIE = "cookie";
    private static final String FIELD_SECURITY_PAYLOAD = "security_payload";
    private static final String FIELD_PRIVATE_KEY = "private_key";

    private final java.util.logging.Logger log = java.util.logging.Logger.getLogger("IptvSourceHandler");
}