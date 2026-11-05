/*
 * 来源: src/app/p000/n11.java 及 src/app/p000/kz0.java / nz0.java / rz0.java / pz0.java 内的
 *       分散工具逻辑（isBlank、参数串拼接、URL 编码、MD5、随机串、截断日志）
 * 原职责: 请求参数序列化、签名串拼接、日志截断等零散静态工具
 * 重构说明: 抽出为 axtv.api 包内的包私有工具类；保持与原实现逐字一致的行为
 *           （例如 joinQuery 不跳过空值、truncate 按字符截断并追加 "..."）
 */
package axtv.api;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Map;

final class Texts {

    static final String ALNUM = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final char[] HEX = "0123456789abcdef".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private Texts() {
    }

    /** 对应 jx4.m6866L：null 或全空白视为空。 */
    static boolean isBlank(String s) {
        if (s == null) {
            return true;
        }
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isWhitespace(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    static String nvl(String s) {
        return s == null ? "" : s;
    }

    /** 对应 o70.m8488Z0(entries, "&", ..., mapper)：逐项拼成 "k=v"，不跳过空值。 */
    static String joinQuery(Map<String, String> params) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : params.entrySet()) {
            if (sb.length() > 0) {
                sb.append('&');
            }
            sb.append(e.getKey()).append('=').append(nvl(e.getValue()));
        }
        return sb.toString();
    }

    /** 对应 m8043h / m8010C0 的 cookie 回写：逐项 "k=v"，用 "; " 拼接，不跳过空值。 */
    static String joinSemicolon(Map<String, String> params) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : params.entrySet()) {
            if (sb.length() > 0) {
                sb.append("; ");
            }
            sb.append(e.getKey()).append('=').append(nvl(e.getValue()));
        }
        return sb.toString();
    }

    /** 对应 m7988B0：跳过 key/value 为空的项，用 "; " 拼接 "k=v"（用于 WS/头场景）。 */
    static String joinSemicolonSkippingBlank(Map<String, String> params) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : params.entrySet()) {
            if (isBlank(e.getKey()) || isBlank(e.getValue())) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append("; ");
            }
            sb.append(e.getKey()).append('=').append(e.getValue());
        }
        return sb.toString();
    }

    static String urlEncode(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (Exception e) {
            return URLEncoder.encode(nvl(s), StandardCharsets.UTF_8);
        }
    }

    /** 对应 jx4.m6886f0 / 反编译里到处出现的 "截断到 n 字符" 日志辅助。 */
    static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        if (s.length() <= max) {
            return s;
        }
        return s.substring(0, max) + "...";
    }

    static String md5Hex(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(s.getBytes(StandardCharsets.UTF_8));
            char[] out = new char[digest.length * 2];
            for (int i = 0; i < digest.length; i++) {
                int b = digest[i] & 0xFF;
                out[i * 2] = HEX[b >>> 4];
                out[i * 2 + 1] = HEX[b & 0x0F];
            }
            return new String(out);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 unavailable", e);
        }
    }

    /** 对应 m7990D：126 个字母数字随机字符 + "=="（合计 128，与 msToken 长度一致）。 */
    static String randomMsToken() {
        StringBuilder sb = new StringBuilder(128);
        for (int i = 0; i < 126; i++) {
            sb.append(ALNUM.charAt(RANDOM.nextInt(ALNUM.length())));
        }
        return sb.append("==").toString();
    }

    /** 对应 k04.m6901m：107 个字母数字随机字符（直播间 enter 用的 msToken）。 */
    static String randomLiveMsToken() {
        StringBuilder sb = new StringBuilder(107);
        for (int i = 0; i < 107; i++) {
            sb.append(ALNUM.charAt(RANDOM.nextInt(ALNUM.length())));
        }
        return sb.toString();
    }

    /** 对应 m7991F：n 个十六进制随机字符。 */
    static String randomHex(int n) {
        StringBuilder sb = new StringBuilder(n);
        for (int i = 0; i < n; i++) {
            sb.append("0123456789abcdef".charAt(RANDOM.nextInt(16)));
        }
        return sb.toString();
    }

    static boolean allDigits(String s) {
        if (isBlank(s)) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isDigit(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
