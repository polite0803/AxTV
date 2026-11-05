/*
 * Android 适配层 —— settings 模块：HttpFetcher 的 OkHttp 实现。
 *
 * 核心层 UpdateManager 原本直接用 java.net.http.HttpClient，但 Android 的 android.jar
 * 不含该包（AGP 直接编译报 100 个错误）。故核心层抽出 HttpFetcher 接口，
 * 此处用 OkHttp 实现（android.jar 外的依赖，AGP 正常支持）。
 *
 * 行为对应（原 UpdateManager 两处 HTTP 调用）：
 *  - getText:  GET + Accept 头 + 10s 超时 → 状态码/正文/Content-Length
 *  - getStream: GET + User-Agent 头 → 状态码/响应流/Content-Length
 * 规格出处: settings/NOTES.md 更新时序；ADAPTERS.md §6
 */
package com.github.axtv.dv.settings

import axtv.settings.HttpFetcher
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

class OkHttpHttpFetcher(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
) : HttpFetcher {

    override fun getText(url: String, acceptHeader: String?, timeoutSeconds: Int): HttpFetcher.TextResponse {
        val builder = Request.Builder().url(url).get()
        if (!acceptHeader.isNullOrEmpty()) builder.header("Accept", acceptHeader)
        client.newCall(builder.build()).execute().use { resp ->
            val body = resp.body?.string() ?: ""
            val len = resp.header("Content-Length")?.toLongOrNull() ?: -1L
            return HttpFetcher.TextResponse(resp.code, body, len)
        }
    }

    override fun getStream(url: String, userAgent: String?): HttpFetcher.StreamResponse {
        val builder = Request.Builder().url(url).get()
        if (!userAgent.isNullOrEmpty()) builder.header("User-Agent", userAgent)
        val resp = client.newCall(builder.build()).execute()
        val len = resp.header("Content-Length")?.toLongOrNull() ?: -1L
        return HttpFetcher.StreamResponse(resp.code, resp.body?.byteStream(), len)
    }
}
