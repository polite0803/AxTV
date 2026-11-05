/*
 * Android 适配层 —— api：装配 DouyinApi。
 *
 * JsonCodec 本轮不实现：api/NOTES.md §5 明确其依赖 refactor/model 的序列化重构，
 * 这里显式抛 UnsupportedOperationException，不返回空实现伪装成功。
 */
package com.github.axtv.dv.adapter

import axtv.api.CookieStore
import axtv.api.DeviceProfile
import axtv.api.DouyinApi
import axtv.api.JsonCodec
import axtv.sign.AbogusSigner
import com.github.axtv.dv.api.OkHttpEngine

object ApiAssembler {

    fun create(signer: AbogusSigner, cookies: CookieStore, device: DeviceProfile): DouyinApi =
        DouyinApi(
            OkHttpEngine(),
            AndroidSignProvider(signer),
            cookies,
            device,
            UnimplementedJsonCodec,
        )
}

private object UnimplementedJsonCodec : JsonCodec {
    override fun <T : Any?> decode(json: String, type: Class<T>): T =
        throw UnsupportedOperationException(
            "JsonCodec 未实现：refactor/model 的序列化尚未重构（api/NOTES.md §5），本轮不伪装成功",
        )
}
