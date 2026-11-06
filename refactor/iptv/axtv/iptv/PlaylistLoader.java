/*
 * 来源: 新建（电视直播功能，无原版对应）。
 * 原职责: 无。
 * 重构说明: 依赖注入 HttpFetcher 拉取 M3U 源并解析为 Playlist。超时与大小上限
 *           取自 Settings.IPTV_SOURCE_LOAD_TIMEOUT_SECONDS，不自造数值。
 *           网络失败（404/500/IO）返回带原因的 Playlist.failure，不返回空表充数。
 */
package axtv.iptv;

import axtv.settings.HttpFetcher;

import java.util.Optional;

/** 源加载器：HttpFetcher + M3uParser 的组合。
 *  纯函数（不持有网络连接），单测可注入假 HttpFetcher。
 */
public final class PlaylistLoader {

    private PlaylistLoader() {
    }

    /** 从 URL 拉取并解析。 */
    public static Playlist loadFromUrl(String url, String sourceId, HttpFetcher fetcher, int timeoutSeconds) {
        if (url == null || url.isBlank()) {
            return Playlist.failure("empty url", 0);
        }
        long startMs = System.currentTimeMillis();
        try {
            HttpFetcher.TextResponse res = fetcher.getText(url, "application/x-mpegurl", timeoutSeconds);
            String text = res.body();
            long bytes = text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
            ParseResult pr = M3uParser.parse(text, sourceId);
            if (pr.ok()) {
                return Playlist.success(pr.channels(), bytes);
            }
            return Playlist.failure(pr.rawError(), bytes);
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - startMs;
            return Playlist.failure("load error: " + e.getMessage(), elapsed);
        }
    }

    /** 从已有正文拉取并解析。 */
    public static Playlist loadFromContent(String content, String sourceId) {
        if (content == null || content.isBlank()) {
            return Playlist.failure("empty content", 0);
        }
        long bytes = content.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        ParseResult pr = M3uParser.parse(content, sourceId);
        if (pr.ok()) {
            return Playlist.success(pr.channels(), bytes);
        }
        return Playlist.failure(pr.rawError(), bytes);
    }
}