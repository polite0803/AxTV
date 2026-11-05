/*
 * 来源: src/app/com/github/mytv/p004dv/widget/WidgetRoutes.java (82 行)
 * 原职责: 小组件点击跳转的 deep link 表 + 构造启动 MainActivity 的 Intent
 * 重构说明: 逐字还原全部 route 字符串、EXTRA_ROUTE key、Intent flags 872415232、
 *           MainActivity 目标；Uri.encode 在核心层用等价百分号编码表达（Android 侧直接用 Uri.encode）。
 */
package axtv.widget;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/** 小组件 deep link 与启动 Intent 规格。 */
public final class WidgetRoutes {

    /** Intent extra key（WidgetRoutes.java:13）。 */
    public static final String EXTRA_ROUTE = "com.github.axtv.dv.widget.ROUTE";
    /** 目标 Activity（:30）。 */
    public static final String TARGET_ACTIVITY = "com.github.axtv.dv.MainActivity";
    /** addFlags(872415232) = 0x34000000（:31）。 */
    public static final int INTENT_FLAGS = 872_415_232;
    /** 分解说明：FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_SINGLE_TOP | FLAG_ACTIVITY_CLEAR_TOP。 */
    public static final int FLAG_NEW_TASK = 0x1000_0000;
    public static final int FLAG_SINGLE_TOP = 0x2000_0000;
    public static final int FLAG_CLEAR_TOP = 0x0400_0000;

    private WidgetRoutes() {
    }

    // ---- 静态路由 ----
    public static String home()    { return "home"; }
    public static String history(){ return "profile/history"; }
    public static String later()  { return "profile/later"; }
    public static String live()   { return "live"; }
    public static String search() { return "search"; }
    public static String login()  { return "login"; }

    // ---- 带参数路由（Uri.encode 参数）----
    public static String liveRoom(String id) {
        return "live/room?id=" + encode(id);
    }

    public static String mixDetail(String id) {
        return "mix/detail?id=" + encode(id);
    }

    public static String playHistory(String id) {
        return "play/history?id=" + encode(id);
    }

    public static String playLater(String id) {
        return "play/later?id=" + encode(id);
    }

    public static String playMix(String id, String awemeId) {
        return "mix/play?id=" + encode(id) + "&aweme=" + encode(awemeId);
    }

    public static String searchResult(String query) {
        return "search/results?query=" + encode(query);
    }

    /**
     * 等价于 android.net.Uri.encode(String)：保留字母数字与 -_.!~*'()，其余字节按 UTF-8
     * 百分号编码（空格为 %20，不含 '+'）。Android 侧实现应直接调用 Uri.encode。
     */
    public static String encode(String s) {
        if (s == null) {
            return "";
        }
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream out = new ByteArrayOutputStream(bytes.length);
        for (byte b : bytes) {
            int c = b & 0xFF;
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9')
                    || c == '-' || c == '_' || c == '.' || c == '!' || c == '~'
                    || c == '*' || c == '\'' || c == '(' || c == ')') {
                out.write(c);
            } else {
                out.write('%');
                out.write(HEX[(c >>> 4) & 0xF]);
                out.write(HEX[c & 0xF]);
            }
        }
        // toString(Charset) 需 API 39，desugaring 不覆盖：见 sign/Assets.readText 同款规避。
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    private static final byte[] HEX = "0123456789ABCDEF".getBytes(StandardCharsets.US_ASCII);
}
