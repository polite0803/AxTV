/* 同步触发器：TvSyncTrigger（5 项，来自 SmokeTest L168-177）。 */
package axtv.test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import axtv.model.Aweme;
import axtv.tv.TvSyncTrigger;

import org.junit.jupiter.api.Test;

class TvSyncTriggerTest {

    private static final List<Aweme> THREE = List.of(
            TestFixtures.aweme("1", "A", "u1", "甲", false),
            TestFixtures.aweme("2", "B", "u2", "乙", true),
            TestFixtures.aweme("3", "C", "u3", "丙", false));

    private TvSyncTrigger readyTrigger() {
        TvSyncTrigger t = new TvSyncTrigger();
        t.setReady(true);
        t.setChannelEnabled(true);
        return t;
    }

    @Test
    void recommendFeedFiresOnce() {
        TvSyncTrigger t = readyTrigger();
        assertNotNull(t.onRecommendFeed(THREE));
        assertNull(t.onRecommendFeed(THREE));
    }

    @Test
    void watchLaterFiresOnEveryChange() {
        TvSyncTrigger t = readyTrigger();
        assertNotNull(t.onWatchLaterChanged(THREE));
        assertNotNull(t.onWatchLaterChanged(List.of()));
    }

    @Test
    void disabledChannelDoesNotFire() {
        TvSyncTrigger t = readyTrigger();
        t.setChannelEnabled(false);
        assertNull(t.onWatchLaterChanged(THREE));
    }

    @Test
    void reEnablingChannelFiresPending() {
        TvSyncTrigger t = readyTrigger();
        t.setChannelEnabled(false);
        t.onWatchLaterChanged(THREE);
        assertNotNull(t.onChannelEnabledChanged(true));
    }

    @Test
    void notReadyDoesNotFire() {
        TvSyncTrigger t = new TvSyncTrigger();
        t.setReady(false);
        t.setChannelEnabled(true);
        assertNull(t.onRecommendFeed(THREE));
    }
}
