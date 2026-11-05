/*
 * 来源: src/app/p000/up2.java + sp2.java（TAG "LiveWebSocket" 的解码产物）
 * 原职责: Response{ messages, internalExt, needAck } 与嵌套的 Message{ method, payload }
 * 重构说明: 合并为 LiveResponse + 内部 record Message；List 只读快照
 */
package axtv.live;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 下行 {@code Response} 消息体。
 *
 * <p>字段号：1 = messages（repeated），5 = internal_ext，9 = need_ack（见 {@link LiveFrameCodec}）。
 */
public final class LiveResponse {

    /** 单条消息：{@code method} 是 protobuf 的消息名，{@code payload} 是该消息的序列化字节。 */
    public record Message(String method, byte[] payload) {

        public Message {
            method = method == null ? "" : method;
            payload = payload == null ? new byte[0] : payload;
        }

        public byte[] payload() {
            return payload.clone();
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Message other
                    && method.equals(other.method)
                    && Arrays.equals(payload, other.payload);
        }

        @Override
        public int hashCode() {
            return Objects.hash(method, Arrays.hashCode(payload));
        }

        @Override
        public String toString() {
            return "Message(method=" + method + ", payload=" + Arrays.toString(payload) + ")";
        }
    }

    private final List<Message> messages;
    private final String internalExt;
    private final boolean needAck;

    public LiveResponse(List<Message> messages, String internalExt, boolean needAck) {
        this.messages = messages == null
                ? List.of()
                : Collections.unmodifiableList(new ArrayList<>(messages));
        this.internalExt = internalExt == null ? "" : internalExt;
        this.needAck = needAck;
    }

    public List<Message> messages() {
        return messages;
    }

    /** 原 {@code up2.m10931a()}：ack 帧要回带的内容。 */
    public String internalExt() {
        return internalExt;
    }

    /** 原 {@code up2.m10933c()}：是否需要回 ack。 */
    public boolean needAck() {
        return needAck;
    }

    /** 原日志 {@code "methods=" + List<Message>}：打印全部消息名。 */
    public List<String> methods() {
        List<String> methods = new ArrayList<>(messages.size());
        for (Message message : messages) {
            methods.add(message.method());
        }
        return methods;
    }

    @Override
    public String toString() {
        return "Response(messages=" + messages + ", internalExt=" + internalExt + ", needAck=" + needAck + ")";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof LiveResponse other)) {
            return false;
        }
        return needAck == other.needAck
                && messages.equals(other.messages)
                && internalExt.equals(other.internalExt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(messages, internalExt, needAck);
    }
}
