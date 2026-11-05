/*
 * 来源: src/app/p000/ff5.java（读取 tag_name / body / assets[].name / browser_download_url / digest）
 *       + src/app/p000/cf5.java（UpdateInfo 数据类）
 * 原职责: GitHub Releases 响应解析
 * 重构说明: 抽 ReleaseJson 接口，纯 JDK 实现只解析需要的字段（不引入 JSON 库）
 */
package axtv.settings;

/**
 * GitHub release 响应解析。
 *
 * <p>原文直接 {@code new JSONObject(body)} 后取四个字段，任何异常都视为「该镜像失败」。
 * Android 侧可用 {@code org.json} 实现；纯 Java 侧用 {@link SimpleReleaseJson}。
 */
public interface ReleaseJson {

    /** 最小 JSON 解析器：解析失败抛 {@link IllegalArgumentException}（与原文 JSONException 等价）。 */
    UpdateManager.ReleaseInfo parse(String body);
}

/** 只实现需要的字段，但语法上按标准 JSON 走（字符串转义、数组、嵌套对象都吃）。 */
final class SimpleReleaseJson implements ReleaseJson {

    @Override
    public UpdateManager.ReleaseInfo parse(String body) {
        Object root = new Parser(body).parseValue();
        if (!(root instanceof JsonObject obj)) {
            throw new IllegalArgumentException("release json is not an object");
        }
        Object tag = obj.get("tag_name");
        if (!(tag instanceof String tagName)) {
            throw new IllegalArgumentException("missing tag_name");
        }
        String updateLog = obj.get("body") instanceof String text && !text.isEmpty()
                ? text : "没有提供更新日志";
        Object assetsValue = obj.get("assets");
        if (!(assetsValue instanceof JsonArray assets)) {
            throw new IllegalArgumentException("missing assets");
        }
        String downloadUrl = null;
        String sha256 = null;
        for (Object item : assets) {
            if (!(item instanceof JsonObject asset)) {
                continue;
            }
            Object name = asset.get("name");
            if (!(name instanceof String fileName) || !fileName.endsWith(".apk")) {
                continue;
            }
            Object url = asset.get("browser_download_url");
            downloadUrl = url instanceof String s ? s : null;
            Object digest = asset.get("digest");
            String raw = digest instanceof String s ? s : "";
            String stripped = raw.startsWith("sha256:") ? raw.substring("sha256:".length()) : raw;
            stripped = stripped.toLowerCase(java.util.Locale.ROOT);
            // 原文：digest 去掉 "sha256:" 前缀后长度必须为 64，否则视为没有摘要
            sha256 = stripped.length() == 64 ? stripped : null;
            break;
        }
        return new UpdateManager.ReleaseInfo(tagName, updateLog, downloadUrl, sha256, null);
    }

    /** 极简 JSON 值模型，只为上面的字段服务。 */
    private static final class JsonObject {
        private final java.util.Map<String, Object> fields = new java.util.LinkedHashMap<>();

        Object get(String key) {
            return fields.get(key);
        }
    }

    private static final class JsonArray extends java.util.ArrayList<Object> {
    }

    private static final class Parser {
        private final String src;
        private int pos;

        Parser(String src) {
            this.src = src;
        }

        Object parseValue() {
            skipWs();
            if (pos >= src.length()) {
                throw new IllegalArgumentException("unexpected end of json");
            }
            char c = src.charAt(pos);
            return switch (c) {
                case '{' -> parseObject();
                case '[' -> parseArray();
                case '"' -> parseString();
                case 't' -> literal("true", Boolean.TRUE);
                case 'f' -> literal("false", Boolean.FALSE);
                case 'n' -> literal("null", null);
                default -> parseNumber();
            };
        }

        private Object parseObject() {
            expect('{');
            JsonObject object = new JsonObject();
            skipWs();
            if (consume('}')) {
                return object;
            }
            while (true) {
                skipWs();
                String key = parseString();
                skipWs();
                expect(':');
                object.fields.put(key, parseValue());
                skipWs();
                if (consume(',')) {
                    continue;
                }
                expect('}');
                return object;
            }
        }

        private Object parseArray() {
            expect('[');
            JsonArray array = new JsonArray();
            skipWs();
            if (consume(']')) {
                return array;
            }
            while (true) {
                array.add(parseValue());
                skipWs();
                if (consume(',')) {
                    continue;
                }
                expect(']');
                return array;
            }
        }

        private String parseString() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (true) {
                if (pos >= src.length()) {
                    throw new IllegalArgumentException("unterminated string");
                }
                char c = src.charAt(pos++);
                if (c == '"') {
                    return sb.toString();
                }
                if (c != '\\') {
                    sb.append(c);
                    continue;
                }
                char e = src.charAt(pos++);
                switch (e) {
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case '/' -> sb.append('/');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'u' -> {
                        sb.append((char) Integer.parseInt(src.substring(pos, pos + 4), 16));
                        pos += 4;
                    }
                    default -> throw new IllegalArgumentException("bad escape \\" + e);
                }
            }
        }

        private Object parseNumber() {
            int start = pos;
            while (pos < src.length() && "+-0123456789.eE".indexOf(src.charAt(pos)) >= 0) {
                pos++;
            }
            if (start == pos) {
                throw new IllegalArgumentException("unexpected char at " + pos);
            }
            return Double.parseDouble(src.substring(start, pos));
        }

        private Object literal(String text, Object value) {
            if (!src.startsWith(text, pos)) {
                throw new IllegalArgumentException("bad literal at " + pos);
            }
            pos += text.length();
            return value;
        }

        private void skipWs() {
            while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) {
                pos++;
            }
        }

        private boolean consume(char c) {
            if (pos < src.length() && src.charAt(pos) == c) {
                pos++;
                return true;
            }
            return false;
        }

        private void expect(char c) {
            if (!consume(c)) {
                throw new IllegalArgumentException("expected '" + c + "' at " + pos);
            }
        }
    }
}
