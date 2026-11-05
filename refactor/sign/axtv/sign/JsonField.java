/*
 * 来源: src/app/p000/C1608s.java:55-68  org.json.JSONObject 的 optBoolean / optString 用法
 * 原职责: 解析 abogus_dy.js 返回的 {"ok":true,"value":"..."} / {"ok":false,"error":"..."}
 * 重构说明: 核心层不绑定 JSON 库（原工程用 org.json），这里内置一个只覆盖所需语义的最小解析器
 */
package axtv.sign;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 最小 JSON 读取器。
 *
 * <p>只实现 {@code C1608s.m10093e} 用到的两个语义：
 * <ul>
 *   <li>{@code optString(key, def)}：JSON string 取原值；number/boolean 取字面量；
 *       null/缺失取 def；object/array 取紧凑序列化（原 org.json 行为是 toString）。</li>
 *   <li>{@code optBoolean(key)}：{@code true}/{@code false} 字面量，或字符串 {@code "true"}/{@code "false"}
 *       （忽略大小写）；其余取 def。</li>
 * </ul>
 *
 * <p>解析失败抛 {@link IllegalArgumentException}，与原实现 {@code new JSONObject(...)} 抛
 * {@code JSONException} 后在 C1608s:69 被 catch 的路径对应。
 */
final class JsonField {

    private final Map<String, Object> values;

    private JsonField(Map<String, Object> values) {
        this.values = values;
    }

    static String string(String json, String key, String defaultValue) {
        Object value = parse(json).values.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof String text) {
            return text;
        }
        // number / boolean 取字面量；object / array 取 toString（原 org.json 的 optString 也是 toString）
        return String.valueOf(value);
    }

    static boolean bool(String json, String key, boolean defaultValue) {
        Object value = parse(json).values.get(key);
        if (value instanceof Boolean flag) {
            return flag;
        }
        if (value instanceof String text) {
            if ("true".equalsIgnoreCase(text)) {
                return true;
            }
            if ("false".equalsIgnoreCase(text)) {
                return false;
            }
        }
        return defaultValue;
    }

    static JsonField parse(String json) {
        Cursor cursor = new Cursor(json);
        cursor.skipWhitespace();
        Object value = cursor.readValue();
        cursor.skipWhitespace();
        if (cursor.index != json.length()) {
            throw new IllegalArgumentException("trailing characters at " + cursor.index);
        }
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("not a JSON object");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> casted = (Map<String, Object>) map;
        return new JsonField(casted);
    }

    private static final class Cursor {
        private final String source;
        private int index;

        Cursor(String source) {
            this.source = source;
        }

        void skipWhitespace() {
            while (index < source.length() && Character.isWhitespace(source.charAt(index))) {
                index++;
            }
        }

        Object readValue() {
            skipWhitespace();
            if (index >= source.length()) {
                throw new IllegalArgumentException("unexpected end of input");
            }
            char c = source.charAt(index);
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
            Map<String, Object> map = new LinkedHashMap<>();
            index++; // '{'
            skipWhitespace();
            if (peek() == '}') {
                index++;
                return map;
            }
            while (true) {
                skipWhitespace();
                String key = readString();
                skipWhitespace();
                expect(':');
                map.put(key, readValue());
                skipWhitespace();
                char next = next();
                if (next == ',') {
                    continue;
                }
                if (next == '}') {
                    return map;
                }
                throw new IllegalArgumentException("expected ',' or '}' at " + index);
            }
        }

        private List<Object> readArray() {
            List<Object> list = new ArrayList<>();
            index++; // '['
            skipWhitespace();
            if (peek() == ']') {
                index++;
                return list;
            }
            while (true) {
                list.add(readValue());
                skipWhitespace();
                char next = next();
                if (next == ',') {
                    continue;
                }
                if (next == ']') {
                    return list;
                }
                throw new IllegalArgumentException("expected ',' or ']' at " + index);
            }
        }

        private String readString() {
            expect('"');
            StringBuilder out = new StringBuilder();
            while (true) {
                char c = next();
                if (c == '"') {
                    return out.toString();
                }
                if (c != '\\') {
                    out.append(c);
                    continue;
                }
                char escaped = next();
                switch (escaped) {
                    case '"', '\\', '/' -> out.append(escaped);
                    case 'b' -> out.append('\b');
                    case 'f' -> out.append('\f');
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    case 'u' -> {
                        if (index + 4 > source.length()) {
                            throw new IllegalArgumentException("bad unicode escape at " + index);
                        }
                        out.append((char) Integer.parseInt(source.substring(index, index + 4), 16));
                        index += 4;
                    }
                    default -> throw new IllegalArgumentException("bad escape \\" + escaped + " at " + index);
                }
            }
        }

        private Object readLiteral(String literal, Object value) {
            if (!source.startsWith(literal, index)) {
                throw new IllegalArgumentException("expected " + literal + " at " + index);
            }
            index += literal.length();
            return value;
        }

        private Number readNumber() {
            int start = index;
            while (index < source.length() && "+-0123456789.eE".indexOf(source.charAt(index)) >= 0) {
                index++;
            }
            if (start == index) {
                throw new IllegalArgumentException("unexpected character at " + index);
            }
            return Double.valueOf(source.substring(start, index));
        }

        private char peek() {
            return index < source.length() ? source.charAt(index) : '\0';
        }

        private char next() {
            if (index >= source.length()) {
                throw new IllegalArgumentException("unexpected end of input");
            }
            return source.charAt(index++);
        }

        private void expect(char expected) {
            if (next() != expected) {
                throw new IllegalArgumentException("expected '" + expected + "' at " + (index - 1));
            }
        }
    }
}
