/*
 * 来源: android.util.Log 的调用点（C1400n / C0761j / C1608s / RunnableC0688h / zg0 / no4 / q30 / C1285k）
 * 原职责: 原实现直接调用 android.util.Log，TAG 为 "ABogusSigner" / "SignJs"
 * 重构说明: 核心层不依赖 Android，改为接口注入；日志点与文案全部保留，级别一一对应
 *           d->DEBUG  i->INFO  w->WARN  e->ERROR
 */
package axtv.sign;

import java.util.logging.Logger;

/** 日志出口。Android 侧用 {@code android.util.Log} 实现，JVM 侧用 {@link #jvm()}。 */
public interface SignLog {

    enum Level {
        DEBUG,
        INFO,
        WARN,
        ERROR
    }

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

    /** 丢弃全部日志（等价于原实现里 Log 被裁剪的构建）。 */
    static SignLog disabled() {
        return (level, tag, message, error) -> {
        };
    }

    /**
     * 纯 JDK 实现，打到 {@link java.util.logging.Logger}；用于桌面端调试与单元测试。
     *
     * <p>注：本接口自带 {@link Level} 枚举，与 {@code java.util.logging.Level} 同名，
     * 故此处全部用全限定名，避免遮蔽。
     */
    static SignLog jvm() {
        return (level, tag, message, error) -> {
            Logger logger = Logger.getLogger(tag);
            java.util.logging.Level mapped = switch (level) {
                case DEBUG -> java.util.logging.Level.FINE;
                case INFO -> java.util.logging.Level.INFO;
                case WARN -> java.util.logging.Level.WARNING;
                case ERROR -> java.util.logging.Level.SEVERE;
            };
            if (error == null) {
                logger.log(mapped, message);
            } else {
                logger.log(mapped, message, error);
            }
        };
    }
}
