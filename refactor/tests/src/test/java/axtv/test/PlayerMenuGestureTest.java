/* 播放器菜单与手势：PlayerMenuSpec / PlayerGestureSpec（12 项，来自 SmokeTest L303-332）。 */
package axtv.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import axtv.ui.PlayerGestureSpec;
import axtv.ui.PlayerMenuSpec;

import org.junit.jupiter.api.Test;

class PlayerMenuGestureTest {

    @Test
    void qualityLabels() {
        assertEquals("540P", PlayerGestureSpec.qualityLabel("480p"));
        assertEquals("自动", PlayerGestureSpec.qualityLabel("auto"));
        assertEquals("最高", PlayerGestureSpec.qualityLabel("highest"));
    }

    @Test
    void speedLabelTimesSign() {
        assertEquals("2×", PlayerMenuSpec.speedLabel(2.0f));
    }

    @Test
    void speedValidity() {
        assertTrue(PlayerGestureSpec.isValidSpeed(PlayerMenuSpec.SPEED_OPTIONS, 1.5f));
        assertFalse(PlayerGestureSpec.isValidSpeed(PlayerMenuSpec.SPEED_OPTIONS, 1.7f));
    }

    @Test
    void afterPlaybackValueIsStorageKey() {
        assertEquals("replay", PlayerGestureSpec.afterPlaybackValue("replay"));
    }

    @Test
    void danmakuRowValues() {
        assertEquals("列表样式", PlayerMenuSpec.danmakuRowValue(true, true));
        assertEquals("滚动样式", PlayerMenuSpec.danmakuRowValue(true, false));
        assertNull(PlayerMenuSpec.danmakuRowValue(false, true));
    }

    @Test
    void blockWordCount() {
        assertEquals("3条", PlayerMenuSpec.blockWordValue(3));
    }

    @Test
    void disabledRowDropsOpacity() {
        assertTrue(PlayerMenuSpec.rowForegroundRole(
                new PlayerMenuSpec.RowState(false, false, false, false)).endsWith("@0.7"));
    }

    @Test
    void segmentCorners12And4() {
        assertEquals(12.0f, PlayerMenuSpec.SEGMENT_SELECTED_CORNER_DP);
        assertEquals(4.0f, PlayerMenuSpec.SEGMENT_CORNER_DP);
        assertTrue(PlayerMenuSpec.SegmentRow.isSelected("a", "a"));
    }

    @Test
    void quickActionInProgressPriority() {
        assertEquals("处理中...", PlayerGestureSpec.like(true, false).label());
        assertEquals("取消点赞", PlayerGestureSpec.like(false, true).label());
        assertEquals("取消收藏", PlayerGestureSpec.collect(false, true).label());
    }

    @Test
    void speedAdjustPillText() {
        assertEquals("2× 倍速 · 按住 ← 中", PlayerGestureSpec.speedAdjustPill("2×"));
    }

    @Test
    void spacingTextOneDecimal() {
        assertEquals("1.5x", PlayerGestureSpec.spacingText(1.5f));
    }

    @Test
    void sliderClamps() {
        assertEquals(1.0f, PlayerGestureSpec.SliderRow.clamp(1.5f, 0f, 1f));
        assertEquals(0.0f, PlayerGestureSpec.SliderRow.clamp(-0.5f, 0f, 1f));
    }
}
