/*
 * Android 适配层 —— live 模块：WebSocket 传输（OkHttp）+ 日志桥。
 * 规格出处: ADAPTERS.md §4 / live/NOTES.md 帧/心跳/重连表（重连退避在核心层 LiveWebSocketClient）
 * 行为: open 逐头透传；onBinary 走 ByteString；onClosing 后由核心层决定确认关闭。
 */
package com.github.axtv.dv.live

import android.util.Log
import axtv.live.LiveLog
import axtv.live.WebSocketTransport
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString

object AndroidLiveLog : LiveLog {
    override fun log(level: LiveLog.Level, tag: String, message: String, error: Throwable?) {
        when (level) {
            LiveLog.Level.DEBUG -> Log.d(tag, message, error)
            LiveLog.Level.INFO -> Log.i(tag, message, error)
            LiveLog.Level.WARN -> Log.w(tag, message, error)
            LiveLog.Level.ERROR -> Log.e(tag, message, error)
        }
    }
}

class OkHttpWebSocket(private val client: OkHttpClient = OkHttpClient()) : WebSocketTransport {

    override fun open(url: String, headers: Map<String, String>, listener: WebSocketTransport.Listener): WebSocketTransport.Handle {
        val builder = Request.Builder().url(url)
        for ((k, v) in headers) builder.header(k, v)

        val ws = client.newWebSocket(builder.build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                listener.onOpen(response.code, response.headers.size)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                listener.onBinary(bytes.toByteArray())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                listener.onText(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                listener.onClosing(code, reason)
                webSocket.close(code, reason)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                listener.onClosed(code, reason)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                listener.onFailure(t, response?.code ?: -1, response?.message ?: "")
            }
        })

        return object : WebSocketTransport.Handle {
            override fun send(data: ByteArray): Boolean = ws.send(data.toByteString())
            override fun close(code: Int, reason: String) {
                ws.close(code, reason)
            }
            override fun abort() {
                ws.cancel()
            }
        }
    }
}
