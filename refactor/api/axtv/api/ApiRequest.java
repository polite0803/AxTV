/*
 * 来源: src/app/p000/n11.java 中 m8002r / m8004t / m8005w / m8015G0 / m8049l 的调用约定
 * 原职责: 各接口方法把"路径 + 参数表 + 请求体 + Referer + 是否签名 + 若干开关"传给通用请求函数
 * 重构说明: 显式建模成请求描述对象，替代原来的 6~9 个位置参数；语义一一对应
 */
package axtv.api;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ApiRequest {

    public enum SignMode {
        /** 不计算 a_bogus（对应 m8002r 的 z=false 分支）。 */
        NONE,
        /** 计算 a_bogus 并追加到 query（对应 m7989C）。 */
        A_BOGUS
    }

    private final String method;
    private final String path;
    private final LinkedHashMap<String, String> query = new LinkedHashMap<>();
    private final LinkedHashMap<String, String> form = new LinkedHashMap<>();
    private final LinkedHashMap<String, String> extraHeaders = new LinkedHashMap<>();

    private String baseUrl = DouyinEndpoints.WEB_HOST;
    private String referer;
    private SignMode signMode = SignMode.NONE;
    private boolean commonParams;
    private boolean msTokenOnly;
    private boolean uifidHeader;
    private boolean ticketGuardHeaders;
    private boolean userCenterSignChain;
    private boolean secsdkSignatureOnly;
    private boolean mobile;
    private boolean rawBytes;
    private boolean checkHeaderErrorCode;
    private boolean logTicketGuardVerdicts;
    private boolean searchDiagnostics;

    private ApiRequest(String method, String path) {
        this.method = method;
        this.path = path;
    }

    public static ApiRequest get(String path) {
        return new ApiRequest("GET", path);
    }

    public static ApiRequest postForm(String path) {
        return new ApiRequest("POST", path);
    }

    public ApiRequest base(String baseUrl) {
        this.baseUrl = baseUrl;
        return this;
    }

    public ApiRequest param(String key, String value) {
        query.put(key, value);
        return this;
    }

    public ApiRequest params(Map<String, String> values) {
        if (values != null) {
            query.putAll(values);
        }
        return this;
    }

    public ApiRequest body(String key, String value) {
        form.put(key, value);
        return this;
    }

    public ApiRequest body(Map<String, String> values) {
        if (values != null) {
            form.putAll(values);
        }
        return this;
    }

    public ApiRequest referer(String referer) {
        this.referer = referer;
        return this;
    }

    /** 追加请求头；buildHeaders 在默认头之后合并，可用于 secsdk CSRF 等网页侧固定头。 */
    public ApiRequest header(String key, String value) {
        extraHeaders.put(key, value);
        return this;
    }

    public ApiRequest sign() {
        this.signMode = SignMode.A_BOGUS;
        return this;
    }

    /** 对应 m8011D0：注入 msToken / 屏幕 / CPU / 内存 / verifyFp / fp / uifid / webid。 */
    public ApiRequest commonParams() {
        this.commonParams = true;
        return this;
    }

    /** 对应 m8013E0：只注入 msToken（搜索热榜等）。 */
    public ApiRequest msTokenOnly() {
        this.msTokenOnly = true;
        return this;
    }


    /** 对应 kz0 的 f12695V：额外加小写 "uifid" 请求头。 */
    public ApiRequest uifidHeader() {
        this.uifidHeader = true;
        return this;
    }

    /** 对应 m8065v / nz0 的 z 参数：附加 bd-ticket-guard-* 请求头。 */
    public ApiRequest ticketGuardHeaders() {
        this.ticketGuardHeaders = true;
        return this;
    }

    /** 对应 m8015G0：a_bogus + uifid + timestamp + x-secsdk-web-signature（用户中心签名链）。 */
    public ApiRequest userCenterSignChain() {
        this.userCenterSignChain = true;
        return this;
    }

    /**
     * 对应 n11.m8036b0（tab/feed）的就地签名：只加 timestamp + x-secsdk-web-signature，
     * 不算 a_bogus；uifid 只从 cookie["UIFID"] 取（不回退 UIFID_TEMP，缺失则不带签名）。
     */
    public ApiRequest secsdkSignatureOnly() {
        this.secsdkSignatureOnly = true;
        return this;
    }

    /** 走 aweme.snssdk.com 的移动端形态（UA / 精简头 / protobuf 响应）。 */
    public ApiRequest mobile() {
        this.mobile = true;
        return this;
    }

    /** 响应按字节返回，不做 UTF-8 文本空体检查。 */
    public ApiRequest rawBytes() {
        this.rawBytes = true;
        return this;
    }

    /** 对应 m8000k：检查 bd-tt-error-code 响应头。 */
    public ApiRequest checkHeaderErrorCode() {
        this.checkHeaderErrorCode = true;
        return this;
    }

    /** 对应 m8056o0：打印 ticket-guard 校验结果日志。 */
    public ApiRequest logTicketGuardVerdicts() {
        this.logTicketGuardVerdicts = true;
        return this;
    }

    /** 对应 m8063u：打印搜索请求 cookie/msToken 诊断日志。 */
    public ApiRequest searchDiagnostics() {
        this.searchDiagnostics = true;
        return this;
    }

    public String method() {
        return method;
    }

    public String path() {
        return path;
    }

    public String baseUrl() {
        return baseUrl;
    }

    public Map<String, String> query() {
        return query;
    }

    public Map<String, String> form() {
        return form;
    }

    public String referer() {
        return referer;
    }

    public Map<String, String> extraHeaders() {
        return extraHeaders;
    }

    public SignMode signMode() {
        return signMode;
    }

    public boolean addCommonParams() {
        return commonParams;
    }

    public boolean addMsTokenOnly() {
        return msTokenOnly;
    }

    public boolean needsUifidHeader() {
        return uifidHeader;
    }

    public boolean needsTicketGuardHeaders() {
        return ticketGuardHeaders;
    }

    public boolean needsUserCenterSignChain() {
        return userCenterSignChain;
    }

    public boolean needsSecsdkSignatureOnly() {
        return secsdkSignatureOnly;
    }

    public boolean isMobile() {
        return mobile;
    }

    public boolean isRawBytes() {
        return rawBytes;
    }

    public boolean checksHeaderErrorCode() {
        return checkHeaderErrorCode;
    }

    public boolean logsTicketGuardVerdicts() {
        return logTicketGuardVerdicts;
    }

    public boolean writesSearchDiagnostics() {
        return searchDiagnostics;
    }
}
