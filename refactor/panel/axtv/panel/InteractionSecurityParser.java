/*
 * 来源: src/app/p000/f32.java（+ e32 中间结构）
 * 原职责: 把手机端提交的 security payload / private key 文本解析成
 *         ticket / ts_sign / privateKey / serverCert 四个字段
 * 重构说明: 保留三层解析顺序与「先到先得」的字段填充规则；
 *           去掉 e32 中间类和链式工具函数，用局部变量直写。
 */
package axtv.panel;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 互动安全凭据解析器。
 *
 * <p>输入是手机端 pages/submit.html 里「提取脚本」的输出，形态有三种（都要支持）：
 * <ol>
 *   <li>JSON 对象/数组：递归找 ticket / ts_sign / privateKey / serverCert 字段；</li>
 *   <li>PEM 文本：{@code -----BEGIN [EC] PRIVATE KEY-----…}；</li>
 *   <li>非标准 {@code key=value} / {@code key:value} 串（脚本输出被截断时的兜底）。</li>
 * </ol>
 */
public final class InteractionSecurityParser {

    private static final Pattern PEM = Pattern.compile(
            "-----BEGIN(?: EC)? PRIVATE KEY-----[\\s\\S]+?-----END(?: EC)? PRIVATE KEY-----");

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern BASE64 = Pattern.compile("[A-Za-z0-9+/=]+");

    private InteractionSecurityParser() {
    }

    /**
     * 解析入口（原 {@code f32.m3996d(String, String)}）。
     *
     * @param securityPayload 手机端 security_payload 原文
     * @param privateKey      手机端 private_key 原文；非空时**覆盖**解析结果里的私钥
     */
    public static InteractionSecurityConfig parse(String securityPayload, String privateKey) {
        String raw = securityPayload == null ? "" : securityPayload.trim();
        InteractionSecurityConfig config = parseRaw(raw);
        String explicitKey = privateKey == null ? "" : privateKey.trim();
        if (!explicitKey.isEmpty()) {
            return new InteractionSecurityConfig(config.rawPayload(), config.ticket(), config.tsSign(),
                    normalizePrivateKey(explicitKey), config.serverCert());
        }
        return config;
    }

    private static InteractionSecurityConfig parseRaw(String raw) {
        Holder holder = new Holder(raw);
        if (raw.isEmpty()) {
            return holder.toConfig();
        }
        // 1) PEM 直接命中
        findPem(holder, raw);
        // 2) JSON（对象或数组）
        try {
            Object parsed = Json.parse(raw);
            if (parsed instanceof Map || parsed instanceof List) {
                walk(holder, parsed);
            }
        } catch (RuntimeException ignored) {
            // 不是 JSON，交给 3) 的 kv 兜底
        }
        // 3) key=value / key:value 兜底
        scanKeyValues(holder, raw);
        return holder.toConfig();
    }

    private static void findPem(Holder holder, String text) {
        if (!holder.privateKey.isEmpty()) {
            return;
        }
        Matcher matcher = PEM.matcher(text);
        if (matcher.find()) {
            holder.privateKey = normalizePrivateKey(matcher.group());
        }
    }

