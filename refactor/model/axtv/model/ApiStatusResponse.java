/*
 * 来源: src/app/com/github/mytv/p004dv/model/ApiStatusResponse.java + ApiStatusResponse$$serializer.java
 * 原职责: 只要状态码的接口返回体（写操作/心跳类接口）
 * 重构说明: 数据类 → record
 */
package axtv.model;

/**
 * @param statusCode 状态码，0 = 成功（{@code status_code}）
 * @param statusMsg  状态文案（{@code status_msg}）
 */
public record ApiStatusResponse(
        @JsonKey("status_code") int statusCode,
        @JsonKey("status_msg") String statusMsg) {

    public ApiStatusResponse {
        statusMsg = statusMsg == null ? "" : statusMsg;
    }

    public ApiStatusResponse() {
        this(0, "");
    }

    public boolean isOk() {
        return statusCode == 0;
    }
}
