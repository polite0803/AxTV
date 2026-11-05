/* TV 频道发布：TvChannelPublisher（10 项，来自 SmokeTest L129-166）。 */
package axtv.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import axtv.model.Aweme;
import axtv.tv.TvChannelPublisher;
import axtv.tv.TvLog;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TvChannelPublisherTest {

    private TestFixtures.FakeProvider fp;
    private TvChannelPublisher pub;

    private static final List<Aweme> THREE = List.of(
            TestFixtures.aweme("1", "A", "u1", "甲", false),
            TestFixtures.aweme("2", "B", "u2", "乙", true),
            TestFixtures.aweme("3", "C", "u3", "丙", false));

    @BeforeEach
    void setUp() {
        fp = new TestFixtures.FakeProvider();
        pub = new TvChannelPublisher(fp, TvLog.noop());
        fp.sdk = 33;
    }

    @Test
    void sdkBelow26Skipped() {
        fp.sdk = 25;
        assertEquals(TvChannelPublisher.Status.SKIPPED,
                pub.sync("axtv_recommend", List.of()).status());
    }

    @Test
    void missingChannelReportsNoChannel() {
        assertEquals(TvChannelPublisher.Status.NO_CHANNEL,
                pub.sync("axtv_recommend", List.of()).status());
    }

    @Test
    void ensureChannelCreatesWithBrowsableAndLogo() throws Exception {
        long cid = pub.ensureChannel("axtv_recommend", true);
        assertEquals(100, cid);
        assertTrue(fp.browsableRequested);
        assertTrue(fp.logoCreated);
    }

    @Test
    void channelDisplayNameIsRecommend() throws Exception {
        long cid = pub.ensureChannel("axtv_recommend", true);
        assertEquals("推荐", fp.channels.get(cid).get("display_name"));
    }

    @Test
    void firstSyncInsertsAll() throws Exception {
        pub.ensureChannel("axtv_recommend", true);
        TvChannelPublisher.SyncResult s1 = pub.sync("axtv_recommend", THREE);
        assertEquals(TvChannelPublisher.Status.OK, s1.status());
        assertEquals(3, s1.wanted());
        assertEquals(3, s1.inserted());
        assertEquals(3, fp.programs.size());
    }

    @Test
    void repeatSyncWritesNothing() throws Exception {
        pub.ensureChannel("axtv_recommend", true);
        pub.sync("axtv_recommend", THREE);
        TvChannelPublisher.SyncResult s2 = pub.sync("axtv_recommend", THREE);
        assertEquals(0, s2.inserted());
        assertEquals(0, s2.updated());
        assertEquals(0, s2.deleted());
    }

    @Test
    void changedFieldTriggersOneUpdate() throws Exception {
        pub.ensureChannel("axtv_recommend", true);
        pub.sync("axtv_recommend", THREE);
        List<Aweme> changed = List.of(
                TestFixtures.aweme("1", "A改了", "u1", "甲", false),
                TestFixtures.aweme("2", "B", "u2", "乙", true),
                TestFixtures.aweme("3", "C", "u3", "丙", false));
        TvChannelPublisher.SyncResult s3 = pub.sync("axtv_recommend", changed);
        assertEquals(1, s3.updated());
        assertEquals(0, s3.inserted());
    }

    @Test
    void extraProgramsDeleted() throws Exception {
        pub.ensureChannel("axtv_recommend", true);
        pub.sync("axtv_recommend", THREE);
        List<Aweme> changed = List.of(
                TestFixtures.aweme("1", "A改了", "u1", "甲", false),
                TestFixtures.aweme("2", "B", "u2", "乙", true),
                TestFixtures.aweme("3", "C", "u3", "丙", false));
        pub.sync("axtv_recommend", changed);
        TvChannelPublisher.SyncResult s4 = pub.sync("axtv_recommend", List.of(changed.get(0)));
        assertEquals(2, s4.deleted());
        assertEquals(1, fp.programs.size());
    }

    @Test
    void emptyListClearsChannel() throws Exception {
        pub.ensureChannel("axtv_recommend", true);
        pub.sync("axtv_recommend", THREE);
        // 与原 SmokeTest 场景一致：先缩减到 1 条，再空列表清空（deleted == 1）
        pub.sync("axtv_recommend", List.of(THREE.get(0)));
        TvChannelPublisher.SyncResult s5 = pub.sync("axtv_recommend", List.of());
        assertEquals(1, s5.deleted());
        assertTrue(fp.programs.isEmpty());
    }

    @Test
    void noTvProviderSkipped() throws Exception {
        pub.ensureChannel("axtv_recommend", true);
        fp.hasProvider = false;
        assertEquals(TvChannelPublisher.Status.SKIPPED,
                pub.sync("axtv_recommend", THREE).status());
    }
}
