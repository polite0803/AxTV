/*
 * 来源: src/app/com/github/mytv/p004dv/model/LiveRoomUtilsKt.java
 * 原职责: 直播观看人数展示的唯一出口（优先用 room_view_stats 的主播视角中文案，否则退回 user_count_str）
 * 重构说明: 顶层函数 → 静态工具方法，行为逐行还原
 */
package axtv.model;

/** 直播模型的小工具。 */
public final class LiveRoomUtils {

    private LiveRoomUtils() {
    }

    /**
     * 原 {@code displayViewerCount(LiveRoom)}。
     *
     * <p>规则：{@code roomViewStats != null && displayType == 1 && displayMiddleAnchor 非空}
     * → 用 {@code displayMiddleAnchor}；否则用 {@code userCount}，其值为空时返回 {@code "0"}。
     */
    public static String displayViewerCount(LiveRoom liveRoom) {
        RoomViewStats stats = liveRoom.roomViewStats();
        if (stats != null && stats.displayType() == 1 && !stats.displayMiddleAnchor().isEmpty()) {
            return stats.displayMiddleAnchor();
        }
        String userCount = liveRoom.userCount();
        return userCount.isEmpty() ? "0" : userCount;
    }
}
