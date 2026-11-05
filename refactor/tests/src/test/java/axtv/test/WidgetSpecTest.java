/* 桌面小组件：WidgetKind / WidgetImageSpec（10 项，来自 SmokeTest L208-231）。 */
package axtv.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import axtv.widget.WidgetImageSpec;
import axtv.widget.WidgetKind;
import axtv.widget.WidgetShape;

import org.junit.jupiter.api.Test;

class WidgetSpecTest {

    @Test
    void fiveKinds() {
        assertEquals(5, WidgetKind.values().length);
    }

    @Test
    void refreshIntervals() {
        assertEquals(360, WidgetKind.HISTORY.getIntervalMinutes());
        assertEquals(30, WidgetKind.LIVE.getIntervalMinutes());
        assertEquals(60, WidgetKind.SEARCH.getIntervalMinutes());
    }

    @Test
    void invalidKindReturnsNull() {
        assertNull(WidgetKind.fromName("NOPE"));
    }

    @Test
    void nowPlayingWideScalesWithDensity() {
        var img = WidgetImageSpec.nowPlayingWide("https://p.test/a.jpg", 2.0f);
        assertEquals(448, img.widthPx);
        assertEquals(252, img.heightPx);
        assertEquals(32.0f, img.cornerPx);
    }

    @Test
    void cacheKeyFormat() {
        var img = WidgetImageSpec.nowPlayingWide("https://p.test/a.jpg", 2.0f);
        assertEquals("https://p.test/a.jpg|448|252|32.0|ROUNDED|null|null", img.cacheKey);
    }

    @Test
    void pixelFloor24() {
        assertEquals(24, WidgetImageSpec.of("u", 4, 4, 0, WidgetShape.ROUNDED, null, null, 1.0f).widthPx);
    }

    @Test
    void solidCacheKeyFormat() {
        assertTrue(WidgetImageSpec.solidCacheKey(96, WidgetShape.ROUNDED, 0xFF112233, 12)
                .equals("solid|96|ROUNDED|2882400000|12")
                || WidgetImageSpec.solidCacheKey(96, WidgetShape.ROUNDED, -16711681, 12)
                .equals("solid|96|ROUNDED|-16711681|12"));
    }

    @Test
    void bitmask224MeansDefaultShapeAndNoColors() {
        assertTrue((224 & WidgetImageSpec.MASK_DEFAULT_SHAPE) != 0);
        assertTrue((224 & WidgetImageSpec.MASK_NO_OUTLINE) != 0);
        assertTrue((224 & WidgetImageSpec.MASK_NO_INNER) != 0);
    }

    @Test
    void centerCropScale() {
        assertEquals(2.0f, WidgetImageSpec.centerCropScale(100, 50, 200, 100));
    }

    @Test
    void blankUrlDetected() {
        assertTrue(WidgetImageSpec.of("  ", 48, 48, 12, WidgetShape.ROUNDED, null, null, 1.0f).isBlankUrl());
    }
}
