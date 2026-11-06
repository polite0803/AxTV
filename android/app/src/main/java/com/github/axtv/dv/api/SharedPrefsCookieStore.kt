/*
 * Android 适配层 —— api 模块：Cookie/设备标识持久化（SharedPreferences）。
 * 规格出处: ADAPTERS.md §2 / api/NOTES.md（SP 文件 douyin_auth，panel/NOTES.md 同文件）
 * 行为:
 *  - cookie 头以整串存 `cookie_header` 键，cookies() 按 "; " 拆分；
 *  - persistentDeviceId(key)：每个 key（device_id/iid…）首启生成 UUID 并持久化；
 *  - userUniqueId / selfUserId 独立键；
 *  - ticketGuard()：占位 empty()，待按 api/NOTES.md 补 bd_ticket_guard 解析。
 */
package com.github.axtv.dv.api

import android.content.Context
import android.content.SharedPreferences
import axtv.api.CookieStore
import axtv.api.TicketGuardCredentials
import java.util.UUID

class SharedPrefsCookieStore(context: Context) : CookieStore {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("douyin_auth", Context.MODE_PRIVATE)

    /**
     * 互动凭据的唯一真源是设置仓库（axtv_settings）：
     * 设置页粘贴保存、手机面板推送、账号切换都会写这一份；
     * douyin_auth 里的 interaction_* 只是面板路径的附带副本，不能作为读取源。
     */
    private val settingsPrefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("axtv_settings", Context.MODE_PRIVATE)

    @Synchronized
    override fun cookieHeader(): String = prefs.getString(KEY_HEADER, "") ?: ""

    @Synchronized
    override fun setCookieHeader(cookieHeader: String) {
        prefs.edit().putString(KEY_HEADER, cookieHeader ?: "").apply()
    }

    @Synchronized
    override fun cookies(): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        for (pair in cookieHeader().split(";")) {
            val idx = pair.indexOf('=')
            if (idx <= 0) continue
            out[pair.substring(0, idx).trim()] = pair.substring(idx + 1).trim()
        }
        return out
    }

    override fun persistentDeviceId(key: String): String {
        val existing = prefs.getString(key, null)
        if (!existing.isNullOrEmpty()) return existing
        val generated = UUID.randomUUID().toString()
        prefs.edit().putString(key, generated).apply()
        return generated
    }

    override fun userUniqueId(): String = prefs.getString(KEY_USER_UNIQUE, "") ?: ""

    override fun setUserUniqueId(value: String) {
        prefs.edit().putString(KEY_USER_UNIQUE, value ?: "").apply()
    }

    override fun selfUserId(): String = prefs.getString(KEY_SELF_USER, "") ?: ""

    override fun applySelfUserId(userId: String) {
        prefs.edit().putString(KEY_SELF_USER, userId ?: "").apply()
    }

    override fun ticketGuard(): TicketGuardCredentials {
        // 老版本面板可能只写过 douyin_auth 的 interaction_*；设置仓库为空时回退读它。
        fun read(key: String): String =
            settingsPrefs.getString(key, "").orEmpty()
                .ifBlank { prefs.getString(key, "").orEmpty() }
        // serverCert 的 ECDH 加密分支核心层未实现（会直接抛错），这里不把它带进签名：
        // 走「私钥派生公钥 + ECDSA client-data」的标准 ticket-guard 路径。
        return TicketGuardCredentials(
            read("interaction_security_payload"),
            read("interaction_ticket"),
            read("interaction_ts_sign"),
            read("interaction_private_key"),
            "",
        )
    }

    companion object {
        private const val KEY_HEADER = "cookie_header"
        private const val KEY_USER_UNIQUE = "user_unique_id"
        private const val KEY_SELF_USER = "self_user_id"
    }
}
