/* 预览节目构建：ProgramBuilder（6 项，来自 SmokeTest L115-127）。 */
package axtv.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.stream.IntStream;

import axtv.tv.ProgramBuilder;
import axtv.tv.PreviewProgram;

import org.junit.jupiter.api.Test;

class ProgramBuilderTest {

    @Test
    void programFieldsMapped() {
        PreviewProgram p = ProgramBuilder.fromAweme(100, 0,
                TestFixtures.aweme("111", "标题", "http://img", "作者", true));
        assertNotNull(p);
        assertEquals(30, p.weight());
        assertFalse("（无标题）".equals(p.title()));
        assertEquals("@作者", p.shortDescription());
        assertEquals(4, p.posterArtAspectRatio());
        assertEquals("axtv://douyin/video/111", p.intentUri());
    }

    @Test
    void noCoverDropped() {
        assertNull(ProgramBuilder.fromAweme(100, 0,
                TestFixtures.aweme("111", "标题", null, "作者", false)));
    }

    @Test
    void blankIdDropped() {
        assertNull(ProgramBuilder.fromAweme(100, 0,
                TestFixtures.aweme("  ", "标题", "u", "作者", false)));
    }

    @Test
    void blankTitleFallsBack() {
        assertEquals("（无标题）", ProgramBuilder.fromAweme(100, 0,
                TestFixtures.aweme("111", "  ", "u", "作者", false)).title());
    }

    @Test
    void weightClampedToMinimum() {
        assertEquals(1, ProgramBuilder.fromAweme(100, 40,
                TestFixtures.aweme("111", "t", "u", "作者", false)).weight());
    }

    @Test
    void atMost30Programs() {
        assertEquals(30, ProgramBuilder.buildWanted(100,
                IntStream.range(0, 45)
                        .mapToObj(i -> TestFixtures.aweme("id" + i, "t", "u", "n", false))
                        .toList()).size());
    }
}
