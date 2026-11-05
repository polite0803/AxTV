/*
 * 来源: src/app/com/github/mytv/p004dv/widget/LiveSnapshot.java (193 行)
 *       + write$Self$app:85-99 暴露 descriptor 元素顺序 accountId/state/items/updatedAt
 * 原职责: 关注直播列表快照，带最后更新时间戳
 * 重构说明: 精简为 record；默认 accountId="", state=LOADING, items=[], updatedAt=0。
 */
package axtv.widget;

import java.util.List;

/** 直播列表快照。 */
public record LiveSnapshot(
        @JsonKey("accountId")  String accountId,
        @JsonKey("state")      WidgetLoadState state,
        @JsonKey("items")      List<WidgetLiveItem> items,
        @JsonKey("updatedAt")  long updatedAt) {

    /** 推定 serial name（见 NOTES.md 存疑）。 */
    public static final String SERIAL_NAME = "com.github.axtv.dv.widget.LiveSnapshot";

    public LiveSnapshot {
        accountId = WidgetVideo.nz(accountId);
        state = state == null ? WidgetLoadState.LOADING : state;
        items = items == null ? List.of() : List.copyOf(items);
    }

    /** 原无参构造：accountId="", state=LOADING, items=[], updatedAt=0。 */
    public LiveSnapshot() {
        this("", WidgetLoadState.LOADING, List.of(), 0L);
    }

    public static LiveSnapshot empty() {
        return new LiveSnapshot();
    }

    /** 对应 Kotlin copy$default：保留 accountId，替换 state/items/updatedAt。 */
    public LiveSnapshot copy(WidgetLoadState newState, List<WidgetLiveItem> newItems, long newUpdatedAt) {
        return new LiveSnapshot(accountId, newState, newItems, newUpdatedAt);
    }
}
