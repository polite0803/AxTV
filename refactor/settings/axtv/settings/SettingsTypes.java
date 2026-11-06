/*
 * Settings 的值类型（从 Settings.java 拆出）。
 * 拆分原因: Settings.java 超 800 行；这些类型是纯数据定义，与读写逻辑无耦合。
 *           原引用 SettingsTypes.Account / SettingsTypes.VideoResolution 等一律改为 SettingsTypes.Xxx。
 */
package axtv.settings;

import java.util.LinkedHashMap;

/**
 * 配置中心的值类型容器：设备能力、账号、互动凭据与全部设置枚举。
 * 原文均为 {@code Settings} 的嵌套类型，字段与解析逻辑原样保留。
 */
public final class SettingsTypes {

    private SettingsTypes() {
    }

    /** null → ""（原 {@code Settings.nz}，值类型规范化共用）。 */
    static String nz(String value) {
        return value == null ? "" : value;
    }


    /** 设备能力（原 {@code sp0.f20095a} 的 TV 判定 + {@code Build.VERSION.SDK_INT}）。 */
    public interface DeviceProfile {
        int sdkInt();

        /** UI_MODE_TYPE_TELEVISION(4) 或 PackageManager.FEATURE_LEANBACK。 */
        boolean isTelevision();

        /**
         * 非 Android 环境的兜底（API 33、非电视）。Android 侧**必须**传入真实值，否则
         * {@link #colorSource()} 的 DEVICE 迁移与 {@link #colorContrast()} 的 TV 默认值会不准。
         */
        static DeviceProfile current() {
            return of(33, false);
        }

        static DeviceProfile of(int sdkInt, boolean television) {
            return new DeviceProfile() {
                @Override
                public int sdkInt() {
                    return sdkInt;
                }

                @Override
                public boolean isTelevision() {
                    return television;
                }
            };
        }
    }

