/*
 * 来源: 新建（电视台持久化与连通性，无原版对应）。
 * 原职责: 无。
 * 重构说明: 单测。双 TTL 判据 + 时钟回拨必须判过期。
 */
package axtv.iptv;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RefreshPolicyTest {

    @Test
    void neverFetchedAlwaysStale() {
        assertTrue(RefreshPolicy.catalogStale(0L, 1700000000000L));
        assertTrue(RefreshPolicy.livenessStale(0L, 1700000000000L));
    }

    @Test
    void clockSkewTreatedAsStale() {
        long now = 1700000000000L;
        // recordedAt 在未来：时钟回拨，宁可多取一次
        assertTrue(RefreshPolicy.catalogStale(now + 3600000L, now));
    }

    @Test
    void freshCatalogNotStale() {
        long now = 1700000000000L;
        long oneHourAgo = now - 3600000L;
        assertFalse(RefreshPolicy.catalogStale(oneHourAgo, now));
    }

    @Test
    void catalogStaleAfterSevenDays() {
        long now = 1700000000000L;
        long eightDaysAgo = now - 8L * 24 * 60 * 60 * 1000;
        long sixDaysAgo = now - 6L * 24 * 60 * 60 * 1000;
        assertTrue(RefreshPolicy.catalogStale(eightDaysAgo, now));
        assertFalse(RefreshPolicy.catalogStale(sixDaysAgo, now));
    }

    @Test
    void livenessStaleAfterTwentyFourHours() {
        long now = 1700000000000L;
        long twentyFiveHoursAgo = now - 25L * 60 * 60 * 1000;
        long twentyThreeHoursAgo = now - 23L * 60 * 60 * 1000;
        assertTrue(RefreshPolicy.livenessStale(twentyFiveHoursAgo, now));
        assertFalse(RefreshPolicy.livenessStale(twentyThreeHoursAgo, now));
    }

    @Test
    void boundaryExactlyAtTtlIsStale() {
        long now = 1700000000000L;
        long exactlyCatalogTtlAgo = now - RefreshPolicy.CATALOG_TTL_MS;
        assertTrue(RefreshPolicy.catalogStale(exactlyCatalogTtlAgo, now));
    }
}