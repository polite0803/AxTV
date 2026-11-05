/*
 * 来源: src/app/com/github/mytv/p004dv/model/PlayProgress.java + PlayProgress$$serializer.java
 * 原职责: 服务端同步的「上次看到哪」记录
 * 重构说明: 数据类 → record；watchTimes 是可空的 Long（接口可能不给），保留 null 表示「未知」
 */
package axtv.model;

/**
 * @param lastModifiedTime 记录最后修改时间（毫秒，{@code last_modified_time}）
 * @param playProgress     已播放到的位置（毫秒，{@code play_progress}）
 * @param watchTimes       观看次数（{@code watch_times}）；<b>可空</b>，接口没下发时为 null，
 *                         与「看了 0 次」语义不同，不要折叠成 0
 */
public record PlayProgress(
        @JsonKey("last_modified_time") long lastModifiedTime,
        @JsonKey("play_progress") long playProgress,
        @JsonKey("watch_times") Long watchTimes) {

    public PlayProgress() {
        this(0L, 0L, null);
    }
}
