/*
 * 来源: src/app/p000/n11.java m8012E（msToken 申请）
 * 原职责: 当 cookie 里没有 msToken 时，向 mssdk.bytedance.com/web/r/token 申领一个
 *        （读 Set-Cookie 中长度 120 或 128 的 msToken），失败则退化为本地随机串
 * 重构说明: 独立成类，保留原始端点、请求体、请求头（注意 UA 是 Chrome/146，与默认头不同）；
 *           加密载荷作为不透明常量原样保留，不做任何"解读"或改写
 */
package axtv.api;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class MsTokenBootstrap {

    private static final String TOKEN_URL =
            "https://mssdk.bytedance.com/web/r/token?ms_appid=6383&msToken="
                    + "1YFEsL4NQgAICNSpgOo3_Yv-ue-AhfJaweBQP0xAJRNQfOqWBTklpusHaMMr80OxFFahFPSxag23YunZwRWW9PXPneC35kCHv00w-2_iQH8PynyH34b8TXekDayIhN2BQAklnxxsbompAg_WQcoErnxP8gBiWXsSkIRlw32-eUYRQu1cDIAd5Q%3D%3D";

    private static final String TOKEN_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36";

    /**
     * 原实现请求体里 strData 字段的值（9568 字符的不透明密文）。
     * 逐字保留；它的生成规则在服务端，客户端仅原样回传。
     */
    private static final String STR_DATA = "__STR_DATA__";

    private static final String BODY_PREFIX = "{\"dataType\":8,\"magic\":538969122,\"strData\":\"";
    private static final String BODY_SUFFIX = "\",\"tspFromClient\":";
    private static final String BODY_TAIL = ",\"ulr\":0,\"version\":1}";

    private MsTokenBootstrap() {
    }

    /**
     * @return 申领到的 msToken；失败返回 null（调用方回退到本地随机串）
     */
    static String fetch(HttpEngine engine, String cookieHeader, boolean uiThread) {
        if (uiThread) {
            return null;
        }
        String body = BODY_PREFIX + STR_DATA + BODY_SUFFIX + (System.currentTimeMillis() / 1000) + BODY_TAIL;
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("User-Agent", TOKEN_USER_AGENT);
        headers.put("Content-Type", "text/plain;charset=UTF-8");
        try {
            ApiResponse response = engine.execute("POST", TOKEN_URL, headers,
                    body.getBytes(StandardCharsets.UTF_8), "application/json; charset=utf-8", true);
            List<String> setCookies = response.headers("Set-Cookie");
            for (String raw : setCookies) {
                Map.Entry<String, String> pair = SetCookie.parse(raw);
                if (pair == null || !"msToken".equalsIgnoreCase(pair.getKey())) {
                    continue;
                }
                String value = pair.getValue();
                if (value != null && (value.length() == 120 || value.length() == 128)) {
                    return value;
                }
            }
        } catch (Exception ignored) {
            // 原实现 catch(Exception) 后静默回退
        }
        return null;
    }
}
