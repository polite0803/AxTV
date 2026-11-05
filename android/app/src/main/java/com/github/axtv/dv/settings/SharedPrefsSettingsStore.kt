/*
 * Android 适配层 —— settings 模块：配置持久化（SharedPreferences）。
 * 规格出处: ADAPTERS.md §6 / settings/NOTES.md 配置键全表（71 键见 SettingsKeys.java）
 * 行为: SettingsStore 13 方法 → SP 通用读写；类型由调用方按 SettingsKeys 决定。
 */
package com.github.axtv.dv.settings

import android.content.Context
import android.content.SharedPreferences
import axtv.settings.SettingsStore

class SharedPrefsSettingsStore(context: Context) : SettingsStore {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("axtv_settings", Context.MODE_PRIVATE)

    /**
     * 注意：核心层（Settings.java）有 8 处传 null 表示「无默认值，缺失时应返回 null 交给 parse 走默认分支」。
     * Java 接口是平台类型，Kotlin 覆写必须保持可空，否则会插入 checkNotNullParameter 在入口直接 NPE。
     * 不能用 "" 代替 null：会把「缺失」变成「空字符串」，可能让 parse 进入非默认分支。
     */
    override fun getString(key: String, defValue: String?): String? =
        try {
            prefs.getString(key, defValue)
        } catch (e: ClassCastException) {
            dropDirty(key, e)
            defValue
        }

    override fun getInt(key: String, defValue: Int): Int =
        try {
            prefs.getInt(key, defValue)
        } catch (e: ClassCastException) {
            dropDirty(key, e)
            defValue
        }

    override fun getLong(key: String, defValue: Long): Long =
        try {
            prefs.getLong(key, defValue)
        } catch (e: ClassCastException) {
            dropDirty(key, e)
            defValue
        }

    override fun getFloat(key: String, defValue: Float): Float =
        try {
            prefs.getFloat(key, defValue)
        } catch (e: ClassCastException) {
            dropDirty(key, e)
            defValue
        }

    override fun getBoolean(key: String, defValue: Boolean): Boolean =
        try {
            prefs.getBoolean(key, defValue)
        } catch (e: ClassCastException) {
            dropDirty(key, e)
            defValue
        }

    /**
     * 类型不匹配（历史脏值 / 版本迁移）时回退默认值并清除脏值，
     * 避免每次读取都抛 ClassCastException。
     */
    private fun dropDirty(key: String, error: ClassCastException) {
        prefs.edit().remove(key).apply()
        android.util.Log.w("SettingsStore", "键 $key 类型不匹配，已回落默认值", error)
    }

    override fun contains(key: String): Boolean = prefs.contains(key)

    /**
     * value 允许为空，与 SharedPreferences.Editor.putString(key, null) 的语义一致（= 移除该键），
     * 避免核心层将来以 null 清除配置时在 Kotlin 入口被 checkNotNullParameter 拦截。
     */
    override fun putString(key: String, value: String?) {
        prefs.edit().putString(key, value).apply()
    }

    override fun putInt(key: String, value: Int) {
        prefs.edit().putInt(key, value).apply()
    }

    override fun putLong(key: String, value: Long) {
        prefs.edit().putLong(key, value).apply()
    }

    override fun putFloat(key: String, value: Float) {
        prefs.edit().putFloat(key, value).apply()
    }

    override fun putBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    override fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }
}
