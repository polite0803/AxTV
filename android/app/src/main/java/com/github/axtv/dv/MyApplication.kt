/*
 * Android 适配层 —— 应用入口。
 * Manifest: <application android:name=".MyApplication">
 * 职责：装配 sign / api / settings 三张表的运行时依赖（AppRuntime）。
 */
package com.github.axtv.dv

import android.app.Application
import com.github.axtv.dv.adapter.AppRuntime

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppRuntime.install(this)
    }

    override fun onTerminate() {
        AppRuntime.shutdown()
        super.onTerminate()
    }

    companion object {
        const val TAG = "AxTV"
    }
}
