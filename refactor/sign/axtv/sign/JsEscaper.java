/*
 * 来源: src/app/p000/C1608s.java:42-44  (m10092b)
 *       src/app/p000/q30.java:310,314      (内联的简化版转义)
 * 原职责: 把 Java 字符串安全地嵌入到 JS 单引号字符串字面量里
 * 重构说明: 拆成 escapeFull(原 m10092b，4 条替换) 与 escapeMinimal(原 q30 内联，2 条替换)
 */
package axtv.sign;

/**
 * JS 单引号字符串字面量转义。
 *
 * <p>两套替换规则不同，不能合并——原代码就是两处不同实现，替换次数会影响结果（见 NOTES.md「实现风险」）：
 * <ul>
 *   <li>{@link #escapeFull(String)}：{@code \ -> \\}、{@code ' -> \'}、换行 -> {@code \n}、回车 -> {@code \r}，
 *       按此顺序<b>依次</b>做全量替换（C1608s:43）。a_bogus 链路使用。</li>
 *   <li>{@link #escapeMinimal(String)}：只替换 {@code \ -> \\} 与 {@code ' -> \'}（q30:310、q30:314）。
 *       live 签名链路使用；不做换行转义，含换行的入参会破坏脚本。</li>
 * </ul>
 */
public final class JsEscaper {

    private JsEscaper() {
    }

    /**
     * 等价于原 {@code C1608s.m10092b(String)}（C1608s.java:43）。
     *
     * <pre>
     * replace("\\", "\\\\") -> replace("'", "\\'") -> replace("\n", "\\n") -> replace("\r", "\\r")
     * </pre>
     *
     * 注意顺序：先把反斜杠翻倍，后面三条产生的反斜杠才不会被二次转义。
     */
    public static String escapeFull(String value) {
        // 原实现无 null 检查，null 会在 qx4.m9701x 里 NPE；这里保持同样的失败语义。
        if (value == null) {
            throw new NullPointerException("value is null");
        }
        return value
                .replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    /** 等价于原 {@code q30.java:310 / q30.java:314} 的内联两段替换（backslash、单引号）。 */
    public static String escapeMinimal(String value) {
        if (value == null) {
            throw new NullPointerException("value is null");
        }
        return value
                .replace("\\", "\\\\")
                .replace("'", "\\'");
    }
}
