package com.cpotify.app;

/**
 * Decides when the media notification must be rebuilt. Metadata/playback-state
 * changes rebuild immediately; pure progress updates rebuild at most once per
 * minIntervalMs so the shade is not flooded with identical notifications.
 */
public final class NotificationUpdatePolicy {
    private NotificationUpdatePolicy() {}

    public static boolean shouldRebuild(String previousKey, String currentKey, long nowMs, long lastNotifyMs, long minIntervalMs) {
        if (previousKey == null || !previousKey.equals(currentKey)) {
            return true;
        }
        return nowMs - lastNotifyMs >= minIntervalMs;
    }
}