/*
 * 来源: src/app/com/github/mytv/p004dv/model/CollectListResponse.java
 * 原职责: 收藏夹列表接口返回体
 * 重构说明: 数据类 → record；$$serializer 未导出，key 按惯例推定
 */
package axtv.model;

import java.util.List;

/**
 * @param collectsList 收藏夹列表（推定 {@code collects_list}）；<b>可空</b>
 * @param hasMore      是否还有下一页（推定 {@code has_more}，0/1）
 * @param statusCode   状态码（推定 {@code status_code}）
 */
public record CollectListResponse(
        @JsonKey("collects_list") List<CollectItem> collectsList,
        @JsonKey("has_more") int hasMore,
        @JsonKey("status_code") int statusCode) {

    public CollectListResponse {
        collectsList = collectsList == null ? null : List.copyOf(collectsList);
    }

    public CollectListResponse() {
        this(null, 0, 0);
    }

    public boolean hasMorePages() {
        return hasMore != 0;
    }
}
