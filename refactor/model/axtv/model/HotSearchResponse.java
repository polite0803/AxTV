/*
 * 来源: src/app/com/github/mytv/p004dv/model/HotSearchResponse.java
 * 原职责: 热搜榜接口返回体
 * 重构说明: 数据类 → record；$$serializer 未导出，key 按惯例推定
 */
package axtv.model;

/**
 * @param data       热搜数据（推定 {@code data}）；<b>可空</b>
 * @param statusCode 状态码（推定 {@code status_code}）
 */
public record HotSearchResponse(@JsonKey("data") HotSearchData data, @JsonKey("status_code") int statusCode) {

    public HotSearchResponse() {
        this(null, 0);
    }
}
