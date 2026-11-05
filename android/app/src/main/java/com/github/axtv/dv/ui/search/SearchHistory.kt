/*
 * 本地搜索历史（对齐原版实现，仅供 ui/search 使用）。
 *
 * 取证（src/ 反编译产物）：
 * - 存储位置：SharedPreferences 文件名 "search_history"，键 "history"
 *   （src/app/p000/tc4.java：getSharedPreferences("search_history", 0).getString("history", "[]")）
 * - 值格式：JSON 字符串数组（新词在前）
 * - 写入规则：提交搜索时把关键词插到最前，过滤重复，只保留最近 10 条
 *   （src/app/p000/ye4.java 的 m12093a：listOf(newWord) + old.filterNot(== newWord)，take(10)）
 * - 登录门槛：仅在 cookie 含非空 sessionid/sessionid_ss 时记录
 *   （src/app/p000/ye4.java 写入前的 fl4.m4214v() 判定）
 * - 清空：原版有「清空历史」按钮（src/app/p000/qe4.java:1233）
 *
 * 注意：refactor/ 没有对应设置键（refactor/settings 内无 search/history 相关键），
 * 因此该存储实现放在 ui/search 内，键名与原版逐字一致，便于与原版数据互认。
 */
package com.github.axtv.dv.ui.search

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray

internal class SearchHistoryStore(context: Context?) {

    private val prefs: SharedPreferences? = context
        ?.applicationContext
        ?.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun load(): List<String> {
        val raw = prefs?.getString(KEY, null).orEmpty()
        if (raw.isBlank()) {
            return emptyList()
        }
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val word = array.optString(index, "").trim()
                    if (word.isNotEmpty() && word !in this) {
                        add(word)
                    }
                }
            }
        }.getOrDefault(emptyList())
    }

    /** 记录一次搜索：新词置顶、去重、最多 [MAX] 条。 */
    fun record(keyword: String): List<String> {
        val word = keyword.trim()
        if (word.isEmpty()) {
            return load()
        }
        val next = (listOf(word) + load().filterNot { it == word }).take(MAX)
        persist(next)
        return next
    }

    fun clear(): List<String> {
        persist(emptyList())
        return emptyList()
    }

    private fun persist(items: List<String>) {
        val prefs = prefs ?: return
        prefs.edit().putString(KEY, JSONArray(items).toString()).apply()
    }

    internal companion object {
        /** 与原版一致的 SharedPreferences 文件名。 */
        const val PREF_NAME = "search_history"

        /** 与原版一致的键名。 */
        const val KEY = "history"

        /** 原版只保留最近 10 条（ye4.java 写入处的常量 10）。 */
        const val MAX = 10
    }
}
