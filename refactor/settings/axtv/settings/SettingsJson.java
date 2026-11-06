/*
 * 来源: src/app/p000/fl4.java（m4215x 读账号数组、m4216y 读弹幕屏蔽词，原文用 org.json.JSONArray）
 * 原职责: 配置项的 JSON 编解码
 * 重构说明: 核心层不绑 JSON 库，抽 SettingsJson 接口 + 纯 JDK 实现（只覆盖原文产生的两种形态）
 */
package axtv.settings;

import java.util.ArrayList;
import java.util.List;

/**
 * 配置层需要的极简 JSON 能力。
 *
 * <p>原文用 {@code org.json.JSONArray} 处理两个键：
 * {@code danmaku_block_words}（字符串数组）、{@code accounts_list}（对象数组，见 {@link SettingsTypes.Account}）。
 * Android 侧可用 {@code org.json} 实现本接口；纯 Java 侧用 {@link SimpleSettingsJson}。
 */
public interface SettingsJson {

    /** 解析字符串数组；空串/非法输入返回空列表（原文 catch 后回落空列表）。 */
    List<String> parseStringArray(String json);

    /** 序列化为 JSON 数组文本，等价 {@code JSONArray#toString()}（无空格、按需转义）。 */
    String writeStringArray(List<String> values);

    /** 解析账号数组；非法输入抛异常，由调用方降级为空列表（与原 try/catch 一致）。 */
    List<SettingsTypes.Account> parseAccounts(String json);

    /** 序列化账号数组，字段名与原文 {@code m4215x} 完全一致。 */
    String writeAccounts(List<SettingsTypes.Account> accounts);
}

/** 纯 JDK 实现：不追求通用，只覆盖原文产生的两种形态。 */
final class SimpleSettingsJson implements SettingsJson {

    @Override
    public List<String> parseStringArray(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        Reader r = new Reader(json);
        List<String> out = new ArrayList<>();
        r.skipWs();
        if (!r.consume('[')) {
            throw new IllegalArgumentException("not a json array");
        }
        r.skipWs();
        if (r.consume(']')) {
            return out;
        }
        while (true) {
            r.skipWs();
            out.add(r.readString());
            r.skipWs();
            if (r.consume(',')) {
                continue;
            }
            r.expect(']');
            return out;
        }
    }

    @Override
    public String writeStringArray(List<String> values) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            escape(sb, values.get(i));
        }
        return sb.append(']').toString();
    }

    @Override
    public List<SettingsTypes.Account> parseAccounts(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        Reader r = new Reader(json);
        List<SettingsTypes.Account> out = new ArrayList<>();
        r.skipWs();
        r.expect('[');
        r.skipWs();
        if (r.consume(']')) {
            return out;
        }
        while (true) {
            r.skipWs();
            r.expect('{');
            String id = "";
            String name = "";
            String cookie = "";
            String payload = "";
            String ticket = "";
            String tsSign = "";
            String privateKey = "";
            String pinHash = "";
            String pinSalt = "";
            String userId = "";
            String serverCert = "";
            r.skipWs();
            if (!r.consume('}')) {
                while (true) {
                    r.skipWs();
                    String field = r.readString();
                    r.skipWs();
                    r.expect(':');
                    r.skipWs();
                    String value = r.readString();
                    switch (field) {
                        case "id" -> id = value;
                        case "name" -> name = value;
                        case "cookie" -> cookie = value;
                        case "securityPayload" -> payload = value;
                        case "ticket" -> ticket = value;
                        case "tsSign" -> tsSign = value;
                        case "privateKey" -> privateKey = value;
                        case "pinHash" -> pinHash = value;
                        case "pinSalt" -> pinSalt = value;
                        case "userId" -> userId = value;
                        case "serverCert" -> serverCert = value;
                        default -> { /* 未知字段忽略，保持前向兼容 */ }
                    }
                    r.skipWs();
                    if (r.consume(',')) {
                        continue;
                    }
                    r.expect('}');
                    break;
                }
            }
            // 原文：id 为空则退化为 name，再为空则 "account_" + index
            String accountId = id != null && !id.isBlank() ? id : (name != null && !name.isBlank() ? name : "account_" + out.size());
            // 确保账户ID唯一：若生成的 ID 与已有账户冲突，在末尾追加 UUID 片段
            for (SettingsTypes.Account existing : out) {
                if (existing.id().equals(accountId)) {
                    accountId = accountId + "_" + java.util.UUID.randomUUID().toString().substring(0, 4);
                    break;
                }
            }
            out.add(new SettingsTypes.Account(accountId, cookie, payload, ticket, tsSign,
                    privateKey, pinHash, pinSalt, userId, serverCert));
            r.skipWs();
            if (r.consume(',')) {
                continue;
            }
            r.expect(']');
            return out;
        }
    }

    @Override
    public String writeAccounts(List<SettingsTypes.Account> accounts) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < accounts.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            SettingsTypes.Account a = accounts.get(i);
            sb.append('{');
            field(sb, "id", a.id()).append(',');
            field(sb, "name", a.id()).append(',');
            field(sb, "cookie", a.cookie()).append(',');
            field(sb, "securityPayload", a.securityPayload()).append(',');
            field(sb, "ticket", a.ticket()).append(',');
            field(sb, "tsSign", a.tsSign()).append(',');
            field(sb, "privateKey", a.privateKey()).append(',');
            field(sb, "serverCert", a.serverCert()).append(',');
            field(sb, "pinHash", a.pinHash()).append(',');
            field(sb, "pinSalt", a.pinSalt()).append(',');
            field(sb, "userId", a.userId());
            sb.append('}');
        }
        return sb.append(']').toString();
    }

    private static StringBuilder field(StringBuilder sb, String name, String value) {
        escape(sb, name);
        sb.append(':');
        return escape(sb, value == null ? "" : value);
    }

    private static StringBuilder escape(StringBuilder sb, String raw) {
        sb.append('"');
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.append('"');
    }

    /** 只为上面两种形态服务的游标解析器（不做通用校验，失败即抛）。 */
    private static final class Reader {
        private final String src;
        private int pos;

        Reader(String src) {
            this.src = src;
        }

        void skipWs() {
            while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) {
                pos++;
            }
        }

        boolean consume(char c) {
            if (pos < src.length() && src.charAt(pos) == c) {
                pos++;
                return true;
            }
            return false;
        }

        void expect(char c) {
            if (!consume(c)) {
                throw new IllegalArgumentException("expected '" + c + "' at " + pos);
            }
        }

        String readString() {
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
    }
}
