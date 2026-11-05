/*
 * 来源: src/app/com/github/mytv/p004dv/MainActivity.java（451 行，全部生命周期方法）
 *       src/app/com/github/mytv/p004dv/widget/WidgetRoutes.java:intent()
 * 原职责: 单 Activity 外壳：字体缩放、冷启动状态丢弃、深链接收、屏幕常亮、
 *         屏幕开关广播、License 状态、方向管理器、系统栏、PIP、按键重映射
 * 重构说明: Activity / Window / BroadcastReceiver / PowerManager 全部收进 Host 接口，
 *           本类只保留决策与顺序，可用假 Host 完整跑通生命周期
 */
package axtv.app;

import java.util.Map;

/** 主 Activity 的生命周期决策模型。 */
public final class MainActivityModel {

    /** Android 侧需要提供的能力。 */
    public interface Host {
        int sdkInt();

        /** 读 SharedPreferences（返回 key → 值）。 */
        Map<String, Object> prefs(String name);

        /** attachBaseContext：用新 fontScale 创建 ConfigurationContext。 */
        void applyFontScale(float scale);

        void setSoftInputMode(int mode);

        void addWindowFlags(int flags);

        void registerScreenOnOffReceiver();

        void unregisterScreenOnOffReceiver();

        /** remote_config 计算出的 License 结果写进全局 StateFlow。 */
        void setLicenseAccepted(boolean accepted);

        void createOrientationManager();

        void setOrientationManagerEnabled(boolean enabled);

        void releaseOrientationManager();

        /** onCreate 末尾启动的两个协程（mt2 / ft2）。 */
        void launchStartupTasks();

        /** m2368v：fullscreen=false → hide(1)，true → show()。 */
        void applySystemBars(boolean fullscreen);

        /** try/catch 包住的 setContent；异常由本类记日志。 */
        void setContent() throws Exception;

        void setPipMode(boolean inPip);

        void clearResumePending(boolean value);

        void pausePlayback();

        void enterPictureInPicture(PipSpec.PipParams params) throws Exception;

        boolean isScreenInteractive();

        boolean isPlaying();

        boolean hasPlaybackState();

        boolean isNonVideoState();

        Float pipAspectRatio();

        /** 把（可能被重映射过的）KeyEvent 交给系统分发。 */
        boolean dispatchKeyEvent(RemoteKeyMap.KeyEvent event);

        void logError(String tag, String message, Throwable error);
    }

    public static final int SOFT_INPUT_MODE = 48;
    public static final int WINDOW_FLAGS = 128;          // FLAG_KEEP_SCREEN_ON
    public static final float FONT_SCALE_MIN = 1.0f;
    public static final float FONT_SCALE_MAX = 1.6f;
    public static final String PREFS_AUTH = "douyin_auth";
    public static final String PREF_APP_FONT_SCALE = "app_font_scale";
    public static final String PREFS_REMOTE_CONFIG = "remote_config";
    public static final String PREF_LICENSE_AGREED = "license_agreed";
    public static final String PREF_LICENSE_AGREED_VERSION = "license_agreed_version";
    public static final String PREF_LICENSE_REQUIRED_VERSION = "license_required_version";
    public static final String TAG = "MainActivity";

    /** 原 {@code MainActivity.f3268p0}：进程内静态标记，用于识别「带 savedInstanceState 的首次启动」。 */
    private static boolean coldLaunchSeen;

    private final Host host;
    /** 三个 StateFlow（f3269i0 全屏 / f3271k0 作品 id / f3272l0 路由）。 */
    private boolean fullscreen;
    private String awemeId;
    private String route;
    private boolean resumePending;
    private boolean inPip;
    private boolean coordinatorPresent = true;

    public MainActivityModel(Host host) {
        this.host = host;
    }

    public String awemeId() {
        return awemeId;
    }

    public String route() {
        return route;
    }

    public boolean isInPip() {
        return inPip;
    }

    public boolean isFullscreen() {
        return fullscreen;
    }

    public void setCoordinatorPresent(boolean present) {
        this.coordinatorPresent = present;
    }

    /** 测试用：复位进程级静态标记。 */
    public static void resetColdLaunchFlag() {
        coldLaunchSeen = false;
    }

    /** {@code attachBaseContext}: 读 douyin_auth/app_font_scale，clamp 到 [1.0, 1.6]。 */
    public void attachBaseContext() {
        Object raw = host.prefs(PREFS_AUTH).get(PREF_APP_FONT_SCALE);
        float scale = raw instanceof Number n ? n.floatValue() : 1.0f;
        scale = clamp(scale, FONT_SCALE_MIN, FONT_SCALE_MAX);
        if (scale != 1.0f) {
            host.applyFontScale(scale);
        }
    }

