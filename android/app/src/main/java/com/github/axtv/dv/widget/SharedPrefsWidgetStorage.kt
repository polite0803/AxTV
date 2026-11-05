/*
 * Android 适配层 —— widget 模块：小组件数据持久化。
 * 规格出处: ADAPTERS.md §8 / widget/NOTES.md §7（SP 文件名与键全表在 WidgetStorage 接口常量）
 * 行为: WidgetStorage → SharedPreferences(PREFS_NAME)；putString(null) = 移除（与 InMemory 一致）。
 */
package com.github.axtv.dv.widget

import android.content.Context
import android.content.SharedPreferences
import axtv.widget.WidgetStorage

class SharedPrefsWidgetStorage(context: Context) : WidgetStorage {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(WidgetStorage.PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * 注意：核心层 WidgetStore.read 固定传 null，用「缺失返回 null」判断默认快照分支
     * （WidgetStore.java:27 `getString(key, null)` → `raw == null` 才走 def）。
     * Java 接口为平台类型，Kotlin 覆写必须可空，否则入口 checkNotNullParameter 直接 NPE。
     */
    override fun getString(key: String, def: String?): String? =
        prefs.getString(key, def)

    /** value 为 null 表示移除该键，与 SharedPreferences.Editor.putString(key, null) 一致。 */
    override fun putString(key: String, value: String?) {
        val editor = prefs.edit()
        if (value == null) editor.remove(key) else editor.putString(key, value)
        editor.apply()
    }

    override fun getInt(key: String, def: Int): Int = prefs.getInt(key, def)

    fun putInt(key: String, value: Int) {
        prefs.edit().putInt(key, value).apply()
    }

    override fun clear() {
        prefs.edit().clear().apply()
    }
}
