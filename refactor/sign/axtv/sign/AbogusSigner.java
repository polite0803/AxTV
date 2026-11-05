/*
 * 来源: src/app/p000/C1608s.java:131-224 (m10095c 签名入口 / 47-84 结果解析)
 *       src/app/p000/C0761j.java:71-106 (真正的 getABogus 脚本拼装)
 *       src/app/p000/C1363m.java:106-125 (withContext 派发)
 *       src/app/p000/C1326l.java / C1437o.java / C1533q.java (协程续体)
 *       src/app/p000/n11.java:642-705 (调用方语义：query / data / uri / userAgent)
 * 原职责: 对外提供 a_bogus 签名与 __ac_signature 签名
 * 重构说明: 拆成 AbogusSigner(脚本拼装 + 结果解析 + 失败语义) 与会话层 AbogusEngine(生命周期)
 */
package axtv.sign;

import java.util.Objects;
import java.util.concurrent.TimeoutException;

/**
 * a_bogus 签名器。
 *
 * <p>对外契约与原实现一致：<b>失败一律返回空串</b>，从不抛异常、从不返回 null
 * （C0761j.java:98、C1608s.java:179/:212）。
 *
 * <p>参数含义（由调用方 n11.m8009B / m7989C 反推，见 NOTES.md）：
 * <ul>
 *   <li>{@code query}：URL query string，不带前导 {@code ?}，如
 *       {@code "device_platform=webapp&aid=6383&..."}</li>
 *   <li>{@code data}：POST 请求体；GET 传空串</li>
 *   <li>{@code uri}：API 路径，如 {@code "/aweme/v1/web/aweme/related/"}；空则 JS 侧用默认路径
 *       {@code /aweme/v1/web/general/search/single/}</li>
 *   <li>{@code userAgent}：透传给 getABogus 的第 4 参；null 表示不传（JS undefined）</li>
 * </ul>
 */
public final class AbogusSigner implements AutoCloseable {

    /** 原 TAG（C0761j:84、C1608s:61 等）。 */
    public static final String TAG = AbogusEngine.TAG;

    /** 日志截断：C0761j.java:84 {@code jx4.m6886f0(150, str3)}。 */
    private static final int LOG_PREVIEW_LIMIT = 150;

    private final AbogusEngine engine;
    private final SignLog log;

