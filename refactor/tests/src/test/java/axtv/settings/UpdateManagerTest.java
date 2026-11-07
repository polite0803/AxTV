package axtv.settings;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 更新校验链：检查更新的镜像回退/版本比较 + 下载后 SHA-256/签名/包名三道校验的拒绝路径。
 * 全部依赖注入假实现，不走真实网络。
 */
class UpdateManagerTest {

    private static final String LOCAL_SIGNER = "a".repeat(64);
    private static final String LOCAL_PACKAGE = "com.github.axtv.dv";
    private static final byte[] APK_BYTES = "apk-bytes-0123456789".getBytes(StandardCharsets.UTF_8);
    private static final String DOWNLOAD_URL = "https://dl.example.com/AxTV.apk";

    @TempDir
    File tempDir;

    final FakeHttpFetcher http = new FakeHttpFetcher();
    final FakeInstaller installer = new FakeInstaller();
    final RecordingListener listener = new RecordingListener();
    FakeInspector inspector = new FakeInspector(LOCAL_SIGNER, LOCAL_PACKAGE);

    private UpdateManager manager(String localVersion) {
        return new UpdateManager(tempDir, appInfo(localVersion), new UpdateMirror(),
                inspector, installer, new SimpleReleaseJson(), listener, http);
    }

    private UpdateManager.AppInfo appInfo(String localVersion) {
        return new UpdateManager.AppInfo() {
            @Override public String versionName() { return localVersion; }
            @Override public String signerSha256() { return LOCAL_SIGNER; }
            @Override public String packageName() { return LOCAL_PACKAGE; }
        };
    }

    private UpdateManager.ReleaseInfo validInfo() {
        return new UpdateManager.ReleaseInfo(
                "1.1.0", "log", DOWNLOAD_URL, sha256Of(APK_BYTES), LOCAL_SIGNER);
    }

    private File apkTarget() {
        return new File(new File(tempDir, UpdateManager.UPDATE_DIR), UpdateManager.APK_FILE_NAME);
    }

    private String lastMessage() {
        List<String> messages = listener.messages;
        return messages.isEmpty() ? null : messages.get(messages.size() - 1);
    }

    // ───────────────────────── 版本比较 ─────────────────────────

    @Test
    void compareVersionsOrdersNumerically() {
        assertTrue(UpdateManager.compareVersions("1.2.3", "1.2.2") > 0);
        assertEquals(0, UpdateManager.compareVersions("1.2.3", "1.2.3"));
        assertTrue(UpdateManager.compareVersions("1.2.2", "1.2.3") < 0);
        assertTrue(UpdateManager.compareVersions("0.9.10", "0.9.9") > 0);
    }

    @Test
    void compareVersionsStripsVPrefixAndPadsMissingSegments() {
        assertTrue(UpdateManager.compareVersions("v1.1", "1.0.0") > 0);
        assertEquals(0, UpdateManager.compareVersions("V1.2", "1.2.0"));
        assertTrue(UpdateManager.compareVersions("1.2", "1.2.1") < 0);
    }

    @Test
    void compareVersionsNonNumericSegmentsCountAsZero() {
        // 「1.2.3-beta」第三段非数字按 0，与文档行为一致
        assertTrue(UpdateManager.compareVersions("1.2.3", "1.2.3-beta") > 0);
        assertEquals(0, UpdateManager.compareVersions("abc", "0"));
    }

    // ───────────────────────── SHA-256 ─────────────────────────

