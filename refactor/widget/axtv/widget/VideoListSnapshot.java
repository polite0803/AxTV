/*
 * 来源: src/app/com/github/mytv/p004dv/widget/VideoListSnapshot.java (169 行)
 *       + write$Self$app:75-86 暴露 descriptor 元素顺序 accountId/state/items
 * 原职责: 历史 / 稍后再看 两类列表的持久化快照
 * 重构说明: 精简为 record；默认 accountId="", state=LOADING, items=[]；提供 copy 等价方法。
 */
package axtv.widget;

import java.util.List;

/** 视频列表快照（history / later 共用）。 */
public record VideoListSnapshot(
        @JsonKey("accountId") String accountId,
        @JsonKey("state")     WidgetLoadState state,
        @JsonKey("items")     List<WidgetVideo> items) {

    /** 推定 serial name（原 VideoListSnapshot$$serializer 未随包提供，见 NOTES.md 存疑）。 */
    public static final String SERIAL_NAME = "com.github.axtv.dv.widget.VideoListSnapshot";

    public VideoListSnapshot {
        accountId = WidgetVideo.nz(accountId);
        state = state == null ? WidgetLoadState.LOADING : state;
        items = items == null ? List.of() : List.copyOf(items);
    }

    /** 原无参构造：accountId="", state=LOADING, items=[]。 */
    public VideoListSnapshot() {
        this("", WidgetLoadState.LOADING, List.of());
    }

    public static VideoListSnapshot empty() {
        return new VideoListSnapshot();
    }

    /** 对应 Kotlin copy$default：保留 accountId，替换 state/items。 */
    public VideoListSnapshot copy(WidgetLoadState newState, List<WidgetVideo> newItems) {
        return new VideoListSnapshot(accountId, newState, newItems);
    }
}
