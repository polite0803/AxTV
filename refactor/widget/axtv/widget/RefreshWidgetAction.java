/*
 * 来源: src/app/com/github/mytv/p004dv/widget/RefreshWidgetAction.java (101 行)
 * 原职责: Glance ActionCallback —— 从 ActionParameters 读 "widget_kind" → WidgetKind.valueOf → refresh
 * 重构说明: 完整保留参数 key、valueOf 容错解析、有效 kind 才刷新的分支。
 */
package axtv.widget;

/** 「重试 / 立即刷新」按钮的 ActionCallback 逻辑。 */
public final class RefreshWidgetAction {

    private final WidgetRefresher refresher;

    public RefreshWidgetAction(WidgetRefresher refresher) {
        this.refresher = refresher;
    }

    /**
     * @param widgetKindParam ActionParameters["widget_kind"] 的原始字符串，可为 null
     * @return 是否实际触发了刷新
     */
    public boolean onAction(String widgetKindParam) {
        WidgetKind kind = WidgetKind.fromName(widgetKindParam);
        if (kind == null) {
            return false;
        }
        return refresher.refresh(kind);
    }
}
