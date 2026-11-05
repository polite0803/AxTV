/*
 * Android 适配层 —— panel 模块：HttpServerCore 的原生 ServerSocket 实现。
 *
 * 核心层 PanelServer 原本用 com.sun.net.httpserver.HttpServer，但 Android 的 android.jar
 * 不含该包。故核心层抽出 HttpServerCore 接口（传输层），
 * 此处用原生 ServerSocket 实现 —— 行为等价：
 *  - start() 绑定端口并开始接受连接（port=0 由系统分配，boundPort 可读实际端口）；
 *  - 每连接一请求：解析请求行/头/体 → PanelRoutes.dispatch → 写响应；
 *  - 请求体按 Content-Length 读满，上限 4 MiB（与核心层 MAX_BODY_BYTES 一致）；
 *  - 环回/任意本地地址统一记为 127.0.0.1；
 *  - stop() 关监听 socket + 中断工作线程。
 *
 * 规格出处: panel/NOTES.md 路由表（9978 Cookie 推送 / 9979 搜索面板 / 9980 帧服务）；
 *           ADAPTERS.md §5
 */
package com.github.axtv.dv.panel

import android.util.Log
import axtv.panel.HttpServerCore
import axtv.panel.PanelRoutes
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class AndroidPanelServer(
    private val options: HttpServerCore.Options
) : HttpServerCore {

    private val lock = Object()
    private var serverSocket: ServerSocket? = null
    private var executor: ExecutorService? = null
    private var acceptThread: Thread? = null
    @Volatile private var running = false
    @Volatile private var bound = -1

    override fun start() {
        synchronized(lock) {
            if (running) return
            val socket = ServerSocket()
            socket.reuseAddress = true
            socket.bind(InetSocketAddress(options.port()), 0)
            bound = socket.localPort
            serverSocket = socket
            executor = Executors.newFixedThreadPool(options.workerThreads()) { r ->
                Thread(r, "${options.tag()}-http-${counter.incrementAndGet()}").apply {
                    isDaemon = options.daemon()
                }
            }
            running = true
            acceptThread = Thread({ acceptLoop(socket) }, "${options.tag()}-accept").apply {
                isDaemon = options.daemon()
                start()
            }
        }
    }

    private fun acceptLoop(socket: ServerSocket) {
        while (running) {
            try {
                val client = socket.accept()
                val pool = executor
                if (pool == null) { client.close(); continue }
                pool.execute { handle(client) }
            } catch (e: Exception) {
                if (running) Log.w(options.tag(), "accept 失败", e)
                if (!running) break
            }
        }
    }

    private fun handle(client: Socket) {
        client.use { sock ->
            try {
                sock.soTimeout = 15_000
                val reader = BufferedReader(InputStreamReader(sock.getInputStream(), StandardCharsets.ISO_8859_1))
                val requestLine = reader.readLine() ?: return
                val parts = requestLine.split(' ')
                if (parts.size < 2) return
                val method = parts[0].uppercase(Locale.ROOT)
                val target = parts[1]

                val headers = LinkedHashMap<String, String>()
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isEmpty()) break
                    val idx = line.indexOf(':')
                    if (idx <= 0) continue
                    headers[line.substring(0, idx).trim().lowercase(Locale.ROOT)] =
                        line.substring(idx + 1).trim()
                }

                val contentLength = headers["content-length"]?.trim()?.toIntOrNull() ?: 0
                val body = if (method == "GET" || method == "HEAD") {
                    ByteArray(0)
                } else {
                    readBody(reader, contentLength)
                }

                val qIdx = target.indexOf('?')
                val path = if (qIdx >= 0) target.substring(0, qIdx) else target
                val query = if (qIdx >= 0) target.substring(qIdx + 1) else null

                val request = PanelRoutes.Request(
                    method, path, query, headers, body, remoteAddress(sock)
                )
                val response = options.routes().dispatch(request)
                write(sock, response, method)
            } catch (e: Exception) {
                Log.w(options.tag(), "请求处理失败", e)
            }
        }
    }

    private fun readBody(reader: BufferedReader, contentLength: Int): ByteArray {
        if (contentLength <= 0) return ByteArray(0)
        val limit = minOf(contentLength, MAX_BODY_BYTES)
        val buf = ByteArray(limit)
        var read = 0
        // BufferedReader 已缓冲，直接用其底层流读更精确；这里用 reader.read() 逐字节
        while (read < limit) {
            val c = reader.read()
            if (c < 0) break
            buf[read++] = c.toByte()
        }
        return if (read == limit) buf else buf.copyOf(read)
    }

    private fun write(sock: Socket, response: PanelRoutes.Response, method: String) {
        val out: OutputStream = sock.getOutputStream()
        val body = response.body() ?: ByteArray(0)
        val noBody = response.status() == 204 || response.status() == 304 || method == "HEAD"
        val sb = StringBuilder()
        sb.append("HTTP/1.1 ").append(response.status()).append(' ').append(reason(response.status())).append("\r\n")
        response.contentType()?.let { sb.append("Content-Type: ").append(it).append("\r\n") }
        response.headers().forEach { (k, v) -> sb.append(k).append(": ").append(v).append("\r\n") }
        sb.append("Content-Length: ").append(if (noBody) 0 else body.size).append("\r\n")
        sb.append("Connection: close\r\n\r\n")
        out.write(sb.toString().toByteArray(StandardCharsets.ISO_8859_1))
        if (!noBody && body.isNotEmpty()) out.write(body)
        out.flush()
    }

    private fun reason(status: Int): String = when (status) {
        200 -> "OK"; 204 -> "No Content"; 301 -> "Moved Permanently"; 302 -> "Found"
        304 -> "Not Modified"; 400 -> "Bad Request"; 401 -> "Unauthorized"; 403 -> "Forbidden"
        404 -> "Not Found"; 405 -> "Method Not Allowed"; 500 -> "Internal Server Error"
        502 -> "Bad Gateway"; 503 -> "Service Unavailable"
        else -> "Status"
    }

    /** 原 e63 构造里把环回/任意本地地址统一记为 127.0.0.1。 */
    private fun remoteAddress(sock: Socket): String {
        val addr = sock.inetAddress ?: return "127.0.0.1"
        return if (addr.isLoopbackAddress || addr.isAnyLocalAddress) "127.0.0.1" else addr.hostAddress
    }

    override fun stop() {
        synchronized(lock) {
            running = false
            try { serverSocket?.close() } catch (_: Exception) {}
            serverSocket = null
            executor?.let {
                it.shutdownNow()
                try {
                    if (!it.awaitTermination(2, TimeUnit.SECONDS)) {
                        Log.w(options.tag(), "处理线程未在 2 秒内退出")
                    }
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
            }
            executor = null
            acceptThread = null
        }
    }

    override fun boundPort(): Int = bound

    override fun close() = stop()

    companion object {
        private const val MAX_BODY_BYTES = 4 * 1024 * 1024
        private val counter = AtomicInteger()
    }
}
