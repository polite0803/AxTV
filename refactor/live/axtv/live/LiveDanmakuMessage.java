/*
 * 来源: src/app/com/github/mytv/dv/model/LiveDanmakuMessage.java（Kotlin data class，11 字段）
 * 原职责: 直播弹幕/在线人数消息的 UI 模型；cq2 构造，wx5 转 DanmakuItem，ym2 渲染聊天列表
 * 重构说明: 去掉 $stable / componentN / copy$default / 掩码构造器，用显式常量代替默认值掩码；
 *           字段顺序与 toString 文案保持与 data class 一致
 */
package axtv.live;

import java.util.Objects;

/**
 * 直播弹幕消息。
 *
 * <p>两种来源（原 {@code cq2.m2584d}）：
 * <ul>
 *   <li>{@code type="chat"}：{@code WebcastChatMessage}，带用户与内容；</li>
 *   <li>{@code type="online"}：{@code WebcastRoomUserSeqMessage}，只有 {@link #onlineCount()}。</li>
 * </ul>
 */
public final class LiveDanmakuMessage {

    public static final String TYPE_CHAT = "chat";
    public static final String TYPE_ONLINE = "online";

    private final long messageId;
    private final String userName;
    private final String content;
    private final long timestamp;
    private final String userId;
    private final int level;
    private final boolean vip;
    private final String badgeName;
    private final int badgeLevel;
    private final String type;
    private final int onlineCount;

    public LiveDanmakuMessage(long messageId,
                              String userName,
                              String content,
                              long timestamp,
                              String userId,
                              int level,
                              boolean vip,
                              String badgeName,
                              int badgeLevel,
                              String type,
                              int onlineCount) {
        this.messageId = messageId;
        this.userName = nullToEmpty(userName);
        this.content = nullToEmpty(content);
        this.timestamp = timestamp;
        this.userId = nullToEmpty(userId);
        this.level = level;
        this.vip = vip;
        this.badgeName = nullToEmpty(badgeName);
        this.badgeLevel = badgeLevel;
        this.type = nullToEmpty(type);
        this.onlineCount = onlineCount;
    }

    /** 原掩码 {@code 1472}：只给账号/灯牌/在线数走默认值，其余取实参。 */
    public static LiveDanmakuMessage chat(long messageId, String userName, String content,
                                          long timestamp, String userId, int level) {
        return new LiveDanmakuMessage(messageId, userName, content, timestamp, userId, level,
                false, "", 0, TYPE_CHAT, 0);
    }

    /** 原掩码 {@code 502}：只保留 messageId / timestamp / type / onlineCount。 */
    public static LiveDanmakuMessage online(long messageId, long timestamp, int onlineCount) {
        return new LiveDanmakuMessage(messageId, "", "", timestamp, "", 0,
                false, "", 0, TYPE_ONLINE, onlineCount);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    public long messageId() {
        return messageId;
    }

    public String userName() {
        return userName;
    }

    public String content() {
        return content;
    }

    public long timestamp() {
        return timestamp;
    }

    public String userId() {
        return userId;
    }

    public int level() {
        return level;
    }

    public boolean vip() {
        return vip;
    }

    public String badgeName() {
        return badgeName;
    }

    public int badgeLevel() {
        return badgeLevel;
    }

    public String type() {
        return type;
    }

    public int onlineCount() {
        return onlineCount;
    }

    public boolean isOnline() {
        return TYPE_ONLINE.equals(type);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof LiveDanmakuMessage other)) {
            return false;
        }
        return messageId == other.messageId
                && timestamp == other.timestamp
                && level == other.level
                && vip == other.vip
                && badgeLevel == other.badgeLevel
                && onlineCount == other.onlineCount
                && userName.equals(other.userName)
                && content.equals(other.content)
                && userId.equals(other.userId)
                && badgeName.equals(other.badgeName)
                && type.equals(other.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(messageId, userName, content, timestamp, userId, level, vip,
                badgeName, badgeLevel, type, onlineCount);
    }

    @Override
    public String toString() {
        return "LiveDanmakuMessage(messageId=" + messageId
                + ", userName=" + userName
                + ", content=" + content
                + ", timestamp=" + timestamp
                + ", userId=" + userId
                + ", level=" + level
                + ", isVip=" + vip
                + ", badgeName=" + badgeName
                + ", badgeLevel=" + badgeLevel
                + ", type=" + type
                + ", onlineCount=" + onlineCount
                + ")";
    }
}
