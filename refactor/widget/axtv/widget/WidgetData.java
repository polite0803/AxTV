/*
 * 来源: src/app/com/github/mytv/p004dv/widget/WidgetDataKt.java (78 行)
 * 原职责: toWidgetVideo(Aweme) / toWidgetLiveItem(LiveRoom) —— App model → 小组件模型的字段映射与兜底
 * 重构说明: 保留全部兜底文案（"无标题视频"/"正在直播"/"主播"/"0"）、URL 回退顺序、
 *           时长格式化 "%d:%02d"、点赞数格式化（由 CountFormatter 注入原 DataModelKt.formatCount）。
 */
package axtv.widget;

import java.util.List;

/** App 原始数据 → 小组件数据模型的转换（原 WidgetDataKt 顶层函数）。 */
public final class WidgetData {

    private WidgetData() {
    }

    /** 空串 / 全空白判定，对应 p000.jx4.m6866L。 */
    public static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /** 取列表中第一个非空串，对应 p000.o70.m8483U0 (firstOrNull) + 空值兜底。 */
    public static String firstNonBlank(List<String> urls) {
        if (urls != null) {
            for (String u : urls) {
                if (u != null && !u.isEmpty()) {
                    return u;
                }
            }
        }
        return null;
    }

    /** 时长格式化：毫秒 → 秒 → "分:秒"；durationSec<=0 返回 ""（WidgetDataKt.java:76）。 */
    public static String formatDuration(long durationMs) {
        long sec = durationMs / 1000L;
        if (sec <= 0) {
            return "";
        }
        return String.format("%d:%02d", sec / 60L, sec % 60L);
    }

    /**
     * Aweme → WidgetVideo。字段顺序：id / title / author / avatarUrl / coverUrl / duration / likes。
     * desc 空 → "无标题视频"；头像 thumb→medium；封面 originCover→cover；空则 ""。
     */
    public static WidgetVideo toWidgetVideo(WidgetDataSource.Video aweme,
                                            WidgetEnvironment.CountFormatter formatter) {
        String title = isBlank(aweme.desc()) ? "无标题视频" : aweme.desc();
        String avatar = firstNonBlank(aweme.authorAvatarUrls());
        String cover = firstNonBlank(aweme.coverUrls());
        String likes = formatter != null
                ? String.valueOf(formatter.formatCount(aweme.diggCount()))
                : String.valueOf(aweme.diggCount());
        return new WidgetVideo(
                WidgetVideo.nz(aweme.awemeId()),
                title,
                WidgetVideo.nz(aweme.authorNickname()),
                avatar == null ? "" : avatar,
                cover == null ? "" : cover,
                formatDuration(aweme.durationMs()),
                likes);
    }

    /**
     * LiveRoom → WidgetLiveItem。title 空 → "正在直播"；owner 空 → "主播"；
     * viewers 优先 roomViewStats.displayShort，其次 userCount，都空 → "0"。
     */
    public static WidgetLiveItem toWidgetLiveItem(WidgetDataSource.LiveRoom room) {
        String title = isBlank(room.title()) ? "正在直播" : room.title();
        String author = isBlank(room.ownerNickname()) ? "主播" : room.ownerNickname();
        String avatar = firstNonBlank(room.ownerAvatarUrls());
        String viewers = room.displayShort();
        if (isBlank(viewers)) {
            viewers = room.userCount();
        }
        if (isBlank(viewers)) {
            viewers = "0";
        }
        return new WidgetLiveItem(
                WidgetVideo.nz(room.resolvedRoomId()),
                title,
                author,
                avatar == null ? "" : avatar,
                viewers);
    }
}
