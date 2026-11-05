/*
 * 来源: src/app/com/github/mytv/p004dv/model/LiveRoomData.java + LiveRoomData$$serializer.java
 * 原职责: 直播列表里「web_rid + 房间」的配对项
 * 重构说明: 数据类 → record
 */
package axtv.model;

/**
 * @param webRid web 房间号（{@code web_rid}）
 * @param room   房间详情（{@code room}）；<b>可空</b>
 */
public record LiveRoomData(
        @JsonKey("web_rid") String webRid,
        @JsonKey("room") LiveRoom room) {

    public LiveRoomData {
        webRid = webRid == null ? "" : webRid;
    }

    public LiveRoomData() {
        this("", null);
    }
}
