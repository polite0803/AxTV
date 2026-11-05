/*
 * 来源: src/app/p000/n11.java m8001p0 / m8030V 中的 android.os.Build 读取
 * 原职责: 拼移动端 User-Agent "com.ss.android.ugc.aweme/391000 (Linux; U; Android {os}; zh_CN; {model})"
 *       以及移动端推荐流的 device_type / os_version / os_api 参数
 * 重构说明: Build.MODEL / VERSION.RELEASE / SDK_INT 属 Android 依赖，改由调用方注入。
 *           机型名的 ASCII 过滤逻辑保留；原实现用 android.icu Transliterator 做音译，
 *           核心层用 java.text.Normalizer 近似（见 NOTES.md「存疑」）。
 */
package axtv.api;

import java.text.Normalizer;

public final class DeviceProfile {

    private static final String DEFAULT_MODEL = "Pixel 10 Pro";
    private static final String DEFAULT_OS_VERSION = "16";

    private final String osVersion;
    private final String model;
    private final int sdkInt;

    public DeviceProfile(String osVersion, String model, int sdkInt) {
        this.osVersion = Texts.isBlank(osVersion) ? DEFAULT_OS_VERSION : osVersion.trim();
        this.model = Texts.isBlank(model) ? DEFAULT_MODEL : model.trim();
        this.sdkInt = sdkInt;
    }

    /** 移动端 User-Agent。对应 m8001p0()。 */
    public String mobileUserAgent() {
        return "com.ss.android.ugc.aweme/391000 (Linux; U; Android " + osVersion + "; zh_CN; " + safeModel() + ")";
    }

    /** device_type 参数值。对应 m8030V 中的 Build.MODEL 处理。 */
    public String deviceType() {
        return safeModel();
    }

    /** os_version 参数值。 */
    public String osVersion() {
        return osVersion;
    }

    /** os_api 参数值。 */
    public int sdkInt() {
        return sdkInt;
    }

    /**
     * 机型名清洗：仅保留可打印 ASCII。
     * 原实现：含非 ASCII 时尝试 Transliterator("Any-Latin; Latin-ASCII")，
     * 失败或结果仍非 ASCII 则回退 "Pixel 10 Pro"。
     */
    private String safeModel() {
        if (isPrintableAscii(model)) {
            return model;
        }
        String normalized = Normalizer.normalize(model, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        if (isPrintableAscii(normalized) && !normalized.isEmpty()) {
            return normalized;
        }
        return DEFAULT_MODEL;
    }

    private static boolean isPrintableAscii(String s) {
        if (s == null || s.isEmpty()) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < ' ' || c >= 127) {
                return false;
            }
        }
        return true;
    }
}
