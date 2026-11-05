/*
 * 来源: src/app/com/github/mytv/p004dv/model/Statistics.java + Statistics$$serializer.java
 * 原职责: 作品互动数据（点赞/评论/分享/收藏/播放）
 * 重构说明: 数据类 → record；五个字段全部用 FlexibleLongSerializer（接口会把计数返回成字符串）
 */
package axtv.model;

/**
 * 五个计数都是 long，接口可能返回字符串或数字，解码时统一走 {@link FlexibleJson#toLong(Object)}。
 *
 * @param diggCount    点赞数（{@code digg_count}）
 * @param commentCount 评论数（{@code comment_count}）
 * @param shareCount   分享数（{@code share_count}）
 * @param collectCount 收藏数（{@code collect_count}）
 * @param playCount    播放数（{@code play_count}）
 */
public record Statistics(
        @JsonKey("digg_count") long diggCount,
        @JsonKey("comment_count") long commentCount,
        @JsonKey("share_count") long shareCount,
        @JsonKey("collect_count") long collectCount,
        @JsonKey("play_count") long playCount) {

    public Statistics() {
        this(0L, 0L, 0L, 0L, 0L);
    }
}
