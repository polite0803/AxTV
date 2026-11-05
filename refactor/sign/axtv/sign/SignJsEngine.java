/*
 * 来源: src/app/p000/no4.java (229 行，SignJs 引擎 + live 签名)
 *       src/app/p000/zg0.java:138-188 (case 4 = sign.js 初始化)
 *       src/app/p000/q30.java:300-338 (case 11 = getMSSDKSignature 调用与重试)
 *       src/app/p000/h40.java:213 处引用的 byte 21（初始化派发，jadx 反编译失败）
 *       src/app/p000/ThreadFactoryC0651g.java:23-26 (线程名 "quickjs-sign")
 * 原职责: 第二条独立的 QuickJS 运行时：只加载 sign.js，用 getMSSDKSignature 生成直播弹幕签名
 * 重构说明: 从 no4 + zg0 + q30 的状态机里抽出独立引擎；与 a_bogus 引擎完全隔离（不同上下文、不同线程）
 */
package axtv.sign;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
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
 * sign.js（getMSSDKSignature）引擎，用于直播弹幕连接签名。
 *
 * <p>与原实现的两点差异（均已在 NOTES.md 记录）：
 * <ol>
 *   <li>原实现加载 sign.js 时<b>不注入 browser shim</b>（zg0:146-153 直接 evaluate 文件），
 *       这里保持一致。</li>
 *   <li>原实现没有销毁入口，只在初始化失败时 destroy；这里补了 {@link #close()}。</li>
 * </ol>
 */
public final class SignJsEngine implements AutoCloseable {

    /** 原 TAG："SignJs"（zg0:157、q30:326、no4:109）。 */
    public static final String TAG = "SignJs";

    /** 线程名，原 ThreadFactoryC0651g.java:25。 */
    public static final String THREAD_NAME = "quickjs-sign";

    /** 初始化超时：no4.java:215 {@code qi4.m9585n(15000L, ...)}。 */
    public static final long INIT_TIMEOUT_MS = 15_000L;

    /** 脚本资源名：zg0.java:146。 */
    public static final String ASSET_SIGN_JS = "sign.js";

    /**
     * live 签名的固定参数串（no4.java:87 的 listOf 逐项，用 "," 连接）。
     * 只有 roomId / userId 两个位置由调用方填充。
     */
    static final String LIVE_PARAMS_PREFIX =
            "live_id=1,aid=6383,version_code=180800,webcast_sdk_version=1.3.0,room_id=";
    static final String LIVE_PARAMS_MIDDLE =
            ",sub_room_id=,sub_channel_id=,did_rule=3,user_unique_id=";
    static final String LIVE_PARAMS_SUFFIX =
            ",device_platform=web,device_type=,ac=,identity=audience";

    /** 日志截断：no4.java:105 {@code jx4.m6886f0(16, string)}（msStub 只打前 16 位）。 */
    private static final int MS_STUB_PREVIEW = 16;

    private final Assets assets;
    private final JsRuntime.Factory runtimeFactory;
    private final SignLog log;
    private final ExecutorService executor;

    /** 对应 no4.f15165e。 */
    private volatile JsRuntime runtime;

    /** 对应 no4.f15164d。 */
    private volatile boolean ready;

    public SignJsEngine(Assets assets, JsRuntime.Factory runtimeFactory, SignLog log) {
        this.assets = Objects.requireNonNull(assets, "assets");
        this.runtimeFactory = Objects.requireNonNull(runtimeFactory, "runtimeFactory");
        this.log = log == null ? SignLog.disabled() : log;
        // no4.java:35-36
        this.executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, THREAD_NAME);
            thread.setDaemon(true);
            return thread;
        });
    }

    public boolean isReady() {
        return ready;
    }

    /**
     * 生成直播签名。
     *
     * <p>等价于 {@code no4.m8230a(roomId, userId, userAgent)}（no4.java:54-187）。
     *
     * @return getMSSDKSignature 的返回值
     * @throws IllegalStateException sign.js 未就绪（"sign.js runtime is not ready"）
     *                               或返回空签名（"Failed to generate Douyin signature"）
     * @throws NoSuchAlgorithmException 运行环境缺少 MD5（原实现同样声明抛出）
     */
    public String signLive(String roomId, String userId, String userAgent) throws NoSuchAlgorithmException {
        if (!ready && !ensureReady()) { // no4:128-151
            throw new IllegalStateException("sign.js runtime is not ready");
        }
        String msStub = md5Hex(buildLiveParamString(roomId, userId)); // no4:153-170
        log.d(TAG, "Generating live signature: roomId=" + roomId + ", userId=" + userId
                + ", msStub=" + AbogusEngine.take(msStub, MS_STUB_PREVIEW) + "..."); // no4:172-175

        int retries = 0;
        while (true) {
            String signature = evaluateSignature(msStub, userAgent); // q30:315-319
            if (!signature.contains("-") && !signature.contains("=")) { // q30:321
                if (AbogusSigner.isBlank(signature)) { // q30:322
                    throw new IllegalStateException("Failed to generate Douyin signature"); // q30:323
                }
                log.d(TAG, "Generated live signature: length=" + signature.length()
                        + ", retries=" + retries); // q30:326
                return signature;
            }
            retries++; // q30:329
        }
    }

    /** 确保 sign.js 就绪（15s 超时）。对应 {@code no4.m8231b}（no4.java:191-228）+ zg0 case 4。 */
    public boolean ensureReady() {
        if (ready) {
            return true;
        }
        try {
            submit(this::initialize, INIT_TIMEOUT_MS);
        } catch (TimeoutException e) {
            log.e(TAG, "sign.js runtime init timed out");
            return false;
        } catch (RuntimeException e) {
            log.e(TAG, "sign.js runtime init failed", e);
            return false;
        }
        return ready;
    }

    /**
     * 初始化 sign.js。对应 {@code zg0.invokeSuspend} case 4（zg0.java:138-188），在 JS 线程上执行。
     * 注意：不注入 browser shim，与原实现一致。
     */
    private Boolean initialize() {
        if (ready && runtime != null) { // zg0:140-142
            return Boolean.TRUE;
        }
        try {
            runtime = runtimeFactory.create(); // zg0:144-145
            String script = AbogusEngine.stripCarriageReturns(assets.readText(ASSET_SIGN_JS)); // zg0:146-152
            runtime.eval(script);

            if (AbogusEngine.asBoolean(runtime.eval(
                    "(function(){ return typeof getMSSDKSignature === 'function'; })()"))) { // zg0:153-155
                ready = true; // zg0:156
                log.i(TAG, "SignJs runtime initialized successfully with QuickJS"); // zg0:157
                return Boolean.TRUE;
            }

            log.e(TAG, "getMSSDKSignature is missing in sign.js"); // zg0:161
            destroyInline(); // zg0:162-168
            return Boolean.FALSE;
        } catch (Exception e) { // zg0:178-187
            log.e(TAG, "Failed to initialize sign.js runtime with QuickJS", e); // zg0:179
            destroyInline();
            return Boolean.FALSE;
        }
    }

    /**
     * 调用一次 {@code getMSSDKSignature(...)}。对应 q30.java:300-338。
     * 返回非字符串时按 "" 处理（q30:316-319）。
     */
    private String evaluateSignature(String msStub, String userAgent) {
        String script = "getMSSDKSignature('" + JsEscaper.escapeMinimal(msStub) + "', '"
                + JsEscaper.escapeMinimal(userAgent == null ? "" : userAgent) + "')"; // q30:310-314
        try {
            Object result = submit(() -> {
                JsRuntime current = this.runtime;
                if (current == null) {
                    throw new IllegalStateException("QuickJSContext is null"); // q30:303-305
                }
                return current.eval(script);
            }, 0L);
            return result instanceof String text ? text : ""; // q30:316-319
        } catch (TimeoutException e) {
            // 调用方传 0（不限时），此分支不会发生；保留以满足签名。
            return "";
        }
    }

    /**
     * 拼装 live 签名参与 MD5 的参数串（no4.java:87）。
     *
     * <pre>
     * live_id=1,aid=6383,version_code=180800,webcast_sdk_version=1.3.0,room_id=&lt;roomId&gt;,
     * sub_room_id=,sub_channel_id=,did_rule=3,user_unique_id=&lt;userId&gt;,
     * device_platform=web,device_type=,ac=,identity=audience
     * </pre>
     */
    static String buildLiveParamString(String roomId, String userId) {
        return LIVE_PARAMS_PREFIX + roomId + LIVE_PARAMS_MIDDLE + userId + LIVE_PARAMS_SUFFIX;
    }

    /** 小写十六进制 MD5（no4.java:88-104 的格式化循环）。 */
    static String md5Hex(String text) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("MD5"); // no4:88
        return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
    }

    @Override
    public void close() {
        if (THREAD_NAME.equals(Thread.currentThread().getName())) {
            destroyInline();
        } else {
            try {
                submit(this::destroyInline, TimeUnit.SECONDS.toMillis(AbogusEngine.DESTROY_WAIT_SECONDS));
            } catch (Exception e) {
                log.e(TAG, "Failed to dispatch destroy", e);
                destroyInline();
            }
        }
        executor.shutdown();
    }

    private Boolean destroyInline() {
        JsRuntime current = this.runtime;
        this.runtime = null;
        this.ready = false;
        if (current != null) {
            try {
                current.close(); // RunnableC0688h case 20（TAG "SignJs"）
            } catch (Exception e) {
                log.e(TAG, "Failed to destroy QuickJSContext", e);
            }
        }
        return Boolean.TRUE;
    }

    private <T> T submit(Callable<T> task, long timeoutMs) throws TimeoutException {
        Future<T> future;
        try {
            future = executor.submit(task);
        } catch (RejectedExecutionException e) {
            throw new IllegalStateException("quickjs-sign executor is shut down", e);
        }
        try {
            if (timeoutMs <= 0L) {
                return future.get();
            }
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
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
}
