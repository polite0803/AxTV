/*
 * 来源: src/app/p000/i42.java (8130 行 —— 首页 Tab 容器 + 退出状态机 + Compose 修饰符工厂)
 * 原职责: 本类覆盖可执行行为 ——
 *         Tab 状态机: 四 Tab 与 ab4 枚举映射(i42:6862-6974 b83("推荐",ab4.f309J) 等)
 *         刷新文案: m5308P(6269-6300) 同 Tab 再触发 → 「正在刷新…」+ 触发对应刷新
 *         双击返回退出: m5305M(6205-6232) 2000ms 窗口 / m5306N(6234) 可退出判定 /
 *                      m5309Q(6312) 提示浮层显示条件 / m5302K(6166-6190) toast 文案 /
 *                      m5304L(6212) 退出序列(stop→finishAndRemoveTask→1s 后清理)
 *         配置切换标签: m9842z 拼接 "长按切换配置 · N 个"(i42:6674) 与 "畅滑短视频"(6728)
 *         其余约半数为 Compose 修饰符工厂(m5287A0 系)，归绘制层。
 * 重构说明: 纯函数状态机；t43=Compose State(布尔/字符串/枚举)，hk3=时间戳。
 */
package axtv.ui;

/** 首页 Tab 容器行为规格（纯函数 + 常量）。 */
public final class HomeTabsSpec {

    // ---- Tab 文案（i42:6862-6974，b83(label, ab4, ...) 逐字） ----
    public static final String TAB_RECOMMEND = "推荐";
    public static final String TAB_FOLLOW = "关注";
    public static final String TAB_FEATURED = "精选";
    public static final String TAB_MOBILE = "移动推荐";

    /** 侧栏入口（i42 其他装配点）。 */
    public static final String TAB_LIVE = "直播";
    public static final String TAB_SEARCH = "搜索";
    public static final String TAB_SETTINGS = "设置";
    public static final String TAB_PROFILE = "个人主页";

    // ---- 刷新文案（m5308P:6269-6300 逐字） ----
    public static final String REFRESH_HOME = "正在刷新首页内容";
    public static final String REFRESH_MOBILE = "正在刷新移动推荐";
    public static final String REFRESH_FEATURED = "正在刷新精选内容";
    public static final String REFRESH_FOLLOW = "正在刷新关注内容";

    // ---- 退出提示（m5302K:6172/6230 逐字） ----
    public static final String MSG_PRESS_AGAIN = "再按一次返回键退出";

    // ---- 配置切换标签（i42:6674 m9842z 拼接 / 6728 二选一） ----
    public static final String LABEL_CONFIG_SWITCH_PREFIX = "长按切换配置 · ";
    public static final String LABEL_CONFIG_SWITCH_SUFFIX = " 个";
    public static final String LABEL_SWIPE_VIDEO = "畅滑短视频";

    // ---- 退出方式枚举（ab4 的关键值，R8 保留名 f308I 等） ----
    public static final int EXIT_FEATURED = 0;    // ab4.f308I = 精选 Tab
    public static final int EXIT_RECOMMEND = 1;   // ab4.f309J = 推荐 Tab
    public static final int EXIT_FOLLOW = 2;      // ab4.f310K = 关注 Tab
    public static final int EXIT_MOBILE = 3;      // ab4.f311L = 移动推荐 Tab
    public static final int EXIT_OTHER_M = 4;     // ab4.f312M

    /** 双击返回时间窗（i42:6210/6222 常量 2000L 毫秒）。 */
    public static final long DOUBLE_BACK_WINDOW_MS = 2000L;
    /** 退出后的延迟清理（m5304L:6198 postDelayed 1000L 毫秒）。 */
    public static final long EXIT_CLEANUP_DELAY_MS = 1000L;

    private HomeTabsSpec() {
    }

    /**
     * m5306N（i42:6234-6240）可退出判定：推荐/关注/移动推荐 → 可；
     * 精选仅在 {@code featuredExitable}（原 z 参数）时可。
     */
    public static boolean exitable(int exitReason, boolean featuredExitable) {
        if (exitReason == EXIT_RECOMMEND || exitReason == EXIT_FOLLOW || exitReason == EXIT_MOBILE) {
            return true;
        }
        return exitReason == EXIT_FEATURED && featuredExitable;
    }

