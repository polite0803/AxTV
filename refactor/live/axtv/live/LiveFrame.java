/*
 * 来源: src/app/p000/tp2.java（TAG "LiveWebSocket" 的帧模型）
 * 原职责: PushFrame —— 抖音 IM 推流帧：logId / headers / payloadEncoding / payloadType / payload
 * 重构说明: 字段顺序与原构造器一致；headers 用 LinkedHashMap 保证日志输出顺序；
 *           toString 文案与 tp2.toString() 逐字一致（便于日志比对）
 */
package axtv.live;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 抖音 IM 推流帧（protobuf {@code PushFrame}）。
 *
 * <p>线上字段号见 {@link LiveFrameCodec}。
 */
public final class LiveFrame {

    /** 帧头键：压缩算法，值为 {@code gzip} 时需要解压 payload。 */
    public static final String HEADER_COMPRESS_TYPE = "compress_type";

    /** 帧头键：ack 帧回带的服务端扩展信息。 */
    public static final String HEADER_INTERNAL_EXT = "im-internal_ext";

    /** payloadType：心跳帧。 */
    public static final String PAYLOAD_TYPE_HEARTBEAT = "hb";

    /** payloadType：下行消息帧。 */
    public static final String PAYLOAD_TYPE_MESSAGE = "msg";

    /** payloadType：上行 ack 帧。 */
    public static final String PAYLOAD_TYPE_ACK = "ack";

    private final long logId;
    private final String payloadEncoding;
    private final String payloadType;
    private final byte[] payload;
    private final Map<String, String> headers;

    public LiveFrame(long logId, String payloadEncoding, String payloadType, byte[] payload, Map<String, String> headers) {
        this.logId = logId;
        this.payloadEncoding = payloadEncoding == null ? "" : payloadEncoding;
        this.payloadType = payloadType == null ? "" : payloadType;
        this.payload = payload == null ? new byte[0] : payload;
        this.headers = headers == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(headers));
    }

    public long logId() {
        return logId;
    }

    /** 原 {@code tp2.m10634d()}：payload 编码（{@code gzip} / 空）。 */
    public String payloadEncoding() {
        return payloadEncoding;
    }

    /** 原 {@code tp2.m10635e()}：payload 类型（{@code hb} / {@code msg} / {@code ack}）。 */
    public String payloadType() {
        return payloadType;
    }

    public byte[] payload() {
        return payload.clone();
    }

    public int payloadSize() {
        return payload.length;
    }

    public Map<String, String> headers() {
        return headers;
    }

    public String header(String name) {
        return headers.get(name);
    }

    public boolean isHeartbeat() {
        return PAYLOAD_TYPE_HEARTBEAT.equals(payloadType);
    }

    @Override
    public String toString() {
        return "PushFrame(logId=" + logId
                + ", payloadEncoding=" + payloadEncoding
                + ", payloadType=" + payloadType
                + ", payload=" + LiveLogFormat.bytes(payload)
                + ", headers=" + headers
                + ")";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof LiveFrame other)) {
            return false;
        }
        return logId == other.logId
                && payloadEncoding.equals(other.payloadEncoding)
                && payloadType.equals(other.payloadType)
                && java.util.Arrays.equals(payload, other.payload)
                && headers.equals(other.headers);
    }

    @Override
    public int hashCode() {
        return Objects.hash(logId, payloadEncoding, payloadType, java.util.Arrays.hashCode(payload), headers);
    }
}
