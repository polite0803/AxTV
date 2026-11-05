/*
 * 来源: src/app/p000/k04.java 的 m6902n / m6907s / m6908t / m6906r / m6909u，
 *       以及这批方法用到的 protobuf 读写器 src/app/p000/c70.java（CodedInputStream 等价物）
 *       与 C0002a1（CodedOutputStream 等价物，被 k04.m6902n 内联使用）
 * 原职责: 抖音 IM 推流协议的帧编码 / 帧解码 / 业务消息解码
 * 重构说明:
 *   - 手写 protobuf 编解码按字段号逐条还原，varint 按 64 位无符号 LEB128 实现（负数 10 字节）
 *   - 原来散落在 6 个 p000 类里的解析合并为一个无状态工具类
 *   - k04.m6902n 里内联的 CodedOutputStream 手写 varint（第 290-356 行）等价于标准 varint 写入，未逐字复刻其分支
 *   - 未识别的字段按原实现「读 tag 后整段跳过」，不抛异常
 */
package axtv.live;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * 抖音 IM 推流协议编解码。
 *
 * <h2>PushFrame（下行帧 / 上行 ack、hb 帧）</h2>
 * <pre>
 * 1  seqId            (varint)   原实现不读不写
 * 2  logId            (varint)
 * 3  service          (string)   原实现不读不写
 * 4  method           (string)   原实现不读不写
 * 5  headers          (message)  repeated, 内含 1=key(string) 2=value(string)
 * 6  payloadEncoding  (string)
 * 7  payloadType      (string)   "msg" / "hb" / "ack"
 * 8  payload          (bytes)
 * </pre>
 *
 * <h2>Response（payload 解压后的消息体）</h2>
 * <pre>
 * 1  messages        (message)  repeated, 内含 1=method(string) 2=payload(bytes)
 * 5  internalExt     (string)
 * 9  needAck         (varint)
 * </pre>
 *
 * <h2>WebcastChatMessage</h2>
 * <pre>
 * 2  user      (message)  内含 1=id(varint) 3=nickName(string) 6=level(varint) 1028=idStr(string)
 * 3  content   (string)
 * 15 eventTime (varint)
 * 20 chatBy    (varint)
 * </pre>
 *
 * <h2>WebcastRoomUserSeqMessage</h2>
 * <pre>
 * 7  total     (varint)
 * </pre>
 */
public final class LiveFrameCodec {

    // ---- PushFrame ----
    public static final int PUSH_FIELD_LOG_ID = 2;
    public static final int PUSH_FIELD_HEADERS = 5;
    public static final int PUSH_FIELD_PAYLOAD_ENCODING = 6;
    public static final int PUSH_FIELD_PAYLOAD_TYPE = 7;
    public static final int PUSH_FIELD_PAYLOAD = 8;

    // ---- PushHeader ----
    public static final int HEADER_FIELD_KEY = 1;
    public static final int HEADER_FIELD_VALUE = 2;

    // ---- Response ----
    public static final int RESPONSE_FIELD_MESSAGES = 1;
    public static final int RESPONSE_FIELD_INTERNAL_EXT = 5;
    public static final int RESPONSE_FIELD_NEED_ACK = 9;

    // ---- Response.Message ----
    public static final int MESSAGE_FIELD_METHOD = 1;
    public static final int MESSAGE_FIELD_PAYLOAD = 2;

    // ---- WebcastChatMessage ----
    public static final int CHAT_FIELD_USER = 2;
    public static final int CHAT_FIELD_CONTENT = 3;
    public static final int CHAT_FIELD_EVENT_TIME = 15;
    public static final int CHAT_FIELD_CHAT_BY = 20;

    // ---- User ----
    public static final int USER_FIELD_ID = 1;
    public static final int USER_FIELD_NICK_NAME = 3;
    public static final int USER_FIELD_LEVEL = 6;
    public static final int USER_FIELD_ID_STR = 1028;

    // ---- RoomUserSeq ----
    public static final int SEQ_FIELD_TOTAL = 7;

    // wire types
    static final int WIRE_VARINT = 0;
    static final int WIRE_FIXED64 = 1;
    static final int WIRE_LENGTH_DELIMITED = 2;
    static final int WIRE_START_GROUP = 3;
    static final int WIRE_END_GROUP = 4;
    static final int WIRE_FIXED32 = 5;

