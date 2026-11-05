/* 直播页：LivePageSpec（5 项，来自 SmokeTest L334-343）。 */
package axtv.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import axtv.ui.LivePageSpec;

import org.junit.jupiter.api.Test;

class LivePageSpecTest {

    @Test
    void resolutionLabel() {
        assertEquals("1920×1080", LivePageSpec.resolutionLabel(1920, 1080));
        assertNull(LivePageSpec.resolutionLabel(0, 1080));
    }

    @Test
    void errorOverlayTwoFixedValues() {
        assertEquals("加载超时", LivePageSpec.errorOverlay("加载超时"));
        assertEquals("加载失败", LivePageSpec.errorOverlay("HTTP 403"));
    }

    @Test
    void autoAdvanceToNextLive() {
        var nl = LivePageSpec.nextLive(5, 2, "已播完");
        assertTrue(nl.advance());
        assertEquals(3, nl.nextIndex());
        assertNull(nl.endMessage());
    }

    @Test
    void lastLiveShowsEndMessage() {
        var nlEnd = LivePageSpec.nextLive(5, 4, "已播完");
        assertEquals(false, nlEnd.advance());
        assertEquals("已播完", nlEnd.endMessage());
    }

    @Test
    void viewersLabel() {
        assertEquals("在线人数", LivePageSpec.LABEL_VIEWERS);
    }
}
