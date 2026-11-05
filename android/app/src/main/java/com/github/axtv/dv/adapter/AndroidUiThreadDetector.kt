/*
 * Android 适配层 —— api：主线程判定。
 * 规格出处: api/NOTES.md（主线程禁止同步网络请求）。
 */
package com.github.axtv.dv.adapter

import android.os.Looper
import axtv.api.UiThreadDetector

object AndroidUiThreadDetector : UiThreadDetector {
    override fun isUiThread(): Boolean = Looper.myLooper() == Looper.getMainLooper()
}
