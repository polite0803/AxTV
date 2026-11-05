/* 播放器按键机 / 控制层 / Feed 页：PlayerKeyMachine + ControlLayerSpec + FeedPageSpec
 * （11 项，来自 SmokeTest L345-379）。 */
package axtv.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Locale;

import axtv.ui.ControlLayerSpec;
import axtv.ui.FeedPageSpec;
import axtv.ui.PlayerKeyMachine;

import org.junit.jupiter.api.Test;

class PlayerKeyControlFeedTest {

    @Test
    void backKeyRoutesFocus() {
        assertEquals("overlay", PlayerKeyMachine.routeBackFocus(true, "overlay", "surface"));
        assertEquals("surface", PlayerKeyMachine.routeBackFocus(false, "overlay", "surface"));
    }

    @Test
    void playableDetection() {
        assertTrue(PlayerKeyMachine.hasPlayableUrl(List.of("u1"), List.of()));
        assertTrue(PlayerKeyMachine.hasPlayableUrl(
                List.of(""), List.of(List.of(), List.of("u1"))));
        assertFalse(PlayerKeyMachine.hasPlayableUrl(List.of(), List.of(List.of())));
    }

    @Test
    void descriptionOverlayFirstBackOnlyScrolls() {
        // 描述未滚到顶：第一次返回只执行向上滚动，不关闭浮层
        assertTrue(PlayerKeyMachine.descriptionOverlayBackAction(100, () -> {
            throw new AssertionError("不应关闭");
        }));
    }

    @Test
    void descriptionOverlayClosesWhenScrolledToTop() {
        final boolean[] closed = {false};
        assertFalse(PlayerKeyMachine.descriptionOverlayBackAction(0, () -> closed[0] = true));
        assertTrue(closed[0]);
    }

    @Test
    void clockFormatting() {
        assertEquals("1:30", ControlLayerSpec.formatClock(90_000));
        assertEquals("0:05", ControlLayerSpec.formatClock(5_000));
    }

    @Test
    void netSpeedKbAndMbTiers() {
        assertEquals("998KB/s", ControlLayerSpec.formatNetSpeed(998 * 1024L, Locale.ROOT));
        assertEquals("1MB/s", ControlLayerSpec.formatNetSpeed(999 * 1024L, Locale.ROOT));
        assertEquals("1.5MB/s", ControlLayerSpec.formatNetSpeed(1_572_864L, Locale.ROOT));
    }

    @Test
    void publishTimeThresholdAdapts() {
        assertEquals(ControlLayerSpec.formatPublishTime(1_000L),
                ControlLayerSpec.formatPublishTime(1_000_000L));
        assertEquals(ControlLayerSpec.formatPublishTime(999_999L),
                ControlLayerSpec.formatPublishTime(999_999_000L));
    }

    @Test
    void mixTitleText() {
        assertEquals("合集 · 测试合集", FeedPageSpec.mixTitleText("测试合集"));
        assertEquals("合集 · 未命名合集", FeedPageSpec.mixTitleText(null));
    }

    @Test
    void relatedEpisodeLabel() {
        assertEquals("第 5 集", FeedPageSpec.relatedEpisodeText(5));
        assertNull(FeedPageSpec.relatedEpisodeText(0));
    }

    @Test
    void loadMoreText() {
        assertEquals("上滑加载更多", FeedPageSpec.TEXT_LOAD_MORE);
    }

    @Test
    void speedOptionsRange() {
        assertTrue(FeedPageSpec.SPEED_OPTIONS.contains(0.5f));
        assertTrue(FeedPageSpec.SPEED_OPTIONS.contains(3.0f));
    }
}
