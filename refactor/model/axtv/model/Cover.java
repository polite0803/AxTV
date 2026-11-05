/*
 * 来源: src/app/com/github/mytv/p004dv/model/Cover.java + Cover$$serializer.java
 * 原职责: 封面图（静态封面 / 动态封面 / 原始封面共用同一结构）
 * 重构说明: 数据类 → record
 */
package axtv.model;

import java.util.List;

/**
 * @param urlList 封面候选地址（{@code url_list}），通常前几个是不同 CDN 的同一张图
 */
public record Cover(@JsonKey("url_list") List<String> urlList) {

    public Cover {
        urlList = urlList == null ? List.of() : List.copyOf(urlList);
    }

    /** 反编译默认值：空列表（不是 null，Aweme/Video 的字段默认值依赖这一点）。 */
    public Cover() {
        this(List.of());
    }

    /** 原代码里大量出现的 {@code cover.getUrlList().get(0)} 前置判断。 */
    public String firstUrl() {
        return urlList.isEmpty() ? "" : urlList.get(0);
    }
}
