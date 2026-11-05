/*
 * 来源: src/app/com/github/mytv/p004dv/widget/WidgetMixItem.java (296 行)
 *       + WidgetMixItem$$serializer.java:32-40 (descriptor 元素名)
 * 原职责: 合集追更条目；8 个字段，含 nextEpisodes: List<WidgetEpisode>
 * 重构说明: 精简为 record；List 归一为不可变空表；默认值 ""/0/emptyList 原样保留。
 */
package axtv.widget;

import java.util.List;

/** 合集追更条目。 */
public record WidgetMixItem(
        @JsonKey("id")             String id,
        @JsonKey("name")           String name,
        @JsonKey("author")         String author,
        @JsonKey("coverUrl")       String coverUrl,
        @JsonKey("episode")        int episode,
        @JsonKey("total")          int total,
        @JsonKey("resumeAwemeId")  String resumeAwemeId,
        @JsonKey("nextEpisodes")   List<WidgetEpisode> nextEpisodes) {

    /** 原 descriptor 的 serial name（WidgetMixItem$$serializer.java:32）。 */
    public static final String SERIAL_NAME = "com.github.axtv.dv.widget.WidgetMixItem";

    public WidgetMixItem {
        id = WidgetVideo.nz(id);
        name = WidgetVideo.nz(name);
        author = WidgetVideo.nz(author);
        coverUrl = WidgetVideo.nz(coverUrl);
        resumeAwemeId = WidgetVideo.nz(resumeAwemeId);
        nextEpisodes = nextEpisodes == null ? List.of() : List.copyOf(nextEpisodes);
    }

    /** 原无参构造：String="", int=0, nextEpisodes=[]。 */
    public WidgetMixItem() {
        this("", "", "", "", 0, 0, "", List.of());
    }
}
