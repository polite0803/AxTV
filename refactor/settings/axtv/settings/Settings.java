/*
 * 来源: src/app/p000/fl4.java（1138 行，Log TAG "SettingsManager"）+ src/app/p000/ll4.java（UI 写入映射）
 *       + src/app/p000/wm4.java（设置页状态读取的全部键与默认值）
 * 原职责: 全部用户配置项的读取/写入/夹取/旧键迁移，账号列表与互动凭据持久化
 * 重构说明: 去掉 fw4/p04 响应式包装（StateFlow 属 UI 层，由 Android 侧订阅）、
 *          去掉 f6213u0 之类字段名；键名/默认值/取值范围/迁移顺序原样保留。
 */
package axtv.settings;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 配置中心（原 {@code fl4}）。
 *
 * <p>写入语义与原文一致：所有 setter 立即落盘（Android 侧 {@code apply()}），同时由 Android 层
 * 自己刷新 Compose 状态（原 {@code fw4.m4285i} 的那一步），核心层不持有任何状态流。
 *
 * <p>纯 Java 17，无 Android 依赖；设备相关默认值（TV 判定、SDK 版本）通过 {@link DeviceProfile} 注入。
 */
public final class Settings {

    private static final Logger LOG = Logger.getLogger("SettingsManager");

    /** 弹幕区域百分比的合法区间（原 {@code ut0.m10995r(i, 1, 100)}）。 */
    private static final int AREA_MIN = 1;
    private static final int AREA_MAX = 100;

    /** 字体缩放区间（原构造与 ll4 case 3 都用 1.0f..1.6f）。 */
    public static final float FONT_SCALE_MIN = 1.0f;
    public static final float FONT_SCALE_MAX = 1.6f;

    /** 可选倍速集合（原 {@code wm4.f23784c}）。 */
    public static final Set<Float> PLAYBACK_SPEEDS =
            Set.of(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f, 3.0f);

    /** 视频加载超时可选值（原 {@code fl4.f6213u0 = {5,10,15,30,60}}）。 */
    public static final Set<Integer> LOAD_TIMEOUTS = Set.of(5, 10, 15, 30, 60);

    private static final SecureRandom RANDOM = new SecureRandom();

    private final SettingsStore store;
    private final SettingsJson json;
    private final DeviceProfile device;

    /** 原构造里一次性算好的账号列表（含迁移副作用），这里用惰性缓存实现等价语义。 */
    private List<Account> accountsCache;

    public Settings(SettingsStore store) {
        this(store, new SimpleSettingsJson(), DeviceProfile.current());
    }

    /** 只换设备判定（Android 侧注入真实 SDK / TV 能力）。 */
    public Settings(SettingsStore store, DeviceProfile device) {
        this(store, new SimpleSettingsJson(), device);
    }

    public Settings(SettingsStore store, SettingsJson json, DeviceProfile device) {
        this.store = store;
        this.json = json;
        this.device = device;
    }

    // ════════════════════════════ 账号 ════════════════════════════

    /**
     * 账号列表（原构造里那段 accounts_list 解析 / 迁移）。
     *
     * <p>顺序还原：读 {@code accounts_list} → 解析失败记日志并降级为空 → 空则删除键并走旧单人账号迁移
     * → 保证存在 id="main" 的账号（否则把第一条改名 main 并回写）→ 校正 {@code current_account_id}。
     */
    public synchronized List<Account> accounts() {
        if (accountsCache != null) {
            return accountsCache;
        }
        List<Account> list = loadAccounts();
        accountsCache = List.copyOf(list);
        return accountsCache;
    }

    private List<Account> loadAccounts() {
        String raw = store.getString(SettingsKeys.ACCOUNTS_LIST, null);
        List<Account> list;
        if (raw == null || raw.isBlank()) {
            list = migrateLegacyAccount();
        } else {
            try {
                list = new ArrayList<>(json.parseAccounts(raw));
            } catch (RuntimeException e) {
                LOG.log(Level.SEVERE, "解析 accounts_list 失败，尝试回退迁移", e);
                list = new ArrayList<>();
            }
            if (list.isEmpty()) {
                store.remove(SettingsKeys.ACCOUNTS_LIST);
                list = migrateLegacyAccount();
            }
        }
        if (list.isEmpty()) {
            return list;
        }
        if (list.stream().noneMatch(a -> a.id().equals("main"))) {
            // 原逻辑：把第一条账号 id 改成 main 并回写（m2971a(..., 1022) 实际只改 id）
            list.set(0, list.get(0).withId("main"));
            writeAccounts(list);
        }
        Account current = findById(list, currentAccountId());
        if (current == null) {
            current = findMainOrFirst(list);
            if (current != null) {
                setCurrentAccountId(current.id());
            }
        }
        return list;
    }

    /** 旧版单账号字段 → 账号列表（原 {@code m4196c}）。 */
    private List<Account> migrateLegacyAccount() {
        String cookie = cookie();
        if (cookie.isBlank() && !interaction().isComplete()) {
            return new ArrayList<>();
        }
        Account main = new Account("main", cookie, interaction().rawPayload(),
                store.getString(SettingsKeys.INTERACTION_TICKET, ""),
                store.getString(SettingsKeys.INTERACTION_TS_SIGN, ""),
                store.getString(SettingsKeys.INTERACTION_PRIVATE_KEY, ""),
                "", "", store.getString(SettingsKeys.SELF_USER_ID, ""),
                store.getString(SettingsKeys.INTERACTION_SERVER_CERT, ""));
        List<Account> list = new ArrayList<>(List.of(main));
        writeAccounts(list);
        setCurrentAccountId("main");
        return list;
    }

