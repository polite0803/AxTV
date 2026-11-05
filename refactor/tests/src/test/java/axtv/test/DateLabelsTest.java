/* 日期标签：DateLabels（5 项，来自 SmokeTest L288-301）。 */
package axtv.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Calendar;

import axtv.ui.DateLabels;

import org.junit.jupiter.api.Test;

class DateLabelsTest {

    private static final long SAME_YEAR;   // 2026-11-05 12:00
    private static final long PREV_YEAR;   // 2024-03-09 12:00

    static {
        Calendar cal = Calendar.getInstance();
        cal.set(2026, Calendar.NOVEMBER, 5, 12, 0, 0);
        SAME_YEAR = cal.getTimeInMillis() / 1000;
        cal.set(2024, Calendar.MARCH, 9, 12, 0, 0);
        PREV_YEAR = cal.getTimeInMillis() / 1000;
    }

    @Test
    void sameYearOmitsYear() {
        assertEquals("11月5日", DateLabels.medium(SAME_YEAR));
    }

    @Test
    void crossYearIncludesYear() {
        assertEquals("2024年3月9日", DateLabels.medium(PREV_YEAR));
    }

    @Test
    void shortLabelZeroPadded() {
        assertEquals("03-09", DateLabels.shortLabel(PREV_YEAR));
    }

    @Test
    void yearMonthIsOneBased() {
        assertEquals(3, DateLabels.yearMonth(PREV_YEAR).month());
        assertEquals(2024, DateLabels.yearMonth(PREV_YEAR).year());
    }

    @Test
    void nonPositiveTimestampReturnsNull() {
        assertNull(DateLabels.medium(0));
        assertNull(DateLabels.shortLabel(-5));
        assertNull(DateLabels.yearMonth(0));
    }
}
