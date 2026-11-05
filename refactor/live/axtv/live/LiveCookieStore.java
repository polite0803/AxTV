/*
 * 来源: src/app/p000/fl4.java 的 cookie 相关成员
 *           m4201h() = cookie map 快照
 *           m4190H(String) = 持久化整串 Cookie
 *           m4211r() = 已持久化的 user_unique_id
 *           m4192J(String) = 持久化 user_unique_id
 * 原职责: 直播 Cookie 与 user_unique_id 的读写（SharedPreferences）
 * 重构说明: 抽象成接口，Android 侧用 SharedPreferences 实现；这里只描述语义，不含存储细节
 */
package axtv.live;

import java.util.Map;

/** 直播握手参数的持久化存储。 */
public interface LiveCookieStore {

    /** 当前 Cookie 快照（键 -> 值，保持插入顺序）。 */
    Map<String, String> cookies();

    /** 整串 Cookie 写回存储；空串表示不写（原实现 {@code if (!isBlank) put}）。 */
    void persistCookie(String cookieHeader);

    /** 已持久化的 user_unique_id，可能为空串。 */
    String userId();

    /** 持久化 user_unique_id。 */
    void persistUserId(String userId);
}
