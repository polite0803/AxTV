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
        // 未登录也要能取到公开数据：装配时开启设备级匿名身份（ttwid）引导。
        this.api = ApiAssembler.create(
            signer,
            cookieStore,
            device,
            settings.clientIdentityBranch(),
            anonymousIdentityBootstrap = true,
        )
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

    fun shutdown() {
        signer?.close()
        signer = null
        settings = null
        api = null
    }
}
