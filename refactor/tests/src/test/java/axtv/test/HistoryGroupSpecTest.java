/* 观看历史按月分组：HistoryGroupSpec（7 项，来自 SmokeTest L456-476）。 */
package axtv.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Map;

import axtv.ui.HistoryGroupSpec;

import org.junit.jupiter.api.Test;

class HistoryGroupSpecTest {

    @Test
    void watchTimeFallbackChain() {
        assertEquals(111L, HistoryGroupSpec.watchTime("a", Map.of("a", 0L), 111L));
        assertEquals(222L, HistoryGroupSpec.watchTime("a", Map.of("a", 222L), 111L));
    }

    @Test
    void compositeGroupKey() {
        assertEquals(202601, HistoryGroupSpec.groupKey(2026, 1));
    }

    @Test
    void flattenByMonthAdjacentGrouping() {
        int[] keys = {202601, 202601, 202512, 202601};
        var entries = HistoryGroupSpec.flattenByMonth(keys, HistoryGroupSpec.countKeys(keys));
        assertEquals(7, entries.size());
        assertEquals("2026 年 1 月", entries.get(0).headerTitle());
        assertEquals(Integer.valueOf(3), entries.get(0).headerCount());
        assertNull(entries.get(0).awemeId());
        assertEquals(true, entries.get(1).awemeId() != null);
    }

    @Test
    void headerLabels() {
        assertEquals("2026 年 1 月", HistoryGroupSpec.headerTitle(2026, 1));
        assertEquals("3 个", HistoryGroupSpec.headerCount(3));
    }

    @Test
    void loadMoreHint() {
        assertEquals("加载到 2025 年 12 月…", HistoryGroupSpec.loadMoreHint(2025, 12));
    }

    @Test
    void monthCellCountLabel() {
        assertEquals("3 个", HistoryGroupSpec.monthCellCount(3));
        assertEquals("未加载", HistoryGroupSpec.monthCellCount(0));
    }

    @Test
    void panelLabels() {
        assertEquals("按日期查找", HistoryGroupSpec.LABEL_FIND_BY_DATE);
        assertEquals("加载失败，点击重试", HistoryGroupSpec.LABEL_LOAD_FAILED_RETRY);
    }
}
