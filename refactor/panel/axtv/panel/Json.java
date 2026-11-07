/*
 * 来源: src/app/p000/qj0.java + src/app/p000/wc4.java + src/app/p000/ct5.java 中的 org.json 用法
 *       （原工程用 org.json.JSONObject/JSONArray，核心层不允许依赖 Android）
 * 原职责: JSON 解析与拼装
 * 重构说明: 极简 JSON 编解码（对象/数组/字符串/数字/布尔/null），
 *           实现 {@link JsonCodec} 约定的最小可用子集。不追求 RFC 8259 全量校验。
 */
package axtv.panel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 极简 JSON。只覆盖面板服务用到的能力：读对象字段、写扁平对象。 */
public final class Json {

    private Json() {
    }

    /** 解析 JSON 文本。失败抛 {@link IllegalArgumentException}（对应原代码被抓的 Exception）。 */
    public static Object parse(String text) {
        Parser parser = new Parser(text);
        Object value = parser.readValue();
        parser.skipWhitespace();
        if (!parser.atEnd()) {
            throw new IllegalArgumentException("Unexpected trailing content at " + parser.position());
        }
        return value;
    }

    /** 解析 JSON 对象；不是对象则抛异常。 */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String text) {
        Object value = parse(text);
        if (!(value instanceof Map)) {
            throw new IllegalArgumentException("Expecting a JSON object");
        }
        return (Map<String, Object>) value;
    }

    /**
     * 取字符串字段，对应 {@code JSONObject.optString(key, "")}：
     * 缺失、null、非字符串一律返回 {@code ""}；数字/布尔按字面量转字符串。
     */
    public static String optString(Map<String, Object> object, String key) {
        Object value = object.get(key);
        if (value == null) {
            return "";
        }
        if (value instanceof String s) {
            return s;
        }
        if (value instanceof Map || value instanceof List) {
            return write(value);
        }
        return String.valueOf(value);
    }

    public static boolean optBoolean(Map<String, Object> object, String key) {
        Object value = object.get(key);
        if (value instanceof Boolean b) {
            return b;
        }
        return value != null && Boolean.parseBoolean(String.valueOf(value));
    }

    /** 取整数字段；缺失/null/非数值一律返回 {@code fallback}（解析器把整数读成 Long，小数读成 Double）。 */
    public static long optLong(Map<String, Object> object, String key, long fallback) {
        Object value = object.get(key);
        if (value instanceof Number n) {
            return n.longValue();
        }
        if (value instanceof String s) {
            try {
                return Long.parseLong(s.trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    /** 取数组字段；缺失/null/非数组一律返回空表（不抛异常，损坏快照由调用方按 schema 判定）。 */
    @SuppressWarnings("unchecked")
    public static List<Object> optList(Map<String, Object> object, String key) {
        Object value = object.get(key);
        if (value instanceof List<?> list) {
            return (List<Object>) list;
        }
        return List.of();
    }

    /** 取对象字段；缺失/null/非对象一律返回空 map。 */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> optObject(Map<String, Object> object, String key) {
        Object value = object.get(key);
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    /** 把 Java 值序列化成 JSON 文本。 */
    public static String write(Object value) {
        StringBuilder out = new StringBuilder(64);
        writeValue(value, out);
        return out.toString();
    }

    /** 转义成 JSON 字符串字面量（含外层引号）。 */
    public static String quote(String text) {
        StringBuilder out = new StringBuilder(text.length() + 16).append('"');
        appendEscaped(text, out);
        return out.append('"').toString();
    }

    /**
     * 拼装扁平对象，键值交替传入：{@code Json.object("success", true, "device_name", "TV")}。
     * 值支持 String / Boolean / Number / Map / List。
     */
    public static String object(Object... keyValues) {
        if (keyValues.length % 2 != 0) {
            throw new IllegalArgumentException("keyValues must be even");
        }
        StringBuilder out = new StringBuilder(64).append('{');
        for (int i = 0; i < keyValues.length; i += 2) {
            if (i > 0) {
                out.append(',');
            }
            out.append(quote(String.valueOf(keyValues[i]))).append(':');
            writeValue(keyValues[i + 1], out);
        }
        return out.append('}').toString();
    }

    private static void writeValue(Object value, StringBuilder out) {
        if (value == null) {
            out.append("null");
        } else if (value instanceof String s) {
            out.append('"');
            appendEscaped(s, out);
            out.append('"');
        } else if (value instanceof Boolean || value instanceof Number) {
            out.append(value);
        } else if (value instanceof Map<?, ?> map) {
            out.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) {
                    out.append(',');
                }
                first = false;
                out.append(quote(String.valueOf(entry.getKey()))).append(':');
                writeValue(entry.getValue(), out);
            }
            out.append('}');
        } else if (value instanceof Iterable<?> iterable) {
            out.append('[');
            boolean first = true;
            for (Object item : iterable) {
                if (!first) {
                    out.append(',');
                }
                first = false;
                writeValue(item, out);
            }
            out.append(']');
        } else {
            out.append(quote(String.valueOf(value)));
        }
    }

    private static void appendEscaped(String text, StringBuilder out) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
    }

    private static final class Parser {
        private final String text;
        private int index;

        Parser(String text) {
            this.text = text == null ? "" : text;
        }

        boolean atEnd() {
            return this.index >= this.text.length();
        }

        int position() {
            return this.index;
        }

        Object readValue() {
            skipWhitespace();
            if (atEnd()) {
                throw new IllegalArgumentException("Unexpected end of JSON");
            }
            char c = this.text.charAt(this.index);
            return switch (c) {
                case '{' -> readObject();
                case '[' -> readArray();
                case '"' -> readString();
                case 't' -> readLiteral("true", Boolean.TRUE);
                case 'f' -> readLiteral("false", Boolean.FALSE);
                case 'n' -> readLiteral("null", null);
                default -> readNumber();
            };
        }

        private Map<String, Object> readObject() {
            expect('{');
            Map<String, Object> object = new LinkedHashMap<>();
            skipWhitespace();
            if (peek() == '}') {
                this.index++;
                return object;
            }
            while (true) {
                skipWhitespace();
                String key = readString();
                skipWhitespace();
                expect(':');
                object.put(key, readValue());
                skipWhitespace();
                char c = next();
                if (c == '}') {
                    return object;
                }
                if (c != ',') {
                    throw new IllegalArgumentException("Expecting ',' or '}' at " + this.index);
                }
            }
        }

        private List<Object> readArray() {
            expect('[');
            List<Object> list = new ArrayList<>();
            skipWhitespace();
            if (peek() == ']') {
                this.index++;
                return list;
            }
            while (true) {
                list.add(readValue());
                skipWhitespace();
                char c = next();
                if (c == ']') {
                    return list;
                }
                if (c != ',') {
                    throw new IllegalArgumentException("Expecting ',' or ']' at " + this.index);
                }
            }
        }

        private String readString() {
            expect('"');
            StringBuilder out = new StringBuilder();
            while (true) {
                if (atEnd()) {
                    throw new IllegalArgumentException("Unterminated string");
                }
                char c = this.text.charAt(this.index++);
                if (c == '"') {
                    return out.toString();
                }
                if (c != '\\') {
                    out.append(c);
                    continue;
                }
                char escape = next();
                switch (escape) {
                    case '"', '\\', '/' -> out.append(escape);
                    case 'b' -> out.append('\b');
                    case 'f' -> out.append('\f');
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    case 'u' -> {
                        if (this.index + 4 > this.text.length()) {
                            throw new IllegalArgumentException("Bad \\u escape");
                        }
                        out.append((char) Integer.parseInt(this.text.substring(this.index, this.index + 4), 16));
                        this.index += 4;
                    }
                    default -> throw new IllegalArgumentException("Bad escape \\" + escape);
                }
            }
        }

        private Object readLiteral(String literal, Object value) {
            if (this.text.startsWith(literal, this.index)) {
                this.index += literal.length();
                return value;
            }
            throw new IllegalArgumentException("Unexpected token at " + this.index);
        }

        private Number readNumber() {
            int start = this.index;
            boolean fractional = false;
            while (!atEnd()) {
                char c = this.text.charAt(this.index);
                if (c == '-' || c == '+' || (c >= '0' && c <= '9')) {
                    this.index++;
                } else if (c == '.' || c == 'e' || c == 'E') {
                    fractional = true;
                    this.index++;
                } else {
                    break;
                }
            }
            String token = this.text.substring(start, this.index);
            if (token.isEmpty()) {
                throw new IllegalArgumentException("Unexpected character at " + start);
            }
            if (fractional) {
                return Double.parseDouble(token);
            }
            try {
                return Long.parseLong(token);
            } catch (NumberFormatException e) {
                return Double.parseDouble(token);
            }
        }

        void skipWhitespace() {
            while (!atEnd()) {
                char c = this.text.charAt(this.index);
                if (c == ' ' || c == '\n' || c == '\r' || c == '\t') {
                    this.index++;
                } else {
                    return;
                }
            }
        }

        private char peek() {
            return atEnd() ? '\0' : this.text.charAt(this.index);
        }

        private char next() {
            if (atEnd()) {
                throw new IllegalArgumentException("Unexpected end of JSON");
            }
            return this.text.charAt(this.index++);
        }

        private void expect(char expected) {
            char c = next();
            if (c != expected) {
                throw new IllegalArgumentException("Expecting '" + expected + "' at " + (this.index - 1));
            }
        }
    }
}
