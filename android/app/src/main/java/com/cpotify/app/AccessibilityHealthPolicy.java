package com.cpotify.app;

/** Decides when to prompt the user that the resume accessibility service is disabled. */
public final class AccessibilityHealthPolicy {
    static final long COOLDOWN_MS = 300000;

    private AccessibilityHealthPolicy() {}

    public static boolean shouldNotify(boolean serviceReady, long lastNotifiedAt, long now) {
        if (serviceReady) return false;
        // Cooldown prevents re-posting the health prompt on every ad reload.
        if (lastNotifiedAt > 0 && now - lastNotifiedAt < COOLDOWN_MS) return false;
        return true;
    }
}