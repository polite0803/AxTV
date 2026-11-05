/*
 * 来源: src/app/com/github/mytv/p004dv/widget/WidgetActionsKt.java (48 行)
 *       + DesktopWidgetsKt.java:1173-1177 (媒体键 keyCode 88 / 85 / 87)
 * 原职责: 构造三类 Glance action；提供 isDeviceProtected 判定；widget_kind 参数 key
 * 重构说明: 逐字还原参数 key "widget_kind"、媒体键常量、action 构造语义；
 *           isDeviceProtected 委托 WidgetSettings，语义在 NOTES.md 说明。
 */
package axtv.widget;

/** 小组件动作工厂（原 WidgetActionsKt 顶层函数）。 */
public final class WidgetActions {

    /** Glance ActionParameters key，逐字还原 WidgetActionsKt.java:21。 */
    public static final String WIDGET_KIND_PARAMETER = "widget_kind";

    // Android KeyEvent 媒体键常量（DesktopWidgetsKt.java:1173-1177 传入的实参）
    public static final int KEYCODE_MEDIA_PLAY_PAUSE = 85;
    public static final int KEYCODE_MEDIA_STOP       = 86;
    public static final int KEYCODE_MEDIA_NEXT       = 87;
    public static final int KEYCODE_MEDIA_PREVIOUS   = 88;
    public static final int KEYCODE_MEDIA_PLAY       = 126;
    public static final int KEYCODE_MEDIA_PAUSE      = 127;

    private WidgetActions() {
    }

    /** 对应 mediaAction(context, i)：发 WidgetMediaActionReceiver.ACTION_MEDIA 广播。 */
    public static WidgetAction mediaAction(int keyCode) {
        return WidgetAction.mediaKey(keyCode);
    }

    /** 对应 refreshAction(widgetKind)：ActionParameters("widget_kind" → kind.name())。 */
    public static WidgetAction refreshAction(WidgetKind kind) {
        return WidgetAction.refresh(kind);
    }

    /** 对应 widgetAction(route, context, i)：启动 MainActivity 并带 ROUTE extra。 */
    public static WidgetAction widgetAction(String route) {
        return WidgetAction.openRoute(route);
    }

    /** 对应 isDeviceProtected：SDK_INT>=24 && context.isDeviceProtectedStorage()。 */
    public static boolean isDeviceProtected(WidgetSettings settings) {
        return settings.sdkInt() >= 24 && settings.isDeviceProtected();
    }
}
