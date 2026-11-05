/*
 * 来源: src/app/p000/AbstractC0656g4.java（m4464l / m4445U / m4454c0 / m4475z）
 *       + src/app/p000/n11.java m8037c（5 个 bd-ticket-guard-* 头）
 * 原职责: 交互安全（ticket-guard）请求头编码
 * 取证更正: m4464l 不是 protobuf——它产出 Base64(JSON)：
 *           {"ts_sign":<凭据tsSign>,"req_content":"ticket,path,timestamp",
 *            "req_sign":<ECDSA签名>,"timestamp":<秒>,"t_trust":1}
 *           签名 = SHA256withECDSA("ticket=<ticket>&path=<path>&timestamp=<秒>")，
 *           私钥经 m4445U 解析（PKCS8 Base64 / PEM）。证据 file:line 见报告。
 * 重构说明: 零依赖（JCE + java.util.Base64 + 手写 JSON/DER/EC 点乘）。
 *           - serverCert 非空 → TicketGuardUnsupportedException（m4440P 加密分支
 *             离线不可取证复现，明确失败，不伪造普通签名头）。
 *           - ree-public-key：m4425A 被 jadx 判定"Method not decompiled"（2921 指令
 *             单元），无法按原算法取证；改用标准 JCE 语义——从 PKCS8 EC 私钥导出
 *             公钥（手写仿射点乘），失败回退 cookie 解码（m4454c0），两者都失败
 *             则抛 TicketGuardException（等价原版重抛异常，不静默）。
 */
package axtv.api;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.ECFieldFp;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

public final class TicketGuardHeaderCodec {

    private TicketGuardHeaderCodec() {
    }

    // ------------------------------------------------------------------ 五个头（n11.m8037c）

    /**
     * 构造 bd-ticket-guard-* 请求头，对应 n11.m8037c（src/app/p000/n11.java L2908-2934）。
     *
     * <ul>
     *   <li>凭据不可用 → 空 Map（原语义：m2724a 为假直接返回空表，合法出口）</li>
     *   <li>serverCert 非空 → {@link TicketGuardUnsupportedException}（加密分支离线不可复现）</li>
     *   <li>公钥派生失败 → cookie 回退；cookie 也不可用 → {@link TicketGuardException}（原版重抛）</li>
     *   <li>client-data 的时间戳取当前秒（System.currentTimeMillis()/1000，m4464l L1153）</li>
     * </ul>
     *
     * @param path             请求路径（进入签名串）
     * @param credentials      交互安全凭据（ticket / tsSign / privateKey / serverCert）
     * @param clientDataCookie cookie["bd_ticket_guard_client_data"] 原始值，公钥回退用
     */
    public static Map<String, String> buildHeaders(String path,
                                                   TicketGuardCredentials credentials,
                                                   String clientDataCookie) {
        if (credentials == null || !credentials.isUsable()) {
            return new LinkedHashMap<>();
        }
        if (!Texts.isBlank(credentials.serverCert())) {
            throw new TicketGuardUnsupportedException(
                    "ticket-guard serverCert 加密分支（AbstractC0656g4.m4440P，ECDH+HKDF）离线无法取证复现，"
                            + "本轮不伪造普通签名头；请清除 serverCert 凭据或提供真机证书交换产物");
        }
        String publicKey = derivePublicKeyOrFallback(credentials.privateKey(), clientDataCookie);
        long timestamp = System.currentTimeMillis() / 1000L;
        LinkedHashMap<String, String> headers = new LinkedHashMap<>();
        headers.put("bd-ticket-guard-client-data", clientData(path, credentials, timestamp));
        headers.put("bd-ticket-guard-ree-public-key", publicKey);
        headers.put("bd-ticket-guard-version", "2");
        headers.put("bd-ticket-guard-web-sign-type", "1");
        headers.put("bd-ticket-guard-web-version", "2");
        return headers;
    }

    // ------------------------------------------------------------------ client-data（m4464l）

    /** 签名输入串："ticket=<ticket>&path=<path>&timestamp=<秒>"（m4464l L1154-1156）。 */
    public static String toSignString(String path, String ticket, long timestampSeconds) {
        return "ticket=" + ticket + "&path=" + path + "&timestamp=" + timestampSeconds;
    }

