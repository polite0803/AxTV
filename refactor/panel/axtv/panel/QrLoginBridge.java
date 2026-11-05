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

            long deadline = System.currentTimeMillis() + QR_TIMEOUT_MS;
            byte[] image = null;
            String imageRaw = null;
            while (!this.cancelled && System.currentTimeMillis() < deadline) {
                Snapshot head = snapshot();
                byte[] decoded = head.qrRaw == null ? null : parseQrImage(head.qrRaw);
                if (decoded != null) {
                    image = decoded;
                    imageRaw = head.qrRaw;
                    break;
                }
                sleep(POLL_INTERVAL_MS);
            }
            if (this.cancelled) {
                return;
            }
            if (image == null) {
                // 原实现：C1902zy.m12465i("抖音二维码生成超时，请重试")
                publish(new State(Status.FAILED, "抖音二维码生成超时，请重试", null, "", "", false));
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
            while (!this.cancelled) {
                String cookie = readCookie();
                if (AuthStore.hasSessionId(cookie)) {
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
                        this.log.log(Level.INFO, "qr rotated from check_qrconnect, bytes="
                                + rotated.length + ", freshAt=" + freshAt);
                        publish(new State(Status.WAITING_SCAN, "等待扫码", image, "", "", false));
                    }
                    String pollStatus = pollStatus(snapshot.pollRaw);
                    if (pollStatus != null && !pollStatus.equals(lastPollStatus)) {
                        lastPollStatus = pollStatus;
                        this.log.log(Level.INFO, "poll status=" + pollStatus);
                        if ("scan".equals(pollStatus) || "scanned".equals(pollStatus)) {
                            publish(new State(Status.SCANNED, "已扫码，请在手机上确认登录", image, "", "", false));
                        }
                    }
                }
                long aliveAt = Math.max(freshAt, pollAt);
                if (System.currentTimeMillis() - aliveAt > QR_STALE_MS) {
                    if (staleRestarts >= MAX_RESTARTS) {
                        publish(new State(Status.EXPIRED, "二维码长时间未刷新，请重试", image, "", "", false));
                        return;
                    }
                    staleRestarts++;
                    this.log.log(Level.INFO, "qr stale " + (QR_STALE_MS / 1000) + "s (freshAt=" + freshAt
                            + ", pollAt=" + pollAt + ", now=" + System.currentTimeMillis()
                            + "), reload login page (#" + staleRestarts + ")");
                    publish(new State(Status.STARTING, "二维码已失效，正在重新生成", image, "", "", false));
                    this.webView.loadUrl(LOGIN_URL, PAGE_LOAD_TIMEOUT_MS);
                    freshAt = System.currentTimeMillis();
                    pollAt = 0L;
                    frameRaw = null;
                    lastPoll = null;
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
