/*
 * 来源: src/app/p000/C1400n.java (243 行，case 0 = 引擎初始化)
 *       src/app/p000/C1608s.java:34-44,87-120,229-267 (生命周期 / 就绪判定 / 销毁)
 *       src/app/p000/C1496p.java:62-83 (初始化任务派发)
 *       src/app/p000/RunnableC0688h.java:64-79 (销毁任务)
 *       src/app/p000/ThreadFactoryC0651g.java:19-22 (线程名 "quickjs-abogus")
 *       src/app/p000/qb1.java (单线程 dispatcher)
 * 原职责: QuickJS 上下文创建、browser shim 注入、abogus_dy.js/sign.js 加载、initABogus() 调用、
 *         getABogus 可用性自检、__ac(byted_acrawler) 初始化、超时与销毁
 * 重构说明: 把散在 4 个类 + 5 个协程状态机里的生命周期收拢成一个类；协程换成单线程 executor
 */
package axtv.sign;

import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * a_bogus 引擎：持有一个 QuickJS 上下文，负责初始化、自检、串行执行与销毁。
 *
 * <p>线程模型（原实现 {@code qb1} 包 {@code Executors.newSingleThreadExecutor(ThreadFactoryC0651g(0))}）：
 * 所有 JS 调用都在名为 {@value #THREAD_NAME} 的单条守护线程上串行执行。
 * 因此本类的公开方法都是阻塞的，Android 侧应在 IO 协程/后台线程调用。
 *
 * <p>初始化时机：与原始实现一致——没有独立的启动入口，第一次 {@link #ensureReady()} 时才加载脚本，
 * 加载结果缓存到 {@link #isReady()}。
 */
public final class AbogusEngine implements AutoCloseable {

    /** 原 TAG：C1400n / C0761j / C1608s / RunnableC0688h / C1285k 全部使用（C1400n:89 等）。 */
    public static final String TAG = "ABogusSigner";

    /** 线程名，原 ThreadFactoryC0651g.java:20；C1608s:89 用它判断是否已在 JS 线程上。 */
    public static final String THREAD_NAME = "quickjs-abogus";

    /** 初始化超时：C1608s.java:254 {@code qi4.m9585n(30000L, ...)}（withTimeout 毫秒）。 */
    public static final long INIT_TIMEOUT_MS = 30_000L;

    /**
     * 单次签名调用超时：C1608s.java:193、:223、:332、:346
     * {@code Renderer.DEFAULT_DURATION_TO_PROGRESS_US}。
     *
     * <p>该常量属于 media3，值为 2_500_000（微秒），但原代码把它当作 withTimeout 的<b>毫秒</b>传入，
     * 实际超时 ≈ 41.7 分钟，等于“不会超时”。数值原样保留，见 NOTES.md「实现风险」。
     */
    public static final long CALL_TIMEOUT_MS = 2_500_000L;

    /** 销毁派发等待：C1608s.java:105 {@code .get(5L, TimeUnit.SECONDS)}。 */
    public static final long DESTROY_WAIT_SECONDS = 5L;

    /** 脚本资源名。原实现只加载这两个（C1400n:93、C1400n:135）。 */
    public static final String ASSET_ABOGUS_DY = "abogus_dy.js";
    public static final String ASSET_SIGN = "sign.js";

    /**
     * 原实现把 browser shim 硬编码成 Java 字符串常量（C1400n:90，11k 字符）。
     * 这里原样抽成资源文件，内容逐字节还原，未做任何语义改动。
     */
    public static final String ASSET_BROWSER_SHIM = "browser-shim.js";

    /** sign.js 补丁：C1400n.java:142，把 byted_acrawler 的 crawler 暴露成 globalThis.__ac。 */
    static final String SIGN_JS_PATCH_FROM = "crawler = _0x5c2014";
    static final String SIGN_JS_PATCH_TO = "crawler = _0x5c2014; globalThis.__ac = crawler";

    // ---- 自检脚本，逐字来自 C1400n.java ----

    /** C1400n.java:105 */
    static final String SCRIPT_BDMS_PRESENT =
            "(function(){ return typeof window.bdms !== 'undefined' && typeof window.bdms.init === 'function'; })()";

    /** C1400n.java:109 */
    static final String SCRIPT_INIT_ABOGUS = "initABogus()";

    /** C1400n.java:116 */
    static final String SCRIPT_GET_ABOGUS_PRESENT =
            "(function(){ return typeof getABogus === 'function' && typeof get_ab === 'function'; })()";

    /** C1400n.java:120 自检请求参数（固定串，不参与真实业务）。 */
    static final String SELF_TEST_QUERY = "aid=6383&device_platform=webapp";

    /** C1400n.java:120 */
    static final String SCRIPT_SELF_TEST =
            "(function(){ try { var r = getABogus('" + SELF_TEST_QUERY + "', ''); return r; }"
                    + " catch(e) { return JSON.stringify({ok:false,error:String(e)}); } })()";

    /** C1400n.java:146 预热 getMSSDKSignature，触发 sign.js 内部初始化；异常被吞。 */
    static final String SCRIPT_SIGN_JS_WARMUP =
            "try { getMSSDKSignature('', '"
                    + "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36"
                    + "') } catch(e) {}";

    /** C1400n.java:150 */
    static final String SCRIPT_AC_PRESENT =
            "(function(){ return typeof globalThis.__ac !== 'undefined' && typeof globalThis.__ac.init === 'function'; })()";

    /** C1400n.java:153 */
    static final String SCRIPT_AC_INIT = "globalThis.__ac.init({aid:99999999,dfp:0})";

    /** 日志截断长度：C1400n:125 {@code jx4.m6886f0(200, str2)}。 */
    private static final int LOG_PREVIEW_LIMIT = 200;

    private final Assets assets;
    private final JsRuntime.Factory runtimeFactory;
    private final SignLog log;
    private final ExecutorService executor;

    /** 对应 C1608s.f19460e。 */
    private volatile JsRuntime runtime;

    /** 对应 C1608s.f19458c：a_bogus 运行时可用。 */
    private volatile boolean abogusReady;

    /** 对应 C1608s.f19459d：byted_acrawler（__ac_signature）可用。 */
    private volatile boolean acrawlerReady;

    /** 最近一次 a_bogus 初始化失败原因；供 UI 诊断（成功不清空，保留现场）。 */
    private volatile String lastError = "";

    public AbogusEngine(Assets assets, JsRuntime.Factory runtimeFactory, SignLog log) {
        this.assets = Objects.requireNonNull(assets, "assets");
        this.runtimeFactory = Objects.requireNonNull(runtimeFactory, "runtimeFactory");
        this.log = log == null ? SignLog.disabled() : log;
        // C1608s.java:36-38
        this.executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, THREAD_NAME);
            thread.setDaemon(true);
            return thread;
        });
    }

    public boolean isReady() {
        return abogusReady;
    }

    /** 最近一次 a_bogus 初始化失败原因；空串表示没有记录到错误。 */
    public String lastError() {
        return lastError;
    }

    public boolean isAcrawlerReady() {
        return acrawlerReady;
    }

    /**
     * 确保引擎可用，必要时触发一次初始化。
     *
     * <p>对应 {@code C1608s.m10096d}（C1608s.java:229-267）：已就绪直接返回 true；
     * 否则 withTimeout(30000) 执行初始化。
     *
     * <p>返回值取初始化后的 {@link #isReady()}，而不是初始化任务的返回值——
     * 原反编译产物在这条路径上有一条 jadx 无法还原的 {@code break}（C1400n.java:159），
     * 语义存在歧义，见 NOTES.md「存疑」。
     */
    public boolean ensureReady() {
        if (abogusReady) {
            return true;
        }
        try {
            submit(this::initialize, INIT_TIMEOUT_MS);
        } catch (TimeoutException e) {
            // 原实现在此处静默返回 false（m10096d 拿不到结果即视为 false），由调用方打 "not ready"。
            lastError = "初始化超时（30000ms）";
            log.e(TAG, "a_bogus runtime init timed out");
            return false;
        } catch (RuntimeException e) {
            lastError = "初始化异常：" + e.getClass().getSimpleName() + ": " + e.getMessage();
            log.e(TAG, "a_bogus runtime init failed", e);
            return false;
        }
        return abogusReady;
    }

    /**
     * 在 JS 线程上执行脚本。
     *
     * @param timeoutMs 超时毫秒；&lt;= 0 表示不限时
     * @return 脚本返回值，运行时尚不存在时返回 {@code null}
     * @throws TimeoutException 超时（原实现返回 null 后由调用方打 "timed out"）
     * @throws RuntimeException 脚本或运行时异常（原实现由 evaluate 抛出 Exception）
     */
    public Object evaluate(String script, long timeoutMs) throws TimeoutException {
        JsRuntime current = this.runtime;
        if (current == null) {
            return null;
        }
        return submit(() -> current.eval(script), timeoutMs);
    }

    /**
     * 初始化：shim -> abogus_dy.js -> initABogus -> 自检 -> sign.js -> __ac。
     * 对应 {@code C1400n.invokeSuspend} case 0（C1400n.java:83-207），在 JS 线程上执行。
     */
    private Boolean initialize() {
        try {
            runtime = runtimeFactory.create(); // C1400n:87-88
            log.d(TAG, "QuickJS: loading browser shim..."); // C1400n:89
            eval(stripCarriageReturns(assets.readText(ASSET_BROWSER_SHIM))); // C1400n:90（含 \r 剥离）
            log.d(TAG, "QuickJS: browser shim loaded OK"); // C1400n:91

            log.d(TAG, "QuickJS: loading abogus_dy.js..."); // C1400n:92
            String abogusScript = stripCarriageReturns(assets.readText(ASSET_ABOGUS_DY)); // C1400n:93-101
            log.d(TAG, "QuickJS: abogus_dy.js read (" + abogusScript.length() + " chars)"); // C1400n:103
            eval(abogusScript); // C1400n:104

            boolean bdmsPresent = asBoolean(eval(SCRIPT_BDMS_PRESENT)); // C1400n:105-107
            log.d(TAG, "QuickJS: bdms object exists: " + bdmsPresent);

            log.d(TAG, "QuickJS: calling initABogus()..."); // C1400n:108
            if (!asBoolean(eval(SCRIPT_INIT_ABOGUS))) { // C1400n:109-111
                lastError = "initABogus() 返回 false（bdms SDK 不可用）";
                log.e(TAG, "initABogus() returned false - bdms SDK _v not available"); // C1400n:112
                destroyInline(); // C1400n:113 -> C1608s.m10094a()
                return Boolean.FALSE;
            }

            boolean getAbogusPresent = asBoolean(eval(SCRIPT_GET_ABOGUS_PRESENT)); // C1400n:116-118
            log.d(TAG, "QuickJS: getABogus available: " + getAbogusPresent); // C1400n:119

            Object testSign = eval(SCRIPT_SELF_TEST); // C1400n:120
            String testResult = testSign instanceof String text ? text : "null"; // C1400n:121-123
            log.d(TAG, "QuickJS: test sign result = " + take(testResult, LOG_PREVIEW_LIMIT)); // C1400n:125

            if (!getAbogusPresent) { // C1400n:126-130
                lastError = "getABogus / get_ab 初始化后不可用";
                log.e(TAG, "getABogus / get_ab not available after init");
                destroyInline();
                return Boolean.FALSE;
            }

            abogusReady = true; // C1400n:131
            log.i(TAG, "QuickJS a_bogus runtime initialised successfully (abogus_dy.js)"); // C1400n:132

            loadSignJs(); // C1400n:134-174，失败不影响 a_bogus 可用性
            return Boolean.TRUE; // C1400n:175-176
        } catch (Exception e) { // C1400n:177-187
            lastError = "初始化失败：" + e.getClass().getSimpleName() + ": " + e.getMessage();
            log.e(TAG, "Failed to initialise QuickJS a_bogus runtime", e);
            logCauses(e, 5); // C1400n:182-187（最多 5 层）
            destroyInline(); // C1400n:188
            return Boolean.FALSE;
        }
    }

    /** sign.js + byted_acrawler 初始化，对应 C1400n.java:134-174。异常只记录，不上抛。 */
    private void loadSignJs() {
        log.d(TAG, "QuickJS: loading sign.js..."); // C1400n:134
        try {
            String signScript = assets.readText(ASSET_SIGN)
                    .replace("\r", ""); // C1400n:141
            signScript = signScript.replace(SIGN_JS_PATCH_FROM, SIGN_JS_PATCH_TO); // C1400n:142
            log.d(TAG, "QuickJS: sign.js read (" + signScript.length() + " chars)"); // C1400n:143
            eval(signScript); // C1400n:144

            try {
                eval(SCRIPT_SIGN_JS_WARMUP); // C1400n:145-149
            } catch (RuntimeException expected) {
                log.d(TAG, "QuickJS: getMSSDKSignature call threw (expected), checking __ac...");
            }

            if (asBoolean(eval(SCRIPT_AC_PRESENT))) { // C1400n:150-152
                eval(SCRIPT_AC_INIT); // C1400n:153
                acrawlerReady = true; // C1400n:154
                log.i(TAG, "QuickJS: byted_acrawler initialised for __ac_signature"); // C1400n:155
            } else {
                log.w(TAG, "QuickJS: byted_acrawler not found in sign.js"); // C1400n:157
            }
        } catch (Exception e) { // C1400n:168-171
            log.e(TAG, "QuickJS: failed to load sign.js", e);
        }
    }

    /** 在 JS 线程上直接销毁（调用方必须已在 JS 线程，或在派发失败时兜底）。对应 C1608s:88-100。 */
    private Boolean destroyInline() {
        JsRuntime current = this.runtime;
        if (current != null) {
            try {
                current.close();
            } catch (Exception e) {
                log.e(TAG, "Failed to destroy QuickJSContext", e); // C1608s:96 / RunnableC0688h:74
            }
        }
        // 原 RunnableC0688h:72-78 只在 destroy 抛异常时才清字段，成功路径留下已销毁的悬空引用；
        // 这里统一清理（见 NOTES.md「实现风险」第 2 条）。
        this.runtime = null;
        this.abogusReady = false;
        this.acrawlerReady = false;
        return Boolean.TRUE;
    }

    /**
     * 销毁并关闭线程池。对应 {@code C1608s.m10094a}（C1608s.java:87-120）：
     * 已在 JS 线程则就地销毁，否则派发到 JS 线程并最多等 5 秒。
     *
     * <p>注意：原实现无论销毁成功与否都会 {@code f19457b.close()}（关闭线程池），
     * 因此一旦关闭，本实例不可再用于签名。这里保持同样语义。
     */
    @Override
    public void close() {
        if (THREAD_NAME.equals(Thread.currentThread().getName())) { // C1608s:89
            destroyInline();
        } else {
            try {
                submit(this::destroyInline, TimeUnit.SECONDS.toMillis(DESTROY_WAIT_SECONDS)); // C1608s:103-105
            } catch (Exception e) {
                log.e(TAG, "Failed to dispatch destroy", e); // C1608s:107
                destroyInline(); // C1608s:108-115 兜底：当前线程直接 destroy
            }
        }
        this.executor.shutdown(); // C1608s:119
    }

    // ---------------------------------------------------------------- helpers

    /** 在 JS 线程上同步执行，并把线程池的异常类型还原成调用方语义。 */
    private <T> T submit(Callable<T> task, long timeoutMs) throws TimeoutException {
        Future<T> future;
        try {
            future = executor.submit(task);
        } catch (RejectedExecutionException e) {
            throw new IllegalStateException("quickjs-abogus executor is shut down", e);
        }
        try {
            if (timeoutMs <= 0L) {
                return future.get();
            }
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            // QuickJS 的 evaluate 是 native 阻塞调用，interrupt 无法中断；与原 withTimeout 行为一致
            // 地“放弃等待”，任务仍会在 JS 线程上跑到结束。
            future.cancel(true);
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting for quickjs", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException(cause);
        }
    }

    private Object eval(String script) {
        JsRuntime current = this.runtime;
        if (current == null) {
            throw new IllegalStateException("QuickJSContext is null");
        }
        return current.eval(script);
    }

    /** 原实现每处都做 {@code qx4.m9701x(text, "\r", "", false)}（C1400n:101、:141）。 */
    static String stripCarriageReturns(String text) {
        return text.replace("\r", "");
    }

    /** 原实现的 {@code obj instanceof Boolean ? (Boolean) obj : null} 再判空取 false。 */
    static boolean asBoolean(Object value) {
        return value instanceof Boolean flag && flag;
    }

    private void logCauses(Throwable error, int maxDepth) {
        Throwable cause = error.getCause();
        for (int depth = 0; cause != null && depth < maxDepth; depth++) {
            String message = cause.getMessage();
            log.e(TAG, "  Caused by [" + depth + "]: " + cause.getClass().getSimpleName() + ": "
                    + (message != null ? take(message, 300) : null));
            cause = cause.getCause();
        }
    }

    static String take(String text, int limit) {
        return text.length() <= limit ? text : text.substring(0, limit);
    }
}