    /** 账号（原 {@code dl4}）。字段顺序与 {@code accounts_list} 序列化一致。 */
    public record Account(String id, String cookie, String securityPayload, String ticket,
                          String tsSign, String privateKey, String pinHash, String pinSalt,
                          String userId, String serverCert) {

        public Account {
            id = id == null || id.isBlank() ? "main" : id;
            cookie = nz(cookie);
            securityPayload = nz(securityPayload);
            ticket = nz(ticket);
            tsSign = nz(tsSign);
            privateKey = nz(privateKey);
            pinHash = nz(pinHash);
            pinSalt = nz(pinSalt);
            userId = nz(userId);
            serverCert = nz(serverCert);
        }

        public Account withId(String newId) {
            return new Account(newId, cookie, securityPayload, ticket, tsSign, privateKey,
                    pinHash, pinSalt, userId, serverCert);
        }

        public Account withUserId(String newUserId) {
            return new Account(id, cookie, securityPayload, ticket, tsSign, privateKey,
                    pinHash, pinSalt, newUserId, serverCert);
        }

        public Account withPin(String newPinHash, String newPinSalt) {
            return new Account(id, cookie, securityPayload, ticket, tsSign, privateKey,
                    newPinHash, newPinSalt, userId, serverCert);
        }

        /** 是否设置了 PIN（pinHash 与 pinSalt 都非空，原 {@code m2972b}）。 */
        public boolean hasPin() {
            return !pinHash.isBlank() && !pinSalt.isBlank();
        }

        /** 账号 uid：Cookie 里的 uid_tt_ss 优先，其次 uid_tt（原 {@code m2973c}）。 */
        public String uidFromCookie() {
            LinkedHashMap<String, String> map = new LinkedHashMap<>();
            for (String part : cookie.split(";")) {
                String[] kv = part.trim().split("=", 2);
                if (kv.length == 2) {
                    map.put(kv[0].trim(), kv[1].trim());
                }
            }
            String uid = map.get("uid_tt_ss");
            if (uid != null) {
                return uid;
            }
            String fallback = map.get("uid_tt");
            return fallback == null ? "" : fallback;
        }

        /** PIN 摘要 = sha256(pinSalt + ":" + pin) 的小写十六进制（原 {@code fl4.m4181t}）。 */
        public static String hashPin(String pin, String salt) {
            try {
                java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
                byte[] bytes = digest.digest((salt + ":" + pin).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder(64);
                for (byte b : bytes) {
                    sb.append(String.format("%02x", b));
                }
                return sb.toString();
            } catch (java.security.NoSuchAlgorithmException e) {
                throw new IllegalStateException("SHA-256 unavailable", e);
            }
        }
    }

    /** 互动安全凭据（原 {@code d32}）：ticket/tsSign/privateKey 三者都非空才可用。 */
    public record Interaction(String rawPayload, String ticket, String tsSign,
                              String privateKey, String serverCert) {

        public Interaction {
            rawPayload = nz(rawPayload);
            ticket = nz(ticket);
            tsSign = nz(tsSign);
            privateKey = nz(privateKey);
            serverCert = nz(serverCert);
        }

        public boolean isComplete() {
            return !ticket.isBlank() && !tsSign.isBlank() && !privateKey.isBlank();
        }
    }

    /** 界面形态（原 {@code nd5}）。 */
    public enum UiFormFactor {
        AUTO("auto"), PHONE_PORTRAIT("phone_portrait"),
        TABLET_LANDSCAPE("tablet_landscape"), TV_LANDSCAPE("tv_landscape");

        final String key;

        UiFormFactor(String key) {
            this.key = key;
        }

        static UiFormFactor parse(String raw) {
            for (UiFormFactor v : values()) {
                if (v.key.equals(raw)) {
                    return v;
                }
            }
            return AUTO;
        }
    }

    /** 点播清晰度（原 {@code lq5}）。 */
    public enum VideoResolution {
        AUTO("auto", "自动"), HIGHEST("highest", "最高"),
        P1080("1080p", "高清 1080p"), P720("720p", "标清 720p"), P480("480p", "流畅 540p");

        final String key;
        final String label;

        VideoResolution(String key, String label) {
            this.key = key;
            this.label = label;
        }

        static VideoResolution parse(String raw) {
            for (VideoResolution v : values()) {
                if (v.key.equals(raw)) {
                    return v;
                }
            }
            return null;
        }
    }

    /** 编码偏好（原 {@code cj5}）。 */
    public enum VideoCodec {
        AUTO("auto"), H264("h264"), H265("h265");

        final String key;

        VideoCodec(String key) {
            this.key = key;
        }

        static VideoCodec parse(String raw) {
            for (VideoCodec v : values()) {
                if (v.key.equals(raw)) {
                    return v;
                }
            }
            return null;
        }
    }

    /** 渲染面（原 {@code dp3}）。 */
    public enum SurfaceType {
        SURFACE_VIEW("surface_view"), TEXTURE_VIEW("texture_view"), GL_SURFACE_VIEW("gl_surface_view");

        final String key;

        SurfaceType(String key) {
            this.key = key;
        }

        static SurfaceType parse(String raw) {
            for (SurfaceType v : values()) {
                if (v.key.equals(raw)) {
                    return v;
                }
            }
            return null;
        }
    }

    /** 对比度（原 {@code z70}）。 */
    public enum ColorContrast {
        STANDARD("standard"), MEDIUM("medium"), HIGH("high");

        final String key;

        ColorContrast(String key) {
            this.key = key;
        }

        static ColorContrast parse(String raw) {
            for (ColorContrast v : values()) {
                if (v.key.equals(raw)) {
                    return v;
                }
            }
            return null;
        }
    }

    /** 取色来源（原 {@code t80}）。 */
    public enum ColorSource {
        CONTENT("content"), DEVICE("device"), BRAND("brand");

        final String key;

        ColorSource(String key) {
            this.key = key;
        }

        static ColorSource parse(String raw) {
            for (ColorSource v : values()) {
                if (v.key.equals(raw)) {
                    return v;
                }
            }
            return null;
        }
    }

    /** 配色风格（原 {@code s90}）。 */
    public enum ColorStyle {
        BY_SCENE("by_scene"), VIBRANT("vibrant"), SOFT("soft"), FIDELITY("fidelity");

        final String key;

        ColorStyle(String key) {
            this.key = key;
        }

        static ColorStyle parse(String raw) {
            for (ColorStyle v : values()) {
                if (v.key.equals(raw)) {
                    return v;
                }
            }
            return null;
        }
    }

    /** 主题切换动画（原 {@code v90}）。 */
    public enum ColorTransition {
        AUTO("auto"), OFF("off");

        final String key;

        ColorTransition(String key) {
            this.key = key;
        }

        static ColorTransition parse(String raw) {
            for (ColorTransition v : values()) {
                if (v.key.equals(raw)) {
                    return v;
                }
            }
            return null;
        }
    }

    /** 音量均衡（原 {@code EnumC0642fr}）。 */
    public enum AudioBalance {
        OFF("off"), LOW("low"), MEDIUM("medium"), HIGH("high");

        final String key;

        AudioBalance(String key) {
            this.key = key;
        }

        static AudioBalance parse(String raw) {
            for (AudioBalance v : values()) {
                if (v.key.equals(raw)) {
                    return v;
                }
            }
            return null;
        }
    }

    /** 主题模式（原 {@code t65}，落盘为 int）。 */
    public enum ThemeMode {
        AUTO(0, "自动"), LIGHT(1, "浅色"), DARK(2, "暗色");

        final int value;
        final String label;

        ThemeMode(int value, String label) {
            this.value = value;
            this.label = label;
        }
    }

    /** 字体档位（原 {@code EnumC1483on}）。 */
    public enum FontScale {
        STANDARD(1.0f, "标准"), LARGE(1.15f, "大"), EXTRA_LARGE(1.3f, "特大"), HUGE(1.5f, "超大");

        final float scale;
        final String label;

        FontScale(float scale, String label) {
            this.scale = scale;
            this.label = label;
        }
    }

    /** 弹幕字号（原 {@code so0}，落盘为倍率）。 */
    public enum DanmakuFontSize {
        EXTRA_SMALL(1.0f, "超小"), SMALL(1.5f, "小"), MEDIUM(2.0f, "中"),
        LARGE(2.5f, "大"), EXTRA_LARGE(3.0f, "超大");

        final float scale;
        final String label;

        DanmakuFontSize(float scale, String label) {
            this.scale = scale;
            this.label = label;
        }
    }

    /** 弹幕速度（原 {@code vo0}，落盘为倍率）。 */
    public enum DanmakuSpeed {
        SLOW(0.5f, "慢"), NORMAL(1.0f, "中"), FAST(1.5f, "快");

        final float scale;
        final String label;

        DanmakuSpeed(float scale, String label) {
            this.scale = scale;
            this.label = label;
        }
    }

    /** 直播弹幕样式（原 {@code an2}）。 */
    public enum DanmakuStyle {
        SCROLL("scroll"), LIST("list");

        final String key;

        DanmakuStyle(String key) {
            this.key = key;
        }

        static DanmakuStyle parse(String raw) {
            for (DanmakuStyle v : values()) {
                if (v.key.equals(raw)) {
                    return v;
                }
            }
            return null;
        }
    }

    /** 播放结束行为（原 {@code so3}）。 */
    public enum CompletionAction {
        AUTO_NEXT("auto_next"), PAUSE("pause"), REPLAY("replay");

        final String key;

        CompletionAction(String key) {
            this.key = key;
        }

        static CompletionAction parse(String raw) {
            for (CompletionAction v : values()) {
                if (v.key.equals(raw)) {
                    return v;
                }
            }
            return null;
        }
    }

    /** 时钟显示（原 {@code po3}）。 */
    public enum PlaybackClock {
        ALWAYS("always"), HOURLY("hourly"), HOURLY_AND_HALF("hourly_and_half"), NEVER("never");

        final String key;

        PlaybackClock(String key) {
            this.key = key;
        }

        static PlaybackClock parse(String raw) {
            for (PlaybackClock v : values()) {
                if (v.key.equals(raw)) {
                    return v;
                }
            }
            return null;
        }
    }

    /** 历史容量（原 {@code sd1}，落盘为 int）。 */
    public enum FeedHistoryCapacity {
        CAPACITY_50(50), CAPACITY_100(100), CAPACITY_200(200), CAPACITY_500(500), CAPACITY_1000(1000);

        final int value;

        FeedHistoryCapacity(int value) {
            this.value = value;
        }

        static FeedHistoryCapacity fromValue(int value) {
            for (FeedHistoryCapacity v : values()) {
                if (v.value == value) {
                    return v;
                }
            }
            return null;
        }
    }

    /** 首页 Tab（原 {@code rr0}，落盘为 int）。 */
    public enum HomeScreen {
        RECOMMEND(0, "推荐"), FEATURED(1, "精选"), FOLLOWING(2, "直播"),
        MOBILE_RECOMMEND(3, "移动推荐");

        final int value;
        final String label;

        HomeScreen(int value, String label) {
            this.value = value;
            this.label = label;
        }

        static HomeScreen fromValue(int value) {
            for (HomeScreen v : values()) {
                if (v.value == value) {
                    return v;
                }
            }
            return null;
        }
    }

    /** 视频加载超时（原 {@code qn5}，落盘为 int 秒）。 */
    public enum LoadTimeout {
        SECONDS_5(5), SECONDS_10(10), SECONDS_15(15), SECONDS_30(30), SECONDS_60(60);

        final int seconds;

        LoadTimeout(int seconds) {
            this.seconds = seconds;
        }

        static LoadTimeout fromSeconds(int seconds) {
            for (LoadTimeout v : values()) {
                if (v.seconds == seconds) {
                    return v;
                }
            }
            return null;
        }
    }

}
