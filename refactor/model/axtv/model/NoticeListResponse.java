/*
 * 来源: src/app/com/github/mytv/p004dv/model/NoticeListResponse.java
 * 原职责: 通知列表接口返回体
 * 重构说明: 数据类 → record；$$serializer 未导出，key 按惯例推定
 */
package axtv.model;

import java.util.List;

/**
 * @param noticeList 通知列表（推定 {@code notice_list}）；<b>可空</b>
 * @param hasMore    是否还有更多（推定 {@code has_more}，0/1）
 * @param minTime    本页最早时间（推定 {@code min_time}）
 * @param maxTime    本页最晚时间（推定 {@code max_time}）
 * @param statusCode 状态码（推定 {@code status_code}）
 */
public record NoticeListResponse(
        @JsonKey("notice_list") List<NoticeItem> noticeList,
        @JsonKey("has_more") int hasMore,
        @JsonKey("min_time") long minTime,
        @JsonKey("max_time") long maxTime,
        @JsonKey("status_code") int statusCode) {

    public NoticeListResponse {
        noticeList = noticeList == null ? null : List.copyOf(noticeList);
    }

    public NoticeListResponse() {
        this(null, 0, 0L, 0L, 0);
    }

    public boolean hasMorePages() {
        return hasMore != 0;
    }
}
