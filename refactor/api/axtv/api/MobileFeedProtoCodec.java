/*
 * 来源: src/app/p000/g13.java（m4375f / m4374e / m4376g / m4370a / m4371b / m4372c /
 *       m4373d / m4377h）+ src/app/p000/cp0.java（字段表读取）+ c70.java（wire 解析）
 * 原职责: /aweme/v2/feed/ 的 protobuf 响应解码
 * 重构说明: 零依赖手写 wire 解析（varint=0 / fixed64=1 / length-delimited=2 / fixed32=5）。
 *           与原版差异（用户要求"严格失败"）：原版 m4377h 用 try/catch 吞掉解析错误
 *           （y42 → 结束循环），本实现截断 / 长度越界 / 未知 wire type / field==0 一律抛
 *           MobileFeedProtoException；不做 group（wire 3/4）跳过——移动 feed 不出现 group。
 *           字段编号逐条来自 g13，未取证到的字段一律不猜。
 */
package axtv.api;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import axtv.model.Author;
import axtv.model.Avatar;
import axtv.model.Aweme;
import axtv.model.BitRate;
import axtv.model.Cover;
import axtv.model.FeedResponse;
import axtv.model.PlayAddr;
import axtv.model.Statistics;
import axtv.model.Video;

public final class MobileFeedProtoCodec {

    /** g13.f6663c：sec_uid 前缀（L33）。 */
    private static final String SEC_UID_PREFIX = "MS4wLjABAAAA";
    /** g13.f6664d：解析后应丢弃的 aweme_type（101/102，L35）。 */
    private static final int TYPE_DISCARD_A = 101;
    private static final int TYPE_DISCARD_B = 102;
    /** g13.m4374e 的递归上限（i > 2 直接返回 null，L182-184）。 */
    private static final int MAX_AWEME_DEPTH = 2;

    private MobileFeedProtoCodec() {
    }

    /** 原版 m4375f L465：首字节 '{' 表示服务端退回 JSON，由调用方改走 JSON 解码。 */
    public static boolean looksLikeJson(byte[] body) {
        return body != null && body.length > 0 && body[0] == (byte) '{';
    }

    /**
     * 解码 /aweme/v2/feed/ 的 protobuf 响应，对应 g13.m4375f（L459-501）。
     *
     * @return status_code 为 0 时的响应；status_code 非 0 时返回 {@code null}
     *         （与原版 L472-478 一致：先打日志再 return null）
     * @throws MobileFeedProtoException 空体 / JSON 回退 / 任何 wire 层错误
     */
    public static FeedResponse decode(byte[] body) {
        if (body == null || body.length == 0) {
            throw new MobileFeedProtoException("移动推荐响应为空（对应 m4375f L461-463）");
        }
        if (looksLikeJson(body)) {
            throw new MobileFeedProtoException("移动推荐响应是 JSON 而非 protobuf（对应 m4375f L465-467 的 c13）");
        }
        Msg top = Msg.parse(body);
        int statusCode = (int) top.num(1);
        String statusMsg = top.str(2);
        if (statusCode != 0) {
            // 原版：statusMsg 空则记 "unknown"；这里只保留 return null 语义，日志由调用方处理。
            return null;
        }
        boolean hasMore = true;
        if (top.hasNum(4) && top.num(4) == 0) {
            hasMore = false;
        }
        List<byte[]> awemes = top.rawList(5);
        if (awemes.isEmpty()) {
            byte[] nested = first(top.rawList(3));
            awemes = nested == null ? List.of() : Msg.parse(nested).rawList(5);
        }
        List<Aweme> list = new ArrayList<>();
        for (byte[] raw : awemes) {
            Aweme aweme = aweme(0, raw);
            if (aweme != null) {
                list.add(aweme);
            }
        }
        return new FeedResponse(list, null, hasMore, 0L, 0L, statusCode);
    }

    // ------------------------------------------------------------------ Aweme（m4374e，L157-456）

