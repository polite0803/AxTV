/*
 * 来源: src/app/com/github/mytv/p004dv/model/SearchVideoWorkData.java + SearchVideoWorkData$$serializer.java
 * 原职责: 作品搜索条目包装
 * 重构说明: 数据类 → record；awemeInfo 默认是空 Aweme（非 null）
 */
package axtv.model;

/**
 * @param awemeInfo 作品（{@code aweme_info}）
 */
public record SearchVideoWorkData(@JsonKey("aweme_info") Aweme awemeInfo) {

    public SearchVideoWorkData {
        awemeInfo = awemeInfo == null ? new Aweme() : awemeInfo;
    }

    public SearchVideoWorkData() {
        this(new Aweme());
    }
}
