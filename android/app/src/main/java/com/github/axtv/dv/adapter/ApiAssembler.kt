/*
 * Android 适配层 —— api：装配 DouyinApi。
 *
 * JsonCodec 由核心层 axtv.api.ModelJsonCodec 提供：基于 @JsonKey + FlexibleJson
 * 直接解码 axtv.model 的 record，不引入 Gson/Moshi/kotlinx.serialization。
 */
package com.github.axtv.dv.adapter

import axtv.api.ClientIdentityBranch
import axtv.api.CookieStore
import axtv.api.DeviceProfile
import axtv.api.DouyinApi
import axtv.api.ModelJsonCodec
import axtv.sign.AbogusSigner
import com.github.axtv.dv.api.OkHttpEngine

object ApiAssembler {

    fun create(signer: AbogusSigner, cookies: CookieStore, device: DeviceProfile): DouyinApi =
        create(signer, cookies, device, ClientIdentityBranch.WEB, true)

    /** 启动时快照设置里的客户端身份分支，本次进程内所有请求统一使用。 */
    fun create(
        signer: AbogusSigner,
        cookies: CookieStore,
        device: DeviceProfile,
        branch: ClientIdentityBranch,
        /** 是否自动补设备级匿名身份（ttwid）。应用启动时置 true，单测/离线请求请置 false。 */
        anonymousIdentityBootstrap: Boolean,
    ): DouyinApi =
        DouyinApi(
            OkHttpEngine(),
            AndroidSignProvider(signer),
            cookies,
            device,
            ModelJsonCodec(),
            branch,
            anonymousIdentityBootstrap,
        )
}
