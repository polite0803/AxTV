/* 画中画：PipSpec（6 项，来自 SmokeTest L93-105）。 */
package axtv.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import axtv.app.PipSpec;

import org.junit.jupiter.api.Test;

class PipSpecTest {

    @Test
    void videoPlayingDecidesEnterPip() {
        PipSpec.PipState ok = new PipSpec.PipState(true, true, true, true, false, 33, 1.78f);
        assertEquals(PipSpec.Decision.ENTER_PIP, PipSpec.decide(ok));
    }

    @Test
    void paramsUse1780Over1000With3Actions() {
        PipSpec.PipParams pp = PipSpec.params(
                new PipSpec.PipState(true, true, true, true, false, 33, 1.78f));
        assertEquals(1780, pp.aspectRatio().numerator());
        assertEquals(3, pp.actions().size());
        assertEquals(true, pp.autoEnterEnabled());
    }

    @Test
    void aspectRatioClampedToMinimum() {
        assertEquals(470, PipSpec.params(
                new PipSpec.PipState(true, true, true, true, false, 33, 0.1f))
                .aspectRatio().numerator());
    }

    @Test
    void nonVideoStateFallsBackTo16By9() {
        assertEquals(16, PipSpec.params(
                new PipSpec.PipState(true, true, true, true, true, 33, 2.0f))
                .aspectRatio().numerator());
    }

    @Test
    void screenOffDecidesPause() {
        assertEquals(PipSpec.Decision.PAUSE, PipSpec.decide(
                new PipSpec.PipState(true, false, true, true, false, 33, null)));
    }

    @Test
    void sdkBelow26DoesNothing() {
        assertEquals(PipSpec.Decision.NONE, PipSpec.decide(
                new PipSpec.PipState(true, true, true, true, false, 25, null)));
    }
}
