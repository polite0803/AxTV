/*
 * 来源: src/app/p000/on5.java (反编译自 com.github.axtv.dv 的 R8 混淆类)
 *   辅证: tg0.java(case 4 seek 恢复), hk3.java(mutableLongState), C1850yj.java
 * 原职责: 播放器控制层 —— 显示/隐藏与计时、重播复位、进度条行(mm:ss + Slider)、
 *         挂起 seek 恢复、时长/日期/网速/分辨率格式化、呼吸与淡入动画参数
 * 重构说明: 纯 Java 行为规格；像素绘制留给 Android 侧 Compose。
 */
package axtv.ui;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 播放器控制层行为规格（on5）。 */
public final class ControlLayerSpec {

    // ===== 时间格式化（on5.m8688G0:3777 —— 进度条/卡片时长均用此）=====
    /** 输入毫秒，输出 "分:秒"，秒两位补零，如 "1:05"（无小时段） */
    public static String formatClock(long millis) {
        long sec = millis / 1000;
        return String.format("%d:%02d", sec / 60, sec % 60);
    }

    // ===== 发布时间（on5.m8690H0:4728）=====
    /**
     * "yyyy-MM-dd HH:mm"，本地时区。
     * 兼容秒/毫秒双单位：小于 MediaPeriodQueue.INITIAL_RENDERER_POSITION_OFFSET_US(1000000)
     * 的值先 ×1000（on5:4729-4731）。
     */
    public static String formatPublishTime(long ts) {
        long v = ts < 1_000_000L ? ts * 1000 : ts;
        return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date(v));
    }

    // ===== 网速（on5.m8742n:6760-6763）=====
    /** 阈值 1022976 字节（=999×1024，on5:6760）；低于则显示 KB/s */
    public static final long NET_SPEED_KB_THRESHOLD_BYTES = 1022976L;
    public static final long NET_SPEED_KB_DIVISOR = 1024L; // RealWebSocket.DEFAULT_MINIMUM_DEFLATE_SIZE

    /** "&lt;1022976 → "(bytes/1024)KB/s"（整数除法）；否则 "#.#" 格式 MB/s */
    public static String formatNetSpeed(long bytesPerSecond, Locale locale) {
        if (bytesPerSecond < NET_SPEED_KB_THRESHOLD_BYTES) {
            return (bytesPerSecond / NET_SPEED_KB_DIVISOR) + "KB/s";
        }
        float mb = (bytesPerSecond / NET_SPEED_KB_DIVISOR) / 1024.0f;
        return new DecimalFormat("#.#", DecimalFormatSymbols.getInstance(locale)).format(mb) + "MB/s";
    }

    // ===== 分辨率 =====

    /** 轨道实际分辨率（on5.m8735j0:6399）：从 ExoPlayer Tracks 选中视频轨取 w×h → "WxH" */
    public static String trackResolutionText(int width, int height) {
        return width > 0 && height > 0 ? width + "x" + height : null;
    }

    /** 浮点显示（on5.m8692I0:4746）：整数值去小数点，如 1.0→"1"、1.5→"1.5" */
    public static String floatToText(float f) {
        return f % 1.0f == 0.0f ? String.valueOf((int) f) : String.valueOf(f);
    }

    /** 倍速显示（on5.m8694J0:4761）：floatToText + "×" */
    public static String speedText(float speed) {
        return floatToText(speed) + "×";
    }

    /** 分辨率串候选（on5.m8696K0:4772）：'x'→'×' 后匹配 "\d{3,4}×\d{3,4}"（on5:4780） */
    private static final Pattern RESOLUTION_PAIR = Pattern.compile("\\d{3,4}×\\d{3,4}");
    /** 无 W×H 时按分隔符切分取首段（on5:4799） */
    private static final Pattern RESOLUTION_SPLIT = Pattern.compile("\\s*[·/|,]\\s*");

    /**
     * 从清晰度描述串提取分辨率段（on5.m8696K0）。
     * 1) 'x'→'×'；2) 命中 W×H 模式 → 取首个匹配；3) 否则按 [·/|,] 切分、
     *    去空白（trim），取第一段非空值；4) 空串/ null → null。
     */
    public static String extractResolutionText(String str) {
        if (str == null || str.isEmpty()) {
            return null;
        }
        String replaced = str.replace('x', '×');
        Matcher m = RESOLUTION_PAIR.matcher(replaced);
        if (m.find()) {
            return m.group();
        }
        String[] parts = RESOLUTION_SPLIT.split(replaced);
        for (String p : parts) {
            String t = p.trim();
            if (!t.isEmpty()) {
                return t;
            }
        }
        return null;
    }

    // ===== 控制层可见性状态机（on5.m8682D0:3243）=====

    /** 控制层运行时状态（对应 m8682D0 的 10 个入参中的可变状态） */
    public static final class ControlState {
        public boolean overlayVisible;        // t43 A（浮层 A，如菜单）
        public boolean overlayBVisible;       // t43 B（浮层 B，如简介）
        /** 进度比例（fk3 Animatable；on5:3247 = position/duration） */
        public float progressRatio;
        /** 控制层可见（t43；on5:3248 置 true） */
        public boolean controlsVisible;
        /** 上次显示时间戳 ms（hk3；on5:3249 = System.currentTimeMillis()） */
        public long lastShowTimestampMs;
        /** 锚定标志（t43；on5:3250 置 false） */
        public boolean anchored;
    }

    /**
     * 显示控制层（on5.m8682D0）。
     * 前置：任一浮层可见 → 直接返回（不打断浮层）。
     * 否则：progressRatio = duration&gt;0 ? position/duration : 0；
     * visible=true；timestamp=now；anchored=false；随后 100ms 恢复焦点
     * （oo0 byte=20 → PlayerKeyMachine.FOCUS_RESTORE_MS_DEFAULT）。
     *
     * @return true=状态已更新（调用方应调度焦点恢复）
     */
    public static boolean showControls(ControlState s, boolean overlayA, boolean overlayB,
                                       long positionMs, long durationMs, long nowMs) {
        s.overlayVisible = overlayA;
        s.overlayBVisible = overlayB;
        if (overlayA || overlayB) {
            return false;
        }
        s.progressRatio = durationMs > 0 ? (float) positionMs / (float) durationMs : 0.0f;
        s.controlsVisible = true;
        s.lastShowTimestampMs = nowMs;
        s.anchored = false;
        return true;
    }

    // ===== 重播/复位（on5.m8684E0:3260）=====

    /**
     * 结束浮层点击「重播」（on5.m8684E0）。
     * 若结束浮层未显示：showRestart=true（on5:3263）并回调 onClose；若已显示：
     * visible=false；positionState=0（on5:3273）；trackQuality=null（on5:3274）；
     * bufferedPosition=0（on5:3275）；pendingSeek=null（on5:3276）；
     * endOverlay=false（on5:3277）；exoPlayer.seekTo(0)+setPlayWhenReady(true)+play()
     * （on5:3278-3289）；关闭来源为手势时回调 onClose（on5:3290-3293）。
     *
     * @param overlayShown 结束浮层当前是否显示
     */
    public static boolean replayReset(PlaybackProgress p, boolean overlayShown,
                                      boolean fromGesture, Runnable onClose) {
        if (!overlayShown) {
            return true; // → 调用方置 showRestart=true 并回调 onClose
        }
        p.positionMs = 0L;
        p.trackQualityText = null;
        p.bufferedPositionMs = 0L;
        p.pendingSeekMs = null;
        p.endOverlayShown = false;
        p.seekToOnPlayer = 0L;
        p.playAfterSeek = true;
        if (fromGesture && onClose != null) {
            onClose.run();
        }
        return false;
    }

    /** 播放进度相关可变状态（hk3×2 + t43 若干，on5.m8684E0/m8767z0 共用） */
    public static final class PlaybackProgress {
        /** 当前播放位置（hk3） */
        public long positionMs;
        /** 已缓冲位置（hk2/hk3） */
        public long bufferedPositionMs;
        /** 实际分辨率 "WxH"（t43&lt;String&gt;，on5.m8735j0 写入） */
        public String trackQualityText;
        /** 挂起 seek 位置（t43&lt;Long&gt;；tg0 case4 消费） */
        public Long pendingSeekMs;
        /** 结束浮层可见 */
        public boolean endOverlayShown;
        /** 复位后要落到 ExoPlayer 的 seek 值（on5:3280 seekTo(0)） */
        public long seekToOnPlayer;
        /** 复位后是否继续播放（on5:3284/3288） */
        public boolean playAfterSeek;
    }

    /**
     * 挂起 seek 落地（tg0 case=4, tg0.java:469-499）。
     * 延迟 500ms 后：exoPlayer.seekTo(pending)；positionState=pending；
     * pendingSeek=null；bufferedPosition=0；某布尔态置 false。
     */
    public static void applyPendingSeek(PlaybackProgress p, ExoPlayerAdapter player) {
        if (p.pendingSeekMs == null) {
            return;
        }
        long pos = p.pendingSeekMs;
        if (player != null) {
            player.seekTo(pos);
        }
        p.positionMs = pos;
        p.pendingSeekMs = null;
        p.bufferedPositionMs = 0L;
    }

    /** ExoPlayer 适配面（Android 侧实现；核心层不绑 media3） */
    public interface ExoPlayerAdapter {
        void seekTo(long positionMs);
    }

    // ===== 动画参数 =====
    /** 「正在播放」呼吸动画：1600ms、2 相位（on5:6808，标签 "nowPlayingPhase"） */
    public static final int NOW_PLAYING_BREATH_MS = 1600;
    public static final int NOW_PLAYING_BREATH_PHASES = 2;
    public static final String NOW_PLAYING_PHASE_LABEL = "nowPlayingPhase";
    /** 占位封面淡入：160ms（on5:6940，标签 "placeholderAlpha"，透明度 0.0/1.0 两态） */
    public static final int PLACEHOLDER_FADE_MS = 160;
    public static final String PLACEHOLDER_ALPHA_LABEL = "placeholderAlpha";
    /** 暂停大图标文案（on5:6901，图标 44dp 居中） */
    public static final String TEXT_PAUSE = "暂停";
}
