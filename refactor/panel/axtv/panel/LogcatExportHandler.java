/*
 * 来源: src/app/p000/qj0.java 的 /logcat 分支（Runtime.getRuntime().exec("logcat -t 10000 -v threadtime")）
 * 原职责: 把最近 10000 行 logcat 以附件形式导出给开发者（手机点「导出日志」）
 * 重构说明: 抽成 LogcatSource 接口 + 进程实现；响应头 filename="logcat.txt" 原样保留。
 */
package axtv.panel;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * GET /logcat —— 导出 logcat。
 *
 * <p>原实现直接 {@code exec("logcat", "-t", "10000", "-v", "threadtime")}，
 * 也就是取最近 1 万行、threadtime 格式。Android 上该二进制位于 {@code /system/bin}，
 * 需要应用有读日志权限（正常安装的 App 只能看到自己的日志；系统签名/开发版可见全部）。
 */
public final class LogcatExportHandler {

    /** 取多少行（原实现的 {@code -t 10000}）。 */
    public static final int TAIL_LINES = 10000;
    public static final String FORMAT = "threadtime";
    public static final String DOWNLOAD_FILE_NAME = "logcat.txt";

    /** logcat 数据源。Android 侧实现见 {@link ProcessLogcat}；也可换成直接读 logcat 的调试实现。 */
    public interface LogcatSource {
        String capture() throws Exception;
    }

    /** 默认实现：起 {@code logcat} 子进程，读干 stdout 后等进程退出。 */
    public static final class ProcessLogcat implements LogcatSource {
        @Override
        public String capture() throws IOException, InterruptedException {
            Process process = Runtime.getRuntime().exec(new String[]{
                    "logcat", "-t", String.valueOf(TAIL_LINES), "-v", FORMAT});
            StringBuilder out = new StringBuilder(64 * 1024);
            try (InputStream in = process.getInputStream();
                 BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8), 8192)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    out.append(line).append('\n');
                }
            }
            process.waitFor();
            return out.toString();
        }
    }

    private final LogcatSource source;
    private final Logger log = Logger.getLogger("CookieServer");

    public LogcatExportHandler(LogcatSource source) {
        this.source = source;
    }

    /** 处理 GET /logcat。异常时 500 纯文本（与原实现一致，不带附件的 Content-Disposition）。 */
    public PanelRoutes.Response handle(PanelRoutes.Request request) {
        try {
            String content = this.source.capture();
            return PanelRoutes.Response.text(PanelRoutes.SC_OK, PanelRoutes.TEXT_PLAIN_UTF8, content)
                    .withHeader("Content-Disposition", "attachment; filename=\"" + DOWNLOAD_FILE_NAME + "\"")
                    .withCors();
        } catch (Exception e) {
            this.log.log(Level.SEVERE, "读取 logcat 失败", e);
            return PanelRoutes.Response.text(PanelRoutes.SC_INTERNAL_ERROR, PanelRoutes.TEXT_PLAIN,
                    "读取 logcat 失败: " + e.getMessage()).withCors();
        }
    }
}
