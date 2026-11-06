/*
 * 来源: src/app/p000/n11.java m8007A（fetchTtwid）+ zg0 case 1（fetchUifidTemp）
 *       + m8049l（数据请求前的身份前置检查）/ m8055o（UIFID 缺失时的补取，60s 节流）
 * 原职责: 未登录 / 缺设备身份时补齐设备级 cookie（ttwid、UIFID_TEMP），
 *        让公开接口（tab/feed 等）拿到"匿名访问身份"，否则服务端返回空响应或 403
 * 重构说明: 独立成类，保留原始端点、请求体、请求头（UA 是 Chrome/147，与默认头不同）；
 *           m8049l 的"每个进程只尝试一次"与 m8055o 的 60s 节流由调用方 DouyinApi 负责，
 *           本类只做"缺什么补什么"，失败不抛异常，只留 WARN。
 */
package axtv.api;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 匿名访问身份引导（设备级 ttwid + UIFID_TEMP）。
 *
 * <p>抖音 Web 公开接口（首页推荐 / 频道 / 搜索…）不要求登录，但要求请求带设备级
 * {@code ttwid} 与 {@code UIFID_TEMP}（用户中心签名链要用它算
 * {@code x-secsdk-web-signature}）。全新安装、Cookie 为空时 jar 里没有它们，
 * 服务端会返回空响应或 403，表现就是"未登录时所有页面都没有数据"。本类负责在首次请求前补齐。
 *
 * <p>逐字还原：{@code n11.m8007A()}（POST {@code ttwid/union/register/}）与
 * {@code zg0} case 1（HEAD {@code https://www.douyin.com/} 取 {@code UIFID_TEMP}）。
 */
public final class AnonymousIdentity {

    /** 设备级匿名身份 cookie。 */
    public static final String COOKIE_TTWID = "ttwid";
    public static final String COOKIE_UIFID_TEMP = "UIFID_TEMP";

    static final String REGISTER_URL = "https://ttwid.bytedance.com/ttwid/union/register/";
    static final String HOME_URL = "https://www.douyin.com/";

    /** n11.m8071z：GET 该地址取首页 HTML，从响应体提取 user_unique_id / msToken / odin_tt。 */
    static final String HOME_RECOMMEND_URL = "https://www.douyin.com/?recommend=1";

    /** 原实现固定用 Chrome/147 UA（与接口默认 UA 不同）。 */
    static final String REGISTER_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36";

    /** n11.m8007A 的请求体，逐字保留。 */
    static final String REGISTER_BODY =
            "{\"region\":\"cn\",\"aid\":1768,\"needFid\":false,\"service\":\"www.ixigua.com\","
                    + "\"migrate_info\":{\"ticket\":\"\",\"source\":\"node\"},"
                    + "\"cbUrlProtocol\":\"https\",\"union\":true}";

    private static final Pattern BODY_TTWID = Pattern.compile("\"ttwid\"\\s*:\\s*\"([^\"]+)\"");

    /** 首页 HTML/JSON 里的设备号（n11.m8069y 第一段正则，匹配 \"user_unique_id\":\"123...\"）。 */
    private static final Pattern BODY_UNIQUE_ID_ESCAPED =
            Pattern.compile("\\\\\"user_unique_id\\\\\":\\\\\"(\\d+)\\\\\"");

    /** 同上（n11.m8069y 第二段正则，匹配 "user_unique_id":"123..."）。 */
    private static final Pattern BODY_UNIQUE_ID_PLAIN =
            Pattern.compile("\"user_unique_id\"\\s*:\\s*\"(\\d+)\"");

    private AnonymousIdentity() {
    }

    /** cookie jar 是否已带可用的 ttwid（非空白）。 */
    public static boolean hasIdentity(CookieStore cookies) {
        return cookies != null && !Texts.isBlank(cookies.get(COOKIE_TTWID));
    }

    /** 用户中心签名链所需的 uifid 是否就绪（UIFID 或 UIFID_TEMP 任一非空）。 */
    public static boolean hasUifid(CookieStore cookies) {
        if (cookies == null) {
            return false;
        }
        Map<String, String> jar = cookies.cookies();
        return !Texts.isBlank(jar.get("UIFID")) || !Texts.isBlank(jar.get(COOKIE_UIFID_TEMP));
    }

    /**
     * 补齐设备级匿名身份：ttwid 与 UIFID_TEMP 缺哪补哪，已有的不重复请求。
     * 同时按 n11.m8071z/m8069y 的路径从首页响应体补 {@code user_unique_id}
     * （缺它时 tab/feed 不带 webid/globalwid，服务端只能按匿名设备出通用热门池，
     * 表现就是「登录与不登录推荐没区别」）。
     *
     * <p>阻塞式网络调用，需在非主线程执行。失败返回 false（不抛异常），
     * 调用方可照常继续——只是匿名接口可能依旧拿不到数据。
     */
    public static boolean ensure(HttpEngine engine, CookieStore cookies) {
        if (cookies == null || engine == null) {
            return false;
        }
        boolean ttwid = hasIdentity(cookies) || !Texts.isBlank(acquireTtwid(engine, cookies));
        boolean uifid = hasUifid(cookies) || !Texts.isBlank(acquireUifidTemp(engine, cookies));
        // user_unique_id 独立于上面两项：哪怕 ttwid/UIFID_TEMP 已齐也值得补一次。
        // acquireHomeIdentity 内部会判断「已有全数字 id 就不发请求」。
        acquireHomeIdentity(engine, cookies);
        return ttwid && uifid;
    }

    private static String acquireTtwid(HttpEngine engine, CookieStore cookies) {
        String ttwid = fetchTtwid(engine);
        if (!Texts.isBlank(ttwid)) {
            cookies.putAll(Collections.singletonMap(COOKIE_TTWID, ttwid));
            ApiLog.d(ApiLog.TAG_LIVE_WS, "[ttwid] anonymous identity acquired: " + Texts.truncate(ttwid, 24));
        }
        return ttwid;
    }

    /**
     * zg0 case 1：HEAD https://www.douyin.com/ 取 UIFID_TEMP（该请求同时会下发 ttwid）。
     * 原实现用"不跟随重定向"的客户端（n11.f14567e）。
     */
    private static String acquireUifidTemp(HttpEngine engine, CookieStore cookies) {
        String uifid = fetchUifidTemp(engine, cookies);
        if (!Texts.isBlank(uifid)) {
            cookies.putAll(Collections.singletonMap(COOKIE_UIFID_TEMP, uifid));
            ApiLog.d(ApiLog.TAG_LIVE_WS, "[uifid] UIFID_TEMP acquired (len=" + uifid.length() + ")");
        }
        return uifid;
    }

    /** n11.m8007A：申领 ttwid，先读 Set-Cookie，再从响应体兜底；失败返回 null。 */
    static String fetchTtwid(HttpEngine engine) {
        ApiLog.d(ApiLog.TAG_LIVE_WS, "[ttwid] requesting " + REGISTER_URL);
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("User-Agent", REGISTER_USER_AGENT);
        headers.put("Accept", "application/json, text/plain, */*");
        headers.put("Content-Type", "application/json");
        headers.put("Origin", "https://www.douyin.com");
        headers.put("Referer", "https://www.douyin.com/");
        try {
            ApiResponse response = engine.execute("POST", REGISTER_URL, headers,
                    REGISTER_BODY.getBytes(StandardCharsets.UTF_8),
                    "application/json; charset=utf-8", true);
            for (String raw : response.headers("Set-Cookie")) {
                Map.Entry<String, String> pair = SetCookie.parse(raw);
                if (pair != null && COOKIE_TTWID.equalsIgnoreCase(pair.getKey())
                        && !Texts.isBlank(pair.getValue())) {
                    return pair.getValue();
                }
            }
            Matcher matcher = BODY_TTWID.matcher(response.bodyText());
            if (matcher.find()) {
                return matcher.group(1);
            }
            ApiLog.w(ApiLog.TAG_LIVE_WS,
                    "[ttwid] register response carried no ttwid (http=" + response.statusCode() + ")");
        } catch (Exception e) {
            ApiLog.w(ApiLog.TAG_LIVE_WS, "[ttwid] fetch failed: " + e.getClass().getSimpleName()
                    + ": " + e.getMessage());
        }
        return null;
    }

    /**
     * zg0 case 1：HEAD https://www.douyin.com/ 取 UIFID_TEMP（该请求同时会下发 ttwid）。
     * 原实现用"不跟随重定向"的客户端（n11.f14567e）。
     */
    static String fetchUifidTemp(HttpEngine engine, CookieStore cookies) {
        ApiLog.d(ApiLog.TAG_LIVE_WS, "[uifid] requesting " + HOME_URL);
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("User-Agent", REGISTER_USER_AGENT);
        headers.put("Accept", "*/*");
        try {
            ApiResponse response = engine.execute("HEAD", HOME_URL, headers, null, null, false);
            String uifid = null;
            String ttwid = null;
            for (String raw : response.headers("Set-Cookie")) {
                Map.Entry<String, String> pair = SetCookie.parse(raw);
                if (pair == null || Texts.isBlank(pair.getValue())) {
                    continue;
                }
                if (COOKIE_UIFID_TEMP.equalsIgnoreCase(pair.getKey()) && uifid == null) {
                    uifid = pair.getValue();
                } else if (COOKIE_TTWID.equalsIgnoreCase(pair.getKey()) && ttwid == null) {
                    ttwid = pair.getValue();
                }
            }
            if (ttwid != null && cookies != null && Texts.isBlank(cookies.get(COOKIE_TTWID))) {
                cookies.putAll(Collections.singletonMap(COOKIE_TTWID, ttwid));
            }
            if (uifid == null) {
                ApiLog.w(ApiLog.TAG_LIVE_WS,
                        "[uifid] homepage HEAD did not return UIFID_TEMP (http=" + response.statusCode() + ")");
            }
            return uifid;
        } catch (Exception e) {
            ApiLog.w(ApiLog.TAG_LIVE_WS, "[uifid] fetch failed: " + e.getClass().getSimpleName()
                    + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * n11.m8071z + m8069y：GET https://www.douyin.com/?recommend=1，从响应体提取
     * {@code user_unique_id} 回写 CookieStore（顺带收 Set-Cookie 里的 msToken/odin_tt 等）。
     *
     * <p><b>必须带上现有 Cookie</b>（原实现用 linkedHashMap 拼 Cookie 头）：否则这次匿名首页
     * 请求会拿到一套全新的设备身份，回写时把已登录用户的 ttwid/UIFID 覆盖成匿名值，
     * 服务端随后把登录态当成无效，表现为「设置里已登录、搜索却始终为空」。
     * 同样的原因，Set-Cookie 只补缺失键，不覆盖已有值。
     *
     * <p>原版在缺 msToken / odin_tt / user_unique_id 时触发，最多跟 5 次重定向；
     * 这里简化为：仅当 {@code userUniqueId()} 为空时发一次 GET（跟随重定向），
     * body 里两段正则各试一次——与 m8069y 的顺序一致（先转义形态再普通形态）。
     * 失败静默返回，不影响 ttwid/UIFID_TEMP 主链路。
     */
    static void acquireHomeIdentity(HttpEngine engine, CookieStore cookies) {
        String existing = cookies.userUniqueId();
        if (!Texts.isBlank(existing) && Texts.allDigits(existing)) {
            return;
        }
        ApiLog.d(ApiLog.TAG_LIVE_WS, "[uue] requesting " + HOME_RECOMMEND_URL);
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("User-Agent", REGISTER_USER_AGENT);
        headers.put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        headers.put("Accept-Language", "zh-CN,zh;q=0.9");
        String cookieHeader = cookies.cookieHeader();
        if (!Texts.isBlank(cookieHeader)) {
            headers.put("Cookie", cookieHeader);
        }
        try {
            // 原版跟重定向且每次回填 Set-Cookie（m8071z 循环），这里交给 followRedirects=true。
            ApiResponse response = engine.execute("GET", HOME_RECOMMEND_URL, headers, null, null, true);
            Map<String, String> current = cookies.cookies();
            Map<String, String> missing = new LinkedHashMap<>();
            for (String raw : response.headers("Set-Cookie")) {
                Map.Entry<String, String> pair = SetCookie.parse(raw);
                if (pair == null || Texts.isBlank(pair.getValue())) {
                    continue;
                }
                // 只补缺失键：已有 ttwid / UIFID / sessionid 等绝不覆盖。
                if (Texts.isBlank(current.get(pair.getKey()))) {
                    missing.put(pair.getKey(), pair.getValue());
                }
            }
            cookies.putAll(missing);
            String body = response.bodyText();
            if (body == null || body.isEmpty()) {
                ApiLog.w(ApiLog.TAG_LIVE_WS,
                        "[uue] homepage body empty (http=" + response.statusCode() + ")");
                return;
            }
            String id = firstMatch(BODY_UNIQUE_ID_ESCAPED, body);
            if (id == null) {
                id = firstMatch(BODY_UNIQUE_ID_PLAIN, body);
            }
            if (id != null) {
                cookies.setUserUniqueId(id);
                ApiLog.d(ApiLog.TAG_LIVE_WS, "[uue] user_unique_id acquired: " + id);
            } else {
                ApiLog.w(ApiLog.TAG_LIVE_WS,
                        "[uue] homepage body carried no user_unique_id (http=" + response.statusCode()
                                + ", len=" + body.length() + ")");
            }
        } catch (Exception e) {
            ApiLog.w(ApiLog.TAG_LIVE_WS, "[uue] fetch failed: " + e.getClass().getSimpleName()
                    + ": " + e.getMessage());
        }
    }

    private static String firstMatch(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }
}

