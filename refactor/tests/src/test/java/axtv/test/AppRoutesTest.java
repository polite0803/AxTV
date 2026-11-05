/* Deep link / 路由：AppRoutes（7 项，来自 SmokeTest L70-81）。 */
package axtv.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import axtv.app.AppRoutes;

import org.junit.jupiter.api.Test;

class AppRoutesTest {

    @Test
    void extraNumericIdParsedAndRemoved() {
        Map<String, String> ex = new LinkedHashMap<>();
        ex.put("aweme_id", " 123456 ");
        assertEquals("123456", AppRoutes.parseAwemeId(ex, null));
        assertFalse(ex.containsKey("aweme_id"));
    }

    @Test
    void nonNumericIdRejected() {
        Map<String, String> ex = new LinkedHashMap<>();
        ex.put("aweme_id", "12a34");
        assertNull(AppRoutes.parseAwemeId(ex, null));
    }

    @Test
    void pathRegexVideoDigits() {
        assertEquals("987", AppRoutes.parseAwemeId(new HashMap<>(), "/video/987"));
    }

    @Test
    void extraTakesPriorityOverPath() {
        assertEquals("55", AppRoutes.parseAwemeId(
                new HashMap<>(Map.of("aweme_id", "55")), "/video/987"));
    }

    @Test
    void playMixEncodesId() {
        assertEquals("mix/play?id=a%20b&aweme=123", AppRoutes.playMix("a b", "123"));
    }

    @Test
    void parseRouteArgs() {
        assertEquals("a b", AppRoutes.parse(AppRoutes.playMix("a b", "1")).args().get("id"));
    }

    @Test
    void takeRouteRemovesExtra() {
        Map<String, String> rex = new LinkedHashMap<>(Map.of(AppRoutes.EXTRA_ROUTE, "profile/history"));
        assertEquals("profile/history", AppRoutes.takeRoute(rex));
        assertTrue(rex.isEmpty());
    }
}
