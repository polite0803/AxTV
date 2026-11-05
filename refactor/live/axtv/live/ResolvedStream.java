/*
 * 来源: src/app/com/github/mytv/dv/model/ResolvedLiveStream.java + ResolvedStreamQuality.java
 * 原职责: 选流结果（qualityName + urls）与带 level 的中间结构
 * 重构说明: 合并为一个文件的两个类型；getUrl() 取首个地址的语义保留
 */
package axtv.live;

import java.util.List;

/** 选流结果。 */
public final class ResolvedStream {

    private final List<String> urls;
    private final String qualityName;

    public ResolvedStream(List<String> urls, String qualityName) {
        this.urls = urls == null ? List.of() : List.copyOf(urls);
        this.qualityName = qualityName == null ? "" : qualityName;
    }

    public List<String> urls() {
        return urls;
    }

    /** 原 {@code getUrl()}：首个地址，没有则 null。 */
    public String url() {
        return urls.isEmpty() ? null : urls.get(0);
    }

    public String qualityName() {
        return qualityName;
    }

    public boolean isEmpty() {
        return urls.isEmpty();
    }

    @Override
    public String toString() {
        return "ResolvedLiveStream(urls=" + urls + ", qualityName=" + qualityName + ")";
    }

    /** 中间结构：带 level 的一路清晰度（原 {@code ResolvedStreamQuality}）。 */
    public record Candidate(String qualityName, int level, List<String> urls) {

        public Candidate {
            urls = urls == null ? List.of() : List.copyOf(urls);
            qualityName = qualityName == null ? "" : qualityName;
        }

        public ResolvedStream toResolvedStream() {
            return new ResolvedStream(urls, qualityName);
        }
    }
}
