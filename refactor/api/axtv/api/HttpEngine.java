/*
 * 来源: n11.java 中 this.f14566d / f14567e 两个 OkHttpClient 的全部调用点
 * 原职责: 真正发出 HTTP 请求（连接/读/写超时 30s，前者跟随重定向、后者不跟随）
 * 重构说明: 抽象成接口，核心层只依赖本接口；
 *           Android 侧建议用 java.net.http.HttpClient（JDK 11+，Android 需 desugar 或换小型实现）
 *           或保留 OkHttp 适配器。两个客户端语义用 followRedirects 参数区分。
 */
package axtv.api;

import java.io.IOException;
import java.util.Map;

public interface HttpEngine {

    /**
     * 发出一次请求。
     *
     * @param method      仅 "GET" / "POST"
     * @param url         完整 URL（已带 query）
     * @param headers     请求头（大小写按原样写入）
     * @param body        POST 请求体（表单为 application/x-www-form-urlencoded 字节；GET 为 null）
     * @param contentType 请求体 Content-Type；GET 传 null
     * @param followRedirects 是否跟随重定向。原实现：普通客户端 true，Cookie 引导客户端 false
     */
    ApiResponse execute(String method,
                        String url,
                        Map<String, String> headers,
                        byte[] body,
                        String contentType,
                        boolean followRedirects) throws IOException;
}
