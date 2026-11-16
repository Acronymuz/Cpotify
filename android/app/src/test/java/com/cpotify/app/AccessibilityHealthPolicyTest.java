package com.cpotify.app;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AccessibilityHealthPolicyTest {
    @Test
    public void healthyServiceDoesNotNotify() {
        assertFalse(AccessibilityHealthPolicy.shouldNotify(true, 0, 10000));
    }

    @Test
    public void disabledServiceFirstTimeNotifies() {
        assertTrue(AccessibilityHealthPolicy.shouldNotify(false, 0, 10000));
    }

    @Test
    public void disabledServiceWithinCooldownDoesNotNotify() {
        // Break caught: spamming the health notification on every ad reload.
        assertFalse(AccessibilityHealthPolicy.shouldNotify(false, 10000, 20000));
    }

    @Test
    public void disabledServiceAfterCooldownNotifies() {
        assertTrue(AccessibilityHealthPolicy.shouldNotify(false, 10000, 400000));
    }
}