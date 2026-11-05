/*
 * Android 适配层 —— sign 模块：日志桥。
 * 规格出处: ADAPTERS.md §1 / sign/NOTES.md §6-3
 * 行为: SignLog.Level → android.util.Log 优先级；TAG 沿用核心层传入值（AbogusEngine/SignJsEngine）。
 */
package com.github.axtv.dv.sign

import android.util.Log
import axtv.sign.SignLog

object AndroidSignLog : SignLog {
    override fun log(level: SignLog.Level, tag: String, message: String, error: Throwable?) {
        when (level) {
            SignLog.Level.DEBUG -> Log.d(tag, message, error)
            SignLog.Level.INFO -> Log.i(tag, message, error)
            SignLog.Level.WARN -> Log.w(tag, message, error)
            SignLog.Level.ERROR -> Log.e(tag, message, error)
        }
    }
}
