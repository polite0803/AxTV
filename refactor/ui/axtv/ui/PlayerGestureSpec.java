/*
 * 来源: src/app/p000/lq2.java（m7517h 滑杆行 / m7519i 步进辅助 / m7529n 开关行）+
 *       调用方 on5.java / vk5.java / xn2.java / dd0.java / a91.java。
 * 原职责: 播放器菜单内的「数值微调」交互规格 —— 滑杆行（左右键微调、长按加速提示文案在
 *         lq2:20025 逐字出现）、单击/长按两种步长、coerceIn 边界；以及倍速/画质/播放完后
 *         的取值联动。遥控器 KeyEvent 分发状态机不在 lq2（见 NOTES-player-ui.md 存疑清单）。
 * 重构说明: 行为规格模式。计时细节（长按重复间隔）由 Compose repeatable 提供，源码不可见，
 *         规格层只落「按键 → 步长 → 边界钳制」的纯函数与状态字段。
 */
package axtv.ui;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** 播放器菜单数值微调（滑杆行）与遥控器步进的行为规格。 */
public final class PlayerGestureSpec {

    private PlayerGestureSpec() {
    }

    // ==================== 静态提示文案 ====================

    /** 滑杆行固定提示（lq2:20025，12sp，onSurfaceVariant）。 */
    public static final String HINT_ADJUST = "左右键微调，长按加速";

    /**
     * 倍速调节态提示条（dd0:29）：速度值文本（on5.m8694J0 → "2×"）+ " 倍速 · 按住 ← 中"。
     * 逐字保留（含空格与「·」）。语义存疑：句尾「中」疑为截断，见 NOTES。
     */
    public static String speedAdjustPill(String speedText) {
        return speedText + " 倍速 · 按住 ← 中";
    }

    // ==================== 滑杆步进模型（m7517h + m7519i） ====================

    /**
     * 滑杆行规格（m7517h，lq2:19849）：
     * 标题色 ON_SURFACE、样式 f18058O，值文本 14sp ON_SURFACE，
     * 提示 {@link #HINT_ADJUST} 12sp ON_SURFACE_VARIANT，
     * 背景 f11877G + 描边 f11878H（video_menu_slider_bg，lq2:19899），
     * 滑杆区 padding(16,12)（lq2:19989），标题 padding(4,0,4,6)（lq2:19930）。
     * 交互处理器四件套：press / release（单击步长 clickStep，lq2:19949/19961）+
     * click / longClick（长按步长 holdStep，lq2:19970/19982，经 uj3 tag 21/22 包装）。
     */
    public static final class SliderRow {
        private final String label;
        private final String valueText;
        private final float min;
        private final float max;
        private final int steps;       // 滑杆等分段数（vk5:54 传 14；显示区域传 98）
        private final float clickStep; // 单击/单步步长（f2）
        private final float holdStep;  // 长按加速步长（f3）
        private float value;

        public SliderRow(String label, String valueText, float value, float min, float max,
                         int steps, float clickStep, float holdStep) {
            this.label = Objects.requireNonNull(label, "label");
            this.valueText = valueText;
            if (min > max) {
                throw new IllegalArgumentException("min>max");
            }
            this.min = min;
            this.max = max;
            this.steps = steps;
            this.clickStep = clickStep;
            this.holdStep = holdStep;
            this.value = clamp(value);
        }

        public String label() {
            return label;
        }

        public String valueText() {
            return valueText;
        }

        public float value() {
            return value;
        }

        public float min() {
            return min;
        }

        public float max() {
            return max;
        }

        public int steps() {
            return steps;
        }

        public float clickStep() {
            return clickStep;
        }

        public float holdStep() {
            return holdStep;
        }

        /** m7519i（lq2:20205-20207）：newValue = clamp(value + delta)。方向由调用方的加减键决定。 */
        public SliderRow adjust(float delta) {
            return new SliderRow(label, valueText, clamp(value + delta), min, max, steps,
                    clickStep, holdStep);
        }

        /** 单击微调（左右键一次 = ±clickStep）。 */
        public SliderRow click(boolean increase) {
            return adjust(increase ? clickStep : -clickStep);
        }

        /** 长按加速一次迭代（= ±holdStep）。 */
        public SliderRow holdTick(boolean increase) {
            return adjust(increase ? holdStep : -holdStep);
        }

        /** ut0.m10994q（lq2:20027）：clamp(v, min, max)。 */
        public static float clamp(float v, float min, float max) {
            return Math.max(min, Math.min(max, v));
        }

        private float clamp(float v) {
            return clamp(v, min, max);
        }
    }

