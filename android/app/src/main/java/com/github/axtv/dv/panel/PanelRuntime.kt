/*
 * Android 适配层 —— 局域网面板（9978 Cookie 推送 / 9979 搜索页 / 9980 WebSocket）。
 *
 * 核心层 PanelController 通过本文件注入 Android 侧实现：
 *  - 传输层：AndroidPanelServer（原生 ServerSocket，android.jar 无 com.sun.net.httpserver）
 *  - 凭据存储：douyin_auth（与 SharedPrefsCookieStore / 登录页共用同一份）
 *  - 页面资源：assets 下的 web 目录
 *  - 设备名：Build.BRAND / Build.MODEL
 *
 * 所有请求都要求 PanelAuth 令牌；手机通过电视设置页里的二维码（带 token 的地址）接入。
 */
package com.github.axtv.dv.panel

import android.content.Context
import android.os.Build
import axtv.panel.AuthStore
import axtv.panel.DeviceAddressResolver
import axtv.panel.InteractionSecurityConfig
import axtv.panel.LogcatExportHandler
import axtv.panel.PanelAuth
import axtv.panel.PanelController
import axtv.panel.PanelServer
import axtv.panel.PanelState
import axtv.panel.ThemeRenderer
import axtv.settings.Settings
import com.github.axtv.dv.adapter.AppRuntime

/** 面板凭据存储：与 App 其它模块共用 douyin_auth，并同步 axtv_settings 的登录态。 */
private class AndroidAuthStore(private val context: Context) : AuthStore {

    private val prefs = context.applicationContext
        .getSharedPreferences("douyin_auth", Context.MODE_PRIVATE)

    override fun cookie(): String = prefs.getString(KEY_COOKIE_STRING, "").orEmpty()

    override fun setCookie(cookie: String) {
        val value = cookie ?: ""
        prefs.edit()
            .putString(KEY_COOKIE_STRING, value)
            .putString(KEY_COOKIE_HEADER, value)
            .apply()
        AppRuntime.settings?.setCookie(value)
    }

    override fun interactionSecurityPayload(): String =
        prefs.getString(KEY_SECURITY_PAYLOAD, "").orEmpty()

    override fun interactionPrivateKey(): String =
        prefs.getString(KEY_PRIVATE_KEY, "").orEmpty()

    override fun interactionSecurity(): InteractionSecurityConfig = InteractionSecurityConfig(
        interactionSecurityPayload(),
        prefs.getString(KEY_TICKET, "").orEmpty(),
        prefs.getString(KEY_TS_SIGN, "").orEmpty(),
        interactionPrivateKey(),
        prefs.getString(KEY_SERVER_CERT, "").orEmpty(),
    )

    override fun setInteractionSecurity(rawPayload: String, config: InteractionSecurityConfig) {
        val payload = rawPayload ?: ""
        prefs.edit()
            .putString(KEY_SECURITY_PAYLOAD, payload)
            .putString(KEY_TICKET, config.ticket())
            .putString(KEY_TS_SIGN, config.tsSign())
            .putString(KEY_PRIVATE_KEY, config.privateKey())
            .putString(KEY_SERVER_CERT, config.serverCert())
            .apply()
        // 设置页/播放器读的是 axtv_settings 里的互动凭据，这里同步一份。
        AppRuntime.settings?.setInteraction(
            payload,
            Settings.Interaction(
                payload,
                config.ticket(),
                config.tsSign(),
                config.privateKey(),
                config.serverCert(),
            ),
        )
    }

    private companion object {
        const val KEY_COOKIE_STRING = "cookie_string"
        const val KEY_COOKIE_HEADER = "cookie_header"
        const val KEY_SECURITY_PAYLOAD = "interaction_security_payload"
        const val KEY_TICKET = "interaction_ticket"
        const val KEY_TS_SIGN = "interaction_ts_sign"
        const val KEY_PRIVATE_KEY = "interaction_private_key"
        const val KEY_SERVER_CERT = "interaction_server_cert"
    }
}

/** 读取 assets 下 web 目录中的 HTML 模板。 */
private class AndroidPanelAssets(context: Context) : ThemeRenderer.AssetSource {
    private val assets = context.applicationContext.assets
    override fun read(path: String): ByteArray = assets.open(path).use { it.readBytes() }
}

private object AndroidDeviceIdentity : DeviceAddressResolver.DeviceIdentity {
    override fun brand(): String = Build.BRAND.orEmpty()
    override fun model(): String = Build.MODEL.orEmpty()
}

object PanelRuntime {

    @Volatile
    var controller: PanelController? = null
        private set

    /** 启动面板服务；重复调用无副作用。 */
    fun install(context: Context) {
        if (controller != null) return
        val app = context.applicationContext
        val panel = PanelController(
            PanelState("PanelState"),
            AndroidAuthStore(app),
            AndroidPanelAssets(app),
            ThemeRenderer.defaultColors(),
            AndroidDeviceIdentity,
            LogcatExportHandler.ProcessLogcat(),
            PanelAuth.random(),
            PanelServer.TransportFactory { options -> AndroidPanelServer(options) },
        )
        controller = panel
        // 监听 socket 不适合放在 Application.onCreate 主线程：后台线程启动，失败只记日志。
        Thread({ runCatching { panel.startAll() } }, "PanelBootstrap").apply {
            isDaemon = true
            start()
        }
    }

    fun shutdown() {
        controller?.close()
        controller = null
    }
}