    /** 整体替换账号列表（原 {@code m4183A} / {@code m4215x}）。 */
    public synchronized void setAccounts(List<Account> list) {
        List<Account> copy = new ArrayList<>(list);
        accountsCache = List.copyOf(copy);
        writeAccounts(copy);
    }

    private void writeAccounts(List<Account> list) {
        store.putString(SettingsKeys.ACCOUNTS_LIST, json.writeAccounts(list));
    }

    public String currentAccountId() {
        return nz(store.getString(SettingsKeys.CURRENT_ACCOUNT_ID, ""));
    }

    public void setCurrentAccountId(String id) {
        store.putString(SettingsKeys.CURRENT_ACCOUNT_ID, id);
    }

    /** 当前账号对象，未登录/找不到时返回 null。 */
    public Account currentAccount() {
        return findById(accounts(), currentAccountId());
    }

    /**
     * 切换账号（原 {@code m4193K}）：写 Cookie → 有 tsSign 则写互动凭据否则清空 → 切 current_account_id。
     */
    public void switchAccount(String accountId) {
        Account account = findById(accounts(), accountId);
        if (account == null) {
            return;
        }
        setCookie(account.cookie());
        if (account.tsSign().isBlank()) {
            clearInteraction();
        } else {
            setInteraction(account.securityPayload(), new Interaction(
                    account.securityPayload(), account.ticket(), account.tsSign(),
                    account.privateKey(), ""));
        }
        setCurrentAccountId(accountId);
    }

