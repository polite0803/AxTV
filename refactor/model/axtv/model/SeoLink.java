/*
 * 来源: src/app/com/github/mytv/p004dv/model/SeoLink.java + SeoLink$$serializer.java
 * 原职责: 一条 SEO 外链（标题 + 地址）
 * 重构说明: 数据类 → record
 */
package axtv.model;

/**
 * @param link  链接地址（{@code link}）
 * @param title 链接标题（{@code title}）
 */
public record SeoLink(
        @JsonKey("link") String link,
        @JsonKey("title") String title) {

    public SeoLink {
        link = link == null ? "" : link;
        title = title == null ? "" : title;
    }

    public SeoLink() {
        this("", "");
    }
}
