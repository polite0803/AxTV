/*
 * 来源: 新建（电视台持久化与连通性，无原版对应）。
 * 原职责: 无。
 * 重构说明: 单测。核心是 R1：未到判死阈值时状态绝不从 UNKNOWN 变 DEAD，
 *           否则首装时整张列表会被判死而变空。
 */
package axtv.iptv;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LivenessRecordTest {

    @Test
    void unknownIsNotHidden() {
        LivenessRecord r = LivenessRecord.unknown("http://x/y.m3u8");
        assertEquals(LivenessRecord.State.UNKNOWN, r.state());
        assertEquals(LivenessRecord.Tier.UNKNOWN, r.tier());
        assertFalse(r.isHidden());
    }

    @Test
    void twoFailuresFromUnknownStayUnknown() {
        LivenessRecord r = LivenessRecord.unknown("http://x/y.m3u8")
            .withFailure(1000L, "timeout")
            .withFailure(2000L, "timeout");
        assertEquals(LivenessRecord.State.UNKNOWN, r.state());
        assertEquals(LivenessRecord.Tier.UNKNOWN, r.tier());
        assertFalse(r.isHidden());
        assertEquals(2, r.consecutiveFailures());
    }

    @Test
    void threeFailuresFromUnknownBecomesDead() {
        LivenessRecord r = LivenessRecord.unknown("http://x/y.m3u8")
            .withFailure(1000L, "e")
            .withFailure(2000L, "e")
            .withFailure(3000L, "e");
        assertEquals(LivenessRecord.State.DEAD, r.state());
        assertEquals(LivenessRecord.Tier.DEAD, r.tier());
        assertTrue(r.isHidden());
    }

    @Test
    void failureFromOkDropsToPoorButNotDead() {
        LivenessRecord r = LivenessRecord.unknown("http://x/y.m3u8")
            .withSuccess(100L, 1000L, 10)
            .withFailure(2000L, "timeout");
        assertEquals(LivenessRecord.State.OK, r.state());
        assertEquals(QualityPolicy.Quality.POOR, r.quality());
        assertEquals(LivenessRecord.Tier.POOR, r.tier());
        assertFalse(r.isHidden());
    }

    @Test
    void successResetsFailureCounter() {
        LivenessRecord r = LivenessRecord.unknown("http://x/y.m3u8")
            .withFailure(1000L, "e")
            .withFailure(2000L, "e")
            .withSuccess(50L, 3000L, 10);
        assertEquals(LivenessRecord.State.OK, r.state());
        assertEquals(0, r.consecutiveFailures());
        assertEquals(QualityPolicy.Quality.GOOD, r.quality());
    }

    @Test
    void successClassifiesQuality() {
        int t = 10;
        assertEquals(QualityPolicy.Quality.GOOD,
            LivenessRecord.unknown("u").withSuccess(100L, 1L, t).quality());
        assertEquals(QualityPolicy.Quality.FAIR,
            LivenessRecord.unknown("u").withSuccess(2000L, 1L, t).quality());
        assertEquals(QualityPolicy.Quality.POOR,
            LivenessRecord.unknown("u").withSuccess(5000L, 1L, t).quality());
    }

    @Test
    void jsonRoundTripIsByteIdentical() {
        LivenessRecord r = new LivenessRecord(
            "http://x/y.m3u8", LivenessRecord.State.OK, QualityPolicy.Quality.FAIR,
            1, 1700000000000L, 1234L, "slow");
        String json = r.toJson();
        LivenessRecord parsed = LivenessRecord.fromJson(json);
        assertEquals(json, parsed.toJson());
        assertEquals("http://x/y.m3u8", parsed.url());
        assertEquals(1, parsed.consecutiveFailures());
    }

    @Test
    void fromJsonHandlesCorruptGracefully() {
        LivenessRecord r = LivenessRecord.fromJson("not json at all");
        assertEquals(LivenessRecord.State.UNKNOWN, r.state());
        assertFalse(r.isHidden());
    }
}