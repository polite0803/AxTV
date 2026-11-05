/* 首页 Tab：HomeTabsSpec（7 项，来自 SmokeTest L427-454）。 */
package axtv.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import axtv.ui.HomeTabsSpec;

import org.junit.jupiter.api.Test;

class HomeTabsSpecTest {

    @Test
    void refreshTextOnlyForSameTab() {
        assertEquals("正在刷新首页内容", HomeTabsSpec.refreshTextIfSameTab(1, 1));
        assertEquals("正在刷新移动推荐", HomeTabsSpec.refreshTextIfSameTab(3, 3));
        assertEquals("正在刷新关注内容", HomeTabsSpec.refreshTextIfSameTab(2, 2));
        assertEquals("正在刷新精选内容", HomeTabsSpec.refreshTextIfSameTab(0, 0));
        assertNull(HomeTabsSpec.refreshTextIfSameTab(1, 0));
    }

    @Test
    void exitableTabs() {
        assertTrue(HomeTabsSpec.exitable(1, false));
        assertTrue(HomeTabsSpec.exitable(2, false));
        assertFalse(HomeTabsSpec.exitable(0, false));
        assertTrue(HomeTabsSpec.exitable(0, true));
    }

    @Test
    void doubleBackWithin2000msWindow() {
        assertTrue(HomeTabsSpec.shouldExitByDoubleBack(false, 5000, 4000));
        assertFalse(HomeTabsSpec.shouldExitByDoubleBack(false, 5000, 2500));
        assertFalse(HomeTabsSpec.shouldExitByDoubleBack(true, 5000, 4000));
    }

    @Test
    void confirmOverlayVisibility() {
        var cs = HomeTabsSpec.shouldShowConfirm(false, false, false, 1, false);
        assertTrue(cs.confirmVisible());
        assertFalse(HomeTabsSpec.shouldShowConfirm(true, false, false, 1, false).confirmVisible());
        assertFalse(HomeTabsSpec.shouldShowConfirm(false, true, false, 1, false).confirmVisible());
    }

    @Test
    void pressAgainToast() {
        assertEquals("再按一次返回键退出", HomeTabsSpec.pressAgainToast(true));
        assertNull(HomeTabsSpec.pressAgainToast(false));
    }

    @Test
    void headerLabelForConfigSwitch() {
        assertEquals("长按切换配置 · 3 个", HomeTabsSpec.headerLabel(true, 3));
        assertEquals("畅滑短视频", HomeTabsSpec.headerLabel(false, 0));
    }

    @Test
    void exitSequenceThreeSteps() {
        assertEquals(3, HomeTabsSpec.exitSequence().length);
        assertEquals(1000L, HomeTabsSpec.EXIT_CLEANUP_DELAY_MS);
    }
}
