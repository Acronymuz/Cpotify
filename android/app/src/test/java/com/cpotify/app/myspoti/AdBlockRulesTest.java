package com.cpotify.app.myspoti;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AdBlockRulesTest {
    @Test
    public void allowsSpotifyClientPlaylistEndpoint() {
        // Break caught: wholesale blocking of spclient.wg.spotify.com kills player state/playback.
        assertFalse(AdBlockRules.isBlocked("https://spclient.wg.spotify.com/playlist/v2/playlist/3D5FqBl49VwgUudMIATEOh"));
    }

    @Test
    public void allowsSpotifyApiTrackEndpoint() {
        // Break caught: wholesale blocking of api.spotify.com kills metadata and library calls.
        assertFalse(AdBlockRules.isBlocked("https://api.spotify.com/v1/tracks/11dFghVXanM5MeGBREMvQp"));
    }

    @Test
    public void allowsOpenPlayerPage() {
        // Break caught: blocking the main player origin prevents the app from loading at all.
        assertFalse(AdBlockRules.isBlocked("https://open.spotify.com/intl-id/album/3D5FqBl49VwgUudMIATEOh"));
    }

    @Test
    public void allowsAudioCdn() {
        // Break caught: blocking audio CDN stops legitimate music streams.
        assertFalse(AdBlockRules.isBlocked("https://audio-fa.scdn.co/audio/11dFghVXanM5MeGBREMvQp"));
    }

    @Test
    public void blocksSpclientAdLogicPath() {
        // Break caught: ad-logic on an essential host must still be blocked by path, not by host.
        assertTrue(AdBlockRules.isBlocked("https://spclient.wg.spotify.com/ad-logic/v1/ads"));
    }

    @Test
    public void blocksSpotifyAudioAdHost() {
        // Break caught: dedicated audio-ad hosts must be blocked.
        assertTrue(AdBlockRules.isBlocked("https://audio-ads.spotify.com/v1/ad"));
    }

    @Test
    public void blocksThirdPartyAdSubdomain() {
        // Break caught: subdomains of ad networks must match by host boundary, not substring.
        assertTrue(AdBlockRules.isBlocked("https://pagead2.googlesyndication.com/pagead/js/ads.js"));
    }

    @Test
    public void doesNotBlockHostContainingAdWordLegitimately() {
        // Break caught: substring matching would block a legitimate host that merely contains an ad-network name.
        assertFalse(AdBlockRules.isBlocked("https://notdoubleclick.net.example.com/music"));
    }
}