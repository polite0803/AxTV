package axtv.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import axtv.api.MobileFeedProtoCodec;
import axtv.api.MobileFeedProtoException;
import axtv.model.Aweme;
import axtv.model.FeedResponse;

/**
 * 证据: src/app/p000/g13.java（m4375f L459-501 / m4374e L157-456 / m4376g L503-507 /
 *      m4370a L37-127 / m4371b L76-103）+ cp0.java（m2570g/m2576m/m2566b/m2568d）。
 *
 * 输入字节流由本测试按 protobuf wire 格式手工构造；期望值来自 g13 字段映射，不由被测实现产出。
 */
class MobileFeedProtoCodecTest {

    @Test
    void decodesVarintLengthDelimitedNestedAndRepeatedFields() {
        byte[] avatar = strField(2, "https://a.example/x.jpg");
        byte[] author = concat(
                strField(1, "12345"),
                strField(3, "nick"),
                strField(4, "sig"),
                bytesField(6, avatar),
                strField(8, "MS4wLjABAAAAsec"));
        byte[] play = concat(
                strField(2, "https://v.example/1.mp4"),
                strField(1, "uri1"),
                varintField(3, 720),
                varintField(4, 1280),
                varintField(6, 1024));
        byte[] bitRate = concat(
                strField(1, "720p"),
                varintField(2, 2),
                varintField(3, 1000),
                bytesField(4, play));
        byte[] video = concat(
                bytesField(1, play),
                varintField(3, 720),
                varintField(4, 1280),
                bytesField(21, bitRate),
                varintField(12, 1000));
        byte[] statistics = concat(
                varintField(3, 10),
                varintField(2, 3),
                varintField(6, 1),
                varintField(5, 100000));
        byte[] aweme = concat(
                strField(1, "7123456789012345678"),
                strField(2, "hello"),
                varintField(3, 1600000000L),
                bytesField(4, author),
                bytesField(7, video),
                bytesField(10, statistics),
                varintField(16, 1),
                varintField(43, 0));
        byte[] body = concat(varintField(1, 0), varintField(4, 1), bytesField(5, aweme));

        FeedResponse response = MobileFeedProtoCodec.decode(body);
        assertNotNull(response);
        assertEquals(0, response.statusCode());
        assertTrue(response.hasMore());
        assertEquals(1, response.awemeList().size());

        Aweme a = response.awemeList().get(0);
        assertEquals("7123456789012345678", a.awemeId());
        assertEquals("hello", a.desc());
        assertEquals(1600000000L, a.createTime());
        assertEquals(1L, a.userDigged());
        assertEquals(0, a.awemeType());

        assertEquals("12345", a.author().uid());
        assertEquals("nick", a.author().nickname());
        assertEquals("sig", a.author().signature());
        assertEquals("MS4wLjABAAAAsec", a.author().secUid());
        assertEquals("https://a.example/x.jpg", a.author().avatarThumb().firstUrl());

        assertEquals("https://v.example/1.mp4", a.video().playAddr().urlList().get(0));
        assertEquals("uri1", a.video().playAddr().uri());
        // g13 L295-296 + L385：height=f3、width=f4（反直觉，按证据固化）
        assertEquals(1280, a.video().width());
        assertEquals(720, a.video().height());
        assertEquals(1000, a.video().duration());
        assertEquals(1, a.video().bitRate().size());
        assertEquals("720p", a.video().bitRate().get(0).gearName());
        assertEquals(1000, a.video().bitRate().get(0).bitRateValue());
        assertEquals(2, a.video().bitRate().get(0).qualityType());
        assertEquals("https://v.example/1.mp4", a.video().bitRate().get(0).playAddr().urlList().get(0));

        assertEquals(10L, a.statistics().diggCount());
        assertEquals(3L, a.statistics().commentCount());
        assertEquals(1L, a.statistics().shareCount());
        assertEquals(100000L, a.statistics().playCount());
    }

    @Test
    void hasMoreFalseWhenField4IsZero() {
        byte[] body = concat(varintField(1, 0), varintField(4, 0));
        FeedResponse response = MobileFeedProtoCodec.decode(body);
        assertFalse(response.hasMore());
        assertEquals(0, response.awemeList().size());
    }

    @Test
    void repeatedAwemeFieldKeepsOrder() {
        byte[] first = strField(1, "1111111111111111111");
        byte[] second = strField(1, "2222222222222222222");
        byte[] body = concat(varintField(1, 0), bytesField(5, first), bytesField(5, second));
        FeedResponse response = MobileFeedProtoCodec.decode(body);
        assertEquals(2, response.awemeList().size());
        assertEquals("1111111111111111111", response.awemeList().get(0).awemeId());
        assertEquals("2222222222222222222", response.awemeList().get(1).awemeId());
    }

    @Test
    void fallsBackToField3NestedList() {
        byte[] aweme = strField(1, "3333333333333333333");
        byte[] inner = bytesField(5, aweme);
        byte[] body = concat(varintField(1, 0), bytesField(3, inner));
        FeedResponse response = MobileFeedProtoCodec.decode(body);
        assertEquals(1, response.awemeList().size());
        assertEquals("3333333333333333333", response.awemeList().get(0).awemeId());
    }

    @Test
    void truncatedLengthDelimitedThrows() {
        byte[] body = {(byte) 0x0A, 0x05, 0x41};
        assertThrows(MobileFeedProtoException.class, () -> MobileFeedProtoCodec.decode(body));
    }

    @Test
    void truncatedVarintThrows() {
        byte[] body = {(byte) 0x08};
        assertThrows(MobileFeedProtoException.class, () -> MobileFeedProtoCodec.decode(body));
    }

    @Test
    void unknownWireTypeThrows() {
        byte[] body = tag(1, 3);
        assertThrows(MobileFeedProtoException.class, () -> MobileFeedProtoCodec.decode(body));
    }

    @Test
    void jsonBodyIsRejectedAndDetected() {
        byte[] body = "{\"status_code\":0}".getBytes(StandardCharsets.UTF_8);
        assertTrue(MobileFeedProtoCodec.looksLikeJson(body));
        assertThrows(MobileFeedProtoException.class, () -> MobileFeedProtoCodec.decode(body));
    }

    @Test
    void nullOrEmptyBodyThrows() {
        assertThrows(MobileFeedProtoException.class, () -> MobileFeedProtoCodec.decode(null));
        assertThrows(MobileFeedProtoException.class, () -> MobileFeedProtoCodec.decode(new byte[0]));
    }

    // ------------------------------------------------------------------ 字节流构造

    private static byte[] tag(int field, int wire) {
        return varint(((long) field << 3) | wire);
    }

    private static byte[] varintField(int field, long value) {
        return concat(tag(field, 0), varint(value));
    }

    private static byte[] bytesField(int field, byte[] payload) {
        return concat(tag(field, 2), varint(payload.length), payload);
    }

    private static byte[] strField(int field, String value) {
        return bytesField(field, value.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] varint(long value) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        long v = value;
        while ((v & ~0x7FL) != 0) {
            out.write((int) ((v & 0x7F) | 0x80));
            v >>>= 7;
        }
        out.write((int) v);
        return out.toByteArray();
    }

    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            out.write(part, 0, part.length);
        }
        return out.toByteArray();
    }
}
