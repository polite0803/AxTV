/*
 * 来源: src/app/p000/vc4.java（端口 9979 的 HTTP 服务，只服务 / 与 /search）
 *       + src/app/p000/wc4.java m11491o（WebSocket 上行消息解析）
 *       + src/app/p000/zc4.java（服务器生命周期与状态流）
 * 原职责: 手机上的「搜索输入」面板页；以及手机通过 WebSocket 发来的输入/搜索指令
 * 重构说明: HTTP 侧只剩「渲染 search.html」；消息解析交给 PanelState（类型与字段名原样保留）。
 */
package axtv.panel;

/**
 * 搜索输入面板（HTTP 端口 9979，WebSocket 端口 9980）。
 *
 * <p>手机打开 {@code http://<电视IP>:9979/} 拿到 {@code search.html}，页面再连
 * {@code ws://<电视IP>:9980} 做双向同步。HTTP 侧**只有两个路径**可用，其余 404。
 */
public final class SearchInputHandler {

    /** 面板页端口（原 vc4 的 super(9979)）。 */
    public static final int PORT = 9979;

    public static final String PATH_ROOT = "/";
    public static final String PATH_SEARCH = "/search";

    private final ThemeRenderer renderer;
    private final PanelState state;
    private final PanelAuth auth;

    public SearchInputHandler(ThemeRenderer renderer, PanelState state, PanelAuth auth) {
        this.renderer = renderer;
        this.state = state;
        this.auth = auth;
    }

    /**
     * 路由表。
     *
     * <p>原实现对 {@code /}、{@code /search} 不校验 HTTP 方法（任何方法都返回页面），这里保持，
     * 因此用 {@link PanelRoutes#ANY_METHOD} 注册。
     */
    public PanelRoutes routes() {
        PanelRoutes.Handler page = request -> this.renderer.render("search.html", java.util.Map.of());
        return new PanelRoutes(java.util.List.of(
                new PanelRoutes.Route(PanelRoutes.ANY_METHOD, PATH_ROOT, page),
                new PanelRoutes.Route(PanelRoutes.ANY_METHOD, PATH_SEARCH, page)),
                request -> PanelRoutes.Response.text(PanelRoutes.SC_NOT_FOUND, PanelRoutes.TEXT_PLAIN, "404"))
                .requireAuth(this.auth);
    }

    /** WebSocket 上行消息入口：解析 type/text 后分发给 UI（原 wc4.m11491o）。 */
    public void onPhoneMessage(String rawText) {
        this.state.dispatch(this.state.parsePhoneMessage(rawText));
    }
}
