/*
 * 来源: src/app/p000/lb5.java:274/327/446、g73.java:734/750、g73.java:210
 * 原职责: android.util.Log 的三个 TAG（TvChannelPublisher / PreviewChannelHelper / PreviewChannel）
 * 重构说明: 接口化，默认实现写到 stderr，Android 侧替换为 Log.i/w/e
 */
package axtv.tv;

import java.io.PrintStream;

/** 日志出口。 */
public interface TvLog {

    void i(String tag, String message);

    void w(String tag, String message, Throwable error);

    void e(String tag, String message, Throwable error);

    /** 纯 JDK 默认实现，方便本地验证与单元测试。 */
    static TvLog stderr() {
        return new TvLog() {
            private final PrintStream out = System.err;

            @Override
            public void i(String tag, String message) {
                out.println("I/" + tag + ": " + message);
            }

            @Override
            public void w(String tag, String message, Throwable error) {
                out.println("W/" + tag + ": " + message);
                if (error != null) {
                    error.printStackTrace(out);
                }
            }

            @Override
            public void e(String tag, String message, Throwable error) {
                out.println("E/" + tag + ": " + message);
                if (error != null) {
                    error.printStackTrace(out);
                }
            }
        };
    }

    /** 静默实现。 */
    static TvLog noop() {
        return new TvLog() {
            @Override
            public void i(String tag, String message) {
            }

            @Override
            public void w(String tag, String message, Throwable error) {
            }

            @Override
            public void e(String tag, String message, Throwable error) {
            }
        };
    }
}
