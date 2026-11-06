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
    private final UserAgentProvider userAgents;
    /** 是否在请求前自动补设备级匿名身份（ttwid + UIFID_TEMP）。App 装配置 true；单测离线 false。 */
    private final boolean anonymousIdentityBootstrap;

    /** 用于 identity 确保的对象锁。 */
    private final Object identityLock = new Object();

    /** 对应 n11.f14568f：身份引导每个进程只尝试一次，失败也不重试，避免每个请求都多打一次。 */
    private boolean identityAttempted;

    /** 默认按 Web 接口分支；设置项切换时由 6 参构造器传入启动快照。 */
    public DouyinApi(HttpEngine http, SignProvider signer, CookieStore cookies,
                     DeviceProfile device, JsonCodec json) {
        this(http, signer, cookies, device, json, ClientIdentityBranch.WEB, false);
    }

    public DouyinApi(HttpEngine http, SignProvider signer, CookieStore cookies,
                     DeviceProfile device, JsonCodec json, ClientIdentityBranch branch) {
        this(http, signer, cookies, device, json, branch, false);
    }

    /**
     * @param anonymousIdentityBootstrap 是否在请求前自动补设备级匿名身份（ttwid + UIFID_TEMP）。
     *                                   App 装配置 true；单测/离线场景置 false 以免发额外请求。
     */
    public DouyinApi(HttpEngine http, SignProvider signer, CookieStore cookies,
                     DeviceProfile device, JsonCodec json, ClientIdentityBranch branch,
                     boolean anonymousIdentityBootstrap) {
        this.http = http;
        this.signer = signer;
        this.cookies = cookies;
        this.device = device;
        this.json = json;
        this.userAgents = new UserAgentProvider(branch, device);
        this.anonymousIdentityBootstrap = anonymousIdentityBootstrap;
    }

    public CookieStore cookies() {
        return cookies;
    }

    /**
     * 确保匿名访问身份（ttwid + UIFID_TEMP）已就绪；已有则立即返回 true。
     * 阻塞式，需在非主线程调用；失败返回 false，不影响调用方继续。
     */
    public boolean ensureAnonymousIdentity() {
        if (AnonymousIdentity.hasIdentity(cookies) && AnonymousIdentity.hasUifid(cookies)) {
            return true;
        }
        synchronized (identityLock) {
            if (AnonymousIdentity.hasIdentity(cookies) && AnonymousIdentity.hasUifid(cookies)) {
                return true;
            }
            // 复刻 n11.f14568f：每个进程只尝试一次，失败也不在每个请求上重试。
            if (identityAttempted) {
                return false;
            }
            identityAttempted = true;
            return AnonymousIdentity.ensure(http, cookies);
        }
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
        ApiResponse response = execute(request);
        if (request.isRawBytes()) {
            return new String(response.bodyBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        if (response.isEmptyBody()) {
            throw ApiException.emptyBody(response.statusCode());
        }
        return response.bodyText();
    }

    /**
     * 执行一次请求并返回原始响应字节，供 protobuf 端点（/aweme/v2/feed/）使用。
     * 文本视图会把非 UTF-8 字节替换成 U+FFFD，protobuf 只能走字节通道。
     *
     * @throws ApiException 网络错误 / HTTP 错误 / 接口 status_code != 0（空体由解码器判定）
     */
    public byte[] callBytes(ApiRequest request) throws ApiException {
        return execute(request).bodyBytes();
    }

    /** 公共参数 → 签名 → 组头 → 发送 → Set-Cookie 回写 → 状态码检查。 */
    private ApiResponse execute(ApiRequest request) throws ApiException {
        if (anonymousIdentityBootstrap) {
            ensureAnonymousIdentity();
        }
        LinkedHashMap<String, String> query = new LinkedHashMap<>(request.query());
        Map<String, String> form = request.form();

        // ① 公共参数
        if (request.addCommonParams()) {
            injectCommonParams(query);
        } else if (request.addMsTokenOnly()) {
            query.put("msToken", currentMsToken());
        }

        String body = form.isEmpty() ? "" : Texts.joinQuery(form);
        String userAgent = userAgents.userAgent();
        String path = request.path();
        // 头与签名必须同值：本次实际分支/UA 打一条可筛选的日志（logcat 标签 System.out）。
        System.out.println("I AxTV_UA: branch=" + userAgents.branch() + " ua=" + userAgent
                + " path=" + path);

        // ② 签名
        if (request.needsUserCenterSignChain()) {
            applyUserCenterSignChain(query, body, path, userAgent);
        } else if (request.needsSecsdkSignatureOnly()) {
            applySecsdkSignature(query);
        } else if (request.signMode() == ApiRequest.SignMode.A_BOGUS) {
            String abogus = signer.signABogus(path, Texts.joinQueryEncodedValues(query),
                    body, userAgent);
            if (!Texts.isBlank(abogus)) {
                query.put("a_bogus", abogus);
            }
        }

        boolean abogusPresent = !Texts.isBlank(query.get("a_bogus"));

        // ③ 请求头
        Map<String, String> headers = buildHeaders(request, path, query);

        // ④ 发送（URL 参数按 yy0 case 0 逐项 URL 编码；未编码会被判 Sign Invalid）
        String url = request.baseUrl() + path
                + (query.isEmpty() ? "" : "?" + Texts.joinQueryEncoded(query));
        if (abogusPresent) {
            // 诊断用：带签名的完整 URL（不含 Cookie）。对比本地/网页请求即可判断 a_bogus 是否一致。
            System.out.println("I AxTV_SIGNED_URL: " + url);
        }
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
            String detail = response.bodyText();
            if (response.statusCode() == 403 && detail != null
                    && detail.contains("ArgusSecurityPlugin")) {
                // 直接把是否带上 a_bogus 写进错误文案：界面截图即可区分「没生成」和「生成了但被拒」。
                detail = detail + (abogusPresent ? " [a_bogus=present]" : " [a_bogus=MISSING]");
                // 再补一行不含敏感值的现场：Cookie 是否完整、UA、a_bogus 长度。
                detail = detail + String.format(
                        java.util.Locale.US,
                        "\n[dbg ua=%s cookieB=%d sid=%s ttwid=%s fp=%s uifid=%s ab=%d sec=%d ts=%s path=%s]",
                        userAgent,
                        cookies.cookieHeader().length(),
                        cookies.get("sessionid") != null,
                        cookies.get("ttwid") != null,
                        cookies.get("s_v_web_id") != null,
                        cookies.get("UIFID") != null,
                        Texts.nvl(query.get("a_bogus")).length(),
                        Texts.nvl(query.get("x-secsdk-web-signature")).length(),
                        query.containsKey("timestamp"),
                        path);
            }
            throw ApiException.http(response.statusCode(), detail);
        }
        if (request.checksHeaderErrorCode()) {
            String errorCode = response.header("bd-tt-error-code");
            if (!Texts.isBlank(errorCode) && !"0".equals(errorCode)) {
                throw ApiException.headerError(errorCode, response.bodyText());
            }
        }
        return response;
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

    /** 用注入的 JSON 编解码器解析响应文本（protobuf 端点的 JSON 回退分支复用）。 */
    public <T> T decodeBody(String body, Class<T> type, String path) throws ApiException {
        try {
            return json.decode(body, type);
        } catch (Exception e) {
            throw ApiException.decode(path, e);
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
        query.put("msToken", currentMsToken());
        injectDeviceParams(query);
        Map<String, String> jar = cookies.cookies();
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

    /**
     * tab/feed 的屏幕、CPU、内存四项（n11.m8036b0 里单独取值，不经 m8011D0）。
     * 全部来自 cookie，缺省值与 m8011D0 共用同一组常量。
     */
    void injectDeviceParams(Map<String, String> query) {
        Map<String, String> jar = cookies.cookies();
        query.put("screen_width", valueOr(jar.get("dy_swidth"), DEFAULT_SCREEN_WIDTH));
        query.put("screen_height", valueOr(jar.get("dy_sheight"), DEFAULT_SCREEN_HEIGHT));
        query.put("cpu_core_num", valueOr(jar.get("device_web_cpu_core"), DEFAULT_CPU_CORE));
        query.put("device_memory", valueOr(jar.get("device_web_memory_size"), DEFAULT_MEMORY));
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
     * a_bogus + uifid + timestamp(秒) + x-secsdk-web-signature（对应 m8015G0）。
     * UIFID / UIFID_TEMP 都缺失时，只写 timestamp 并打 WARN
     * （原文案 "… 将不带 x-secsdk-web-signature"）。
     */
    void applyUserCenterSignChain(Map<String, String> query, String body, String path, String userAgent) {
        String abogus = signer.signABogus(path, Texts.joinQueryEncodedValues(query), body, userAgent);
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

    /**
     * 只补 timestamp + x-secsdk-web-signature（对应 n11.m8036b0:2846-2857 的 tab/feed 分支）。
     *
     * <p>与原版的差异点必须保持一致：这里<b>不算 a_bogus</b>，且 uifid 只认 cookie["UIFID"]；
     * 只有 UIFID_TEMP 时不带签名（原实现仅写 timestamp）。UIFID_TEMP 是匿名临时值，
     * 拿它去签 secsdk 会被服务端拒绝。
     */
    void applySecsdkSignature(Map<String, String> query) {
        String uifid = cookies.cookies().get("UIFID");
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000L);
        query.put("timestamp", timestamp);
        if (Texts.isBlank(uifid)) {
            return;
        }
        query.put("uifid", uifid);
        query.put("x-secsdk-web-signature", signer.xSecSdkWebSignature(query, uifid, timestamp));
    }

    // ------------------------------------------------------------------ 请求头

    private Map<String, String> buildHeaders(ApiRequest request, String path, Map<String, String> query) {
        LinkedHashMap<String, String> headers = new LinkedHashMap<>(userAgents.headers());
        if (userAgents.branch() != ClientIdentityBranch.APP_FEED) {
            String referer = request.referer() != null
                    ? request.referer()
                    : DouyinEndpoints.refererFor(path, query);
            headers.put("referer", referer);
        }
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
