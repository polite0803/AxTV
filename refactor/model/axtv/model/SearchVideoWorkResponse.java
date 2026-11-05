/*
 * 来源: src/app/com/github/mytv/p004dv/model/SearchVideoWorkResponse.java
 * 原职责: 话题/作品聚合页的搜索返回体
 * 重构说明: 数据类 → record；cursor 为 long、hasMore 为 int。$$serializer 未导出，key 按惯例推定
 */
package axtv.model;

import java.util.List;

/**
 * @param cursor       游标（推定 {@code cursor}）
 * @param data         作品条目（推定 {@code data}）；<b>可空</b>
 * @param hasMore      是否有下一页（推定 {@code has_more}，0/1）
 * @param logPb        埋点（推定 {@code log_pb}）；<b>可空</b>
 * @param searchNilInfo 无结果提示（推定 {@code search_nil_info}）；<b>可空</b>
 * @param statusCode   状态码（推定 {@code status_code}）
 * @param statusMsg    状态文案（推定 {@code status_msg}）
 */
public record SearchVideoWorkResponse(
        @JsonKey("cursor") long cursor,
        @JsonKey("data") List<SearchVideoWorkData> data,
        @JsonKey("has_more") int hasMore,
        @JsonKey("log_pb") LogPb logPb,
        @JsonKey("search_nil_info") SearchNilInfo searchNilInfo,
        @JsonKey("status_code") int statusCode,
        @JsonKey("status_msg") String statusMsg) {

    public SearchVideoWorkResponse {
        data = data == null ? null : List.copyOf(data);
        statusMsg = statusMsg == null ? "" : statusMsg;
    }

    public SearchVideoWorkResponse() {
        this(0L, null, 0, null, null, 0, "");
    }

    public boolean hasMorePages() {
        return hasMore != 0;
    }
}
