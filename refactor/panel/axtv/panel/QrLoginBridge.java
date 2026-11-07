/*
 * 来源: src/app/p000/ez3.java（QrLoginManager 主流程）
 *       + src/app/p000/od2.java（登录用 WebView 会话：UA、Cookie、JS 求值、页面加载超时）
 *       + src/app/p000/sy3.java（注入页面的 XHR/fetch 钩子脚本）
 *       + src/app/p000/ty3.java（按文档坐标模拟点击）
 *       + src/app/p000/vy3.java / uy3.java（UI 状态）
 * 原职责: 用隐藏 WebView 打开抖音网页登录页，抓 /passport/web/get_qrcode/ 的二维码图片、
 *         轮询 /passport/web/check_qrconnect/，扫码成功后取 Cookie 登录电视端
 * 重构说明: WebView 依赖收进 WebViewHost 接口；状态机、超时、重试上限、注入脚本、
 *           请求路径判定原样保留，解码为位图的部分留在 Android 侧（核心层只给 PNG 字节）。
 */
package axtv.panel;

import java.util.Base64;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 扫码登录桥。
 *
 * <p>时序：点「扫码登录」→ 清空旧 Cookie → 打开 {@value #LOGIN_URL} → 等页面注入钩子 →
 * 轮询页面里的 {@code window.__dyQr} 拿到二维码 → 展示「等待扫码」→ 用户扫码确认 →
 * 读 {@code document.cookie} 拿到登录态 → 交给 {@link AuthStore}。
 *
 * <p><b>存疑</b>：原类中三个核心方法（{@code ez3.m3955b} 轮询二维码、{@code m3957d}、{@code m3960i}）
 * 因 jadx 溢出未能反编译，本类按可见的常量、日志、状态与消息重建，具体轮询间隔与
 * 登录成功的判定条件见 NOTES.md，需要真机对照。
 */
public final class QrLoginBridge {

    /** 登录页（原 {@code od2} 里 loadUrl 的目标）。 */
    public static final String LOGIN_URL = "https://creator.douyin.com/";

    /** 扫码状态（原 {@code uy3}，前 7 个枚举顺序一致）。 */
    public enum Status {
        IDLE, STARTING, WAITING_SCAN, SCANNED, EXPIRED, SUCCESS, FAILED,
        /**
         * 风控要求二次身份验证（短信 / 滑块 / 刷脸）。原版没有独立枚举位，
         * 是直接把隐藏 WebView 浮出来（{@code py3} case 2）；本应用需要一个能驱动
         * 「验证码数字键盘 / 登录页浮出」两种 UI 的显式状态，故追加在末尾。
         */
        VERIFY
    }

    /**
     * 一次登录会话的 UI 状态（原 {@code vy3}）。
     *
     * @param status      阶段
     * @param message     展示文案
     * @param qrImage     二维码图片字节（原类型是 Bitmap；PNG 解码在 Android 侧）
     * @param cookieHeader 登录成功时的 Cookie
     * @param nickname    昵称（存疑，见 NOTES.md）
     * @param codeInput   是否处于「输入验证码」状态
     */
    public record State(Status status, String message, byte[] qrImage, String cookieHeader, String nickname, boolean codeInput) {
        public State {
            status = status == null ? Status.IDLE : status;
            message = message == null ? "" : message;
            cookieHeader = cookieHeader == null ? "" : cookieHeader;
            nickname = nickname == null ? "" : nickname;
        }

        public static State idle() {
            return new State(Status.IDLE, "", null, "", "", false);
        }
    }

    /** WebView 能力。Android 侧实现要点见 NOTES.md。 */
    public interface WebViewHost {
        /** 加载页面，超时抛异常（原 od2.m8558a：60 秒）。 */
        void loadUrl(String url, long timeoutMs) throws Exception;

        /** 执行 JS 并返回结果字符串（Android 的 evaluateJavascript 会把结果再 JSON 编码一层）。 */
        String evaluateJavascript(String script, long timeoutMs) throws Exception;

        /** 清空 Cookie（原 ez3.m3962k：removeAllCookies + removeSessionCookies + flush，5 秒超时）。 */
        void clearCookies(long timeoutMs) throws Exception;

        /** 销毁隐藏 WebView（原 od2.m8559b）。 */
        void destroy();

        /** 按文档坐标模拟一次点击（原 ty3：换算成 view 坐标后 dispatchTouchEvent）。 */
        void tap(double x, double y);

        /**
         * 二次验证时把隐藏 WebView 显示出来并允许交互（false 时收回）。
         * 风控要求滑块 / 刷脸这类无法代填的验证时，页面必须可见才能继续，否则会一直停在「已扫码」；
         * 只是短信验证码时保持隐藏，由应用侧数字键盘代填（{@link #requestCodeSubmission}）。
         */
        void setInteractive(boolean interactive);

        /** 当前 Activity 是否还在（原 ez3 用 k21 的弱引用判断，取不到就提示「请回到应用界面内再发起登录」）。 */
        boolean isHostAvailable();

        /** 浏览器内核标识（如 "Chrome/77.0.3865.120"），用于诊断；未知返回 "unknown"。 */
        String engineInfo();

        /**
         * 本机 WebView 是否可用。
         *
         * <p>电视盒子 / 定制 ROM 可能把 WebView 裁掉，构造 {@code new WebView(context)} 直接抛
         * {@code MissingWebViewPackageException} / {@code AndroidRuntimeException}。为 false 时
         * 没有 WebView 时扫码登录无法完成（轮询需要页面 SDK 生成的 msToken，裸 HTTP 会被风控 4031），
         * 由 {@link #start()} 直接给出可操作提示，不再启动会失败的流程。
         */
        boolean isUsable();
    }

    public interface Listener {
        void onState(State state);
    }


    /** 自建会话脚本（测试与 HTTP 会话用；生产主流程已改走页面内出码）。 */
    public static String selfQrParams() {
        return QrLoginScripts.SELF_QR_PARAMS;
    }

    /** 自建会话脚本（测试用）。 */
    public static String selfQrScript() {
        return QrLoginScripts.SELF_QR_SCRIPT;
    }

    // 以下为 QrLoginProtocol 的包级委托：历史测试直接以 QrLoginBridge.前缀调用。

    /** @see QrLoginProtocol#parseQrImage */
    public static byte[] parseQrImage(String qrField) {
        return QrLoginProtocol.parseQrImage(qrField);
    }

    /** @see QrLoginProtocol#pollStatus */
    static String pollStatus(String poll) {
        return QrLoginProtocol.pollStatus(poll);
    }

    /** @see QrLoginProtocol#urlLooksLikeVerification */
    static boolean urlLooksLikeVerification(String url) {
        return QrLoginProtocol.urlLooksLikeVerification(url);
    }

    /** @see QrLoginProtocol#pollLooksLikeVerification */
    static boolean pollLooksLikeVerification(String poll) {
        return QrLoginProtocol.pollLooksLikeVerification(poll);
    }

    /** @see QrLoginProtocol#confirmRedirectUrl */
    static String confirmRedirectUrl(String poll) {
        return QrLoginProtocol.confirmRedirectUrl(poll);
    }

    /** @see QrLoginProtocol#redactPoll */
    static String redactPoll(String poll) {
        return QrLoginProtocol.redactPoll(poll);
    }

    // 以下为 QrLoginScripts 脚本常量的委托：测试与 Android 侧历史代码以 QrLoginBridge.前缀引用。

    /** @see QrLoginScripts#HOOK_SCRIPT */
    public static final String HOOK_SCRIPT = QrLoginScripts.HOOK_SCRIPT;

    /** @see QrLoginScripts#SNAPSHOT_SCRIPT */
    public static final String SNAPSHOT_SCRIPT = QrLoginScripts.SNAPSHOT_SCRIPT;

    /** @see QrLoginScripts#DISMISS_COMPAT_SCRIPT */
    public static final String DISMISS_COMPAT_SCRIPT = QrLoginScripts.DISMISS_COMPAT_SCRIPT;

    /** @see QrLoginScripts#OPEN_LOGIN_MODAL_SCRIPT */
    public static final String OPEN_LOGIN_MODAL_SCRIPT = QrLoginScripts.OPEN_LOGIN_MODAL_SCRIPT;

    public static final long PAGE_LOAD_TIMEOUT_MS = 60_000L;
    public static final long QR_TIMEOUT_MS = 60_000L;
    public static final long QR_FIRST_TIMEOUT_MS = 20_000L;
    public static final long EVAL_TIMEOUT_MS = 15_000L;
    public static final long COOKIE_TIMEOUT_MS = 5_000L;
    public static final long POLL_INTERVAL_MS = 1_000L;
    /** 二维码过期后最多重新生成几次（原 {@code ez3.f5677f >= 8}）。 */
    public static final int MAX_RESTARTS = 8;
    /** 页面超过这么久既没出新二维码帧、也没再发 check_qrconnect，就重载登录页重新出码。 */
    public static final long QR_STALE_MS = 25_000L;
    /** 已扫码后等待手机端确认的窗口：页面此时不再刷新二维码，不能按 stale 重载登录页。 */
    public static final long QR_CONFIRM_TIMEOUT_MS = 120_000L;
    /** 手机已确认却这么久了还没拿到 sessionid：把登录页浮出来，让用户看到卡在哪一步。 */
    public static final long CONFIRM_SURFACE_DELAY_MS = 8_000L;
    /** 「浏览器不太兼容」提示的点击间隔（弹窗可能被页面重新渲染）。 */
    private static final long COMPAT_DISMISS_INTERVAL_MS = 5_000L;
    /** 浮出登录页后重试点开登录入口的间隔与上限。 */
    private static final long PAGE_OPEN_INTERVAL_MS = 3_000L;
    private static final int PAGE_OPEN_MAX_ATTEMPTS = 5;

    private final WebViewHost webView;
    private final Logger log = Logger.getLogger("QrLoginManager");

    private volatile State state = State.idle();
    private volatile Listener listener;
    private volatile boolean cancelled;
    /** UI 侧提交的验证码，由轮询线程取走执行（evaluateJavascript 会阻塞，不能在主线调用）。 */
    private final AtomicReference<String> pendingCode = new AtomicReference<>();
    /** UI 侧「显示登录页」请求；由轮询线程取走执行。 */
    private final AtomicBoolean manualVerify = new AtomicBoolean();
    /** UI 侧「收起登录页」：抑制自动浮出，直到用户再点开或验证态消失。 */
    private final AtomicBoolean manualDismiss = new AtomicBoolean();
    private Thread worker;
    private int restartCount;

    public QrLoginBridge(WebViewHost webView) {
        this.webView = webView;
    }

    /**
     * 排队提交一个短信验证码（{@link QrLoginScripts#CODE_FILL_SCRIPT} + {@link QrLoginScripts#CODE_CLICK_SCRIPT}）。
     *
     * <p>只接受数字：非数字一律丢弃，剩下的一路带到 JS 里做字符串替换，所以没有注入面。
     */
    public void requestCodeSubmission(String code) {
        String digits = code == null ? "" : code.replaceAll("\\D", "");
        if (!digits.isEmpty()) {
            this.pendingCode.set(digits);
        }
    }

    /**
     * 手动打开登录页（电视端遥控器直接操作页面、完成页面上的验证）。
     *
     * <p>探测不到验证态时的兜底：自动判据依赖页面 DOM/URL/轮询响应，任何一种变化都可能漏掉，
     * 用户至少要有一条「把登录页拿出来自己点」的路，不能卡在「已扫码」。
     */
    public void requestManualVerification() {
        if (!this.webViewReady) {
            // HTTP 直连模式没有页面可浮出：UI 侧按 webView 可用性隐藏入口。
            return;
        }
        this.manualDismiss.set(false);
        this.manualVerify.set(true);
    }

    /** 收起浮出的登录页（页面里按 BACK）。验证态仍在时不自动再弹，直到用户再点「显示登录页」。 */
    public void requestVerificationDismiss() {
        this.manualDismiss.set(true);
    }

    public State state() {
        return this.state;
    }

    /** 一屏能看完的紧凑诊断（内核 / 页面弹窗 / 二维码 / 兼容提示 / 轮询状态），供登录页展示。 */
    public String diagnostic() {
        return this.diagnostic;
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    /** 本机 WebView 是否可用（扫码登录依赖页面内 SDK，没有它无法完成登录）。 */
    private volatile boolean webViewReady;
    /** 一屏能看完的紧凑诊断，LoginScreen 每秒刷新展示。 */
    private volatile String diagnostic = "";

    /** 开始登录（原 {@code ez3.m3964m()}：重试计数清零 + 「正在打开抖音登录页」）。 */
    public synchronized void start() {
        if (this.worker != null) {
            return;
        }
        boolean webViewReady = this.webView != null && this.webView.isUsable();
        if (!webViewReady) {
            // 为什么不能"无 WebView 也能扫码"：check_qrconnect 会被风控拦成 4031，
            // 因为网页 SDK 才会生成 msToken 等反爬令牌，裸 HTTP 请求补不齐（已用真实接口验证）。
            publish(new State(Status.FAILED,
                    "本机没有可用的 WebView，无法完成扫码登录；请用手机面板推送登录态，或「导入 Cookie」",
                    null, "", "", false));
            return;
        }
        if (!this.webView.isHostAvailable()) {
            // 原实现：C1902zy.m12465i("请回到应用界面内再发起登录")
            publish(new State(Status.FAILED, "请回到应用界面内再发起登录", null, "", "", false));
            return;
        }
        this.cancelled = false;
        this.restartCount = 0;
        this.webViewReady = true;
        this.log.log(Level.INFO, "qr login channel=webview, engine=" + this.webView.engineInfo());
        publish(new State(Status.STARTING, "正在打开抖音登录页", null, "", "", false));
        Thread thread = new Thread(this::run, "QrLogin");
        thread.setDaemon(true);
        this.worker = thread;
        thread.start();
    }

    /** 停止（原 {@code ez3.m3958f()}）：中断流程、清 Cookie、销毁 WebView、回到 IDLE。 */
    public synchronized void stop() {
        this.cancelled = true;
        Thread thread = this.worker;
        this.worker = null;
        if (thread != null) {
            thread.interrupt();
        }
        if (this.webView != null) {
            try {
                this.webView.clearCookies(COOKIE_TIMEOUT_MS);
            } catch (Exception e) {
                this.log.log(Level.WARNING, "清空登录 Cookie 失败", e);
            }
            try {
                this.webView.destroy();
            } catch (RuntimeException e) {
                this.log.log(Level.WARNING, "销毁登录 WebView 失败", e);
            }
        }
        publish(State.idle());
    }

    /** 二维码过期（原 {@code ez3.m3959g(String)}）：未超过 8 次则重开登录页。 */
    public synchronized void onQrExpired(String message) {
        if (this.cancelled) {
            return;
        }
        if (this.restartCount >= MAX_RESTARTS) {
            publish(new State(Status.EXPIRED, message == null ? "二维码已过期" : message, null, "", "", false));
            return;
        }
        this.restartCount++;
        this.log.log(Level.INFO, "qr expired, restarting login (#" + this.restartCount + ")");
        // 原实现先取消上一轮登录协程（rv4.mo1818e）再重开，这里同样先中断旧线程
        Thread previous = this.worker;
        this.worker = null;
        if (previous != null) {
            previous.interrupt();
        }
        this.cancelled = false;
        publish(new State(Status.STARTING, "二维码已过期，正在重新生成", null, "", "", false));
        Thread thread = new Thread(this::run, "QrLogin");
        thread.setDaemon(true);
        this.worker = thread;
        thread.start();
    }

    /** 主流程：清 Cookie → 打开登录页 → 注入钩子 → 轮询二维码 → 等待扫码 → 取 Cookie。 */
    private void run() {
        try {
            this.webView.clearCookies(COOKIE_TIMEOUT_MS);
            this.webView.loadUrl(LOGIN_URL, PAGE_LOAD_TIMEOUT_MS);
            this.webView.evaluateJavascript(QrLoginScripts.HOOK_SCRIPT, EVAL_TIMEOUT_MS);

            // 登录页是 SPA：不打开登录弹窗就不会出码，而电视遥控器点不动网页。
            // 这里全程用注入脚本自动操作页面：先点掉「浏览器不兼容」提示，再点开「登录/扫码登录」入口。
            //
            // 不再使用"自建会话"出码：它拿不到网页 SDK 生成的 msToken，check_qrconnect 会被风控
            // 判成 4031（已用真实接口验证），手机扫码后永远不会 confirmed —— 之前"卡在已扫码"就是这条。
            long qrDeadline = System.currentTimeMillis() + QR_TIMEOUT_MS;
            boolean timeoutNotified = false;
            long lastOpenAt = 0L;
            long lastCompatAt = 0L;
            int openAttempts = 0;
            byte[] image = null;
            String imageRaw = null;
            while (!this.cancelled && image == null) {
                Snapshot head = snapshot();
                updateDiagnostic(head, null);
                long now = System.currentTimeMillis();
                if (head.compat && now - lastCompatAt > COMPAT_DISMISS_INTERVAL_MS) {
                    lastCompatAt = now;
                    try {
                        String dismissed = this.webView.evaluateJavascript(
                                QrLoginScripts.DISMISS_COMPAT_SCRIPT, EVAL_TIMEOUT_MS);
                        this.log.log(Level.INFO, "compat dialog dismiss: " + dismissed);
                    } catch (Exception e) {
                        this.log.log(Level.WARNING, "compat dialog dismiss failed", e);
                    }
                }
                image = head.qrRaw == null ? null : QrLoginProtocol.parseQrImage(head.qrRaw);
                if (image != null) {
                    imageRaw = head.qrRaw;
                } else if (head.domQrRaw != null) {
                    // 原版 ez3.m3955b 的 DOM 兜底：XHR 钩子没拿到/拿到坏响应时，直接取弹窗里的二维码图片。
                    image = QrLoginProtocol.parseQrImage(head.domQrRaw);
                    if (image != null) {
                        imageRaw = head.domQrRaw;
                    }
                }
                if (image != null) {
                    this.log.log(Level.INFO, "page produced qr (modal=" + head.open
                            + " compat=" + head.compat + " attempts=" + openAttempts + ")");
                    break;
                }
                boolean manual = this.manualVerify.getAndSet(false);
                if (manual) {
                    this.manualDismiss.set(false);
                    this.webView.setInteractive(true);
                    this.log.log(Level.INFO, "login page surfaced by user request");
                    publish(new State(Status.VERIFY,
                            "已打开登录页：可查看页面提示，或直接在页面上完成登录，完成后自动继续",
                            null, "", "", false));
                }
                if (this.manualDismiss.getAndSet(false)) {
                    this.webView.setInteractive(false);
                    publish(new State(Status.STARTING, "正在打开抖音登录页", null, "", "", false));
                }
                if (openAttempts < PAGE_OPEN_MAX_ATTEMPTS && now - lastOpenAt > PAGE_OPEN_INTERVAL_MS) {
                    lastOpenAt = now;
                    openAttempts++;
                    try {
                        String opened = this.webView.evaluateJavascript(
                                QrLoginScripts.OPEN_LOGIN_MODAL_SCRIPT, EVAL_TIMEOUT_MS);
                        this.log.log(Level.INFO, "open login modal #" + openAttempts + ": " + opened);
                    } catch (Exception e) {
                        this.log.log(Level.WARNING, "open login modal failed", e);
                    }
                }
                if (!timeoutNotified && now > qrDeadline) {
                    timeoutNotified = true;
                    // 不 return：继续留在循环里，用户点「显示登录页」或页面稍后出码都还能恢复。
                    this.log.log(Level.WARNING, "page produced no qr in " + QR_TIMEOUT_MS + "ms");
                    publish(new State(Status.FAILED,
                            "登录页未能生成二维码（本机浏览器内核可能过旧，站点提示不兼容）。"
                                    + "可点「显示登录页」查看页面提示，或改用手机面板/导入 Cookie",
                            null, "", "", false));
                }
                sleep(POLL_INTERVAL_MS);
            }
            if (this.cancelled) {
                return;
            }
            publish(new State(Status.WAITING_SCAN, "等待扫码", image, "", "", false));

            // 等待扫码：每秒同时做两件事——
            // 1) 读 Cookie：出现 sessionid 即登录完成（权威判据，creator / www 两个域由宿主合并）；
            // 2) 读页面快照：__dyQr 变了就立刻把新二维码推给 UI（页面换码后界面不再显示旧码），
            //    并记录页面自己 check_qrconnect 的最新响应（__dyPoll）用于取证。
            String frameRaw = imageRaw;
            long freshAt = System.currentTimeMillis();
            long pollAt = 0L;
            String lastPoll = null;
            String lastPollStatus = null;
            int staleRestarts = 0;
            long scannedAt = 0L;
            long confirmedAt = 0L;
            boolean verifying = false;
            /** 当前验证态是「应用侧数字键盘」还是「浮出登录页」。 */
            boolean keypadMode = false;
            /** 连续判到「验证已消失」的轮数：见下面 needsVerification 分支的去抖说明。 */
            int verifyClears = 0;
            /** 上一次的页面探针结果，只在变化时打日志，避免每秒刷屏。 */
            String lastProbeKey = "";
            /** 浮出登录页后点开登录入口的重试节流（弹窗关掉后入口才点得到）。 */
            long lastPageOpenAt = 0L;
            int pageOpenAttempts = 0;
            boolean surfacedManually = false;
            while (!this.cancelled) {
                String cookie = readCookie();
                if (AuthStore.hasSessionId(cookie)) {
                    this.webView.setInteractive(false);
                    publish(new State(Status.SUCCESS, "登录成功", image, cookie, "", false));
                    return;
                }
                Snapshot snapshot = snapshot();
                String candidateRaw = snapshot.qrRaw != null
                        && QrLoginProtocol.parseQrImage(snapshot.qrRaw) != null
                        ? snapshot.qrRaw : snapshot.domQrRaw;
                if (candidateRaw != null && !candidateRaw.equals(frameRaw)) {
                    byte[] next = QrLoginProtocol.parseQrImage(candidateRaw);
                    if (next != null) {
                        image = next;
                        frameRaw = candidateRaw;
                        freshAt = System.currentTimeMillis();
                        scannedAt = 0L;
                        confirmedAt = 0L;
                        this.log.log(Level.INFO, "qr frame updated, bytes=" + next.length + ", freshAt=" + freshAt);
                        publish(new State(Status.WAITING_SCAN, "等待扫码", image, "", "", false));
                    }
                }
                if (snapshot.pollAt > pollAt) {
                    pollAt = snapshot.pollAt;
                }
                if (snapshot.pollRaw != null && !snapshot.pollRaw.equals(lastPoll)) {
                    lastPoll = snapshot.pollRaw;
                    this.log.log(Level.INFO, "check_qrconnect: " + QrLoginProtocol.redactPoll(snapshot.pollRaw));
                    // 页面换码走的是 check_qrconnect 响应里的 data.qrcode（不是重新发 get_qrcode），
                    // 所以这里也要解一帧，否则界面会一直显示已经过期的那张码。
                    byte[] rotated = QrLoginProtocol.parseQrImage(snapshot.pollRaw);
                    if (rotated != null && !java.util.Arrays.equals(rotated, image)) {
                        image = rotated;
                        freshAt = System.currentTimeMillis();
                        frameRaw = null;
                        scannedAt = 0L;
                        confirmedAt = 0L;
                        this.log.log(Level.INFO, "qr rotated from check_qrconnect, bytes="
                                + rotated.length + ", freshAt=" + freshAt);
                        publish(new State(Status.WAITING_SCAN, "等待扫码", image, "", "", false));
                    }
                    String pollStatus = QrLoginProtocol.pollStatus(snapshot.pollRaw);
                    if (pollStatus != null && !pollStatus.equals(lastPollStatus)) {
                        lastPollStatus = pollStatus;
                        this.log.log(Level.INFO, "poll status=" + pollStatus);
                        if ("confirmed".equals(pollStatus)) {
                            // 手机已确认：之后只等 Cookie 落地。文案必须和「请确认」区分开，
                            // 否则界面上看起来永远是同一个状态（真机上就是「卡死在已扫码」的观感）。
                            confirmedAt = System.currentTimeMillis();
                            String redirect = QrLoginProtocol.confirmRedirectUrl(snapshot.pollRaw);
                            if (redirect != null) {
                                // 网页自己是跳到这个地址才真正种下 sessionid；自建二维码会话只轮询、
                                // 不跳转，手机确认后不补这一步，Cookie 就永远不会落地。
                                this.log.log(Level.INFO, "confirmed, completing login via redirect: " + redirect);
                                try {
                                    this.webView.loadUrl(redirect, PAGE_LOAD_TIMEOUT_MS);
                                } catch (Exception e) {
                                    this.log.log(Level.WARNING, "confirm redirect failed", e);
                                }
                            }
                            publish(new State(Status.SCANNED, "手机已确认，正在获取登录态…", image, "", "", false));
                        } else if ("scan".equals(pollStatus) || "scanned".equals(pollStatus)) {
                            if (scannedAt == 0L) {
                                scannedAt = System.currentTimeMillis();
                            }
                            publish(new State(Status.SCANNED, "已扫码，请在手机上确认登录", image, "", "", false));
                        }
                    }
                }
                // 二次验证有三路判据，任何一路命中都要把登录页交给用户：
                //   1) DOM：验证码输入框 / 验证文案（含同源 iframe）；
                //   2) URL：跳到了 verify/captcha/challenge 这类验证页；
                //   3) 轮询响应：出现风控相关字段（此前读的 account_flow 是猜的，按关键字兜底）。
                // 检查必须放在「本轮有新的 check_qrconnect 响应」那个块外面：验证页出现后页面
                // 常常不再发轮询请求，只在响应变化时判就永远等不到。
                boolean urlVerify = QrLoginProtocol.urlLooksLikeVerification(snapshot.url);
                boolean pollVerify = QrLoginProtocol.pollLooksLikeVerification(snapshot.pollRaw);
                boolean detectedVerify = snapshot.verify || snapshot.codeInput || urlVerify || pollVerify;
                String probeKey = snapshot.valid + "|" + snapshot.verify + "|" + snapshot.codeInput
                        + "|" + urlVerify + "|" + pollVerify + "|" + snapshot.open + "|" + snapshot.url;
                if (!probeKey.equals(lastProbeKey)) {
                    lastProbeKey = probeKey;
                    this.log.log(Level.INFO, "page probe: valid=" + snapshot.valid
                            + " domVerify=" + snapshot.verify + " codeInput=" + snapshot.codeInput
                            + " urlVerify=" + urlVerify + " pollVerify=" + pollVerify
                            + " open=" + snapshot.open + " selfQr=" + snapshot.selfQr
                            + " compat=" + snapshot.compat + " url=" + snapshot.url);
                }
                // 抖音创作者中心会给"不认识的浏览器"弹一张兼容性提示（推荐装 Edge/Chrome）。
                // 那是网页里的下载引导，电视上点了没反应，还会盖住登录入口 —— 检测到就直接点掉。
                // 注意：隐藏态也要点，否则它会一直挡着页面自己的登录流程。
                if (snapshot.compat) {
                    long nowMs = System.currentTimeMillis();
                    if (nowMs - lastCompatAt > COMPAT_DISMISS_INTERVAL_MS) {
                        lastCompatAt = nowMs;
                        try {
                            String dismissed = this.webView.evaluateJavascript(
                                    QrLoginScripts.DISMISS_COMPAT_SCRIPT, EVAL_TIMEOUT_MS);
                            this.log.log(Level.INFO, "compat dialog dismiss: " + dismissed);
                        } catch (Exception e) {
                            this.log.log(Level.WARNING, "compat dialog dismiss failed", e);
                        }
                    }
                }
                boolean manualRequest = this.manualVerify.getAndSet(false);
                if (manualRequest || !detectedVerify) {
                    // 用户主动打开、或验证态确实消失了 → 解除「手动收起」抑制。
                    this.manualDismiss.set(false);
                }
                boolean wantVerification = (detectedVerify || manualRequest) && !this.manualDismiss.get();
                if (wantVerification) {
                    verifyClears = 0;
                    // 页面上真有验证码输入框、且不是用户主动要页面 → 用应用侧数字键盘代填；
                    // 其余情况（滑块/刷脸/整页跳到验证页）一律把登录页浮出来给用户操作。
                    // 已经处在这一态时也要响应「改为在登录页操作」：键盘态下点它会切到页面态。
                    boolean nextKeypad = detectedVerify && snapshot.codeInput && !manualRequest;
                    if (!verifying || nextKeypad != keypadMode) {
                        verifying = true;
                        keypadMode = nextKeypad;
                        surfacedManually = manualRequest && !keypadMode;
                        pageOpenAttempts = 0;
                        lastPageOpenAt = System.currentTimeMillis();
                        this.webView.setInteractive(!keypadMode);
                        this.log.log(Level.INFO, "verification page shown (keypad=" + keypadMode
                                + ", manual=" + manualRequest + ")");
                        if (!keypadMode && manualRequest) {
                            // 用户主动要页面（或手机已确认却拿不到登录态）：把页面的登录弹窗点出来。
                            // 二维码如果来自自建会话，页面其实还没进登录流程，不点开就永远没有验证窗口。
                            try {
                                String opened = this.webView.evaluateJavascript(
                                        QrLoginScripts.OPEN_LOGIN_MODAL_SCRIPT, EVAL_TIMEOUT_MS);
                                this.log.log(Level.INFO, "open login modal: " + opened);
                            } catch (Exception e) {
                                this.log.log(Level.WARNING, "open login modal failed", e);
                            }
                        }
                        publish(new State(Status.VERIFY,
                                keypadMode
                                        ? "该账号需要短信验证码：用下方数字键盘输入后提交"
                                        : "已打开登录页：请完成页面上的验证，完成后会自动继续",
                                image, "", "", keypadMode));
                    }
                } else if (verifying) {
                    // 手动收起立即生效；自动探测消失要连续两轮才信，避免一次 JS 失败就收起页面。
                    boolean dismissed = this.manualDismiss.get();
                    if (dismissed || (snapshot.valid && ++verifyClears >= 2)) {
                        verifying = false;
                        keypadMode = false;
                        verifyClears = 0;
                        this.webView.setInteractive(false);
                        this.log.log(Level.INFO, dismissed
                                ? "verification page hidden by user"
                                : "verification dismissed, hiding login page");
                        publish(new State(Status.SCANNED,
                                dismissed ? "已收起登录页（可再点「显示登录页」）" : "验证已完成，等待手机端确认登录",
                                image, "", "", false));
                    }
                }
                // 只有确实处在「数字键盘代填」态时才注入，避免在用户自己操作的页面上误点按钮。
                if (verifying && keypadMode && snapshot.codeInput) {
                    submitPendingCode(image);
                }
                // 兼容性弹窗点掉后登录入口才露出来：浮出态下按节流重试几次（最多 5 次）。
                // 只在用户主动要页面时做，避免在自动浮出的验证页上乱点。
                if (verifying && !keypadMode && surfacedManually && !snapshot.compat
                        && pageOpenAttempts < PAGE_OPEN_MAX_ATTEMPTS
                        && System.currentTimeMillis() - lastPageOpenAt > PAGE_OPEN_INTERVAL_MS) {
                    lastPageOpenAt = System.currentTimeMillis();
                    pageOpenAttempts++;
                    try {
                        String opened = this.webView.evaluateJavascript(
                                QrLoginScripts.OPEN_LOGIN_MODAL_SCRIPT, EVAL_TIMEOUT_MS);
                        this.log.log(Level.INFO,
                                "open login modal retry #" + pageOpenAttempts + ": " + opened);
                    } catch (Exception e) {
                        this.log.log(Level.WARNING, "open login modal retry failed", e);
                    }
                }
                // 手机已确认却迟迟拿不到 sessionid：多半还有一步网页端验证/确认没做完。
                // 把登录页浮出来让用户看到并完成那一步，而不是干等 2 分钟后被 stale 逻辑重载。
                if (confirmedAt > 0L && !verifying
                        && System.currentTimeMillis() - confirmedAt > CONFIRM_SURFACE_DELAY_MS) {
                    confirmedAt = 0L;
                    this.log.log(Level.INFO, "confirmed but no sessionid after "
                            + (CONFIRM_SURFACE_DELAY_MS / 1000) + "s, surfacing login page");
                    this.manualVerify.set(true);
                }
                updateDiagnostic(snapshot, lastPollStatus);
                long aliveAt = Math.max(freshAt, pollAt);
                long now = System.currentTimeMillis();
                boolean awaitingConfirm = scannedAt > 0L;
                long staleBase = awaitingConfirm ? Math.max(aliveAt, scannedAt) : aliveAt;
                long staleLimit = awaitingConfirm ? QR_CONFIRM_TIMEOUT_MS : QR_STALE_MS;
                if (!verifying && now - staleBase > staleLimit) {
                    if (staleRestarts >= MAX_RESTARTS) {
                        publish(new State(Status.EXPIRED, "二维码长时间未刷新，请重试", image, "", "", false));
                        return;
                    }
                    staleRestarts++;
                    this.log.log(Level.INFO, "qr stale " + (staleLimit / 1000) + "s (freshAt=" + freshAt
                            + ", pollAt=" + pollAt + ", now=" + System.currentTimeMillis()
                            + "), reload login page (#" + staleRestarts + ")");
                    publish(new State(Status.STARTING, "二维码已失效，正在重新生成", image, "", "", false));
                    this.webView.loadUrl(LOGIN_URL, PAGE_LOAD_TIMEOUT_MS);
                    freshAt = System.currentTimeMillis();
                    pollAt = 0L;
                    frameRaw = null;
                    lastPoll = null;
                    scannedAt = 0L;
                    confirmedAt = 0L;
                    verifying = false;
                    keypadMode = false;
                    verifyClears = 0;
                    lastProbeKey = "";
                    this.manualDismiss.set(false);
                    this.webView.setInteractive(false);
                }
                sleep(POLL_INTERVAL_MS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            if (!this.cancelled) {
                this.log.log(Level.SEVERE, "扫码登录失败", e);
                publish(new State(Status.FAILED, "登录失败: " + e.getMessage(), null, "", "", false));
            }
        } finally {
            synchronized (this) {
                if (!this.cancelled) {
                    this.worker = null;
                }
            }
        }
    }

    /** 组装一句紧凑诊断，登录页每秒展示一次；用户截图即可定位问题出在哪一环。 */
    private void updateDiagnostic(Snapshot snapshot, String pollStatus) {
        StringBuilder text = new StringBuilder();
        text.append("内核=").append(this.webView.engineInfo());
        if (snapshot != null) {
            // Snapshot 字段来自 Json.optString（缺失即 ""，永不为 null）：用 isEmpty 判空，
            // "码=无" 才是可达分支——代表页面没发 get_qrcode 且 DOM 里没有二维码图，
            // 与「抓到响应但解不出 PNG（无效 N B）」是两种不同病因。
            String rawQr = !snapshot.qrRaw.isEmpty() ? snapshot.qrRaw
                    : (!snapshot.domQrRaw.isEmpty() ? snapshot.domQrRaw : null);
            if (rawQr != null) {
                // “码=有”只说明页面发过 get_qrcode；这里再区分响应能否解出 PNG，
                // 老内核上常见“有响应但数据不对”，只看“有/无”会误判。
                boolean valid = QrLoginProtocol.parseQrImage(rawQr) != null;
                text.append(" 码=").append(valid
                        ? "有效"
                        : "无效(" + rawQr.length() + "B)");
            } else {
                text.append(" 码=无");
            }
            text.append(" 登录弹窗=").append(snapshot.open ? "开" : "关");
            if (snapshot.compat) {
                text.append(" 兼容提示=有");
            }
            if (snapshot.url != null && !snapshot.url.isEmpty()) {
                text.append(" url=").append(snapshot.url.length() > 56
                        ? snapshot.url.substring(0, 56) + "…" : snapshot.url);
            }
        }
        // 出码前的循环调用方传 null：从快照推导页面自己的轮询状态——
        // 「页面登录 SDK 是否活着」就体现在有没有 check_qrconnect 往返。
        String poll = pollStatus;
        if (poll == null && snapshot != null && !snapshot.pollRaw.isEmpty()) {
            String derived = QrLoginProtocol.pollStatus(snapshot.pollRaw);
            poll = derived != null ? derived : "未知(" + snapshot.pollRaw.length() + "B)";
        }
        if (poll != null) {
            text.append(" 轮询=").append(poll);
        } else if (snapshot != null && snapshot.pollAt > 0L) {
            // 钩子对空响应体也记 pollAt：发了请求但回来的是空 body（风控拦的一般是这个形态）。
            text.append(" 轮询=空响应");
        }
        this.diagnostic = text.toString();
    }

    /**
     * 读页面快照（{@link QrLoginScripts#SNAPSHOT_SCRIPT}）：二维码帧、页面自己 check_qrconnect 的最新响应与时间戳。
     *
     * <p>扫描/过期状态由页面自己判定，本类只负责把结果如实带给 UI 与日志；登录成功仍以
     * Cookie 里出现 sessionid 为唯一判据。
     */
    private Snapshot snapshot() {
        try {
            String raw = this.webView.evaluateJavascript(QrLoginScripts.SNAPSHOT_SCRIPT, EVAL_TIMEOUT_MS);
            String json = QrLoginProtocol.unwrapJsResult(raw);
            if (json == null || json.isEmpty()) {
                return Snapshot.EMPTY;
            }
            Map<String, Object> map = Json.parseObject(json);
            long pollAt = 0L;
            try {
                pollAt = Long.parseLong(Json.optString(map, "pollAt"));
            } catch (NumberFormatException ignored) {
                // 页面还没轮询过
            }
            return new Snapshot(
                    Json.optString(map, "qr"),
                    Json.optString(map, "domQr"),
                    Json.optString(map, "poll"),
                    pollAt,
                    Json.optString(map, "url"),
                    Json.optBoolean(map, "verify"),
                    Json.optBoolean(map, "codeInput"),
                    Json.optBoolean(map, "open"),
                    Json.optBoolean(map, "selfQr"),
                    Json.optBoolean(map, "compat"),
                    true);
        } catch (Exception e) {
            return Snapshot.EMPTY;
        }
    }


    /**
     * 取走 UI 提交的验证码：注入页面 input，再点最接近它的那个「验证/登录/确认」按钮。
     *
     * <p>跑在轮询线程上（{@code evaluateJavascript} 会等主线程回调，主线自己不能调）。
     */
    private void submitPendingCode(byte[] frame) {
        String code = this.pendingCode.getAndSet(null);
        if (code == null) {
            return;
        }
        try {
            String fill = this.webView.evaluateJavascript(
                    QrLoginScripts.CODE_FILL_SCRIPT.replace("__CODE__", code), EVAL_TIMEOUT_MS);
            String click = this.webView.evaluateJavascript(QrLoginScripts.CODE_CLICK_SCRIPT, EVAL_TIMEOUT_MS);
            this.log.log(Level.INFO, "verification code submitted (" + code.length() + " digits): fill="
                    + fill + " click=" + click);
            publish(new State(Status.VERIFY, "验证码已提交，等待页面确认…", frame, "", "", true));
        } catch (Exception e) {
            this.log.log(Level.WARNING, "verification code submission failed", e);
            publish(new State(Status.VERIFY,
                    "验证码提交失败：" + e.getMessage() + "（可重新输入后再提交）", frame, "", "", true));
        }
    }


    /**
     * 页面快照。
     *
     * @param qrRaw     get_qrcode 原始响应
     * @param pollRaw   check_qrconnect 原始响应
     * @param pollAt    页面最近一次轮询的时间戳
     * @param verify    DOM 判到网页端二次身份验证
     * @param codeInput DOM 上真正渲染了一个「验证码」输入框（可由应用侧代填）
     */
    record Snapshot(String qrRaw, String domQrRaw, String pollRaw, long pollAt, String url,
                    boolean verify, boolean codeInput, boolean open, boolean selfQr,
                    boolean compat, boolean valid) {
        static final Snapshot EMPTY =
                new Snapshot(null, null, null, 0L, null, false, false, false, false, false, false);
    }

    /**
     * 轮询一次二维码。
     *
     * @return PNG 字节日志；尚未生成返回 null
     */
    public byte[] pollQr(long timeoutMs) throws Exception {
        String raw = this.webView.evaluateJavascript(QrLoginScripts.SNAPSHOT_SCRIPT, timeoutMs);
        String json = QrLoginProtocol.unwrapJsResult(raw);
        if (json == null || json.isEmpty()) {
            return null;
        }
        Map<String, Object> snapshot = Json.parseObject(json);
        byte[] image = QrLoginProtocol.parseQrImage(Json.optString(snapshot, "qr"));
        return image != null ? image : QrLoginProtocol.parseQrImage(Json.optString(snapshot, "domQr"));
    }

    private String readCookie() {
        try {
            String raw = this.webView.evaluateJavascript("document.cookie", EVAL_TIMEOUT_MS);
            String cookie = QrLoginProtocol.unwrapJsResult(raw);
            return cookie == null ? "" : cookie;
        } catch (Exception e) {
            return "";
        }
    }


    private void publish(State newState) {
        this.state = newState;
        Listener current = this.listener;
        if (current != null) {
            try {
                current.onState(newState);
            } catch (RuntimeException e) {
                this.log.log(Level.WARNING, "扫码状态回调失败", e);
            }
        }
        this.log.log(Level.INFO, "状态=" + newState.status() + " " + newState.message());
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
