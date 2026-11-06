/*
 * 扫码登录协议判定与解析工具（从 QrLoginBridge 拆出的纯静态函数）。
 * 拆分原因: QrLoginBridge 超 800 行；这些函数不依赖实例状态。
 */
package axtv.panel;

import java.util.Base64;
import java.util.Map;

/** check_qrconnect 响应/二维码图片解析、验证态判定、日志脱敏。 */
final class QrLoginProtocol {

    private QrLoginProtocol() {
    }

    /** 从 check_qrconnect 原始响应里抠 {@code "status":"..."}（页面用它区分 未扫码/已扫码）。 */
    static String pollStatus(String poll) {
        if (poll == null) {
            return null;
        }
        int key = poll.indexOf("\"status\"");
        if (key < 0) {
            return null;
        }
        int colon = poll.indexOf(':', key);
        int open = colon < 0 ? -1 : poll.indexOf('"', colon + 1);
        int close = open < 0 ? -1 : poll.indexOf('"', open + 1);
        if (close < 0) {
            return null;
        }
        return poll.substring(open + 1, close);
    }

    /**
     * URL 是否像验证 / 风控页。
     *
     * <p>二次验证不一定是页面里的一个弹窗：实名/短信/滑块验证常把整页跳到
     * passport、sso、verify 等域名，此时顶层 DOM 可能干干净净，只有 URL 能看出来。
     * 不能按域名白名单拦这些跳转（见 AndroidQrWebViewHost.shouldOverrideUrlLoading）。
     */
    static boolean urlLooksLikeVerification(String url) {
        if (url == null) {
            return false;
        }
        String u = url.toLowerCase(java.util.Locale.ROOT);
        return u.contains("verify") || u.contains("captcha") || u.contains("challenge")
                || u.contains("safe_check") || u.contains("identity_verify") || u.contains("/ucenter");
    }

    /**
     * 轮询响应里是否出现风控相关字段。
     *
     * <p>{@code account_flow} 是此前猜的字段（整份 APK 里都不存在），但真实响应里可能换成
     * 别的字段名。这里按常见风控键名兜底：宁可多浮一次登录页，也不要卡在「已扫码」。
     */
    static boolean pollLooksLikeVerification(String poll) {
        if (poll == null) {
            return false;
        }
        String p = poll.toLowerCase(java.util.Locale.ROOT);
        return p.contains("\"account_flow\"") || p.contains("\"verify_type\"")
                || p.contains("\"verify_center\"") || p.contains("\"second_verify\"")
                || p.contains("\"need_verify\"") || p.contains("\"verify_scene\"")
                || p.contains("\"verify_ticket\"") || p.contains("\"verify_url\"")
                || p.contains("\"captcha\"") || p.contains("\"challenge\"")
                || p.contains("identity_verify");
    }

    /**
     * 从 confirmed 响应里取「登录完成地址」。
     *
     * <p>网页自己是跳到这个地址才真正种下 sessionid；自建二维码会话只轮询、不跳转，
     * 所以手机确认后 Cookie 永远不落地——真机表现就是「手机已确认」之后一直没有下文。
     */
    static String confirmRedirectUrl(String poll) {
        if (poll == null) {
            return null;
        }
        for (String key : new String[]{
                "redirect_url", "redirectUrl", "sso_url", "login_url", "next_url", "redirect"}) {
            String value = stringField(poll, key);
            if (value == null) {
                continue;
            }
            String url = value.replace("\\/", "/").replace("\\u0026", "&").replace("&amp;", "&");
            if (url.startsWith("http://") || url.startsWith("https://")) {
                return url;
            }
        }
        return null;
    }

    /** 从 JSON 文本里抠一个字符串字段的值（不引入完整解析器，原实现读状态字段也是这么做的）。 */
    static String stringField(String json, String key) {
        int at = json.indexOf('"' + key + '"');
        if (at < 0) {
            return null;
        }
        int colon = json.indexOf(':', at);
        int open = colon < 0 ? -1 : json.indexOf('"', colon + 1);
        int close = open < 0 ? -1 : json.indexOf('"', open + 1);
        if (close < 0) {
            return null;
        }
        return json.substring(open + 1, close);
    }

    /** 日志脱敏：token/ticket/sessionid 的值换成 ***，并截断超长内容。 */
    static String redactPoll(String poll) {
        if (poll == null) {
            return "";
        }
        String text = poll.replaceAll("(?i)(\"(?:token|ticket|sessionid|sessionid_ss)\"\\s*:\\s*\")[^\"]*\"", "$1***\"");
        return text.length() > 240 ? text.substring(0, 240) + "..." : text;
    }

    /**
     * 从 {@code {"qr":"<内层 JSON>"}} 里取出 {@code data.qrcode} 的 Base64 并解码。
     * 原实现 {@code ez3.m3953h} + {@code m3952e}：先去 {@code data:image/…base64,} 前缀再 Base64 解码。
     *
     * @return PNG 字节；缺失或格式不对返回 null
     */
    public static byte[] parseQrImage(String qrField) {
        if (qrField == null) {
            return null;
        }
        String raw = qrField.trim();
        if (raw.isEmpty() || "null".equals(raw)) {
            return null;
        }
        // 有些页面把响应再 JSON 编码成字符串（"{\"data\":...}"），先剥一层。
        if (raw.length() >= 2 && raw.charAt(0) == '"' && raw.charAt(raw.length() - 1) == '"') {
            try {
                Object decoded = Json.parse(raw);
                if (decoded instanceof String value) {
                    raw = value.trim();
                }
            } catch (RuntimeException ignored) {
                // 不是合法 JSON 字符串，按原文继续
            }
        }
        String code;
        try {
            Map<String, Object> outer = Json.parseObject(raw);
            Object data = outer.get("data");
            code = data instanceof Map<?, ?> map ? String.valueOf(map.get("qrcode")) : null;
            if (code == null && data instanceof String value) {
                code = value;
            }
            if (code == null) {
                Object top = outer.get("qrcode");
                if (top != null) {
                    code = String.valueOf(top);
                }
            }
        } catch (RuntimeException e) {
            // 不是 JSON：有些页面直接返回 data:image/... 或裸 base64。
            code = raw;
        }
        if (code == null || code.isEmpty() || "null".equals(code)) {
            return null;
        }
        String base64 = code;
        int marker = base64.indexOf("base64,");
        if (base64.startsWith("data:image/") && marker > 0) {
            base64 = base64.substring(marker + "base64,".length());
        }
        try {
            // MIME 解码器容忍换行与空白（页面可能把 base64 折行）。
            return Base64.getMimeDecoder().decode(base64);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Android 的 evaluateJavascript 会把结果再包一层 JSON 字符串（原 od2.m8561d 的 JSONTokener 处理）。 */
    static String unwrapJsResult(String raw) {
        if (raw == null) {
            return null;
        }
        String text = raw.trim();
        if (text.isEmpty() || "null".equals(text)) {
            return null;
        }
        if (text.length() >= 2 && text.charAt(0) == '"' && text.charAt(text.length() - 1) == '"') {
            try {
                Object decoded = Json.parse(text);
                if (decoded instanceof String value) {
                    return value;
                }
            } catch (RuntimeException ignored) {
                // 不是合法 JSON 字符串，按原文返回
            }
        }
        return text;
    }
}
