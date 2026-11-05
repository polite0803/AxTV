/*
 * 适配层 —— 配置导出 / 导入（设置页『配置管理』行的后端）。
 *
 * 落点：应用私有外部目录 getExternalFilesDir(Download)，即
 *   /storage/emulated/0/Android/data/com.github.axtv.dv/files/Download/axtv-settings-<yyyyMMdd-HHmmss>.json
 * 选它的理由：TV 上不需要任何运行时权限（私有目录随装随有），adb / 文件管理器可直接取；
 * MediaStore Downloads 在 API 29+ 才可用，本应用 minSdk 24，用它会多一条分支且读回要查询。
 *
 * 序列化：直接导出 SharedPreferences 的全部键值（含类型），
 * 因为核心层 SettingsJson（refactor/settings/axtv/settings/SettingsJson.java:18-31）只覆盖
 * accounts_list / danmaku_block_words 两种形态，没有整表序列化能力；
 * org.json 在 Android 侧已有先例（ui/settings/UpdatePlatform.kt:24、ui/search/SearchHistory.kt:21）。
 */
package com.github.axtv.dv.adapter

import android.content.Context
import android.os.Environment
import com.github.axtv.dv.settings.SharedPrefsSettingsStore
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object SettingsConfigIO {

    private const val FILE_PREFIX = "axtv-settings-"
    private const val FILE_SUFFIX = ".json"
    private const val FORMAT_VERSION = 1

    /** 导出/导入可用的落点（优先应用私有外部目录，无外部存储时退回 filesDir）。 */
    fun targetDir(context: Context): File =
        context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir

    /** 已导出的配置包，按时间倒序（导入对话框列候选用）。 */
    fun existingExports(context: Context): List<File> =
        targetDir(context)
            .listFiles { file -> file.name.startsWith(FILE_PREFIX) && file.name.endsWith(FILE_SUFFIX) }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()

    /** 导出一整份配置；返回落盘文件。异常原样抛出，由 UI 展示原因。 */
    @Throws(Exception::class)
    fun export(context: Context): File {
        val prefs = context.applicationContext
            .getSharedPreferences(SharedPrefsSettingsStore.PREFS_NAME, Context.MODE_PRIVATE)
        val values = JSONObject()
        prefs.all.forEach { (key, value) ->
            values.put(key, JSONObject().apply {
                when (value) {
                    is Boolean -> { put("t", "b"); put("v", value) }
                    is Int -> { put("t", "i"); put("v", value) }
                    is Long -> { put("t", "l"); put("v", value) }
                    is Float -> { put("t", "f"); put("v", value.toDouble()) }
                    is String -> { put("t", "s"); put("v", value) }
                    // SharedPreferences 只可能给出以上 5 种类型；未知形态按字符串导出，避免丢键。
                    else -> { put("t", "s"); put("v", value?.toString().orEmpty()) }
                }
            })
        }
        val document = JSONObject()
            .put("format", FORMAT_VERSION)
            .put("package", context.packageName)
            .put("exportedAt", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()))
            .put("values", values)
        val dir = targetDir(context).apply { mkdirs() }
        val file = File(dir, FILE_PREFIX +
            SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()) + FILE_SUFFIX)
        file.writeText(document.toString())
        return file
    }

    /** 导入一份配置，返回写入的键数；文件形态不合法时抛异常（不做静默跳过）。 */
    @Throws(Exception::class)
    fun import(context: Context, file: File): Int {
        val document = JSONObject(file.readText())
        val values = document.optJSONObject("values")
            ?: throw IllegalArgumentException("缺少 values 段，不是本应用导出的配置包")
        val editor = context.applicationContext
            .getSharedPreferences(SharedPrefsSettingsStore.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
        var applied = 0
        for (key in values.keys()) {
            val entry = values.getJSONObject(key)
            when (entry.optString("t")) {
                "b" -> editor.putBoolean(key, entry.optBoolean("v"))
                "i" -> editor.putInt(key, entry.optInt("v"))
                "l" -> editor.putLong(key, entry.optLong("v"))
                "f" -> editor.putFloat(key, entry.optDouble("v").toFloat())
                "s" -> editor.putString(key, entry.optString("v"))
                else -> throw IllegalArgumentException("键 $key 的类型标记无法识别")
            }
            applied += 1
        }
        editor.apply()
        return applied
    }
}
