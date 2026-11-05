/*
 * 来源: src/app/com/github/mytv/p004dv/model/PlayAddr.java + PlayAddr$$serializer.java
 * 原职责: 视频/直播的播放地址集合（一组等价 url + 分辨率 + 体积）
 * 重构说明: 数据类 → record；urlList 是「同一路流的多个 CDN 地址」，播放器按顺序挑选
 */
package axtv.model;

import java.util.List;

/**
 * @param urlList 同一路流的多个候选地址，按接口给出的顺序（{@code url_list}）
 * @param uri     数据 URI / 原始地址（{@code uri}）
 * @param width   视频宽度（{@code width}）
 * @param height  视频高度（{@code height}）
 * @param dataSize 文件字节数（{@code data_size}）
 */
public record PlayAddr(
        @JsonKey("url_list") List<String> urlList,
        @JsonKey("uri") String uri,
        @JsonKey("width") int width,
        @JsonKey("height") int height,
        @JsonKey("data_size") long dataSize) {

    public PlayAddr {
        urlList = urlList == null ? List.of() : List.copyOf(urlList);
        uri = uri == null ? "" : uri;
    }

    /** 反编译默认值：urlList = 空列表、uri = ""、宽高 = 0、dataSize = 0。 */
    public PlayAddr() {
        this(List.of(), "", 0, 0, 0L);
    }

    /** 原 {@code getUrlList().isEmpty()} 的常用判断点，单独提出来避免重复。 */
    public boolean hasUrls() {
        return !urlList.isEmpty();
    }

    /** 像素数，VideoPlayUrlResolver 排序用的口径（width * height）。 */
    public int pixelCount() {
        return width * height;
    }
}