    private static void walk(Holder holder, Object node) {
        if (node instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                Object value = entry.getValue();
                if (value instanceof Map || value instanceof List) {
                    walk(holder, value);
                } else if (value != null) {
                    apply(holder, String.valueOf(entry.getKey()), String.valueOf(value));
                    String text = clean(String.valueOf(value));
                    if (!text.isEmpty()) {
                        findPem(holder, text);
                        if (text.startsWith("{") || text.startsWith("[")) {
                            try {
                                walk(holder, Json.parse(text));
                            } catch (RuntimeException ignored) {
                                // 值不是合法 JSON，忽略
                            }
                        }
                        scanKeyValues(holder, text);
                    }
                }
            }
            return;
        }
        if (node instanceof List<?> list) {
            for (Object item : list) {
                walk(holder, item);
            }
        }
    }

    /**
     * 单键值填充。键名匹配规则与原 {@code f32.m3993a} 一致：
     * 去 {@code - _ 空格} 后小写，再做等值或包含判断；已填过的字段不再覆盖。
     */
    private static void apply(Holder holder, String key, String value) {
        String text = clean(value);
        if (text.isEmpty()) {
            return;
        }
        String normalized = key.toLowerCase(Locale.ROOT).replace("-", "").replace("_", "").replace(" ", "");
        if (normalized.equals("ticket") || normalized.equals("bdticket") || normalized.contains("ticket")) {
            if (holder.ticket.isEmpty()) {
                holder.ticket = text;
            }
            return;
        }
        if (normalized.equals("tssign") || normalized.contains("tssign")) {
            if (holder.tsSign.isEmpty()) {
                holder.tsSign = text;
            }
            return;
        }
        if (normalized.equals("privatekey") || normalized.equals("prik") || normalized.contains("privatekey")) {
            if (holder.privateKey.isEmpty()) {
                holder.privateKey = normalizePrivateKey(text);
            }
            return;
        }
        if (normalized.equals("servercert") && holder.serverCert.isEmpty()) {
            holder.serverCert = text;
        }
    }

    /**
     * 非标准串扫描：按行/分号/逗号切片，每片取第一个 `=` 与 `:` 中位置更靠后的那个作为分隔符。
     * （原实现同样取两者里较大的下标，属于对「被截断的 JSON」的经验性兜底。）
     */
    private static void scanKeyValues(Holder holder, String raw) {
        for (String chunk : raw.split("[\\n;,]")) {
            String part = chunk.trim();
            if (part.isEmpty()) {
                continue;
            }
            int equals = part.indexOf('=');
            int colon = part.indexOf(':');
            int split = Math.max(equals > 0 ? equals : -1, colon > 0 ? colon : -1);
            if (split <= 0 || split >= part.length() - 1) {
                continue;
            }
            String key = stripQuotes(part.substring(0, split).trim());
            String value = stripQuotes(part.substring(split + 1).trim());
            if (key.isEmpty() || value.isEmpty()) {
                continue;
            }
            apply(holder, key, value);
        }
    }

    /**
     * 私钥归一化（原 {@code f32.m3995c}）：
     * 已是 PEM 原样返回；否则去掉所有空白，若长度 > 80 且全是 Base64 字符，则按 64 列重新包成 PEM。
     */
    public static String normalizePrivateKey(String raw) {
        String text = raw == null ? "" : raw.replace("\\r", "").replace("\\n", "\n").trim();
        if (text.startsWith("-----BEGIN")) {
            return text;
        }
        String compact = WHITESPACE.matcher(text).replaceAll("");
        if (compact.length() <= 80) {
            return text;
        }
        if (!BASE64.matcher(compact).matches()) {
            return text;
        }
        StringBuilder pem = new StringBuilder(compact.length() + 64).append("-----BEGIN PRIVATE KEY-----\n");
        for (int i = 0; i < compact.length(); i += 64) {
            pem.append(compact, i, Math.min(i + 64, compact.length())).append('\n');
        }
        return pem.append("-----END PRIVATE KEY-----").toString();
    }

    /** 去掉两端引号、把字面 {@code \n} 还原成换行（原 {@code f32.m3999g}）。 */
    private static String clean(String value) {
        if (value == null) {
            return "";
        }
        String text = stripQuotes(value.trim()).replace("\\r", "").replace("\\n", "\n");
        return text.trim();
    }

    private static String stripQuotes(String text) {
        if (text.length() >= 2) {
            char first = text.charAt(0);
            char last = text.charAt(text.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return text.substring(1, text.length() - 1);
            }
        }
        return text;
    }

    /** 解析过程中的可变累加器。 */
    private static final class Holder {
        private final String raw;
        private String ticket = "";
        private String tsSign = "";
        private String privateKey = "";
        private String serverCert = "";

        Holder(String raw) {
            this.raw = raw;
        }

        InteractionSecurityConfig toConfig() {
            return new InteractionSecurityConfig(this.raw, this.ticket, this.tsSign, this.privateKey, this.serverCert);
        }
    }
}
