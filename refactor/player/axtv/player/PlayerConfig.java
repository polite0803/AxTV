/*
 * 来源: src/app/p000/ro5.java、sn5.java、pq5.java、ko5.java、mo5.java、MainActivity.java
 * 原职责: 播放器各处散落的魔法数字（超时、重试、缓存、缩放、PIP 边界）
 * 重构说明: 集中为常量表，每个常量注明反编译产物中的取数位置，行为不变
 */
package axtv.player;

/**
 * 播放器常量表。
 *
 * <p>所有数值均直接取自反编译产物，禁止"顺手调优"——这些值决定起播速度、
 * 首帧静音时长、缓存命中率与缩放手感。
 */
public final class PlayerConfig {

    private PlayerConfig() {
    }

    // ---------------------------------------------------------------- 播放器构建

    /** ExoPlayer 重复模式：关闭（{@code ro5.java:35926} {@code setRepeatMode(0)}）。 */
    public static final int REPEAT_MODE_OFF = 0;

    /** 起播不自动播放（{@code ro5.java:35927}）。 */
    public static final boolean PLAY_WHEN_READY_ON_BUILD = false;

    /**
     * 视频渲染器 {@code setWakeMode}/{@code allowedJoiningTimeMs}：
     * {@code sy5.java:20} 构造 {@code MediaCodecVideoRenderer(..., 50)} —— 50ms 允许并入。
     */
    public static final long VIDEO_RENDERER_ALLOWED_JOINING_MS = 50L;

    // ---------------------------------------------------------------- 首帧与静音

    /**
     * 是否"等首帧期间静音"。
     * 来源 {@code ro5.java:1010-1014}：{@code !z12 && !firstFrameFlag} 时 {@code setVolume(0f)}，
     * 首帧渲染后 {@code m9953d} 恢复 {@code setVolume(1f)}。
     */
    public static final float VOLUME_MUTED = 0.0f;

    /** 首帧解锁音量（{@code ro5.java:35895}）。 */
    public static final float VOLUME_UNMUTED = 1.0f;

    /** 播放速度下限（{@code ro5.java:1174} {@code if (f5 < 0.1f) f5 = 0.1f;}）。 */
    public static final float MIN_PLAYBACK_SPEED = 0.1f;

    // ---------------------------------------------------------------- 重试

    /**
     * 同一条目最多重试 1 次。
     * 来源 {@code ko5.java:184-198}：{@code hasRetried} 由 false 置 true 后 {@code prepare()}，
     * 再次失败则上报 {@code onError}。
     */
    public static final int MAX_RETRY_PER_ITEM = 1;

    // ---------------------------------------------------------------- 转场预缓冲

    /**
     * 转场动画期间准备视频的轮询片长（ms）。
     * 来源 {@code ro5.java:36014} {@code rt0.m10020n(16L, ...)}（delay 16ms）。
     */
    public static final long ANIM_PREPARE_SLICE_MS = 16L;

    /**
     * 转场预缓冲最长等待（ms）。
     * 来源 {@code ro5.java:36001} {@code if (exoPlayer2.isPlaying() || j2 >= 500)}：旧片仍在播时
     * 最多等 500ms 再切换 {@code setMediaItem+prepare}，避免打断正在播的画面。
     */
    public static final long ANIM_PREPARE_DEADLINE_MS = 500L;

    /** 转场前首次延迟（{@code i53.java:213} {@code rt0.m10020n(32L, this)}）。 */
    public static final long TRANSITION_START_DELAY_MS = 32L;

    // ---------------------------------------------------------------- 缓存

    /** 缓存目录名（{@code sn5.java:85} {@code new File(cacheDir, "video-media")}）。 */
    public static final String CACHE_DIR_NAME = "video-media";

    /** LRU 上限 96 MiB（{@code sn5.java:85} {@code LeastRecentlyUsedCacheEvictor(100663296L)}）。 */
    public static final long CACHE_MAX_BYTES = 100_663_296L;

    /** 预载头部 2 MiB（{@code sn5.java:108} {@code setLength(2097152L)}）。 */
    public static final long PRELOAD_BYTES = 2_097_152L;

    /**
     * {@code CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR}
     * （{@code sn5.java:76} {@code setFlags(2)}）：缓存/上游出错直接回源，不阻塞播放。
     */
    public static final int CACHE_FLAGS = 2;

