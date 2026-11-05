/*
 * 来源: src/app/com/github/mytv/p004dv/model/SearchSugItem.java + SearchSugItem$$serializer.java
 * 原职责: 一条搜索联想词
 * 重构说明: 数据类 → record
 */
package axtv.model;

/**
 * @param content 联想词文本（{@code content}）
 * @param sugType 联想类型（{@code sug_type}），字符串形态
 */
public record SearchSugItem(
        @JsonKey("content") String content,
        @JsonKey("sug_type") String sugType) {

    public SearchSugItem {
        content = content == null ? "" : content;
        sugType = sugType == null ? "" : sugType;
    }

    public SearchSugItem() {
        this("", "");
    }
}
