/*
 * 来源: 新建（电视台持久化与连通性，无原版对应）。
 * 原职责: 无。
 * 重构说明: 单测。快照往返逐字节一致；版本不符与缺 id 必须拒收，
 *           否则读不懂的快照会被渲染成「没有频道」的假空态。
 */
package axtv.iptv;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

class CatalogSnapshotTest {

    private static CatalogSnapshot.Entry e(String id, String... urls) {
        return new CatalogSnapshot.Entry(id, id, "", "", "", List.of(urls));
    }

    @Test
    void writeParseWriteIsByteIdentical() {
        CatalogSnapshot snap = CatalogSnapshot.of(1700000000000L, List.of(
            e("cctv1", "http://a/1", "http://b/1"),
            e("cctv5", "http://a/5")
        ));
        String json = snap.write();
        CatalogSnapshot parsed = CatalogSnapshot.parse(json);
        assertEquals(json, parsed.write());
        assertEquals(2, parsed.totalChannels());
        assertEquals("http://a/1", parsed.byId().get("cctv1").candidates().get(0));
        assertEquals("http://b/1", parsed.byId().get("cctv1").candidates().get(1));
    }

    @Test
    void unknownSchemaRejected() {
        String json = CatalogSnapshot.of(1700000000000L, List.of()).write();
        String bumped = json.replace("\"schemaVersion\":1", "\"schemaVersion\":99");
        assertThrows(IllegalArgumentException.class, () -> CatalogSnapshot.parse(bumped));
    }

    @Test
    void blankEntryIdRejected() {
        String json = CatalogSnapshot.of(1700000000000L, List.of()).write();
        String bad = json.replace("\"entries\":[]", "\"entries\":[{\"id\":\"\",\"name\":\"x\",\"logo\":\"\",\"group\":\"\",\"source\":\"\",\"urls\":[]}]");
        assertThrows(IllegalArgumentException.class, () -> CatalogSnapshot.parse(bad));
    }

    @Test
    void corruptJsonRejected() {
        assertThrows(IllegalArgumentException.class, () -> CatalogSnapshot.parse("{oops"));
    }

    @Test
    void missingEntriesRejected() {
        String json = CatalogSnapshot.of(1700000000000L, List.of()).write();
        String bad = json.replace("\"entries\":[]", "");
        assertThrows(IllegalArgumentException.class, () -> CatalogSnapshot.parse(bad));
    }
}