    @Test
    void sha256HexMatchesKnownVectors() throws IOException {
        File empty = newFile("empty.bin", new byte[0]);
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                UpdateManager.sha256Hex(empty));
        File abc = newFile("abc.bin", "abc".getBytes(StandardCharsets.UTF_8));
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                UpdateManager.sha256Hex(abc));
    }

    // ───────────────────────── 检查更新 ─────────────────────────

    @Test
    void checkFallsBackToSecondMirrorWhenFirstFails() {
        http.onText("gh-proxy.org", 500, "");
        http.onText("gh-proxy.com", 200, releaseJson("v1.1.0", DOWNLOAD_URL, "sha256:" + "b".repeat(64)));
        UpdateManager manager = manager("1.0.0");

        UpdateManager.ReleaseInfo info = manager.checkForUpdate(false);

        assertNotNull(info);
        // versionName 保留原始 tag（v 前缀只在比较时剥掉）
        assertEquals("v1.1.0", info.versionName());
        assertEquals(DOWNLOAD_URL, info.downloadUrl());
        assertTrue(http.textUrls.get(0).startsWith("https://gh-proxy.org/"));
        assertTrue(http.textUrls.get(1).startsWith("https://gh-proxy.com/"));
    }

    @Test
    void checkReportsUpToDateWhenLocalVersionMatches() {
        http.onText("api.github.com", 200, releaseJson("v1.2.3", DOWNLOAD_URL, "sha256:" + "b".repeat(64)));
        UpdateManager manager = manager("1.2.3");

        assertNull(manager.checkForUpdate(false));

        assertTrue(listener.statuses.contains(UpdateManager.Status.UP_TO_DATE));
        assertTrue(listener.infos.isEmpty());
    }

    @Test
    void checkParsesDigestAndNormalizesCase() {
        String upperHex = "ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789";
        http.onText("api.github.com", 200, releaseJson("v1.1.0", DOWNLOAD_URL, "sha256:" + upperHex));
        UpdateManager manager = manager("1.0.0");

        UpdateManager.ReleaseInfo info = manager.checkForUpdate(false);

        assertNotNull(info);
        assertEquals(upperHex.toLowerCase(Locale.ROOT), info.sha256());
    }

    @Test
    void checkReportsNoApkWhenNoAssetMatches() {
        http.onText("api.github.com", 200,
                "{\"tag_name\":\"v1.1.0\",\"body\":\"\",\"assets\":[{\"name\":\"readme.txt\"}]}");
        UpdateManager manager = manager("1.0.0");

        assertNull(manager.checkForUpdate(false));
        assertTrue(listener.statuses.contains(UpdateManager.Status.NO_APK));
    }

    @Test
    void checkReportsFailureOnInvalidJson() {
        http.onText("api.github.com", 200, "not-json");
        UpdateManager manager = manager("1.0.0");

        assertNull(manager.checkForUpdate(false));
        assertTrue(listener.statuses.contains(UpdateManager.Status.CHECK_FAILED));
    }

    @Test
    void silentCheckSuppressesAllNotificationsAndReturnsInfo() {
        http.onText("api.github.com", 200, releaseJson("v1.1.0", DOWNLOAD_URL, "sha256:" + "b".repeat(64)));
        UpdateManager manager = manager("1.0.0");

        UpdateManager.ReleaseInfo info = manager.checkForUpdate(true);

        // 静默模式只通过返回值告知，不推 onStatus 也不推 onUpdateInfo（与实现一致）
        assertNotNull(info);
        assertTrue(listener.statuses.isEmpty());
        assertTrue(listener.infos.isEmpty());
    }

    // ───────────────────────── 下载与校验链 ─────────────────────────

    @Test
    void downloadNeverHitsPlainHttpTargetDirectly() {
        // 全部候选都失败；循环走完后 lastError 应是最后一条裸 http 候选的文案
        http.onStream("dl.example.com", 500, APK_BYTES);
        UpdateManager manager = manager("1.0.0");
        UpdateManager.ReleaseInfo info = new UpdateManager.ReleaseInfo(
                "1.1.0", "log", "http://dl.example.com/AxTV.apk", "b".repeat(64), LOCAL_SIGNER);

        boolean triggered = manager.downloadAndInstall(info);

        assertFalse(triggered);
        // 加了镜像前缀的 https 候选可以发，裸 http 目标绝不能直连
        for (String url : http.streamUrls) {
            assertFalse(url.startsWith("http://"), url);
        }
        assertTrue(listener.statuses.contains(UpdateManager.Status.DOWNLOAD_FAILED));
        assertEquals(UpdateManager.MSG_INSECURE_URL, lastMessage());
    }

    @Test
    void installAbortsWhenDigestMissing() {
        http.onStream("dl.example.com", 200, APK_BYTES);
        http.onText("api.github.com", 200, releaseJson("v1.1.0", DOWNLOAD_URL, null));
        UpdateManager manager = manager("1.0.0");
        UpdateManager.ReleaseInfo info = manager.checkForUpdate(false);
        assertNotNull(info);
        assertNull(info.sha256());

        boolean triggered = manager.downloadAndInstall(info);

        assertFalse(triggered);
        assertFalse(installer.invoked);
        assertEquals(UpdateManager.MSG_DIGEST_MISSING, lastMessage());
        assertFalse(apkTarget().exists());
    }

    @Test
    void installAbortsWhenDigestMismatch() {
        http.onStream("dl.example.com", 200, APK_BYTES);
        String wrongDigest = sha256Of("other-bytes".getBytes(StandardCharsets.UTF_8));
        UpdateManager manager = manager("1.0.0");
        UpdateManager.ReleaseInfo info = new UpdateManager.ReleaseInfo(
                "1.1.0", "log", DOWNLOAD_URL, wrongDigest, LOCAL_SIGNER);

        boolean triggered = manager.downloadAndInstall(info);

        assertFalse(triggered);
        assertFalse(installer.invoked);
        assertEquals(UpdateManager.MSG_DIGEST_MISMATCH, lastMessage());
        assertFalse(apkTarget().exists());
    }

    @Test
    void installAbortsWhenSignatureUnreadable() {
        http.onStream("dl.example.com", 200, APK_BYTES);
        inspector = new FakeInspector(null, LOCAL_PACKAGE);
        UpdateManager manager = manager("1.0.0");

        boolean triggered = manager.downloadAndInstall(validInfo());

        assertFalse(triggered);
        assertFalse(installer.invoked);
        assertEquals(UpdateManager.MSG_SIGNATURE_UNREADABLE, lastMessage());
    }

    @Test
    void installAbortsWhenSignatureMismatch() {
        http.onStream("dl.example.com", 200, APK_BYTES);
        inspector = new FakeInspector("f".repeat(64), LOCAL_PACKAGE);
        UpdateManager manager = manager("1.0.0");

        boolean triggered = manager.downloadAndInstall(validInfo());

        assertFalse(triggered);
        assertFalse(installer.invoked);
        assertEquals(UpdateManager.MSG_SIGNATURE_MISMATCH, lastMessage());
    }

    @Test
    void installAbortsWhenPackageMismatch() {
        http.onStream("dl.example.com", 200, APK_BYTES);
        inspector = new FakeInspector(LOCAL_SIGNER, "com.other.app");
        UpdateManager manager = manager("1.0.0");

        boolean triggered = manager.downloadAndInstall(validInfo());

        assertFalse(triggered);
        assertFalse(installer.invoked);
        assertEquals(UpdateManager.MSG_PACKAGE_MISMATCH, lastMessage());
    }

    @Test
    void installTriggersWhenAllChecksPass() throws IOException {
        http.onStream("dl.example.com", 200, APK_BYTES);
        UpdateManager manager = manager("1.0.0");

        boolean triggered = manager.downloadAndInstall(validInfo());

        assertTrue(triggered);
        assertTrue(installer.invoked);
        assertNotNull(installer.installed);
        assertTrue(Arrays.equals(APK_BYTES, Files.readAllBytes(installer.installed.toPath())));
        assertTrue(listener.statuses.contains(UpdateManager.Status.DOWNLOAD_DONE));
        assertTrue(listener.statuses.contains(UpdateManager.Status.INSTALL_TRIGGERED));
    }

    @Test
    void downloadFallsBackToSecondMirror() {
        http.onStream("gh-proxy.org", 500, APK_BYTES);
        http.onStream("gh-proxy.com", 200, APK_BYTES);
        UpdateManager manager = manager("1.0.0");

        boolean triggered = manager.downloadAndInstall(validInfo());

        assertTrue(triggered);
        assertTrue(installer.invoked);
        assertTrue(http.streamUrls.get(0).startsWith("https://gh-proxy.org/"));
        assertTrue(http.streamUrls.get(1).startsWith("https://gh-proxy.com/"));
    }

    // ───────────────────────── 夹具 ─────────────────────────

    private static String releaseJson(String tag, String url, String digest) {
        String digestField = digest == null ? "" : ",\"digest\":\"" + digest + "\"";
        return "{\"tag_name\":\"" + tag + "\",\"body\":\"log\",\"assets\":[{"
                + "\"name\":\"AxTV.apk\",\"browser_download_url\":\"" + url + "\"" + digestField + "}]}";
    }

    private static String sha256Of(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(data);
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private File newFile(String name, byte[] content) throws IOException {
        File file = new File(tempDir, name);
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(content);
        }
        return file;
    }

    /** 记录请求 URL 的假 HTTP；按「URL 包含」匹配返回预设响应。流每次现造，可被多次下载。 */
    private static final class FakeHttpFetcher implements HttpFetcher {
        final List<String> textUrls = new ArrayList<>();
        final List<String> streamUrls = new ArrayList<>();
        private final Map<String, TextResponse> textByContains = new LinkedHashMap<>();
        private final Map<String, Preset> streamByContains = new LinkedHashMap<>();

        private record Preset(int code, byte[] bytes) {
        }

        void onText(String urlContains, int code, String body) {
            textByContains.put(urlContains, new TextResponse(code, body, body.length()));
        }

        void onStream(String urlContains, int code, byte[] bytes) {
            streamByContains.put(urlContains, new Preset(code, bytes));
        }

        @Override
        public TextResponse getText(String url, String acceptHeader, int timeoutSeconds) {
            textUrls.add(url);
            for (Map.Entry<String, TextResponse> e : textByContains.entrySet()) {
                if (url.contains(e.getKey())) {
                    return e.getValue();
                }
            }
            return new TextResponse(500, "", 0);
        }

        @Override
        public StreamResponse getStream(String url, String userAgent) {
            streamUrls.add(url);
            for (Map.Entry<String, Preset> e : streamByContains.entrySet()) {
                if (url.contains(e.getKey())) {
                    Preset preset = e.getValue();
                    // 每次调用新建流：镜像回退会对同一 URL 反复下载
                    return new StreamResponse(preset.code(),
                            preset.code() >= 200 && preset.code() < 300
                                    ? new ByteArrayInputStream(preset.bytes()) : null,
                            preset.bytes().length);
                }
            }
            return new StreamResponse(500, null, 0);
        }
    }

    private static final class FakeInspector implements UpdateManager.ApkInspector {
        private final String signer;
        private final String packageName;

        FakeInspector(String signer, String packageName) {
            this.signer = signer;
            this.packageName = packageName;
        }

        @Override public String signerSha256(File apk) { return signer; }

        @Override public String packageName(File apk) { return packageName; }
    }

    private static final class FakeInstaller implements UpdateManager.ApkInstaller {
        File installed;
        boolean invoked;

        @Override
        public boolean install(File apk) {
            this.invoked = true;
            this.installed = apk;
            return true;
        }
    }

    private static final class RecordingListener implements UpdateManager.Listener {
        final List<UpdateManager.Status> statuses = new ArrayList<>();
        final List<String> messages = new ArrayList<>();
        final List<UpdateManager.ReleaseInfo> infos = new ArrayList<>();

        @Override
        public void onStatus(UpdateManager.Status status, String message) {
            statuses.add(status);
            messages.add(message);
        }

        @Override
        public void onUpdateInfo(UpdateManager.ReleaseInfo info) {
            infos.add(info);
        }
    }
}
