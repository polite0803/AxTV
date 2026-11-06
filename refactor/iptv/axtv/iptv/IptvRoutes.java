/*
 * 来源: 新建（电视直播功能，无原版对应；仅沿用 submit.html 骨架与 PanelController.routes 写法）。
 * 原职责: 无。
 * 重构说明: 用于手机扫码/手动输入直播源的推送页。沿用 panel-head.html 的占位符 {{THEME_CSS}} / {{PANEL_HEAD}} / {{DEVICE_NAME}}。
 *          POST /api/iptv 接收 {source:"url"|"content", name, ...} 并落库；成功后渲染 success.html。
 *          挂在 9978 端口，与 Cookie 推送共用同一 PanelAuth 令牌。
 */
package axtv.iptv;

/** IptvSourceHandler 路由使用的常量。 */
public final class IptvRoutes {
    public static final int PORT = 9978;
    public static final String PATH_SUBMIT = "/submit";
    public static final String PATH_API_SUBMIT = "/api/iptv";
    public static final String PATH_API_LIST = "/api/iptv/list";
    private IptvRoutes() {}
}