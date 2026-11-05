/*
 * 来源: src/app/p000/n11.java m8003r0（解析单条 Set-Cookie）
 * 原职责: 从 "k=v; Path=/; ..." 中取出 cookie 名值对
 * 重构说明: 逐字还原原始算法——按 ";" 切分后只取【最后一段】，去空白，
 *           以第一个 '=' 拆成 name/value（'=' 位置 <= 0 时视为无效返回 null）。
 *           因此 "Set-Cookie: a=1; Path=/; HttpOnly" 只会产出 HttpOnly 这一段的解析结果（无效），
 *           这正是原实现的行为，不做"修正"。
 */
package axtv.api;

import java.util.AbstractMap;
import java.util.Map;

final class SetCookie {

    private SetCookie() {
    }

    static Map.Entry<String, String> parse(String setCookieLine) {
        if (setCookieLine == null) {
            return null;
        }
        String[] parts = setCookieLine.split(";");
        if (parts.length == 0) {
            return null;
        }
        String last = parts[parts.length - 1].trim();
        if (last.isEmpty()) {
            return null;
        }
        int eq = last.indexOf('=');
        if (eq <= 0) {
            return null;
        }
        return new AbstractMap.SimpleImmutableEntry<>(last.substring(0, eq).trim(), last.substring(eq + 1).trim());
    }
}
