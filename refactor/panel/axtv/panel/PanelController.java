/*
 * 来源: src/app/p000/hu2.java（构造里启动/停止 CookieServer、onStop 停止）
 *       + src/app/p000/iu1.java（按开关启动搜索输入服务器，记录端口与失败状态）
 *       + src/app/p000/zc4.java（搜索服务整体启停、连接集合清理）
 *       + src/app/p000/qj0.java m9586h / wg0（面板地址拼装）
 * 原职责: 两台面板服务器的装配与生命周期（谁先起、失败怎么记、停的时候清什么）
 * 重构说明: 从 ViewModel / Compose 副作用里抽出来，纯 JDK，可单独跑。
 *           启动时 Cookie 推送与搜索输入一起启动；全部请求需 PanelAuth 令牌。
 */
package axtv.panel;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 面板服务的装配与生命周期。
 *
 * <ul>
 *   <li>Cookie 推送：TCP 9978（HTTP），随应用启动，{@code http://<ip>:9978/submit}</li>
 *   <li>搜索输入：TCP 9979（HTTP 页面）+ 9980（WebSocket），开关打开时才启动，{@code http://<ip>:9979}</li>
 * </ul>
 */
public final class PanelController implements AutoCloseable {

    private final PanelState state;
    private final AuthStore authStore;
    private final ThemeRenderer renderer;
    private final DeviceAddressResolver.DeviceIdentity identity;
    private final PanelAuth auth;
    private final PanelServer.TransportFactory transportFactory;

    private final CookiePushHandler cookieHandler;
    private final SearchInputHandler searchHandler;
    private final LogcatExportHandler logcatHandler;

    private final PanelServer cookieServer;
    private PanelServer searchPageServer;
    private SearchSocketServer searchSocketServer;

    private final Logger log = Logger.getLogger("CookieServer");
    private final Logger searchLog = Logger.getLogger("SearchInputServer");

    /**
     * @param auth             访问令牌；所有 HTTP/WS 请求都要携带（手机通过电视展示的地址接入）
     * @param transportFactory Android 侧传入原生 ServerSocket 实现；JVM 侧传 null 走 JDK HTTP
     */
    public PanelController(PanelState state,
                           AuthStore authStore,
                           ThemeRenderer.AssetSource assets,
                           ThemeRenderer.ThemeColors colors,
                           DeviceAddressResolver.DeviceIdentity identity,
                           LogcatExportHandler.LogcatSource logcatSource,
                           PanelAuth auth,
                           PanelServer.TransportFactory transportFactory) {
        if (auth == null) {
            throw new IllegalArgumentException("面板令牌不能为空");
        }
        this.state = state;
        this.authStore = authStore;
        this.identity = identity;
        this.auth = auth;
        this.transportFactory = transportFactory;
        this.renderer = new ThemeRenderer(assets, colors);
        this.logcatHandler = new LogcatExportHandler(logcatSource);
        this.cookieHandler = new CookiePushHandler(this.renderer, authStore, state,
                () -> DeviceAddressResolver.deviceName(identity), this.logcatHandler, auth);
        this.searchHandler = new SearchInputHandler(this.renderer, state, auth);
        this.cookieServer = new PanelServer("CookieServer", CookiePushHandler.PORT,
                this.cookieHandler.routes(), false, 8, transportFactory);
    }

    public PanelState state() {
        return this.state;
    }

    public ThemeRenderer renderer() {
        return this.renderer;
    }

    public AuthStore authStore() {
        return this.authStore;
    }

    /** 手机端要打开的 Cookie 推送地址（原 qj0.m9586h），已带访问令牌。 */
    public String cookieSubmitUrl() {
        return this.auth.appendTo(DeviceAddressResolver.url(
                DeviceAddressResolver.localAddress(), CookiePushHandler.PORT,
                CookiePushHandler.PATH_SUBMIT));
    }

    /** 手机端要打开的搜索输入地址（原 wg0 拼装），已带访问令牌。 */
    public String searchPanelUrl() {
        return this.auth.appendTo(DeviceAddressResolver.url(
                DeviceAddressResolver.localAddress(), SearchInputHandler.PORT, ""));
    }

    /** Cookie 服务启动；失败只记日志，不影响应用其它功能。 */
    public synchronized void startCookieServer() {
        try {
            if (this.cookieServer.isRunning()) {
                return;
            }
            this.cookieServer.start();
            this.log.log(Level.INFO, "Cookie 服务器启动: " + cookieSubmitUrl());
        } catch (Exception e) {
            this.log.log(Level.SEVERE, "服务器启动失败", e);
        }
    }

    public synchronized void stopCookieServer() {
        try {
            this.cookieServer.stop();
            this.log.log(Level.INFO, "Cookie 服务器已停止");
        } catch (Exception e) {
            this.log.log(Level.SEVERE, "服务器停止失败", e);
        }
    }

    /**
     * 搜索输入服务启动：先起 WebSocket，再起 HTTP 页面服务（原 iu1 的顺序：vc4 先 start，
     * 再 new wc4 + start；失败则状态置 ERROR）。
     */
    public synchronized void startSearchServer() {
        if (this.searchPageServer != null && this.searchPageServer.isRunning()) {
            return;
        }
        try {
            PanelServer pageServer = new PanelServer("SearchInputServer", SearchInputHandler.PORT,
                    this.searchHandler.routes(), true, 8, this.transportFactory);
            pageServer.start();
            this.searchPageServer = pageServer;

            SearchSocketServer socketServer = new SearchSocketServer(
                    this.state, this.searchHandler::onPhoneMessage, this.auth);
            socketServer.start();
            this.searchSocketServer = socketServer;

            this.state.markServerStarted();
            this.searchLog.log(Level.INFO, "搜索输入服务器启动，HTTP 端口: " + SearchInputHandler.PORT);
        } catch (Exception e) {
            this.searchLog.log(Level.SEVERE, "服务器启动失败", e);
            this.state.markServerStartFailed();
            stopSearchServer();
        }
    }

    public synchronized void stopSearchServer() {
        try {
            SearchSocketServer socketServer = this.searchSocketServer;
            this.searchSocketServer = null;
            if (socketServer != null) {
                socketServer.stop();
            }
            PanelServer pageServer = this.searchPageServer;
            this.searchPageServer = null;
            if (pageServer != null) {
                pageServer.stop();
            }
            this.state.markStopped();
            this.searchLog.log(Level.INFO, "搜索输入服务器已停止");
        } catch (Exception e) {
            this.searchLog.log(Level.SEVERE, "服务器停止失败", e);
        }
    }

    public boolean isSearchServerRunning() {
        SearchSocketServer socketServer = this.searchSocketServer;
        return socketServer != null && socketServer.isRunning();
    }

    /**
     * 电视搜索框文字变化 → 存一份 + 广播给已连接的手机（原 te4 的 tv_text 通道）。
     * 没有连接时只存不发，新连接握手时会补发。
     */
    public void publishTvText(String text) {
        this.state.setTvText(text);
        SearchSocketServer socketServer = this.searchSocketServer;
        if (socketServer != null) {
            try {
                socketServer.broadcast(this.state.tvTextMessage());
            } catch (RuntimeException e) {
                this.searchLog.log(Level.WARNING, "回传电视文字失败", e);
            }
        }
    }

    /** 应用启动：Cookie 推送与搜索输入都启动（两者都要求令牌）。 */
    public void startAll() {
        startCookieServer();
        startSearchServer();
    }

    /** 应用退出：两台都停。 */
    public void stopAll() {
        stopSearchServer();
        stopCookieServer();
    }

    @Override
    public void close() {
        stopAll();
    }
}
