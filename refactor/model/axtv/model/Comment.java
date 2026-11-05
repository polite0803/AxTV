/*
 * 来源: src/app/com/github/mytv/p004dv/model/Comment.java + Comment$$serializer.java
 * 原职责: 一条评论（含一层内嵌的回复列表）
 * 重构说明: 数据类 → record；cid 的 descriptor 用的是 media3 常量 CmcdConfiguration.KEY_CONTENT_ID
 *           （= "cid"），反编译里没切成字面量，这里补回。
 */
package axtv.model;

import java.util.List;

/**
 * @param cid              评论 id（{@code cid}）
 * @param text             评论内容（{@code text}）
 * @param user             评论者（{@code user}）
 * @param diggCount        点赞数（{@code digg_count}）
 * @param replyCommentTotal 回复总数（{@code reply_comment_total}）
 * @param createTime       发布时间，秒级时间戳（{@code create_time}）
 * @param awemeId          所属作品 id（{@code aweme_id}）
 * @param ipLabel          IP 属地文案（{@code ip_label}）
 * @param replyComments    内嵌的回复（{@code reply_comment}）；<b>可空</b>，接口未返回或未展开时为 null
 * @param isUserDigg       当前用户是否已点赞（{@code is_user_digg}，0/1，接口可能返回字符串）
 */
public record Comment(
        @JsonKey("cid") String cid,
        @JsonKey("text") String text,
        @JsonKey("user") Author user,
        @JsonKey("digg_count") long diggCount,
        @JsonKey("reply_comment_total") long replyCommentTotal,
        @JsonKey("create_time") long createTime,
        @JsonKey("aweme_id") String awemeId,
        @JsonKey("ip_label") String ipLabel,
        @JsonKey("reply_comment") List<Comment> replyComments,
        @JsonKey("is_user_digg") int isUserDigg) {

    public Comment {
        cid = cid == null ? "" : cid;
        text = text == null ? "" : text;
        user = user == null ? new Author() : user;
        awemeId = awemeId == null ? "" : awemeId;
        ipLabel = ipLabel == null ? "" : ipLabel;
        replyComments = replyComments == null ? null : List.copyOf(replyComments);
    }

    public Comment() {
        this("", "", new Author(), 0L, 0L, 0L, "", "", null, 0);
    }

    public boolean isDigged() {
        return isUserDigg == 1;
    }

    /** 有内嵌回复且未展开（reply_comment_total > 实际拿到的条数）时界面显示「展开 N 条回复」。 */
    public boolean hasHiddenReplies() {
        int loaded = replyComments == null ? 0 : replyComments.size();
        return replyCommentTotal > loaded;
    }
}
