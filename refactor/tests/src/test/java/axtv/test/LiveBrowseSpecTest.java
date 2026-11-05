/* 直播浏览/搜索：LiveBrowseSpec（4 项，来自 SmokeTest L498-518）。 */
package axtv.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import axtv.ui.LiveBrowseSpec;

import org.junit.jupiter.api.Test;

class LiveBrowseSpecTest {

    @Test
    void searchBodyStateMachine() {
        assertEquals(LiveBrowseSpec.SearchBody.LOADING,
                LiveBrowseSpec.resolveBody(true, null, "q", "VIDEO", false));
        assertEquals(LiveBrowseSpec.SearchBody.ERROR,
                LiveBrowseSpec.resolveBody(false, "e", "q", "VIDEO", false));
        assertEquals(LiveBrowseSpec.SearchBody.HINT,
                LiveBrowseSpec.resolveBody(false, null, " ", "VIDEO", false));
        assertEquals(LiveBrowseSpec.SearchBody.USER_LIST,
                LiveBrowseSpec.resolveBody(false, null, "q", "USER", false));
        assertEquals(LiveBrowseSpec.SearchBody.LIVE_LIST,
                LiveBrowseSpec.resolveBody(false, null, "q", "LIVE", false));
        assertEquals(LiveBrowseSpec.SearchBody.HINT,
                LiveBrowseSpec.resolveBody(false, null, "q", "VIDEO", true));
    }

    @Test
    void hidePortraitKeepsLandscape() {
        assertTrue(LiveBrowseSpec.keepVideo(true, 1920, 1080));
        assertFalse(LiveBrowseSpec.keepVideo(true, 1080, 1920));
        assertTrue(LiveBrowseSpec.keepVideo(false, 1080, 1920));
    }

    @Test
    void dedupeAndItemKeyFormats() {
        assertEquals("fallback:2:u1:123:3:abc",
                LiveBrowseSpec.dedupeKey("", "u1", 123L, "abc"));
        assertEquals("id:3:abc", LiveBrowseSpec.dedupeKey("abc", "u1", 123L, "abc"));
        assertEquals("result:abc:0", LiveBrowseSpec.itemKey("abc", 0));
    }

    @Test
    void tvCompactColumnCount() {
        assertEquals(2, LiveBrowseSpec.columnCount(0, true));
        assertEquals(4, LiveBrowseSpec.columnCount(2, false));
    }
}
