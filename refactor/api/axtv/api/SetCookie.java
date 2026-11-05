/*
 * 来源: src/app/p000/n11.java m8003r0（解析单条 Set-Cookie）
 * 原职责: 从 "k=v; Path=/; ..." 中取出 cookie 名值对
 * 重构说明: 逐字还原原始算法——按 ";" 切分后取【第一段】（o70.m8483U0 = list.get(0)），
 *           去空白，以第一个 '=' 拆成 name/value（'=' 位置 <= 0 时视为无效返回 null）。
 *           即 "Set-Cookie: ttwid=1%7C...; Path=/; HttpOnly" → 取 ttwid=1%7C...。
 *           注意：不能取最后一段，否则只会拿到 SameSite/HttpOnly 这类属性项，导致
 *           服务端下发的 ttwid/msToken/UIFID 永远回写不进 cookie jar。
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
        String first = parts[0].trim();
        if (first.isEmpty()) {
            return null;
        }
        int eq = first.indexOf('=');
        if (eq <= 0) {
            return null;
        }
        return new AbstractMap.SimpleImmutableEntry<>(first.substring(0, eq).trim(), first.substring(eq + 1).trim());
    }
}
