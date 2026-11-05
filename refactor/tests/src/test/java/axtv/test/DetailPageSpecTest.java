/* 详情页：DetailPageSpec（4 项，来自 SmokeTest L478-497）。 */
package axtv.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import axtv.model.LiveRoom;
import axtv.ui.DetailPageSpec;

import org.junit.jupiter.api.Test;

class DetailPageSpecTest {

    @Test
    void coverCardAspectTwoOrientations() {
        assertEquals(0.5625f, DetailPageSpec.cardAspect(1080, 1920));
        assertEquals(1.7777778f, DetailPageSpec.cardAspect(1920, 1080));
    }

    @Test
    void durationAsMss() {
        assertEquals("1:30", DetailPageSpec.durationText(90_000L));
        assertNull(DetailPageSpec.durationText(0));
    }

    @Test
    void titleThreeLevelFallback() {
        assertEquals("描述", DetailPageSpec.cardTitle("描述", "作者"));
        assertEquals("作者", DetailPageSpec.cardTitle("", "作者"));
        assertEquals("未命名视频", DetailPageSpec.cardTitle(null, null));
    }

    @Test
    void sectionStatusFourBranches() {
        assertEquals("正在刷新直播间", DetailPageSpec.sectionStatusText(true, false, null));
        assertEquals("正在加载更多直播间", DetailPageSpec.sectionStatusText(false, true, null));
        assertEquals("当前分区暂无直播", DetailPageSpec.sectionStatusText(false, false, null));
        assertEquals("共 1 个直播间", DetailPageSpec.sectionStatusText(false, false,
                new DetailPageSpec.SectionData(
                        new DetailPageSpec.Section("id", "名"),
                        List.of(new LiveRoom(
                                "r", "", "", "", "t", null, null, null, null, null,
                                0, null, null)), true)));
    }
}
