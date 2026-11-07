/*
 * 来源: 新建（电视台持久化与连通性，无原版对应）。
 * 原职责: 无。
 * 重构说明: 单测。分组修正必须活过目录重下（快照保留原始 group，修正只在读取时套用）。
 */
package axtv.iptv;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GroupOverridesTest {

    @Test
    void blankTextParsesToEmpty() {
        assertEquals(0, GroupOverrides.parse("").size());
        assertEquals(0, GroupOverrides.parse("   ").size());
    }

    @Test
    void corruptJsonParsesToEmpty() {
        assertEquals(0, GroupOverrides.parse("{not json").size());
    }

    @Test
    void writeIsByteStable() {
        GroupOverrides g = GroupOverrides.empty().with("cctv1", "央视").with("cctv5", "体育");
        String json = g.write();
        GroupOverrides parsed = GroupOverrides.parse(json);
        assertEquals(json, parsed.write());
        assertEquals("央视", parsed.groupFor("cctv1", "whatever"));
    }

    @Test
    void withBlankGroupRemovesOverride() {
        GroupOverrides g = GroupOverrides.empty().with("cctv1", "央视");
        assertEquals(1, g.size());
        g = g.with("cctv1", "");
        assertEquals(0, g.size());
    }

    @Test
    void groupForFallsBackToRawThenDefault() {
        GroupOverrides g = GroupOverrides.empty();
        assertEquals("未分组", g.groupFor("none", null));
        assertEquals("未分组", g.groupFor("none", "   "));
        assertEquals("新闻", g.groupFor("none", "新闻"));
        // 修正优先于原始 group
        g = g.with("none", "体育");
        assertEquals("体育", g.groupFor("none", "新闻"));
    }
}