    /** 缓存与预载请求使用的固定请求头（{@code sn5.java:47}）。 */
    public static final String CACHE_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36";
    public static final String CACHE_REFERER = "https://www.douyin.com/";
    public static final String CACHE_ACCEPT_LANGUAGE = "zh-CN,zh;q=0.9,en;q=0.8";

    // ---------------------------------------------------------------- 画面缩放

    /** 缩放下限：1.0 = 适配（{@code pq5.java:54} clamp 下界）。 */
    public static final float ZOOM_MIN = 1.0f;

    /** 缩放上限 3.0（{@code pq5.java:54} {@code ut0.m10994q(scale*f, 1.0f, 3.0f)}）。 */
    public static final float ZOOM_MAX = 3.0f;

    /** 判定"已放大"的阈值（{@code pq5.java:70} {@code scale > 1.001f}）。 */
    public static final float ZOOM_ACTIVE_EPSILON = 1.001f;

    /**
     * 双击/点击手势互斥窗口（ms）。
     * 来源 {@code pq5.java:38} {@code f17256c = uptimeMillis() + 400}；
     * 消费处 {@code C1789ww.java:99} {@code if (uptimeMillis() >= f17256c)} 才响应单击。
     */
    public static final long GESTURE_GUARD_MS = 400L;

    /** 点击归位阈值：缩放小于此值视为"未放大"，回 1.0（{@code j16.java:680}）。 */
    public static final float ZOOM_SNAP_THRESHOLD = 1.1f;

    /**
     * SurfaceView 固定缓冲区宽度对齐：{@code (w + 63) & ~63}
     * （{@code ro5.java:35882}），即 64 像素对齐，避免部分 GPU 行跨度过窄。
     */
    public static final int SURFACE_WIDTH_ALIGN = 64;

    // ---------------------------------------------------------------- 画中画

    /** PIP 最小宽高比（{@code MainActivity.java:403} clamp 0.47）。 */
    public static final float PIP_MIN_ASPECT_RATIO = 0.47f;
    /** PIP 最大宽高比（同处 clamp 2.39）。 */
    public static final float PIP_MAX_ASPECT_RATIO = 2.39f;
    /** 宽高比量化分母 1000（同处 {@code new Rational((int)(ratio*1000), 1000)}）。 */
    public static final int PIP_RATIO_DENOMINATOR = 1000;
    /** 未知比例时回退 16:9（同处 {@code new Rational(16, 9)}）。 */
    public static final int PIP_DEFAULT_RATIO_NUM = 16;
    public static final int PIP_DEFAULT_RATIO_DEN = 9;

    /** PIP 最低系统版本（{@code MainActivity.java:383} {@code if (i < 26) return;}）。 */
    public static final int PIP_MIN_SDK = 26;
    /** 自动进入 PIP 需要 SDK 31（{@code MainActivity.java:417}）。 */
    public static final int PIP_AUTO_ENTER_SDK = 31;

    public static final String ACTION_PIP_PAUSE = "com.github.axtv.dv.PIP_PAUSE";
    public static final String ACTION_PIP_PLAY = "com.github.axtv.dv.PIP_PLAY";
    public static final String ACTION_PIP_NEXT = "com.github.axtv.dv.PIP_NEXT";

    // ---------------------------------------------------------------- GL 直通

    /** EGL 上下文版本（{@code mp1.java:26}）。 */
    public static final int GL_CONTEXT_CLIENT_VERSION = 2;
    /** 颜色缓冲位深 RGBA 8/8/8/8，无深度/模板（{@code mp1.java:27}）。 */
    public static final int[] GL_CONFIG_RGBA = {8, 8, 8, 8, 0, 0};
    /** 渲染模式 RENDERMODE_WHEN_DIRTY（{@code mp1.java:29} {@code setRenderMode(1)}）。 */
    public static final int GL_RENDER_MODE_WHEN_DIRTY = 1;
    /** 视频纹理目标 {@code GL_TEXTURE_EXTERNAL_OES}（{@code xn5.java:146} 36197）。 */
    public static final int GL_TEXTURE_EXTERNAL_OES = 0x8D65;
    /** 纹理单元 0（{@code xn5.java:145} 33984）。 */
    public static final int GL_TEXTURE0 = 0x84C0;

    /** 是否对 TPV（飞利浦）机型强制走 output surface workaround（{@code ro5.java:35}）。 */
    public static final String CODEC_WORKAROUND_MANUFACTURER = "TPV";
}
