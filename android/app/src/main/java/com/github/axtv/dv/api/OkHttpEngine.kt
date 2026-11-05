/*
 * Android 适配层 —— api 模块：HTTP 引擎（OkHttp 4.x）。
 * 规格出处: ADAPTERS.md §2 / api/NOTES.md HttpEngine 章节常量表
 * 行为: method/url/headers/body/contentType 逐参直通；followRedirects 用派生 client。
 */
package com.github.axtv.dv.api

import axtv.api.ApiResponse
import axtv.api.HttpEngine
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class OkHttpEngine(baseClient: OkHttpClient = defaultClient()) : HttpEngine {

    private val followClient = baseClient.newBuilder()
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val noFollowClient = baseClient.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    override fun execute(
        method: String,
        url: String,
        headers: Map<String, String>,
        body: ByteArray?,
        contentType: String?,
        followRedirects: Boolean,
    ): ApiResponse {
        val client = if (followRedirects) followClient else noFollowClient
        val builder = Request.Builder().url(url)
        for ((k, v) in headers) builder.header(k, v)

        val m = method.uppercase()
        val mediaType = contentType?.toMediaTypeOrNull()
        when (m) {
            "GET" -> builder.get()
            "HEAD" -> builder.head()
            "DELETE" -> if (body != null) builder.delete(body.toRequestBody(mediaType)) else builder.delete()
            "POST" -> builder.post((body ?: ByteArray(0)).toRequestBody(mediaType))
            "PUT" -> builder.put((body ?: ByteArray(0)).toRequestBody(mediaType))
            "PATCH" -> builder.patch((body ?: ByteArray(0)).toRequestBody(mediaType))
            else -> throw IOException("Unsupported method: $method")
        }
        client.newCall(builder.build()).execute().use { resp ->
            val listHeaders = resp.headers.toMultimap()
            return ApiResponse(resp.code, resp.body?.bytes(), listHeaders)
        }
    }

    companion object {
        /** 规格：连接/读/写各 30s（api/NOTES.md HttpEngine 常量表）。 */
        private const val TIMEOUT_SECONDS = 30L

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }
}
