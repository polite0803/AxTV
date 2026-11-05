/*
 * 来源: src/app/com/github/mytv/p004dv/model/NoticeItem.java + NoticeItem$$serializer.java
 * 原职责: 消息中心的单条通知
 * 重构说明: 数据类 → record
 */
package axtv.model;

/**
 * @param noticeId   通知 id（{@code notice_id}）
 * @param noticeType 通知类型（{@code notice_type}，接口可能返回字符串）
 * @param content    通知文案（{@code content}）
 * @param createTime 时间，秒级（{@code create_time}）
 * @param user       关联用户（{@code user}）
 * @param aweme      关联作品（{@code aweme}）；<b>可空</b>
 */
public record NoticeItem(
        @JsonKey("notice_id") String noticeId,
        @JsonKey("notice_type") int noticeType,
        @JsonKey("content") String content,
        @JsonKey("create_time") long createTime,
        @JsonKey("user") Author user,
        @JsonKey("aweme") Aweme aweme) {

    public NoticeItem {
        noticeId = noticeId == null ? "" : noticeId;
        content = content == null ? "" : content;
        user = user == null ? new Author() : user;
    }

    public NoticeItem() {
        this("", 0, "", 0L, new Author(), null);
    }
}
