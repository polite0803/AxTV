/*
 * 来源: src/app/com/github/mytv/p004dv/model/LiveRoom.java + LiveRoom$$serializer.java
 * 原职责: 一个直播间的完整信息（标题、封面、主播、拉流地址、在线人数文案、多机位）
 * 重构说明: 数据类 → record；userCount 是**字符串**（接口下发 user_count_str），不是数字
 */
package axtv.model;

import java.util.List;

/**
 * @param roomId        房间数字 id（{@code room_id}）
 * @param idStr         房间 id 字符串形式（{@code id_str}）
 * @param enterRoomId   进房用的 id（{@code enter_room_id}）
 * @param webRid        web 版房间号，拼 live.douyin.com/{web_rid} 用（{@code web_rid}）
 * @param title         直播标题（{@code title}）
 * @param cover         封面（{@code cover}）
 * @param userCount     在线人数**文案**，如「1.2万」（{@code user_count_str}）；默认 "0"
 * @param roomViewStats 更精确的观看数展示结构（{@code room_view_stats}）；<b>可空</b>
 * @param owner         主播（{@code owner}）
 * @param streamUrl     拉流地址（{@code stream_url}）；<b>可空</b>，未开播时没有
 * @param status        直播状态（{@code status}），2 = 直播中
 * @param cameraInfos   多机位列表（{@code camera_infos}）；<b>可空</b>
 * @param episodeExtra  剧集附加信息（{@code episode_extra}）；<b>可空</b>
 */
public record LiveRoom(
        @JsonKey("room_id") String roomId,
        @JsonKey("id_str") String idStr,
        @JsonKey("enter_room_id") String enterRoomId,
        @JsonKey("web_rid") String webRid,
        @JsonKey("title") String title,
        @JsonKey("cover") Cover cover,
        @JsonKey("user_count_str") String userCount,
        @JsonKey("room_view_stats") RoomViewStats roomViewStats,
        @JsonKey("owner") Author owner,
        @JsonKey("stream_url") StreamUrl streamUrl,
        @JsonKey("status") int status,
        @JsonKey("camera_infos") List<CameraInfo> cameraInfos,
        @JsonKey("episode_extra") EpisodeExtra episodeExtra) {

    public LiveRoom {
        roomId = roomId == null ? "" : roomId;
        idStr = idStr == null ? "" : idStr;
        enterRoomId = enterRoomId == null ? "" : enterRoomId;
        webRid = webRid == null ? "" : webRid;
        title = title == null ? "" : title;
        cover = cover == null ? new Cover() : cover;
        userCount = userCount == null ? "0" : userCount;
        owner = owner == null ? new Author() : owner;
        cameraInfos = cameraInfos == null ? null : List.copyOf(cameraInfos);
    }

    /** 反编译默认值：字符串 ""、userCount "0"、空 Cover/Author，其余 null。 */
    public LiveRoom() {
        this("", "", "", "", "", new Cover(), "0", null, new Author(), null, 0, null, null);
    }

    /** 界面/URL 用的房间号：优先 webRid，其次 roomId。 */
    public String effectiveWebRid() {
        return webRid.isEmpty() ? roomId : webRid;
    }
}