    static Aweme aweme(int depth, byte[] body) {
        if (depth > MAX_AWEME_DEPTH) {
            return null;
        }
        Msg msg = Msg.parse(body);
        String id = awemeId(msg);
        String desc = msg.str(2);
        if (desc.length() > 0 && looksLikeId(desc) && id.length() == 0) {
            // m4374e L188-194：desc 是纯数字长度的 id 时与 aweme_id 交换
            String swapped = desc;
            id = swapped;
            desc = "";
        }
        long createTime = msg.num(3);

        byte[] authorRaw = first(concat(msg.rawList(4), msg.rawList(5)));
        Author author = authorRaw == null ? new Author() : author(Msg.parse(authorRaw));

        byte[] videoRaw = first(concat(msg.rawList(7), msg.rawList(8)));
        Video video = videoRaw == null ? new Video() : video(Msg.parse(videoRaw));

        byte[] statRaw = first(concat(msg.rawList(10), msg.rawList(11)));
        Statistics statistics = new Statistics();
        if (statRaw != null) {
            Msg s = Msg.parse(statRaw);
            statistics = new Statistics(s.num(3), s.num(2), s.num(6), s.num(11), s.num(5));
        }
        long userDigged = msg.num(16);
        int awemeType = awemeType(msg);

        if (id.length() > 0 && looksLikeId(id)) {
            if (awemeType == TYPE_DISCARD_A || awemeType == TYPE_DISCARD_B) {
                return null;
            }
            return new Aweme(id, desc, video, author, statistics, createTime, awemeType,
                    null, userDigged, 0L, "", null);
        }
        if (depth != 0) {
            return null;
        }
        // m4374e L439-454：顶层再扫字段 1..8 的嵌套 message，取第一个带 id 的 aweme
        List<byte[]> nested = new ArrayList<>();
        for (int f = 1; f <= 8; f++) {
            nested.addAll(msg.rawList(f));
        }
        for (byte[] raw : nested) {
            Aweme inner = aweme(depth + 1, raw);
            if (inner != null && !Texts.isBlank(inner.awemeId())) {
                return inner;
            }
        }
        return null;
    }

    /** g13.m4371b（L76-103）：字段 1 的首个非空字符串，否则字段 1 的正 varint 转字符串。 */
    private static String awemeId(Msg msg) {
        String s = msg.str(1);
        if (!s.isEmpty()) {
            return s;
        }
        if (msg.hasNum(1) && msg.num(1) > 0) {
            return String.valueOf(msg.num(1));
        }
        return "";
    }

    /** g13.m4372c（L105-142）：6 <= len < 26。原 jadx 的 isDigit 循环体为空，等价只判长度。 */
    static boolean looksLikeId(String s) {
        int len = s.length();
        return len >= 6 && len < 26;
    }

    // ------------------------------------------------------------------ Author（m4374e L199-246）

    static Author author(Msg msg) {
        String uid = awemeId(msg);
        String unique = msg.str(3);
        if (unique.isEmpty()) {
            unique = msg.str(2);
        }
        String signature = msg.str(4);
        Avatar thumb = avatar(msg.rawList(6));
        Avatar medium = avatar(msg.rawList(7));
        Avatar larger = avatar(msg.rawList(5));
        String secUid = "";
        for (String s : msg.allStrings()) {
            if (s.regionMatches(true, 0, SEC_UID_PREFIX, 0, SEC_UID_PREFIX.length())) {
                secUid = s;
                break;
            }
        }
        return new Author(uid, unique, thumb, medium, larger, secUid, signature,
                0L, 0L, 0L, 0L, 0L);
    }

    private static Avatar avatar(List<byte[]> list) {
        byte[] raw = first(list);
        return raw == null ? new Avatar() : new Avatar(urls(Msg.parse(raw), 0));
    }

    private static Cover cover(List<byte[]> list) {
        byte[] raw = first(list);
        return raw == null ? new Cover() : new Cover(urls(Msg.parse(raw), 0));
    }

    // ------------------------------------------------------------------ Video（m4374e L247-395）

    static Video video(Msg msg) {
        PlayAddr main = null;
        for (byte[] raw : concat(msg.rawList(1), msg.rawList(9))) {
            PlayAddr candidate = playAddr(raw);
            if (!candidate.urlList().isEmpty()) {
                main = candidate;
                break;
            }
        }
        if (main == null) {
            main = new PlayAddr();
        }
        Cover staticCover = null;
        for (byte[] raw : concat(msg.rawList(2), msg.rawList(10))) {
            Cover candidate = cover(List.of(raw));
            if (!candidate.urlList().isEmpty()) {
                staticCover = candidate;
                break;
            }
        }
        if (staticCover == null) {
            staticCover = new Cover();
        }
        int width = (int) msg.num(4);
        int height = (int) msg.num(3);
        Cover dynamicCover = cover(msg.rawList(5));
        Cover originCover = cover(msg.rawList(6));
        String ratio = msg.str(7);
        PlayAddr download = first(msg.rawList(8)) == null ? new PlayAddr() : playAddr(first(msg.rawList(8)));
        int duration = 0;
        for (int f : new int[]{12, 22, 32}) {
            int value = (int) msg.num(f);
            if (value > 0) {
                duration = value;
                break;
            }
        }
        List<BitRate> bitRates = new ArrayList<>();
        for (byte[] raw : concat(msg.rawList(21), msg.rawList(31))) {
            BitRate bitRate = bitRate(Msg.parse(raw));
            if (bitRate != null) {
                bitRates.add(bitRate);
            }
        }
        PlayAddr playAddr = main.urlList().isEmpty() ? download : main;
        return new Video(bitRates, playAddr, null, download, staticCover, dynamicCover,
                originCover, null, duration, width, height, ratio, "");
    }

