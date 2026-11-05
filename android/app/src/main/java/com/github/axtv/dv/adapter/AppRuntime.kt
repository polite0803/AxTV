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
        this.api = ApiAssembler.create(signer, cookieStore, device, settings.clientIdentityBranch())
    }

    fun shutdown() {
        signer?.close()
        signer = null
        settings = null
        api = null
    }
}
