/*
 * 来源: src/app/com/github/mytv/p004dv/model/Video.java + Video$$serializer.java
 * 原职责: 作品的播放信息：多码率列表、H.264/H.265 主地址、下载地址、三种封面、进度条缩略图、时长与画幅
 * 重构说明: 数据类 → record；去掉 13 个 default 位掩码构造器，默认值集中到无参构造器
 */
package axtv.model;

import java.util.List;

/**
 * @param bitRate    多码率列表（{@code bit_rate}），选流的主要来源
 * @param playAddr   H.264 主播放地址（{@code play_addr}）
 * @param playAddr265 H.265 主播放地址（{@code play_addr_265}）
 * @param downloadAddr 下载地址（{@code download_addr}）
 * @param cover      静态封面（{@code cover}）
 * @param dynamicCover 动态封面（{@code dynamic_cover}）
 * @param originCover 原始封面（{@code origin_cover}）
 * @param bigThumbs  进度条缩略图（{@code big_thumbs}）
 * @param duration   时长，毫秒（{@code duration}）
 * @param width      宽（{@code width}）
 * @param height     高（{@code height}）
 * @param ratio      画幅比例字符串（{@code ratio}），如 {@code 720p}
 * @param authenticationToken 播放鉴权 token（{@code authentication_token}）
 */
public record Video(
        @JsonKey("bit_rate") List<BitRate> bitRate,
        @JsonKey("play_addr") PlayAddr playAddr,
        @JsonKey("play_addr_265") PlayAddr playAddr265,
        @JsonKey("download_addr") PlayAddr downloadAddr,
        @JsonKey("cover") Cover cover,
        @JsonKey("dynamic_cover") Cover dynamicCover,
        @JsonKey("origin_cover") Cover originCover,
        @JsonKey("big_thumbs") List<VideoBigThumb> bigThumbs,
        @JsonKey("duration") int duration,
        @JsonKey("width") int width,
        @JsonKey("height") int height,
        @JsonKey("ratio") String ratio,
        @JsonKey("authentication_token") String authenticationToken) {

    public Video {
        bitRate = bitRate == null ? List.of() : List.copyOf(bitRate);
        playAddr = playAddr == null ? new PlayAddr() : playAddr;
        playAddr265 = playAddr265 == null ? new PlayAddr() : playAddr265;
        downloadAddr = downloadAddr == null ? new PlayAddr() : downloadAddr;
        cover = cover == null ? new Cover() : cover;
        dynamicCover = dynamicCover == null ? new Cover() : dynamicCover;
        originCover = originCover == null ? new Cover() : originCover;
        bigThumbs = bigThumbs == null ? List.of() : List.copyOf(bigThumbs);
        ratio = ratio == null ? "" : ratio;
        authenticationToken = authenticationToken == null ? "" : authenticationToken;
    }

    /** 反编译默认值：空列表 / 空 PlayAddr / 空 Cover / 0 / ""。 */
    public Video() {
        this(List.of(), new PlayAddr(), new PlayAddr(), new PlayAddr(),
                new Cover(), new Cover(), new Cover(), List.of(), 0, 0, 0, "", "");
    }

    /** 是否有任何可播地址（主地址或任一码率）。 */
    public boolean isPlayable() {
        if (playAddr.hasUrls()) {
            return true;
        }
        for (BitRate br : bitRate) {
            if (br.hasUrls()) {
                return true;
            }
        }
        return false;
    }
}
