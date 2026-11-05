/*
 * HttpServerCore 的 JDK 实现（com.sun.net.httpserver.HttpServer）。
 * 纯 JVM 可用；Android 侧不含 com.sun.net.httpserver，改用原生 ServerSocket 实现
 * （Android 壳工程里提供 AndroidPanelServer.kt）。
 *
 * 行为与原 NanoHTTPD 骨架（k63/j63）等价：
 *  - 端口在 start() 时绑定，port=0 由系统分配（boundPort 可读实际端口）；
 *  - 每连接一请求，交给 PanelRoutes.dispatch 分发；
 *  - 请求体按 Content-Length 读满，上限 MAX_BODY_BYTES（4 MiB）；
 *  - 环回/任意本地地址统一记为 127.0.0.1。
 */
package axtv.panel;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

final class JdkPanelServer implements HttpServerCore {

    /** 原 NanoHTTPD 默认 backlog。 */
    private static final int BACKLOG = 0;

    private final HttpServerCore.Options options;
    private final Logger log;
    private final Object lock = new Object();

    private volatile HttpServer server;
    private volatile ExecutorService executor;

    private JdkPanelServer(HttpServerCore.Options options, Logger log) {
        this.options = options;
        this.log = log;
    }

    static JdkPanelServer create(HttpServerCore.Options options, Logger log) {
        return new JdkPanelServer(options, log);
    }

    @Override
    public void start() throws IOException {
        synchronized (this.lock) {
            if (this.server != null) {
                return;
            }
            HttpServer created = HttpServer.create(
                    new InetSocketAddress(this.options.port()), BACKLOG);
            ExecutorService pool = Executors.newFixedThreadPool(
                    this.options.workerThreads(), threadFactory());
            created.setExecutor(pool);
            created.createContext("/", this::handle);
            created.start();
            this.executor = pool;
            this.server = created;
        }
    }

    @Override
    public void stop() {
        HttpServer current;
        ExecutorService pool;
        synchronized (this.lock) {
            current = this.server;
            pool = this.executor;
            this.server = null;
            this.executor = null;
        }
        if (current != null) {
            current.stop(0);
        }
        if (pool != null) {
            pool.shutdownNow();
            try {
                if (!pool.awaitTermination(2, TimeUnit.SECONDS)) {
                    this.log.log(Level.WARNING, "处理线程未在 2 秒内退出: " + this.options.tag());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public int boundPort() {
        HttpServer current = this.server;
        return current == null ? -1 : current.getAddress().getPort();
    }

    @Override
    public void close() {
        stop();
    }

    private void handle(HttpExchange exchange) {
        long startedAt = System.nanoTime();
        try (exchange) {
            PanelRoutes.Request request = toRequest(exchange);
            PanelRoutes.Response response = this.options.routes().dispatch(request);
            write(exchange, response);
            // 注：java.util.logging 的 log(Level, String, Object[]) 是数组形参（非可变参数），
            // 故这里显式构造 Object[]；MessageFormat 占位符 {0}..{3} 与原实现一致。
            this.log.log(Level.FINE, "{0} {1} -> {2} ({3} ms)",
                    new Object[] {
                            request.method(), request.path(), response.status(),
                            (System.nanoTime() - startedAt) / 1_000_000
                    });
        } catch (Exception e) {
            this.log.log(Level.WARNING, "请求处理失败: " + exchange.getRequestURI(), e);
            try {
                write(exchange, PanelRoutes.Response.text(PanelRoutes.SC_INTERNAL_ERROR,
                        PanelRoutes.TEXT_PLAIN, "SERVER INTERNAL ERROR: " + e.getMessage()));
            } catch (IOException ignored) {
                // 连接已断，原实现同样只记日志
            }
        }
    }

    private PanelRoutes.Request toRequest(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod().toUpperCase(Locale.ROOT);
        String path = exchange.getRequestURI().getPath();
        if (path == null || path.isEmpty()) {
            path = "/";
        }
        Map<String, String> headers = new LinkedHashMap<>();
        exchange.getRequestHeaders().forEach((name, values) -> {
            if (values != null && !values.isEmpty()) {
                headers.put(name.toLowerCase(Locale.ROOT), values.get(0));
            }
        });
        int contentLength = 0;
        String lengthHeader = headers.get("content-length");
        if (lengthHeader != null) {
            try {
                contentLength = Integer.parseInt(lengthHeader.trim());
            } catch (NumberFormatException ignored) {
                contentLength = 0;
            }
        }
        byte[] body = "GET".equals(method) || "HEAD".equals(method)
                ? new byte[0]
                : PanelRoutes.readBody(exchange.getRequestBody(), contentLength,
                        PanelServer.MAX_BODY_BYTES);
        return new PanelRoutes.Request(method, path, exchange.getRequestURI().getQuery(),
                headers, body, remoteAddress(exchange));
    }

    /** 原 e63 构造里把环回/任意本地地址统一记为 127.0.0.1。 */
    private static String remoteAddress(HttpExchange exchange) {
        InetAddress address = exchange.getRemoteAddress() == null
                ? null : exchange.getRemoteAddress().getAddress();
        if (address == null) {
            return "127.0.0.1";
        }
        if (address.isLoopbackAddress() || address.isAnyLocalAddress()) {
            return "127.0.0.1";
        }
        return address.getHostAddress();
    }

    private static void write(HttpExchange exchange, PanelRoutes.Response response)
            throws IOException {
        if (response.contentType() != null) {
            exchange.getResponseHeaders().set("Content-Type", response.contentType());
        }
        response.headers().forEach((name, value) -> exchange.getResponseHeaders().set(name, value));
        byte[] body = response.body() == null ? new byte[0] : response.body();
        boolean noBody = response.status() == 204 || response.status() == 304
                || "HEAD".equalsIgnoreCase(exchange.getRequestMethod());
        exchange.sendResponseHeaders(response.status(), noBody ? -1 : body.length);
        if (!noBody && body.length > 0) {
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
                out.flush();
            }
        }
    }

    private ThreadFactory threadFactory() {
        AtomicInteger counter = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable,
                    this.options.tag() + "-http-" + counter.incrementAndGet());
            thread.setDaemon(this.options.daemon());
            return thread;
        };
    }
}
