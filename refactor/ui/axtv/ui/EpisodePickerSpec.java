/*
 * 来源: src/app/p000/w03.java（2513 行，选集面板 Composable）
 *       关键行：75（三态颜色，已看 alpha=0.6）、352/2160（标题）、357/657（帮助文案）、
 *       483/783（加载态）、1379/1559/1663（失效态）、2020-2036（元信息行）、
 *       2143/2152（继续/从第 N 集）、2085（未命名合集）、2468（分页提示）
 * 原职责: 合集选集面板的全部行为规格：视觉三态、文案格式、状态分支、点击语义
 * 重构说明: 纯 JDK 规格模型；Compose 单元格绘制交给 Android 侧（见 ui/NOTES.md §10）
 */
package axtv.ui;

import java.util.ArrayList;
import java.util.List;

/** 合集选集面板的行为规格。 */
public final class EpisodePickerSpec {

    /** 已看过的集使用 60% alpha 弱化（w03:75 的 {@code x70.m11833c(color, 0.6f)}）。 */
    public static final float DIM_ALPHA = 0.6f;

    /** 单元格最小高度（w03:78 的 {@code 44.0f} dp）与圆角（12dp）。 */
    public static final float CELL_HEIGHT_DP = 44f;
    public static final float CELL_CORNER_DP = 12f;

    public static final String DEFAULT_MIX_NAME = "未命名合集";
    public static final String HELP_TEXT = "已看过的集弱化显示，上次看到的一集高亮。选中任意一集，从这一集连播。";
    public static final String FAILURE_TITLE = "合集内容已失效";
    public static final String FAILURE_HINT = "请返回重试";
    public static final String LOADING_EPISODE = "正在加载该集…";
    public static final String CANCEL = "取消";
    public static final String MIX_LABEL = "合集";

    /**
     * 单元格前景色的四种取值，对应 w03:70-75 的四个颜色分支。
     * 三个布尔标志的语义在反编译产物里丢失了变量名（见 {@code ui/NOTES.md} S7），
     * 因此这里按**分支结构**而不是按猜测的业务含义命名。
     */
    public enum Foreground {
        /** {@code z3 == true} → {@code k80.f11899d}（高亮色）。 */
        PRIMARY,
        /** {@code z2 == true} → {@code k80.f11914s}（正常色）。 */
        NORMAL,
        /** else 且 {@code z == true} → {@code k80.f11912q}（另一档弱化色）。 */
        ALTERNATE_DIM,
        /** else 且 {@code z == false} → {@code x70.m11833c(f11914s, 0.6f)}（60% alpha）。 */
        DIMMED_60
    }

    /** 面板整体状态。 */
    public enum PanelState {
        /** 合集数据失效：显示「合集内容已失效 / 请返回重试」。 */
        INVALID,
        /** 正在加载某一集：显示「正在加载该集…」。 */
        LOADING_EPISODE,
        /** 正常展示选集网格。 */
        READY
    }

    /**
     * 合集元信息（原 {@code MixInfo.getStatis()}）。
     *
     * @param totalEpisode       总集数
     * @param updatedToEpisode   更新到集数
     * @param currentEpisode     当前连载集数
     * @param playVvFormatted    播放次数，已用 {@code DataModels.formatCount} 格式化
     * @param updatedToText      「更新至」后面的原文（可为 null）
     * @param extraText          附带的其它文案（可为 null）
     * @param lastWatchedEpisode 上次看到的集号（可为 null，表示没看过）
     */
    public record MixMeta(int totalEpisode, int updatedToEpisode, int currentEpisode,
                          String playVvFormatted, String updatedToText, String extraText,
                          Integer lastWatchedEpisode) {

        /** w03:2020 —— 展示用总集数取三个字段的最大值。 */
        public int displayTotal() {
            return Math.max(totalEpisode, Math.max(updatedToEpisode, currentEpisode));
        }
    }

    private EpisodePickerSpec() {
    }

    /**
     * 精确还原 w03:70-75 的颜色分支（不猜测三个布尔的业务含义）。
     * 结合 HELP_TEXT 可推断 {@code primary} 大致对应「上次看到/聚焦」、
     * {@code alternateDim} 与「已看过」相关，但未在反编译里确证。
     */
    public static Foreground foreground(boolean primary, boolean normal, boolean alternateDim) {
        if (primary) {
            return Foreground.PRIMARY;
        }
        if (normal) {
            return Foreground.NORMAL;
        }
        return alternateDim ? Foreground.ALTERNATE_DIM : Foreground.DIMMED_60;
    }

    /**
     * w03:2020-2036 —— 元信息行：把若干段用 {@code " · "} 连接。
     * 顺序固定：共 N 集 → 已看到第 N 集 → 更新至 X → N 次播放 → 附加文案。
     */
    public static String metaLine(MixMeta meta) {
        List<String> parts = new ArrayList<>();
        int total = meta.displayTotal();
        if (total > 0) {
            parts.add("共 " + total + " 集");
        }
        if (meta.lastWatchedEpisode() != null) {
            parts.add("已看到第 " + meta.lastWatchedEpisode() + " 集");
        }
        if (meta.updatedToText() != null) {
            parts.add("更新至 " + meta.updatedToText());
        }
        if (meta.playVvFormatted() != null && !meta.playVvFormatted().isEmpty()) {
            parts.add(meta.playVvFormatted() + " 次播放");
        }
        if (meta.extraText() != null) {
            parts.add(meta.extraText());
        }
        return String.join(" · ", parts);
    }

    /** w03:2160 —— 面板标题。 */
    public static String panelTitle(int episodeCount) {
        return "选集 · 共 " + episodeCount + " 集";
    }

    /** w03:2143/2152 —— 有观看进度显示「继续第 N 集」，否则「从第 N 集播放」。 */
    public static String primaryButtonLabel(Integer lastWatchedEpisode, int startEpisode) {
        return lastWatchedEpisode != null
                ? "继续第 " + lastWatchedEpisode + " 集"
                : "从第 " + startEpisode + " 集播放";
    }

    /**
     * w03:2468 —— 分页加载提示：
     * 已加载区间非空时 {@code 已加载第 from–to 集 / 共 total 集}，否则 {@code 共 total 集}。
     */
    public static String loadHint(int loadedFrom, int loadedTo, int total) {
        return loadedFrom > 0
                ? "已加载第 " + loadedFrom + "–" + loadedTo + " 集 / 共 " + total + " 集"
                : "共 " + total + " 集";
    }

    /**
     * w03:483/783 —— 状态文案：有 status 文本就用它；
     * 否则正在加载该集时显示「正在加载该集…」，其它情况为空串。
     */
    public static String statusLabel(String status, boolean loadingEpisode) {
        if (status != null) {
            return status;
        }
        return loadingEpisode ? LOADING_EPISODE : "";
    }

    /** w03:2085 —— 合集名为空时的回退。 */
    public static String mixName(String raw) {
        return raw == null || raw.isBlank() ? DEFAULT_MIX_NAME : raw;
    }

    /**
     * 点击语义：选中任意一集即「从这一集连播」（HELP_TEXT 的承诺）。
     * 返回该集的起始播放序号，供 {@code PlaybackQueue} 从该位置起播。
     */
    public static int onEpisodeSelected(int episodeIndex) {
        return episodeIndex;
    }
}
