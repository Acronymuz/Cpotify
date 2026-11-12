package com.cpotify.app;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AdRestartPolicyTest {
    @Test
    public void noAdSeenDoesNotRestart() {
        assertFalse(AdRestartPolicy.shouldRestart(0, 0, 10000));
    }

    @Test
    public void adWithinDebounceDoesNotRestart() {
        // Break caught: reloading on a brief ad blip or false positive disrupts playback.
        assertFalse(AdRestartPolicy.shouldRestart(10000, 0, 12000));
    }

    @Test
    public void sustainedAdWithoutPriorRestartRestarts() {
        assertTrue(AdRestartPolicy.shouldRestart(10000, 0, 15000));
    }

    @Test
    public void sustainedAdWithinCooldownDoesNotRestart() {
        // Break caught: a persistent ad must not trigger an endless reload loop.
        assertFalse(AdRestartPolicy.shouldRestart(10000, 14000, 15000));
    }

    @Test
    public void sustainedAdAfterCooldownRestarts() {
        assertTrue(AdRestartPolicy.shouldRestart(10000, 14000, 80000));
    }
}