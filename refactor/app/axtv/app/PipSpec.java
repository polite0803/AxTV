/*
 * 来源: src/app/com/github/mytv/p004dv/MainActivity.java:289-298,301-326,347-429
 * 原职责: 离开 Activity 时进入画中画（PIP）的判定、宽高比计算、三个自定义 action，
 *         PIP 模式变化与回到前台时的播放状态处理
 * 重构说明: PictureInPictureParams / PendingIntent / PowerManager 用 record + 接口隔离；
 *           判定顺序与所有常量逐条还原
 */
package axtv.app;

import java.util.List;

/** 画中画规格与决策。 */
public final class PipSpec {

    public static final String ACTION_PAUSE = "com.github.axtv.dv.PIP_PAUSE";
    public static final String ACTION_PLAY = "com.github.axtv.dv.PIP_PLAY";
    public static final String ACTION_NEXT = "com.github.axtv.dv.PIP_NEXT";

    public static final int DEFAULT_RATIO_NUM = 16;
    public static final int DEFAULT_RATIO_DEN = 9;
    public static final float MIN_RATIO = 0.47f;
    public static final float MAX_RATIO = 2.39f;
    public static final int RATIO_SCALE = 1000;
    public static final int MIN_SDK_PIP = 26;
    public static final int MIN_SDK_AUTO_ENTER = 31;

    /** 一个 RemoteAction。 */
    public record PipAction(String action, int requestCode, String iconResource) {
    }

    /** 三个固定 action，顺序与 MainActivity:404-413 一致。 */
    public static final List<PipAction> ACTIONS = List.of(
            new PipAction(ACTION_PAUSE, 0, "R.drawable.pip_pause"),
            new PipAction(ACTION_PLAY, 1, "R.drawable.pip_play"),
            new PipAction(ACTION_NEXT, 2, "R.drawable.pip_next"));

    /** 有理数宽高比。 */
    public record Rational(int numerator, int denominator) {
    }

    public record PipParams(Rational aspectRatio, List<PipAction> actions, boolean autoEnterEnabled) {
    }

    /** onUserLeaveHint 的三种结局。 */
    public enum Decision {
        /** 不满足条件，什么都不做。 */
        NONE,
        /** 调用 coordinator.pause()（屏幕关闭 / 未在播放 / 进 PIP 抛异常）。 */
        PAUSE,
        /** 调 enterPictureInPictureMode。 */
        ENTER_PIP
    }

    /**
     * 判定输入。
     *
     * @param coordinatorPresent  hu2 是否已注入（{@code f3270j0 != null}）
     * @param screenInteractive   {@code PowerManager.isInteractive()}
     * @param playing             {@code hu2.f8497h.f23797i0} 播放中标记
     * @param playbackStatePresent {@code hu2.f8485M} 非空
     * @param stateIsNonVideo     播放状态等于 {@code ab4.f312M} 时置 true，此时忽略自定义宽高比
     * @param aspectRatio         用户设置的宽高比（{@code hu2.f8482J}），可空或 &lt;= 0
     */
    public record PipState(boolean coordinatorPresent, boolean screenInteractive, boolean playing,
                           boolean playbackStatePresent, boolean stateIsNonVideo,
                           int sdkInt, Float aspectRatio) {
    }

    private PipSpec() {
    }

    /** 精确还原 {@code onUserLeaveHint} 的判定顺序。 */
    public static Decision decide(PipState s) {
        if (!s.coordinatorPresent()) {
            return Decision.NONE;
        }
        if (!s.screenInteractive()) {
            return Decision.PAUSE;
        }
        if (!s.playing() || !s.playbackStatePresent()) {
            return Decision.PAUSE;
        }
        if (s.sdkInt() < MIN_SDK_PIP) {
            return Decision.NONE;
        }
        return Decision.ENTER_PIP;
    }

    /** 构造 PictureInPictureParams；仅在 {@link #decide} 返回 ENTER_PIP 时调用。 */
    public static PipParams params(PipState s) {
        Float ratio = s.stateIsNonVideo() ? null : s.aspectRatio();
        Rational r;
        if (ratio == null || ratio <= 0.0f) {
            r = new Rational(DEFAULT_RATIO_NUM, DEFAULT_RATIO_DEN);
        } else {
            float clamped = clamp(ratio, MIN_RATIO, MAX_RATIO);
            r = new Rational((int) (clamped * RATIO_SCALE), RATIO_SCALE);
        }
        return new PipParams(r, ACTIONS, s.sdkInt() >= MIN_SDK_AUTO_ENTER);
    }

    private static float clamp(float v, float min, float max) {
        return v < min ? min : (v > max ? max : v);
    }

    /** {@code onPictureInPictureModeChanged(boolean)}：把 PIP 状态写回 coordinator。 */
    public static boolean onPipModeChanged(boolean inPip) {
        return inPip;
    }

    /**
     * {@code onResume}：仅当「待恢复播放」标记为 true 且当前未播放时，
     * 清除该标记并把 {@code f8486N} 置 false（避免回到前台自动续播被用户暂停的视频）。
     *
     * @return 是否需要清除恢复标记
     */
    public static boolean shouldClearResumeFlag(boolean resumePending, boolean playing) {
        return resumePending && !playing;
    }
}
