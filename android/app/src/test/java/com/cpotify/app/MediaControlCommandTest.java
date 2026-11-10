package com.cpotify.app;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class MediaControlCommandTest {
    @Test
    public void mapsPlayToBridgeCall() {
        assertEquals("if (window.MyspotiBridge) window.MyspotiBridge.play();",
            MediaControlCommand.toJavaScript("play"));
    }

    @Test
    public void mapsNumericSeekToBridgeCall() {
        assertEquals("if (window.MyspotiBridge) window.MyspotiBridge.seek({positionMs: 123});",
            MediaControlCommand.toJavaScript("seek:123"));
    }

    @Test
    public void rejectsSeekWithJsInjectionPayload() {
        // Break caught: unvalidated seek payload interpolated into evaluateJavascript.
        assertNull(MediaControlCommand.toJavaScript("seek:123);alert(1)//"));
    }

    @Test
    public void rejectsNegativeSeek() {
        assertNull(MediaControlCommand.toJavaScript("seek:-1"));
    }

    @Test
    public void rejectsUnknownControl() {
        assertNull(MediaControlCommand.toJavaScript("eval:alert(1)"));
    }
}