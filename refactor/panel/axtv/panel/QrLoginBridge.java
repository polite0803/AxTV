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

    /** 扫码状态（原 {@code uy3}，枚举顺序一致）。 */
    public enum Status {
        IDLE, STARTING, WAITING_SCAN, SCANNED, EXPIRED, SUCCESS, FAILED
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
         * 安全验证（account_flow=verify）时把隐藏 WebView 显示出来并允许交互。
         * 账号风控要求短信/滑块验证时，页面必须可见才能完成，否则会一直停在「已扫码」。
         */
        void setInteractive(boolean interactive);

        /** 当前 Activity 是否还在（原 ez3 用 k21 的弱引用判断，取不到就提示「请回到应用界面内再发起登录」）。 */
        boolean isHostAvailable();
    }

    public interface Listener {
        void onState(State state);
    }

    /** 页面注入脚本（原 sy3，逐字保留：钩住 XHR/fetch 记录二维码与轮询响应）。 */
    public static final String HOOK_SCRIPT = """
            (function(){
              if (window.__dyHook) return;
              window.__dyHook = true;
              window.__dyQr = null;
              window.__dyPoll = null;
              window.__dyPollAt = 0;
              function save(url, text) {
                try {
                  // 自建会话已经拿到码时，页面自己的响应不再覆盖：两套 token 交错会让界面
                  // 显示一张没人轮询的旧码。自建会话还没出码前仍接受页面响应，避免两边都空。
                  if (window.__dySelfQr) return;
                  if (url.indexOf('/passport/web/get_qrcode/') >= 0) window.__dyQr = text;
                  else if (url.indexOf('/passport/web/check_qrconnect/') >= 0) {
                    window.__dyPoll = text;
                    window.__dyPollAt = Date.now();
                  }
                } catch (e) {}
              }
              var open = XMLHttpRequest.prototype.open;
              var send = XMLHttpRequest.prototype.send;
              XMLHttpRequest.prototype.open = function(method, url) {
                try { this.__dyUrl = String(url || ''); } catch (e) {}
                return open.apply(this, arguments);
              };
              XMLHttpRequest.prototype.send = function() {
                var xhr = this;
                try {
                  xhr.addEventListener('load', function() {
                    var text = '';
                    try { text = xhr.responseText; } catch (e) {
                      try { text = JSON.stringify(xhr.response); } catch (e2) { text = ''; }
                    }
                    save(xhr.__dyUrl, text);
                  });
                } catch (e) {}
                return send.apply(this, arguments);
              };
              if (window.fetch) {
                var origFetch = window.fetch;
                window.fetch = function(input) {
                  var url = '';
                  try { url = typeof input === 'string' ? input : ((input && input.url) || ''); } catch (e) {}
                  return origFetch.apply(this, arguments).then(function(res) {
                    try { res.clone().text().then(function(t) { save(url, t); }); } catch (e) {}
                    return res;
                  });
                };
              }
            })()
            """;

    /** 读快照脚本（原 ez3.m3961j 里的 JS，逐字保留）。 */
    public static final String SNAPSHOT_SCRIPT =
            "\n(function(){\n  try {\n    return JSON.stringify({\n      qr: window.__dyQr || null,\n"
                    + "      poll: window.__dyPoll || null,\n      pollAt: window.__dyPollAt || 0\n"
                    + "    });\n  } catch (e) { return JSON.stringify({error:String(e)}); }\n})()\n";

    /**
     * 出码参数：真实登录页请求 get_qrcode / check_qrconnect 时用的那一套（aid=2906 + request_host）。
     * 缺 request_host 或 aid 换成 6383 都会被风控判成 4031，逐字保留。
     */
    static final String SELF_QR_PARAMS =
            "passport_jssdk_version=3.4.17&passport_jssdk_type=normal&is_from_ttaccountsdk=1"
                    + "&aid=2906&language=zh&account_app_language=zh-CN&ts=1793880000"
                    + "&next=https%3A%2F%2Fcreator.douyin.com%2F&need_short_url=true&need_logo=false"
                    + "&is_new_login=1&is_from_iesaccountsaas=1&p_ui=2.4.19&account_sdk_source=web"
                    + "&p_js_v=3.4.17&p_js_t=pro&p_zt=3.3.34&p_ver=1.1.4-beta.5&p_ver_real=0"
                    + "&request_host=https%253A%252F%252Fcreator.douyin.com&p_bd=1.0.1.16";

    /**
     * 页面自身不出码时的自建会话脚本。
     *
     * <p>电视盒子的 WebView 内核常比手机落后好几代，登录页的 UMD 包跑不起来时，页面既不会请求
     * get_qrcode、也不会轮询，界面只能等到「二维码生成超时」。本脚本只用 ES5 + XMLHttpRequest
     * （不依赖 fetch/Promise/箭头函数），在同一页面内自己出码并轮询，结果仍写回
     * {@code __dyQr/__dyPoll}，主流程无需区分来源。
     *
     * <p>已实测：同源请求出码与轮询都不需要 sign/a_bogus，只要 aid=2906 与 request_host 正确。
     */
    public static final String SELF_QR_SCRIPT =
            "(function(){\n"
                    + "  if (window.__dySelf) return;\n"
                    + "  window.__dySelf = true;\n"
                    + "  var P = '" + SELF_QR_PARAMS + "';\n"
                    + "  var token = null;\n"
                    + "  var confirmedAt = 0;\n"
                    + "  var busy = false;\n"
                    + "  function request(method, url, body, done) {\n"
                    + "    try {\n"
                    + "      var xhr = new XMLHttpRequest();\n"
                    + "      xhr.open(method, url, true);\n"
                    + "      xhr.withCredentials = true;\n"
                    + "      if (body) xhr.setRequestHeader('Content-Type', 'application/x-www-form-urlencoded');\n"
                    + "      xhr.onreadystatechange = function(){\n"
                    + "        if (xhr.readyState !== 4) return;\n"
                    + "        try { done(xhr.responseText); } catch (e) {}\n"
                    + "      };\n"
                    + "      xhr.send(body || null);\n"
                    + "    } catch (e) {}\n"
                    + "  }\n"
                    + "  function getQr() {\n"
                    + "    request('GET', '/passport/web/get_qrcode/?' + P, null, function(text){\n"
                    + "      var t = null;\n"
                    + "      try { t = JSON.parse(text).data.token; } catch (e) {}\n"
                    + "      if (!t) return;\n"
                    + "      window.__dyQr = text;\n"
                    + "      window.__dySelfQr = true;\n"
                    + "      token = t;\n"
                    + "    });\n"
                    + "  }\n"
                    + "  function poll() {\n"
                    + "    if (busy || confirmedAt) return;\n"
                    + "    if (!token) { getQr(); return; }\n"
                    + "    busy = true;\n"
                    + "    request('POST', '/passport/web/check_qrconnect/?' + P, 'token=' + encodeURIComponent(token), function(text){\n"
                    + "      busy = false;\n"
                    + "      window.__dyPoll = text;\n"
                    + "      window.__dyPollAt = new Date().getTime();\n"
                    + "      var d = null;\n"
                    + "      try { d = JSON.parse(text).data; } catch (e) { return; }\n"
                    + "      if (!d) return;\n"
                    + "      if (d.status === 'confirmed') {\n"
                    + "        confirmedAt = new Date().getTime();\n"
                    + "        window.location.href = d.redirect_url ? d.redirect_url : '/';\n"
                    + "      } else if (d.status === 'expired' || d.status === 'refused') {\n"
                    + "        token = null;\n"
                    + "      }\n"
                    + "    });\n"
                    + "  }\n"
                    + "  getQr();\n"
                    + "  setInterval(poll, 2000);\n"
                    + "})()\n";

    /** 自建会话的出码参数（测试用；生产代码直接引用常量）。 */
    public static String selfQrParams() {
        return SELF_QR_PARAMS;
    }

    /** 自建会话脚本（测试用；生产代码直接引用常量）。 */
    public static String selfQrScript() {
        return SELF_QR_SCRIPT;
    }

    public static final long PAGE_LOAD_TIMEOUT_MS = 60_000L;    public static final long QR_TIMEOUT_MS = 60_000L;
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

    private final WebViewHost webView;
    private final Logger log = Logger.getLogger("QrLoginManager");

    private volatile State state = State.idle();
    private volatile Listener listener;
    private volatile boolean cancelled;
    private Thread worker;
    private int restartCount;

    public QrLoginBridge(WebViewHost webView) {
        this.webView = webView;
    }

    public State state() {
        return this.state;
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    /** 开始登录（原 {@code ez3.m3964m()}：重试计数清零 + 「正在打开抖音登录页」）。 */
    public synchronized void start() {
        if (this.webView == null || this.worker != null) {
            return;
        }
        if (!this.webView.isHostAvailable()) {
            // 原实现：C1902zy.m12465i("请回到应用界面内再发起登录")
            publish(new State(Status.FAILED, "请回到应用界面内再发起登录", null, "", "", false));
            return;
        }
        this.cancelled = false;
        this.restartCount = 0;
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
            this.webView.evaluateJavascript(HOOK_SCRIPT, EVAL_TIMEOUT_MS);

            // 页面自己在 20s 内没出码（电视端 WebView 内核旧、登录页 UMD 包跑不起来）时，
            // 改用页面内自建会话继续出码，而不是干等 60s 后报「二维码生成超时」。
            long firstDeadline = System.currentTimeMillis() + QR_FIRST_TIMEOUT_MS;
            long deadline = System.currentTimeMillis() + QR_TIMEOUT_MS;
            byte[] image = null;
            String imageRaw = null;
            boolean selfStarted = false;
            while (!this.cancelled && System.currentTimeMillis() < deadline) {
                Snapshot head = snapshot();
                byte[] decoded = head.qrRaw == null ? null : parseQrImage(head.qrRaw);
                if (decoded != null) {
                    image = decoded;
                    imageRaw = head.qrRaw;
                    break;
                }
                if (!selfStarted && System.currentTimeMillis() > firstDeadline) {
                    selfStarted = true;
                    // 页面已经耗掉首轮预算，自建会话重新起算，否则会剩不到 40s 就报超时。
                    deadline = System.currentTimeMillis() + QR_TIMEOUT_MS;
                    this.log.log(Level.INFO, "page did not produce qr in " + QR_FIRST_TIMEOUT_MS
                            + "ms, start self qr session");
                    try {
                        this.webView.evaluateJavascript(SELF_QR_SCRIPT, EVAL_TIMEOUT_MS);
                    } catch (Exception e) {
                        this.log.log(Level.WARNING, "self qr session failed to start", e);
                    }
                }
                sleep(POLL_INTERVAL_MS);
            }
            if (this.cancelled) {
                return;
            }
            if (image == null) {
                // 原实现：C1902zy.m12465i("抖音二维码生成超时，请重试")
                publish(new State(Status.FAILED,
                        selfStarted
                                ? "抖音二维码生成超时（登录页未出码，自动出码也失败），请检查网络后重试"
                                : "抖音二维码生成超时，请重试",
                        null, "", "", false));
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
            boolean verifying = false;
            while (!this.cancelled) {
                String cookie = readCookie();
                if (AuthStore.hasSessionId(cookie)) {
                    this.webView.setInteractive(false);
                    publish(new State(Status.SUCCESS, "登录成功", image, cookie, "", false));
                    return;
                }
                Snapshot snapshot = snapshot();
                if (snapshot.qrRaw != null && !snapshot.qrRaw.equals(frameRaw)) {
                    byte[] next = parseQrImage(snapshot.qrRaw);
                    if (next != null) {
                        image = next;
                        frameRaw = snapshot.qrRaw;
                        freshAt = System.currentTimeMillis();
                        scannedAt = 0L;
                        this.log.log(Level.INFO, "qr frame updated, bytes=" + next.length + ", freshAt=" + freshAt);
                        publish(new State(Status.WAITING_SCAN, "等待扫码", image, "", "", false));
                    }
                }
                if (snapshot.pollAt > pollAt) {
                    pollAt = snapshot.pollAt;
                }
                if (snapshot.pollRaw != null && !snapshot.pollRaw.equals(lastPoll)) {
                    lastPoll = snapshot.pollRaw;
                    this.log.log(Level.INFO, "check_qrconnect: " + redactPoll(snapshot.pollRaw));
                    // 页面换码走的是 check_qrconnect 响应里的 data.qrcode（不是重新发 get_qrcode），
                    // 所以这里也要解一帧，否则界面会一直显示已经过期的那张码。
                    byte[] rotated = parseQrImage(snapshot.pollRaw);
                    if (rotated != null && !java.util.Arrays.equals(rotated, image)) {
                        image = rotated;
                        freshAt = System.currentTimeMillis();
                        frameRaw = null;
                        scannedAt = 0L;
                        this.log.log(Level.INFO, "qr rotated from check_qrconnect, bytes="
                                + rotated.length + ", freshAt=" + freshAt);
                        publish(new State(Status.WAITING_SCAN, "等待扫码", image, "", "", false));
                    }
                    String pollStatus = pollStatus(snapshot.pollRaw);
                    if (pollStatus != null && !pollStatus.equals(lastPollStatus)) {
                        lastPollStatus = pollStatus;
                        this.log.log(Level.INFO, "poll status=" + pollStatus);
                        if ("scan".equals(pollStatus) || "scanned".equals(pollStatus)
                                || "confirmed".equals(pollStatus)) {
                            if (scannedAt == 0L) {
                                scannedAt = System.currentTimeMillis();
                            }
                            publish(new State(Status.SCANNED, "已扫码，请在手机上确认登录", image, "", "", false));
                        }
                    }
                    // 账号风控会把确认流程转成安全验证（短信/滑块），页面必须可见才能继续，
                    // 否则会永远停在「已扫码」，超时后还会把验证页重载掉。
                    if ("verify".equals(pollAccountFlow(snapshot.pollRaw)) && !verifying) {
                        verifying = true;
                        this.webView.setInteractive(true);
                        this.log.log(Level.INFO, "account_flow=verify, show login page for verification");
                        publish(new State(Status.SCANNED,
                                "该账号需要安全验证：请在登录页完成验证后等待自动返回", image, "", "", false));
                    }
                }
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
                    if (selfStarted) {
                        // 重载会清掉页面里的自建会话（__dySelf），旧 token 也随之失效。
                        try {
                            this.webView.evaluateJavascript(SELF_QR_SCRIPT, EVAL_TIMEOUT_MS);
                        } catch (Exception e) {
                            this.log.log(Level.WARNING, "self qr session failed to restart", e);
                        }
                    }
                    freshAt = System.currentTimeMillis();
                    pollAt = 0L;
                    frameRaw = null;
                    lastPoll = null;
                    scannedAt = 0L;
                    verifying = false;
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

    /**
     * 读页面快照（{@link #SNAPSHOT_SCRIPT}）：二维码帧、页面自己 check_qrconnect 的最新响应与时间戳。
     *
     * <p>扫描/过期状态由页面自己判定，本类只负责把结果如实带给 UI 与日志；登录成功仍以
     * Cookie 里出现 sessionid 为唯一判据。
     */
    private Snapshot snapshot() {
        try {
            String raw = this.webView.evaluateJavascript(SNAPSHOT_SCRIPT, EVAL_TIMEOUT_MS);
            String json = unwrapJsResult(raw);
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
                    Json.optString(map, "poll"),
                    pollAt);
        } catch (Exception e) {
            return Snapshot.EMPTY;
        }
    }

    /** 从 check_qrconnect 原始响应里抠 {@code "status":"..."}（页面用它区分 未扫码/已扫码）。 */
    static String pollStatus(String poll) {
        if (poll == null) {
            return null;
        }
        int key = poll.indexOf("\"status\"");
        if (key < 0) {
            return null;
        }
        int colon = poll.indexOf(':', key);
        int open = colon < 0 ? -1 : poll.indexOf('"', colon + 1);
        int close = open < 0 ? -1 : poll.indexOf('"', open + 1);
        if (close < 0) {
            return null;
        }
        return poll.substring(open + 1, close);
    }

    /** 登录流程类型：{@code account_flow=verify} 表示账号触发了安全验证。 */
    static String pollAccountFlow(String poll) {
        if (poll == null) {
            return null;
        }
        int key = poll.indexOf("\"account_flow\"");
        if (key < 0) {
            return null;
        }
        int colon = poll.indexOf(':', key);
        int open = colon < 0 ? -1 : poll.indexOf('"', colon + 1);
        int close = open < 0 ? -1 : poll.indexOf('"', open + 1);
        if (close < 0) {
            return null;
        }
        return poll.substring(open + 1, close);
    }

    /** 日志脱敏：token/ticket/sessionid 的值换成 ***，并截断超长内容。 */
    static String redactPoll(String poll) {
        if (poll == null) {
            return "";
        }
        String text = poll.replaceAll("(?i)(\"(?:token|ticket|sessionid|sessionid_ss)\"\\s*:\\s*\")[^\"]*\"", "$1***\"");
        return text.length() > 240 ? text.substring(0, 240) + "..." : text;
    }

    /** 页面快照：{@code qr} 为 get_qrcode 原始响应，{@code poll} 为 check_qrconnect 原始响应。 */
    record Snapshot(String qrRaw, String pollRaw, long pollAt) {
        static final Snapshot EMPTY = new Snapshot(null, null, 0L);
    }

    /**
     * 轮询一次二维码。
     *
     * @return PNG 字节日志；尚未生成返回 null
     */
    public byte[] pollQr(long timeoutMs) throws Exception {
        String raw = this.webView.evaluateJavascript(SNAPSHOT_SCRIPT, timeoutMs);
        String json = unwrapJsResult(raw);
        if (json == null || json.isEmpty()) {
            return null;
        }
        Map<String, Object> snapshot = Json.parseObject(json);
        return parseQrImage(Json.optString(snapshot, "qr"));
    }

    /**
     * 从 {@code {"qr":"<内层 JSON>"}} 里取出 {@code data.qrcode} 的 Base64 并解码。
     * 原实现 {@code ez3.m3953h} + {@code m3952e}：先去 {@code data:image/…base64,} 前缀再 Base64 解码。
     *
     * @return PNG 字节；缺失或格式不对返回 null
     */
    public static byte[] parseQrImage(String qrField) {
        if (qrField == null) {
            return null;
        }
        String raw = qrField.trim();
        if (raw.isEmpty() || "null".equals(raw)) {
            return null;
        }
        String code;
        try {
            Map<String, Object> outer = Json.parseObject(raw);
            Object data = outer.get("data");
            code = data instanceof Map<?, ?> map ? String.valueOf(map.get("qrcode")) : null;
        } catch (RuntimeException e) {
            return null;
        }
        if (code == null || code.isEmpty() || "null".equals(code)) {
            return null;
        }
        String base64 = code;
        int marker = base64.indexOf("base64,");
        if (base64.startsWith("data:image/") && marker > 0) {
            base64 = base64.substring(marker + "base64,".length());
        }
        try {
            return Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String readCookie() {
        try {
            String raw = this.webView.evaluateJavascript("document.cookie", EVAL_TIMEOUT_MS);
            String cookie = unwrapJsResult(raw);
            return cookie == null ? "" : cookie;
        } catch (Exception e) {
            return "";
        }
    }

    /** Android 的 evaluateJavascript 会把结果再包一层 JSON 字符串（原 od2.m8561d 的 JSONTokener 处理）。 */
    static String unwrapJsResult(String raw) {
        if (raw == null) {
            return null;
        }
        String text = raw.trim();
        if (text.isEmpty() || "null".equals(text)) {
            return null;
        }
        if (text.length() >= 2 && text.charAt(0) == '"' && text.charAt(text.length() - 1) == '"') {
            try {
                Object decoded = Json.parse(text);
                if (decoded instanceof String value) {
                    return value;
                }
            } catch (RuntimeException ignored) {
                // 不是合法 JSON 字符串，按原文返回
            }
        }
        return text;
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
