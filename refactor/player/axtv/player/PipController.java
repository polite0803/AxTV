/*
 * 来源: src/app/com/github/mytv/p004dv/MainActivity.java:370-425（参数与动作构造）、
 *       src/app/p000/AbstractC1317kr.java（API 26 兼容壳）、
 *       src/app/com/github/mytv/p004dv/PipActionReceiver.java（动作分发）
 * 原职责: 进入画中画、设置宽高比与三个遥控动作、接收动作广播
 * 重构说明: 参数计算与动作表抽为纯数据；进入 PIP 由 PipHost（Android）执行
 */
package axtv.player;

import java.util.List;

/**
 * 画中画控制器。
 *
 * <h2>参数（{@code MainActivity.java:400-418}）</h2>
 * <pre>
 * ratio = clamp(videoAspect, 0.47, 2.39)
 * Rational((int)(ratio * 1000), 1000)      // 未知比例时 new Rational(16, 9)
 * actions = [暂停, 播放, 下一个]
 * SDK >= 31: setAutoEnterEnabled(true)
 * </pre>
 *
 * <h2>动作（{@code PipActionReceiver.java}）</h2>
 * <ul>
 *   <li>{@code PIP_PAUSE} / {@code PIP_PLAY} → 播放暂停计数器 +1（两者走同一条 toggle 路径）；</li>
 *   <li>{@code PIP_NEXT} → 置"下一个"标志。</li>
 * </ul>
 *
 * <p>失败兜底：{@code enterPictureInPictureMode} 抛异常时调用宿主的重置逻辑
 * （{@code hu2.m5244z()}），而不是崩溃。
 */
public final class PipController {

    private PipController() {
    }

    /** PIP 遥控动作。 */
    public enum Command {
        /** 暂停/播放共用（原实现两只 action 都落到同一个计数器）。 */
        TOGGLE_PLAYBACK,
        /** 下一个。 */
        NEXT
    }

    /**
     * 一个 RemoteAction。
     *
     * @param requestCode  PendingIntent requestCode（0/1/2）
     * @param action       广播 action 字符串
     * @param title        标题，如"暂停"
     * @param description  无障碍描述，如"暂停播放"
     * @param iconName     drawable 资源名
     * @param command      分发到的命令
     */
    public record Action(int requestCode, String action, String title, String description,
                         String iconName, Command command) {
    }

    /**
     * 一次 PIP 进入请求。
     *
     * @param ratioNum  宽高比分子
     * @param ratioDen  宽高比分母
     * @param autoEnter SDK&gt;=31 时为 true
     * @param actions   遥控动作列表
     */
    public record Params(int ratioNum, int ratioDen, boolean autoEnter, List<Action> actions) {
    }

    /** Android 宿主：负责真正调用系统 API。 */
    public interface PipHost {

        /** {@code enterPictureInPictureMode(params.build())}。 */
        void enterPip(Params params);

        /** 进入失败时的重置（{@code hu2.m5244z()}）。 */
        void onPipEnterFailed();
    }

    /** 三个固定动作，顺序即系统里从左到右的排列（{@code MainActivity.java:404-414}）。 */
    public static List<Action> standardActions() {
        return List.of(
                new Action(0, PlayerConfig.ACTION_PIP_PAUSE, "暂停", "暂停播放",
                        "pip_pause", Command.TOGGLE_PLAYBACK),
                new Action(1, PlayerConfig.ACTION_PIP_PLAY, "播放", "继续播放",
                        "pip_play", Command.TOGGLE_PLAYBACK),
                new Action(2, PlayerConfig.ACTION_PIP_NEXT, "下一个", "播放下一个",
                        "pip_next", Command.NEXT));
    }

    /** 系统是否支持 PIP（{@code MainActivity.java:383}：SDK &lt; 26 直接 return）。 */
    public static boolean isSupported(int sdkInt) {
        return sdkInt >= PlayerConfig.PIP_MIN_SDK;
    }

    /**
     * 计算宽高比。
     *
     * @param aspectRatio 视频宽高比；≤0 或未知（直播等）时用 16:9
     * @return {@code {分子, 分母}}
     */
    public static int[] aspectRatio(float aspectRatio) {
        if (aspectRatio <= 0f || Float.isNaN(aspectRatio)) {
            return new int[]{PlayerConfig.PIP_DEFAULT_RATIO_NUM, PlayerConfig.PIP_DEFAULT_RATIO_DEN};
        }
        float clamped = Math.max(PlayerConfig.PIP_MIN_ASPECT_RATIO,
                Math.min(PlayerConfig.PIP_MAX_ASPECT_RATIO, aspectRatio));
        int num = (int) (clamped * PlayerConfig.PIP_RATIO_DENOMINATOR);
        return new int[]{num, PlayerConfig.PIP_RATIO_DENOMINATOR};
    }

    /**
     * 组装进入参数。
     *
     * @param aspectRatio 视频宽高比，未知传 0
     * @param sdkInt      当前 SDK_INT
     */
    public static Params buildParams(float aspectRatio, int sdkInt) {
        int[] ratio = aspectRatio(aspectRatio);
        return new Params(ratio[0], ratio[1], sdkInt >= PlayerConfig.PIP_AUTO_ENTER_SDK, standardActions());
    }

    /** 把广播 action 映射成命令，未知动作返回 null（接收器直接忽略）。 */
    public static Command commandOf(String action) {
        if (action == null) {
            return null;
        }
        return switch (action) {
            case PlayerConfig.ACTION_PIP_PAUSE, PlayerConfig.ACTION_PIP_PLAY -> Command.TOGGLE_PLAYBACK;
            case PlayerConfig.ACTION_PIP_NEXT -> Command.NEXT;
            default -> null;
        };
    }

    /**
     * 进入画中画，异常时回退。
     *
     * @return true 表示已请求进入；false 表示条件不满足或失败
     */
    public static boolean enter(PipHost host, float aspectRatio, int sdkInt, boolean pipEnabled) {
        if (!pipEnabled || !isSupported(sdkInt)) {
            return false;
        }
        try {
            host.enterPip(buildParams(aspectRatio, sdkInt));
            return true;
        } catch (RuntimeException e) {
            host.onPipEnterFailed();
            return false;
        }
    }
}
