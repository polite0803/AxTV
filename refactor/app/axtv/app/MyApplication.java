/*
 * 来源: src/app/com/github/mytv/p004dv/MyApplication.java（130 行）
 * 原职责: Application 初始化：小组件刷新调度 → 播放地址解析器 → Activity 生命周期跟踪 →
 *         QuickJS 初始化 → 后台清理旧更新包/初始化视频缓存 → 安装崩溃兜底
 * 重构说明: 去掉 Android 类型，保留**初始化顺序**与每步的失败兜底；平台能力抽 Platform 接口
 */
package axtv.app;

import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;

import axtv.settings.CrashHandler;
import axtv.settings.UpdateManager;

/**
 * 应用启动流程（原 {@code MyApplication}）。
 *
 * <p><b>顺序是行为的一部分</b>，与原文逐行对齐：
 * <ol>
 *   <li>小组件刷新调度（{@code WidgetRefreshScheduler.scheduleAll}）</li>
 *   <li>播放地址解析器初始化（{@code VideoPlayUrlResolver.init}）</li>
 *   <li>注册 Activity 生命周期回调（记录当前前台 Activity）</li>
 *   <li>QuickJS 初始化 —— <b>失败不阻断启动</b>，只打 warn（QuickJS 不可用时签名/播放会退化）</li>
 *   <li>后台串行：清理 {@code updates/*.apk} → 初始化视频缓存；两步都各自吞异常</li>
 *   <li>安装崩溃兜底处理器</li>
 *   <li>打印 "Application onCreate 完成"</li>
 * </ol>
 *
 * <p>Coil 的 ImageLoader 工厂（原文 {@code newImageLoader}，OkHttp + 拦截器）属于 UI 层，
 * 见 NOTES.md「待迁移」。
 */
public final class MyApplication {

    private static final Logger LOG = Logger.getLogger("AxTV");

    private final Platform platform;
    private final File filesDir;
    private final CrashHandler crashHandler;

    public MyApplication(Platform platform, File filesDir, CrashHandler crashHandler) {
        this.platform = platform;
        this.filesDir = filesDir;
        this.crashHandler = crashHandler;
    }

    /** 对应 {@code Application.onCreate()}。 */
    public void onCreate() {
        platform.scheduleWidgetRefresh();
        platform.initVideoPlayUrlResolver();
        platform.registerActivityTracker();

        try {
            platform.initQuickJs();
            LOG.info("QuickJSLoader initialised");
        } catch (Throwable t) {
            // 原文：g54/h54 捕获后只打一条 warn，不抛出
            LOG.log(Level.WARNING, "QuickJSLoader.init() failed (QuickJS not available?)", t);
        }

        platform.runInBackground(() -> {
            try {
                UpdateManager.cleanOldApks(filesDir);
            } catch (Throwable ignored) {
                // 原文第一段 try/catch：静默
            }
            try {
                platform.initVideoCache();
            } catch (Throwable ignored) {
                // 原文第二段 try/catch：静默
            }
        });

        // 原文：Thread.getDefaultUncaughtExceptionHandler(); // 返回值丢弃，仅取当前链
        crashHandler.install();
        LOG.info("Application onCreate 完成");
    }

    /** Android 侧实现要点见各类注释；核心层不依赖任何 Android 类。 */
    public interface Platform {

        /** {@code WidgetRefreshScheduler.INSTANCE.scheduleAll(this)}：注册 6 个桌面部件的刷新。 */
        void scheduleWidgetRefresh();

        /** {@code VideoPlayUrlResolver.INSTANCE.init(this)}：注入 context / 缓存目录。 */
        void initVideoPlayUrlResolver();

        /**
         * {@code registerActivityLifecycleCallbacks(new s53())}：
         * onResume 记录当前 Activity（WeakReference），onPause 清空；供全局 Toast/Dialog 取 context。
         */
        void registerActivityTracker();

        /** {@code QuickJSLoader.init()}；不可用时抛异常，由调用方吞掉并降级。 */
        void initQuickJs() throws Throwable;

        /**
         * 在后台线程串行执行（原文在 applicationScope 上 launch）。
         * 实现方不得把异常向外抛。
         */
        void runInBackground(Runnable task);

        /**
         * {@code sn5.m10276c(this)}：初始化 ExoPlayer SimpleCache，
         * 目录 {@code cacheDir/video-media}，LRU 上限 100MB（96 MiB = 100663296 字节）。
         */
        void initVideoCache();
    }
}
