/*
 * HttpFetcher 的 JDK 实现（java.net.http.HttpClient）。
 * 纯 JVM 可用；Android 侧不含 java.net.http，改用 OkHttp 实现（OkHttpHttpFetcher.kt）。
 */
package axtv.settings;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

final class JdkHttpFetcher implements HttpFetcher {

    private final HttpClient http;

    JdkHttpFetcher() {
        this(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build());
    }

    JdkHttpFetcher(HttpClient http) {
        this.http = http;
    }

    @Override
    public TextResponse getText(String url, String acceptHeader, int timeoutSeconds) throws IOException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .GET();
        if (acceptHeader != null && !acceptHeader.isEmpty()) {
            builder.header("Accept", acceptHeader);
        }
        try {
            HttpResponse<String> response =
                    http.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            long len = response.headers().firstValueAsLong("Content-Length").orElse(-1L);
            return new TextResponse(response.statusCode(), response.body(), len);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted", e);
        }
    }

    @Override
    public StreamResponse getStream(String url, String userAgent) throws IOException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url)).GET();
        if (userAgent != null && !userAgent.isEmpty()) {
            builder.header("User-Agent", userAgent);
        }
        try {
            HttpResponse<InputStream> response =
                    http.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
            long len = response.headers().firstValueAsLong("Content-Length").orElse(-1L);
            return new StreamResponse(response.statusCode(), response.body(), len);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted", e);
        }
    }
}
