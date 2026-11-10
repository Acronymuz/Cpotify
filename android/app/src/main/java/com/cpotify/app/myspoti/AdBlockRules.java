package com.cpotify.app.myspoti;

import java.net.URI;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Precise host/path ad blocking for the Spotify Web Player.
 * Essential Spotify endpoints are allowed unless their path is explicitly ad-serving.
 */
public final class AdBlockRules {
    private static final Set<String> AD_HOSTS = new HashSet<>(Arrays.asList(
        "ads.spotify.com", "audio-ads.spotify.com", "audio-ads-eu.spotify.com", "audio-ads-us.spotify.com",
        "doubleclick.net", "googlesyndication.com", "googleadservices.com", "adservice.google.com",
        "amazon-adsystem.com", "ads.tremorhub.com", "adnxs.com", "rubiconproject.com",
        "pubmatic.com", "openx.net", "criteo.com", "spotxchange.com", "moatads.com",
        "serving-sys.com", "mediamath.com", "turn.com", "casalemedia.com", "advertising.com",
        "adtechus.com", "adroll.com", "quantserve.com", "scorecardresearch.com", "outbrain.com",
        "taboola.com", "revcontent.com", "mgid.com", "popads.net", "propellerads.com", "adsterra.com",
        "connect.facebook.net", "bat.bing.com", "c.bing.com", "ads.yahoo.com", "adserver.yahoo.com",
        "analytics.twitter.com", "ads.tiktok.com", "unityads.unity3d.com", "applovin.com",
        "ironsrc.com", "vungle.com", "chartboost.com", "fyber.com", "tapjoy.com", "adcolony.com",
        "inmobi.com", "smaato.net", "pubnative.net", "admost.com", "startappnetwork.com",
        "admob.com", "admobapi.com"
    ));

    private static final List<String> AD_PATH_PREFIXES = Arrays.asList(
        "/ad-logic/", "/ads/", "/audio-ad/", "/adserver/", "/gampad/", "/pagead/",
        "/vast", "/vmap", "/prebid/", "/header-bidding/"
    );

    private AdBlockRules() {}

    public static boolean isBlocked(String url) {
        final URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException | NullPointerException e) {
            return false;
        }
        String host = uri.getHost();
        if (host == null) {
            return false;
        }
        host = host.toLowerCase(Locale.ROOT);
        String path = uri.getPath();
        if (path == null) {
            path = "";
        }

        if (matchesHost(host, AD_HOSTS)) {
            return true;
        }
        return hasAdPath(path);
    }

    private static boolean matchesHost(String host, Set<String> rules) {
        for (String rule : rules) {
            if (host.equals(rule) || host.endsWith("." + rule)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasAdPath(String path) {
        for (String prefix : AD_PATH_PREFIXES) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}