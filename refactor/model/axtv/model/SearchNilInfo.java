/*
 * 来源: src/app/com/github/mytv/p004dv/model/SearchNilInfo.java + SearchNilInfo$$serializer.java
 * 原职责: 搜索无结果时的提示信息（文案类型 + 是否还能加载更多）
 * 重构说明: 数据类 → record；注意 is_load_more 在原模型里是**字符串**（不是布尔）
 */
package axtv.model;

/**
 * @param searchNilType 无结果类型（{@code search_nil_type}）
 * @param isLoadMore    是否还能加载更多，字符串形态（{@code is_load_more}）
 * @param searchNilItem 无结果条目标识（{@code search_nil_item}）
 * @param textType      文案类型（{@code text_type}）
 */
public record SearchNilInfo(
        @JsonKey("search_nil_type") String searchNilType,
        @JsonKey("is_load_more") String isLoadMore,
        @JsonKey("search_nil_item") String searchNilItem,
        @JsonKey("text_type") String textType) {

    public SearchNilInfo {
        searchNilType = searchNilType == null ? "" : searchNilType;
        isLoadMore = isLoadMore == null ? "" : isLoadMore;
        searchNilItem = searchNilItem == null ? "" : searchNilItem;
        textType = textType == null ? "" : textType;
    }

    public SearchNilInfo() {
        this("", "", "", "");
    }
}
