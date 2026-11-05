/* 遥控器按键重映射：RemoteKeyMap（5 项，来自 SmokeTest L83-91）。 */
package axtv.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import axtv.app.RemoteKeyMap;

import org.junit.jupiter.api.Test;

class RemoteKeyMapTest {

    @Test
    void mediaKey88RemapsTo19WithZeroScanCode() {
        RemoteKeyMap.KeyEvent k = new RemoteKeyMap.KeyEvent(1, 2, 0, 88, 0, 0, 7, 99, 8);
        RemoteKeyMap.KeyEvent r = RemoteKeyMap.remapForDispatch(k);
        assertNotNull(r);
        assertEquals(19, r.keyCode());
        assertEquals(0, r.scanCode());
        assertEquals(8, r.flags());
    }

    @Test
    void unmatchedKeyReturnsNull() {
        assertNull(RemoteKeyMap.remapForDispatch(
                new RemoteKeyMap.KeyEvent(1, 2, 0, 19, 0, 0, 7, 99, 8)));
    }

    @Test
    void dispatchRemapTableHas7Entries() {
        assertEquals(7, RemoteKeyMap.DISPATCH_REMAP.size());
    }

    @Test
    void focusDirectionTableHas10Entries() {
        assertEquals(10, RemoteKeyMap.FOCUS_DIRECTION.size());
    }

    @Test
    void confirmKeySet() {
        assertTrue(RemoteKeyMap.isConfirm(160));
        assertFalse(RemoteKeyMap.isConfirm(19));
    }
}
