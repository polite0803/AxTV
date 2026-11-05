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

    /** 原实现固定用 Chrome/147 UA（与接口默认 UA 不同）。 */
    static final String REGISTER_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36";

    /** n11.m8007A 的请求体，逐字保留。 */
    static final String REGISTER_BODY =
            "{\"region\":\"cn\",\"aid\":1768,\"needFid\":false,\"service\":\"www.ixigua.com\","
                    + "\"migrate_info\":{\"ticket\":\"\",\"source\":\"node\"},"
                    + "\"cbUrlProtocol\":\"https\",\"union\":true}";

    private static final Pattern BODY_TTWID = Pattern.compile("\"ttwid\"\\s*:\\s*\"([^\"]+)\"");

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
     * zg0 case 1：HEAD 首页。原实现只取 UIFID_TEMP；首页同时下发的 ttwid 也一并收下
     * （原版 ttwid 已由 m8007A 单独申领，这里属于额外收益，不改变主链路）。
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
}

