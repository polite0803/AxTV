/*
 * 来源: src/app/com/github/mytv/p004dv/widget/WidgetLiveItem.java (201 行)
 *       + WidgetLiveItem$$serializer.java:28-34 (descriptor 元素名)
 * 原职责: 直播间条目；5 个 String 字段，默认空串
 * 重构说明: 精简为 record；JSON key 逐字来自 descriptor。
 */
package axtv.widget;

/** 直播间条目。 */
public record WidgetLiveItem(
        @JsonKey("roomId")    String roomId,
        @JsonKey("title")     String title,
        @JsonKey("author")    String author,
        @JsonKey("avatarUrl") String avatarUrl,
        @JsonKey("viewers")   String viewers) {

    /** 原 descriptor 的 serial name（WidgetLiveItem$$serializer.java:28）。 */
    public static final String SERIAL_NAME = "com.github.axtv.dv.widget.WidgetLiveItem";

    public WidgetLiveItem {
        roomId = WidgetVideo.nz(roomId);
        title = WidgetVideo.nz(title);
        author = WidgetVideo.nz(author);
        avatarUrl = WidgetVideo.nz(avatarUrl);
        viewers = WidgetVideo.nz(viewers);
    }

    /** 原无参构造：全字段默认空串。 */
    public WidgetLiveItem() {
        this("", "", "", "", "");
    }
}
