/*
 * 来源: src/app/p000/cq2.java（连接主体：地址拼装 / 帧收发 / 心跳 / 重连）
 *       + bq2.java（心跳循环 10000ms / 重连 5 次 × 5000ms / 首帧处理）
 *       + sz0.java（连接前的两个同步 GET，见 LiveHttpClient）
 * 原职责: 直播弹幕长连接的完整生命周期
 * 重构说明: 拆成"地址/参数拼装" + "帧循环" + "重连调度" 三段，UI 层只订阅回调。
 *           实测常量全部原样保留：心跳 10000ms、重连最多 5 次、每次间隔 5000ms、
 *           关闭码 1000、心跳帧 payloadType "hb"、logId 0。
 */
package axtv.live;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 直播弹幕 WebSocket 客户端。
 *
 * <p>URL 与参数逐字取自原实现（cq2.java:170）：
 * <pre>
 * wss://webcast3-ws-web-lq.douyin.com/webcast/im/push/v2/
 *   app_name=douyin_web, version_code=180800, webcast_sdk_version=1.3.0,
 *   update_version_code=1.3.0, compress=gzip, cursor=h-1_t-&lt;now&gt;_r-1_d-1_u-1,
 *   host=https://live.douyin.com, aid=6383, live_id=1, did_rule=3, debug=false,
 *   maxCacheMessageNumber=20, endpoint=live_pc, support_wrds=1,
 *   im_path=/webcast/im/fetch/, device_platform=web, cookie_enabled=true,
 *   screen_width=1920, screen_height=1080, browser_language=zh-CN,
 *   browser_platform=Win32, browser_name=Mozilla
 * </pre>
 */
public final class LiveWebSocketClient {

    static final String WS_URL = "wss://webcast3-ws-web-lq.douyin.com/webcast/im/push/v2/";
    static final long HEARTBEAT_INTERVAL_MS = 10_000L;
    static final long RECONNECT_DELAY_MS = 5_000L;
    static final int MAX_RECONNECT_ATTEMPTS = 5;
    static final int CLOSE_NORMAL = 1000;

    /** 业务回调。全部在内部单线程上触发，实现方无需再串行化。 */
    public interface Listener {

        void onConnected();

        /** 弹幕（`WebcastChatMessage`）。 */
        void onChat(LiveChat.Message message);

        /** 在线人数（`WebcastRoomUserSeqMessage`）。 */
        void onRoomUserSeq(LiveChat.RoomUserSeq seq);

        void onDisconnected(String reason);

        void onError(Throwable error, int httpStatus, String body);
    }

    private final WebSocketTransport transport;
    private final LiveSignatureProvider signer;
    private final Listener listener;
    private final LiveLog log;
    private final ScheduledExecutorService scheduler;

    private volatile WebSocketTransport.Handle handle;
    private volatile boolean closed;
    private ScheduledFuture<?> heartbeat;
    private final AtomicInteger attempt = new AtomicInteger();
    private LiveCredentials credentials;
    private String userAgent;

    public LiveWebSocketClient(WebSocketTransport transport, LiveSignatureProvider signer, Listener listener) {
        this(transport, signer, listener, LiveLog.stderr());
    }

