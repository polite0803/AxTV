/*
 * 来源: src/app/com/github/mytv/p004dv/widget/WidgetActionsKt.java (48 行) + WidgetRoutes.java
 * 原职责: 小组件点击动作分三类：启动 Activity(带 ROUTE extra)、发媒体键广播、触发刷新回调
 * 重构说明: 把 Glance 的 InterfaceC0548d6（action）抽象成可执行的数据模型，
 *           让「点了做什么」不依赖 Android SDK 也能被完整表达与消费。
 */
package axtv.widget;

/** 小组件点击动作模型。 */
public record WidgetAction(Type type, String route, int keyCode, WidgetKind kind) {

    /** 动作类型。 */
    public enum Type {
        /** WidgetActionsKt.widgetAction → startActivity(Intent(MainActivity, EXTRA_ROUTE=route))。 */
        OPEN_ROUTE,
        /** WidgetActionsKt.mediaAction → 发 WidgetMediaActionReceiver.ACTION_MEDIA 广播。 */
        MEDIA_KEY,
        /** WidgetActionsKt.refreshAction → RefreshWidgetAction 回调，携带 widget_kind 参数。 */
        REFRESH
    }

    public static WidgetAction openRoute(String route) {
        return new WidgetAction(Type.OPEN_ROUTE, route, -1, null);
    }

    public static WidgetAction mediaKey(int keyCode) {
        return new WidgetAction(Type.MEDIA_KEY, null, keyCode, null);
    }

    public static WidgetAction refresh(WidgetKind kind) {
        return new WidgetAction(Type.REFRESH, null, -1, kind);
    }

    /** 供 Android 侧消费：动作对应的 Intent/Broadcast/Callback 描述。 */
    public String describe() {
        switch (type) {
            case OPEN_ROUTE:
                return "startActivity(" + WidgetRoutes.TARGET_ACTIVITY
                        + ", flags=" + WidgetRoutes.INTENT_FLAGS
                        + ", " + WidgetRoutes.EXTRA_ROUTE + "=" + route + ")";
            case MEDIA_KEY:
                return "broadcast(" + WidgetMediaActionReceiver.ACTION_MEDIA
                        + ", " + WidgetMediaActionReceiver.EXTRA_KEY_CODE + "=" + keyCode + ")";
            case REFRESH:
                return "actionCallback(RefreshWidgetAction, " + WidgetActions.WIDGET_KIND_PARAMETER
                        + "=" + (kind != null ? kind.name() : "null") + ")";
            default:
                return "unknown";
        }
    }
}
