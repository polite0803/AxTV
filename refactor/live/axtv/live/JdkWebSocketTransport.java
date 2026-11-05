/*
 * 来源: src/app/p000/cq2.java 构造 OkHttpClient 的参数
 *       （connectTimeout 10s / readTimeout 0 / writeTimeout 10s / pingInterval 0）
 * 原职责: 用 OkHttp 建 wss；pingInterval=0 表示禁用协议层 ping，心跳由应用层 hb 帧完成
 * 重构说明: 纯 JDK 实现，便于无 Android 环境跑通；Android 侧应换回 OkHttp 实现（见 NOTES「待迁移」）
 *           JDK WebSocket 没有 writeTimeout，未实现
 */
package axtv.live;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;

/**
 * 基于 {@link java.net.http.WebSocket} 的实现。
 *
 * <p>与原 OkHttp 配置的对应关系：connectTimeout=10s，readTimeout=0（无读超时），
 * writeTimeout=10s（JDK 无对应项），pingInterval=0。
 */
public final class JdkWebSocketTransport implements WebSocketTransport {

    /** 原 {@code connectTimeout(10, SECONDS)}。 */
    public static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);

    private static final int MAX_PENDING_SENDS = 16;

    private final HttpClient client;

    public JdkWebSocketTransport() {
        this(HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build());
    }

    public JdkWebSocketTransport(HttpClient client) {
        this.client = client;
    }

    public JdkWebSocketTransport(Executor executor) {
        this(HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER)
                .executor(executor)
                .build());
    }

    @Override
    public Handle open(String url, Map<String, String> headers, Listener listener) {
        JdkHandle handle = new JdkHandle(listener);
        WebSocket.Builder builder = client.newWebSocketBuilder().connectTimeout(CONNECT_TIMEOUT);
        if (headers != null) {
            for (Map.Entry<String, String> header : headers.entrySet()) {
                if (header.getKey() == null || header.getValue() == null) {
                    continue;
                }
                builder.header(header.getKey(), header.getValue());
            }
        }
        builder.buildAsync(URI.create(url), handle)
                .whenComplete((socket, error) -> {
                    if (error != null) {
                        handle.fail(error);
                    } else {
                        handle.attach(socket);
                    }
                });
        return handle;
    }

    /** 连接句柄：负责未就绪时的发送排队与回调转发。 */
    private static final class JdkHandle implements Handle, WebSocket.Listener {

        private final Listener listener;
        private final Deque<byte[]> pending = new ArrayDeque<>();
        private final ByteArrayOutputStream partial = new ByteArrayOutputStream();
        private volatile WebSocket socket;
        private volatile boolean closed;

        JdkHandle(Listener listener) {
            this.listener = listener;
        }

        void attach(WebSocket webSocket) {
            this.socket = webSocket;
            // JDK 在 onOpen 前不允许发送；把排队帧补发出去
            synchronized (pending) {
                while (!pending.isEmpty()) {
                    byte[] frame = pending.poll();
                    if (frame != null) {
                        webSocket.sendBinary(ByteBuffer.wrap(frame), true);
                    }
                }
            }
        }

        void fail(Throwable error) {
            if (closed) {
                return;
            }
            closed = true;
            listener.onFailure(error, -1, "");
        }

        @Override
        public boolean send(byte[] data) {
            if (closed) {
                return false;
            }
            WebSocket current = socket;
            if (current == null) {
                synchronized (pending) {
                    if (pending.size() >= MAX_PENDING_SENDS) {
                        return false;
                    }
                    pending.add(data.clone());
                }
                return true;
            }
            CompletableFuture<WebSocket> future = current.sendBinary(ByteBuffer.wrap(data), true);
            return !future.isCompletedExceptionally();
        }

        @Override
        public void close(int code, String reason) {
            WebSocket current = socket;
            closed = true;
            if (current != null) {
                current.sendClose(code, reason == null ? "" : reason);
            }
        }

        @Override
        public void abort() {
            WebSocket current = socket;
            closed = true;
            if (current != null) {
                current.abort();
            }
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            listener.onOpen(101, 0);
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
            byte[] chunk = new byte[data.remaining()];
            data.get(chunk);
            partial.write(chunk, 0, chunk.length);
            if (last) {
                byte[] frame = partial.toByteArray();
                partial.reset();
                listener.onBinary(frame);
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            listener.onText(data.toString());
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            closed = true;
            listener.onClosed(statusCode, reason == null ? "" : reason);
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            closed = true;
            listener.onFailure(error == null ? new IOException("websocket error") : error, -1, "");
        }
    }
}