    /**
     * {@code onCreate(Bundle)}。
     *
     * @param savedInstanceState 非 null 表示系统要恢复状态；首次冷启动时会把它丢弃
     * @param extras             可变 extra 表，命中后会被移除（还原 removeExtra）
     * @param dataPath           Intent.getData().getPath()
     */
    public void onCreate(Object savedInstanceState, Map<String, String> extras, String dataPath) {
        // (bundle == null || f3268p0) ? false : true  →  bundle != null && !f3268p0
        boolean discardSavedState = savedInstanceState != null && !coldLaunchSeen;
        coldLaunchSeen = true;

        attachBaseContext();
        if (discardSavedState) {
            savedInstanceState = null;   // 原实现传给 super.onCreate(null)
        }

        host.setSoftInputMode(SOFT_INPUT_MODE);
        this.awemeId = AppRoutes.parseAwemeId(extras, dataPath);
        this.route = AppRoutes.takeRoute(extras);
        host.addWindowFlags(WINDOW_FLAGS);
        host.registerScreenOnOffReceiver();

        Map<String, Object> remote = host.prefs(PREFS_REMOTE_CONFIG);
        boolean agreed = Boolean.TRUE.equals(remote.get(PREF_LICENSE_AGREED));
        int agreedVersion = intOr(remote.get(PREF_LICENSE_AGREED_VERSION), agreed ? 1 : 0);
        int requiredVersion = intOr(remote.get(PREF_LICENSE_REQUIRED_VERSION), 1);
        host.setLicenseAccepted(agreed && agreedVersion >= requiredVersion);

        host.createOrientationManager();
        host.launchStartupTasks();
        host.applySystemBars(fullscreen);
        try {
            host.setContent();
        } catch (Exception e) {
            host.logError(TAG, "Fatal error in setContent", e);
        }
    }

    /** {@code onNewIntent}: setIntent + 重新解析两个入口，命中后移除 extra。 */
    public void onNewIntent(Map<String, String> extras, String dataPath) {
        this.awemeId = AppRoutes.parseAwemeId(extras, dataPath);
        this.route = AppRoutes.takeRoute(extras);
    }

    public void onStart() {
        host.setOrientationManagerEnabled(true);
    }

    public void onStop() {
        host.setOrientationManagerEnabled(false);
    }

    public void onDestroy() {
        host.releaseOrientationManager();
        host.unregisterScreenOnOffReceiver();
    }

    /** {@code onResume}: 待恢复播放但当前未播放 → 清标记，不自动续播。 */
    public void onResume() {
        if (PipSpec.shouldClearResumeFlag(resumePending, host.isPlaying())) {
            resumePending = false;
            host.clearResumePending(false);
        }
    }

    public void setResumePending(boolean value) {
        this.resumePending = value;
    }

    /** {@code onPictureInPictureModeChanged}: 状态写回 coordinator。 */
    public void onPictureInPictureModeChanged(boolean inPip) {
        this.inPip = inPip;
        host.setPipMode(PipSpec.onPipModeChanged(inPip));
    }

    /** {@code onUserLeaveHint}: 完整 PIP 决策；进 PIP 失败时回落到暂停。 */
    public void onUserLeaveHint() {
        PipSpec.PipState state = new PipSpec.PipState(
                coordinatorPresent,
                host.isScreenInteractive(),
                host.isPlaying(),
                host.hasPlaybackState(),
                host.isNonVideoState(),
                host.sdkInt(),
                host.pipAspectRatio());
        PipSpec.Decision d = PipSpec.decide(state);
        if (d == PipSpec.Decision.PAUSE) {
            host.pausePlayback();
            return;
        }
        if (d == PipSpec.Decision.ENTER_PIP) {
            try {
                host.enterPictureInPicture(PipSpec.params(state));
            } catch (Exception ignored) {
                host.pausePlayback();
            }
        }
    }

    /** {@code dispatchKeyEvent}: 命中 r13.f18571e 时换成新 KeyEvent 再交给系统。 */
    public boolean dispatchKeyEvent(RemoteKeyMap.KeyEvent event) {
        RemoteKeyMap.KeyEvent mapped = RemoteKeyMap.remapForDispatch(event);
        return host.dispatchKeyEvent(mapped == null ? event : mapped);
    }

    private static float clamp(float v, float min, float max) {
        return v < min ? min : (v > max ? max : v);
    }

    private static int intOr(Object o, int fallback) {
        return o instanceof Number n ? n.intValue() : fallback;
    }
}
