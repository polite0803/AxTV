/*
 * 来源: src/app/p000/ko5.java:117-135（m7237a）、ko5.java:163-198（onPlayerError 降档分支）
 * 原职责: 解码器不兼容时切到 H.264 档位重载
 * 重构说明: 抽成回调接口；具体"怎么换 URL"属于数据层（cj5 编码偏好 + VideoPlayUrlResolverKt）
 */
package axtv.player;

/**
 * 解码失败降档。
 *
 * <p>触发条件（{@code ko5.java:118-126}）：错误链上出现
 * {@code MediaCodecVideoDecoderException}，且回调返回 true——
 * 返回 true 表示"已经换成 H.264 直链并重新加载"，此时播放器吞掉本次错误、
 * 不再上报 {@code onError}；返回 false 则走正常错误上报。
 *
 * <p>日志点：{@code "视频解码失败，已切换到 H.264 档位重载"}。
 */
public interface CodecFallback {

    /**
     * @param failedUrl 刚才失败的直链
     * @return true = 已切换（调用方静默处理）；false = 无法降级
     */
    boolean switchToH264(String failedUrl);
}
