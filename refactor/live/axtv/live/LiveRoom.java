/*
 * 来源: src/app/com/github/mytv/dv/model/LiveRoom.java 中直播播放用到的字段
 *       （roomId / enterRoomId / idStr / webRid / owner.uid / title / userCount / streamUrl / status）
 * 原职责: 房间模型（kotlinx.serialization data class，13 个字段）
 * 重构说明: 只保留直播链路需要的子集；完整模型由 model 模块还原，这里给出显式适配注释
 */
package axtv.live;

/**
 * 直播房间（精简版）。
 *
 * <p>完整字段见原 {@code LiveRoom}（cameraInfos / cover / episodeExtra / roomViewStats / streamUrl …）。
 * 本模块只依赖下列几项，Android 侧可用一个适配器把完整模型投影过来。
 */
public final class LiveRoom {

    private final String roomId;
    private final String enterRoomId;
    private final String idStr;
    private final String webRid;
    private final String ownerUid;
    private final String title;
    private final String userCount;
    private final int status;
    private final LiveStreamInfo streamInfo;

    public LiveRoom(String roomId,
                    String enterRoomId,
                    String idStr,
                    String webRid,
                    String ownerUid,
                    String title,
                    String userCount,
                    int status,
                    LiveStreamInfo streamInfo) {
        this.roomId = nullToEmpty(roomId);
        this.enterRoomId = nullToEmpty(enterRoomId);
        this.idStr = nullToEmpty(idStr);
        this.webRid = nullToEmpty(webRid);
        this.ownerUid = nullToEmpty(ownerUid);
        this.title = nullToEmpty(title);
        this.userCount = nullToEmpty(userCount);
        this.status = status;
        this.streamInfo = streamInfo;
    }

    public String roomId() {
        return roomId;
    }

    public String enterRoomId() {
        return enterRoomId;
    }

    public String idStr() {
        return idStr;
    }

    public String webRid() {
        return webRid;
    }

    /** 主播 uid（原 {@code owner.uid}），用于弹幕 user_unique_id 的回退。 */
    public String ownerUid() {
        return ownerUid;
    }

    public String title() {
        return title;
    }

    public String userCount() {
        return userCount;
    }

    /** 原 {@code status}：2 表示直播中，4 表示已结束。 */
    public int status() {
        return status;
    }

    public LiveStreamInfo streamInfo() {
        return streamInfo;
    }

    /**
     * 原 {@code LiveRoom.getResolvedRoomId()}：{@code roomId ?: idStr ?: enterRoomId ?: webRid}。
     * 连接弹幕时优先用它，保证与服务端一致的房间标识。
     */
    public String resolvedRoomId() {
        if (!LiveLogFormat.isBlank(roomId)) {
            return roomId;
        }
        if (!LiveLogFormat.isBlank(idStr)) {
            return idStr;
        }
        if (!LiveLogFormat.isBlank(enterRoomId)) {
            return enterRoomId;
        }
        return webRid;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    @Override
    public String toString() {
        return "LiveRoom(webRid=" + webRid + ", roomId=" + roomId + ", enterRoomId=" + enterRoomId
                + ", ownerUid=" + ownerUid + ", status=" + status + ")";
    }
}
