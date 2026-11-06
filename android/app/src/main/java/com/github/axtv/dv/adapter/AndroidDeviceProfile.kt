/*
 * Android 适配层 —— api：设备信息。
 * 规格出处: DeviceProfile.java 头注释（Build.MODEL / VERSION.RELEASE / SDK_INT 由调用方注入）。
 */
package com.github.axtv.dv.adapter

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import axtv.api.DeviceProfile
import axtv.settings.Settings
import axtv.settings.SettingsTypes

object AndroidDeviceProfile {

    /** api 层设备信息（移动端 UA / device_type / os_api）。 */
    fun fromBuild(): DeviceProfile =
        DeviceProfile(Build.VERSION.RELEASE, Build.MODEL, Build.VERSION.SDK_INT)

    /** settings 层 TV 判定（原 sp0.f20095a：UI_MODE_TYPE_TELEVISION 或 FEATURE_LEANBACK）。 */
    fun settingsProfile(context: Context): SettingsTypes.DeviceProfile {
        val packageManager = context.packageManager
        val configuration = context.resources.configuration
        val isTelevision =
            (configuration.uiMode and Configuration.UI_MODE_TYPE_MASK) ==
                Configuration.UI_MODE_TYPE_TELEVISION ||
                packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
        return SettingsTypes.DeviceProfile.of(Build.VERSION.SDK_INT, isTelevision)
    }
}
