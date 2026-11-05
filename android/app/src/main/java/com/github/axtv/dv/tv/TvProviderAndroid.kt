/*
 * Android 适配层 —— tv 模块：EPG ContentProvider 桥。
 * 规格出处: ADAPTERS.md §7 / tv/NOTES.md 字段全表（列名常量 TvProviderColumns）
 *
 * 键映射（核心层键 → TvContractCompat 列）:
 *   _id→_ID                          display_name→COLUMN_DISPLAY_NAME
 *   internal_provider_id→COLUMN_INTERNAL_PROVIDER_ID
 *   app_link_intent_uri→COLUMN_APP_LINK_INTENT_URI   type→COLUMN_TYPE
 *   browsable→COLUMN_BROWSABLE       channel_id→COLUMN_CHANNEL_ID
 *   weight→COLUMN_WEIGHT             title→COLUMN_TITLE
 *   short_description→COLUMN_SHORT_DESCRIPTION
 *   poster_art_uri→COLUMN_POSTER_ART_URI
 *   poster_art_aspect_ratio→COLUMN_POSTER_ART_ASPECT_RATIO
 *   duration_millis→COLUMN_DURATION_MILLIS           intent_uri→COLUMN_INTENT_URI
 * SDK 裁剪逻辑（SDK<26 删 channel_id/weight/browsable）在核心层 TvProviderColumns，本类不重复。
 */
package com.github.axtv.dv.tv

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.tvprovider.media.tv.TvContractCompat
import androidx.tvprovider.media.tv.TvContractCompat.Channels
import androidx.tvprovider.media.tv.TvContractCompat.PreviewPrograms
import axtv.tv.TvProvider
import java.io.File

class TvProviderAndroid(private val context: Context) : TvProvider {

    private val resolver get() = context.contentResolver

    override fun sdkInt(): Int = Build.VERSION.SDK_INT

    override fun hasTvProvider(): Boolean {
        if (Build.VERSION.SDK_INT < 26) return false
        return try {
            resolver.query(Channels.CONTENT_URI, arrayOf(Channels._ID), null, null, null)
                ?.use { true } ?: false
        } catch (e: SecurityException) {
            false
        } catch (e: IllegalArgumentException) {
            false
        }
    }

    override fun queryChannels(): List<MutableMap<String, Any>> =
        queryRows(Channels.CONTENT_URI, CHANNEL_PROJECTION)

    override fun queryAllPrograms(): List<MutableMap<String, Any>> =
        queryRows(PreviewPrograms.CONTENT_URI, PROGRAM_PROJECTION)

    override fun queryProgram(rowId: Long): MutableMap<String, Any>? {
        if (Build.VERSION.SDK_INT < 26) return null
        return try {
            resolver.query(
                ContentUris.withAppendedId(PreviewPrograms.CONTENT_URI, rowId),
                PROGRAM_PROJECTION, null, null, null
            )?.use { c ->
                if (!c.moveToFirst()) return null
                c.toRow(PROGRAM_KEYS)
            }
        } catch (e: Exception) {
            null
        }
    }

    override fun insertChannel(values: MutableMap<String, Any>): Long {
        if (Build.VERSION.SDK_INT < 26) return -1L
        val cv = ContentValues()
        for ((k, v) in values) {
            val column = when (k) {
                "display_name" -> Channels.COLUMN_DISPLAY_NAME
                "internal_provider_id" -> Channels.COLUMN_INTERNAL_PROVIDER_ID
                "app_link_intent_uri" -> Channels.COLUMN_APP_LINK_INTENT_URI
                "type" -> Channels.COLUMN_TYPE
                "browsable" -> Channels.COLUMN_BROWSABLE
                else -> k
            }
            putValue(cv, column, v)
        }
        cv.put(Channels.COLUMN_INPUT_ID, context.packageName)
        val uri = resolver.insert(Channels.CONTENT_URI, cv) ?: return -1L
        return uri.lastPathSegment?.toLongOrNull() ?: -1L
    }

    override fun addChannelLogo(channelId: Long, logoUri: String): Boolean {
        if (Build.VERSION.SDK_INT < 26) return false
        if (logoUri.isNullOrEmpty()) return false
        return try {
            val target = TvContractCompat.buildChannelLogoUri(channelId)
            val source = Uri.parse(logoUri)
            resolver.openOutputStream(target)?.use { out ->
                resolver.openInputStream(source)?.use { input -> input.copyTo(out) } ?: return false
            } ?: return false
            true
        } catch (e: Exception) {
            false
        }
    }

    override fun deleteChannel(channelId: Long) {
        if (Build.VERSION.SDK_INT < 26) return
        runCatching {
            resolver.delete(ContentUris.withAppendedId(Channels.CONTENT_URI, channelId), null, null)
        }
    }

    override fun requestChannelBrowsable(channelId: Long) {
        if (Build.VERSION.SDK_INT < 26) return
        runCatching { TvContractCompat.requestChannelBrowsable(context, channelId) }
    }

    override fun insertProgram(values: MutableMap<String, Any>): Long {
        if (Build.VERSION.SDK_INT < 26) return -1L
        val cv = ContentValues()
        for ((k, v) in values) {
            putValue(cv, programColumn(k), v)
        }
        val uri = resolver.insert(PreviewPrograms.CONTENT_URI, cv) ?: return -1L
        return uri.lastPathSegment?.toLongOrNull() ?: -1L
    }

