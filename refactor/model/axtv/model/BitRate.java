/*
 * 来源: src/app/com/github/mytv/p004dv/model/BitRate.java + BitRate$$serializer.java
 * 原职责: 一路码率（gear_name 档位名 + 编码 + 帧率 + 该档位的播放地址）
 * 重构说明: 数据类 → record；isH265/isBytevc1 在接口里是 0/1 的数字（可能以字符串下发），
 *           保留 int 语义（原代码全程用 {@code == 1} 判断，不做布尔化以免丢失「未知」态）
 */
package axtv.model;

/**
 * @param gearName     档位名（{@code gear_name}），例如 {@code normal_1080_0} / {@code adapt_lowest_1080_1}；
 *                     选流时按「包含 1080/720/540」做档位匹配
 * @param bitRateValue 码率 bps（{@code bit_rate}）
 * @param qualityType  画质类型枚举值（{@code quality_type}）
 * @param isH265       是否 H.265，1 = 是（{@code is_h265}，接口可能返回字符串）
 * @param isBytevc1    是否 bytevc1（抖音对 H.265 的另一种标记，1 = 是，{@code is_bytevc1}，可能返回字符串）
 * @param fps          帧率（{@code FPS}，注意 key 是全大写，接口可能返回字符串）
 * @param format       容器格式（{@code format}），如 mp4 / dash
 * @param playAddr     该档位的播放地址（{@code play_addr}）
 */
public record BitRate(
        @JsonKey("gear_name") String gearName,
        @JsonKey("bit_rate") int bitRateValue,
        @JsonKey("quality_type") int qualityType,
        @JsonKey("is_h265") int isH265,
        @JsonKey("is_bytevc1") int isBytevc1,
        @JsonKey("FPS") int fps,
        @JsonKey("format") String format,
        @JsonKey("play_addr") PlayAddr playAddr) {

    public BitRate {
        gearName = gearName == null ? "" : gearName;
        format = format == null ? "" : format;
        playAddr = playAddr == null ? new PlayAddr() : playAddr;
    }

    /** 反编译默认值：空档位名 / 全 0 / 空 PlayAddr。 */
    public BitRate() {
        this("", 0, 0, 0, 0, 0, "", new PlayAddr());
    }

    /**
     * 是否 HEVC（H.265）编码。原逻辑：{@code isH265() == 1 || isBytevc1() == 1}。
     * 出现在 VideoPlayUrlResolver.isHevcVariant 与 DataModelKt.encodingScore。
     */
    public boolean isHevc() {
        return isH265 == 1 || isBytevc1 == 1;
    }

    /** 该档位是否带可用地址（原代码到处判断 {@code playAddr.getUrlList().isEmpty()}）。 */
    public boolean hasUrls() {
        return playAddr.hasUrls();
    }
}
