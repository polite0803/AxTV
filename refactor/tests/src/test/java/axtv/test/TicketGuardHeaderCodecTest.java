package axtv.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Base64;
import java.util.Map;

import org.junit.jupiter.api.Test;

import axtv.api.TicketGuardCredentials;
import axtv.api.TicketGuardException;
import axtv.api.TicketGuardHeaderCodec;
import axtv.api.TicketGuardUnsupportedException;

/**
 * 证据: src/app/p000/AbstractC0656g4.java m4464l L1149-1207 / m4475z L1366-1385 /
 *      m4445U L567-603 / m4454c0 L998-1025；src/app/p000/n11.java m8037c L2908-2934。
 *
 * 期望 Base64 由独立工具（PowerShell [Convert]::ToBase64String）计算后硬编码，
 * 不是由被测实现生成——避免"自己验自己"。
 */
class TicketGuardHeaderCodecTest {

    private static final String FIXED_JSON =
            "{\"ts_sign\":\"ts-X\",\"req_content\":\"ticket,path,timestamp\","
                    + "\"req_sign\":\"SIG\",\"timestamp\":1700000000,\"t_trust\":1}";
    private static final String FIXED_BASE64 =
            "eyJ0c19zaWduIjoidHMtWCIsInJlcV9jb250ZW50IjoidGlja2V0LHBhdGgsdGltZXN0YW1wIiwicmVx"
                    + "X3NpZ24iOiJTSUciLCJ0aW1lc3RhbXAiOjE3MDAwMDAwMDAsInRfdHJ1c3QiOjF9";

    @Test
    void encodeClientDataMatchesHandComputedBase64() {
        assertEquals(FIXED_BASE64, TicketGuardHeaderCodec.encodeClientData("ts-X", 1700000000L, "SIG"));
        String decoded = new String(Base64.getDecoder().decode(FIXED_BASE64), StandardCharsets.UTF_8);
        assertEquals(FIXED_JSON, decoded);
    }

    @Test
    void escapeJsonFollowsM4475z() {
        assertEquals("a\\\"b\\\\c\\td\\ne", TicketGuardHeaderCodec.escapeJson("a\"b\\c\td\ne"));
    }

    @Test
    void clientDataSignatureVerifiesWithPublicKey() throws Exception {
        KeyPair keyPair = ecKeyPair();
        String privateKey = Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded());
        TicketGuardCredentials credentials =
                new TicketGuardCredentials("raw", "TICKET", "TSIGN", privateKey, "");
        String path = "/aweme/v1/web/aweme/detail/";
        long timestamp = 1700000000L;

        String clientData = TicketGuardHeaderCodec.clientData(path, credentials, timestamp);
        String json = new String(Base64.getDecoder().decode(clientData), StandardCharsets.UTF_8);
        assertTrue(json.startsWith("{\"ts_sign\":\"TSIGN\""), json);
        assertTrue(json.contains("\"req_content\":\"ticket,path,timestamp\""), json);
        assertTrue(json.contains("\"timestamp\":1700000000,\"t_trust\":1}"), json);

        String reqSign = json.split("\"req_sign\":\"")[1].split("\"")[0];
        Signature verifier = Signature.getInstance("SHA256withECDSA");
        verifier.initVerify(keyPair.getPublic());
        verifier.update(TicketGuardHeaderCodec.toSignString(path, "TICKET", timestamp)
                .getBytes(StandardCharsets.UTF_8));
        assertTrue(verifier.verify(Base64.getDecoder().decode(reqSign)),
                "ECDSA 签名必须能被对应公钥验证");
    }

    @Test
    void deriveReePublicKeyEqualsJdkPublicEncoding() throws Exception {
        KeyPair keyPair = ecKeyPair();
        String privateKey = Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded());
        String expected = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
        String actual = TicketGuardHeaderCodec.deriveReePublicKey(privateKey);
        assertEquals(expected, actual, "手写 EC 点乘派生结果必须等于 JDK 导出的 X.509 公钥");
    }

    @Test
    void buildHeadersReturnsFiveRealHeaders() throws Exception {
        KeyPair keyPair = ecKeyPair();
        String privateKey = Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded());
        TicketGuardCredentials credentials =
                new TicketGuardCredentials("raw", "TICKET", "TSIGN", privateKey, "");
        Map<String, String> headers = TicketGuardHeaderCodec.buildHeaders("/aweme/v1/web/feed/", credentials, null);

        assertEquals(5, headers.size());
        assertEquals("2", headers.get("bd-ticket-guard-version"));
        assertEquals("1", headers.get("bd-ticket-guard-web-sign-type"));
        assertEquals("2", headers.get("bd-ticket-guard-web-version"));
        assertFalse(headers.get("bd-ticket-guard-client-data").isEmpty());
        assertEquals(Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded()),
                headers.get("bd-ticket-guard-ree-public-key"));
    }

    @Test
    void unusableCredentialsReturnEmptyMap() {
        assertTrue(TicketGuardHeaderCodec.buildHeaders("/x", TicketGuardCredentials.empty(), null).isEmpty());
        assertTrue(TicketGuardHeaderCodec.buildHeaders("/x", null, null).isEmpty());
    }

    @Test
    void serverCertBranchIsExplicitlyUnsupported() throws Exception {
        KeyPair keyPair = ecKeyPair();
        String privateKey = Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded());
        TicketGuardCredentials credentials =
                new TicketGuardCredentials("raw", "TICKET", "TSIGN", privateKey, "SERVER_CERT");
        TicketGuardUnsupportedException e = assertThrows(TicketGuardUnsupportedException.class,
                () -> TicketGuardHeaderCodec.buildHeaders("/x", credentials, null));
        assertTrue(e.getMessage().contains("serverCert"));
    }

    @Test
    void cookieFallbackDecodesM4454c0() throws Exception {
        String cookieJson = "{\"bd-ticket-guard-tree-public-key\":\"COOKIE_KEY\"}";
        String cookie = Base64.getEncoder().encodeToString(
                cookieJson.getBytes(StandardCharsets.UTF_8)).replace("+", "%2B").replace("/", "%2F");
        assertEquals("COOKIE_KEY", TicketGuardHeaderCodec.decodeClientDataCookie(cookie));
        assertEquals("COOKIE_KEY", TicketGuardHeaderCodec.derivePublicKeyOrFallback("not-a-key", cookie));
    }

    @Test
    void deriveFailureWithoutCookieRaisesInsteadOfSilentEmpty() {
        TicketGuardException e = assertThrows(TicketGuardException.class,
                () -> TicketGuardHeaderCodec.derivePublicKeyOrFallback("not-a-key", null));
        assertTrue(e.getMessage().contains("ree-public-key"));
    }

    @Test
    void pemPkcs8PrivateKeyIsAccepted() throws Exception {
        KeyPair keyPair = ecKeyPair();
        String body = Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded());
        String pem = "-----BEGIN PRIVATE KEY-----\n" + body + "\n-----END PRIVATE KEY-----";
        TicketGuardCredentials credentials = new TicketGuardCredentials("raw", "TICKET", "TSIGN", pem, "");
        Map<String, String> headers = TicketGuardHeaderCodec.buildHeaders("/x", credentials, null);
        assertEquals(5, headers.size());
        assertNull(headers.get("bd-ticket-guard-client-data").isEmpty() ? "empty" : null);
    }

    private static KeyPair ecKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(256);
        return generator.generateKeyPair();
    }
}
