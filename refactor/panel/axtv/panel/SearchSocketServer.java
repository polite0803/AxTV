/*
 * 来源: src/app/p000/wc4.java（WebSocketServer 子类，端口 9980）
 *       + 其父类 src/app/p000/AbstractC1687u4.java（org.java-websocket 的 WebSocketServer，第三方库）
 * 原职责: 手机搜索页与电视之间的 WebSocket 通道：
 *         握手接入 → 新连接补发 tv_text → 收到 input/search 转给 UI → 断线更新状态
 * 重构说明: 用纯 JDK 重写一个最小 RFC 6455 服务端（只支持文本帧，够本协议用），
 *           去掉第三方库的 Selector 多路复用与解码器池；协议行为（端口、消息格式、1001 关闭码）保持一致。
 */
package axtv.panel;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 搜索输入的 WebSocket 服务（端口 9980）。
 *
 * <p>浏览器端行为（见 assets/web/search.html）：
 * <ul>
 *   <li>连上即认为「已连接」；断线 2 秒后重连；</li>
 *   <li>上行 {@code {"type":"input","text":…}} 边打边同步、{@code {"type":"search","text":…}} 触发搜索；</li>
 *   <li>下行 {@code {"type":"tv_text","text":…}} 回显电视搜索框当前文字（连接成功时补发一次）。</li>
 * </ul>
 */
public final class SearchSocketServer implements AutoCloseable {

    /** WebSocket 端口（原 wc4 的 {@code new InetSocketAddress(9980)}）。 */
    public static final int PORT = 9980;

    /** 关闭码 1001 = going away（原实现对每个连接调 {@code close(1001, reason)}）。 */
    public static final int CLOSE_GOING_AWAY = 1001;

    /** 单帧上限，防止畸形帧打爆内存。 */
    private static final int MAX_FRAME_BYTES = 1024 * 1024;
    private static final int MAX_HEADER_BYTES = 16 * 1024;

    private static final String WEBSOCKET_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

    private final PanelState state;
    private final java.util.function.Consumer<String> messageSink;
    private final Logger log = Logger.getLogger("SearchInputServer");

    private final Set<Client> clients = ConcurrentHashMap.newKeySet();
    private final AtomicInteger clientIds = new AtomicInteger();

    private volatile ServerSocket serverSocket;
    private volatile Thread acceptThread;
    private volatile boolean running;

    /**
     * @param state       状态/消息通道
     * @param messageSink 上行消息消费方，通常是 {@link SearchInputHandler#onPhoneMessage(String)}
     */
    public SearchSocketServer(PanelState state, java.util.function.Consumer<String> messageSink) {
        this.state = state;
        this.messageSink = messageSink;
    }

    public int port() {
        return PORT;
    }

    public int boundPort() {
        ServerSocket socket = this.serverSocket;
        return socket == null ? -1 : socket.getLocalPort();
    }

    public boolean isRunning() {
        return this.running;
    }

    public int clientCount() {
        return this.clients.size();
    }

    /** 启动监听。原实现启动成功后打一行 {@code WsServer started on port 9980}。 */
    public synchronized void start() throws IOException {
        if (this.running) {
            return;
        }
        ServerSocket socket = new ServerSocket();
        socket.setReuseAddress(true);
        socket.bind(new InetSocketAddress(PORT));
        this.serverSocket = socket;
        this.running = true;
        Thread thread = new Thread(this::acceptLoop, "WebSocketSelector-9980");
        thread.setDaemon(true);
        this.acceptThread = thread;
        thread.start();
        this.log.log(Level.INFO, "WsServer started on port " + PORT);
    }

