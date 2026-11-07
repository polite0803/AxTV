/*
 * 焦点记忆（进程内 + 落盘）。
 *
 * TV 页面从播放器/详情返回时是重新组合（remember 状态不在），如果没有记忆，
 * 各列表会退回到「第一项/返回按钮」，遥控器要重新走很远。这里按「页面上下文」
 * 记下最后聚焦条目的 id 或下标，页面重建后优先恢复。
 *
 * 内存层是唯一读路径（last() 不碰磁盘）；remember() 同步写内存、异步写盘。
 * 落盘让「进程被杀后重启」也能恢复（TV 上应用常被系统回收）。
 * 只存字符串，不持有 Composable/Requester；调用方负责按 id 找回条目。
 *
 * 独立 prefs 文件，不进 axtv_settings：焦点记忆是易失 UI 状态，
 * 不该跟着「导出配置 / 导入配置」跨设备搬运。
 */
package com.github.axtv.dv.ui

import android.content.Context
import android.content.SharedPreferences

internal object FocusMemory {

    private const val PREFS_NAME = "axtv_focus_memory"

    @Volatile
    private var prefs: SharedPreferences? = null

    private val lastValues = HashMap<String, String>()

    /** 应用入口装配（MyApplication.onCreate）；之后 remember/last 才有落盘能力。 */
    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs!!.all.forEach { (key, value) ->
            if (value is String && key.isNotBlank() && value.isNotBlank()) {
                lastValues[key] = value
            }
        }
    }

    @Synchronized
    fun remember(scope: String, value: String) {
        if (scope.isBlank() || value.isBlank()) {
            return
        }
        if (lastValues[scope] == value) {
            return
        }
        lastValues[scope] = value
        prefs?.edit()?.putString(scope, value)?.apply()
    }

    @Synchronized
    fun last(scope: String): String? = lastValues[scope]
}