    /** 删除账号的 PIN（原 {@code wm4.m11603p}：pinHash / pinSalt 清空）。 */
    public synchronized void clearAccountPin(String accountId) {
        List<Account> list = new ArrayList<>(accounts());
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id().equals(accountId)) {
                list.set(i, list.get(i).withPin("", ""));
                setAccounts(list);
                return;
            }
        }
    }

    /** 校验 PIN（原 {@code wm4.m11602B}：sha256(salt + ":" + pin) 与 pinHash 比较）。 */
    public boolean verifyPin(String accountId, String pin) {
        Account account = findById(accounts(), accountId);
        return account != null && account.hasPin() && Account.hashPin(pin, account.pinSalt()).equals(account.pinHash());
    }

    // ════════════════════════════ Cookie / 登录态 ════════════════════════════

    /** {@code cookie_string}，缺省空串（原 {@code m4200g}）。 */
    public String cookie() {
        return nz(store.getString(SettingsKeys.COOKIE_STRING, ""));
    }

    /** 写 Cookie（原 {@code m4190H}）。 */
    public void setCookie(String cookie) {
        store.putString(SettingsKeys.COOKIE_STRING, nz(cookie));
    }

    /** 是否已登录：Cookie 中存在非空 sessionid / sessionid_ss（原 {@code el4.m3730a}）。 */
    public boolean isLoggedIn() {
        return hasSessionId(cookie());
    }

    public static boolean hasSessionId(String cookie) {
        if (cookie == null || cookie.isEmpty()) {
            return false;
        }
        for (String part : cookie.split(";")) {
            int eq = part.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String name = part.substring(0, eq).trim();
            String value = part.substring(eq + 1).trim();
            if ((name.equals("sessionid") || name.equals("sessionid_ss")) && !value.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** Cookie 解析成有序 Map（原 {@code m4201h}：按 ";" 切，再按第一个 "=" 切，两端 trim）。 */
    public LinkedHashMap<String, String> cookieValues() {
        LinkedHashMap<String, String> map = new LinkedHashMap<>();
        for (String part : cookie().split(";")) {
            String[] kv = part.trim().split("=", 2);
            if (kv.length == 2) {
                map.put(kv[0].trim(), kv[1].trim());
            }
        }
        return map;
    }

    // ════════════════════════════ 互动凭据 ════════════════════════════

    /** 读互动凭据（原 {@code m4205l}）。 */
    public Interaction interaction() {
        return new Interaction(
                store.getString(SettingsKeys.INTERACTION_SECURITY_PAYLOAD, ""),
                store.getString(SettingsKeys.INTERACTION_TICKET, ""),
                store.getString(SettingsKeys.INTERACTION_TS_SIGN, ""),
                store.getString(SettingsKeys.INTERACTION_PRIVATE_KEY, ""),
                store.getString(SettingsKeys.INTERACTION_SERVER_CERT, ""));
    }

    /** 写互动凭据（原 {@code m4189G}）：5 个键一起写。 */
    public void setInteraction(String rawPayload, Interaction config) {
        store.putString(SettingsKeys.INTERACTION_SECURITY_PAYLOAD, nz(rawPayload));
        store.putString(SettingsKeys.INTERACTION_TICKET, config.ticket());
        store.putString(SettingsKeys.INTERACTION_TS_SIGN, config.tsSign());
        store.putString(SettingsKeys.INTERACTION_PRIVATE_KEY, config.privateKey());
        store.putString(SettingsKeys.INTERACTION_SERVER_CERT, config.serverCert());
    }

    /** 清空互动凭据（原 {@code m4195b}）。 */
    public void clearInteraction() {
        store.remove(SettingsKeys.INTERACTION_SECURITY_PAYLOAD);
        store.remove(SettingsKeys.INTERACTION_TICKET);
        store.remove(SettingsKeys.INTERACTION_TS_SIGN);
        store.remove(SettingsKeys.INTERACTION_PRIVATE_KEY);
        store.remove(SettingsKeys.INTERACTION_SERVER_CERT);
    }

    /** 互动凭据是否完整可用（原 {@code m4213u}）。 */
    public boolean hasInteraction() {
        return interaction().isComplete();
    }

    // ════════════════════════════ 用户标识 ════════════════════════════

    /** 自己 userId（原 {@code m4210q}：优先当前账号，其次 self_user_id）。 */
    public String selfUserId() {
        Account account = currentAccount();
        if (account != null && !account.userId().isBlank()) {
            return account.userId();
        }
        return nz(store.getString(SettingsKeys.SELF_USER_ID, ""));
    }

    /** 写自己 userId（原 {@code m4191I}）：有当前账号就写账号并回写列表，否则写 self_user_id。 */
    public synchronized void setSelfUserId(String userId) {
        List<Account> list = new ArrayList<>(accounts());
        int index = indexOf(list, currentAccountId());
        if (index < 0) {
            store.putString(SettingsKeys.SELF_USER_ID, userId);
            return;
        }
        if (list.get(index).userId().equals(userId)) {
            return;
        }
        list.set(index, list.get(index).withUserId(userId));
        setAccounts(list);
    }

    public String userUniqueId() {
        return nz(store.getString(SettingsKeys.USER_UNIQUE_ID, ""));
    }

    public void setUserUniqueId(String id) {
        store.putString(SettingsKeys.USER_UNIQUE_ID, id);
    }

    /**
     * 惰性生成 16 位纯数字设备号（原 {@code m4208o}）。
     * 已存在且长度为 16 且全为数字就复用；否则用 SecureRandom 生成并写回。
     */
    public String deviceId(String key) {
        String value = nz(store.getString(key, ""));
        if (value.length() == 16 && value.chars().allMatch(Character::isDigit)) {
            return value;
        }
        String generated = Long.toString(((RANDOM.nextLong() >>> 1) % 9_000_000_000_000_000L) + 1_000_000_000_000_000L);
        store.putString(key, generated);
        return generated;
    }

    public String mobileFeedDeviceId() {
        return deviceId(SettingsKeys.MOBILE_FEED_DEVICE_ID);
    }

    public String mobileFeedInstallId() {
        return deviceId(SettingsKeys.MOBILE_FEED_INSTALL_ID);
    }

    // ════════════════════════════ 观看记录 ════════════════════════════

    /** 已看过的视频 id（原 {@code m4212s}：逗号分隔，去空白项）。 */
    public List<String> viewedVideoIds() {
        String raw = nz(store.getString(SettingsKeys.VIEWED_VIDEO_IDS, ""));
        if (raw.isBlank()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String part : raw.split(",")) {
            if (!part.isBlank()) {
                out.add(part);
            }
        }
        return out;
    }

    public void setViewedVideoIds(List<String> ids) {
        store.putString(SettingsKeys.VIEWED_VIDEO_IDS, String.join(",", ids));
    }

    // ════════════════════════════ 界面形态 / 字体 / 主题 ════════════════════════════

    /** {@code ui_form_factor}，未知值回落 AUTO。 */
    public UiFormFactor formFactor() {
        return UiFormFactor.parse(store.getString(SettingsKeys.UI_FORM_FACTOR, null));
    }

    public void setFormFactor(UiFormFactor factor) {
        store.putString(SettingsKeys.UI_FORM_FACTOR, factor.key);
    }

    /** {@code app_font_scale}，夹到 1.0..1.6（原构造与 ll4 case 3）。 */
    public float fontScale() {
        return clamp(store.getFloat(SettingsKeys.APP_FONT_SCALE, 1.0f), FONT_SCALE_MIN, FONT_SCALE_MAX);
    }

    public void setFontScale(float scale) {
        store.putFloat(SettingsKeys.APP_FONT_SCALE, clamp(scale, FONT_SCALE_MIN, FONT_SCALE_MAX));
    }

    /** 字体档位（原 {@code EnumC1483on}），由 app_font_scale 反查。 */
    public FontScale fontScalePreset() {
        float value = fontScale();
        for (FontScale preset : FontScale.values()) {
            if (preset.scale == value) {
                return preset;
            }
        }
        return FontScale.STANDARD;
    }

    public void setFontScalePreset(FontScale preset) {
        setFontScale(preset.scale);
    }

    /** {@code theme_mode}（0 自动 / 1 浅色 / 2 暗色），未知值回落 AUTO。 */
    public ThemeMode themeMode() {
        int value = store.getInt(SettingsKeys.THEME_MODE, 0);
        for (ThemeMode mode : ThemeMode.values()) {
            if (mode.value == value) {
                return mode;
            }
        }
        return ThemeMode.AUTO;
    }

    public void setThemeMode(ThemeMode mode) {
        store.putInt(SettingsKeys.THEME_MODE, mode.value);
    }

    /**
     * 对比度（原 {@code m4197d}）：未设置时按设备判定 —— TV（leanback 或 uiMode 为 TV）用 MEDIUM，否则 STANDARD。
     * 注意原文这里**不回写**。
     */
    public ColorContrast colorContrast() {
        ColorContrast stored = ColorContrast.parse(store.getString(SettingsKeys.COLOR_CONTRAST, null));
        if (stored != null) {
            return stored;
        }
        return device.isTelevision() ? ColorContrast.MEDIUM : ColorContrast.STANDARD;
    }

    public void setColorContrast(ColorContrast contrast) {
        store.putString(SettingsKeys.COLOR_CONTRAST, contrast.key);
    }

    /**
     * 取色来源（原 {@code m4198e}）：先读 {@code color_source}；
     * 未设置时做一次性迁移 —— 有 {@code dynamic_color} 键：false→BRAND、true 且 SDK≥31→DEVICE、其余 CONTENT；
     * 迁移结果**立即回写** color_source。
     */
    public ColorSource colorSource() {
        ColorSource stored = ColorSource.parse(store.getString(SettingsKeys.COLOR_SOURCE, null));
        if (stored != null) {
            return stored;
        }
        ColorSource resolved = ColorSource.CONTENT;
        if (store.contains(SettingsKeys.DYNAMIC_COLOR)) {
            if (!store.getBoolean(SettingsKeys.DYNAMIC_COLOR, true)) {
                resolved = ColorSource.BRAND;
            } else if (device.sdkInt() >= 31) {
                resolved = ColorSource.DEVICE;
            }
        }
        store.putString(SettingsKeys.COLOR_SOURCE, resolved.key);
        return resolved;
    }

    public void setColorSource(ColorSource source) {
        store.putString(SettingsKeys.COLOR_SOURCE, source.key);
    }

    /** 配色风格（原 {@code m4199f}），缺省 BY_SCENE。 */
    public ColorStyle colorStyle() {
        ColorStyle stored = ColorStyle.parse(store.getString(SettingsKeys.COLOR_STYLE, null));
        return stored == null ? ColorStyle.BY_SCENE : stored;
    }

    public void setColorStyle(ColorStyle style) {
        store.putString(SettingsKeys.COLOR_STYLE, style.key);
    }

    /** 主题切换动画（原 {@code v90}），缺省 AUTO。 */
    public ColorTransition colorTransition() {
        ColorTransition stored = ColorTransition.parse(store.getString(SettingsKeys.COLOR_TRANSITION, null));
        return stored == null ? ColorTransition.AUTO : stored;
    }

    public void setColorTransition(ColorTransition transition) {
        store.putString(SettingsKeys.COLOR_TRANSITION, transition.key);
    }

    /** 全局取色种子（ARGB），0 表示未设置。 */
    public int globalSeedArgb() {
        return store.getInt(SettingsKeys.GLOBAL_SEED_ARGB, 0);
    }

    public void setGlobalSeedArgb(int argb) {
        store.putInt(SettingsKeys.GLOBAL_SEED_ARGB, argb);
    }

    // ════════════════════════════ 播放 ════════════════════════════

    /** 点播清晰度（原 {@code video_resolution}），缺省 AUTO（"auto"）。 */
    public VideoResolution videoResolution() {
        VideoResolution stored = VideoResolution.parse(store.getString(SettingsKeys.VIDEO_RESOLUTION, "auto"));
        return stored == null ? VideoResolution.AUTO : stored;
    }

    public void setVideoResolution(VideoResolution resolution) {
        store.putString(SettingsKeys.VIDEO_RESOLUTION, resolution.key);
    }

    /**
     * 直播清晰度（原构造 316-320 行 + {@code m4182w}）。
     *
     * <p>规则：有 {@code live_video_resolution} 用它的值；否则取 {@code video_resolution}。
     * 解析后若为 AUTO（或未知）→ 直播用 HIGHEST（"highest"），因为「自动」在直播里不合法。
     */
    public VideoResolution liveVideoResolution() {
        String raw = store.contains(SettingsKeys.LIVE_VIDEO_RESOLUTION)
                ? store.getString(SettingsKeys.LIVE_VIDEO_RESOLUTION, "auto")
                : store.getString(SettingsKeys.VIDEO_RESOLUTION, "auto");
        VideoResolution resolved = VideoResolution.parse(raw);
        if (resolved == null || resolved == VideoResolution.AUTO) {
            return VideoResolution.HIGHEST;
        }
        return resolved;
    }

    public void setLiveVideoResolution(VideoResolution resolution) {
        store.putString(SettingsKeys.LIVE_VIDEO_RESOLUTION, resolution.key);
    }

    /** 编码偏好（auto / h264 / h265），未知值回落 AUTO。 */
    public VideoCodec videoCodec() {
        VideoCodec stored = VideoCodec.parse(store.getString(SettingsKeys.VIDEO_CODEC_PREFERENCE, "auto"));
        return stored == null ? VideoCodec.AUTO : stored;
    }

    public void setVideoCodec(VideoCodec codec) {
        store.putString(SettingsKeys.VIDEO_CODEC_PREFERENCE, codec.key);
    }

    /** 分离音视频流（默认 true）。 */
    public boolean separateVideoFetch() {
        return store.getBoolean(SettingsKeys.SEPARATE_VIDEO_FETCH, true);
    }

    public void setSeparateVideoFetch(boolean enabled) {
        store.putBoolean(SettingsKeys.SEPARATE_VIDEO_FETCH, enabled);
    }

    /** 起播预取（默认 false）。 */
    public boolean videoStartPrefetch() {
        return store.getBoolean(SettingsKeys.VIDEO_START_PREFETCH, false);
    }

    public void setVideoStartPrefetch(boolean enabled) {
        store.putBoolean(SettingsKeys.VIDEO_START_PREFETCH, enabled);
    }

    /** 视频加载超时秒数；只允许 5/10/15/30/60，其它值回落 10。 */
    public int videoLoadTimeoutSeconds() {
        int value = store.getInt(SettingsKeys.VIDEO_LOAD_TIMEOUT_SECONDS, 10);
        return LOAD_TIMEOUTS.contains(value) ? value : 10;
    }

    public void setVideoLoadTimeoutSeconds(int seconds) {
        if (LOAD_TIMEOUTS.contains(seconds)) {
            store.putInt(SettingsKeys.VIDEO_LOAD_TIMEOUT_SECONDS, seconds);
        }
    }

    /** 超时档位视图（原 {@code qn5}），仅供设置页使用。 */
    public LoadTimeout loadTimeout() {
        LoadTimeout preset = LoadTimeout.fromSeconds(videoLoadTimeoutSeconds());
        return preset == null ? LoadTimeout.SECONDS_10 : preset;
    }

    public void setLoadTimeout(LoadTimeout timeout) {
        setVideoLoadTimeoutSeconds(timeout.seconds);
    }

    /** 渲染面类型（默认 surface_view）。 */
    public SurfaceType surfaceType() {
        SurfaceType stored = SurfaceType.parse(store.getString(SettingsKeys.PLAYER_SURFACE_TYPE, "surface_view"));
        return stored == null ? SurfaceType.SURFACE_VIEW : stored;
    }

    public void setSurfaceType(SurfaceType type) {
        store.putString(SettingsKeys.PLAYER_SURFACE_TYPE, type.key);
    }

    /** Surface 缓冲对齐（默认 false；原构造里还会同步给 zz4 全局状态）。 */
    public boolean surfaceBufferAlign() {
        return store.getBoolean(SettingsKeys.SURFACE_BUFFER_ALIGN, false);
    }

    public void setSurfaceBufferAlign(boolean enabled) {
        store.putBoolean(SettingsKeys.SURFACE_BUFFER_ALIGN, enabled);
    }

    /** 播放倍速；不在允许集合内时回落 1.0 并回写（原 wm4 构造末尾的校正）。 */
    public float playbackSpeed() {
        float value = store.getFloat(SettingsKeys.PLAYBACK_SPEED, 1.0f);
        if (!PLAYBACK_SPEEDS.contains(value)) {
            store.putFloat(SettingsKeys.PLAYBACK_SPEED, 1.0f);
            return 1.0f;
        }
        return value;
    }

    public void setPlaybackSpeed(float speed) {
        if (PLAYBACK_SPEEDS.contains(speed)) {
            store.putFloat(SettingsKeys.PLAYBACK_SPEED, speed);
        }
    }

    /** 播放结束行为（原 {@code m4209p}）：缺省由旧键 auto_play_next_video 推导。 */
    public CompletionAction completionAction() {
        String raw = store.getString(SettingsKeys.PLAYBACK_COMPLETION_ACTION, null);
        if (raw == null || raw.isBlank()) {
            return store.getBoolean(SettingsKeys.AUTO_PLAY_NEXT_VIDEO, true)
                    ? CompletionAction.AUTO_NEXT : CompletionAction.PAUSE;
        }
        CompletionAction stored = CompletionAction.parse(raw);
        return stored == null ? CompletionAction.AUTO_NEXT : stored;
    }

    /** 写播放结束行为：同时写旧键，保持与旧版本互通（原 {@code wm4.m11612y}）。 */
    public void setCompletionAction(CompletionAction action) {
        store.putString(SettingsKeys.PLAYBACK_COMPLETION_ACTION, action.key);
        store.putBoolean(SettingsKeys.AUTO_PLAY_NEXT_VIDEO, action == CompletionAction.AUTO_NEXT);
    }

    /** 时钟显示模式（默认 never）。 */
    public PlaybackClock playbackClock() {
        PlaybackClock stored = PlaybackClock.parse(store.getString(SettingsKeys.PLAYBACK_CLOCK_DISPLAY_MODE, "never"));
        return stored == null ? PlaybackClock.NEVER : stored;
    }

    public void setPlaybackClock(PlaybackClock clock) {
        store.putString(SettingsKeys.PLAYBACK_CLOCK_DISPLAY_MODE, clock.key);
    }

    /** 播放器进度条（默认 false）。 */
    public boolean playerProgressBar() {
        return store.getBoolean(SettingsKeys.PLAYER_PROGRESS_BAR, false);
    }

    public void setPlayerProgressBar(boolean enabled) {
        store.putBoolean(SettingsKeys.PLAYER_PROGRESS_BAR, enabled);
    }

    /** 画中画（默认 false）。 */
    public boolean pipEnabled() {
        return store.getBoolean(SettingsKeys.PIP_ENABLED, false);
    }

    public void setPipEnabled(boolean enabled) {
        store.putBoolean(SettingsKeys.PIP_ENABLED, enabled);
    }

    /** 暂停时隐藏控件（默认 false）。 */
    public boolean cleanPause() {
        return store.getBoolean(SettingsKeys.CLEAN_PAUSE, false);
    }

    public void setCleanPause(boolean enabled) {
        store.putBoolean(SettingsKeys.CLEAN_PAUSE, enabled);
    }

    /** 音量均衡（off/low/medium/high，默认 off）。 */
    public AudioBalance audioBalance() {
        AudioBalance stored = AudioBalance.parse(store.getString(SettingsKeys.AUDIO_BALANCE_LEVEL, "off"));
        return stored == null ? AudioBalance.OFF : stored;
    }

    public void setAudioBalance(AudioBalance balance) {
        store.putString(SettingsKeys.AUDIO_BALANCE_LEVEL, balance.key);
    }

    // ════════════════════════════ 列表 / 导航 ════════════════════════════

    /** 历史容量（50/100/200/500/1000，默认 100）。 */
    public FeedHistoryCapacity feedHistoryCapacity() {
        int value = store.getInt(SettingsKeys.FEED_HISTORY_CAPACITY, 100);
        FeedHistoryCapacity capacity = FeedHistoryCapacity.fromValue(value);
        return capacity == null ? FeedHistoryCapacity.CAPACITY_100 : capacity;
    }

    public void setFeedHistoryCapacity(FeedHistoryCapacity capacity) {
        store.putInt(SettingsKeys.FEED_HISTORY_CAPACITY, capacity.value);
    }

    /** 首页默认 Tab（0 推荐 / 1 精选 / 2 直播 / 3 移动推荐）。 */
    public HomeScreen defaultHomeScreen() {
        int value = store.getInt(SettingsKeys.DEFAULT_HOME_SCREEN, 0);
        HomeScreen screen = HomeScreen.fromValue(value);
        return screen == null ? HomeScreen.RECOMMEND : screen;
    }

    public void setDefaultHomeScreen(HomeScreen screen) {
        store.putInt(SettingsKeys.DEFAULT_HOME_SCREEN, screen.value);
    }

    /** 混合播放列表浮层（默认 true）。 */
    public boolean mixPlaylistPanelEnabled() {
        return store.getBoolean(SettingsKeys.MIX_PLAYLIST_PANEL_ENABLED, true);
    }

    public void setMixPlaylistPanelEnabled(boolean enabled) {
        store.putBoolean(SettingsKeys.MIX_PLAYLIST_PANEL_ENABLED, enabled);
    }

    /** 进详情页显示简介（默认 true）。 */
    public boolean showDetailsOnStart() {
        return store.getBoolean(SettingsKeys.SHOW_DETAILS_ON_START, true);
    }

    public void setShowDetailsOnStart(boolean enabled) {
        store.putBoolean(SettingsKeys.SHOW_DETAILS_ON_START, enabled);
    }

    /** 主页打开自己的作品集时进历史 Tab（默认 false）。 */
    public boolean openOwnProfileOnHistoryTab() {
        return store.getBoolean(SettingsKeys.OPEN_OWN_PROFILE_ON_HISTORY_TAB, false);
    }

    public void setOpenOwnProfileOnHistoryTab(boolean enabled) {
        store.putBoolean(SettingsKeys.OPEN_OWN_PROFILE_ON_HISTORY_TAB, enabled);
    }

    /** 个人页倒序播放列表（默认 false）。 */
    public boolean reversePlaylistInProfile() {
        return store.getBoolean(SettingsKeys.REVERSE_PLAYLIST_IN_PROFILE, false);
    }

    public void setReversePlaylistInProfile(boolean enabled) {
        store.putBoolean(SettingsKeys.REVERSE_PLAYLIST_IN_PROFILE, enabled);
    }

    /** 遥控器方向键反向（默认 false）。 */
    public boolean reverseNavigation() {
        return store.getBoolean(SettingsKeys.REVERSE_NAVIGATION, false);
    }

    public void setReverseNavigation(boolean enabled) {
        store.putBoolean(SettingsKeys.REVERSE_NAVIGATION, enabled);
    }

    /** 双击返回不退出（默认 false）。 */
    public boolean disableDoubleBackExit() {
        return store.getBoolean(SettingsKeys.DISABLE_DOUBLE_BACK_EXIT, false);
    }

    public void setDisableDoubleBackExit(boolean enabled) {
        store.putBoolean(SettingsKeys.DISABLE_DOUBLE_BACK_EXIT, enabled);
    }

    /** 精选自动播放（默认 true）。 */
    public boolean featuredAutoPlay() {
        return store.getBoolean(SettingsKeys.FEATURED_AUTO_PLAY, true);
    }

    public void setFeaturedAutoPlay(boolean enabled) {
        store.putBoolean(SettingsKeys.FEATURED_AUTO_PLAY, enabled);
    }

    /** 过滤移动端视频（默认 false）。 */
    public boolean filterMobileVideo() {
        return store.getBoolean(SettingsKeys.FILTER_MOBILE_VIDEO, false);
    }

    public void setFilterMobileVideo(boolean enabled) {
        store.putBoolean(SettingsKeys.FILTER_MOBILE_VIDEO, enabled);
    }

    /** 桌面 TV 频道（默认 false）。 */
    public boolean tvLauncherChannelEnabled() {
        return store.getBoolean(SettingsKeys.TV_LAUNCHER_CHANNEL_ENABLED, false);
    }

    public void setTvLauncherChannelEnabled(boolean enabled) {
        store.putBoolean(SettingsKeys.TV_LAUNCHER_CHANNEL_ENABLED, enabled);
    }

    /** 沉浸式背景（默认 true）。 */
    public boolean ambientBackdrop() {
        return store.getBoolean(SettingsKeys.AMBIENT_BACKDROP, true);
    }

    public void setAmbientBackdrop(boolean enabled) {
        store.putBoolean(SettingsKeys.AMBIENT_BACKDROP, enabled);
    }

    // ════════════════════════════ 弹幕 ════════════════════════════

    /** 旧的总开关，仅作为 video/live 两项的缺省来源，UI 不再直接改它。 */
    public boolean danmakuEnabled() {
        return store.getBoolean(SettingsKeys.DANMAKU_ENABLED, true);
    }

    /** 视频弹幕（有独立键用独立键，否则跟随 danmaku_enabled，默认 true）。 */
    public boolean videoDanmakuEnabled() {
        return store.contains(SettingsKeys.VIDEO_DANMAKU_ENABLED)
                ? store.getBoolean(SettingsKeys.VIDEO_DANMAKU_ENABLED, true)
                : store.getBoolean(SettingsKeys.DANMAKU_ENABLED, true);
    }

    public void setVideoDanmakuEnabled(boolean enabled) {
        store.putBoolean(SettingsKeys.VIDEO_DANMAKU_ENABLED, enabled);
    }

    /** 直播弹幕（同上）。 */
    public boolean liveDanmakuEnabled() {
        return store.contains(SettingsKeys.LIVE_DANMAKU_ENABLED)
                ? store.getBoolean(SettingsKeys.LIVE_DANMAKU_ENABLED, true)
                : store.getBoolean(SettingsKeys.DANMAKU_ENABLED, true);
    }

    public void setLiveDanmakuEnabled(boolean enabled) {
        store.putBoolean(SettingsKeys.LIVE_DANMAKU_ENABLED, enabled);
    }

    /** 弹幕表情（默认 true）。 */
    public boolean danmakuEmojiEnabled() {
        return store.getBoolean(SettingsKeys.DANMAKU_EMOJI_ENABLED, true);
    }

    public void setDanmakuEmojiEnabled(boolean enabled) {
        store.putBoolean(SettingsKeys.DANMAKU_EMOJI_ENABLED, enabled);
    }

    /** 直播弹幕样式（滚动/列表，默认 SCROLL）。 */
    public DanmakuStyle liveDanmakuStyle() {
        DanmakuStyle stored = DanmakuStyle.parse(store.getString(SettingsKeys.LIVE_DANMAKU_STYLE, "scroll"));
        return stored == null ? DanmakuStyle.SCROLL : stored;
    }

    public void setLiveDanmakuStyle(DanmakuStyle style) {
        store.putString(SettingsKeys.LIVE_DANMAKU_STYLE, style.key);
    }

    /** 弹幕不透明度，夹到 0.01..1.0（默认 1.0）。 */
    public float danmakuOpacity() {
        return clamp(store.getFloat(SettingsKeys.DANMAKU_OPACITY, 1.0f), 0.01f, 1.0f);
    }

    public void setDanmakuOpacity(float opacity) {
        store.putFloat(SettingsKeys.DANMAKU_OPACITY, clamp(opacity, 0.01f, 1.0f));
    }

    /** 弹幕区域百分比 1..100（原 {@code m4203j}）：优先 percent，否则由旧比例键换算。 */
    public int danmakuAreaPercent() {
        if (store.contains(SettingsKeys.DANMAKU_AREA_PERCENT)) {
            return clamp(store.getInt(SettingsKeys.DANMAKU_AREA_PERCENT, 100), AREA_MIN, AREA_MAX);
        }
        float area = clamp(store.getFloat(SettingsKeys.DANMAKU_AREA, 1.0f), 0.01f, 1.0f);
        return clamp((int) (area * 100.0f), AREA_MIN, AREA_MAX);
    }

    /** 写弹幕区域（原 {@code m4185C}）：percent 与旧比例键同时写，旧版本可读。 */
    public void setDanmakuAreaPercent(int percent) {
        int clamped = clamp(percent, AREA_MIN, AREA_MAX);
        store.putInt(SettingsKeys.DANMAKU_AREA_PERCENT, clamped);
        store.putFloat(SettingsKeys.DANMAKU_AREA, clamped / 100.0f);
    }

    /** 弹幕垂直间距 0.5..2.0（默认 1.0）。 */
    public float danmakuVerticalSpacing() {
        return clamp(store.getFloat(SettingsKeys.DANMAKU_VERTICAL_SPACING, 1.0f), 0.5f, 2.0f);
    }

    public void setDanmakuVerticalSpacing(float spacing) {
        store.putFloat(SettingsKeys.DANMAKU_VERTICAL_SPACING, clamp(spacing, 0.5f, 2.0f));
    }

    /** 弹幕水平间距 0.5..2.0（默认 1.0）。 */
    public float danmakuHorizontalSpacing() {
        return clamp(store.getFloat(SettingsKeys.DANMAKU_HORIZONTAL_SPACING, 1.0f), 0.5f, 2.0f);
    }

    public void setDanmakuHorizontalSpacing(float spacing) {
        store.putFloat(SettingsKeys.DANMAKU_HORIZONTAL_SPACING, clamp(spacing, 0.5f, 2.0f));
    }

    /** 弹幕字号系数（原文不夹取，默认 1.0；档位见 {@link DanmakuFontSize}）。 */
    public float danmakuFontScale() {
        return store.getFloat(SettingsKeys.DANMAKU_FONT_SIZE, 1.0f);
    }

    public void setDanmakuFontScale(float scale) {
        store.putFloat(SettingsKeys.DANMAKU_FONT_SIZE, scale);
    }

    /** 字号档位反查，找不到档位时回落 MEDIUM。 */
    public DanmakuFontSize danmakuFontSize() {
        float value = danmakuFontScale();
        for (DanmakuFontSize size : DanmakuFontSize.values()) {
            if (size.scale == value) {
                return size;
            }
        }
        return DanmakuFontSize.MEDIUM;
    }

    public void setDanmakuFontSize(DanmakuFontSize size) {
        setDanmakuFontScale(size.scale);
    }

    /** 弹幕速度系数（原文不夹取，默认 1.0；档位见 {@link DanmakuSpeed}）。 */
    public float danmakuSpeedScale() {
        return store.getFloat(SettingsKeys.DANMAKU_SPEED, 1.0f);
    }

    public void setDanmakuSpeedScale(float scale) {
        store.putFloat(SettingsKeys.DANMAKU_SPEED, scale);
    }

    /** 速度档位反查，找不到档位时回落 NORMAL。 */
    public DanmakuSpeed danmakuSpeed() {
        float value = danmakuSpeedScale();
        for (DanmakuSpeed speed : DanmakuSpeed.values()) {
            if (speed.scale == value) {
                return speed;
            }
        }
        return DanmakuSpeed.NORMAL;
    }

    public void setDanmakuSpeed(DanmakuSpeed speed) {
        setDanmakuSpeedScale(speed.scale);
    }

    /** 弹幕屏蔽总开关（默认 false）。 */
    public boolean danmakuBlockEnabled() {
        return store.getBoolean(SettingsKeys.DANMAKU_BLOCK_ENABLED, false);
    }

    public void setDanmakuBlockEnabled(boolean enabled) {
        store.putBoolean(SettingsKeys.DANMAKU_BLOCK_ENABLED, enabled);
    }

    /**
     * 屏蔽词列表（原构造 409-442 行）：JSON 数组 → 逐项 trim → 去空 → 保序去重。
     * 解析失败记日志并回落空列表。
     */
    public List<String> danmakuBlockWords() {
        String raw = store.getString(SettingsKeys.DANMAKU_BLOCK_WORDS, null);
        if (raw == null) {
            return List.of();
        }
        try {
            Set<String> unique = new LinkedHashSet<>();
            for (String item : json.parseStringArray(raw)) {
                String trimmed = item.trim();
                if (!trimmed.isEmpty()) {
                    unique.add(trimmed);
                }
            }
            return List.copyOf(unique);
        } catch (RuntimeException e) {
            LOG.log(Level.SEVERE, "解析弹幕屏蔽词失败", e);
            return List.of();
        }
    }

    /** 新增屏蔽词（原 {@code m4194a}）：空串/重复（忽略大小写）返回 false。 */
    public boolean addDanmakuBlockWord(String word) {
        String trimmed = word.trim();
        if (trimmed.isEmpty()) {
            return false;
        }
        List<String> words = danmakuBlockWords();
        for (String existing : words) {
            if (existing.equalsIgnoreCase(trimmed)) {
                return false;
            }
        }
        List<String> updated = new ArrayList<>(words);
        updated.add(trimmed);
        saveDanmakuBlockWords(updated);
        return true;
    }

    /** 删除屏蔽词（原 {@code m4217z}）。 */
    public void removeDanmakuBlockWord(String word) {
        List<String> updated = new ArrayList<>();
        for (String existing : danmakuBlockWords()) {
            if (!existing.equals(word)) {
                updated.add(existing);
            }
        }
        saveDanmakuBlockWords(updated);
    }

    /** 覆盖屏蔽词（原 {@code m4216y}）：去空、去重后写回。 */
    public void saveDanmakuBlockWords(List<String> words) {
        List<String> kept = new ArrayList<>();
        for (String word : words) {
            if (word != null && !word.isBlank()) {
                kept.add(word);
            }
        }
        List<String> normalized = distinct(kept);
        store.putString(SettingsKeys.DANMAKU_BLOCK_WORDS, json.writeStringArray(normalized));
    }

    /** 过滤「粉丝团/等级」类弹幕包（默认 true）。 */
    public boolean danmakuFilterPackage() {
        return store.getBoolean(SettingsKeys.DANMAKU_FILTER_PACKAGE, true);
    }

    public void setDanmakuFilterPackage(boolean enabled) {
        store.putBoolean(SettingsKeys.DANMAKU_FILTER_PACKAGE, enabled);
    }

    // ════════════════════════════ remote_config ════════════════════════════

    /**
     * 许可协议是否已同意（原 MainActivity：{@code license_agreed} 为真且
     * {@code license_agreed_version >= license_required_version}）。
     */
    public boolean licenseAgreed(SettingsStore remoteStore) {
        boolean agreed = remoteStore.getBoolean(SettingsKeys.LICENSE_AGREED, false);
        int agreedVersion = remoteStore.getInt(SettingsKeys.LICENSE_AGREED_VERSION, agreed ? 1 : 0);
        int requiredVersion = remoteStore.getInt(SettingsKeys.LICENSE_REQUIRED_VERSION, 1);
        return agreed && agreedVersion >= requiredVersion;
    }

    /** 记录同意（原 {@code C0602eo}：同时写布尔与版本号）。 */
    public void acceptLicense(SettingsStore remoteStore, int version) {
        remoteStore.putBoolean(SettingsKeys.LICENSE_AGREED, true);
        remoteStore.putInt(SettingsKeys.LICENSE_AGREED_VERSION, version);
    }

    // ════════════════════════════ 值类型 ════════════════════════════

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

    // ════════════════════════════ 工具 ════════════════════════════

    static String nz(String value) {
        return value == null ? "" : value;
    }

    static float clamp(float value, float min, float max) {
        return value < min ? min : Math.min(value, max);
    }

    static int clamp(int value, int min, int max) {
        return value < min ? min : Math.min(value, max);
    }

    private static Account findById(List<Account> list, String id) {
        int index = indexOf(list, id);
        return index < 0 ? null : list.get(index);
    }

    private static int indexOf(List<Account> list, String id) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id().equals(id)) {
                return i;
            }
        }
        return -1;
    }

    private static Account findMainOrFirst(List<Account> list) {
        Account main = findById(list, "main");
        if (main != null) {
            return main;
        }
        return list.isEmpty() ? null : list.get(0);
    }

    /** 过滤工具：原文 {@code o70.m8478P0} 之类的去重语义。 */
    static <T> List<T> distinct(List<T> values) {
        return List.copyOf(new LinkedHashSet<>(values));
    }

    /** 便于 UI 层遍历所有账号：id → 展示名（原文 name 字段总等于 id）。 */
    public Map<String, Account> accountsById() {
        LinkedHashMap<String, Account> map = new LinkedHashMap<>();
        for (Account account : accounts()) {
            map.put(account.id(), account);
        }
        return map;
    }
}
