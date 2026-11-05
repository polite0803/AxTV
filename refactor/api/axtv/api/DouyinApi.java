/*
 * 来源: src/app/p000/n11.java —— 原类是一个 5659 行的 God class，把"接口清单 + 请求编排 + 响应解析"
 *       全塞在一起。其中 m8002r / m8004t / m8005w / m8015G0 / m8049l 这套通用请求函数
 *       就是本类的原型；m8011D0（公共参数）、m8013E0（只注入 msToken）、m7993X（Referer）、
 *       m8000k（bd-tt-error-code 检查）、m8056o0（ticket-guard 结论日志）、m8063u（搜索诊断）
 *       逐个还原成私有方法。
 * 原职责: 把 ApiRequest 变成一次真实 HTTP 调用：补公共参数 → 签名 → 组头 → 发送 → 落 Cookie → 查错
 * 重构说明: 只保留"编排"，端点业务交给 FeedApi/VideoApi/... 各门面；JSON 解析交给 JsonCodec。
 */
package axtv.api;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 抖音 Web API 的请求编排器。
 *
 * <p>一次调用的固定顺序（与原 n11 的通用请求函数一致）：
 * <ol>
 *   <li>公共参数注入（msToken / 屏幕 / CPU / 内存 / verifyFp / uifid / webid）</li>
 *   <li>用户中心签名链（a_bogus + uifid + timestamp + x-secsdk-web-signature）</li>
 *   <li>或仅 a_bogus</li>
 *   <li>组请求头（默认头 + Referer + Cookie + 可选 uifid 头 + 可选 bd-ticket-guard-*）</li>
 *   <li>发送、同步 Set-Cookie、按状态码与 body 形态抛 {@link ApiException}</li>
 * </ol>
 *
 * <p>本类线程安全：不持有可变状态，所有状态都在参数与 CookieStore 里。
 */
public final class DouyinApi {

    /** m8011D0 里的兜底值，逐字保留。 */
    private static final String DEFAULT_SCREEN_WIDTH = "2560";
    private static final String DEFAULT_SCREEN_HEIGHT = "1440";
    private static final String DEFAULT_CPU_CORE = "24";
    private static final String DEFAULT_MEMORY = "8";

    private final HttpEngine http;
    private final SignProvider signer;
    private final CookieStore cookies;
    private final DeviceProfile device;
    private final JsonCodec json;

    public DouyinApi(HttpEngine http, SignProvider signer, CookieStore cookies,
                     DeviceProfile device, JsonCodec json) {
        this.http = http;
        this.signer = signer;
        this.cookies = cookies;
        this.device = device;
        this.json = json;
    }

    public CookieStore cookies() {
        return cookies;
    }

    public DeviceProfile device() {
        return device;
    }

    // ------------------------------------------------------------------ 主流程

