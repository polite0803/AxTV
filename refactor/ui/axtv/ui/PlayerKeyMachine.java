/*
 * 来源: src/app/p000/on5.java (反编译自 com.github.axtv.dv 的 R8 混淆类)
 *   辅证: src/app/p000/ki1.java(Compose FocusRequester), oo0.java(延迟焦点恢复协程),
 *         tg0.java(共享协程 lambda, case 1/4), C1850yj.java(case 15)
 * 原职责: 播放页的「返回/焦点」状态机 —— 浮层可见时的焦点路由、浮层关闭后
 *         延迟恢复焦点、视频切换时的运行时状态重置、占位结束日志、可播判定
 * 重构说明: ki1=FocusRequester（m7177a=requestFocus），oo0=delay+requestFocus。
 *         遥控器 KeyEvent 的原始分发不在 on5（on5 全文件无 KeyEvent 引用，
 *         见 NOTES-on5.md §按键），此处落地 on5 实际承载的焦点/返回行为。
 */
package axtv.ui;

import java.util.List;

/** 播放页焦点路由 / 返回处理 / 视频切换状态重置（on5 行为规格）。 */
public final class PlayerKeyMachine {

    // ===== 播放模式（pn5, on5 引用；枚举名取自反编译 static 块 pn5.java:24-27）=====
    public enum PlayerMode {
        /** 详情页播放（合集/相关推荐容器内） */
        DETAIL,
        /** 信息流播放 */
        FEED
    }

    // ===== 焦点恢复延迟（oo0.java 各 case 的 delay 毫秒数）=====
    /** oo0 byte=17（on5.m8695K 关浮层后） */
    public static final int FOCUS_RESTORE_MS_FAST = 50;
    /** oo0 byte=16（on5.m8693J）/ byte=18（on5.m8697L 条件性）/ tg0 case=1 */
    public static final int FOCUS_RESTORE_MS_NORMAL = 80;
    /** oo0 byte=20 默认分支（on5.m8682D0 显示控制层后） */
    public static final int FOCUS_RESTORE_MS_DEFAULT = 100;
    /** oo0 byte=19 */
    public static final int FOCUS_RESTORE_MS_SLOW = 150;

    // ===== seek 恢复（tg0 case=4, tg0.java:479）=====
    /** 挂起 seek 在协程中延迟 500ms 后才落到 ExoPlayer */
    public static final int PENDING_SEEK_APPLY_DELAY_MS = 500;

    private PlayerKeyMachine() {
    }

    /**
     * 返回键焦点路由（on5.m8681D:3231）。
     * 有浮层可见时把焦点交给「浮层关闭目标」，否则交还「播放面焦点」。
     * 原实现整体 try/catch 吞掉 requestFocus 异常（FocusRequester 未初始化等）。
     *
     * @param overlayVisible 任一浮层（菜单/弹幕设置/简介/不喜欢…）可见
     * @return 应 requestFocus 的焦点目标键
     */
    public static String routeBackFocus(boolean overlayVisible, String overlayFocusKey, String surfaceFocusKey) {
        return overlayVisible ? overlayFocusKey : surfaceFocusKey;
    }

    /**
     * 焦点恢复守卫（tg0 case=1, tg0.java:250）。
     * 延迟 80ms 后检查：任一守卫位仍为真（浮层仍在/播放态异常）则放弃恢复。
     * qg5.m9566z / qg5.m9466A 两个守卫均为布尔状态读取（qg5.java:325/7567）。
     */
    public static boolean shouldRestoreFocus(boolean guardOverlayA, boolean guardOverlayB,
                                             boolean guardFlagC, boolean guardFlagD) {
        return !(guardOverlayA || guardOverlayB || guardFlagC || guardFlagD);
    }

    /**
     * 浮层关闭 + 回调 + 延迟恢复焦点（on5.m8693J:4751 / m8695K:4766 / m8697L:4834）。
     * m8693J: visible=false → 回调(false) → 80ms 恢复焦点（byte 16）
     * m8695K: visible=false → 50ms 恢复焦点（byte 17，无回调）
     * m8697L: visible=false → 回调(false) → 仅当 z 时 80ms 恢复焦点（byte 18）
     *
     * @param kind 关闭变体：0=m8693J(80ms,带回调) 1=m8695K(50ms,无回调) 2=m8697L(80ms,条件)
     */
    public static int dismissOverlayRestoreDelay(int kind, boolean condition) {
        return switch (kind) {
            case 1 -> FOCUS_RESTORE_MS_FAST;                     // m8695K
            case 2 -> condition ? FOCUS_RESTORE_MS_NORMAL : -1;  // m8697L: 不恢复则 -1
            default -> FOCUS_RESTORE_MS_NORMAL;                  // m8693J
        };
    }

