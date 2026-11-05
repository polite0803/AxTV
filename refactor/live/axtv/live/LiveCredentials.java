/*
 * 来源: src/app/p000/az0.java
 * 原职责: 弹幕握手参数：webRid / roomId / userId / cookie
 * 重构说明: 类名改为语义化的 LiveCredentials；字段名对应 getter
 *           a() -> webRid, b() -> roomId, c() -> userId, d() -> cookieHeader
 */
package axtv.live;

import java.util.Objects;

/**
 * 弹幕连接所需的握手参数。
 *
 * <p>{@code webRid} 是网页房间号（{@code live.douyin.com/<webRid>} 里的那一段），
 * {@code roomId} 是 IM 长连接要的数值房间 id，两者不一定相同。
 */
public final class LiveCredentials {

    private final String webRid;
    private final String roomId;
    private final String userId;
    private final String cookieHeader;

    public LiveCredentials(String webRid, String roomId, String userId, String cookieHeader) {
        this.webRid = nullToEmpty(webRid);
        this.roomId = nullToEmpty(roomId);
        this.userId = nullToEmpty(userId);
        this.cookieHeader = nullToEmpty(cookieHeader);
    }

    public String webRid() {
        return webRid;
    }

    public String roomId() {
        return roomId;
    }

    public String userId() {
        return userId;
    }

    /** 已拼好的 Cookie 请求头（{@code k=v; k=v}）。 */
    public String cookieHeader() {
        return cookieHeader;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof LiveCredentials other)) {
            return false;
        }
        return webRid.equals(other.webRid)
                && roomId.equals(other.roomId)
                && userId.equals(other.userId)
                && cookieHeader.equals(other.cookieHeader);
    }

    @Override
    public int hashCode() {
        return Objects.hash(webRid, roomId, userId, cookieHeader);
    }

    @Override
    public String toString() {
        return "LiveCredentials(webRid=" + webRid + ", roomId=" + roomId
                + ", userId=" + userId + ", cookieKeys=" + LiveLogFormat.list(cookieKeys()) + ")";
    }

    /** 日志用：只输出 Cookie 键名（原 {@code m2579i} 的行为）。 */
    public java.util.List<String> cookieKeys() {
        java.util.List<String> keys = new java.util.ArrayList<>();
        for (String part : cookieHeader.split(";")) {
            int index = part.indexOf('=');
            if (index > 0) {
                String key = part.substring(0, index).trim();
                if (!key.isEmpty()) {
                    keys.add(key);
                }
            }
        }
        return keys;
    }
}
