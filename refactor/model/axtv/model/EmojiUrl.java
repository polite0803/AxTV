/*
 * 来源: src/app/com/github/mytv/p004dv/model/EmojiUrl.java + EmojiUrl$$serializer.java
 * 原职责: 表情图片地址（uri + 多 CDN 地址）
 * 重构说明: 数据类 → record
 */
package axtv.model;

import java.util.List;

/**
 * @param uri     资源 uri（{@code uri}）
 * @param urlList 图片候选地址（{@code url_list}）
 */
public record EmojiUrl(
        @JsonKey("uri") String uri,
        @JsonKey("url_list") List<String> urlList) {

    public EmojiUrl {
        uri = uri == null ? "" : uri;
        urlList = urlList == null ? List.of() : List.copyOf(urlList);
    }

    public EmojiUrl() {
        this("", List.of());
    }
}
