/*
 * 来源: src/app/com/github/mytv/p004dv/widget/WidgetLoadState.java (18 行)
 * 原职责: 快照加载状态枚举
 * 重构说明: 原样保留 LOADING / READY / ERROR 三个常量与顺序（kotlinx.serialization 按名字序列化）。
 */
package axtv.widget;

/** 快照加载状态。 */
public enum WidgetLoadState {
    LOADING,
    READY,
    ERROR
}
