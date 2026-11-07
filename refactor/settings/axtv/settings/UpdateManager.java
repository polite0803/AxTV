/*
 * 来源: src/app/p000/lf5.java（m7411a 入口、m7412b 清理旧包、m7414d/m7415e/m7416f/m7418h 签名与摘要校验）
 *       + src/app/p000/ff5.java（检查更新、版本比较、选 apk 资源）
 *       + src/app/p000/nu1.java case 3（镜像回退下载、进度、SHA-256 校验）
 *       + src/app/p000/gf5.java / kf5.java（下载完成提示、导出、安装结果广播）
 *       + src/app/p000/cf5.java（UpdateInfo）
 * 原职责: 检查更新 → 下载 → 校验 → 安装
 * 重构说明: 合并成一个门面类；HTTP / 安装 / 签名读取抽成接口，核心层只用 JDK
 */
package axtv.settings;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 应用更新（原 Log TAG "UpdateManager"）。
 *
 * <p>流程（与原文一致）：
 * <ol>
 *   <li>按镜像顺序请求 {@value #RELEASE_API}（{@code Accept: application/vnd.github.v3+json}），
 *       第一个能解析出 JSON 的镜像胜出；</li>
 *   <li>比较 {@code tag_name} 与当前 versionName（去掉前缀 v/V，按 "." 分段转 int，缺段按 0）；</li>
 *   <li>有更新则取第一个 {@code *.apk} 资源，{@code digest} 去掉 "sha256:" 后长度必须为 64；</li>
 *   <li>下载到 {@code filesDir/updates/AxTV_update.apk}，逐镜像回退，进度按 Content-Length 归一；</li>
 *   <li>下载后先校验 SHA-256，再校验 APK 签名（证书公钥 SHA-256，多个签名用 "," 连接），
 *       通过后交给 {@link ApkInstaller}。</li>
 * </ol>
 *
 * <p>提示文案逐字保留（Toast 由 Android 侧在 {@link Listener#onStatus} 里弹）。
 */
public final class UpdateManager {

    private static final Logger LOG = Logger.getLogger("UpdateManager");

    /** Releases 最新版接口（原文指向上游 myDV，现指向本仓库 Release）。 */
    public static final String RELEASE_API = "https://api.github.com/repos/polite0803/AxTV/releases/latest";

    /** 下载目录名（相对 filesDir）。 */
    public static final String UPDATE_DIR = "updates";

    /** 下载文件名（原文固定，每次覆盖）。 */
    public static final String APK_FILE_NAME = "AxTV_update.apk";

    /** 下载请求的 UA（原文写死 Chrome 126）。 */
    private static final String DOWNLOAD_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36";

    private static final int BUFFER_SIZE = 8192;

    /** 提示文案（原文中文，一个字都不改）。 */
    public static final String MSG_CHECK_FAILED = "检查更新失败，请稍后重试";
    public static final String MSG_UP_TO_DATE = "当前已是最新版本";
    public static final String MSG_NO_APK = "未找到可用的更新文件";
    public static final String MSG_DOWNLOADING = "正在下载更新中，请稍候...";
    public static final String MSG_DOWNLOAD_DONE = "下载完成";
    public static final String MSG_EMPTY_FILE = "文件为空";
    public static final String MSG_DIGEST_FAILED = "校验失败: 无法计算文件摘要";
    public static final String MSG_DIGEST_MISSING = "更新包缺少 SHA-256 摘要，已中止安装";
    public static final String MSG_DIGEST_MISMATCH = "校验失败: SHA-256 不匹配";
    public static final String MSG_SIGNATURE_MISMATCH = "更新包签名与本机应用不一致";
    public static final String MSG_SIGNATURE_UNREADABLE = "无法读取更新包签名，已中止安装";
    public static final String MSG_PACKAGE_MISMATCH = "更新包包名与本机应用不一致，已中止安装";
    public static final String MSG_INSECURE_URL = "更新地址不是 HTTPS，已中止";
    public static final String MSG_INSTALLED = "更新包已安装";
    public static final String MSG_INSTALL_FAILED = "安装失败";

    private final File filesDir;
    private final AppInfo appInfo;
    private final UpdateMirror mirrors;
    private final ApkInspector apkInspector;
    private final ApkInstaller apkInstaller;
    private final ReleaseJson releaseJson;
    private final Listener listener;
    private final HttpFetcher http;
    private final AtomicBoolean downloading = new AtomicBoolean(false);

    public UpdateManager(File filesDir, AppInfo appInfo, UpdateMirror mirrors,
                         ApkInspector apkInspector, ApkInstaller apkInstaller,
                         ReleaseJson releaseJson, Listener listener) {
        this(filesDir, appInfo, mirrors, apkInspector, apkInstaller, releaseJson, listener,
                HttpFetcher.jdk());
    }

    /** 允许注入 HttpFetcher（单测 / Android OkHttp 实现用）。 */
    public UpdateManager(File filesDir, AppInfo appInfo, UpdateMirror mirrors,
                         ApkInspector apkInspector, ApkInstaller apkInstaller,
                         ReleaseJson releaseJson, Listener listener, HttpFetcher http) {
        this.filesDir = filesDir;
        this.appInfo = appInfo;
        this.mirrors = mirrors;
        this.apkInspector = apkInspector;
        this.apkInstaller = apkInstaller;
        this.releaseJson = releaseJson;
        this.listener = listener;
        this.http = http;
    }

    /** 是否正在下载（原 {@code lf5.f13111a}）。 */
    public boolean isDownloading() {
        return downloading.get();
    }

    // ════════════════════════════ 检查更新 ════════════════════════════

    /**
     * 检查更新（原 {@code lf5.m7411a} → {@code ff5}）。
     *
     * @param silent 静默检查（启动时自动检查）：不推送 {@link Listener#onStatus}
     *               与 {@link Listener#onUpdateInfo}，结果只通过返回值告知
     * @return 有更新时返回信息，否则 null
     */
    public ReleaseInfo checkForUpdate(boolean silent) {
        if (downloading.get()) {
            if (!silent) {
                listener.onStatus(Status.DOWNLOADING, MSG_DOWNLOADING);
            }
            return null;
        }
        String localVersion = appInfo.versionName();
        ReleaseJsonBody body = fetchLatestRelease();
        if (body == null) {
            if (!silent) {
                listener.onStatus(Status.CHECK_FAILED, MSG_CHECK_FAILED);
            }
            return null;
        }
        try {
            ReleaseInfo release = releaseJson.parse(body.json());
            if (release.downloadUrl() == null) {
                if (!silent) {
                    listener.onStatus(Status.NO_APK, MSG_NO_APK);
                }
                return null;
            }
            if (compareVersions(release.versionName(), localVersion) <= 0) {
                if (!silent) {
                    listener.onStatus(Status.UP_TO_DATE, MSG_UP_TO_DATE);
                }
                return null;
            }
            ReleaseInfo withSigner = new ReleaseInfo(release.versionName(), release.updateLog(),
                    release.downloadUrl(), release.sha256(), appInfo.signerSha256());
            if (!silent) {
                listener.onUpdateInfo(withSigner);
            }
            return withSigner;
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "解析 release 响应失败", e);
            if (!silent) {
                listener.onStatus(Status.CHECK_FAILED, MSG_CHECK_FAILED);
            }
            return null;
        }
    }

    /** 逐个镜像试，返回第一个成功响应体；全部失败返回 null。 */
    private ReleaseJsonBody fetchLatestRelease() {
        for (String url : UpdateMirror.candidates(mirrors.forCheck(), RELEASE_API)) {
            if (!isHttps(url)) {
                LOG.warning("跳过非 HTTPS 更新地址: " + url);
                continue;
            }
            try {
                HttpFetcher.TextResponse response =
                        http.getText(url, "application/vnd.github.v3+json", 10);
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    String body = response.body();
                    if (body != null && !body.isBlank()) {
                        return new ReleaseJsonBody(body);
                    }
                }
            } catch (IOException | IllegalArgumentException e) {
                LOG.log(Level.FINE, "镜像请求失败: " + url, e);
            }
        }
        return null;
    }

    // ════════════════════════════ 下载 ════════════════════════════

    /**
     * 下载并安装（原 {@code nu1} case 3 的下载循环 + {@code gf5} 的安装）。
     *
     * @return 安装是否已被成功唤起
     */
    public boolean downloadAndInstall(ReleaseInfo info) {
        if (!downloading.compareAndSet(false, true)) {
            listener.onStatus(Status.DOWNLOADING, MSG_DOWNLOADING);
            return false;
        }
        try {
            File apk = download(info);
            if (apk == null) {
                return false;
            }
            listener.onStatus(Status.DOWNLOAD_DONE, MSG_DOWNLOAD_DONE);
            boolean triggered = apkInstaller.install(apk);
            if (!triggered) {
                listener.onStatus(Status.INSTALL_FAILED, MSG_INSTALL_FAILED);
            } else {
                // 后续结果由安装器广播回报，见 installResultMessage(...)
                listener.onStatus(Status.INSTALL_TRIGGERED, null);
            }
            return triggered;
        } finally {
            downloading.set(false);
        }
    }

    /** 镜像回退下载；成功返回文件路径，失败返回 null 并已通过 listener 报告原因。 */
    private File download(ReleaseInfo info) {
        if (info.downloadUrl() == null || info.downloadUrl().isBlank()) {
            listener.onStatus(Status.DOWNLOAD_FAILED, MSG_NO_APK);
            return null;
        }
        File dir = new File(filesDir, UPDATE_DIR);
        File target = new File(dir, APK_FILE_NAME);
        try {
            // java.nio.file 在 API 26 才可用（minSdk 23）；mkdirs 失败仍走原 catch。
            if (!dir.isDirectory() && !dir.mkdirs()) {
                throw new IOException("无法创建更新目录: " + dir);
            }
        } catch (IOException e) {
            LOG.log(Level.WARNING, "创建更新目录失败", e);
            listener.onStatus(Status.DOWNLOAD_FAILED, "下载异常");
            return null;
        }
        List<String> prefixes = mirrors.forDownload();
        String lastError = null;
        for (String url : UpdateMirror.candidates(prefixes, info.downloadUrl())) {
            if (!isHttps(url)) {
                lastError = MSG_INSECURE_URL;
                continue;
            }
            listener.onProgress(0.0f);
            try {
                deleteIfExists(target);
                String failure = fetchToFile(url, target);
                if (failure != null) {
                    lastError = failure;
                    continue;
                }
                String verify = verify(target, info);
                if (verify != null) {
                    deleteIfExists(target);
                    lastError = verify;
                    continue;
                }
                listener.onProgress(-1.0f);
                return target;
            } catch (IOException e) {
                LOG.log(Level.FINE, "下载失败: " + url, e);
                lastError = e.getMessage() == null ? "下载异常" : e.getMessage();
            }
        }
        listener.onStatus(Status.DOWNLOAD_FAILED, lastError == null ? "下载异常" : lastError);
        return null;
    }
    /** 单次 HTTP 下载；成功返回 null，失败返回错误文案（与原文中文一致）。 */
    private String fetchToFile(String url, File target) throws IOException {
        HttpFetcher.StreamResponse response = http.getStream(url, DOWNLOAD_UA);
        int code = response.statusCode();
        if (code < 200 || code >= 300) {
            closeQuietly(response.stream());
            return "HTTP " + code;
        }
        InputStream in = response.stream();
        if (in == null) {
            return "响应为空";
        }
        long total = response.contentLength();
        long read = 0;
        byte[] buffer = new byte[BUFFER_SIZE];
        try (in; FileOutputStream out = new FileOutputStream(target)) {
            int n;
            while ((n = in.read(buffer)) != -1) {
                out.write(buffer, 0, n);
                read += n;
                if (total > 0) {
                    listener.onProgress((float) Math.min(1.0, (double) read / total));
                } else {
                    // 原文：没有 Content-Length 时进度按 MB 递增（+1）
                    listener.onProgress((read / 1024.0f / 1024.0f) + 1.0f);
                }
            }
        }
        if (!target.isFile() || target.length() <= 0) {
            return MSG_EMPTY_FILE;
        }
        return null;
    }

    /** SHA-256 + 签名校验，返回错误文案或 null（原 {@code nu1} 475-485 行 + {@code lf5.m7418h}）。 */
    private String verify(File apk, ReleaseInfo info) {
        // 摘要与签名都必须校验通过；任何一项读不到都中止，不能“读不到就放行”。
        String expected = info.sha256();
        if (expected == null || expected.isBlank()) {
            return MSG_DIGEST_MISSING;
        }
        String actual = sha256Hex(apk);
        if (actual == null) {
            return MSG_DIGEST_FAILED;
        }
        if (!actual.equalsIgnoreCase(expected)) {
            return MSG_DIGEST_MISMATCH;
        }

        String localSigner = info.signerSha256() != null ? info.signerSha256() : appInfo.signerSha256();
        if (localSigner == null || localSigner.isEmpty()) {
            return MSG_SIGNATURE_UNREADABLE;
        }
        String apkSigner = apkInspector.signerSha256(apk);
        if (apkSigner == null) {
            return MSG_SIGNATURE_UNREADABLE;
        }
        if (!apkSigner.equalsIgnoreCase(localSigner)) {
            return MSG_SIGNATURE_MISMATCH;
        }

        String localPackage = appInfo.packageName();
        String apkPackage = apkInspector.packageName(apk);
        if (localPackage == null || localPackage.isBlank()
                || apkPackage == null || !apkPackage.equals(localPackage)) {
            return MSG_PACKAGE_MISMATCH;
        }
        return null;
    }

    private static boolean isHttps(String url) {
        return url != null && url.regionMatches(true, 0, "https://", 0, 8);
    }

    /** 文件 SHA-256 小写十六进制（原 {@code lf5.m7415e}）。 */
    public static String sha256Hex(File file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = new FileInputStream(file)) {
                byte[] buffer = new byte[65536];
                int n;
                while ((n = in.read(buffer)) != -1) {
                    digest.update(buffer, 0, n);
                }
            }
            return toHex(digest.digest());
        } catch (IOException | NoSuchAlgorithmException e) {
            LOG.log(Level.WARNING, "计算文件摘要失败", e);
            return null;
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    private static void closeQuietly(InputStream in) {
        if (in == null) {
            return;
        }
        try {
            in.close();
        } catch (IOException ignored) {
            // 原文同样吞掉关闭异常
        }
    }

    // ════════════════════════════ 工具 ════════════════════════════

    /**
     * 版本比较（原 {@code ff5} 的逐段比较）：去掉 v/V 前缀，按 "." 分段，段内转 int
     * （非数字按 0），缺段按 0；返回 >0 表示 remote 更新。
     */
    public static int compareVersions(String remote, String local) {
        List<Integer> remoteParts = versionParts(remote);
        List<Integer> localParts = versionParts(local);
        int size = Math.max(remoteParts.size(), localParts.size());
        for (int i = 0; i < size; i++) {
            int r = i < remoteParts.size() ? remoteParts.get(i) : 0;
            int l = i < localParts.size() ? localParts.get(i) : 0;
            if (r != l) {
                return Integer.compare(r, l);
            }
        }
        return 0;
    }

    private static List<Integer> versionParts(String version) {
        String trimmed = version == null ? "" : version;
        if (trimmed.startsWith("V")) {
            trimmed = trimmed.substring(1);
        } else if (trimmed.startsWith("v")) {
            trimmed = trimmed.substring(1);
        }
        // 截断到首个非数字字符，避免“1.2.3-beta”导致 parseIntOrZero("beta")=0 导致比较错误
        int dotIndex = trimmed.indexOf('.');
        if (dotIndex > 0) {
            String firstSegment = trimmed.substring(0, dotIndex);
            if (!firstSegment.matches("\\d+")) {
                trimmed = firstSegment;
            }
        }
        List<Integer> parts = new ArrayList<>();
        for (String part : trimmed.split("\\.")) {
            parts.add(parseIntOrZero(part));
        }
        return parts;
    }

    private static int parseIntOrZero(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** 清理历史更新包（原 {@code lf5.m7412b}：删 {@code filesDir/updates} 下所有 .apk）。 */
    public static void cleanOldApks(File filesDir) {
        File dir = new File(filesDir, UPDATE_DIR);
        if (!dir.isDirectory()) {
            return;
        }
        File[] children = dir.listFiles();
        if (children == null) {
            LOG.warning("遍历更新目录失败: " + dir);
            return;
        }
        for (File child : children) {
            if (!child.getName().endsWith(".apk")) {
                continue;
            }
            try {
                deleteIfExists(child);
            } catch (IOException e) {
                LOG.log(Level.FINE, "删除旧更新包失败: " + child, e);
            }
        }
    }

    /** 把 APK 复制到外部目录（原 {@code gf5} case 2），失败返回 null。 */
    public static File exportForInstall(File apk, File externalFilesDir) {
        try {
            if (!externalFilesDir.isDirectory() && !externalFilesDir.mkdirs()) {
                throw new IOException("无法创建导出目录: " + externalFilesDir);
            }
            File dest = new File(externalFilesDir, apk.getName());
            // Files.copy(REPLACE_EXISTING) 的等价实现：FileOutputStream 默认截断覆盖。
            try (InputStream in = new FileInputStream(apk);
                 FileOutputStream out = new FileOutputStream(dest)) {
                byte[] buffer = new byte[BUFFER_SIZE];
                int n;
                while ((n = in.read(buffer)) != -1) {
                    out.write(buffer, 0, n);
                }
            }
            return dest;
        } catch (IOException e) {
            LOG.log(Level.WARNING, "导出更新包失败", e);
            return null;
        }
    }

    /** 等价于 {@code Files.deleteIfExists}：不存在时直接返回，删除失败抛 IOException。 */
    private static void deleteIfExists(File file) throws IOException {
        if (file.exists() && !file.delete()) {
            throw new IOException("无法删除: " + file);
        }
    }

    // ════════════════════════════ 类型 ════════════════════════════

    /** 更新信息（原 {@code cf5}）：versionName / updateLog / downloadUrl / sha256 / signerSha256。 */
    public record ReleaseInfo(String versionName, String updateLog, String downloadUrl,
                              String sha256, String signerSha256) {
    }

    /** 通知回调：状态文案（Toast）、进度、有更新时的信息。 */
    public interface Listener {
        /** 进度：0..1 为百分比，-1 表示「结束/复位」（原 {@code fw4<Float>} 的 -1.0f）。 */
        default void onProgress(float progress) {
        }

        default void onStatus(Status status, String message) {
        }

        default void onUpdateInfo(ReleaseInfo info) {
        }
    }

    /** 状态（与原 Toast 一一对应）。 */
    public enum Status {
        CHECK_FAILED,
        UP_TO_DATE,
        NO_APK,
        DOWNLOADING,
        DOWNLOAD_DONE,
        DOWNLOAD_FAILED,
        /** 已唤起系统安装器，结果待广播回报。 */
        INSTALL_TRIGGERED,
        INSTALL_FAILED
    }

    /**
     * 安装结果文案（原 {@code kf5.onReceive}）：状态 0 → "更新包已安装"，
     * 其它非 -1 → "安装失败[: 原因]"；-1 表示需要用户确认，由 Android 侧拉起 EXTRA_INTENT。
     */
    public static String installResultMessage(int statusCode, String statusMessage) {
        if (statusCode == 0) {
            return MSG_INSTALLED;
        }
        if (statusCode == -1) {
            return null;
        }
        return MSG_INSTALL_FAILED
                + (statusMessage == null || statusMessage.isBlank() ? "" : ": " + statusMessage);
    }

    /** 本机应用信息（原用 PackageManager 现场取）。 */
    public interface AppInfo {
        /** versionName，取不到返回空串。 */
        String versionName();

        /** 本机签名指纹：证书公钥 SHA-256 小写十六进制，多个签名用 "," 连接；取不到返回 null。 */
        String signerSha256();

        /** 本机包名，用于拒绝安装包名不一致的更新包。 */
        String packageName();
    }

    /** APK 签名读取（原 {@code lf5.m7418h} 里 {@code getPackageArchiveInfo} 那一段）。 */
    public interface ApkInspector {
        /** 返回 APK 的签名指纹，读不到返回 null。 */
        String signerSha256(File apk);

        /** 返回 APK 的包名，读不到返回 null。 */
        String packageName(File apk);
    }

    /** 安装（原 {@code gf5} 的两种落地方式）。 */
    public interface ApkInstaller {
        /**
         * 唤起安装：导出到外部目录 + {@code ACTION_VIEW(application/vnd.android.package-archive)}，
         * 见 NOTES.md「待迁移」。
         */
        boolean install(File apk);
    }

    private record ReleaseJsonBody(String json) {
    }
}
