/*
 * 来源: src/app/com/github/mytv/p004dv/model/LiveRoomEnterResponse.java
 * 原职责: 进房接口返回体
 * 重构说明: 数据类 → record；$$serializer 未导出，key 按 snake_case 惯例推定
 */
package axtv.model;

/**
 * @param data       进房数据（推定 {@code data}）；<b>可空</b>
 * @param statusCode 状态码（推定 {@code status_code}）
 */
public record LiveRoomEnterResponse(@JsonKey("data") LiveRoomEnterData data,
                                    @JsonKey("status_code") int statusCode) {

    public LiveRoomEnterResponse() {
        this(null, 0);
    }

    /** 便捷取第一个房间。 */
    public LiveRoom firstRoom() {
        return data == null ? null : data.firstRoom();
    }
}
