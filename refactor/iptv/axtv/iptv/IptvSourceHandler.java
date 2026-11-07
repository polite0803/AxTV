/*
 * 来源: 新建（电视直播功能，无原版对应）。
 * 原职责: 无。
 * 重构说明: 挂在 Cookie 推送那台 9978 服务器上的 IPTV 推送路由（不另开端口）。手机打开 /iptv 页 →
 *           填名称 + 粘贴「一条源 URL 或整份 M3U 正文」→ POST /api/iptv → 经 SourceInputDetector
 *           判形态后落进 IptvStore（Settings.iptv_sources_list）。GET /api/iptv/list 回读当前用户源。
 *
 * 安全约束: 本类只提供**未鉴权**路由（{@link #routes()}），令牌校验由 PanelController 合并路由后
 *           统一 requireAuth 完成（与 Cookie 推送共用同一 PanelAuth，不为直播源另造免鉴权通道）。
 *           这正是 PLAN §C 的要求——不得为直播源放开鉴权。OPTIONS 预检由 Cookie 处理器统一提供。
 */
package axtv.iptv;

import axtv.panel.Json;
import axtv.panel.PanelRoutes;
import axtv.panel.ThemeRenderer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * IPTV 源推送处理器。
 *
 * <p>路由（并入 9978 服务器，顺序见 {@link #routes()}）：
 * <pre>
 * GET     /iptv           200 iptv.html（DEVICE_NAME 已替换）
 * POST    /api/iptv       200 {"success":true,"message":…}   落库一条用户源
 *                         400 {"success":false,"message":…}   缺名称或内容为空
 * GET     /api/iptv/list  200 {"success":true,"sources":[…]}  回读当前用户源
 * </pre>
 */
public final class IptvSourceHandler {

    /** POST JSON 字段名。 */
    public static final String FIELD_NAME = "name";
    public static final String FIELD_SOURCE = "source";

    private static final String MESSAGE_OK = "推送成功！直播源已保存";

    private final ThemeRenderer renderer;
    private final IptvStore store;
    private final Supplier<String> deviceName;
    private final Logger log = Logger.getLogger("IptvServer");

    public IptvSourceHandler(ThemeRenderer renderer, IptvStore store, Supplier<String> deviceName) {
        this.renderer = renderer;
        this.store = store;
        this.deviceName = deviceName;
    }

    /**
     * 未鉴权路由列表。PanelController 用 {@code cookieRoutes.with(...).requireAuth(auth)} 并入同一台服务器，
     * 因此这里不注册 OPTIONS，也不各自 requireAuth。
     */
    public List<PanelRoutes.Route> routes() {
        List<PanelRoutes.Route> routes = new ArrayList<>();
        routes.add(new PanelRoutes.Route(PanelRoutes.METHOD_GET, IptvRoutes.PATH_PAGE, this::inputPage));
        routes.add(new PanelRoutes.Route(PanelRoutes.METHOD_POST, IptvRoutes.PATH_API_SUBMIT, this::submit));
        routes.add(new PanelRoutes.Route(PanelRoutes.METHOD_GET, IptvRoutes.PATH_API_LIST, this::listSources));
        return routes;
    }

    /** GET /iptv：渲染直播源输入页。 */
    private PanelRoutes.Response inputPage(PanelRoutes.Request request) {
        return this.renderer.render("iptv.html", Map.of("DEVICE_NAME", this.deviceName.get())).withCors();
    }

    /**
     * POST /api/iptv：判形态 → 落库 → 回 JSON。
     *
     * <p>名称与源内容都必填；形态（URL / 整份正文）由 {@link SourceInputDetector} 判定后不额外存类型，
     * 存储层只保留原文，读取侧再判一次形态后交给 {@link PlaylistLoader}。
     */
    private PanelRoutes.Response submit(PanelRoutes.Request request) {
        try {
            Map<String, Object> body = Json.parseObject(request.bodyAsString());
            String name = Json.optString(body, FIELD_NAME).trim();
            String source = Json.optString(body, FIELD_SOURCE).trim();

            if (name.isEmpty() || source.isEmpty()) {
                return json(false, "请同时填写频道名称和直播源（URL 或整份 M3U 正文）",
                        PanelRoutes.SC_BAD_REQUEST);
            }
            boolean isContent = SourceInputDetector.isContent(source);
            this.store.addSource(new IptvStore.SourceJson(
                    name, source, "", "", "user-" + newId(name)));
            this.log.log(Level.INFO, "收到 IPTV 源推送: name=" + name
                    + " form=" + (isContent ? "content" : "url"));
            return json(true, MESSAGE_OK, PanelRoutes.SC_OK);
        } catch (Exception e) {
            this.log.log(Level.SEVERE, "处理 IPTV 提交失败", e);
            return json(false, "提交失败: " + e.getMessage(), PanelRoutes.SC_INTERNAL_ERROR);
        }
    }

    /** GET /api/iptv/list：回读当前用户源（供手机端确认已推上去）。 */
    private PanelRoutes.Response listSources(PanelRoutes.Request request) {
        List<String> items = new ArrayList<>();
        for (IptvStore.SourceJson s : this.store.sources()) {
            items.add(Json.object(
                    "name", s.name(),
                    "source", s.urlOrContent(),
                    "source_id", s.sourceId()));
        }
        String body = "{\"success\":true,\"sources\":[" + String.join(",", items) + "]}";
        return PanelRoutes.Response.text(PanelRoutes.SC_OK, PanelRoutes.APPLICATION_JSON, body).withCors();
    }

    private static String newId(String name) {
        return Integer.toHexString(name.toLowerCase(Locale.ROOT).hashCode())
                + "-" + Long.toHexString(System.nanoTime());
    }

    private static PanelRoutes.Response json(boolean success, String message, int status) {
        return PanelRoutes.Response.text(status, PanelRoutes.APPLICATION_JSON,
                Json.object("success", success, "message", message)).withCors();
    }
}