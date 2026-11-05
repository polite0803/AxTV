/*
 * 来源: src/app/com/github/mytv/p004dv/model/CommentListResponse.java
 * 原职责: 评论列表接口返回体
 * 重构说明: 数据类 → record。原类的 $$serializer 未被 jadx 导出，JSON key 按同模块的
 *           snake_case 惯例推定，见 NOTES.md「存疑」。
 */
package axtv.model;

import java.util.List;

/**
 * @param comments   评论列表（推定 {@code comments}）
 * @param hasMore    是否还有下一页（推定 {@code has_more}，0/1，接口可能返回字符串）
 * @param cursor     下一页游标（推定 {@code cursor}）
 * @param total      评论总数（推定 {@code total}）
 * @param statusCode 接口状态码，0 = 成功（推定 {@code status_code}）
 */
public record CommentListResponse(
        @JsonKey("comments") List<Comment> comments,
        @JsonKey("has_more") int hasMore,
        @JsonKey("cursor") long cursor,
        @JsonKey("total") long total,
        @JsonKey("status_code") int statusCode) {

    public CommentListResponse {
        comments = comments == null ? null : List.copyOf(comments);
    }

    public CommentListResponse() {
        this(null, 0, 0L, 0L, 0);
    }

    /** 原代码 {@code hasMore != 0} 的语义包装。 */
    public boolean hasMorePages() {
        return hasMore != 0;
    }
}
