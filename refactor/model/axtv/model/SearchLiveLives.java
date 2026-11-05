/*
 * 来源: src/app/com/github/mytv/p004dv/model/SearchLiveLives.java + SearchLiveLives$$serializer.java
 * 原职责: 直播搜索结果主体（作品 id + 主播 + 原始 JSON）
 * 重构说明: 数据类 → record；rawdata 是接口原样的 JSON 字符串（不是解析后的对象）
 */
package axtv.model;

/**
 * @param awemeId 直播的 aweme id（{@code aweme_id}）
 * @param author  主播（{@code author}）
 * @param rawdata 原始 JSON 字符串（{@code rawdata}），直播间地址等信息要从这里取
 */
public record SearchLiveLives(
        @JsonKey("aweme_id") String awemeId,
        @JsonKey("author") Author author,
        @JsonKey("rawdata") String rawdata) {

    public SearchLiveLives {
        awemeId = awemeId == null ? "" : awemeId;
        author = author == null ? new Author() : author;
        rawdata = rawdata == null ? "" : rawdata;
    }

    public SearchLiveLives() {
        this("", new Author(), "");
    }
}