    override fun updateProgram(rowId: Long, values: MutableMap<String, Any>) {
        if (Build.VERSION.SDK_INT < 26) return
        val cv = ContentValues()
        for ((k, v) in values) {
            putValue(cv, programColumn(k), v)
        }
        runCatching {
            resolver.update(
                ContentUris.withAppendedId(PreviewPrograms.CONTENT_URI, rowId), cv, null, null
            )
        }
    }

    override fun deleteProgram(rowId: Long) {
        if (Build.VERSION.SDK_INT < 26) return
        runCatching {
            resolver.delete(ContentUris.withAppendedId(PreviewPrograms.CONTENT_URI, rowId), null, null)
        }
    }

    override fun channelLogoReady(): Boolean = logoFile().exists()

    override fun createChannelLogo() {
        val file = logoFile()
        if (file.exists()) return
        val drawable = runCatching {
            ContextCompat.getDrawable(context, context.applicationInfo.icon)
        }.getOrNull() ?: return
        val size = 128
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, size, size)
        drawable.draw(canvas)
        file.outputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
        bitmap.recycle()
    }

    override fun channelLogoUri(): String = Uri.fromFile(logoFile()).toString()

    // ---- 内部 ----

    private fun logoFile(): File = File(context.cacheDir, "tv_channel_logo.png")

    private fun programColumn(key: String): String = when (key) {
        "channel_id" -> PreviewPrograms.COLUMN_CHANNEL_ID
        "weight" -> PreviewPrograms.COLUMN_WEIGHT
        "type" -> PreviewPrograms.COLUMN_TYPE
        "title" -> PreviewPrograms.COLUMN_TITLE
        "short_description" -> PreviewPrograms.COLUMN_SHORT_DESCRIPTION
        "poster_art_uri" -> PreviewPrograms.COLUMN_POSTER_ART_URI
        "poster_art_aspect_ratio" -> PreviewPrograms.COLUMN_POSTER_ART_ASPECT_RATIO
        "duration_millis" -> PreviewPrograms.COLUMN_DURATION_MILLIS
        "intent_uri" -> PreviewPrograms.COLUMN_INTENT_URI
        "internal_provider_id" -> PreviewPrograms.COLUMN_INTERNAL_PROVIDER_ID
        "_id" -> PreviewPrograms._ID
        else -> key
    }

    private fun queryRows(uri: Uri, projection: Array<String>): List<MutableMap<String, Any>> {
        if (Build.VERSION.SDK_INT < 26) return emptyList()
        val keys = if (projection === CHANNEL_PROJECTION) CHANNEL_KEYS else PROGRAM_KEYS
        return try {
            resolver.query(uri, projection, null, null, null)?.use { c ->
                val out = ArrayList<MutableMap<String, Any>>(c.count)
                while (c.moveToNext()) {
                    out.add(c.toRow(keys))
                }
                out
            } ?: emptyList()
        } catch (e: SecurityException) {
            emptyList()
        } catch (e: IllegalArgumentException) {
            emptyList()
        }
    }

    private fun Cursor.toRow(keys: List<String>): MutableMap<String, Any> {
        val row = LinkedHashMap<String, Any>(keys.size)
        for (j in keys.indices) {
            val name = getColumnName(j)
            val value: Any = when (getType(j)) {
                Cursor.FIELD_TYPE_INTEGER -> getLong(j)
                Cursor.FIELD_TYPE_FLOAT -> getDouble(j)
                Cursor.FIELD_TYPE_BLOB -> getBlob(j)
                Cursor.FIELD_TYPE_NULL -> ""
                else -> getString(j) ?: ""
            }
            row[name] = value
        }
        return row
    }

    private fun putValue(cv: ContentValues, column: String, v: Any) {
        when (v) {
            null -> cv.putNull(column)
            is Long -> cv.put(column, v)
            is Int -> cv.put(column, v)
            is Double -> cv.put(column, v)
            is Float -> cv.put(column, v)
            is Boolean -> cv.put(column, v)
            is ByteArray -> cv.put(column, v)
            else -> cv.put(column, v.toString())
        }
    }

    companion object {
        private val CHANNEL_KEYS = listOf(
            "_id", "display_name", "internal_provider_id", "app_link_intent_uri", "type", "browsable"
        )
        private val CHANNEL_PROJECTION = arrayOf(
            Channels._ID, Channels.COLUMN_DISPLAY_NAME, Channels.COLUMN_INTERNAL_PROVIDER_ID,
            Channels.COLUMN_APP_LINK_INTENT_URI, Channels.COLUMN_TYPE, Channels.COLUMN_BROWSABLE
        )
        private val PROGRAM_KEYS = listOf(
            "_id", "channel_id", "weight", "type", "title", "short_description",
            "poster_art_uri", "poster_art_aspect_ratio", "duration_millis",
            "intent_uri", "internal_provider_id"
        )
        private val PROGRAM_PROJECTION = arrayOf(
            PreviewPrograms._ID, PreviewPrograms.COLUMN_CHANNEL_ID, PreviewPrograms.COLUMN_WEIGHT,
            PreviewPrograms.COLUMN_TYPE, PreviewPrograms.COLUMN_TITLE,
            PreviewPrograms.COLUMN_SHORT_DESCRIPTION, PreviewPrograms.COLUMN_POSTER_ART_URI,
            PreviewPrograms.COLUMN_POSTER_ART_ASPECT_RATIO, PreviewPrograms.COLUMN_DURATION_MILLIS,
            PreviewPrograms.COLUMN_INTENT_URI, PreviewPrograms.COLUMN_INTERNAL_PROVIDER_ID
        )
    }
}
