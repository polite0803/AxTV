/*
 * 来源: src/app/com/github/mytv/p004dv/widget/WidgetKind.java:8-28
 * 原职责: 五个可周期刷新的小组件种类，各带一个刷新周期（分钟）
 * 重构说明: 常量与 intervalMinutes 数值逐字保留（HISTORY/LATER/MIX=360, LIVE=30, SEARCH=60）。
 *           注意 NowPlayingWidget 没有对应 kind（播放状态驱动，不参与周期刷新）。
 */
package axtv.widget;

/** 可周期刷新的小组件种类。 */
public enum WidgetKind {
    HISTORY(360),
    LATER(360),
    MIX(360),
    LIVE(30),
    SEARCH(60);

    private final long intervalMinutes;

    WidgetKind(long intervalMinutes) {
        this.intervalMinutes = intervalMinutes;
    }

    /** 周期刷新间隔（分钟），来源 WidgetKind.java:9-13。 */
    public long getIntervalMinutes() {
        return intervalMinutes;
    }

    /** 从 WorkManager input data 的字符串安全解析（对应 WidgetKind.valueOf）。 */
    public static WidgetKind fromName(String name) {
        if (name == null) {
            return null;
        }
        try {
            return valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
