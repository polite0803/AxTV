/*
 * 来源: src/app/p000/k63.java（NanoHTTPD 服务器骨架）+ j63.java（接受循环）
 * 原职责: 一个端口上的极简 HTTP 服务；start/stop 同步等待真正开始监听
 * 重构说明: 抽出传输层接口，使核心层不绑定 com.sun.net.httpserver
 *           （Android 的 android.jar 不含该包）。
 *           核心层默认实现 PanelServer（JDK com.sun.net.httpserver）；
 *           Android 侧用原生 ServerSocket 实现（AndroidPanelServer.kt）。
 * 规格出处: panel/NOTES.md 路由表；ADAPTERS.md §5（端口 9978/9979/9980）
 */
package axtv.panel;

import java.io.IOException;

/**
 * 极简 HTTP 服务器的传输层。
 *
 * <p>与 {@code com.sun.net.httpserver.HttpServer} 的差异仅在于：Android 上不可用
 * （android.jar 不含 com.sun.net.httpserver），需要一个原生 Socket 实现。
 * 路由分发逻辑（{@link PanelRoutes#dispatch}）在传输层之上，两者完全解耦。
 */
public interface HttpServerCore extends AutoCloseable {

    /** 传输层构造参数。 */
    final class Options {
        private final String tag;
        private final int port;
        private final PanelRoutes routes;
        private final boolean daemon;
        private final int workerThreads;

        public Options(String tag, int port, PanelRoutes routes, boolean daemon, int workerThreads) {
            this.tag = tag;
            this.port = port;
            this.routes = routes;
            this.daemon = daemon;
            this.workerThreads = workerThreads;
        }

        public String tag() {
            return tag;
        }

        public int port() {
            return port;
        }

        public PanelRoutes routes() {
            return routes;
        }

        public boolean daemon() {
            return daemon;
        }

        public int workerThreads() {
            return workerThreads;
        }
    }

    /** 启动监听（阻塞至真正开始监听，与原实现一致）。 */
    void start() throws IOException;

    /** 停止监听并回收线程。 */
    void stop();

    /** 实际绑定的端口（port=0 时由系统分配）。 */
    int boundPort();

    @Override
    void close();
}
