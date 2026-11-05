/*
 * Android 适配层 —— widget 模块：封面本地缓存。
 *
 * RemoteViews 不能直接加载网络图，因此刷新时把封面/头像下载到应用私有目录；
 * 渲染时由本进程解码成缩略位图，用 setImageViewBitmap 交付桌面。
 *
 * 为何不用 content:// + setImageViewUri：
 *   res/xml/file_paths.xml 的 files-path="." 确实覆盖 filesDir/widget_covers（路径层没问题），
 *   但 FileProvider 是 exported=false，桌面（launcher）能否打开依赖向 HOME 包授予 URI 权限；
 *   Android 11+ 包可见性下 queryIntentActivities(HOME) 可能返回空（本应用未声明 <queries>，
 *   且任务边界禁改 manifest），授权失败又是静默的——无法判定 launcher 一定读得到。
 *   位图随 RemoteViews 一起跨进程传输，不依赖 URI 授权，因此作为唯一路径。
 *
 * 目录仍是 filesDir/widget_covers（file_paths 已覆盖，若将来回到 URI 方案无需再动 res），
 * 带 LRU 上限（文件数 + 总字节），不会无限增长。
 */
package com.github.axtv.dv.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.util.LruCache
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request

internal object WidgetCoverCache {

    private const val TAG = "WidgetCover"
    private const val DIR_NAME = "widget_covers"
    private const val MAX_FILES = 64
    private const val MAX_TOTAL_BYTES = 24L * 1024L * 1024L
    private const val MAX_IMAGE_BYTES = 4L * 1024L * 1024L
    private const val MAX_URLS_PER_REFRESH = 12
    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    /** 缩略图最长边；单张 RGB_565 约 70KB，单个组件最多 4 张，远低于 RemoteViews 的 binder 限制。 */
    private const val MAX_BITMAP_SIDE = 192

    /** 渲染进程内的位图缓存，避免每次 RemoteViews 更新都重新解码。 */
    private val bitmaps = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
    }

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    /**
     * 预下载（在 Worker 的后台线程调用）。逐个串行下载，避免刷新时同时打满网络。
     * 单个失败不影响其它图片，也不会让整次刷新失败。
     */
    fun prefetch(context: Context, urls: List<String>) {
        val targets = urls.asSequence()
            .map(::normalizeUrl)
            .filter { it.isNotEmpty() }
            .distinct()
            .take(MAX_URLS_PER_REFRESH)
            .filter { !fileFor(context, it).isFile }
            .toList()
        if (targets.isEmpty()) {
            prune(context)
            return
        }
        val dir = cacheDir(context)
        if (!dir.isDirectory && !dir.mkdirs()) return
        targets.forEach { url ->
            runCatching { download(dir, url) }
                .onFailure { Log.w(TAG, "cover download failed: ${it.javaClass.simpleName}") }
        }
        prune(context)
    }

    /**
     * 渲染时解码本地文件为缩略位图；文件不存在/解码失败返回 null（由布局保持纯色占位）。
     * 位图随 RemoteViews 跨进程传输，launcher 一定能读到。
     */
    fun bitmapFor(context: Context, url: String): Bitmap? {
        val normalized = normalizeUrl(url)
        if (normalized.isEmpty()) return null
        bitmaps.get(normalized)?.let { return it }
        val file = fileFor(context, normalized)
        if (!file.isFile || file.length() <= 0L) return null

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val maxSide = maxOf(bounds.outWidth, bounds.outHeight)
        if (maxSide <= 0) return null
        var sample = 1
        while (maxSide / (sample * 2) >= MAX_BITMAP_SIDE) sample *= 2

        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        val bitmap = runCatching {
            BitmapFactory.decodeFile(file.absolutePath, options)
        }.getOrNull() ?: return null
        file.setLastModified(System.currentTimeMillis())
        bitmaps.put(normalized, bitmap)
        return bitmap
    }

    // ---------------------------------------------------------------- 下载

    private fun download(dir: File, url: String) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return
            val body = response.body ?: return
            val declared = body.contentLength()
            if (declared > MAX_IMAGE_BYTES) return
            val bytes = body.bytes()
            if (bytes.isEmpty() || bytes.size.toLong() > MAX_IMAGE_BYTES) return
            val target = File(dir, fileName(url))
            val temp = File(dir, target.name + ".tmp")
            temp.writeBytes(bytes)
            if (temp.renameTo(target)) {
                target.setLastModified(System.currentTimeMillis())
            } else {
                temp.delete()
            }
        }
    }

    // ---------------------------------------------------------------- 目录 / LRU

    private fun cacheDir(context: Context): File = File(context.filesDir, DIR_NAME)

    private fun fileFor(context: Context, url: String): File =
        File(cacheDir(context), fileName(url))

    private fun fileName(url: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(url.toByteArray())
        val hex = StringBuilder(digest.size * 2)
        digest.forEach { hex.append(String.format("%02x", it)) }
        return hex.toString() + ".jpg"
    }

    /** 简单 LRU：按最近访问时间从旧到新删，直到文件数 / 总字节都回到上限内。 */
    private fun prune(context: Context) {
        val dir = cacheDir(context)
        val files = dir.listFiles()?.filter { it.isFile }?.sortedBy { it.lastModified() } ?: return
        var count = files.size
        var total = files.sumOf { it.length() }
        for (file in files) {
            if (count <= MAX_FILES && total <= MAX_TOTAL_BYTES) break
            val length = file.length()
            if (file.delete()) {
                count--
                total -= length
            }
        }
    }

    private fun normalizeUrl(url: String): String {
        val trimmed = url.trim()
        return when {
            trimmed.isEmpty() -> ""
            trimmed.startsWith("//") -> "https:$trimmed"
            else -> trimmed
        }
    }
}