    /** 真实签名路径：SHA256withECDSA + 私钥解析 + Base64（m4464l L1161-1168）。 */
    public static String signEcdsaBase64(String toSign, String privateKey) {
        try {
            Signature signature = Signature.getInstance("SHA256withECDSA");
            signature.initSign(parsePrivateKey(privateKey));
            signature.update(toSign.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signature.sign());
        } catch (TicketGuardException e) {
            throw e;
        } catch (Exception e) {
            throw new TicketGuardException("ticket-guard ECDSA 签名失败", e);
        }
    }

    /** 真实 client-data：签名 + JSON 拼装 + Base64（m4464l 全流程）。 */
    public static String clientData(String path, TicketGuardCredentials credentials, long timestampSeconds) {
        String reqSign = signEcdsaBase64(toSignString(path, credentials.ticket(), timestampSeconds),
                credentials.privateKey());
        return encodeClientData(credentials.tsSign(), timestampSeconds, reqSign);
    }

    /**
     * 纯 JSON 拼装（供字节级单测注入固定 reqSign），字段顺序与 m4464l L1193-1198 逐字一致：
     * {@code {"ts_sign":"..","req_content":"ticket,path,timestamp","req_sign":"..","timestamp":N,"t_trust":1}}
     */
    public static String encodeClientData(String tsSign, long timestampSeconds, String reqSign) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"ts_sign\":\"").append(escapeJson(tsSign));
        sb.append("\",\"req_content\":\"ticket,path,timestamp\",\"req_sign\":\"");
        sb.append(escapeJson(reqSign));
        sb.append("\",\"timestamp\":").append(timestampSeconds);
        sb.append(",\"t_trust\":1}");
        return Base64.getEncoder().encodeToString(sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    /** JSON 字符串转义，逐字对应 m4475z（L1366-1385）：仅 \t \n \r \" \\ 五种。 */
    public static String escapeJson(String str) {
        StringBuilder sb = new StringBuilder(str.length());
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (c == '\t') {
                sb.append("\\t");
            } else if (c == '\n') {
                sb.append("\\n");
            } else if (c == '\r') {
                sb.append("\\r");
            } else if (c == '"') {
                sb.append("\\\"");
            } else if (c != '\\') {
                sb.append(c);
            } else {
                sb.append("\\\\");
            }
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ 公钥（m4425A 替代 + m4454c0 回退）

    /**
     * 公钥取得顺序 = n11.m8037c L2913-2931：
     * ① 从私钥派生（m4425A，jadx 反编译失败 → 标准 JCE 语义替代，可信度"中"）；
     * ② 派生抛异常 → cookie 解码（m4454c0）；
     * ③ 都失败 → 抛 TicketGuardException（原版此处重抛原始异常，不静默降级）。
     */
    public static String derivePublicKeyOrFallback(String privateKey, String clientDataCookie) {
        try {
            return deriveReePublicKey(privateKey);
        } catch (RuntimeException e) {
            String fromCookie = decodeClientDataCookie(clientDataCookie);
            if (!Texts.isBlank(fromCookie)) {
                return fromCookie;
            }
            throw new TicketGuardException(
                    "bd-ticket-guard-ree-public-key 派生失败且 cookie 回退不可用（原版 m8037c L2929-2931 重抛）", e);
        }
    }

    /**
     * 从 PKCS8 EC 私钥导出公钥：取 ECParameterSpec → 手写仿射点乘 W=d·G → ECPublicKeySpec →
     * X.509/SPKI DER → Base64。编码格式为推断（原 m4425A 无法取证），报告标可信度"中"。
     */
    public static String deriveReePublicKey(String privateKey) {
        PrivateKey pk = parsePrivateKey(privateKey);
        if (!(pk instanceof ECPrivateKey ec)) {
            throw new TicketGuardException("ticket-guard 私钥不是 EC 密钥: " + pk.getAlgorithm());
        }
        ECParameterSpec spec = ec.getParams();
        ECPoint w = multiply(spec.getGenerator(), ec.getS(), spec);
        try {
            AlgorithmParameters ap = AlgorithmParameters.getInstance("EC");
            ap.init(spec);
            ECParameterSpec named = ap.getParameterSpec(ECParameterSpec.class);
            PublicKey pub = KeyFactory.getInstance("EC")
                    .generatePublic(new ECPublicKeySpec(w, named));
            return Base64.getEncoder().encodeToString(pub.getEncoded());
        } catch (Exception e) {
            throw new TicketGuardException("ticket-guard 公钥构造失败", e);
        }
    }

    // ------------------------------------------------------------------ 私钥解析（m4445U，L567-603）

    /**
     * 对应 m4445U：trim 后不以 "-----BEGIN" 开头 → 去全部空白 + Base64 → PKCS8；
     * PEM：body 取 BEGIN/END 之间，"PRIVATE KEY" 直接按 PKCS8，"EC PRIVATE KEY"（SEC1）
     * 先包一层 PKCS8（手写 DER，见 wrapSec1InPkcs8）。原版 PEM 分支用第三方 PEM 解析器
     * （wh3/u52），支持加密键——离线无法取证其加密形态，这里不实现加密 PEM（遇到即抛）。
     */
    public static PrivateKey parsePrivateKey(String raw) {
        String s = raw.strip();
        try {
            if (!s.startsWith("-----BEGIN")) {
                String b64 = s.replaceAll("\\s+", "");
                return KeyFactory.getInstance("EC")
                        .generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(b64)));
            }
            int typeStart = s.indexOf("-----BEGIN") + "-----BEGIN".length();
            int typeEnd = s.indexOf("-----", typeStart);
            String type = s.substring(typeStart, typeEnd).trim();
            int bodyStart = typeEnd + "-----".length();
            int bodyEnd = s.indexOf("-----END", bodyStart);
            if (bodyEnd < 0) {
                throw new TicketGuardException("PEM 私钥缺少 END 行");
            }
            String body = s.substring(bodyStart, bodyEnd).replaceAll("\\s+", "");
            byte[] der = Base64.getDecoder().decode(body);
            if ("EC PRIVATE KEY".equals(type)) {
                der = wrapSec1InPkcs8(der);
            } else if (!"PRIVATE KEY".equals(type)) {
                throw new TicketGuardException("不支持的 PEM 私钥类型: " + type);
            }
            return KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (TicketGuardException e) {
            throw e;
        } catch (Exception e) {
            throw new TicketGuardException("ticket-guard 私钥解析失败（对应 m4445U）", e);
        }
    }

    /** SEC1 ECPrivateKey → PKCS8：SEQUENCE{INTEGER 0, SEQUENCE{OID id-ecPublicKey}, OCTET STRING sec1}。 */
    static byte[] wrapSec1InPkcs8(byte[] sec1) {
        byte[] version = {0x02, 0x01, 0x00};
        byte[] oid = {0x06, 0x07, 0x2A, (byte) 0x86, 0x48, (byte) 0xCE, 0x3D, 0x02, 0x01};
        byte[] algId = der(0x30, concat(oid));
        byte[] octet = der(0x04, concat(sec1));
        return der(0x30, concat(version, algId, octet));
    }

    private static byte[] der(int tag, byte[] content) {
        byte[] len;
        if (content.length < 0x80) {
            len = new byte[]{(byte) content.length};
        } else {
            int n = Integer.numberOfLeadingZeros(content.length) >> 3; // 有效字节数 1..4（长度不会超 int 范围高位）
            len = new byte[1 + n];
            len[0] = (byte) (0x80 | n);
            for (int i = 0; i < n; i++) {
                len[1 + i] = (byte) (content.length >>> (8 * (n - 1 - i)));
            }
        }
        byte[] out = new byte[1 + len.length + content.length];
        out[0] = (byte) tag;
        System.arraycopy(len, 0, out, 1, len.length);
        System.arraycopy(content, 0, out, 1 + len.length, content.length);
        return out;
    }

    private static byte[] concat(byte[]... parts) {
        int total = 0;
        for (byte[] p : parts) {
            total += p.length;
        }
        byte[] out = new byte[total];
        int off = 0;
        for (byte[] p : parts) {
            System.arraycopy(p, 0, out, off, p.length);
            off += p.length;
        }
        return out;
    }

    // ------------------------------------------------------------------ cookie 回退（m4454c0，L998-1025）

    /**
     * 对应 m4454c0：cookie 值先做 %2F→/ %2B→+ %3D→= 还原（原版 ignoreCase），
     * Base64 解码 → UTF-8 JSON → 取顶层字符串字段 "bd-ticket-guard-tree-public-key"。
     * 任何失败返回 ""（原版 optString/g54 语义），不抛异常。
     */
    public static String decodeClientDataCookie(String cookieValue) {
        if (Texts.isBlank(cookieValue)) {
            return "";
        }
        try {
            String unescaped = cookieValue
                    .replaceAll("(?i)%2F", "/")
                    .replaceAll("(?i)%2B", "+")
                    .replaceAll("(?i)%3D", "=");
            String json = new String(Base64.getDecoder().decode(unescaped), StandardCharsets.UTF_8);
            return extractJsonString(json, "bd-ticket-guard-tree-public-key");
        } catch (RuntimeException e) {
            return "";
        }
    }

    /** 极小 JSON 顶层字符串字段提取（对应 org.json JSONObject.optString 的本用例行为）。 */
    static String extractJsonString(String json, String key) {
        int k = json.indexOf("\"" + key + "\"");
        if (k < 0) {
            return "";
        }
        int colon = json.indexOf(':', k + key.length() + 2);
        if (colon < 0) {
            return "";
        }
        int open = json.indexOf('"', colon + 1);
        if (open < 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = open + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '\\' && i + 1 < json.length()) {
                char n = json.charAt(++i);
                switch (n) {
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case '/' -> sb.append('/');
                    case 'n' -> sb.append('\n');
                    case 't' -> sb.append('\t');
                    case 'r' -> sb.append('\r');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    default -> sb.append(n);
                }
            } else if (c == '"') {
                return sb.toString();
            } else {
                sb.append(c);
            }
        }
        return "";
    }

    // ------------------------------------------------------------------ 手写 EC 仿射点乘

    /** double-and-add 标量乘，W = k·G（短 Weierstrass，y² = x³ + ax + b over Fp）。 */
    static ECPoint multiply(ECPoint g, BigInteger k, ECParameterSpec spec) {
        BigInteger p = ((ECFieldFp) spec.getCurve().getField()).getP();
        BigInteger a = spec.getCurve().getA();
        BigInteger b = spec.getCurve().getB();
        if (k.signum() <= 0) {
            throw new TicketGuardException("ticket-guard 私钥标量非法（<=0）");
        }
        ECPoint r = ECPoint.POINT_INFINITY;
        for (int i = k.bitLength() - 1; i >= 0; i--) {
            r = twice(r, a, p);
            if (k.testBit(i)) {
                r = add(r, g, a, p);
            }
        }
        if (r.equals(ECPoint.POINT_INFINITY)) {
            throw new TicketGuardException("ticket-guard 公钥派生得到无穷远点");
        }
        // 曲线方程自检：y² - (x³ + ax + b) ≡ 0 (mod p)
        BigInteger x = r.getAffineX();
        BigInteger y = r.getAffineY();
        BigInteger lhs = y.multiply(y).mod(p);
        BigInteger rhs = x.multiply(x).multiply(x).add(a.multiply(x)).add(b).mod(p);
        if (!lhs.equals(rhs)) {
            throw new TicketGuardException("ticket-guard 派生点不在曲线上（自检失败）");
        }
        return r;
    }

    private static ECPoint twice(ECPoint p, BigInteger a, BigInteger mod) {
        if (p.equals(ECPoint.POINT_INFINITY)) {
            return ECPoint.POINT_INFINITY;
        }
        BigInteger x = p.getAffineX();
        BigInteger y = p.getAffineY();
        if (y.signum() == 0) {
            return ECPoint.POINT_INFINITY;
        }
        BigInteger num = x.multiply(x).multiply(BigInteger.valueOf(3)).add(a).mod(mod);
        BigInteger den = y.shiftLeft(1).mod(mod);
        BigInteger lambda = num.multiply(den.modInverse(mod)).mod(mod);
        BigInteger x3 = lambda.multiply(lambda).subtract(x.shiftLeft(1)).mod(mod);
        BigInteger y3 = lambda.multiply(x.subtract(x3)).subtract(y).mod(mod);
        return new ECPoint(x3, y3);
    }

    private static ECPoint add(ECPoint p, ECPoint q, BigInteger a, BigInteger mod) {
        if (p.equals(ECPoint.POINT_INFINITY)) {
            return q;
        }
        if (q.equals(ECPoint.POINT_INFINITY)) {
            return p;
        }
        BigInteger x1 = p.getAffineX();
        BigInteger y1 = p.getAffineY();
        BigInteger x2 = q.getAffineX();
        BigInteger y2 = q.getAffineY();
        if (x1.equals(x2)) {
            return y1.equals(y2) ? twice(p, a, mod) : ECPoint.POINT_INFINITY;
        }
        BigInteger lambda = y2.subtract(y1)
                .multiply(x2.subtract(x1).modInverse(mod)).mod(mod);
        BigInteger x3 = lambda.multiply(lambda).subtract(x1).subtract(x2).mod(mod);
        BigInteger y3 = lambda.multiply(x1.subtract(x3)).subtract(y1).mod(mod);
        return new ECPoint(x3, y3);
    }
}