    public AbogusSigner(AbogusEngine engine, SignLog log) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.log = log == null ? SignLog.disabled() : log;
    }

    /** 便捷构造：内部自建引擎（等价于原 {@code new C1608s(context)} 的持有方式）。 */
    public AbogusSigner(Assets assets, JsRuntime.Factory runtimeFactory, SignLog log) {
        this(new AbogusEngine(assets, runtimeFactory, log), log);
    }

    /**
     * 计算 a_bogus。
     *
     * <p>流程同 {@code C1608s.m10095c}（C1608s.java:131-224）。
     *
     * @return a_bogus 值；任何失败都返回 {@code ""}
     */
    public String sign(String query, String data, String uri, String userAgent) {
        if (!engine.ensureReady()) { // C1608s:202 -> m10096d
            log.e(TAG, "a_bogus runtime is not ready"); // C1608s:211
            return "";
        }
        try {
            Object raw = engine.evaluate(buildSignScript(query, data, uri, userAgent), AbogusEngine.CALL_TIMEOUT_MS);
            if (!(raw instanceof String text)) {
                // C0761j:82-87：结果不是 String（含 runtime 为空）时直接落到末尾的 return ""。
                return "";
            }
            log.d(TAG, "getABogus raw result (" + text.length() + " chars): "
                    + AbogusEngine.take(text, LOG_PREVIEW_LIMIT)); // C0761j:84
            return parseResult(text); // C0761j:85
        } catch (TimeoutException e) {
            log.e(TAG, "getABogus timed out"); // C1608s:178
            return "";
        } catch (RuntimeException e) {
            log.e(TAG, "getABogus failed", e); // C0761j:89
            logCauses(e); // C0761j:90-96（最多 3 层）
            return "";
        }
    }

    /** {@code userAgent} 省略版本（JS 侧收到 undefined）。 */
    public String sign(String query, String data, String uri) {
        return sign(query, data, uri, null);
    }

    /**
     * 计算 {@code __ac_signature}（byted_acrawler）。
     *
     * <p>对应 {@code C1608s.m10097f}（C1608s.java:284-347）+ {@code C1285k} case 0
     * （C1285k.java:226-249）。原实现中没有任何调用方，属于未接线的能力，此处原样保留。
     *
     * @param data 交给 {@code globalThis.__ac.sign('', data)} 的第二个参数
     * @return 签名值；失败返回 {@code ""}
     */
    public String signAcSignature(String data) {
        if (!engine.isAcrawlerReady() && !engine.ensureReady()) { // C1608s:320-323
            log.e(TAG, "byted_acrawler is not ready for __ac_signature"); // C1608s:336
            return "";
        }
        if (!engine.isAcrawlerReady()) { // C1608s:325-327
            log.e(TAG, "byted_acrawler init failed, cannot generate __ac_signature");
            return "";
        }
        String script = "(function(){ try { return globalThis.__ac.sign('', '"
                + JsEscaper.escapeFull(Objects.requireNonNull(data, "data"))
                + "'); } catch(e) { return JSON.stringify({ok:false,error:String(e)}); } })()"; // C1285k:235
        try {
            Object raw = engine.evaluate(script, AbogusEngine.CALL_TIMEOUT_MS);
            if (!(raw instanceof String text)) {
                return ""; // C1285k:237-239
            }
            if (text.startsWith("{\"ok\":false")) { // C1285k:240
                log.e(TAG, "signAcSignature failed: " + text); // C1285k:241
                return "";
            }
            log.d(TAG, "Generated __ac_signature: length=" + text.length()); // C1285k:244
            return text;
        } catch (TimeoutException e) {
            log.e(TAG, "signAcSignature timed out"); // C1608s:316
            return "";
        } catch (RuntimeException e) {
            log.e(TAG, "signAcSignature failed", e); // C1285k:247
            return "";
        }
    }

    /** 关闭底层引擎（含线程池）。对应 C1608s.m10094a。 */
    @Override
    public void close() {
        engine.close();
    }

    // ---------------------------------------------------------------- 脚本拼装

    /**
     * 逐字对应 {@code C0761j.java:79-81}：
     *
     * <pre>
     * (function(){ try { return getABogus('&lt;query&gt;', '&lt;data&gt;', &lt;uri&gt;, &lt;userAgent&gt;); }
     *   catch(e) { return JSON.stringify({ok:false,error:String(e)}); } })()
     * </pre>
     *
     * uri 为空串时传 {@code undefined}；userAgent 为 null 时传 {@code undefined}。
     * 参数顺序由 n11 -> C1608s.m10095c -> C1363m -> C0761j 的构造参数传递链确定。
     */
    static String buildSignScript(String query, String data, String uri, String userAgent) {
        String queryLiteral = "'" + JsEscaper.escapeFull(Objects.requireNonNull(query, "query")) + "'";
        String dataLiteral = "'" + JsEscaper.escapeFull(Objects.requireNonNull(data, "data")) + "'";
        String uriLiteral = isBlank(uri) ? "undefined" : "'" + JsEscaper.escapeFull(uri) + "'";
        String userAgentLiteral = userAgent != null ? "'" + JsEscaper.escapeFull(userAgent) + "'" : "undefined";
        return "(function(){ try { return getABogus(" + queryLiteral + ", " + dataLiteral + ", "
                + uriLiteral + ", " + userAgentLiteral
                + "); } catch(e) { return JSON.stringify({ok:false,error:String(e)}); } })()";
    }

    // ---------------------------------------------------------------- 结果解析

    /**
     * 等价于 {@code C1608s.m10093e}（C1608s.java:47-84）。
     *
     * <p>步骤（顺序不可换）：
     * <ol>
     *   <li>trim</li>
     *   <li>去掉首尾成对的英文双引号（jx4.m6875U）</li>
     *   <li>反转义 {@code \u003d} {@code \u002b} {@code \u002f} {@code \/} {@code \"}</li>
     *   <li>空 / "null" / "undefined" -> ""</li>
     *   <li>解析 {@code {"ok":true,"value":"..."}}；ok=false 打 error 并返回 ""</li>
     *   <li>解析异常时兜底：长度 &gt; 20 视为裸签名直接返回，否则打 "Unexpected a_bogus result" 返回 ""</li>
     * </ol>
     */
    String parseResult(String raw) {
        String text = raw.strip(); // jx4.m6887g0
        text = stripSurroundingQuotes(text); // jx4.m6875U
        text = text
                .replace("\\u003d", "=")
                .replace("\\u002b", "+")
                .replace("\\u002f", "/")
                .replace("\\/", "/")
                .replace("\\\"", "\"");
        if (isBlank(text) || text.equals("null") || text.equals("undefined")) {
            return "";
        }
        try {
            if (JsonField.bool(text, "ok", false)) {
                String value = JsonField.string(text, "value", "");
                if (!isBlank(value)) {
                    log.d(TAG, "Generated a_bogus: length=" + value.length()); // C1608s:61
                }
                return value;
            }
            log.e(TAG, "a_bogus generation failed: " + JsonField.string(text, "error", "")); // C1608s:65
            return "";
        } catch (RuntimeException e) {
            // 原实现 catch(Throwable) 后走 kotlin Result 的失败分支（C1608s:69-82）。
            if (text.length() > 20) {
                log.d(TAG, "Generated a_bogus (raw): length=" + text.length()); // C1608s:76
                return text;
            }
            log.e(TAG, "Unexpected a_bogus result: " + text); // C1608s:78
            return "";
        }
    }

    private void logCauses(Throwable error) {
        Throwable cause = error.getCause();
        for (int depth = 0; cause != null && depth < 3; depth++) {
            String message = cause.getMessage();
            log.e(TAG, "  Caused by: " + cause.getClass().getSimpleName() + ": "
                    + (message != null ? AbogusEngine.take(message, 200) : null));
            cause = cause.getCause();
        }
    }

    /** 等价于 {@code jx4.m6875U}：长度 &ge; 2、以 {@code "} 开头且以 {@code "} 结尾时剥掉一层。 */
    static String stripSurroundingQuotes(String text) {
        if (text.length() >= 2 && text.startsWith("\"") && text.endsWith("\"")) {
            return text.substring(1, text.length() - 1);
        }
        return text;
    }

    static boolean isBlank(String text) {
        if (text == null || text.isEmpty()) {
            return true;
        }
        for (int i = 0; i < text.length(); i++) {
            if (!Character.isWhitespace(text.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
