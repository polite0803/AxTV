/*
 * Android 适配层 —— 应用入口。
 * Manifest: <application android:name=".MyApplication">
 * 职责：装配 sign / api / settings 三张表的运行时依赖（AppRuntime）。
 */
package com.github.axtv.dv

import android.app.Application
import com.github.axtv.dv.adapter.AndroidCrashReporter
import com.github.axtv.dv.adapter.AppRuntime
import com.github.axtv.dv.ui.FocusMemory

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // 最先安装崩溃兜底，才能兜住后续装配阶段的异常。
        AndroidCrashReporter.install(this)
        AppRuntime.install(this)
        // 焦点记忆落盘：进程被系统回收后重启仍能恢复各列表的焦点位置。
        FocusMemory.init(this)
    }

    override fun onTerminate() {
        AppRuntime.shutdown()
        super.onTerminate()
    }

    companion object {
        const val TAG = "AxTV"
    }
}
