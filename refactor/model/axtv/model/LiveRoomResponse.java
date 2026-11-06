/*
 * 来源: src/app/com/github/mytv/p004dv/model/LiveRoomResponse.java
 * 原职责: 直播详情接口返回体
 * 重构说明: 数据类 → record；$$serializer 未导出，key 按 snake_case 惯例推定
 */
package axtv.model;

/**
 * <p>实测（2026-11-06，/webcast/room/info_by_scene/）响应里的 {@code data} <b>直接就是房间对象</b>：
 * {@code {"data":{...id_str/stream_url...},"status_code":0}}。
 * 之前按 {@code data.room} 解析（推定），导致永远拿不到房间，直播间全部提示「未取到直播间信息」。
 *
 * @param room       房间数据（{@code data}）；<b>可空</b>
 * @param statusCode 状态码（{@code status_code}）
 */
public record LiveRoomResponse(@JsonKey("data") LiveRoom room, @JsonKey("status_code") int statusCode) {

    public LiveRoomResponse() {
        this(null, 0);
    }
}
