/*
 * 失效合集的「本机移除」记录。
 *
 * 抖音没有可核实的「取消收藏合集 / 删除系列」接口，服务端列表仍会把失效条目发回来；
 * 用户在详情页确认清除后，记到本机并在列表里过滤，避免反复点进失效合集。
 * 换账号不影响这份记录：它按合集/系列 id 存，同一 id 在哪个账号下都指向同一个合集。
 */
package com.github.axtv.dv.ui.collection

import android.content.Context
import androidx.compose.runtime.mutableStateOf

internal object RemovedCollections {

    private const val PREFS_NAME = "axtv_collections"
    private const val KEY_REMOVED_IDS = "removed_mix_ids"

    /** 组合期读取即订阅：详情页移除后，返回列表会立即过滤掉该条。 */
    val ids = mutableStateOf<Set<String>>(emptySet())

    fun load(context: Context) {
        ids.value = prefs(context).getStringSet(KEY_REMOVED_IDS, emptySet())
            .orEmpty()
            .toSet()
    }

    fun remove(context: Context, id: String) {
        if (id.isBlank()) return
        val next = ids.value + id
        ids.value = next
        prefs(context).edit().putStringSet(KEY_REMOVED_IDS, next).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
