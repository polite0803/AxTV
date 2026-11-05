/* 评论区：CommentUiSpec（9 项，来自 SmokeTest L233-266）。 */
package axtv.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import axtv.model.Author;
import axtv.model.Comment;
import axtv.ui.CommentUiSpec;

import org.junit.jupiter.api.Test;

class CommentUiSpecTest {

    private static final long NOW = 1_700_000_000_000L;
    private final SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA);

    private final Author cAuthor = new Author("1", "张三", null, null, null, null, null, 0, 0, 0, 0, 0);
    private final Comment comment = new Comment("123", "内容", cAuthor, 5, 0, 0, "", null, null, 0);

    @Test
    void relativeTimeFiveBranches() {
        assertEquals("刚刚", CommentUiSpec.relativeTime((NOW - 30_000) / 1000, NOW, fmt));
        assertEquals("5分钟前", CommentUiSpec.relativeTime((NOW - 5 * 60_000) / 1000, NOW, fmt));
        assertEquals("3小时前", CommentUiSpec.relativeTime((NOW - 3 * 3_600_000L) / 1000, NOW, fmt));
        assertEquals("2天前", CommentUiSpec.relativeTime((NOW - 2 * 86_400_000L) / 1000, NOW, fmt));
        assertFalse(CommentUiSpec.relativeTime((NOW - 40 * 86_400_000L) / 1000, NOW, fmt).contains("前"));
    }

    @Test
    void nonPositiveCreateTimeHidesTime() {
        assertTrue(CommentUiSpec.relativeTime(0, NOW, fmt).isEmpty());
    }

    @Test
    void anonymousUserFallback() {
        assertEquals("匿名用户", CommentUiSpec.displayName(null));
        assertEquals("匿名用户", CommentUiSpec.displayName("  "));
        assertEquals("小明", CommentUiSpec.displayName("小明"));
    }

    @Test
    void lazyKeyIdFormat() {
        assertEquals("comment:id:3:123:0", CommentUiSpec.lazyKey("comment", comment));
    }

    @Test
    void lazyKeyMissingFormat() {
        Comment anonComment = new Comment("", "hi", cAuthor, 0, 0, 123L, "", null, null, 0);
        assertEquals("comment:missing:1:1:123:2:hi:0", CommentUiSpec.lazyKey("comment", anonComment));
    }

    @Test
    void duplicateKeyGetsCounter() {
        var counter = new HashMap<String, Integer>();
        String k1 = CommentUiSpec.lazyKey("comment", comment, counter);
        String k2 = CommentUiSpec.lazyKey("comment", comment, counter);
        assertTrue(k1.endsWith(":0"));
        assertTrue(k2.endsWith(":1"));
    }

    @Test
    void replyPrefixContainsLength() {
        assertEquals("reply:18:comment:id:3:123:0",
                CommentUiSpec.replyPrefix("comment:id:3:123:0"));
    }

    @Test
    void noSelectionKeepsRepliesCollapsed() {
        var items = CommentUiSpec.flatten(List.of(comment, withReply()),
                null, Set.of(), Set.of(), new HashMap<>());
        assertEquals(2, items.size());
    }

    @Test
    void selectionExpandsOneReplyMarkedAsReply() {
        var items2 = CommentUiSpec.flatten(List.of(comment, withReply()),
                "comment:id:3:789:0", Set.of(), Set.of(), new HashMap<>());
        assertEquals(3, items2.size());
        assertTrue(items2.get(2).isReply());
    }

    private Comment withReply() {
        Comment reply = new Comment("456", "回复", cAuthor, 0, 0, 0, "", null, null, 0);
        return new Comment("789", "主楼", cAuthor, 0, 1, 0, "", null, List.of(reply), 0);
    }
}
