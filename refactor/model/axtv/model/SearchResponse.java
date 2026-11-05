/*
 * 来源: src/app/com/github/mytv/p004dv/model/SearchResponse.java
 * 原职责: 视频搜索接口返回体
 * 重构说明: 数据类 → record；hasMore 走 FlexibleBoolean。$$serializer 未导出，key 按 snake_case 推定
 */
package axtv.model;

import java.util.List;

/**
 * @param data         搜索结果条目（推定 {@code data}）；<b>可空</b>
 * @param awemeList    平铺的作品列表（推定 {@code aweme_list}）；<b>可空</b>，与 data 二选一
 * @param hasMore      是否有下一页（推定 {@code has_more}，接口可能返回字符串）
 * @param cursor       游标（推定 {@code cursor}）
 * @param statusCode   状态码（推定 {@code status_code}）
 * @param statusMsg    状态文案（推定 {@code status_msg}）
 * @param searchNilInfo 无结果提示（推定 {@code search_nil_info}）；<b>可空</b>
 * @param logPb        日志埋点（推定 {@code log_pb}）；<b>可空</b>
 */
public record SearchResponse(
        @JsonKey("data") List<SearchItem> data,
        @JsonKey("aweme_list") List<Aweme> awemeList,
        @JsonKey("has_more") boolean hasMore,
        @JsonKey("cursor") long cursor,
        @JsonKey("status_code") int statusCode,
        @JsonKey("status_msg") String statusMsg,
        @JsonKey("search_nil_info") SearchNilInfo searchNilInfo,
        @JsonKey("log_pb") LogPb logPb) {

    public SearchResponse {
        data = data == null ? null : List.copyOf(data);
        awemeList = awemeList == null ? null : List.copyOf(awemeList);
        statusMsg = statusMsg == null ? "" : statusMsg;
    }

    public SearchResponse() {
        this(null, null, false, 0L, 0, "", null, null);
    }

    /** 归一化取作品：优先 aweme_list，否则展开 data[].aweme_info 并丢掉空壳与 null。 */
    public List<Aweme> flatAwemes() {
        if (awemeList != null && !awemeList.isEmpty()) {
            return awemeList;
        }
        if (data == null) {
            return List.of();
        }
        return data.stream().map(SearchItem::awemeInfo).filter(a -> a != null).toList();
    }
}
