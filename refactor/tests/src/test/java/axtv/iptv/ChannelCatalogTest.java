/*
 * 来源: 新建（电视台持久化与连通性，无原版对应）。
 * 原职责: 无。
 * 重构说明: 单测。原实现的四个缺陷（URL 源空表 / isBuiltIn 由形态推断 / hashCode 排序 /
 *           candidatesForChannel 返回整组）必须全部被本文件证伪过。
 */
package axtv.iptv;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;

class ChannelCatalogTest {

    private static Playlist pl(Channel... chs) {
        return Playlist.success(List.of(chs), 0);
    }

    private static Channel ch(String name, String url, String group) {
        return new Channel(name, "", group, url, Map.of(), "");
    }

    @Test
    void builtInSourcesAreSortedBeforeUserSources() {
        ChannelCatalog.ResolvedSource a = new ChannelCatalog.ResolvedSource("u", "用户", false, pl(ch("CCTV", "http://u/1", "")));
        ChannelCatalog.ResolvedSource b = new ChannelCatalog.ResolvedSource("built", "内置", true, pl(ch("CCTV", "http://b/1", "")));
        CatalogSnapshot snap = ChannelCatalog.merge(List.of(a, b), Set.of());
        List<String> urls = snap.byId().get("cctv").candidates();
        assertEquals(List.of("http://b/1", "http://u/1"), urls);
    }

    @Test
    void disabledBuiltInIdsOnlyAffectBuiltIn() {
        ChannelCatalog.ResolvedSource b = new ChannelCatalog.ResolvedSource("built", "内置", true, pl(ch("CCTV", "http://b/1", "")));
        ChannelCatalog.ResolvedSource u = new ChannelCatalog.ResolvedSource("u", "用户", false, pl(ch("CCTV", "http://u/1", "")));
        CatalogSnapshot snap = ChannelCatalog.merge(List.of(b, u), Set.of("built"));
        assertEquals(List.of("http://u/1"), snap.byId().get("cctv").candidates());
    }

    @Test
    void candidatesForChannelReturnsOnlyThatChannel() {
        ChannelCatalog.ResolvedSource b = new ChannelCatalog.ResolvedSource("b", "内置", true,
            pl(ch("CCTV", "http://b/1", ""), ch("CCTV5", "http://b/5", "")));
        CatalogSnapshot snap = ChannelCatalog.merge(List.of(b), Set.of());
        assertEquals(List.of("http://b/1"), ChannelCatalog.candidatesForChannel(snap, "cctv"));
        assertEquals(List.of("http://b/5"), ChannelCatalog.candidatesForChannel(snap, "cctv5"));
        assertEquals(List.of(), ChannelCatalog.candidatesForChannel(snap, "none"));
    }

    @Test
    void channelHiddenOnlyWhenEveryCandidateDead() {
        Map<String, LivenessRecord> liveness = Map.of(
            "http://dead/1", LivenessRecord.unknown("http://dead/1")
                .withFailure(1L, "e").withFailure(2L, "e").withFailure(3L, "e"),
            "http://ok/1", LivenessRecord.unknown("http://ok/1").withSuccess(10L, 1L, 10)
        );
        // 两个候选：一个死一个活 → 不隐藏
        assertFalse(ChannelCatalog.channelHidden(List.of("http://dead/1", "http://ok/1"), liveness));
        // 单候选全死 → 隐藏
        assertTrue(ChannelCatalog.channelHidden(List.of("http://dead/1"), liveness));
        // 无候选地址 → 不隐藏（无地址不等于连不上）
        assertFalse(ChannelCatalog.channelHidden(List.of(), liveness));
    }

    @Test
    void channelTierUnknownWhenAnyCandidateUnprobed() {
        Map<String, LivenessRecord> liveness = Map.of(
            "http://dead/1", LivenessRecord.unknown("http://dead/1")
                .withFailure(1L, "e").withFailure(2L, "e").withFailure(3L, "e")
        );
        // 另一个候选从未探测过 → 整条仍是 UNKNOWN，不是 DEAD
        assertEquals(LivenessRecord.Tier.UNKNOWN,
            ChannelCatalog.channelTier(List.of("http://dead/1", "http://never/1"), liveness));
    }

    @Test
    void groupByAppliesOverridesAndSorts() {
        ChannelCatalog.ResolvedSource b = new ChannelCatalog.ResolvedSource("b", "内置", true,
            pl(ch("CCTV", "http://b/1", "央视"), ch("CCTV5", "http://b/5", "")));
        CatalogSnapshot snap = ChannelCatalog.merge(List.of(b), Set.of());
        GroupOverrides ov = GroupOverrides.empty().with("cctv5", "体育");
        Map<String, List<CatalogSnapshot.Entry>> groups = ChannelCatalog.groupBy(snap, ov, Map.of(), true);
        assertTrue(groups.containsKey("央视"));
        assertTrue(groups.containsKey("体育"));
        // 组内按展示名排序
        assertEquals("CCTV", groups.get("央视").get(0).name());
    }

    @Test
    void countVisibilitySeparatesProbedUsableHidden() {
        ChannelCatalog.ResolvedSource b = new ChannelCatalog.ResolvedSource("b", "内置", true,
            pl(
                ch("A", "http://a/1", ""),   // 未探测
                ch("B", "http://b/1", ""),   // 3 次失败 → 死
                ch("C", "http://c/1", "")    // 活
            ));
        CatalogSnapshot snap = ChannelCatalog.merge(List.of(b), Set.of());
        Map<String, LivenessRecord> liveness = Map.of(
            "http://a/1", LivenessRecord.unknown("http://a/1"),
            "http://b/1", LivenessRecord.unknown("http://b/1")
                .withFailure(1L, "e").withFailure(2L, "e").withFailure(3L, "e"),
            "http://c/1", LivenessRecord.unknown("http://c/1").withSuccess(50L, 1L, 10)
        );
        ChannelCatalog.Visibility v = ChannelCatalog.countVisibility(snap, liveness);
        assertEquals(3, v.total());
        assertEquals(2, v.probed());   // 死 + 活
        assertEquals(1, v.usable());
        assertEquals(1, v.hidden());
    }

    @Test
    void normalizeStripsResolutionAndFullwidth() {
        assertEquals("cctv-1", ChannelCatalog.normalizeName("CCTV-1[HD]"));
        assertEquals("cctv1", ChannelCatalog.normalizeName(" ＣＣＴＶ１ "));
        assertEquals("cctv-1", ChannelCatalog.normalizeName("CCTV-1[4KHD]"));
        assertEquals("", ChannelCatalog.normalizeName("   "));
    }
}