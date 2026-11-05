/* 互动栏：InteractionBarSpec（2 项，来自 SmokeTest L267-272）。 */
package axtv.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import axtv.ui.InteractionBarSpec;

import org.junit.jupiter.api.Test;

class InteractionBarSpecTest {

    @Test
    void likeAndCollectLabelSwitching() {
        assertEquals("已点赞", InteractionBarSpec.like(true, 12000).label());
        assertEquals("点赞", InteractionBarSpec.like(false, 0).label());
        assertEquals("1.2万", InteractionBarSpec.like(true, 12000).count());
        assertEquals("已收藏", InteractionBarSpec.collect(true, 3).label());
    }

    @Test
    void statsRowHasFourItems() {
        assertEquals(4, InteractionBarSpec.statsRow(1, 2, 3, 4).length);
        assertEquals("评论", InteractionBarSpec.statsRow(1, 2, 3, 4)[1].label());
    }
}
