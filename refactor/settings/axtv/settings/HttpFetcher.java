/*
 * 来源: src/app/p000/lf5.java / nu1.java 的 HTTP 调用点（检查更新 JSON + 下载 apk 流）
 * 原职责: 两次 HTTP GET —— ①拉 GitHub release JSON（带 Accept 头）；②下载 apk 到文件（带 UA）
 * 重构说明: 抽出接口，使核心层不绑定 java.net.http（Android 的 android.jar 不含该包）。
 *           核心层默认实现仍用 JDK HttpClient（JVM 单测用）；
 *           Android 侧注入 OkHttp 实现（见 android/.../settings/OkHttpHttpFetcher.kt）。
 * 规格出处: settings/NOTES.md 更新时序；ADAPTERS.md §6
 */
package axtv.settings;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * 更新流程用到的最小 HTTP 能力。
 *
 * <p>与 {@code java.net.http.HttpClient} 的差异仅在于：Android 端需要一个 OkHttp 实现，
 * 而 {@code HttpClient} 在 Android 上不可用（android.jar 不含 java.net.http）。
 */
public interface HttpFetcher {

    /** HTTP 响应（文本 + 状态码 + Content-Length）。 */
    final class TextResponse {
        private final int statusCode;
        private final String body;
        private final long contentLength;

        public TextResponse(int statusCode, String body, long contentLength) {
            this.statusCode = statusCode;
            this.body = body == null ? "" : body;
            this.contentLength = contentLength;
        }

        public int statusCode() {
            return statusCode;
        }

        public String body() {
            return body;
        }

        public long contentLength() {
            return contentLength;
        }
    }

    /** 下载响应（字节流 + 状态码 + Content-Length）。调用方负责关闭 stream。 */
    final class StreamResponse {
        private final int statusCode;
        private final InputStream stream;
        private final long contentLength;

        public StreamResponse(int statusCode, InputStream stream, long contentLength) {
            this.statusCode = statusCode;
            this.stream = stream;
            this.contentLength = contentLength;
        }

        public int statusCode() {
            return statusCode;
        }

        public InputStream stream() {
            return stream;
        }

        public long contentLength() {
            return contentLength;
        }
    }

    /** GET 并读取 UTF-8 文本。 */
    TextResponse getText(String url, String acceptHeader, int timeoutSeconds) throws IOException;

    /** GET 并返回响应体字节流（非 2xx 也返回流，由调用方判断状态码）。 */
    StreamResponse getStream(String url, String userAgent) throws IOException;

    /**
     * 基于 JDK {@code java.net.http} 的实现（JVM 环境；Android 不可用 ——
     * android.jar 不含 java.net.http，故 Android 侧须显式注入 OkHttp 实现）。
     */
    static HttpFetcher jdk() {
        // 反射加载：Android 壳工程排除了 JdkHttpFetcher（android.jar 无 java.net.http），
        // 静态引用会在编译期造成硬依赖，故用反射延迟到真正调用时才解析。
        try {
            return (HttpFetcher) Class.forName("axtv.settings.JdkHttpFetcher")
                    .getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                    "JdkHttpFetcher 不可用：当前运行时不含 java.net.http（Android 需注入 OkHttp 实现）", e);
        }
    }

    /** UTF-8 常量引用，避免各处重复。 */
    default java.nio.charset.Charset charset() {
        return StandardCharsets.UTF_8;
    }
}
