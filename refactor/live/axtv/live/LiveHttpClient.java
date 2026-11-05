/*
 * 来源: src/app/p000/sz0.java + b11.java 里的 okhttp 同步 GET（newCall(...).execute()）
 * 原职责: 直播参数准备阶段的两个同步 GET：
 *           1) https://live.douyin.com            取 ttwid（sz0 / n11.m8007A）
 *           2) https://live.douyin.com/<webRid>   取房间页 HTML，用正则抠 roomId / user_unique_id（b11）
 * 重构说明: 只抽 HTTP 语义（状态码 / Set-Cookie / 响应体），不绑定 OkHttp；
 *           Android 侧用 axtv.api 的 OkHttp 引擎实现，避免与 API 模块重复造轮子
 */
package axtv.live;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/** 直播引导阶段的同步 HTTP 能力。 */
public interface LiveHttpClient {

    /**
     * 发起 GET。
     *
     * @param headers 请求头（UA、Cookie），按插入顺序发送
     */
    Response get(String url, Map<String, String> headers) throws IOException;

    /**
     * 极简响应。
     *
     * @param status       HTTP 状态码
     * @param setCookies   所有 {@code Set-Cookie} 头（原样字符串）
     * @param body         响应体全文（原实现直接 {@code body.string()}）
     */
    record Response(int status, List<String> setCookies, String body) {

        public Response {
            setCookies = setCookies == null ? List.of() : List.copyOf(setCookies);
            body = body == null ? "" : body;
        }
    }
}