    /**
     * 刷新文案（m5308P:6269-6300）：**同 Tab 再次触发**才显示「正在刷新…」并触发刷新；
     * 不同 Tab 只做切换（返回 null，调用方执行切换）。
     *
     * @param newReason 目标 Tab（EXIT_* 常量）
     * @param oldReason 当前 Tab
     */
    public static String refreshTextIfSameTab(int newReason, int oldReason) {
        if (newReason == EXIT_RECOMMEND && oldReason == EXIT_RECOMMEND) {
            return REFRESH_HOME;
        }
        if (newReason == EXIT_MOBILE && oldReason == EXIT_MOBILE) {
            return REFRESH_MOBILE;
        }
        if (newReason == EXIT_FOLLOW && oldReason == EXIT_FOLLOW) {
            return REFRESH_FOLLOW;
        }
        if (newReason == EXIT_FEATURED && oldReason == EXIT_FEATURED) {
            return REFRESH_FEATURED;
        }
        return null;
    }

    /**
     * 双击返回：是否真正退出（m5305M:6222-6225）——
     * 「双击返回退出」开关关闭 且 距上次返回 &lt; 2000ms → 退出。
     * （开关开启时走确认浮层路径，见 {@link #shouldShowConfirm}。）
     */
    public static boolean shouldExitByDoubleBack(boolean doubleBackEnabled, long now, long lastBackAt) {
        return !doubleBackEnabled && now - lastBackAt < DOUBLE_BACK_WINDOW_MS;
    }

    /**
     * 退出确认浮层显示条件（m5309Q:6312-6319）：未显示过 且 触发方要求显示；
     * 第二个提示位 = {@code trigger} 且 非 doubleBack 开关 且 {@link #exitable}。
     */
    public static ConfirmShow shouldShowConfirm(boolean alreadyVisible, boolean trigger,
                                                boolean doubleBackEnabled, int exitReason,
                                                boolean featuredExitable) {
        if (trigger || alreadyVisible) {
            return new ConfirmShow(false, false);
        }
        boolean secondHint = !doubleBackEnabled && exitable(exitReason, featuredExitable);
        return new ConfirmShow(true, secondHint);
    }

    /** 浮层显示结果（confirmVisible=置 TRUE，secondHint=第二提示位）。 */
    public record ConfirmShow(boolean confirmVisible, boolean secondHint) {
    }

    /**
     * 浮层已显示时再次触发的 toast（m5302K:6171-6173）：
     * 记录时间 + 文案「再按一次返回键退出」。
     */
    public static String pressAgainToast(boolean confirmVisibleAtTrigger) {
        return confirmVisibleAtTrigger ? MSG_PRESS_AGAIN : null;
    }

    /**
     * 配置切换标签（i42:6674/6728）：配置模式显示「长按切换配置 · N 个」，
     * 否则「畅滑短视频」（z2 参数二选一）。
     */
    public static String headerLabel(boolean configMode, int configCount) {
        return configMode
                ? LABEL_CONFIG_SWITCH_PREFIX + configCount + LABEL_CONFIG_SWITCH_SUFFIX
                : LABEL_SWIPE_VIDEO;
    }

    /** 退出序列（m5304L:6212-6203）：返回三步，调用方按序执行。 */
    public static ExitStep[] exitSequence() {
        return new ExitStep[]{ExitStep.STOP_PLAYER, ExitStep.FINISH_TASK, ExitStep.DELAYED_CLEANUP};
    }

    /** 退出序列步骤（原 m5304L + RunnableC0688h 1s 延迟）。 */
    public enum ExitStep {
        /** 全局 ExoPlayer.stop()（wi4.f23545c）。 */
        STOP_PLAYER,
        /** Activity.finishAndRemoveTask()。 */
        FINISH_TASK,
        /** postDelayed(1000ms) 清理（原 RunnableC0688h）。 */
        DELAYED_CLEANUP
    }
}
