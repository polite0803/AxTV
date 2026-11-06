/*
 * 来源: 新建（电视直播功能，无原版对应）。
 * 原职责: 无。
 * 重构说明: 一份 M3U 播放列表解析后的结果。成功时含频道表，失败时 rawError 非空。
 *           不把失败当成"没有频道"返回空表 —— 失败必须带原因，由 UI 展示可重试的错误态。
 */
package axtv.iptv;

import java.util.List;

/**
 * 解析结果。
 *
 * <p>两种互斥状态：
 * <ul>
 *   <li>成功：{@code channels} 非空，{@code rawError} 为空；</li>
 *   <li>失败：{@code rawError} 非空，{@code channels} 为空（不返回空表充数）。</li>
 * </ul>
 *
 * @param channels  解析到的频道（可能为空，仅当 rawError 也为空时）
 * @param rawError  失败原因原文；成功时为空串
 * @param bytes     原始字节数（用于 UI 显示源大小与健康度）
 */
public record Playlist(List<Channel> channels, String rawError, long bytes) {

    public Playlist {
        channels = channels == null ? List.of() : List.copyOf(channels);
        rawError = rawError == null ? "" : rawError;
    }

    public static Playlist success(List<Channel> channels, long bytes) {
        return new Playlist(channels, "", bytes);
    }

    public static Playlist failure(String rawError, long bytes) {
        return new Playlist(List.of(), rawError, bytes);
    }

    /** 是否解析成功（有内容可播）。 */
    public boolean ok() {
        return rawError.isEmpty() && !channels.isEmpty();
    }

    /** 是否明确失败。 */
    public boolean failed() {
        return !rawError.isEmpty();
    }

    /** 是否成功但频道数为 0（空清单，与失败要能区分）。 */
    public boolean empty() {
        return rawError.isEmpty() && channels.isEmpty();
    }
}