    private static final byte[] EMPTY = new byte[0];

    private LiveFrameCodec() {
    }

    // ------------------------------------------------------------------
    // 编码
    // ------------------------------------------------------------------

    /**
     * 原 {@code k04.m6902n(payloadType, logId, payload)}。
     *
     * <p>顺序固定：logId（logId != 0 时）→ payloadType → payload（payload 非空时）。
     * 心跳/加入帧为 {@code encodeFrame("hb", 0, new byte[0])}，只有 payloadType 一个字段。
     */
    public static byte[] encodeFrame(String payloadType, long logId, byte[] payload) {
        String type = payloadType == null ? "" : payloadType;
        byte[] body = payload == null ? EMPTY : payload;
        Writer writer = new Writer();
        if (logId != 0) {
            writer.writeTag(PUSH_FIELD_LOG_ID, WIRE_VARINT);
            writer.writeVarint(logId);
        }
        writer.writeTag(PUSH_FIELD_PAYLOAD_TYPE, WIRE_LENGTH_DELIMITED);
        writer.writeBytesRaw(type.getBytes(StandardCharsets.UTF_8));
        if (body.length != 0) {
            writer.writeTag(PUSH_FIELD_PAYLOAD, WIRE_LENGTH_DELIMITED);
            writer.writeBytesRaw(body);
        }
        return writer.toByteArray();
    }

    /** 原 {@code k04.m6902n("hb", 0, new byte[0])}：加入房间 / 心跳帧。 */
    public static byte[] encodeHeartbeatFrame() {
        return encodeFrame(LiveFrame.PAYLOAD_TYPE_HEARTBEAT, 0L, EMPTY);
    }

    /** 原 {@code k04.m6902n("ack", logId, internalExtBytes)}。 */
    public static byte[] encodeAckFrame(long logId, String internalExt) {
        String ext = internalExt == null ? "" : internalExt;
        return encodeFrame(LiveFrame.PAYLOAD_TYPE_ACK, logId, ext.getBytes(StandardCharsets.UTF_8));
    }

    // ------------------------------------------------------------------
    // 帧解码
    // ------------------------------------------------------------------

    /** 原 {@code k04.m6907s(byte[])}。 */
    public static LiveFrame decodeFrame(byte[] raw) {
        Reader reader = new Reader(raw);
        long logId = 0L;
        String payloadEncoding = "";
        String payloadType = "";
        byte[] payload = EMPTY;
        Map<String, String> headers = new LinkedHashMap<>();
        int tag;
        while ((tag = reader.readTag()) > 0) {
            int field = tag >>> 3;
            switch (field) {
                case PUSH_FIELD_LOG_ID -> logId = reader.readVarint();
                case PUSH_FIELD_HEADERS -> {
                    String[] header = decodeHeader(reader.readBytes());
                    if (header != null) {
                        headers.put(header[0], header[1]);
                    }
                }
                case PUSH_FIELD_PAYLOAD_ENCODING -> payloadEncoding = reader.readString();
                case PUSH_FIELD_PAYLOAD_TYPE -> payloadType = reader.readString();
                case PUSH_FIELD_PAYLOAD -> payload = reader.readBytes();
                default -> reader.skipField(tag);
            }
        }
        return new LiveFrame(logId, payloadEncoding, payloadType, payload, headers);
    }

    /** PushHeader：key 为空则整条丢弃（原实现 {@code if (!isBlank(key)) put}）。 */
    private static String[] decodeHeader(byte[] raw) {
        Reader reader = new Reader(raw);
        String key = "";
        String value = "";
        int tag;
        while ((tag = reader.readTag()) > 0) {
            int field = tag >>> 3;
            if (field == HEADER_FIELD_KEY) {
                key = reader.readString();
            } else if (field == HEADER_FIELD_VALUE) {
                value = reader.readString();
            } else {
                reader.skipField(tag);
            }
        }
        return LiveLogFormat.isBlank(key) ? null : new String[]{key, value};
    }