    /** 对应 m4374e L332-383（gear_name=f1、bit_rate=f3、quality_type=f2、is_H265=f5/f7、play_addr=f4）。 */
    private static BitRate bitRate(Msg msg) {
        String gearName = msg.str(1);
        int bitRate = (int) msg.num(3);
        int qualityType = (int) msg.num(2);
        int h265 = 0;
        for (int f : new int[]{5, 7}) {
            if (msg.hasNum(f) && msg.num(f) != 0) {
                h265 = (int) msg.num(f);
                break;
            }
        }
        byte[] raw = first(msg.rawList(4));
        PlayAddr addr = raw == null ? new PlayAddr() : playAddr(raw);
        if (addr.urlList().isEmpty() && gearName.isEmpty()) {
            return null;
        }
        return new BitRate(gearName, bitRate, qualityType, h265, 0, 0, "", addr);
    }

    /** g13.m4376g（L503-507）：url_list/uri/width=f3/height=f4/data_size=f6。 */
    static PlayAddr playAddr(byte[] body) {
        Msg msg = Msg.parse(body);
        return new PlayAddr(urls(msg, 0), msg.str(1), (int) msg.num(3), (int) msg.num(4), msg.num(6));
    }

    // ------------------------------------------------------------------ URL 列表（m4370a，L37-127）

    static List<String> urls(Msg msg, int depth) {
        List<String> direct = filtered(msg.stringList(2));
        if (!direct.isEmpty()) {
            return direct;
        }
        if (depth >= 2) {
            return filtered(msg.allStrings());
        }
        List<String> nested = new ArrayList<>();
        for (byte[] raw : msg.allBytes()) {
            nested.addAll(urls(Msg.parse(raw), depth + 1));
        }
        if (!nested.isEmpty()) {
            return nested;
        }
        return filtered(msg.allStrings());
    }

    /** g13.m4373d（L145-153）：http:// 或 https:// 前缀，忽略大小写。 */
    private static boolean isUrl(String s) {
        return s.regionMatches(true, 0, "http://", 0, 7)
                || s.regionMatches(true, 0, "https://", 0, 8);
    }

