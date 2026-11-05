/*
 * Android 侧：把核心层 refactor/settings/axtv/settings/UpdateManager.java 的 6 个接口
 * 落到本机实现，供设置页「检查更新」对话框使用（不另写下载器，全部走 UpdateManager）。
 *
 * 已存在的能力（直接复用，不重复实现）：
 *  - HTTP：com.github.axtv.dv.settings.OkHttpHttpFetcher（core 默认的 HttpFetcher.jdk() 在 Android 不可用）
 *  - 镜像回退：axtv.settings.UpdateMirror
 *  - 版本比较 / SHA-256 / 签名校验 / 下载：UpdateManager 内部
 *  - 安装文件共享：AndroidManifest 已声明 androidx.core.content.FileProvider
 *    （authorities = ${applicationId}.fileprovider，res/xml/file_paths.xml 已含 files-path "updates"）
 */
package com.github.axtv.dv.ui.settings

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.net.Uri
import androidx.core.content.FileProvider
import axtv.settings.ReleaseJson
import axtv.settings.UpdateManager
import axtv.settings.UpdateMirror
import com.github.axtv.dv.settings.OkHttpHttpFetcher
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.Locale

/** 组装 UpdateManager：HTTP / 应用信息 / APK 签名 / 安装器 / Release JSON 全部注入本机实现。 */
internal fun createUpdateManager(context: Context, listener: UpdateManager.Listener): UpdateManager {
    val app = context.applicationContext
    return UpdateManager(
        app.filesDir,
        AndroidAppInfo(app),
        UpdateMirror(),
        AndroidApkInspector(app),
        FileProviderApkInstaller(app),
        OrgJsonReleaseJson(),
        listener,
        OkHttpHttpFetcher(),
    )
}

/** 本机 versionName + 签名指纹（与服务端 Release 的包签名比对）。 */
private class AndroidAppInfo(private val context: Context) : UpdateManager.AppInfo {

    override fun versionName(): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    }.getOrDefault("")

    @Suppress("DEPRECATION")
    override fun signerSha256(): String? = try {
        val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
        signatureFingerprint(info.signatures)
    } catch (e: Exception) {
        null
    }
}

/** 下载完成后读更新包自身签名（UpdateManager.verify 会与本机签名比对）。 */
private class AndroidApkInspector(private val context: Context) : UpdateManager.ApkInspector {

    @Suppress("DEPRECATION")
    override fun signerSha256(apk: File): String? = try {
        val info = context.packageManager.getPackageArchiveInfo(apk.absolutePath, PackageManager.GET_SIGNATURES)
        signatureFingerprint(info?.signatures)
    } catch (e: Exception) {
        null
    }
}

/**
 * 唤起系统安装器。
 *
 * UpdateManager 已把 APK 下载到 filesDir/updates/AxTV_update.apk；
 * 通过已声明的 FileProvider 暴露 content:// URI，避免 targetSdk 35 下 file:// 触发
 * FileUriExposedException。安装未知应用权限由 ActionView 的系统流程提示。
 */
private class FileProviderApkInstaller(private val context: Context) : UpdateManager.ApkInstaller {

    override fun install(apk: File): Boolean = try {
        val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    } catch (e: Exception) {
        false
    }
}

/**
 * GitHub Release JSON 解析（字段与 SimpleReleaseJson 一致）：
 * tag_name / body / assets[].name / browser_download_url / digest。
 * 用 Android 内置 org.json；任何解析异常抛 RuntimeException，由 UpdateManager 归为 CHECK_FAILED。
 */
private class OrgJsonReleaseJson : ReleaseJson {

    override fun parse(body: String): UpdateManager.ReleaseInfo {
        val root = JSONObject(body)
        val tag = root.optString("tag_name").takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("missing tag_name")
        val updateLog = root.optString("body").takeIf { it.isNotBlank() } ?: "没有提供更新日志"
        val assets = root.optJSONArray("assets") ?: throw IllegalArgumentException("missing assets")
        var downloadUrl: String? = null
        var sha256: String? = null
        for (index in 0 until assets.length()) {
            val asset = assets.optJSONObject(index) ?: continue
            val name = asset.optString("name")
            if (!name.endsWith(".apk")) continue
            downloadUrl = asset.optString("browser_download_url").takeIf { it.isNotBlank() }
            val raw = asset.optString("digest")
            val stripped = (if (raw.startsWith("sha256:")) raw.substring("sha256:".length) else raw)
                .lowercase(Locale.ROOT)
            sha256 = stripped.takeIf { it.length == 64 }
            break
        }
        return UpdateManager.ReleaseInfo(tag, updateLog, downloadUrl, sha256, null)
    }
}

/** 证书签名 → 指纹（公钥 SHA-256 小写十六进制，多个签名用 "," 连接；读不到返回 null）。 */
private fun signatureFingerprint(signatures: Array<Signature>?): String? {
    if (signatures == null || signatures.isEmpty()) return null
    return signatures.joinToString(",") { signature ->
        MessageDigest.getInstance("SHA-256")
            .digest(signature.toByteArray())
            .joinToString("") { byte -> "%02x".format(byte) }
    }
}
