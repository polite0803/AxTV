/*
 * Android 适配层 —— 崩溃兜底装配（axtv.settings.CrashHandler 的 Platform 实现）。
 *
 * 规格出处：
 *   refactor/settings/NOTES.md §4（报告字段/顺序）；
 *   refactor/settings/axtv/settings/CrashHandler.java（文件名与落盘路径）。
 *
 * 落盘：filesDir/crash/crash-yyyyMMdd-HHmmss.txt
 *   filesDir 为应用私有目录（/data/data/com.github.axtv.dv/files），
 *   debug 包可用 `adb shell run-as com.github.axtv.dv cat files/crash/<name>` 直接读取。
 *
 * 链式：安装前保存系统默认 handler；本处理器完成落盘/拉起 CrashActivity/500ms 延时后，
 *   在 killProcess 阶段回调系统默认 handler，保留系统崩溃对话框与进程终结语义。
 */
package com.github.axtv.dv.adapter

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.util.Log
import axtv.settings.CrashHandler
import com.github.axtv.dv.BuildConfig
import com.github.axtv.dv.CrashActivity
import java.io.File

object AndroidCrashReporter {

    private const val TAG = "AxTV_CRASH"

    @Volatile
    private var installed = false

    /** 在 MyApplication.onCreate 最先调用；重复调用无副作用。 */
    fun install(context: Context) {
        if (installed) return
        synchronized(this) {
            if (installed) return
            installed = true
        }

        val app = context.applicationContext
        val crashDir = File(app.filesDir, "crash")
        // 必须在覆盖之前取到系统默认 handler（Android 的 KillApplicationHandler）。
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        val platform = CrashPlatform(app, previous)
        val handler = CrashHandler(platform, crashDir)

        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            // killProcess() 没有参数，需把本次崩溃现场留给回调系统 handler 时使用。
            platform.record(thread, error)
            try {
                handler.handle(thread, error)
            } catch (t: Throwable) {
                // 核心处理链路自身异常时，仍把现场交还系统默认 handler，避免吞掉崩溃。
                Log.e(TAG, "崩溃兜底处理失败，回落系统默认 handler", t)
                previous?.uncaughtException(thread, error)
            }
        }
        Log.i(TAG, "崩溃兜底已安装: " + crashDir.absolutePath)
    }

    private class CrashPlatform(
        private val context: Context,
        private val previous: Thread.UncaughtExceptionHandler?
    ) : CrashHandler.Platform {

        private val crashingThread = ThreadLocal<Thread>()
        private val crashingError = ThreadLocal<Throwable>()

        fun record(thread: Thread, error: Throwable) {
            crashingThread.set(thread)
            crashingError.set(error)
        }

        override fun brand(): String = Build.BRAND

        override fun model(): String = Build.MODEL

        override fun versionName(): String = BuildConfig.VERSION_NAME

        override fun versionCode(): Int = BuildConfig.VERSION_CODE

        override fun androidRelease(): String = Build.VERSION.RELEASE

        override fun sdkInt(): Int = Build.VERSION.SDK_INT

        /** 原实现：new Intent(app, CrashActivity.class) + flags 268468224（NEW_TASK|CLEAR_TASK）。 */
        override fun showCrashScreen(report: String) {
            val intent = Intent(context, CrashActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                .putExtra(CrashActivity.EXTRA_SUMMARY, report)
            context.startActivity(intent)
        }

        /**
         * 在处理完崩溃现场后调用，清除 ThreadLocal 引用，防止内存泄漏。
         */
        fun clearThreadLocals() {
            crashingThread.remove()
            crashingError.remove()
        }

        /**
         * 先回调系统默认 handler（保留崩溃对话框/系统上报/进程终结），再兜底 killProcess。
         * 任何一步都不允许影响后续兜底。
         */
        override fun killProcess() {
            val thread = crashingThread.get() ?: Thread.currentThread()
            val error = crashingError.get()
            try {
                if (previous != null && error != null) {
                    previous.uncaughtException(thread, error)
                }
            } catch (t: Throwable) {
                Log.e(TAG, "系统默认崩溃处理失败", t)
            } finally {
                Process.killProcess(Process.myPid())
            }
        }
    }
}