    /**
     * 执行一次请求，返回响应体文本。
     *
     * @throws ApiException 网络错误 / HTTP 错误 / 空体 / 接口 status_code != 0
     */
    public String call(ApiRequest request) throws ApiException {
        LinkedHashMap<String, String> query = new LinkedHashMap<>(request.query());
        Map<String, String> form = request.form();

        // ① 公共参数
        if (request.addCommonParams()) {
            injectCommonParams(query);
        } else if (request.addMsTokenOnly()) {
            query.put("msToken", currentMsToken());
        }

        String body = form.isEmpty() ? "" : Texts.joinQuery(form);
        String userAgent = userAgentFor(request);
        String path = request.path();

        // ② 签名
        if (request.needsUserCenterSignChain()) {
            applyUserCenterSignChain(query, body, path, userAgent);
        } else if (request.signMode() == ApiRequest.SignMode.A_BOGUS) {
            String abogus = signer.signABogus(path, Texts.joinQuery(query), body,
                    isLiveHost(request.baseUrl()) ? userAgent : null);
            if (!Texts.isBlank(abogus)) {
                query.put("a_bogus", abogus);
            }
        }

        // ③ 请求头
        Map<String, String> headers = buildHeaders(request, path, query, body, userAgent);

        // ④ 发送
        String url = request.baseUrl() + path + (query.isEmpty() ? "" : "?" + Texts.joinQuery(query));
        if (request.writesSearchDiagnostics()) {
            logSearchDiagnostics(path, query);
        }
        ApiResponse response;
        try {
            response = http.execute(request.method(), url, headers,
                    body.isEmpty() ? null : body.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    body.isEmpty() ? null : "application/x-www-form-urlencoded; charset=UTF-8",
                    true);
        } catch (IOException e) {
            throw ApiException.network(path, e);
        }

        // ⑤ Set-Cookie 回写
        List<String> setCookies = response.headers("set-cookie");
        if (setCookies != null && !setCookies.isEmpty()) {
            LinkedHashMap<String, String> updates = new LinkedHashMap<>();
            for (String line : setCookies) {
                Map.Entry<String, String> entry = SetCookie.parse(line);
                if (entry != null) {
                    updates.put(entry.getKey(), entry.getValue());
                }
            }
            cookies.putAll(updates);
        }

        // ⑥ 查错
        if (response.statusCode() >= 400) {
            throw ApiException.http(response.statusCode(), response.bodyText());
        }
        if (request.checksHeaderErrorCode()) {
            String errorCode = response.header("bd-tt-error-code");
            if (!Texts.isBlank(errorCode) && !"0".equals(errorCode)) {
                throw ApiException.headerError(errorCode, response.bodyText());
            }
        }
        if (request.isRawBytes()) {
            return new String(response.bodyBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        if (response.isEmptyBody()) {
            throw ApiException.emptyBody(response.statusCode());
        }
        return response.bodyText();
    }

    /** 解码成指定类型（JSON 库由调用方注入，核心层不绑定）。 */
    public <T> T callFor(ApiRequest request, Class<T> type) throws ApiException {
        String body = call(request);
        try {
            return json.decode(body, type);
        } catch (Exception e) {
            throw ApiException.decode(request.path(), e);
        }
    }

    // ------------------------------------------------------------------ 公共参数（m8011D0）

    /**
     * 逐字还原 m8011D0：
     * msToken ← cookie["msToken"]（空则现取）；
     * screen_width ← cookie["dy_swidth"]，兜底 "2560"；
     * screen_height ← cookie["dy_sheight"]，兜底 "1440"；
     * cpu_core_num ← cookie["device_web_cpu_core"]，兜底 "24"；
     * device_memory ← cookie["device_web_memory_size"]，兜底 "8"；
     * verifyFp / fp ← cookie["s_v_web_id"]（两个键同值，且仅在存在时写入）；
     * uifid ← cookie["UIFID"]；
     * webid ← user_unique_id（**仅当全为数字**时写入）。
     */
    void injectCommonParams(Map<String, String> query) {
        Map<String, String> jar = cookies.cookies();
        query.put("msToken", currentMsToken());
        query.put("screen_width", valueOr(jar.get("dy_swidth"), DEFAULT_SCREEN_WIDTH));
        query.put("screen_height", valueOr(jar.get("dy_sheight"), DEFAULT_SCREEN_HEIGHT));
        query.put("cpu_core_num", valueOr(jar.get("device_web_cpu_core"), DEFAULT_CPU_CORE));
        query.put("device_memory", valueOr(jar.get("device_web_memory_size"), DEFAULT_MEMORY));
        String verifyFp = jar.get("s_v_web_id");
        if (verifyFp != null) {
            query.put("verifyFp", verifyFp);
            query.put("fp", verifyFp);
        }
        String uifid = jar.get("UIFID");
        if (uifid != null) {
            query.put("uifid", uifid);
        }
        String uniqueId = cookies.userUniqueId();
        if (!Texts.isBlank(uniqueId) && Texts.allDigits(uniqueId)) {
            query.put("webid", uniqueId);
        }
    }

    private String currentMsToken() {
        String msToken = cookies.cookies().get("msToken");
        if (Texts.isBlank(msToken)) {
            msToken = Texts.randomMsToken();
        }
        return msToken;
    }

    // ------------------------------------------------------------------ 用户中心签名链（m8015G0）

    /**
     * a_bogus + uifid + timestamp(秒) + x-secsdk-web-signature。
     * UIFID / UIFID_TEMP 都缺失时，只写 timestamp 并打 WARN
     * （原文案 "… 将不带 x-secsdk-web-signature"）。
     */
    void applyUserCenterSignChain(Map<String, String> query, String body, String path, String userAgent) {
        String abogus = signer.signABogus(path, Texts.joinQuery(query), body,
                isLiveHostForPath(path) ? userAgent : null);
        if (!Texts.isBlank(abogus)) {
            query.put("a_bogus", abogus);
        }
        Map<String, String> jar = cookies.cookies();
        String uifid = firstNonBlank(query.get("uifid"), jar.get("UIFID"), jar.get("UIFID_TEMP"));
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000L);
        if (Texts.isBlank(uifid)) {
            ApiLog.w(ApiLog.TAG_API, "withUserCenterSignChain: UIFID/UIFID_TEMP 均缺失，"
                    + path + " 将不带 x-secsdk-web-signature");
            query.put("timestamp", timestamp);
            return;
        }
        query.put("uifid", uifid);
        query.put("timestamp", timestamp);
        query.put("x-secsdk-web-signature", signer.xSecSdkWebSignature(query, uifid, timestamp));
    }

    // ------------------------------------------------------------------ 请求头

    private Map<String, String> buildHeaders(ApiRequest request, String path,
                                             Map<String, String> query, String body, String userAgent) {
        LinkedHashMap<String, String> headers = new LinkedHashMap<>(DouyinEndpoints.DEFAULT_HEADERS);
        headers.put("user-agent", userAgent);
        String referer = request.referer() != null
                ? request.referer()
                : DouyinEndpoints.refererFor(path, query);
        headers.put("referer", referer);
        String cookie = cookies.cookieHeader();
        if (!Texts.isBlank(cookie)) {
            headers.put("cookie", cookie);
        }
        if (request.needsUifidHeader()) {
            String uifid = cookies.get("UIFID");
            if (uifid != null) {
                headers.put("uifid", uifid);
            }
        }
        if (request.needsTicketGuardHeaders()) {
            headers.putAll(signer.ticketGuardHeaders(path, cookies.ticketGuard()));
            if (request.logsTicketGuardVerdicts()) {
                ApiLog.d(ApiLog.TAG_API, "ticket-guard headers attached for " + path);
            }
        }
        return headers;
    }

    private String userAgentFor(ApiRequest request) {
        if (request.isMobile()) {
            return device.mobileUserAgent();
        }
        return isLiveHost(request.baseUrl())
                ? DouyinEndpoints.LIVE_USER_AGENT
                : DouyinEndpoints.WEB_USER_AGENT;
    }

    private static boolean isLiveHost(String baseUrl) {
        return baseUrl != null && baseUrl.contains("live.douyin.com");
    }

    private static boolean isLiveHostForPath(String path) {
        return path != null && path.startsWith("/webcast/");
    }

    // ------------------------------------------------------------------ 诊断（m8063u）

    private static void logSearchDiagnostics(String path, Map<String, String> query) {
        ApiLog.d(ApiLog.TAG_API, "search request " + path
                + " msToken=" + Texts.truncate(String.valueOf(query.get("msToken")), 16)
                + " hasCookie=" + query.containsKey("uifid")
                + " a_bogus=" + Texts.truncate(String.valueOf(query.get("a_bogus")), 16));
    }

    private static String valueOr(String value, String fallback) {
        return Texts.isBlank(value) ? fallback : value;
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (!Texts.isBlank(v)) {
                return v;
            }
        }
        return "";
    }
}
