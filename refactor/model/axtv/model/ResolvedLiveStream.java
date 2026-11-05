/*
 * 来源: src/app/com/github/mytv/p004dv/model/ResolvedLiveStream.java
 * 原职责: 直播选流结果（一组可播地址 + 档位显示名），交给播放器
 * 重构说明: 数据类 → record；getUrl() 是「取第一条地址」的便捷方法，保留为 firstUrl()
 */
package axtv.model;

import java.util.List;

/**
 * @param urls        可播地址，flv 在前 hls 在后（同档位两个协议都给，播放器按能力挑）
 * @param qualityName 档位中文名，用于浮层显示
 */
public record ResolvedLiveStream(List<String> urls, String qualityName) {

    public ResolvedLiveStream {
        urls = urls == null ? List.of() : List.copyOf(urls);
        qualityName = qualityName == null ? "默认" : qualityName;
    }

    /** 原 {@code getUrl()}：第一条地址，没有则空串。 */
    public String firstUrl() {
        return urls.isEmpty() ? "" : urls.get(0);
    }
}
