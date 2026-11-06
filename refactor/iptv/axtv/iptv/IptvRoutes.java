/*
 * 来源: 新建（电视直播功能，无原版对应；仅沿用 submit.html 骨架与 PanelController.routes 写法）。
 * 原职责: 无。
 * 重构说明: 用于手机扫码/手动输入直播源的推送页。沿用 panel-head.html 的占位符 {{THEME_CSS}} / {{PANEL_HEAD}} / {{DEVICE_NAME}}。
 *          POST /api/iptv 接收 {name, source}（source 由 SourceInputDetector 判 URL/正文）并落库。
 *          不另开端口：路由并入 Cookie 推送那台 9978 服务器（见 PanelController），共用同一 PanelAuth 令牌。
 */
package axtv.iptv;

/** IptvSourceHandler 路由使用的常量（挂在 Cookie 推送的 9978 端口上）。 */
public final class IptvRoutes {
    /** 直播源输入页路径。刻意与 Cookie 推送页 {@code /submit} 区分，两者共用同一台服务器。 */
    public static final String PATH_PAGE = "/iptv";
    public static final String PATH_API_SUBMIT = "/api/iptv";
    public static final String PATH_API_LIST = "/api/iptv/list";
    private IptvRoutes() {}
}