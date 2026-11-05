/*
 * 来源: src/app/com/github/mytv/p004dv/widget/MixSnapshot.java (169 行)
 *       + write$Self$app:75-86 暴露 descriptor 元素顺序 accountId/state/items
 * 原职责: 合集追更列表快照
 * 重构说明: 精简为 record；默认 accountId="", state=LOADING, items=[]。
 */
package axtv.widget;

import java.util.List;

/** 合集追更列表快照。 */
public record MixSnapshot(
        @JsonKey("accountId") String accountId,
        @JsonKey("state")     WidgetLoadState state,
        @JsonKey("items")     List<WidgetMixItem> items) {

    /** 推定 serial name（见 NOTES.md 存疑）。 */
    public static final String SERIAL_NAME = "com.github.axtv.dv.widget.MixSnapshot";

    public MixSnapshot {
        accountId = WidgetVideo.nz(accountId);
        state = state == null ? WidgetLoadState.LOADING : state;
        items = items == null ? List.of() : List.copyOf(items);
    }

    /** 原无参构造：accountId="", state=LOADING, items=[]。 */
    public MixSnapshot() {
        this("", WidgetLoadState.LOADING, List.of());
    }

    public static MixSnapshot empty() {
        return new MixSnapshot();
    }

    /** 对应 Kotlin copy$default：保留 accountId，替换 state/items。 */
    public MixSnapshot copy(WidgetLoadState newState, List<WidgetMixItem> newItems) {
        return new MixSnapshot(accountId, newState, newItems);
    }
}
