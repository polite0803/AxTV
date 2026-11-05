/*
 * 来源: src/app/com/github/mytv/p004dv/model/LiveDanmakuMessage.java
 * 原职责: 直播间实时弹幕/礼物/进场等消息的统一模型（LiveWS 推送解析后的产物）
 * 重构说明: 数据类 → record；type 区分消息种类，默认 "chat"
 */
package axtv.model;

/**
 * @param messageId  消息 id
 * @param userName   发送者昵称
 * @param content    消息内容（chat 为文本）
 * @param timestamp  本地收到的时间（毫秒）；默认取当前时间
 * @param userId     发送者 id
 * @param level      用户等级
 * @param isVip      是否会员
 * @param badgeName  粉丝团/勋章名
 * @param badgeLevel 勋章等级
 * @param type       消息类型，默认 {@code "chat"}
 * @param onlineCount 当时在线人数（部分消息类型才带）
 */
public record LiveDanmakuMessage(
        long messageId,
        String userName,
        String content,
        long timestamp,
        String userId,
        int level,
        boolean isVip,
        String badgeName,
        int badgeLevel,
        String type,
        int onlineCount) {

    /** 消息类型：聊天弹幕。 */
    public static final String TYPE_CHAT = "chat";

    public LiveDanmakuMessage {
        userName = userName == null ? "" : userName;
        content = content == null ? "" : content;
        userId = userId == null ? "" : userId;
        badgeName = badgeName == null ? "" : badgeName;
        type = type == null ? TYPE_CHAT : type;
    }

    /** 反编译默认值：0 / "" / 当前时间 / false / "chat"。 */
    public LiveDanmakuMessage() {
        this(0L, "", "", System.currentTimeMillis(), "", 0, false, "", 0, TYPE_CHAT, 0);
    }
}
