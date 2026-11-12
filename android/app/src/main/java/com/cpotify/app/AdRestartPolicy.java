package com.cpotify.app;

/** Decides when a sustained, unskippable ad warrants a player reload. */
public final class AdRestartPolicy {
    static final long DEBOUNCE_MS = 4000;
    static final long COOLDOWN_MS = 60000;

    private AdRestartPolicy() {}

    public static boolean shouldRestart(long adFirstSeenAt, long lastRestartAt, long now) {
        if (adFirstSeenAt <= 0) return false;
        if (now - adFirstSeenAt < DEBOUNCE_MS) return false;
        // Persistent cooldown (survives process death) prevents an endless reload loop.
        if (lastRestartAt > 0 && now - lastRestartAt < COOLDOWN_MS) return false;
        return true;
    }
}