    private static List<String> filtered(List<String> values) {
        List<String> out = new ArrayList<>();
        for (String v : values) {
            if (isUrl(v)) {
                out.add(v);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ aweme_type（m4374e L410-429）

    private static int awemeType(Msg msg) {
        for (int f : new int[]{43, 55, 61}) {
            if (msg.hasNum(f)) {
                int v = (int) msg.num(f);
                if (v >= 0 && v < 201) {
                    return v;
                }
            }
        }
        return 0;
    }

    private static <T> T first(List<T> list) {
        return list.isEmpty() ? null : list.get(0);
    }

    private static List<byte[]> concat(List<byte[]> a, List<byte[]> b) {
        if (a.isEmpty()) {
            return b;
        }
        if (b.isEmpty()) {
            return a;
        }
        List<byte[]> out = new ArrayList<>(a.size() + b.size());
        out.addAll(a);
        out.addAll(b);
        return out;
    }

    // ------------------------------------------------------------------ 字段表（cp0）+ wire 解析（m4377h）

    /**
     * 对应 cp0：字段号 → 按出现顺序的取值（Long=varint，byte[]=length-delimited）。
     * 读取语义逐条对齐 cp0.m2570g / m2576m / m2566b / m2568d。
     */
    static final class Msg {
        private final Map<Integer, List<Object>> fields = new LinkedHashMap<>();

        static Msg parse(byte[] body) {
            Msg msg = new Msg();
            Reader reader = new Reader(body);
            while (reader.hasMore()) {
                int tag = (int) reader.varint();
                if (tag == 0) {
                    break;
                }
                int field = tag >>> 3;
                int wire = tag & 7;
                if (field == 0) {
                    throw new MobileFeedProtoException("非法 tag：字段号为 0（对应 m2044j L305-309）");
                }
                List<Object> values = msg.fields.computeIfAbsent(field, k -> new ArrayList<>());
                switch (wire) {
                    case 0 -> values.add(reader.varint());
                    case 1 -> reader.skip(8);
                    case 2 -> values.add(reader.lengthDelimited());
                    case 5 -> reader.skip(4);
                    default -> throw new MobileFeedProtoException("未知 wire type=" + wire + " field=" + field);
                }
            }
            return msg;
        }

        /** cp0.m2570g：该字段首个 varint；不存在返回 -1（调用方用 hasNum 区分）。 */
        long num(int field) {
            List<Object> values = fields.get(field);
            if (values == null) {
                return 0L;
            }
            for (Object v : values) {
                if (v instanceof Long l) {
                    return l;
                }
            }
            return 0L;
        }

        boolean hasNum(int field) {
            List<Object> values = fields.get(field);
            if (values == null) {
                return false;
            }
            for (Object v : values) {
                if (v instanceof Long) {
                    return true;
                }
            }
            return false;
        }

        /** cp0.m2575l：该字段首个非空字符串（空串被过滤），无则 ""。 */
        String str(int field) {
            List<String> values = stringList(field);
            return values.isEmpty() ? "" : values.get(0);
        }

        List<String> stringList(int field) {
            List<String> out = new ArrayList<>();
            List<Object> values = fields.get(field);
            if (values == null) {
                return out;
            }
            for (Object v : values) {
                if (v instanceof byte[] b) {
                    String s = new String(b, StandardCharsets.UTF_8);
                    if (!s.isEmpty()) {
                        out.add(s);
                    }
                }
            }
            return out;
        }

        /** cp0.m2568d：该字段全部 length-delimited 负载。 */
        List<byte[]> rawList(int field) {
            List<byte[]> out = new ArrayList<>();
            List<Object> values = fields.get(field);
            if (values == null) {
                return out;
            }
            for (Object v : values) {
                if (v instanceof byte[] b) {
                    out.add(b);
                }
            }
            return out;
        }

        /** cp0.m2566b / g13.m2568d 的"全部字段按出现顺序"视图。 */
        List<String> allStrings() {
            List<String> out = new ArrayList<>();
            for (List<Object> values : fields.values()) {
                for (Object v : values) {
                    if (v instanceof byte[] b) {
                        String s = new String(b, StandardCharsets.UTF_8);
                        if (!s.isEmpty()) {
                            out.add(s);
                        }
                    }
                }
            }
            return out;
        }

        List<byte[]> allBytes() {
            List<byte[]> out = new ArrayList<>();
            for (List<Object> values : fields.values()) {
                for (Object v : values) {
                    if (v instanceof byte[] b) {
                        out.add(b);
                    }
                }
            }
            return out;
        }
    }

    /** 对应 c70：严格边界检查；任何越界直接抛，不吞异常。 */
    static final class Reader {
        private final byte[] buf;
        private int pos;

        Reader(byte[] buf) {
            this.buf = buf;
        }

        boolean hasMore() {
            return pos < buf.length;
        }

        long varint() {
            long value = 0;
            for (int shift = 0; shift < 64; shift += 7) {
                if (pos >= buf.length) {
                    throw new MobileFeedProtoException("varint 截断（对应 c70.m2042h 的 y42）");
                }
                byte b = buf[pos++];
                value |= (long) (b & 0x7F) << shift;
                if ((b & 0x80) == 0) {
                    return value;
                }
            }
            throw new MobileFeedProtoException("varint 超过 10 字节（malformed varint）");
        }

        byte[] lengthDelimited() {
            long len = varint();
            if (len < 0) {
                throw new MobileFeedProtoException("长度为负: " + len);
            }
            if (len > buf.length - pos) {
                throw new MobileFeedProtoException("length-delimited 越界: len=" + len
                        + " remaining=" + (buf.length - pos));
            }
            int n = (int) len;
            byte[] out = new byte[n];
            System.arraycopy(buf, pos, out, 0, n);
            pos += n;
            return out;
        }

        void skip(int n) {
            if (n > buf.length - pos) {
                throw new MobileFeedProtoException("fixed 字段越界: need=" + n
                        + " remaining=" + (buf.length - pos));
            }
            pos += n;
        }
    }
}