    // ===== 可播判定（on5.m8701N:4883）=====

    /**
     * 视频是否可播：主播放地址 urlList 非空，或任一码率档 playAddr urlList 非空。
     * Aweme==null 视为不可播（调用侧先判空）。
     *
     * @param mainUrlList     aweme.video.playAddr.urlList
     * @param bitRateUrlLists aweme.video.bitRate[*].playAddr.urlList（可为 null）
     */
    public static boolean hasPlayableUrl(List<String> mainUrlList, List<List<String>> bitRateUrlLists) {
        if (mainUrlList != null && !mainUrlList.isEmpty()) {
            return true;
        }
        if (bitRateUrlLists == null || bitRateUrlLists.isEmpty()) {
            return false;
        }
        for (List<String> urls : bitRateUrlLists) {
            if (urls != null && !urls.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /**
     * 占位结束日志（on5.m8691I:4738，TAG="VideoItem"）。
     * 仅当占位标记当前为 true 时才置 false 并打日志。
     */
    public static String placeholderEndLog(String awemeId, String reason) {
        return "占位结束: awemeId=" + awemeId + " reason=" + reason;
    }

    // ===== 视频切换状态重置（on5.m8767z0:10441，上滑换视频时由信息流调用）=====

    /** 播放页运行时状态（对应 on5.m8767z0 的 18 个状态位入参）。 */
    public static final class PlayerRuntimeState {
        /** 重组触发计数（gk3 int 状态；on5:10443 +1） */
        public int recomposeCounter;
        /** 播放模式（pn5，t43） */
        public PlayerMode mode;
        /** 视频序号推进计数（gk3；on5:10447 由调用方传 +1 后的值） */
        public int episodeTickCounter;
        /** 控制层可见（t43；on5:10449 置 false） */
        public boolean controlsVisible;
        /** 控制层锚定（t43；on5:10450 置 false，m8729g0） */
        public boolean controlsAnchored;
        /** 二级计数（gk3；on5:10452 +1） */
        public int secondaryCounter;
        /** 进度条拖动态（t43；on5:10453 置 false，m8739l0） */
        public boolean scrubbing;
        /** 待应用 seek 位置（t43&lt;Long&gt;；on5:10454 置 null） */
        public Long pendingSeekMs;
        /** 状态位 A/B/C（t43；on5:10455-10457 置 false） */
        public boolean flagA;
        public boolean flagB;
        public boolean flagC;
        /** 当前视频（t43&lt;Aweme&gt;；on5:10459-10462 仅可播时写入，否则 null） */
        public Object currentVideo;
        /** 预载重试计数（gk3；on5:10464-10465 DETAIL 模式且无可播视频时 +1） */
        public int preloadRetryCounter;
    }

    /**
     * 切换视频：按 m8767z0 的次序重置运行时状态。
     *
     * @param playable 当前视频是否可播（m8701N 结果；null 视频视为不可播）
     * @return 本次切换后是否需要预载重试（DETAIL 模式且无可播视频）
     */
    public static boolean applyVideoSwitch(PlayerRuntimeState s, PlayerMode newModeIfDetail,
                                           boolean isDetailWithPlayer, boolean playable, Object video) {
        s.recomposeCounter++;
        s.mode = (isDetailWithPlayer && video != null) ? newModeIfDetail : PlayerMode.FEED;
        s.episodeTickCounter++;
        s.controlsVisible = false;
        s.controlsAnchored = false;
        s.secondaryCounter++;
        s.scrubbing = false;
        s.pendingSeekMs = null;
        s.flagA = false;
        s.flagB = false;
        s.flagC = false;
        s.currentVideo = playable ? video : null;
        boolean retry = s.mode == PlayerMode.DETAIL && s.currentVideo == null;
        if (retry) {
            s.preloadRetryCounter++;
        }
        return retry;
    }

    /**
     * 简介浮层「上/关闭」二段行为（C1850yj case=15, C1850yj.java:297）。
     * 简介正文已滚动（scrollTop&gt;0）→ 先平滑滚回顶部；已在顶部 → 关闭浮层（回调 onClose）。
     */
    public static boolean descriptionOverlayBackAction(int scrollTop, Runnable onClose) {
        if (scrollTop > 0) {
            return true; // 滚回顶部，不关闭
        }
        if (onClose != null) {
            onClose.run();
        }
        return false;
    }
}
