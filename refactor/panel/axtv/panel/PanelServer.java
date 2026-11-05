/*
 * 来源: src/app/p000/k63.java（NanoHTTPD 服务器骨架：ServerSocket、监听线程、启停）
 *       + src/app/p000/j63.java（ServerRunnable 接受循环）
 * 原职责: 一个端口上的极简 HTTP 服务；start/stop 同步等待真正开始监听
 * 重构说明: 传输层（HTTP 解析/响应写出）抽到 {@link HttpServerCore} 接口 ——
 *           核心层默认用 JDK com.sun.net.httpserver（JdkPanelServer），
 *           Android 侧用原生 ServerSocket 实现（Android 壳工程里提供）。
 *           本类只保留：参数校验、启停编排、路由分发、日志。
 *
 * 与原实现的差异（已确认，见 NOTES.md）：
 * - 原实现每连接一线程（NanoHTTPD DefaultAsyncRunner）；此处用固定上限的线程池。
 * - 原实现读取请求体依赖 Content-Length，无上限（可用超大 Content-Length 触发 OOM）；
 *   此处按 contentLength 读满但设 4 MiB 上限。
 */
package axtv.panel;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 极简 HTTP 服务。
 *
 * <p>对应原 {@code k63}（NanoHTTPD 抽象基类）：端口在构造时确定，{@link #start()} 绑定并开始
 * 接收请求，{@link #stop()} 关闭监听并等待处理线程退出。两台服务器各自一个实例：
 * Cookie 推送 9978、搜索面板 9979。
 */
public final class PanelServer implements AutoCloseable {

    /** 请求体上限（原实现无上限）。 */
    public static final int MAX_BODY_BYTES = 4 * 1024 * 1024;

    private final String tag;
    private final int port;
    private final PanelRoutes routes;
    private final boolean daemon;
    private final int workerThreads;
    /** 传输层；null = 用 JDK com.sun.net.httpserver（Android 侧必须传入原生实现）。 */
    private final HttpServerCore transport;
    /** 传输层工厂；与 {@link #transport} 二选一，null 时走 JDK 反射实现。 */
    private final TransportFactory transportFactory;

    /** 传输层工厂：让 Android 侧按 Options 创建自己的 ServerSocket 实现。 */
    @FunctionalInterface
    public interface TransportFactory {
        HttpServerCore create(HttpServerCore.Options options);
    }

    private final Logger log;
    private final Object lock = new Object();
    private volatile HttpServerCore server;
    private volatile ExecutorService executor;

    public PanelServer(String tag, int port, PanelRoutes routes, boolean daemon) {
        this(tag, port, routes, daemon, 8);
    }

    public PanelServer(String tag, int port, PanelRoutes routes, boolean daemon, int workerThreads) {
        this(tag, port, routes, daemon, workerThreads, (HttpServerCore) null);
    }

    /**
     * @param transport 传输层；null = 用 JDK {@code com.sun.net.httpserver} 实现。
     *                  Android 侧必须传入原生 ServerSocket 实现（android.jar 无 com.sun.net.httpserver）。
     */
    public PanelServer(String tag, int port, PanelRoutes routes, boolean daemon, int workerThreads,
                       HttpServerCore transport) {
        this(tag, port, routes, daemon, workerThreads, transport, null);
    }

    /**
     * @param transportFactory 传输层工厂；Android 侧传入 {@code AndroidPanelServer::new}。
     *                         与 {@code transport} 二选一，都为 null 时走 JDK 反射实现。
     */
    public PanelServer(String tag, int port, PanelRoutes routes, boolean daemon, int workerThreads,
                       TransportFactory transportFactory) {
        this(tag, port, routes, daemon, workerThreads, null, transportFactory);
    }

    private PanelServer(String tag, int port, PanelRoutes routes, boolean daemon, int workerThreads,
                        HttpServerCore transport, TransportFactory transportFactory) {
        this.tag = tag;
        this.port = port;
        this.routes = routes;
        this.daemon = daemon;
        this.workerThreads = Math.max(1, workerThreads);
        this.transport = transport;
        this.transportFactory = transportFactory;
        this.log = Logger.getLogger(tag);
    }

    public String tag() {
        return this.tag;
    }

    public int port() {
        return this.port;
    }

    /** 实际监听端口（构造时传 0 才有意义）。 */
    public int boundPort() {
        HttpServerCore current = this.server;
        return current == null ? -1 : current.boundPort();
    }

    public boolean isRunning() {
        return this.server != null;
    }

    /**
     * 绑定并启动。重复调用是幂等的（原实现对已启动的服务器直接跳过）。
     *
     * @throws IOException 绑定失败（端口被占用等），调用方负责记录「服务器启动失败」
     */
    public void start() throws IOException {
        synchronized (this.lock) {
            if (this.server != null) {
                return;
            }
            ExecutorService pool = Executors.newFixedThreadPool(this.workerThreads, threadFactory());
            try {
                HttpServerCore.Options opts = new HttpServerCore.Options(
                        this.tag, this.port, this.routes, this.daemon, this.workerThreads);
                HttpServerCore created;
                if (this.transport != null) {
                    created = this.transport;
                } else if (this.transportFactory != null) {
                    created = this.transportFactory.create(opts);
                } else {
                    created = createJdkTransport(opts, this.log);
                }
                created.start();
                this.executor = pool;
                this.server = created;
            } catch (IOException | RuntimeException e) {
                pool.shutdownNow();
                throw e;
            }
        }
    }

    /** 停止。对应原 {@code k63.f()}：关监听 → 中断工作线程 → 等待结束。 */
    public void stop() {
        ExecutorService pool;
        HttpServerCore current;
        synchronized (this.lock) {
            current = this.server;
            pool = this.executor;
            this.server = null;
            this.executor = null;
        }
        if (current != null) {
            current.stop();
        }
        if (pool != null) {
            pool.shutdownNow();
            try {
                if (!pool.awaitTermination(2, TimeUnit.SECONDS)) {
                    this.log.log(Level.WARNING, "处理线程未在 2 秒内退出: " + this.tag);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public void close() {
        stop();
    }

    /**
     * 反射创建 JDK 传输层：Android 壳工程排除了 JdkPanelServer
     * （android.jar 无 com.sun.net.httpserver），静态引用会造成编译期硬依赖。
     */
    private static HttpServerCore createJdkTransport(HttpServerCore.Options opts, Logger log) {
        try {
            Class<?> type = Class.forName("axtv.panel.JdkPanelServer");
            java.lang.reflect.Method m = type.getDeclaredMethod(
                    "create", HttpServerCore.Options.class, Logger.class);
            m.setAccessible(true);
            return (HttpServerCore) m.invoke(null, opts, log);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                    "JdkPanelServer 不可用：当前运行时不含 com.sun.net.httpserver"
                            + "（Android 侧须注入原生 ServerSocket 传输层）", e);
        }
    }

    private ThreadFactory threadFactory() {
        AtomicInteger counter = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, this.tag + "-http-" + counter.incrementAndGet());
            thread.setDaemon(this.daemon);
            return thread;
        };
    }

    /** 面板地址：{@code http://ip:port/path}（原 qj0.m9586h / wg0 的拼装方式）。 */
    public static String baseUrl(String host, int port) {
        return "http://" + host + ":" + port;
    }
}
