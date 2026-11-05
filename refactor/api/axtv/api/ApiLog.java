/*
 * 来源: n11.java 中 android.util.Log 调用（TAG: DouyinDirectApi / LiveWS / DanmakuAPI / ABogusSigner）
 * 原职责: 打调试日志（请求参数、签名结果、空响应诊断、ticket-guard 校验结论）
 * 重构说明: 核心层不依赖 android.util.Log，改用 JDK Logger；
 *           日志点与文案保持不变，便于对齐原 App 的排查方式
 */
package axtv.api;

import java.util.logging.Level;
import java.util.logging.Logger;

public final class ApiLog {

    public static final String TAG_API = "DouyinDirectApi";
    public static final String TAG_LIVE_WS = "LiveWS";
    public static final String TAG_DANMAKU = "DanmakuAPI";

    private ApiLog() {
    }

    public static void d(String tag, String message) {
        logger(tag).log(Level.FINE, message);
    }

    public static void w(String tag, String message) {
        logger(tag).log(Level.WARNING, message);
    }

    public static void e(String tag, String message, Throwable error) {
        logger(tag).log(Level.SEVERE, message, error);
    }

    private static Logger logger(String tag) {
        return Logger.getLogger(tag);
    }
}
