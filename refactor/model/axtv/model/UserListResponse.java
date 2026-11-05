/*
 * 来源: src/app/com/github/mytv/p004dv/model/UserListResponse.java
 * 原职责: 关注/粉丝列表接口返回体
 * 重构说明: 数据类 → record；hasMore 走 FlexibleBoolean，
 *           minTime/maxTime 走 FlexibleLong（接口把时间戳返回成字符串）
 */
package axtv.model;

import java.util.List;

/**
 * @param followings 关注列表（推定 {@code followings}）；<b>可空</b>
 * @param followers  粉丝列表（推定 {@code followers}）；<b>可空</b>
 * @param hasMore    是否有更多（推定 {@code has_more}，接口可能返回字符串）
 * @param total      总数（推定 {@code total}）
 * @param minTime    本页最早时间（推定 {@code min_time}，可能返回字符串）
 * @param maxTime    本页最晚时间（推定 {@code max_time}，可能返回字符串）
 * @param offset     偏移量（推定 {@code offset}）
 * @param statusCode 状态码（推定 {@code status_code}）
 */
public record UserListResponse(
        @JsonKey("followings") List<Author> followings,
        @JsonKey("followers") List<Author> followers,
        @JsonKey("has_more") boolean hasMore,
        @JsonKey("total") long total,
        @JsonKey("min_time") long minTime,
        @JsonKey("max_time") long maxTime,
        @JsonKey("offset") int offset,
        @JsonKey("status_code") int statusCode) {

    public UserListResponse {
        followings = followings == null ? null : List.copyOf(followings);
        followers = followers == null ? null : List.copyOf(followers);
    }

    public UserListResponse() {
        this(null, null, false, 0L, 0L, 0L, 0, 0);
    }
}
