/*
 * 来源: src/app/p000/qj0.java（CookieServer 的 serve()）+ src/app/p000/k63.java（响应构造）
 * 原职责: 端口 9978 上的 Cookie 推送服务：
 *         GET /submit 推送页、GET /api/info 设备信息、POST /api/submit 落地 Cookie 与互动凭据、
 *         GET /logcat 导出日志、OPTIONS 预检、其余 404
 * 重构说明: 从 serve() 的 if-else 拆成路由表 + 各处理函数；字段名 cookie / security_payload / private_key
 *           与返回 JSON 的 success / message / device_name / app_name 原样保留。
 *           已补 PanelAuth 令牌：所有路由（OPTIONS 预检除外）都要求 ?token= 或 X-AxTV-Token；
 *           明文 HTTP 仍保留（局域网面板），限制与残余风险见 NOTES.md「安全风险」。
 */
package axtv.panel;

import java.util.Map;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Cookie 推送处理器（端口 9978）。
 *
 * <p>手机侧是 {@code http://<电视IP>:9978/submit} 这一页：粘贴 Cookie 与互动凭据 → 点「推送到电视」→
 * 前端 POST {@code /api/submit} → 电视落盘并立即换登录态。
 *
 * <p>路由（与原实现的判断顺序一致）：
 * <pre>
 * OPTIONS *            200 空体 + CORS(含 Max-Age: 86400)
 * GET     /submit      200 submit.html（DEVICE_NAME 已替换）
 * GET     /api/info    200 {"success":true,"device_name":…,"app_name":"AxTV"}
 * POST    /api/submit  200 {"success":true,"message":"推送成功！登录信息已更新"}
 *                      400 {"success":false,"message":"请至少填写 Cookie 或互动安全凭据"}
 *                      500 {"success":false,"message":"提交失败: …"}
 * GET     /logcat      见 LogcatExportHandler
 * 其它                 404 text/plain "404"
 * </pre>
 */
public final class CookiePushHandler {

    /** Cookie 推送服务端口（原 qj0 的 super(9978)）。 */
    public static final int PORT = 9978;

    /** 推送页路径（原 qj0.m9586h() 拼出的地址：http://ip:9978/submit）。 */
    public static final String PATH_SUBMIT = "/submit";
    public static final String PATH_API_INFO = "/api/info";
    public static final String PATH_API_SUBMIT = "/api/submit";
    public static final String PATH_LOGCAT = "/logcat";

    /** 手机端 POST 的 JSON 字段名。 */
    public static final String FIELD_COOKIE = "cookie";
    public static final String FIELD_SECURITY_PAYLOAD = "security_payload";
    public static final String FIELD_PRIVATE_KEY = "private_key";

    private static final String MESSAGE_NEED_INPUT = "请至少填写 Cookie 或互动安全凭据";
    private static final String MESSAGE_OK = "推送成功！登录信息已更新";

    private final ThemeRenderer renderer;
    private final AuthStore authStore;
    private final PanelState state;
    private final Supplier<String> deviceName;
    private final LogcatExportHandler logcatHandler;
    private final PanelAuth auth;
    private final Logger log = Logger.getLogger("CookieServer");

    public CookiePushHandler(ThemeRenderer renderer, AuthStore authStore, PanelState state,
                             Supplier<String> deviceName, LogcatExportHandler logcatHandler,
                             PanelAuth auth) {
        this.renderer = renderer;
        this.authStore = authStore;
        this.state = state;
        this.deviceName = deviceName;
        this.logcatHandler = logcatHandler;
        this.auth = auth;
    }

    /** 路由表（已带令牌校验）。独立使用时走这个。 */
    public PanelRoutes routes() {
        return routesWithoutAuth().requireAuth(this.auth);
    }

    /**
     * 未加令牌校验的 Cookie 路由。PanelController 用它把 IPTV 路由并进同一台 9978 服务器后再统一
     * {@code requireAuth}，避免两台服务器抢同一端口。OPTIONS 预检与各处理函数自行追加 CORS 头。
     */
    public PanelRoutes routesWithoutAuth() {
        return new PanelRoutes(java.util.List.of(
                new PanelRoutes.Route(PanelRoutes.METHOD_OPTIONS, PanelRoutes.WILDCARD, this::preflight),
                new PanelRoutes.Route(PanelRoutes.METHOD_GET, PATH_SUBMIT, this::submitPage),
                new PanelRoutes.Route(PanelRoutes.METHOD_GET, PATH_API_INFO, this::info),
                new PanelRoutes.Route(PanelRoutes.METHOD_POST, PATH_API_SUBMIT, this::submit),
                new PanelRoutes.Route(PanelRoutes.METHOD_GET, PATH_LOGCAT, this.logcatHandler::handle)),
                request -> PanelRoutes.Response.text(PanelRoutes.SC_NOT_FOUND, PanelRoutes.TEXT_PLAIN, "404").withCors());
    }

    /** OPTIONS 预检：空体 200 + 4 个 CORS 头（预检多一个 Max-Age）。 */
    private PanelRoutes.Response preflight(PanelRoutes.Request request) {
        return PanelRoutes.Response.text(PanelRoutes.SC_OK, PanelRoutes.TEXT_PLAIN, "")
                .withCors()
                .withHeader("Access-Control-Max-Age", "86400");
    }

    /** GET /submit：渲染推送页，只替换 DEVICE_NAME（原实现同样给页面加 CORS 头）。 */
    private PanelRoutes.Response submitPage(PanelRoutes.Request request) {
        return this.renderer.render("submit.html", Map.of("DEVICE_NAME", this.deviceName.get())).withCors();
    }

    /** GET /api/info：手机端探测设备名（页面标题/成功页都会用到）。 */
    private PanelRoutes.Response info(PanelRoutes.Request request) {
        String body = Json.object(
                "success", true,
                "device_name", this.deviceName.get(),
                "app_name", "AxTV");
        return PanelRoutes.Response.text(PanelRoutes.SC_OK, PanelRoutes.APPLICATION_JSON, body).withCors();
    }

    /**
     * POST /api/submit：落盘 + 通知 UI。
     *
     * <p>注意与原实现一致的两点：
     * <ul>
     *   <li>三个字段都是 trim 后判空，全空即 400；</li>
     *   <li>日志与回传 payload 取的是**落盘后**的值，不是本次请求里的值（推送只填一项时，
     *       另一项沿用旧的）。</li>
     * </ul>
     */
    private PanelRoutes.Response submit(PanelRoutes.Request request) {
        try {
            Map<String, Object> body = Json.parseObject(request.bodyAsString());
            String cookie = Json.optString(body, FIELD_COOKIE).trim();
            String security = Json.optString(body, FIELD_SECURITY_PAYLOAD).trim();
            String privateKey = Json.optString(body, FIELD_PRIVATE_KEY).trim();

            if (cookie.isEmpty() && security.isEmpty() && privateKey.isEmpty()) {
                return json(false, MESSAGE_NEED_INPUT, PanelRoutes.SC_BAD_REQUEST);
            }
            if (!cookie.isEmpty()) {
                this.authStore.setCookie(cookie);
            }
            if (!security.isEmpty() || !privateKey.isEmpty()) {
                this.authStore.setInteractionSecurity(security, InteractionSecurityParser.parse(security, privateKey));
            }

            PanelState.AuthSubmitPayload payload = new PanelState.AuthSubmitPayload(
                    this.authStore.cookie(),
                    this.authStore.interactionSecurityPayload(),
                    this.authStore.interactionPrivateKey());
            this.state.authPushed(payload);
            this.log.log(Level.INFO, "收到扫码提交: cookie=" + !payload.cookie().isBlank()
                    + ", security=" + this.authStore.hasInteractionSecurity());
            return json(true, MESSAGE_OK, PanelRoutes.SC_OK);
        } catch (Exception e) {
            this.log.log(Level.SEVERE, "处理提交失败", e);
            return json(false, "提交失败: " + e.getMessage(), PanelRoutes.SC_INTERNAL_ERROR);
        }
    }

    private static PanelRoutes.Response json(boolean success, String message, int status) {
        return PanelRoutes.Response.text(status, PanelRoutes.APPLICATION_JSON,
                Json.object("success", success, "message", message)).withCors();
    }
}
