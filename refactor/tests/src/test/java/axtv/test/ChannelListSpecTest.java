/* 直播频道列表：ChannelListSpec（8 项，来自 SmokeTest L381-425）。 */
package axtv.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import axtv.ui.ChannelListSpec;

import org.junit.jupiter.api.Test;

class ChannelListSpecTest {

    private static ChannelListSpec.ChannelGroup group(List<Object> videos, boolean loading, String error) {
        return new ChannelListSpec.ChannelGroup("ch", "标题", videos, loading, error, true);
    }

    @Test
    void sectionFiveStates() {
        var g1 = group(List.of("v"), false, null);
        var g2 = group(List.of(), true, null);
        var g3 = group(List.of(), false, "e");
        var g4 = group(List.of(), false, null);
        assertEquals(ChannelListSpec.SectionState.CONTENT, ChannelListSpec.sectionState(g1));
        assertEquals(ChannelListSpec.SectionState.LOADING, ChannelListSpec.sectionState(g2));
        assertEquals(ChannelListSpec.SectionState.ERROR, ChannelListSpec.sectionState(g3));
        assertEquals(ChannelListSpec.SectionState.EMPTY, ChannelListSpec.sectionState(g4));
        assertEquals(ChannelListSpec.SectionState.PLACEHOLDER, ChannelListSpec.sectionState(null));
    }

    @Test
    void featuredPageThreeStates() {
        assertEquals(ChannelListSpec.FeaturedPageState.LOADING,
                ChannelListSpec.featuredPageState(true, false));
        assertEquals(ChannelListSpec.FeaturedPageState.NO_COOKIE,
                ChannelListSpec.featuredPageState(false, false));
        assertEquals(ChannelListSpec.FeaturedPageState.NO_VIDEOS,
                ChannelListSpec.featuredPageState(false, true));
    }

    @Test
    void videoCountText() {
        assertEquals("3 个视频", ChannelListSpec.videoCountText(
                group(List.of(1, 2, 3), false, null)));
        assertNull(ChannelListSpec.videoCountText(group(List.of(), false, null)));
    }

    @Test
    void columnCountMapping() {
        assertEquals(2, ChannelListSpec.columnCount(0));
        assertEquals(3, ChannelListSpec.columnCount(1));
        assertEquals(4, ChannelListSpec.columnCount(2));
    }

    @Test
    void paginationDualThresholds() {
        var bigList = new ArrayList<Object>();
        for (int i = 0; i < 100; i++) bigList.add(i);
        var gBig = group(bigList, false, null);
        var gLoading = group(List.of(), true, null);
        assertTrue(ChannelListSpec.shouldRequestLoadMore(95, 100));
        assertFalse(ChannelListSpec.shouldRequestLoadMore(94, 100));
        assertTrue(ChannelListSpec.repoAcceptsLoadMore(97, gBig));
        assertFalse(ChannelListSpec.repoAcceptsLoadMore(96, gBig));
        assertFalse(ChannelListSpec.repoAcceptsLoadMore(99, gLoading));
    }

    @Test
    void locateAndPrefetch() {
        var g1 = group(List.of("v"), false, null);
        var bigList = new ArrayList<Object>();
        for (int i = 0; i < 100; i++) bigList.add(i);
        var gBig = group(bigList, false, null);
        var g4 = group(List.of(), false, null);
        assertEquals(List.of(0, 1, 2),
                ChannelListSpec.locateAndPrefetch("标题", List.of(g1, gBig, g1, g4)));
        assertTrue(ChannelListSpec.locateAndPrefetch("不存在", List.of(g1)).isEmpty());
    }

    @Test
    void replayCountdownTwoLevels() {
        assertEquals(3_000L, ChannelListSpec.replayRemainingMs(10_000, 7_000));
        assertEquals(2, ChannelListSpec.replayToastLevel(true, 5_000, 1_999));
        assertEquals(1, ChannelListSpec.replayToastLevel(true, 5_000, 3_000));
        assertEquals(0, ChannelListSpec.replayToastLevel(false, 5_000, 1_000));
    }

    @Test
    void sectionPositionText() {
        assertEquals("分区 2 / 5 · ↑↓ 换分区", ChannelListSpec.sectionPositionText(1, 5));
    }
}
