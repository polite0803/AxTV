/* TV Provider 列裁剪：TvProviderColumns（2 项，来自 SmokeTest L107-113）。 */
package axtv.test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;

import axtv.tv.TvProviderColumns;

import org.junit.jupiter.api.Test;

class TvProviderColumnsTest {

    private Map<String, Object> sample() {
        Map<String, Object> vals = new LinkedHashMap<>();
        vals.put("channel_id", 1L);
        vals.put("weight", 30);
        vals.put("title", "t");
        vals.put("browsable", 1);
        return vals;
    }

    @Test
    void sdkBelow26DropsNewerColumns() {
        Map<String, Object> f25 = TvProviderColumns.applySdkFilter(sample(), 25);
        assertFalse(f25.containsKey("channel_id"));
        assertFalse(f25.containsKey("weight"));
        assertFalse(f25.containsKey("browsable"));
        assertTrue(f25.containsKey("title"));
    }

    @Test
    void sdkAtLeast26OnlyDropsBrowsable() {
        assertTrue(TvProviderColumns.applySdkFilter(sample(), 33).containsKey("channel_id"));
    }
}
