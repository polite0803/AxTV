/*
 * 来源: src/app/com/github/mytv/p004dv/MainActivity.java:78,102-126,217-224,274-286
 *       src/app/com/github/mytv/p004dv/widget/WidgetRoutes.java（13 条路由生成侧）
 * 原职责: 应用内 deep link 的生成、接收与解析；启动 Intent 里的 aweme_id / ROUTE 提取
 * 重构说明: Uri.encode / Intent 用 JDK 等价实现；路由字符串与 extra 名逐字保留
 */
package axtv.app;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 应用内路由表与 deep link 解析。 */
public final class AppRoutes {

    /** 小组件 → MainActivity 的 extra 名（WidgetRoutes.EXTRA_ROUTE）。 */
    public static final String EXTRA_ROUTE = "com.github.axtv.dv.widget.ROUTE";
    /** 启动 Intent 里直接带作品 id 的 extra 名（MainActivity.m2367w）。 */
    public static final String EXTRA_AWEME_ID = "aweme_id";

    /** {@code MainActivity.f3267o0}：从 {@code axtv://douyin/video/<digits>} 抠出 id。 */
    public static final Pattern VIDEO_PATH = Pattern.compile("/video/(\\d+)");

    public static final String HOME = "home";
    public static final String SEARCH = "search";
    public static final String LOGIN = "login";
    public static final String LIVE = "live";
    public static final String HISTORY = "profile/history";
    public static final String LATER = "profile/later";

    private AppRoutes() {
    }

    // ---- 生成侧（原 WidgetRoutes 的全部方法，逐字还原） ----

    public static String home() {
        return HOME;
    }

    public static String search() {
        return SEARCH;
    }

    public static String login() {
        return LOGIN;
    }

    public static String live() {
        return LIVE;
    }

    public static String history() {
        return HISTORY;
    }

    public static String later() {
        return LATER;
    }

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

    public static String playMix(String mixId, String awemeId) {
        return "mix/play?id=" + encode(mixId) + "&aweme=" + encode(awemeId);
    }

    public static String searchResult(String query) {
        return "search/results?query=" + encode(query);
    }

    /**
     * Android {@code Uri.encode(String)} 的 JDK 等价：UTF-8 百分号编码，
     * 保留 {@code A-Z a-z 0-9 - _ . ! ~ * ' ( )}，空格为 {@code %20}。
     */
    public static String encode(String s) {
        if (s == null) {
            throw new NullPointerException("str");
        }
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        StringBuilder out = new StringBuilder(bytes.length);
        for (byte b : bytes) {
            int c = b & 0xFF;
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                    || c == '-' || c == '_' || c == '.' || c == '!' || c == '~'
                    || c == '*' || c == '\'' || c == '(' || c == ')') {
                out.append((char) c);
            } else {
                out.append('%');
                out.append(Character.toUpperCase(Character.forDigit(c >> 4, 16)));
                out.append(Character.toUpperCase(Character.forDigit(c & 0xF, 16)));
            }
        }
        return out.toString();
    }

    // ---- 接收侧（MainActivity.m2367w / onCreate / onNewIntent） ----

    /**
     * 精确还原 {@code MainActivity.m2367w(Intent)}：
     * 优先取 extra {@code aweme_id}（trim 后必须**每一个字符都是数字**），命中即从 Intent 移除该 extra；
     * 否则用 {@link #VIDEO_PATH} 匹配 data 的 path，取第 1 个捕获组。
     *
     * @param extras 可变的 extra 表，命中 {@code aweme_id} 时会被移除（还原 removeExtra 副作用）
     */
    public static String parseAwemeId(Map<String, String> extras, String dataPath) {
        if (extras != null) {
            String raw = extras.get(EXTRA_AWEME_ID);
            if (raw != null) {
                String trimmed = raw.trim();
                if (!trimmed.isEmpty() && allDigits(trimmed)) {
                    extras.remove(EXTRA_AWEME_ID);
                    return trimmed;
                }
            }
        }
        if (dataPath == null) {
            return null;
        }
        Matcher m = VIDEO_PATH.matcher(dataPath);
        return m.find() ? m.group(1) : null;
    }

    /** 取出并移除 ROUTE extra（onCreate / onNewIntent 都这么做，防止旋转后重复导航）。 */
    public static String takeRoute(Map<String, String> extras) {
        if (extras == null) {
            return null;
        }
        String route = extras.get(EXTRA_ROUTE);
        if (route != null) {
            extras.remove(EXTRA_ROUTE);
        }
        return route;
    }

    private static boolean allDigits(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isDigit(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /** 路由 → 页面 + 参数，供 Compose NavHost 消费。 */
    public record Target(String page, Map<String, String> args) {
    }

    /** 把一条路由解析成页面与参数；未知路由返回 {@code page=route} 本身。 */
    public static Target parse(String route) {
        if (route == null || route.isEmpty()) {
            return new Target(HOME, Map.of());
        }
        int q = route.indexOf('?');
        String path = q < 0 ? route : route.substring(0, q);
        Map<String, String> args = new LinkedHashMap<>();
        if (q >= 0) {
            for (String pair : route.substring(q + 1).split("&")) {
                int eq = pair.indexOf('=');
                if (eq > 0) {
                    args.put(pair.substring(0, eq), percentDecode(pair.substring(eq + 1)));
                }
            }
        }
        return new Target(path, Map.copyOf(args));
    }

    private static String percentDecode(String s) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '%' && i + 2 < s.length()) {
                int hi = Character.digit(s.charAt(i + 1), 16);
                int lo = Character.digit(s.charAt(i + 2), 16);
                if (hi >= 0 && lo >= 0) {
                    out.write((hi << 4) | lo);
                    i += 2;
                    continue;
                }
            } else if (c == '+') {
                out.write(' ');
                continue;
            }
            // 非 ASCII 字符按 UTF-8 写入
            byte[] b = String.valueOf(c).getBytes(StandardCharsets.UTF_8);
            out.write(b, 0, b.length);
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }
}
