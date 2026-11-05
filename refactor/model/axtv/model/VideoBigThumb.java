/*
 * 来源: src/app/com/github/mytv/p004dv/model/VideoBigThumb.java + VideoBigThumb$$serializer.java
 * 原职责: 视频进度条缩略图雪碧图（一张大图切成 img_x_len × img_y_len 格，每格 img_x_size × img_y_size）
 * 重构说明: 数据类 → record；五个尺寸字段接口会返回字符串，用 FlexibleJson 语义解码
 */
package axtv.model;

import java.util.List;

/**
 * @param imgUrls  雪碧图地址（{@code img_urls}）
 * @param imgXSize 单格宽（{@code img_x_size}，接口可能返回字符串）
 * @param imgYSize 单格高（{@code img_y_size}，接口可能返回字符串）
 * @param imgXLen  横向格数（{@code img_x_len}，接口可能返回字符串）
 * @param imgYLen  纵向格数（{@code img_y_len}，接口可能返回字符串）
 * @param imgNum   总格数（{@code img_num}，接口可能返回字符串）
 */
public record VideoBigThumb(
        @JsonKey("img_urls") List<String> imgUrls,
        @JsonKey("img_x_size") int imgXSize,
        @JsonKey("img_y_size") int imgYSize,
        @JsonKey("img_x_len") int imgXLen,
        @JsonKey("img_y_len") int imgYLen,
        @JsonKey("img_num") int imgNum) {

    public VideoBigThumb {
        imgUrls = imgUrls == null ? List.of() : List.copyOf(imgUrls);
    }

    public VideoBigThumb() {
        this(List.of(), 0, 0, 0, 0, 0);
    }
}
