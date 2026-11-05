/*
 * 来源: src/app/p000/tp2.java / up2.java / sp2.java / rp2.java / wp2.java / vp2.java 的 toString 行为
 *      以及 cq2.java / b11.java 里对 jx4 / o70 / hf1 字符串工具的使用
 * 原职责: 日志格式化（截断、列表拼接、字节数组打印）与空白判定
 * 重构说明: kotlin-stdlib 薄封装的逐个还原；只保留本模块实际用到的语义
 */
package axtv.live;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/** 字符串 / 集合工具，对应原 {@code jx4}、{@code o70}、{@code hf1} 的少量函数。 */
public final class LiveLogFormat {

    private LiveLogFormat() {
    }

    /** 原 {@code jx4.m6866L}：null 或空白串（Kotlin {@code isNullOrBlank}）。 */
    public static boolean isBlank(CharSequence value) {
        if (value == null || value.length() == 0) {
            return true;
        }
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isWhitespace(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /** 原 {@code jx4.m6887g0}：Kotlin {@code trim()}。 */
    public static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    /** 原 {@code jx4.m6886f0}：Kotlin {@code take(n)}，用于日志截断。 */
    public static String truncate(int max, String value) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    /** 原 {@code jx4.m6873S}：Kotlin {@code substringAfter(delimiter)}。 */
    public static String substringAfter(String value, String delimiter) {
        if (value == null || delimiter == null) {
            return "";
        }
        int index = value.indexOf(delimiter);
        return index < 0 ? "" : value.substring(index + delimiter.length());
    }

    /** 原 {@code jx4.m6859E}：Kotlin {@code contains(char)}。 */
    public static boolean contains(char needle, String value) {
        return value != null && value.indexOf(needle) >= 0;
    }

    /** 原 {@code o70.m8498j1}：集合转 ArrayList（适配 {@code map.keySet()}）。 */
    public static List<String> toList(Collection<String> values) {
        return new java.util.ArrayList<>(values);
    }

    /** 原 {@code o70.m8488Z0(iterable, "[", "]", ...)}：{@code [a, b, c]}。 */
    public static String list(Collection<String> values) {
        return values == null ? "[]" : "[" + String.join(", ", values) + "]";
    }

    /** 原 {@code o70.m8488Z0(entries, "; ", ...)}：把 cookie map 拼成请求头。 */
    public static String joinEntries(Map<String, String> entries, String separator) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : entries.entrySet()) {
            if (isBlank(entry.getKey()) || isBlank(entry.getValue())) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(separator);
            }
            sb.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return sb.toString();
    }

    /** 原 {@code Arrays.toString(byte[])}。 */
    public static String bytes(byte[] value) {
        return Arrays.toString(value);
    }

    private static final char[] HEX = "0123456789ABCDEF".toCharArray();

    /** 供 {@link #bytes(byte[])} 的等价短格式（大帧日志用，避免刷屏）。 */
    public static String hex(byte[] value, int maxBytes) {
        if (value == null) {
            return "";
        }
        int limit = Math.min(value.length, maxBytes);
        StringBuilder sb = new StringBuilder(limit * 2);
        for (int i = 0; i < limit; i++) {
            int b = value[i] & 0xFF;
            sb.append(HEX[b >>> 4]).append(HEX[b & 0x0F]);
        }
        if (value.length > limit) {
            sb.append("...(").append(value.length).append(" bytes)");
        }
        return sb.toString();
    }
}
