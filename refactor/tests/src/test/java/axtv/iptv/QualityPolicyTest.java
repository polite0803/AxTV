/*
 * 来源: 新建（电视台持久化与连通性，无原版对应）。
 * 原职责: 无。
 * 重构说明: 单测。判据的边界值必须落在 T/8 与 T/3 的 ±1ms 上，而不是拍一个「大概合理」的数。
 */
package axtv.iptv;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QualityPolicyTest {

    @Test
    void effectiveTimeoutSecondsClampsToAtLeastOne() {
        assertEquals(1, QualityPolicy.effectiveTimeoutSeconds(0));
        assertEquals(1, QualityPolicy.effectiveTimeoutSeconds(-5));
        assertEquals(10, QualityPolicy.effectiveTimeoutSeconds(10));
    }

    @Test
    void divisorsDerivedFromTimeout() {
        // T=1：GOOD≤125ms，FAIR≤333ms
        assertEquals(125L, QualityPolicy.goodMaxMs(1));
        assertEquals(333L, QualityPolicy.fairMaxMs(1));
        // T=10：GOOD≤1250，FAIR≤3333
        assertEquals(1250L, QualityPolicy.goodMaxMs(10));
        assertEquals(3333L, QualityPolicy.fairMaxMs(10));
        // T=8：GOOD≤1000，FAIR≤2666
        assertEquals(1000L, QualityPolicy.goodMaxMs(8));
        assertEquals(2666L, QualityPolicy.fairMaxMs(8));
    }

    @Test
    void goodAlwaysLessThanFairForAllReasonableTimeouts() {
        for (int t = 1; t <= 120; t++) {
            assertTrue(QualityPolicy.goodMaxMs(t) < QualityPolicy.fairMaxMs(t),
                "T=" + t + " goodMax=" + QualityPolicy.goodMaxMs(t) + " fairMax=" + QualityPolicy.fairMaxMs(t));
        }
    }

    @Test
    void classifyBoundaries() {
        int t = 10;
        assertEquals(QualityPolicy.Quality.GOOD, QualityPolicy.classify(QualityPolicy.goodMaxMs(t), t));
        assertEquals(QualityPolicy.Quality.FAIR, QualityPolicy.classify(QualityPolicy.goodMaxMs(t) + 1, t));
        assertEquals(QualityPolicy.Quality.FAIR, QualityPolicy.classify(QualityPolicy.fairMaxMs(t), t));
        assertEquals(QualityPolicy.Quality.POOR, QualityPolicy.classify(QualityPolicy.fairMaxMs(t) + 1, t));
    }

    @Test
    void negativeElapsedTreatedAsGood() {
        assertEquals(QualityPolicy.Quality.GOOD, QualityPolicy.classify(-1000L, 10));
    }
}