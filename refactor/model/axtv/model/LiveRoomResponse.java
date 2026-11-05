/*
 * 来源: src/app/com/github/mytv/p004dv/model/LiveRoomResponse.java
 * 原职责: 直播详情接口返回体
 * 重构说明: 数据类 → record；$$serializer 未导出，key 按 snake_case 惯例推定
 */
package axtv.model;

/**
 * @param data       房间数据（推定 {@code data}）；<b>可空</b>
 * @param statusCode 状态码（推定 {@code status_code}）
 */
public record LiveRoomResponse(@JsonKey("data") LiveRoomData data, @JsonKey("status_code") int statusCode) {

    public LiveRoomResponse() {
        this(null, 0);
    }
}
