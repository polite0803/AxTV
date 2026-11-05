/*
 * 来源: src/app/p000/n11.java（各处 throw new Exception / IllegalStateException / rc4 的聚合）
 * 原职责: API 层没有统一异常类型，按场景抛裸 Exception、IllegalStateException 或 rc4(状态码)
 * 重构说明: 统一成 ApiException，用 Kind 保留原始错误语义与文案，便于上层按类别处理
 *           （登录失效 / 限流 / 凭据缺失 / HTTP 错误 / 空响应 / 头部错误码）
 */
package axtv.api;

public final class ApiException extends RuntimeException {

    /** 原实现中的错误分类。 */
    public enum Kind {
        /** 网络/IO 层失败（原 OkHttp 抛 IOException 后由协程包成 g54）。 */
        NETWORK,
        /** HTTP 非 2xx：原文案 "HTTP {code}: {body 前 300 字符}"。 */
        HTTP_STATUS,
        /** 2xx 但响应体为空（签名失效 / Cookie 过期 / 搜索被拦截）。 */
        EMPTY_BODY,
        /** 响应头 bd-tt-error-code 非 0。 */
        HEADER_ERROR,
        /** 业务状态码非 0。 */
        API_STATUS,
        /** 需要登录（关注流 8）。 */
        LOGIN_REQUIRED,
        /** 频率限制（关注流 2154）。 */
        RATE_LIMITED,
        /** 互动接口缺少本地交互安全凭据。 */
        MISSING_CREDENTIAL,
        /** JSON 反序列化失败。 */
        DECODE
    }

    private final Kind kind;
    private final int httpStatus;
    private final int apiStatus;

    private ApiException(Kind kind, String message, int httpStatus, int apiStatus, Throwable cause) {
        super(message, cause);
        this.kind = kind;
        this.httpStatus = httpStatus;
        this.apiStatus = apiStatus;
    }

    public Kind kind() {
        return kind;
    }

    public int httpStatus() {
        return httpStatus;
    }

    public int apiStatus() {
        return apiStatus;
    }

    public static ApiException network(String path, Throwable cause) {
        return new ApiException(Kind.NETWORK, "请求失败: " + path + " (" + cause.getClass().getSimpleName()
                + (cause.getMessage() == null ? "" : ": " + cause.getMessage()) + ")", 0, 0, cause);
    }

    public static ApiException http(int code, String body) {
        return new ApiException(Kind.HTTP_STATUS, "HTTP " + code + ": " + Texts.truncate(body, 300), code, 0, null);
    }

    /** 普通接口空响应文案。 */
    public static ApiException emptyBody(int code) {
        return new ApiException(Kind.EMPTY_BODY,
                "服务器返回空响应 (HTTP " + code + ")，可能是签名参数(a_bogus)无效或Cookie已过期", code, 0, null);
    }

    /** 搜索接口空响应文案（pz0 专用）。 */
    public static ApiException emptySearchBody(int code) {
        return new ApiException(Kind.EMPTY_BODY,
                "服务器返回空响应 (HTTP " + code + ")，搜索请求可能被拦截", code, 0, null);
    }

    /** 移动端推荐流空响应文案（lz0 专用）。 */
    public static ApiException emptyMobileFeedBody(int code) {
        return new ApiException(Kind.EMPTY_BODY, "移动推荐返回空响应 (HTTP " + code + ")", code, 0, null);
    }

    /** m8000k：bd-tt-error-code 非空且非 "0" 时抛错。 */
    public static ApiException headerError(String errorCode, String body) {
        return new ApiException(Kind.HEADER_ERROR,
                "bd-tt-error-code=" + errorCode + ": " + Texts.truncate(body, 200), 200, 0, null);
    }

    /** 通用业务状态码错误。 */
    public static ApiException apiStatus(int status, String message) {
        return new ApiException(Kind.API_STATUS, message, 200, status, null);
    }

    /** 关注流 8：未登录。 */
    public static ApiException loginRequired() {
        return new ApiException(Kind.LOGIN_REQUIRED, "需要登录才能查看关注内容，请在设置中配置完整登录 Cookie", 200, 8, null);
    }

    /** 关注流 2154：限流。 */
    public static ApiException rateLimited() {
        return new ApiException(Kind.RATE_LIMITED, "请求频率过高，请稍后再试", 200, 2154, null);
    }

    /** m8065v：互动前校验本地是否已配置交互安全凭据。 */
    public static ApiException missingInteractionCredential() {
        return new ApiException(Kind.MISSING_CREDENTIAL, "请先在设置中补充互动安全凭据", 0, 0, null);
    }

    public static ApiException decode(String path, Throwable cause) {
        return new ApiException(Kind.DECODE, "响应解析失败: " + path, 0, 0, cause);
    }
}
