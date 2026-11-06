/*
 * Android 适配层 —— 电视台直播源存储（axtv.iptv.IptvStore）。
 *
 * 核心层不碰 SharedPreferences：用户推送进来的源以 JSON 数组字符串落在 Settings（键 iptv_sources_list）。
 * 与推送侧 IptvSourceHandler 共用同一份数据 —— 手机推一条，电视列表下次加载就能看到。
 * 数组每项：{"name","source","group","logo","id"}。source 是 URL 或整份 M3U 正文原文，形态由读取侧判。
 */
package com.github.axtv.dv.panel

import axtv.iptv.IptvStore
import com.github.axtv.dv.adapter.AppRuntime
import org.json.JSONArray
import org.json.JSONObject

/** [IptvStore] 的 Android 实现：读写 Settings.iptvSourcesList（JSON 数组）。 */
class AndroidIptvStore : IptvStore {

    override fun sources(): List<IptvStore.SourceJson> {
        val raw = AppRuntime.settings?.iptvSourcesList().orEmpty()
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                IptvStore.SourceJson(
                    o.optString(KEY_NAME),
                    o.optString(KEY_SOURCE),
                    o.optString(KEY_GROUP),
                    o.optString(KEY_LOGO),
                    o.optString(KEY_ID),
                )
            }
        }.getOrDefault(emptyList())
    }

    override fun addSource(source: IptvStore.SourceJson) {
        val arr = parseArray(AppRuntime.settings?.iptvSourcesList().orEmpty())
        arr.put(toJson(source))
        AppRuntime.settings?.setIptvSourcesList(arr.toString())
    }

    override fun removeSource(sourceId: String) {
        val arr = parseArray(AppRuntime.settings?.iptvSourcesList().orEmpty())
        val kept = JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            if (o.optString(KEY_ID) != sourceId) kept.put(o)
        }
        AppRuntime.settings?.setIptvSourcesList(kept.toString())
    }

    private fun parseArray(raw: String): JSONArray =
        runCatching { JSONArray(raw) }.getOrDefault(JSONArray())

    private fun toJson(s: IptvStore.SourceJson): JSONObject = JSONObject().apply {
        put(KEY_NAME, s.name())
        put(KEY_SOURCE, s.urlOrContent())
        put(KEY_GROUP, s.group())
        put(KEY_LOGO, s.logoUrl())
        put(KEY_ID, s.sourceId())
    }

    private companion object {
        const val KEY_NAME = "name"
        const val KEY_SOURCE = "source"
        const val KEY_GROUP = "group"
        const val KEY_LOGO = "logo"
        const val KEY_ID = "id"
    }
}