    /**
     * 原 {@code cq2.m2584d} 的解压判定：
     * {@code compress_type} 头为 gzip、或 {@code payloadEncoding} 为 gzip、或 payload 以 1F 8B 开头。
     */
    public static byte[] decodePayload(LiveFrame frame) throws IOException {
        byte[] payload = frame.payload();
        if (payload.length == 0) {
            return EMPTY;
        }
        String compressType = frame.header(LiveFrame.HEADER_COMPRESS_TYPE);
        boolean gzip = "gzip".equalsIgnoreCase(compressType == null ? "" : compressType)
                || "gzip".equalsIgnoreCase(frame.payloadEncoding())
                || (payload.length >= 2 && payload[0] == 0x1F && (payload[1] & 0xFF) == 0x8B);
        if (!gzip) {
            return payload;
        }
        try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(payload))) {
            // InputStream.readAllBytes 为 Java 9 / API 33 API，minSdk 23 下不可用；手写累积，语义一致。
            ByteArrayOutputStream out = new ByteArrayOutputStream(payload.length);
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) != -1) {
                out.write(buffer, 0, n);
            }
            return out.toByteArray();
        }
    }

    // ------------------------------------------------------------------
    // 业务消息解码
    // ------------------------------------------------------------------

    /** 原 {@code k04.m6908t(byte[])}。 */
    public static LiveResponse decodeResponse(byte[] raw) {
        Reader reader = new Reader(raw);
        List<LiveResponse.Message> messages = new ArrayList<>();
        String internalExt = "";
        boolean needAck = false;
        int tag;
        while ((tag = reader.readTag()) > 0) {
            int field = tag >>> 3;
            switch (field) {
                case RESPONSE_FIELD_MESSAGES -> messages.add(decodeMessage(reader.readBytes()));
                case RESPONSE_FIELD_INTERNAL_EXT -> internalExt = reader.readString();
                case RESPONSE_FIELD_NEED_ACK -> needAck = reader.readVarint() != 0L;
                default -> reader.skipField(tag);
            }
        }
        return new LiveResponse(messages, internalExt, needAck);
    }

    private static LiveResponse.Message decodeMessage(byte[] raw) {
        Reader reader = new Reader(raw);
        String method = "";
        byte[] payload = EMPTY;
        int tag;
        while ((tag = reader.readTag()) > 0) {
            int field = tag >>> 3;
            if (field == MESSAGE_FIELD_METHOD) {
                method = reader.readString();
            } else if (field == MESSAGE_FIELD_PAYLOAD) {
                payload = reader.readBytes();
            } else {
                reader.skipField(tag);
            }
        }
        return new LiveResponse.Message(method, payload);
    }

    /** 原 {@code k04.m6906r(byte[])}。 */
    public static LiveChat.Message decodeChat(byte[] raw) {
        Reader reader = new Reader(raw);
        LiveChat.User user = new LiveChat.User(0L, "", 0, "");
        String content = "";
        long eventTime = 0L;
        int chatBy = 0;
        int tag;
        while ((tag = reader.readTag()) > 0) {
            int field = tag >>> 3;
            switch (field) {
                case CHAT_FIELD_USER -> user = decodeUser(reader.readBytes());
                case CHAT_FIELD_CONTENT -> content = reader.readString();
                case CHAT_FIELD_EVENT_TIME -> eventTime = reader.readVarint();
                case CHAT_FIELD_CHAT_BY -> chatBy = (int) reader.readVarint();
                default -> reader.skipField(tag);
            }
        }
        return new LiveChat.Message(user, content, eventTime, chatBy);
    }

    private static LiveChat.User decodeUser(byte[] raw) {
        Reader reader = new Reader(raw);
        long id = 0L;
        String nickName = "";
        int level = 0;
        String idStr = "";
        int tag;
        while ((tag = reader.readTag()) > 0) {
            int field = tag >>> 3;
            switch (field) {
                case USER_FIELD_ID -> id = reader.readVarint();
                case USER_FIELD_NICK_NAME -> nickName = reader.readString();
                case USER_FIELD_LEVEL -> level = (int) reader.readVarint();
                case USER_FIELD_ID_STR -> idStr = reader.readString();
                default -> reader.skipField(tag);
            }
        }
        return new LiveChat.User(id, nickName, level, idStr);
    }

    /** 原 {@code k04.m6909u(byte[])}。 */
    public static LiveChat.RoomUserSeq decodeRoomUserSeq(byte[] raw) {
        Reader reader = new Reader(raw);
        long total = 0L;
        int tag;
        while ((tag = reader.readTag()) > 0) {
            if ((tag >>> 3) == SEQ_FIELD_TOTAL) {
                total = reader.readVarint();
            } else {
                reader.skipField(tag);
            }
        }
        return new LiveChat.RoomUserSeq(total);
    }

    // ------------------------------------------------------------------
    // protobuf 读写器
    // ------------------------------------------------------------------

    /** 对应原 {@code c70}（CodedInputStream）。读到末尾返回 -1，调用方按 {@code >0} 判断。 */
    static final class Reader {

        private final byte[] buffer;
        private final int limit;
        private int pos;

        Reader(byte[] buffer) {
            this.buffer = buffer == null ? EMPTY : buffer;
            this.limit = this.buffer.length;
        }

        boolean isAtEnd() {
            return pos >= limit;
        }

        /** 读一个 tag；到末尾返回 -1。field == 0 视为非法（原实现在此抛 y42）。 */
        int readTag() {
            if (isAtEnd()) {
                return -1;
            }
            long tag = readVarint();
            if (tag == 0L) {
                throw new IllegalArgumentException("Protocol message contained an invalid tag (zero).");
            }
            return (int) tag;
        }

        long readVarint() {
            long result = 0L;
            int shift = 0;
            while (shift < 64) {
                if (pos >= limit) {
                    throw new IllegalArgumentException("CodedInputStream encountered an incomplete varint.");
                }
                int b = buffer[pos++] & 0xFF;
                result |= (long) (b & 0x7F) << shift;
                if ((b & 0x80) == 0) {
                    return result;
                }
                shift += 7;
            }
            throw new IllegalArgumentException("CodedInputStream encountered a malformed varint.");
        }

        String readString() {
            int length = (int) readVarint();
            if (length <= 0) {
                return "";
            }
            if (length > limit - pos) {
                throw new IllegalArgumentException("CodedInputStream encountered a truncated string.");
            }
            String value = new String(buffer, pos, length, StandardCharsets.UTF_8);
            pos += length;
            return value;
        }

        byte[] readBytes() {
            int length = (int) readVarint();
            if (length == 0) {
                return EMPTY;
            }
            if (length < 0) {
                throw new IllegalArgumentException("CodedInputStream encountered a negative size.");
            }
            if (length > limit - pos) {
                throw new IllegalArgumentException("CodedInputStream encountered a truncated message.");
            }
            byte[] value = new byte[length];
            System.arraycopy(buffer, pos, value, 0, length);
            pos += length;
            return value;
        }

        void skip(int count) {
            if (count < 0 || count > limit - pos) {
                throw new IllegalArgumentException("CodedInputStream skipped past end of message.");
            }
            pos += count;
        }

        /** 原 {@code c70.m2045k(tag)}：按 wire type 整段跳过未知字段。 */
        void skipField(int tag) {
            switch (tag & 0x07) {
                case WIRE_VARINT -> readVarint();
                case WIRE_FIXED64 -> skip(8);
                case WIRE_LENGTH_DELIMITED -> skip((int) readVarint());
                case WIRE_FIXED32 -> skip(4);
                case WIRE_START_GROUP -> {
                    int nested;
                    while ((nested = readTag()) > 0) {
                        if ((nested & 0x07) == WIRE_END_GROUP) {
                            break;
                        }
                        skipField(nested);
                    }
                }
                case WIRE_END_GROUP -> {
                    // 由外层 group 循环消费
                }
                default -> throw new IllegalArgumentException("Protocol message tag had invalid wire type.");
            }
        }
    }

    /** 对应原 {@code C0002a1}（CodedOutputStream）的写入部分。 */
    static final class Writer {

        private final ByteArrayOutputStream out = new ByteArrayOutputStream(64);

        void writeTag(int field, int wireType) {
            writeVarint(((long) field << 3) | (wireType & 0x07));
        }

        void writeVarint(long value) {
            long remaining = value;
            while ((remaining & ~0x7FL) != 0L) {
                out.write((int) ((remaining & 0x7F) | 0x80));
                remaining >>>= 7;
            }
            out.write((int) remaining);
        }

        /** 先写长度前缀，再写内容。 */
        void writeBytesRaw(byte[] value) {
            writeVarint(value.length);
            out.write(value, 0, value.length);
        }

        byte[] toByteArray() {
            return out.toByteArray();
        }
    }
}
