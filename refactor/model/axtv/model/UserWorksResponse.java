/*
 * 来源: src/app/com/github/mytv/p004dv/model/UserWorksResponse.java
 * 原职责: 用户作品列表接口返回体（同时兼容 aweme_list 与 items 两种形态）
 * 重构说明: 数据类 → record；hasMore 走 FlexibleBoolean；cursor 是**可空 Long**
 */
package axtv.model;

import java.util.List;
import java.util.Map;

/**
 * @param awemeList  作品列表（推定 {@code aweme_list}）；<b>可空</b>
 * @param items      作品列表（推定 {@code items}）；<b>可空</b>，与 awemeList 二选一
 * @param hasMore    是否有更多（推定 {@code has_more}，接口可能返回字符串）
 * @param maxCursor  下一页游标（推定 {@code max_cursor}）
 * @param cursor     当前游标（推定 {@code cursor}）；<b>可空</b>
 * @param awemeDate  作品 id → 时间戳 的索引（推定 {@code aweme_date}），用于按时间分组；默认空表
 * @param statusCode 状态码（推定 {@code status_code}）
 */
public record UserWorksResponse(
        @JsonKey("aweme_list") List<Aweme> awemeList,
        @JsonKey("items") List<Aweme> items,
        @JsonKey("has_more") boolean hasMore,
        @JsonKey("max_cursor") long maxCursor,
        @JsonKey("cursor") Long cursor,
        @JsonKey("aweme_date") Map<String, Long> awemeDate,
        @JsonKey("status_code") int statusCode) {

    public UserWorksResponse {
        awemeList = awemeList == null ? null : List.copyOf(awemeList);
        items = items == null ? null : List.copyOf(items);
        awemeDate = awemeDate == null ? Map.of() : Map.copyOf(awemeDate);
    }

    public UserWorksResponse() {
        this(null, null, false, 0L, null, Map.of(), 0);
    }

    /** 归一化取作品：aweme_list 优先，其次 items。 */
    public List<Aweme> works() {
        if (awemeList != null && !awemeList.isEmpty()) {
            return awemeList;
        }
        return items == null ? List.of() : items;
    }
}
