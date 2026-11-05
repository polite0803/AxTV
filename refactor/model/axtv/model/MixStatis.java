/*
 * 来源: src/app/com/github/mytv/p004dv/model/MixStatis.java + MixStatis$$serializer.java
 * 原职责: 合集的进度统计（当前第几集 / 共几集 / 更新到第几集 / 播放量 / 收藏量）
 * 重构说明: 数据类 → record
 */
package axtv.model;

/**
 * @param currentEpisode    当前集（{@code current_episode}）
 * @param updatedToEpisode  已更新到第几集（{@code updated_to_episode}）
 * @param totalEpisode      总集数（{@code total_episode}）
 * @param playVv            播放量（{@code play_vv}）
 * @param collectVv         收藏量（{@code collect_vv}）
 */
public record MixStatis(
        @JsonKey("current_episode") int currentEpisode,
        @JsonKey("updated_to_episode") int updatedToEpisode,
        @JsonKey("total_episode") int totalEpisode,
        @JsonKey("play_vv") long playVv,
        @JsonKey("collect_vv") long collectVv) {

    public MixStatis() {
        this(0, 0, 0, 0L, 0L);
    }
}
