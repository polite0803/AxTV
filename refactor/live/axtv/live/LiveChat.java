/*
 * 来源: src/app/p000/rp2.java + wp2.java + vp2.java
 * 原职责: 三类业务消息的解析结果
 *           rp2 = ChatMessage(user, content, eventTime, chatBy)
 *           wp2 = User(id, nickName, level, idStr)
 *           vp2 = RoomUserSeqMessage(totalUser)
 * 重构说明: 合并到一个文件，用内部 record 表达；toString 文案与原 Kotlin data class 一致
 */
package axtv.live;

/**
 * 直播弹幕相关的业务消息模型。
 *
 * <p>字段号全部来自 {@code k04.m6906r / m6909u} 的解码顺序，见 {@link LiveFrameCodec}。
 */
public final class LiveChat {

    private LiveChat() {
    }

    /**
     * {@code User} 消息（原 {@code wp2}）。
     *
     * <p>字段号：1 = id（varint），3 = nick_name，6 = level（varint），1028 = id_str。
     * 1028 在官方 proto 里没有公开定义，此处按反编译结果保留，见 NOTES「存疑」。
     */
    public record User(long id, String nickName, int level, String idStr) {

        public User {
            nickName = nullToEmpty(nickName);
            idStr = nullToEmpty(idStr);
        }

        /** 原 {@code wp2.m11625b()}：字符串型用户 id，为空时调用方回退到 {@link #id()}。 */
        public boolean hasIdStr() {
            return !LiveLogFormat.isBlank(idStr);
        }

        @Override
        public String toString() {
            return "User(id=" + id + ", nickName=" + nickName + ", level=" + level + ", idStr=" + idStr + ")";
        }
    }

    /**
     * {@code WebcastChatMessage}（原 {@code rp2}）。
     *
     * <p>字段号：2 = user，3 = content，15 = event_time，20 = chat_by。
     * {@code chatBy} 为 9 / 10 时表示“包裹/礼物”类评论，受 filterPackageComment 开关控制。
     * {@code eventTime} 为 0 表示服务端未给，调用方回退到本机时间。
     */
    public record Message(User user, String content, long eventTime, int chatBy) {

        public Message {
            user = user == null ? new User(0L, "", 0, "") : user;
            content = nullToEmpty(content);
        }

        @Override
        public String toString() {
            return "ChatMessage(user=" + user + ", content=" + content
                    + ", eventTime=" + eventTime + ", chatBy=" + chatBy + ")";
        }
    }

    /** {@code WebcastRoomUserSeqMessage}（原 {@code vp2}）：字段号 7 = total（varint）。 */
    public record RoomUserSeq(long totalUser) {

        @Override
        public String toString() {
            return "RoomUserSeqMessage(totalUser=" + totalUser + ")";
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
