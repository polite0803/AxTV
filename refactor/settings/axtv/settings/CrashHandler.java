/*
 * 来源: src/app/com/github/mytv/p004dv/MyApplication.java（onCreate 里 Thread.setDefaultUncaughtExceptionHandler 的匿名类 p53）
 *       + src/app/p004dv/CrashActivity.java（展示/复制/重启）
 * 原职责: 崩溃时把设备/版本/线程/异常/堆栈拼成报告，跳到 CrashActivity，睡 500ms 后杀进程
 * 重构说明: 拼装逻辑抽成纯函数；「弹 UI」「写文件」「杀进程」用接口隔离
 */
package axtv.settings;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 全局崩溃兜底（原 {@code MyApplication} 里的匿名 {@code UncaughtExceptionHandler}）。
 *
 * <p>报告格式逐行还原（分隔线、字段名、应用版本号都写死成 2.3.43 (43)）。
 *
 * <p>与原实现的差异（有意增强，见 NOTES.md）：原文只在 CrashActivity 里展示，**不落盘**；
 * 本实现额外写到 {@code filesDir/crash/crash-yyyyMMdd-HHmmss.txt}，便于用户导出。
 */
public final class CrashHandler {

    private static final Logger LOG = Logger.getLogger("AxTV_CRASH");

    private static final DateTimeFormatter FILE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final DateTimeFormatter REPORT_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Platform platform;
    private final File crashDir;

    public CrashHandler(Platform platform, File crashDir) {
        this.platform = platform;
        this.crashDir = crashDir;
    }

    /** 安装为全局处理器（原 {@code Thread.setDefaultUncaughtExceptionHandler}）。 */
    public void install() {
        Thread.setDefaultUncaughtExceptionHandler(this::handle);
    }

    /** 处理一次崩溃：记日志 → 落盘 → 展示 → 延时 → 杀进程（顺序与原文一致，落盘是新增）。 */
    public void handle(Thread thread, Throwable error) {
        LOG.log(Level.SEVERE, "=== 应用崩溃 ===", error);
        String report = buildReport(thread, error, platform, LocalDateTime.now());
        writeToDisk(report);
        try {
            platform.showCrashScreen(report);
        } catch (RuntimeException e) {
            LOG.log(Level.SEVERE, "启动CrashActivity失败", e);
        }
        try {
            Thread.sleep(500L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        platform.killProcess();
    }

    /** 拼装报告（原匿名类里的 StringBuilder 拼接，逐行一致）。 */
    public static String buildReport(Thread thread, Throwable error, Platform platform, LocalDateTime now) {
        StringWriter stack = new StringWriter();
        error.printStackTrace(new PrintWriter(stack));

        StringBuilder sb = new StringBuilder("━━ AxTV 崩溃报告 ━━\n");
        sb.append("时间: ").append(REPORT_TIME.format(now)).append('\n');
        sb.append("设备: ").append(platform.brand()).append(' ').append(platform.model()).append('\n');
        sb.append("应用版本: ").append(platform.versionName()).append(" (").append(platform.versionCode()).append(")\n");
        sb.append("Android: ").append(platform.androidRelease())
                .append(" (API ").append(platform.sdkInt()).append(")").append('\n');
        sb.append("线程: ").append(thread.getName()).append('\n');
        sb.append("\n━━ 异常信息 ━━\n");
        sb.append(error.getClass().getName()).append(": ").append(error.getMessage()).append('\n');
        Throwable cause = error.getCause();
        if (cause != null) {
            sb.append("根因: ").append(cause.getClass().getName()).append(": ").append(cause.getMessage()).append('\n');
        }
        sb.append("\n━━ 堆栈跟踪 ━━\n");
        sb.append(stack);
        return sb.toString();
    }

    /** 落盘：{@code crashDir/crash-<时间>.txt}；失败只记日志（不能因为写盘失败二次崩溃）。 */
    private void writeToDisk(String report) {
        try {
            // java.nio.file 自 API 26 起可用，minSdk 23 下用 java.io；创建失败仍走原 catch 记日志。
            if (!crashDir.isDirectory() && !crashDir.mkdirs()) {
                throw new IOException("无法创建崩溃日志目录: " + crashDir);
            }
            File file = new File(crashDir, "crash-" + FILE_STAMP.format(LocalDateTime.now()) + ".txt");
            // Android 上 android.jar 无 Files.writeString（Java 11 API）；改用 FileOutputStream + UTF-8，写入字节与截断语义一致。
            try (FileOutputStream out = new FileOutputStream(file)) {
                out.write(report.getBytes(StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            LOG.log(Level.WARNING, "崩溃日志落盘失败", e);
        }
    }

    /** 崩溃现场需要的设备/应用信息，以及副作用（弹界面、杀进程）。 */
    public interface Platform {
        String brand();

        String model();

        String versionName();

        int versionCode();

        String androidRelease();

        int sdkInt();

        /** 原：{@code startActivity(new Intent(app, CrashActivity.class).putExtra("crash_info", report))}。 */
        void showCrashScreen(String report);

        /** 原：{@code Process.killProcess(Process.myPid())}。 */
        void killProcess();
    }
}