    /**
     * 停止：给所有连接发 1001，然后关监听。
     *
     * @param reason 关闭原因，原实现停止时传空串
     */
    public synchronized void stop(String reason) {
        if (!this.running) {
            return;
        }
        this.running = false;
        for (Client client : this.clients) {
            client.close(CLOSE_GOING_AWAY, reason == null ? "" : reason);
        }
        ServerSocket socket = this.serverSocket;
        this.serverSocket = null;
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException e) {
                this.log.log(Level.WARNING, "关闭监听失败", e);
            }
        }
        Thread thread = this.acceptThread;
        this.acceptThread = null;
        if (thread != null) {
            thread.interrupt();
        }
    }

    /** 原 {@code wc4.m11497u()}：停止但不带原因。 */
    public void stop() {
        stop("");
    }

    @Override
    public void close() {
        stop();
    }

    /** 向所有已连接手机广播一条文本（原 {@code wc4.m11481d}）。 */
    public void broadcast(String text) {
        for (Client client : this.clients) {
            client.sendText(text);
        }
    }

    private void acceptLoop() {
        while (this.running) {
            try {
                Socket socket = this.serverSocket == null ? null : this.serverSocket.accept();
                if (socket == null) {
                    return;
                }
                socket.setTcpNoDelay(false);
                socket.setKeepAlive(true);
                Thread handler = new Thread(() -> serve(socket), "WebSocketClient-" + socket.getPort());
                handler.setDaemon(true);
                handler.start();
            } catch (IOException e) {
                if (this.running) {
                    this.log.log(Level.SEVERE, "WebSocket 错误", e);
                    this.state.socketError();
                }
                return;
            }
        }
    }

    private void serve(Socket socket) {
        Client client = null;
        try {
            client = new Client(String.valueOf(this.clientIds.incrementAndGet()), socket);
            if (!handshake(client)) {
                client.sendHttpError(400, "Bad Request");
                return;
            }
            if (!this.running) {
                client.close(CLOSE_GOING_AWAY, "");
                return;
            }
            this.clients.add(client);
            if (this.state.clientConnected(client.id)) {
                // 新连接补发电视当前文字（原 wc4.m11492p）
                try {
                    client.sendText(this.state.tvTextMessage());
                } catch (RuntimeException e) {
                    this.log.log(Level.WARNING, "回传电视文字失败", e);
                }
            }
            client.readLoop(this::onText);
        } catch (IOException e) {
            this.log.log(Level.FINE, "连接结束: " + socket.getRemoteSocketAddress(), e);
        } finally {
            if (client != null) {
                this.clients.remove(client);
                this.state.clientDisconnected(client.id);
                client.closeQuietly();
            } else {
                closeQuietly(socket);
            }
        }
    }

    private static void closeQuietly(Socket socket) {
        try {
            socket.close();
        } catch (IOException ignored) {
            // 已关闭
        }
    }

    private void onText(String text) {
        try {
            this.messageSink.accept(text);
        } catch (RuntimeException e) {
            this.log.log(Level.SEVERE, "消息处理失败: " + text, e);
        }
    }

    /** RFC 6455 握手：校验 Sec-WebSocket-Key，回 101 + Sec-WebSocket-Accept。 */
    private boolean handshake(Client client) throws IOException {
        String header = client.readHttpHead(MAX_HEADER_BYTES);
        if (header == null || header.isEmpty()) {
            return false;
        }
        if (!header.startsWith("GET ")) {
            return false;
        }
        String key = headerField(header, "sec-websocket-key");
        if (key == null || key.isBlank()) {
            return false;
        }
        String accept;
        try {
            accept = Base64.getEncoder().encodeToString(
                    MessageDigest.getInstance("SHA-1").digest((key.trim() + WEBSOCKET_GUID).getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-1 unavailable", e);
        }
        client.writeRaw("HTTP/1.1 101 Switching Protocols\r\n"
                + "Upgrade: websocket\r\n"
                + "Connection: Upgrade\r\n"
                + "Sec-WebSocket-Accept: " + accept + "\r\n\r\n");
        return true;
    }

    private static String headerField(String header, String name) {
        for (String line : header.split("\r\n")) {
            int colon = line.indexOf(':');
            if (colon > 0 && line.substring(0, colon).trim().equalsIgnoreCase(name)) {
                return line.substring(colon + 1).trim();
            }
        }
        return null;
    }

    /** 一条已连接的对端。写操作加锁，读在自身线程里。 */
    private static final class Client {
        private final String id;
        private final Socket socket;
        private final InputStream in;
        private final OutputStream out;
        private final Object writeLock = new Object();
        private volatile boolean closed;

        Client(String id, Socket socket) throws IOException {
            this.id = id;
            this.socket = socket;
            this.in = new BufferedInputStream(socket.getInputStream(), 8192);
            this.out = socket.getOutputStream();
        }

        String remoteAddress() {
            return this.socket.getInetAddress() == null ? "?" : this.socket.getInetAddress().getHostAddress();
        }

        /** 读到空行为止的请求头（不含最后一个空行之后的内容）。 */
        String readHttpHead(int maxBytes) throws IOException {
            ByteArrayOutputStream head = new ByteArrayOutputStream(512);
            int matched = 0;
            while (head.size() < maxBytes) {
                int b = this.in.read();
                if (b < 0) {
                    return head.size() == 0 ? null : head.toString(StandardCharsets.UTF_8);
                }
                head.write(b);
                matched = switch (matched) {
                    case 0 -> b == '\r' ? 1 : 0;
                    case 1 -> b == '\n' ? 2 : (b == '\r' ? 1 : 0);
                    case 2 -> b == '\r' ? 3 : 0;
                    default -> b == '\n' ? 4 : 0;
                };
                if (matched == 4) {
                    String text = head.toString(StandardCharsets.UTF_8);
                    return text.substring(0, text.length() - 4);
                }
            }
            return head.toString(StandardCharsets.UTF_8);
        }

        void readLoop(java.util.function.Consumer<String> onText) throws IOException {
            ByteArrayOutputStream fragments = new ByteArrayOutputStream();
            int fragmentOpcode = -1;
            while (!this.closed) {
                int first = this.in.read();
                if (first < 0) {
                    return;
                }
                int second = this.in.read();
                if (second < 0) {
                    return;
                }
                boolean fin = (first & 0x80) != 0;
                int opcode = first & 0x0F;
                boolean masked = (second & 0x80) != 0;
                long length = second & 0x7F;
                if (length == 126) {
                    length = readUnsigned(2);
                } else if (length == 127) {
                    length = readUnsigned(8);
                }
                if (length > MAX_FRAME_BYTES) {
                    close(1009, "frame too big");
                    return;
                }
                byte[] mask = masked ? readFully(4) : null;
                byte[] payload = readFully((int) length);
                if (masked) {
                    for (int i = 0; i < payload.length; i++) {
                        payload[i] ^= mask[i & 3];
                    }
                }
                switch (opcode) {
                    case 0x8 -> {
                        close(1000, "");
                        return;
                    }
                    case 0x9 -> sendFrame(0xA, payload);
                    case 0xA -> {
                        // pong，忽略
                    }
                    case 0x1 -> {
                        if (fin) {
                            onText.accept(new String(payload, StandardCharsets.UTF_8));
                        } else {
                            fragments.reset();
                            fragments.write(payload);
                            fragmentOpcode = opcode;
                        }
                    }
                    case 0x0 -> {
                        fragments.write(payload);
                        if (fin) {
                            onText.accept(new String(fragments.toByteArray(), StandardCharsets.UTF_8));
                            fragments.reset();
                            fragmentOpcode = -1;
                        }
                    }
                    default -> {
                        // 二进制帧本协议不使用，丢弃
                    }
                }
                if (fragmentOpcode == -1 && opcode != 0x0) {
                    fragments.reset();
                }
            }
        }

        void sendText(String text) {
            sendFrame(0x1, text.getBytes(StandardCharsets.UTF_8));
        }

        void close(int code, String reason) {
            if (this.closed) {
                return;
            }
            byte[] reasonBytes = reason == null ? new byte[0] : reason.getBytes(StandardCharsets.UTF_8);
            byte[] payload = new byte[2 + reasonBytes.length];
            payload[0] = (byte) ((code >> 8) & 0xFF);
            payload[1] = (byte) (code & 0xFF);
            System.arraycopy(reasonBytes, 0, payload, 2, reasonBytes.length);
            try {
                sendFrame(0x8, payload);
            } catch (RuntimeException ignored) {
                // 对端已断
            }
            closeQuietly();
        }

        void closeQuietly() {
            this.closed = true;
            try {
                this.socket.close();
            } catch (IOException ignored) {
                // 已关闭
            }
        }

        void sendHttpError(int status, String message) {
            try {
                byte[] body = (message + "\r\n").getBytes(StandardCharsets.UTF_8);
                synchronized (this.writeLock) {
                    this.out.write(("HTTP/1.1 " + status + " " + message + "\r\n"
                            + "Content-Type: text/plain; charset=utf-8\r\n"
                            + "Content-Length: " + body.length + "\r\n"
                            + "Connection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8));
                    this.out.write(body);
                    this.out.flush();
                }
            } catch (IOException ignored) {
                // 对端已断
            } finally {
                closeQuietly();
            }
        }

        private void sendFrame(int opcode, byte[] payload) {
            if (this.closed) {
                return;
            }
            try {
                synchronized (this.writeLock) {
                    ByteArrayOutputStream frame = new ByteArrayOutputStream(payload.length + 10);
                    frame.write(0x80 | opcode);
                    if (payload.length < 126) {
                        frame.write(payload.length);
                    } else if (payload.length <= 0xFFFF) {
                        frame.write(126);
                        frame.write((payload.length >> 8) & 0xFF);
                        frame.write(payload.length & 0xFF);
                    } else {
                        frame.write(127);
                        long length = payload.length;
                        for (int shift = 56; shift >= 0; shift -= 8) {
                            frame.write((int) ((length >> shift) & 0xFF));
                        }
                    }
                    frame.write(payload);
                    this.out.write(frame.toByteArray());
                    this.out.flush();
                }
            } catch (IOException e) {
                closeQuietly();
            }
        }

        private void writeRaw(String text) throws IOException {
            synchronized (this.writeLock) {
                this.out.write(text.getBytes(StandardCharsets.UTF_8));
                this.out.flush();
            }
        }

        private long readUnsigned(int bytes) throws IOException {
            long value = 0;
            for (int i = 0; i < bytes; i++) {
                int b = this.in.read();
                if (b < 0) {
                    throw new EOFException("连接已断开");
                }
                value = (value << 8) | (b & 0xFFL);
            }
            return value;
        }

        private byte[] readFully(int length) throws IOException {
            byte[] buffer = new byte[length];
            int read = 0;
            while (read < length) {
                int n = this.in.read(buffer, read, length - read);
                if (n < 0) {
                    throw new EOFException("连接已断开");
                }
                read += n;
            }
            return buffer;
        }
    }
}
