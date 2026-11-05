/*
 * 来源: android.util.Log 的调用点（bq2 / cq2 / C1285k / b11 / sz0 / q30 / om5 / i42）
 * 原职责: 原实现直接调用 android.util.Log，TAG 为 "LiveWS" / "LiveWebSocket" / "LiveViewModel" / "Danmaku"
 * 重构说明: 核心层不依赖 Android，改为接口注入；所有日志点、文案、级别一一保留
 *           d->DEBUG  i->INFO  w->WARN  e->ERROR
 */
package axtv.live;

/** 日志出口。Android 侧用 {@code android.util.Log} 实现，JVM 侧用 {@link #stderr()}。 */
public interface LiveLog {

    enum Level {
        DEBUG,
        INFO,
        WARN,
        ERROR
    }

    String TAG_LIVE_WS = "LiveWS";
    String TAG_LIVE_WEBSOCKET = "LiveWebSocket";
    String TAG_LIVE_VIEW_MODEL = "LiveViewModel";
    String TAG_DANMAKU = "Danmaku";

    void log(Level level, String tag, String message, Throwable error);

    default void d(String tag, String message) {
        log(Level.DEBUG, tag, message, null);
    }

    default void i(String tag, String message) {
        log(Level.INFO, tag, message, null);
    }

    default void w(String tag, String message) {
        log(Level.WARN, tag, message, null);
    }

    default void e(String tag, String message) {
        log(Level.ERROR, tag, message, null);
    }

    default void e(String tag, String message, Throwable error) {
        log(Level.ERROR, tag, message, error);
    }

    /** 丢弃全部日志。 */
    static LiveLog nop() {
        return (level, tag, message, error) -> {
        };
    }

    /** 输出到 {@code System.err}，格式与 logcat 的 threadtime 行接近。 */
    static LiveLog stderr() {
        return (level, tag, message, error) -> {
            System.err.println(level.name().charAt(0) + "/" + tag + ": " + message);
            if (error != null) {
                error.printStackTrace(System.err);
            }
        };
    }
}
