/*
 * 来源: src/app/com/github/mytv/p004dv/model/WorkDetailResponse.java
 * 原职责: 作品详情接口返回体
 * 重构说明: 数据类 → record；$$serializer 未导出，key 按惯例推定
 */
package axtv.model;

/**
 * @param awemeDetail 作品详情（推定 {@code aweme_detail}）
 * @param statusCode  状态码（推定 {@code status_code}）
 */
public record WorkDetailResponse(@JsonKey("aweme_detail") Aweme awemeDetail,
                                 @JsonKey("status_code") int statusCode) {

    public WorkDetailResponse() {
        this(null, 0);
    }
}
