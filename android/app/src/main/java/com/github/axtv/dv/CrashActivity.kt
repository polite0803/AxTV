/*
 * Android 适配层 —— 崩溃展示页（独立 :crash 进程）。
 * Manifest: <activity android:name=".CrashActivity" android:process=":crash">
 * 行为: 读取 intent extra（崩溃摘要）纯文本展示；落盘/导出桥接见 ADAPTERS.md §6 CrashReporter。
 */
package com.github.axtv.dv

import android.os.Bundle
import android.widget.TextView
import androidx.activity.ComponentActivity

class CrashActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val summary = intent.getStringExtra(EXTRA_SUMMARY) ?: "未捕获崩溃信息"
        val view = TextView(this)
        view.text = "AxTV 发生异常\n\n$summary"
        view.setPadding(48, 96, 48, 48)
        view.movementMethod = android.text.method.ScrollingMovementMethod.getInstance()
        setContentView(view)
    }

    companion object {
        const val EXTRA_SUMMARY = "crash_summary"
    }
}
