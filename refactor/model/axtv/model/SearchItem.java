/*
 * 来源: src/app/com/github/mytv/p004dv/model/SearchItem.java + SearchItem$$serializer.java
 * 原职责: 搜索结果里的一个条目（作品 + 条目类型）
 * 重构说明: 数据类 → record
 */
package axtv.model;

/**
 * @param awemeInfo 作品（{@code aweme_info}）；<b>可空</b>，非视频类条目没有
 * @param type      条目类型（{@code type}），0 = 视频
 */
public record SearchItem(
        @JsonKey("aweme_info") Aweme awemeInfo,
        @JsonKey("type") int type) {

    public SearchItem() {
        this(null, 0);
    }
}
