/*
 * Android 适配层 —— sign/api/settings 的装配入口。
 *
 * 由 MyApplication.onCreate 调用一次；UI 通过 AppRuntime.settings 读写配置。
 * signer 在进程退出时关闭（AbogusSigner.close() 内部等待 JS 线程销毁）。
 */
package com.github.axtv.dv.adapter

import android.content.Context
import axtv.api.DouyinApi
import axtv.settings.Settings
import axtv.sign.AbogusSigner
import com.github.axtv.dv.api.SharedPrefsCookieStore
import com.github.axtv.dv.panel.PanelRuntime
import com.github.axtv.dv.settings.SharedPrefsSettingsStore
import com.github.axtv.dv.sign.AndroidAssets
import com.github.axtv.dv.sign.AndroidSignLog

object AppRuntime {

    @Volatile
    var settings: Settings? = null
        private set

    @Volatile
    var signer: AbogusSigner? = null
        private set

    @Volatile
    var api: DouyinApi? = null
        private set

    fun install(context: Context) {
        if (signer != null) return
        val application = context.applicationContext
        val device = AndroidDeviceProfile.fromBuild()
        val settingsDevice = AndroidDeviceProfile.settingsProfile(application)
        val cookieStore = SharedPrefsCookieStore(application)
        val signer = AbogusSigner(AndroidAssets(application), QuickJsRuntimeFactoryAdapter, AndroidSignLog)
        this.signer = signer
        val settings = Settings(SharedPrefsSettingsStore(application), settingsDevice)
        this.settings = settings
        // 启动时对账：运行时 CookieStore 没有 sessionid、但设置里保存了登录态时补写一次。
        // 历史版本/设置导入可能只写了 axtv_settings，请求读的却是 douyin_auth，
        // 表现为「设置里显示已登录，搜索始终为空」。运行时已有登录态时不覆盖，
        // 避免丢掉请求过程中新下发的设备 cookie。
        if (!Settings.hasSessionId(cookieStore.cookieHeader()) && settings.isLoggedIn) {
            cookieStore.setCookieHeader(settings.cookie())
        }
        // 未登录也要能取到公开数据：装配时开启设备级匿名身份（ttwid）引导。
        this.api = ApiAssembler.create(
            signer,
            cookieStore,
            device,
            settings.clientIdentityBranch(),
            anonymousIdentityBootstrap = true,
        )
        // 局域网面板（9978/9979/9980）依赖 settings 与 douyin_auth，放在最后启动。
        PanelRuntime.install(application)
    }

    /**
     * 导入配置后重建 Settings 实例：核心层把 accounts_list 缓存在实例字段里
     * （refactor/settings/axtv/settings/Settings.java:55 accountsCache），
     * 沿用旧实例会读到导入前的账号表。api / signer 不重建（身份分支只在装配时读一次）。
     */
    fun refreshSettings(context: Context) {
        val application = context.applicationContext
        settings = Settings(
            SharedPrefsSettingsStore(application),
            AndroidDeviceProfile.settingsProfile(application),
        )
    }

    /**
     * 把设置仓库里的 Cookie 同步进运行时 CookieStore（douyin_auth/cookie_header）。
     *
     * 缺口来源：请求实际读的是 SharedPrefsCookieStore（见 install()），而「设置导入」只写
     * axtv_settings（SettingsConfigIO 把 COOKIE_STRING 列为敏感键跳过），两处从未接线
     * （LibraryCommon.kt:75）。于是设置里显示「已登录」，但搜索请求不带 sessionid，服务端回空。
     * 导入或保存 Cookie 后调用本方法，与登录页的双写保持一致。
     */
    fun syncCookieToRuntimeStore(context: Context) {
        val application = context.applicationContext
        val cookie = settings?.cookie().orEmpty()
        SharedPrefsCookieStore(application).setCookieHeader(cookie)
    }

    fun shutdown() {
        PanelRuntime.shutdown()
        signer?.close()
        signer = null
        settings = null
        api = null
    }
}
