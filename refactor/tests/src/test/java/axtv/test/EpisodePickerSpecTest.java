/* 选集器：EpisodePickerSpec（7 项 = SmokeTest L195-206 + L519-526）。 */
package axtv.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import axtv.ui.EpisodePickerSpec;

import org.junit.jupiter.api.Test;

class EpisodePickerSpecTest {

    private static EpisodePickerSpec.MixMeta meta() {
        return new EpisodePickerSpec.MixMeta(10, 12, 14, "1.2万", "第 12 集", null, 3);
    }

    @Test
    void watchedDimAlpha() {
        assertEquals(0.6f, EpisodePickerSpec.DIM_ALPHA);
    }

    @Test
    void foregroundFourBranches() {
        assertEquals(EpisodePickerSpec.Foreground.PRIMARY,
                EpisodePickerSpec.foreground(true, false, false));
        assertEquals(EpisodePickerSpec.Foreground.NORMAL,
                EpisodePickerSpec.foreground(false, true, false));
        assertEquals(EpisodePickerSpec.Foreground.ALTERNATE_DIM,
                EpisodePickerSpec.foreground(false, false, true));
        assertEquals(EpisodePickerSpec.Foreground.DIMMED_60,
                EpisodePickerSpec.foreground(false, false, false));
    }

    @Test
    void displayTotalTakesMaxOfThree() {
        assertEquals(14, meta().displayTotal());
    }

    @Test
    void metaLineOrder() {
        assertEquals("共 14 集 · 已看到第 3 集 · 更新至 第 12 集 · 1.2万 次播放",
                EpisodePickerSpec.metaLine(meta()));
    }

    @Test
    void primaryButtonTwoBranches() {
        assertEquals("继续第 3 集", EpisodePickerSpec.primaryButtonLabel(3, 1));
        assertEquals("从第 1 集播放", EpisodePickerSpec.primaryButtonLabel(null, 1));
    }

    @Test
    void loadHintTwoBranches() {
        assertEquals("已加载第 1–20 集 / 共 50 集", EpisodePickerSpec.loadHint(1, 20, 50));
        assertEquals("共 50 集", EpisodePickerSpec.loadHint(0, 0, 50));
    }

    @Test
    void statusLabelLoadingAndPassthrough() {
        assertEquals("正在加载该集…", EpisodePickerSpec.statusLabel(null, true));
        assertEquals("X", EpisodePickerSpec.statusLabel("X", false));
    }
}
