/* 播放队列：信息流播放器到队尾续页时的 append 语义。 */
package axtv.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import axtv.player.PlaybackQueue;

import java.util.List;

import org.junit.jupiter.api.Test;

class PlaybackQueueTest {

    private static PlaybackQueue queue() {
        return new PlaybackQueue(PlaybackQueue.Item::id);
    }

    private static PlaybackQueue.Item item(String id) {
        return new PlaybackQueue.Item(id, "", 0L);
    }

    @Test
    void appendKeepsCursorAndDeduplicatesById() {
        PlaybackQueue queue = queue();
        queue.replaceAll(List.of(item("a"), item("b")), 0);

        assertEquals(1, queue.append(List.of(item("b"), item("c"))));
        assertEquals(3, queue.items().size());
        assertEquals("a", queue.current().id(), "追加不能移动当前游标");
        assertEquals("b", queue.peekNext().id());

        assertTrue(queue.moveNext());
        assertTrue(queue.moveNext());
        assertEquals("c", queue.current().id());
        assertFalse(queue.hasNext());
    }

    @Test
    void appendEmptyIsNoop() {
        PlaybackQueue queue = queue();
        queue.replaceAll(List.of(item("a")), 0);

        assertEquals(0, queue.append(List.of()));
        assertEquals(0, queue.append(null));
        assertEquals(1, queue.items().size());
    }
}
