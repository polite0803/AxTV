/*
 * 来源: src/app/com/github/mytv/p004dv/model/MixDetailResponse.java
 * 原职责: 合集详情接口返回体
 * 重构说明: 数据类 → record；mixInfo 主键推定 {@code mix_info}，备用 {@code series_info}
 *           （来自 @c72 JsonNames 注解）
 */
package axtv.model;

/**
 * @param mixInfo    合集详情（{@code mix_info}，备用 key {@code series_info}）；<b>可空</b>
 * @param statusCode 状态码（推定 {@code status_code}）
 */
public record MixDetailResponse(
        @JsonKey(value = "mix_info", alternates = {"series_info"}) MixInfo mixInfo,
        @JsonKey("status_code") int statusCode) {

    public MixDetailResponse() {
        this(null, 0);
    }
}
