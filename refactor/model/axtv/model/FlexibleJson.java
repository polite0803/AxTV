/*
 * 来源: src/app/com/github/mytv/p004dv/model/FlexibleIntSerializer.java
 *       src/app/com/github/mytv/p004dv/model/FlexibleLongSerializer.java
 *       src/app/com/github/mytv/p004dv/model/FlexibleBooleanSerializer.java
 *       (依赖 p000.r62 = kotlinx JsonPrimitive 的 long 解析 / p000.qx4 = String.toIntOrNull/toLongOrNull)
 * 原职责: 抖音接口把数字/布尔随机返回成字符串（"123"、"1"、true、1 都可能），
 *         三个 KSerializer 负责「宽松地」把任意 JSON 原始值收敛成 int/long/boolean。
 * 重构说明: 去掉 KSerializer 外壳，合并成一个可配置的静态解码器。
 *           逐条还原三个序列化器的分支（见各类方法注释与 NOTES.md「柔性解码规则」）。
 */
package axtv.model;

/**
 * 抖音接口的「柔性」标量解码。
 *
 * <p>原实现三人组的行为完全一致：把 JSON 原始值先取成 <b>token 文本</b>，再按目标类型严格解析。
 * token 文本的取法（kotlinx {@code JsonPrimitive.content} 语义）：
 * <ul>
 *   <li>JSON 字符串 {@code "123"} → {@code 123}（引号已去掉，所以字符串数字能解析）</li>
 *   <li>JSON 数字 {@code 123} / {@code 1.5} → 源码字面量文本 {@code 123} / {@code 1.5}</li>
 *   <li>JSON 布尔 {@code true} → {@code true}</li>
 *   <li>JSON {@code null} → 拿不到文本，走字段默认值</li>
 *   <li>JSON 对象/数组 → 原实现 {@code asPrimitive} 抛 IllegalArgumentException，被外层 catch 吞掉 → 默认值</li>
 * </ul>
 */
public final class FlexibleJson {

    private FlexibleJson() {
    }

    // ---------------------------------------------------------------- 文本提取

    /**
     * 把一个已解析的 JSON 值转成「token 文本」，等价于 kotlinx 的 {@code JsonPrimitive.content}。
     *
     * @return null 表示不是标量（null / 对象 / 数组），调用方应回落字段默认值
     */
    public static String rawText(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String s) {
            return s;
        }
        if (value instanceof Boolean b) {
            return b ? "true" : "false";
        }
        if (value instanceof Number n) {
            return n.toString();
        }
        if (value instanceof Character c) {
            return String.valueOf(c);
        }
        // 对象 / 数组：原实现在这里抛异常并被吞
        return null;
    }

    // ---------------------------------------------------------------- int

    /**
     * FlexibleIntSerializer 的规则。
     *
     * <ol>
     *   <li>文本能按 long 解析且落在 int 范围内 → 直接用（{@code -2147483648..2147483647}）</li>
     *   <li>能按 long 解析但越界 → 强转 {@code (int)} <b>截断</b>（原代码在 int 溢出后会再做一次
     *       {@code (int) longValue}，等价于取低 32 位）</li>
     *   <li>不是合法整数文本（含空串、{@code "12.0"}、{@code "abc"}、带空白、{@code true}）→ 0</li>
     *   <li>null / 非标量 → 0（原本由属性的默认值兜底，默认值就是 0）</li>
     * </ol>
     */
    public static int toInt(Object value) {
        return toInt(rawText(value), 0);
    }

    public static int toInt(String text, int fallback) {
        if (text == null) {
            return fallback;
        }
        Long parsed = parseLongStrict(text);
        if (parsed == null) {
            return fallback;
        }
        return (int) parsed.longValue(); // 越界时与 JVM 强转一致（截断）
    }

    // ---------------------------------------------------------------- long

    /**
     * FlexibleLongSerializer 的规则。
     *
     * <ol>
     *   <li>文本能按 long 严格解析 → 用</li>
     *   <li>否则尝试 {@code toLongOrNull}（允许前导 {@code +}/{@code -}，其余同严格解析）→ 用</li>
     *   <li>都失败 → 0</li>
     * </ol>
     * 两步解析在 Java 里等价于一次 {@link Long#parseLong(String)}：两者都只接受
     * {@code [+-]?[0-9]+} 且必须整串匹配。因此最终语义是「不合法就是 0」，不会出现小数截断。
     */
    public static long toLong(Object value) {
        return toLong(rawText(value), 0L);
    }

    public static long toLong(String text, long fallback) {
        if (text == null) {
            return fallback;
        }
        Long parsed = parseLongStrict(text);
        return parsed == null ? fallback : parsed.longValue();
    }

    // ---------------------------------------------------------------- boolean

    /**
     * FlexibleBooleanSerializer 的规则。
     *
     * <p>只有文本<b>恰好等于</b> {@code "true"} 或 {@code "1"} 才是 true，其余全部 false：
     * <ul>
     *   <li>{@code true} → true；{@code 1} / {@code "1"} → true</li>
     *   <li>{@code false} / {@code 0} / {@code "0"} → false</li>
     *   <li>注意大小写敏感：{@code "TRUE"} / {@code "True"} → <b>false</b></li>
     *   <li>{@code "true "}（带空格）、{@code "yes"}、{@code 1.0}（字面量 "1.0"）→ false</li>
     *   <li>null / 非标量 → false（字段默认值）</li>
     * </ul>
     * 原代码用了一个恒等的三元表达式（两分支完全相同），说明作者本意可能是「字符串/非字符串分别处理」，
     * 但实际行为就是单纯的字符串相等判断，这里按实际行为还原。
     */
    public static boolean toBoolean(Object value) {
        return toBoolean(rawText(value), false);
    }

    public static boolean toBoolean(String text, boolean fallback) {
        if (text == null) {
            return fallback;
        }
        return "true".equals(text) || "1".equals(text);
    }

    // ---------------------------------------------------------------- 序列化方向

    /** 写回：原实现直接 {@code encodeInt}，即输出 JSON 数字。 */
    public static Integer writeInt(int value) {
        return value;
    }

    /** 写回：原实现直接 {@code encodeLong}，即输出 JSON 数字。 */
    public static Long writeLong(long value) {
        return value;
    }

    /** 写回：原实现直接 {@code encodeBoolean}，即输出 JSON 布尔。 */
    public static Boolean writeBoolean(boolean value) {
        return value;
    }

    // ---------------------------------------------------------------- 内部

    /**
     * 等价于 kotlinx {@code JsonPrimitive.long} / Java {@link Long#parseLong(String)}：
     * 整串必须是合法十进制整数，否则 null。
     */
    private static Long parseLongStrict(String text) {
        if (text.isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
