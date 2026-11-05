/*
 * 来源: src/app/p000/cq2.java + bq2.java 里对 okhttp3.WebSocket / OkHttpClient.newWebSocket 的使用
 * 原职责: 建立 wss 长连接、发送二进制帧、接收回调
 * 重构说明: 核心层不绑定 OkHttp；抽出 WebSocketTransport（接口）+ Handle（连接句柄）。
 *           Android 侧应基于 OkHttp 实现，JVM 侧见 {@link JdkWebSocketTransport}。
 *           注意：原实现把「消息大于阈值」的重活丢到 IO 协程，这里由实现方决定回调线程，
 *           调用方（LiveWebSocketClient）内部自行串行化。
 */
package axtv.live;

import java.util.Map;

/** WebSocket 传输抽象。 */
public interface WebSocketTransport {

    /** 打开连接。返回的句柄可立即用于 send（实现可自行排队直到 onOpen）。 */
    Handle open(String url, Map<String, String> headers, Listener listener);

    /** 回调。除 onOpen 外都可能在同一线程连续触发。 */
    interface Listener {

        /** 握手成功。{@code headerCount} 对应原日志里的 responseHeaders 数量。 */
        void onOpen(int statusCode, int headerCount);

        void onBinary(byte[] data);

        void onText(String text);

        /** 收到对端 close 帧（握手的对端已开始关闭）。 */
        void onClosing(int code, String reason);

        /** 连接已关闭（本地或对端）。 */
        void onClosed(int code, String reason);

        /**
         * 连接失败。
         *
         * @param httpStatus 握手失败时的 HTTP 状态码，未知为 -1
         * @param body       握手失败时响应体的前 512 字节（原实现 {@code peekBody(512)}），未知为 ""
         */
        void onFailure(Throwable error, int httpStatus, String body);
    }

    /** 连接句柄。 */
    interface Handle {

        /** 发送一帧；连接未就绪或已关闭时返回 false。 */
        boolean send(byte[] data);

        /** 主动关闭（1000 / null reason，与原实现一致）。 */
        void close(int code, String reason);

        /** 立即放弃连接（不等待 close 握手）。 */
        void abort();
    }
}