    public LiveWebSocketClient(WebSocketTransport transport, LiveSignatureProvider signer,
                               Listener listener, LiveLog log) {
        this.transport = transport;
        this.signer = signer;
        this.listener = listener;
        this.log = log == null ? LiveLog.stderr() : log;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "live-ws");
            t.setDaemon(true);
            return t;
        });
    }

    /** 连接（首次）。 */
    public void connect(LiveCredentials credentials, String userAgent) {
        this.credentials = credentials;
        this.userAgent = userAgent;
        this.closed = false;
        attempt.set(0);
        open();
    }

    /** 主动断开，不再重连。 */
    public void disconnect() {
        closed = true;
        stopHeartbeat();
        WebSocketTransport.Handle h = handle;
        if (h != null) {
            h.close(CLOSE_NORMAL, null);
        }
        scheduler.shutdownNow();
    }

    // ------------------------------------------------------------------ 连接

    private void open() {
        String url;
        try {
            url = buildUrl(credentials, userAgent);
        } catch (Exception e) {
            listener.onError(e, -1, "");
            return;
        }
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("user-agent", userAgent);
        if (!LiveLogFormat.isBlank(credentials.cookieHeader())) {
            headers.put("cookie", credentials.cookieHeader());
        }
        log.d(LiveLog.TAG_LIVE_WS, "connecting: roomId=" + credentials.roomId()
                + " attempt=" + attempt.get() + "/" + MAX_RECONNECT_ATTEMPTS);
        handle = transport.open(url, headers, new ListenerImpl());
    }

    /** 拼 URL。signature 由 sign.js 的 getMSSDKSignature 生成（失败为空串，仍会拼进去）。 */
    String buildUrl(LiveCredentials credentials, String userAgent) throws Exception {
        String signature = signer.signLive(credentials.roomId(), credentials.userId(), userAgent);
        StringBuilder sb = new StringBuilder(WS_URL).append('?');
        Map<String, String> q = new LinkedHashMap<>();
        q.put("app_name", "douyin_web");
        q.put("version_code", "180800");
        q.put("webcast_sdk_version", "1.3.0");
        q.put("update_version_code", "1.3.0");
        q.put("compress", "gzip");
        q.put("cursor", "h-1_t-" + System.currentTimeMillis() + "_r-1_d-1_u-1");
        q.put("host", "https://live.douyin.com");
        q.put("aid", "6383");
        q.put("live_id", "1");
        q.put("did_rule", "3");
        q.put("debug", "false");
        q.put("maxCacheMessageNumber", "20");
        q.put("endpoint", "live_pc");
        q.put("support_wrds", "1");
        q.put("im_path", "/webcast/im/fetch/");
        q.put("user_unique_id", credentials.userId());
        q.put("device_platform", "web");
        q.put("cookie_enabled", "true");
        q.put("screen_width", "1920");
        q.put("screen_height", "1080");
        q.put("browser_language", "zh-CN");
        q.put("browser_platform", "Win32");
        q.put("browser_name", "Mozilla");
        q.put("browser_version", "5.0 (Windows)");
        q.put("identity", "audience");
        q.put("room_id", credentials.roomId());
        q.put("signature", signature);
        boolean first = true;
        for (Map.Entry<String, String> e : q.entrySet()) {
            if (!first) {
                sb.append('&');
            }
            first = false;
            sb.append(e.getKey()).append('=').append(java.net.URLEncoder.encode(e.getValue() == null ? "" : e.getValue(), java.nio.charset.StandardCharsets.UTF_8));
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ 心跳

    private void startHeartbeat() {
        stopHeartbeat();
        heartbeat = scheduler.scheduleAtFixedRate(() -> {
            WebSocketTransport.Handle h = handle;
            if (h == null) {
                return;
            }
            // 原实现：k04.m6902n("hb", 0L, new byte[0])
            h.send(LiveFrameCodec.encodeFrame("hb", 0L, new byte[0]));
        }, HEARTBEAT_INTERVAL_MS, HEARTBEAT_INTERVAL_MS, TimeUnit.MILLISECONDS);
        log.d(LiveLog.TAG_LIVE_WS, "heartbeat loop started: intervalMs=" + HEARTBEAT_INTERVAL_MS);
    }

    private void stopHeartbeat() {
        ScheduledFuture<?> hb = heartbeat;
        if (hb != null) {
            hb.cancel(false);
            heartbeat = null;
        }
    }

    // ------------------------------------------------------------------ 重连

    private void scheduleReconnect(String source) {
        if (closed) {
            return;
        }
        int current = attempt.incrementAndGet();
        if (current > MAX_RECONNECT_ATTEMPTS) {
            log.w(LiveLog.TAG_LIVE_WS, "websocket reconnect gave up after " + MAX_RECONNECT_ATTEMPTS);
            listener.onDisconnected("reconnect-exhausted");
            return;
        }
        log.w(LiveLog.TAG_LIVE_WS, "scheduling websocket reconnect: source=" + source
                + ", attempt=" + current + "/" + MAX_RECONNECT_ATTEMPTS + ", delayMs=" + RECONNECT_DELAY_MS);
        scheduler.schedule(this::open, RECONNECT_DELAY_MS, TimeUnit.MILLISECONDS);
    }

    // ------------------------------------------------------------------ 帧处理

    private void onFrame(byte[] data) {
        LiveFrame frame;
        try {
            frame = LiveFrameCodec.decodeFrame(data);
        } catch (RuntimeException e) {
            log.w(LiveLog.TAG_LIVE_WS, "frame decode failed: " + e);
            return;
        }
        if ("hb".equals(frame.payloadType())) {
            log.d(LiveLog.TAG_LIVE_WS, "heartbeat frame received");
            return;
        }
        byte[] payload;
        try {
            payload = LiveFrameCodec.decodePayload(frame);
        } catch (Exception e) {
            log.w(LiveLog.TAG_LIVE_WS, "payload decode failed: " + e);
            return;
        }
        LiveResponse response = LiveFrameCodec.decodeResponse(payload);
        for (LiveResponse.Message message : response.messages()) {
            dispatch(message);
        }
        if (response.needAck()) {
            WebSocketTransport.Handle h = handle;
            if (h != null) {
                h.send(LiveFrameCodec.encodeAckFrame(frame.logId(), response.internalExt()));
            }
        }
    }

    private void dispatch(LiveResponse.Message message) {
        String method = message.method();
        if (method == null) {
            return;
        }
        switch (method) {
            case "WebcastChatMessage":
                listener.onChat(LiveFrameCodec.decodeChat(message.payload()));
                break;
            case "WebcastRoomUserSeqMessage":
                listener.onRoomUserSeq(LiveFrameCodec.decodeRoomUserSeq(message.payload()));
                break;
            default:
                log.d(LiveLog.TAG_LIVE_WS, "ignored message method=" + method);
                break;
        }
    }

    private final class ListenerImpl implements WebSocketTransport.Listener {

        @Override
        public void onOpen(int statusCode, int headerCount) {
            attempt.set(0);
            log.d(LiveLog.TAG_LIVE_WS, "websocket opened: status=" + statusCode
                    + " headers=" + headerCount);
            startHeartbeat();
            listener.onConnected();
        }

        @Override
        public void onBinary(byte[] data) {
            onFrame(data);
        }

        @Override
        public void onText(String text) {
            log.d(LiveLog.TAG_LIVE_WS, "text frame ignored: " + LiveLogFormat.truncate(80, text));
        }

        @Override
        public void onClosing(int code, String reason) {
            stopHeartbeat();
            log.d(LiveLog.TAG_LIVE_WS, "websocket closing: " + code + " " + reason);
        }

        @Override
        public void onClosed(int code, String reason) {
            stopHeartbeat();
            listener.onDisconnected(reason);
            scheduleReconnect("closed:" + code);
        }

        @Override
        public void onFailure(Throwable error, int httpStatus, String body) {
            stopHeartbeat();
            listener.onError(error, httpStatus, body);
            scheduleReconnect("failure");
        }
    }
}
