package com.cpotify.app;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NotificationUpdatePolicyTest {
    @Test
    public void firstUpdateAlwaysRebuilds() {
        assertTrue(NotificationUpdatePolicy.shouldRebuild(null, "t|a|art|true", 1000, 0, 5000));
    }

    @Test
    public void identicalMetadataWithinIntervalDoesNotRebuild() {
        // Break caught: rebuilding the notification on every progress tick hides title/artwork on MIUI.
        assertFalse(NotificationUpdatePolicy.shouldRebuild("t|a|art|true", "t|a|art|true", 2000, 1000, 5000));
    }

    @Test
    public void identicalMetadataAfterIntervalRebuildsForProgress() {
        assertTrue(NotificationUpdatePolicy.shouldRebuild("t|a|art|true", "t|a|art|true", 7000, 1000, 5000));
    }

    @Test
    public void metadataChangeRebuildsImmediately() {
        assertTrue(NotificationUpdatePolicy.shouldRebuild("t|a|art|true", "t2|a|art|true", 1500, 1000, 5000));
    }

    @Test
    public void playbackStateChangeRebuildsImmediately() {
        assertTrue(NotificationUpdatePolicy.shouldRebuild("t|a|art|true", "t|a|art|false", 1500, 1000, 5000));
    }
}