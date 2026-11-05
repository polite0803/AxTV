/*
 * 来源: okhttp3.Response 在 n11.java / kz0.java / nz0.java / pz0.java 中的用法
 * 原职责: HTTP 响应（code / body 字符串或字节 / Set-Cookie / x-ms-token / Location 等头）
 * 重构说明: 核心层自有的响应模型，屏蔽 OkHttp；body 同时保留字节与文本两种视图
 *           （移动端 /aweme/v2/feed/ 返回 protobuf 字节，其余返回 JSON 文本）
 */
package axtv.api;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class ApiResponse {

    private final int statusCode;
    private final byte[] body;
    private final Map<String, List<String>> headers;

    public ApiResponse(int statusCode, byte[] body, Map<String, List<String>> headers) {
        this.statusCode = statusCode;
        this.body = body == null ? new byte[0] : body;
        this.headers = headers == null ? Collections.emptyMap() : headers;
    }

    public int statusCode() {
        return statusCode;
    }

    public boolean isSuccessful() {
        return statusCode >= 200 && statusCode < 300;
    }

    public byte[] bodyBytes() {
        return body;
    }

    public String bodyText() {
        return new String(body, StandardCharsets.UTF_8);
    }

    public boolean isEmptyBody() {
        return body.length == 0;
    }

    /** 大小写不敏感的头读取，返回第一个值；不存在返回 null。 */
    public String header(String name) {
        for (Map.Entry<String, List<String>> e : headers.entrySet()) {
            if (e.getKey().equalsIgnoreCase(name)) {
                List<String> values = e.getValue();
                if (values == null || values.isEmpty()) {
                    return null;
                }
                String first = values.get(0);
                return first == null ? null : first.trim();
            }
        }
        return null;
    }

    /** 对应 response.headers("Set-Cookie")。 */
    public List<String> headers(String name) {
        for (Map.Entry<String, List<String>> e : headers.entrySet()) {
            if (e.getKey().equalsIgnoreCase(name)) {
                List<String> values = e.getValue();
                return values == null ? Collections.<String>emptyList() : values;
            }
        }
        return Collections.emptyList();
    }
}
