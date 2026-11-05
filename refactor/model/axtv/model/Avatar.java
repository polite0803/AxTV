/*
 * 来源: src/app/com/github/mytv/p004dv/model/Avatar.java + Avatar$$serializer.java
 * 原职责: 用户头像（缩略/中/大三档共用同一结构）
 * 重构说明: 数据类 → record
 */
package axtv.model;

import java.util.List;

/**
 * @param urlList 头像候选地址（{@code url_list}）
 */
public record Avatar(@JsonKey("url_list") List<String> urlList) {

    public Avatar {
        urlList = urlList == null ? List.of() : List.copyOf(urlList);
    }

    /** 反编译默认值：空列表。 */
    public Avatar() {
        this(List.of());
    }

    public String firstUrl() {
        return urlList.isEmpty() ? "" : urlList.get(0);
    }
}
