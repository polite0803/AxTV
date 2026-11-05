/*
 * 来源: src/app/com/github/mytv/p004dv/widget/WidgetVideo.java (254 行)
 *       + WidgetVideo$$serializer.java:29-36 (descriptor 元素名)
 * 原职责: 小组件用视频条目；7 个 String 字段，默认空串，kotlinx.serialization @Serializable
 * 重构说明: 精简为 record；JSON key 逐字来自 descriptor；null 入参归一为空串。
 */
package axtv.widget;

/** 小组件视频条目。 */
public record WidgetVideo(
        @JsonKey("id")        String id,
        @JsonKey("title")     String title,
        @JsonKey("author")    String author,
        @JsonKey("avatarUrl") String avatarUrl,
        @JsonKey("coverUrl")  String coverUrl,
        @JsonKey("duration")  String duration,
        @JsonKey("likes")     String likes) {

    /** 原 descriptor 的 serial name（WidgetVideo$$serializer.java:29）。 */
    public static final String SERIAL_NAME = "com.github.axtv.dv.widget.WidgetVideo";

    public WidgetVideo {
        id = nz(id);
        title = nz(title);
        author = nz(author);
        avatarUrl = nz(avatarUrl);
        coverUrl = nz(coverUrl);
        duration = nz(duration);
        likes = nz(likes);
    }

    /** 原无参构造：全字段默认空串。 */
    public WidgetVideo() {
        this("", "", "", "", "", "", "");
    }

    public static WidgetVideo empty() {
        return new WidgetVideo();
    }

    static String nz(String s) {
        return s == null ? "" : s;
    }
}