    /** 预置滑杆行参数表（来源：调用方装配点）。 */
    public static SliderRow danmakuVSpacing(float value, String valueText) {
        return new SliderRow(PlayerMenuSpec.LABEL_V_SPACING, valueText, value,
                PlayerMenuSpec.DANMAKU_SPACING_MIN, PlayerMenuSpec.DANMAKU_SPACING_MAX,
                PlayerMenuSpec.DANMAKU_SPACING_STEPS,
                PlayerMenuSpec.DANMAKU_SPACING_CLICK_STEP, PlayerMenuSpec.DANMAKU_SPACING_HOLD_STEP);
    }

    public static SliderRow danmakuHSpacing(float value, String valueText) {
        return new SliderRow(PlayerMenuSpec.LABEL_H_SPACING, valueText, value,
                PlayerMenuSpec.DANMAKU_SPACING_MIN, PlayerMenuSpec.DANMAKU_SPACING_MAX,
                PlayerMenuSpec.DANMAKU_SPACING_STEPS,
                PlayerMenuSpec.DANMAKU_SPACING_CLICK_STEP, PlayerMenuSpec.DANMAKU_SPACING_HOLD_STEP);
    }

    /** 显示区域滑杆（xn2:117 / vv0:56：label 显示区域，steps 98，clickStep 1.0，holdStep 5.0）。 */
    public static SliderRow displayArea(float value, String valueText, float min, float max) {
        return new SliderRow(PlayerMenuSpec.LABEL_DISPLAY_AREA, valueText, value,
                min, max, 98, 1.0f, 5.0f);
    }

    /** 间距值显示（vk5:46/68）。 */
    public static String spacingText(float v) {
        return String.format(Locale.ROOT, "%.1fx", v);
    }

    // ==================== 倍速 / 画质 / 播放完后 的联动规格 ====================

    /**
     * 倍速段选（ld4:84-92 / vk5:105-113 / on5:56）：取值列表 on5.f16125c，
     * 显示 = 值 + "×"；选中项与当前播放速度联动，点选即应用。
     */
    public static String speedSegmentLabel(float speed) {
        return PlayerMenuSpec.speedLabel(speed);
    }

    /** 倍速合法性（必须在 SPEED_OPTIONS 中，否则拒绝应用）。 */
    public static boolean isValidSpeed(List<Float> options, float speed) {
        return options.contains(speed);
    }

    /**
     * 画质段选（ku3:377 / a91:623 / on5:1445）：lq5 档位，显示与存储 code 分离
     * （480p 显示为 540P，lq5:28）。
     */
    public static String qualityLabel(String code) {
        for (PlayerMenuSpec.Quality q : PlayerMenuSpec.QUALITY_OPTIONS) {
            if (q.code().equals(code)) {
                return q.label();
            }
        }
        throw new IllegalArgumentException("unknown quality code: " + code);
    }

    /** 播放完后（a91:587，图标 ICON_REPLAY）：显示值即存储键（auto_next/pause/replay）。 */
    public static String afterPlaybackValue(String mode) {
        if (!PlayerMenuSpec.AFTER_PLAYBACK_MODES.contains(mode)) {
            throw new IllegalArgumentException("unknown after-playback mode: " + mode);
        }
        return mode;
    }

    // ==================== 快捷胶囊（on5 播放页覆盖层，wy2 快捷项，行为规格） ====================

    /** 快捷项模型（on5:1294-1357，wy2(icon, label, active, onClick, size)）。 */
    public record QuickAction(String icon, String label, boolean active) {
    }

    /**
     * 播放页快捷菜单全表（on5:1294-1357，出现两份 1294-1357 / 2145-2208 为重复装配）：
     * 主页 / 评论 / 不喜欢 / 点赞(↔已点赞，处理中...) / 收藏(↔已收藏) /
     * 弹幕开(↔弹幕关) / 屏蔽词 / 简介 / 外屏放映(↔结束放映)。
     */
    /** 快捷项文案方向：已赞 →「取消点赞」（on5:1575 z33 ? 取消点赞 : 点赞），处理中优先。 */
    public static QuickAction like(boolean processing, boolean liked) {
        return new QuickAction(null, processing ? PlayerMenuSpec.LABEL_PROCESSING
                : (liked ? PlayerMenuSpec.LABEL_UNLIKE : PlayerMenuSpec.LABEL_LIKE), liked);
    }

    public static QuickAction collect(boolean processing, boolean collected) {
        return new QuickAction(null, processing ? PlayerMenuSpec.LABEL_PROCESSING
                : (collected ? PlayerMenuSpec.LABEL_UNCOLLECT : PlayerMenuSpec.LABEL_COLLECT),
                collected);
    }
}
