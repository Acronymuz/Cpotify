package com.cpotify.app;

/** Temporary vulnerable characterization; replaced after red tests. */
public final class MediaControlCommand {
    private MediaControlCommand() {}

    public static String toJavaScript(String control) {
        if (control == null) return null;
        switch (control) {
            case "play": return "if (window.MyspotiBridge) window.MyspotiBridge.play();";
            case "pause": return "if (window.MyspotiBridge) window.MyspotiBridge.pause();";
            case "next": return "if (window.MyspotiBridge) window.MyspotiBridge.next();";
            case "prev": return "if (window.MyspotiBridge) window.MyspotiBridge.previous();";
            default:
                if (control.startsWith("seek:")) {
                    String pos = control.substring(5);
                    if (pos.isEmpty()) return null;
                    for (int i = 0; i < pos.length(); i++) {
                        char c = pos.charAt(i);
                        if (c < '0' || c > '9') return null;
                    }
                    return "if (window.MyspotiBridge) window.MyspotiBridge.seek({positionMs: " + pos + "});";
                }
                return null;
        }
    }
}