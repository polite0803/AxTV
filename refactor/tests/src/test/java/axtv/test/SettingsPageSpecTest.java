/* 设置页：SettingsPageSpec（11 项，来自 SmokeTest L179-193）。 */
package axtv.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import axtv.ui.SettingsPageSpec;

import org.junit.jupiter.api.Test;

class SettingsPageSpecTest {

    @Test
    void mainPageHas62Controls() {
        assertEquals(62, SettingsPageSpec.MAIN_PAGE.size());
    }

    @Test
    void danmakuStylePanelHas15Controls() {
        assertEquals(15, SettingsPageSpec.DANMAKU_STYLE_PANEL.size());
    }

    @Test
    void controlTypeResolvable() {
        assertEquals(SettingsPageSpec.ControlType.ANIMATED_ENUM_ROW,
                SettingsPageSpec.ControlType.of("animated_enum_row"));
    }

    @Test
    void controlTypeCount26() {
        assertEquals(26, SettingsPageSpec.ControlType.values().length);
    }

    @Test
    void groupedHas13Groups() {
        assertEquals(13, SettingsPageSpec.grouped().size());
    }

    @Test
    void videoQualityHas5Options() {
        assertEquals(5, SettingsPageSpec.VideoQualityOption.values().length);
    }

    @Test
    void liveQualityHas5Options() {
        assertEquals(5, SettingsPageSpec.LiveQualityOption.values().length);
    }

    @Test
    void p540ShowsAs540P() {
        assertEquals("540P", SettingsPageSpec.VideoQualityOption.P540.shortLabel());
    }

    @Test
    void autoLinksLiveToHighest() {
        var rc = SettingsPageSpec.applyResolutionChange("auto", false);
        assertEquals("auto", rc.videoResolution());
        assertEquals("highest", rc.liveVideoResolution());
    }

    @Test
    void separatelySetLiveDoesNotLink() {
        assertNull(SettingsPageSpec.applyResolutionChange("1080p", true).liveVideoResolution());
    }

    @Test
    void labelsCount37() {
        assertEquals(37, SettingsPageSpec.LABELS.size());
    }
}
