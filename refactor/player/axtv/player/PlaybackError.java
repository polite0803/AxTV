/*
 * 来源: src/app/p000/ko5.java:154-198（onPlayerError）、ro5.java:1014
 * 原职责: 播放失败信息 + 是否硬件解码器异常
 * 重构说明: 用纯 Java 的 cause 链扫描替代对 media3 异常类的 instanceof
 */
package axtv.player;

/**
 * 一次播放失败。
 *
 * @param errorCode      media3 {@code PlaybackException.errorCode}
 * @param message        原始错误信息（为 null 时用"播放失败"）
 * @param decoderFailure 失败链上是否出现视频解码器异常 —— 对应
 *                       {@code ko5.java:163-175} 遍历 cause 找 {@code MediaCodecVideoDecoderException}
 * @param cause          原始异常，仅用于打日志
 */
public record PlaybackError(int errorCode, String message, boolean decoderFailure, Throwable cause) {

    /** 兜底文案，原样来自 {@code ko5.java:131}。 */
    public static final String FALLBACK_MESSAGE = "播放失败";

    /** 解码器异常类名，{@code ko5.java:169} 的 {@code MediaCodecVideoDecoderException}。 */
    private static final String DECODER_EXCEPTION_CLASS = "MediaCodecVideoDecoderException";

    public PlaybackError {
        if (message == null) {
            message = FALLBACK_MESSAGE;
        }
    }

    /**
     * 从 media3 异常构造：在 cause 链上找解码器异常（{@code ko5.java:163-175} 的 while 循环）。
     */
    public static PlaybackError of(int errorCode, String message, Throwable cause) {
        return new PlaybackError(errorCode, message, hasDecoderFailure(cause), cause);
    }

    /**
     * 遍历 {@code throwable} 及其 cause 链，判断是否命中解码器异常。
     *
     * <p>原实现只比较类型；这边按类名比较，避免核心层依赖 media3。
     */
    public static boolean hasDecoderFailure(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (DECODER_EXCEPTION_CLASS.equals(current.getClass().getSimpleName())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    /** 日志行，对齐 {@code ko5.java:157}：{@code "播放失败: code=..., msg=..."}。 */
    public String toLogLine() {
        return "播放失败: code=" + errorCode + ", msg=" + message;
    }
}
