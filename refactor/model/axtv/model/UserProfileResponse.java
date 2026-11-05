/*
 * 来源: src/app/com/github/mytv/p004dv/model/UserProfileResponse.java + UserProfileResponse$$serializer.java
 * 原职责: 用户主页资料接口返回体
 * 重构说明: 数据类 → record
 */
package axtv.model;

/**
 * @param user       用户资料（{@code user}）；<b>可空</b>
 * @param statusCode 状态码（{@code status_code}）
 */
public record UserProfileResponse(
        @JsonKey("user") Author user,
        @JsonKey("status_code") int statusCode) {

    public UserProfileResponse() {
        this(null, 0);
    }
}
