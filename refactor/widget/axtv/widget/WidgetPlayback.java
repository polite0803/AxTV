/*
 * 来源: src/app/com/github/mytv/p004dv/widget/WidgetPlayback.java (188 行)
 * 原职责: 正在播放状态（纯内存，不持久化、无 @Serializable）；isLive/durationMs 有默认值
 * 重构说明: 精简为 record；isLive 默认 false、durationMs 默认 0 由重载构造保留。
 */
package axtv.widget;

/** 正在播放状态（由播放器 publish 到 WidgetPlaybackStore）。 */
public record WidgetPlayback(
        String id,
        String title,
        String author,
        String coverUrl,
        String source,
        boolean isPlaying,
        boolean isLive,
        long durationMs) {

    public WidgetPlayback {
        id = WidgetVideo.nz(id);
        title = WidgetVideo.nz(title);
        author = WidgetVideo.nz(author);
        coverUrl = WidgetVideo.nz(coverUrl);
        source = WidgetVideo.nz(source);
    }

    /** 对应原 8 参构造 + 默认参数 (i & 64) → isLive=false, (i & 128) → durationMs=0。 */
    public WidgetPlayback(String id, String title, String author, String coverUrl,
                          String source, boolean isPlaying) {
        this(id, title, author, coverUrl, source, isPlaying, false, 0L);
    }
}
