/*
 * 来源: src/app/com/github/mytv/p004dv/model/SeoLinkResponse.java
 * 原职责: 搜索页 SEO 链接接口返回体
 * 重构说明: 数据类 → record；$$serializer 未导出，key 按惯例推定
 */
package axtv.model;

import java.util.List;

/**
 * @param data       SEO 链接列表（推定 {@code data}）；<b>可空</b>
 * @param statusCode 状态码（推定 {@code status_code}）
 */
public record SeoLinkResponse(@JsonKey("data") List<SeoLink> data, @JsonKey("status_code") int statusCode) {

    public SeoLinkResponse {
        data = data == null ? null : List.copyOf(data);
    }

    public SeoLinkResponse() {
        this(null, 0);
    }
}
