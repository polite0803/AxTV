/*
 * 来源: src/app/p000/ye4.java (2191 行，搜索页状态与逻辑)
 * 原职责: 本类覆盖「错误文案映射 + 空态文案」——
 *         m12087y(264-286): Throwable → 错误文案（rc4 业务码三特殊分支）
 *         670/788/880/1108: 三频道空结果文案（"未找到相关视频/用户/直播"）
 *         717/722/932/937: 用户/直播独立 API 的 catch 文案（"用户搜索异常: " 等）
 *         抛出点: kq5:557（status_code != 0 → rc4(数字码)）、kq5:575-585（结果空且
 *         searchNilInfo.searchNilType 非空 → rc4(nilType 语义码)）、n11:4894/4937/5155/5190（直播/用户）
 * 重构说明: 原实现按 `str + "搜索失败: " + msg` 拼接（m9803C 是裸 concat），
 *         综合视频 Tab 前缀传 ""、用户/直播 Tab 传 "用户"/"直播"，原样保留。
 */
package axtv.ui;

/** 搜索页错误/空态文案规格（纯函数）。 */
public final class SearchUiSpec {

    // ---- rc4 业务码（ye4:273-280 逐字） ----
    /** 参数校验失败码。 */
    public static final String CODE_PARAMS_CHECK = "params_check";
    /** 未登录码。 */
    public static final String CODE_NEED_LOGIN = "web_need_login";
    /** 风控拦截码。 */
    public static final String CODE_VERIFY_CHECK = "verify_check";

    // ---- 文案（逐字） ----
    public static final String MSG_PARAMS_CHECK = "搜索参数校验失败";
    public static final String MSG_NEED_LOGIN = "搜索需要登录态，请更新 Cookie";
    public static final String MSG_VERIFY_CHECK = "搜索被风控校验拦截，请稍后重试";
    /** 空结果文案（670/788/880）。 */
    public static final String EMPTY_VIDEO = "未找到相关视频";
    public static final String EMPTY_USER = "未找到相关用户";
    public static final String EMPTY_LIVE = "未找到相关直播";

    /** 频道前缀（787/879 等：用户/直播 Tab 传频道名，综合视频传 ""）。 */
    public static final String CHANNEL_VIDEO = "";
    public static final String CHANNEL_USER = "用户";
    public static final String CHANNEL_LIVE = "直播";

    private SearchUiSpec() {
    }

    /**
     * 错误文案，逐分支还原 m12087y（ye4:264-286）。
     *
     * @param channelPrefix   频道前缀（"" / "用户" / "直播"）
     * @param businessCode    rc4 业务码；{@code null} 表示非业务异常（走 fallbackMessage）
     * @param fallbackMessage 非业务异常的 {@code getMessage()}
     */
    public static String errorLabel(String channelPrefix, String businessCode, String fallbackMessage) {
        if (businessCode == null) {
            return channelPrefix + "搜索失败: " + fallbackMessage;
        }
        switch (businessCode) {
            case CODE_PARAMS_CHECK:
                return MSG_PARAMS_CHECK;
            case CODE_NEED_LOGIN:
                return MSG_NEED_LOGIN;
            case CODE_VERIFY_CHECK:
                return MSG_VERIFY_CHECK;
            default:
                return "搜索错误: " + businessCode;
        }
    }

    /**
     * 用户/直播独立 API 的 catch 文案（ye4:717/722/932/937）：
     * {@code "用户搜索异常: " / "直播搜索异常: " + e.getMessage()}。
     */
    public static String channelErrorLabel(boolean userChannel, String exceptionMessage) {
        return (userChannel ? "用户搜索异常: " : "直播搜索异常: ") + exceptionMessage;
    }

    /** 空结果文案（670 综合视频 / 788+1108+1658 用户 / 880+1211+1751 直播）。 */
    public static String emptyLabel(boolean userChannel) {
        return userChannel ? EMPTY_USER : EMPTY_LIVE;
    }

    /** 空结果文案（综合视频频道）。 */
    public static String emptyVideoLabel() {
        return EMPTY_VIDEO;
    }
}
