/*
 * 来源: src/app/p000/fl4.java（原 CookieStore + 账号/偏好存储，Android SharedPreferences 实现）
 * 原职责: 提供 cookie 串、解析 cookie map、写入 Set-Cookie、生成/读取设备级持久 id
 *       （m4200g / m4201h / m4190H / m4208o / m4211r / m4191I / m4192J / m4205l）
 * 重构说明: 只抽出 API 层用到的部分为接口，去掉 SharedPreferences / Context / 账号列表 UI 逻辑。
 *           Android 侧实现建议：cookie 串存入 SharedPreferences("cookie_string")，
 *           user_unique_id / self_user_id / mobile_feed_device_id 等按原 key 持久化。
 */
package axtv.api;

import java.util.Map;

public interface CookieStore {

    /** 完整 cookie 串，形如 "k=v; k2=v2"。对应 fl4.m4200g()。 */
    String cookieHeader();

    /** 覆盖写入完整 cookie 串。对应 fl4.m4190H()（同时会重算"是否已登录"标记）。 */
    void setCookieHeader(String cookieHeader);

    /** 解析成有序 map。对应 fl4.m4201h()：按 ";" 切分、再按 "=" 切两段、两侧去空白。 */
    Map<String, String> cookies();

    /** 读取单个 cookie，不存在返回 null。 */
    default String get(String name) {
        return cookies().get(name);
    }

    /** 批量写入（Set-Cookie 同步）。对应 m8043h：非空值覆盖同名项后整串回写。 */
    default void putAll(Map<String, String> updates) {
        if (updates == null || updates.isEmpty()) {
            return;
        }
        java.util.LinkedHashMap<String, String> merged = new java.util.LinkedHashMap<>(cookies());
        boolean changed = false;
        for (Map.Entry<String, String> e : updates.entrySet()) {
            if (!Texts.isBlank(e.getValue())) {
                merged.put(e.getKey(), e.getValue());
                changed = true;
            }
        }
        if (changed) {
            setCookieHeader(Texts.joinSemicolon(merged));
        }
    }

    /**
     * 设备级持久 id（16 位数字）。对应 fl4.m4208o(key)：
     * 已存在且为 16 位数字则复用，否则生成 1000000000000000~9999999999999999 区间随机数并持久化。
     * 用于 mobile_feed_device_id / mobile_feed_install_id。
     */
    String persistentDeviceId(String key);

    /** user_unique_id。对应 fl4.m4211r()，无值时返回 ""。 */
    String userUniqueId();

    /** 写入 user_unique_id。对应 fl4.m4192J()。 */
    void setUserUniqueId(String value);

    /** 当前账号的 self_user_id。对应 fl4.m4210q() 中 self_user_id 的取值。 */
    String selfUserId();

    /** 写入 self_user_id。对应 fl4.m4191I()（原实现同时更新账号列表条目）。 */
    void applySelfUserId(String userId);

    /** 当前账号的交互安全凭据。对应 fl4.m4205l()。 */
    TicketGuardCredentials ticketGuard();

    /** bd_ticket_guard_client_data cookie，ticket-guard 公钥回退时使用。 */
    default String ticketGuardClientDataCookie() {
        return get("bd_ticket_guard_client_data");
    }
}
