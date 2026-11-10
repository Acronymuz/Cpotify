package com.cpotify.app.myspoti;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebViewClient;

import androidx.webkit.WebSettingsCompat;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;
import androidx.webkit.UserAgentMetadata;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class WebPlayerHost {
    
    public interface StateCallback {
        void onStateChange(JSONObject state);
    }
    
    private static final String TAG = "WebPlayerHost";
    private static final String PLAYER_URL = "https://open.spotify.com/";
    private static final String PLAYER_HOST = "open.spotify.com";
    private static final String LOGIN_URL = "https://accounts.spotify.com/login";
    private static final String LICENSE_PATH = "/widevine-license";
    private static final int PAGE_LOAD_TIMEOUT_MS = 30000;
    private static final int LOAD_WATCH_MS = 8000;
    private static final int LOAD_RETRIES = 3;
    private static final int DRM_WATCH_MS = 15000;
    private static final int DRM_REBUILD_WINDOW_MS = 10 * 60 * 1000;
    private static final int DRM_REBUILDS_MAX = 2;
    private static final int LIMIT_WINDOW_MS = 4000;
    private static final int LIMIT_COUNT = 3;
    private static final int LIMIT_HOLD_MS = 5000;
    private static final int LIMIT_HOLD_MAX_MS = 60000;
    private static final int LIMIT_FORGET_MS = 30000;
    private static final int LICENSE_ANSWERS_LOGGED = 5;
    private static final int REBUILD_WATCH_MS = 1500;
    private static final String ROOT_KEY = "player_root_url";
    private static final String SIGNED_IN_KEY = "signed_in_since";
    private static final Set<String> LICENSE_HEADERS = Set.of("retry-after", "www-authenticate", "x-spotify-license-error");
    private static final List<Long> RETRY_PAGE_MS = Arrays.asList(5000L, 15000L, 30000L, 60000L);

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Context context;
    
    private WebView webView;
    private String currentUrl = "";
    private boolean isLoading = false;
    private boolean mainFrameFailed = false;
    private int pageFailures = 0;
    private int pageStarts = 0;
    private boolean loadAsked = false;
    private boolean drmAsked = false;
    private int licenseAnswersLogged = 0;
    private long licenceRefusedAt = 0L;
    private int heldLicenses = 0;
    private int limitStreak = 0;
    private long lastLimitAt = 0L;
    private final ArrayDeque<Long> license429 = new ArrayDeque<>();
    private final ArrayDeque<Long> drmRebuilds = new ArrayDeque<>();
    private boolean adBlockEnabled = true;
    
    private StateCallback stateCallback;
    private Consumer<WebView> webViewChangedListener;

    public void setStateCallback(StateCallback callback) {
        this.stateCallback = callback;
    }

    public void setWebViewChangedListener(Consumer<WebView> listener) {
        this.webViewChangedListener = listener;
    }
    
    // Bridge communication
    private final Map<String, Consumer<JSONObject>> pendingCallbacks = new ConcurrentHashMap<>();
    private int callIdCounter = 0;
    
    // Block rules (Brave-style)
    private final List<String> BLOCK_RULES = Arrays.asList(
        "||ads.spotify.com^", "||audio-ads.spotify.com^", "||audio-ads-eu.spotify.com^", "||audio-ads-us.spotify.com^",
        "||spclient.wg.spotify.com/ad-logic/", "||spclient.wg.spotify.com/ads/", "||api.spotify.com/v1/ads/",
        "||doubleclick.net^", "||googlesyndication.com^", "||googleadservices.com^", "||pagead2.googlesyndication.com^",
        "||adservice.google.com^", "||googletagmanager.com/gtag/js*", "||google-analytics.com/analytics.js",
        "||amazon-adsystem.com^", "||ads.tremorhub.com^", "||adnxs.com^", "||rubiconproject.com^",
        "||pubmatic.com^", "||openx.net^", "||criteo.com^", "||spotxchange.com^", "||moatads.com^",
        "||serving-sys.com^", "||mediamath.com^", "||turn.com^", "||casalemedia.com^", "||advertising.com^",
        "||adtechus.com^", "||adroll.com^", "||quantserve.com^", "||scorecardresearch.com^", "||outbrain.com^",
        "||taboola.com^", "||revcontent.com^", "||mgid.com^", "||popads.net^", "||propellerads.com^", "||adsterra.com^",
        "||connect.facebook.net^", "||facebook.net/tr^", "||bat.bing.com^", "||c.bing.com^",
        "||ads.yahoo.com^", "||advertising.yahoo.com^", "||adserver.yahoo.com^",
        "||analytics.twitter.com^", "||t.co/i/ads^",
        "||tiktok.com/api/ad^", "||ads.tiktok.com^",
        "||snapchat.com/ads^", "||storyly.io^",
        "||unityads.unity3d.com^", "||applovin.com^", "||ironsrc.com^", "||vungle.com^",
        "||chartboost.com^", "||fyber.com^", "||tapjoy.com^", "||adcolony.com^",
        "||inmobi.com^", "||smaato.net^", "||pubnative.net^", "||admost.com^",
        "||startappnetwork.com^", "||admob.com^", "||admobapi.com^",
        "||pagead2.googlesyndication.com/pagead/", "||googleads.g.doubleclick.net^",
        "||securepubads.g.doubleclick.net^", "||pubads.g.doubleclick.net^",
        "||cm.g.doubleclick.net^", "||stats.g.doubleclick.net^",
        "||fls.doubleclick.net^", "||ad.doubleclick.net^",
        "||g.doubleclick.net^", "||n4061ad.doubleclick.net^",
        "||pixel.quantserve.com^", "||pixel.mathtag.com^", "||sync.mathtag.com^",
        "||dpm.demdex.net^", "||d.turn.com^", "||ds.turn.com^",
        "||ib.adnxs.com^", "||cdn.adnxs.com^", "||acdn.adnxs.com^",
        "||ads.pubmatic.com^", "||ads.pubmatic.com^", "||image.pubmatic.com^",
        "||ads.rubiconproject.com^", "||pixel.rubiconproject.com^", "||rp.rubiconproject.com^",
        "||ads.openx.net^", "||ox-d.openx.net^", "||us-u.openx.net^",
        "||ads.criteo.com^", "||cas.criteo.com^", "||rtax.criteo.com^",
        "||ads.spotxchange.com^", "||px.spotxchange.com^", "||vast.spotxchange.com^",
        "||moatads.com^", "||pixel.moatads.com^", "||z.moatads.com^",
        "||ads.serving-sys.com^", "||bs.serving-sys.com^", "||cdn.serving-sys.com^",
        "||ads.mediamath.com^", "||pixel.mediamath.com^", "||tags.mediamath.com^",
        "||ads.turn.com^", "||d.turn.com^", "||sync.turn.com^",
        "||ads.casalemedia.com^", "||pixel.casalemedia.com^", "||sync.casalemedia.com^",
        "||ads.advertising.com^", "||pixel.advertising.com^", "||tracking.advertising.com^",
        "||ads.adtechus.com^", "||track.adtechus.com^", "||sync.adtechus.com^",
        "||ads.adroll.com^", "||pixel.adroll.com^", "||d.adroll.com^",
        "||ads.quantserve.com^", "||pixel.quantserve.com^", "||b.quantserve.com^",
        "||ads.scorecardresearch.com^", "||b.scorecardresearch.com^", "||c.scorecardresearch.com^",
        "||ads.outbrain.com^", "||pixel.outbrain.com^", "||odb.outbrain.com^",
        "||ads.taboola.com^", "||trc.taboola.com^", "||cdn.taboola.com^",
        "||ads.revcontent.com^", "||pixel.revcontent.com^", "||trk.revcontent.com^",
        "||ads.mgid.com^", "||pixel.mgid.com^", "||t.mgid.com^",
        "||ads.popads.net^", "||c.popads.net^", "||t.popads.net^",
        "||ads.propellerads.com^", "||pixel.propellerads.com^", "||track.propellerads.com^",
        "||ads.adsterra.com^", "||pixel.adsterra.com^", "||track.adsterra.com^"
    );

    private final Set<String> BLOCK_HOSTS = new HashSet<>(Arrays.asList(
        "ads.spotify.com", "audio-ads.spotify.com", "audio-ads-eu.spotify.com", "audio-ads-us.spotify.com",
        "spclient.wg.spotify.com", "api.spotify.com",
        "doubleclick.net", "googlesyndication.com", "googleadservices.com", "pagead2.googlesyndication.com",
        "adservice.google.com", "googletagmanager.com", "google-analytics.com",
        "amazon-adsystem.com", "ads.tremorhub.com", "adnxs.com", "rubiconproject.com",
        "pubmatic.com", "openx.net", "criteo.com", "spotxchange.com", "moatads.com",
        "serving-sys.com", "mediamath.com", "turn.com", "casalemedia.com", "advertising.com",
        "adtechus.com", "adroll.com", "quantserve.com", "scorecardresearch.com", "outbrain.com",
        "taboola.com", "revcontent.com", "mgid.com", "popads.net", "propellerads.com", "adsterra.com",
        "connect.facebook.net", "facebook.net", "bat.bing.com", "c.bing.com",
        "ads.yahoo.com", "advertising.yahoo.com", "adserver.yahoo.com",
        "analytics.twitter.com", "t.co", "tiktok.com", "ads.tiktok.com",
        "snapchat.com", "storyly.io", "unityads.unity3d.com", "applovin.com",
        "ironsrc.com", "vungle.com", "chartboost.com", "fyber.com", "tapjoy.com",
        "adcolony.com", "inmobi.com", "smaato.net", "pubnative.net", "admost.com",
        "startappnetwork.com", "admob.com", "admobapi.com",
        "pagead2.googlesyndication.com", "googleads.g.doubleclick.net",
        "securepubads.g.doubleclick.net", "pubads.g.doubleclick.net", "cm.g.doubleclick.net",
        "stats.g.doubleclick.net", "fls.doubleclick.net", "ad.doubleclick.net",
        "g.doubleclick.net", "n4061ad.doubleclick.net",
        "pixel.quantserve.com", "pixel.mathtag.com", "sync.mathtag.com",
        "dpm.demdex.net", "d.turn.com", "ds.turn.com",
        "ib.adnxs.com", "cdn.adnxs.com", "acdn.adnxs.com",
        "ads.pubmatic.com", "image.pubmatic.com",
        "ads.rubiconproject.com", "pixel.rubiconproject.com", "rp.rubiconproject.com",
        "ads.openx.net", "ox-d.openx.net", "us-u.openx.net",
        "ads.criteo.com", "cas.criteo.com", "rtax.criteo.com",
        "ads.spotxchange.com", "px.spotxchange.com", "vast.spotxchange.com",
        "moatads.com", "pixel.moatads.com", "z.moatads.com",
        "ads.serving-sys.com", "bs.serving-sys.com", "cdn.serving-sys.com",
        "ads.mediamath.com", "pixel.mediamath.com", "tags.mediamath.com",
        "ads.turn.com", "d.turn.com", "sync.turn.com",
        "ads.casalemedia.com", "pixel.casalemedia.com", "sync.casalemedia.com",
        "ads.advertising.com", "pixel.advertising.com", "tracking.advertising.com",
        "ads.adtechus.com", "track.adtechus.com", "sync.adtechus.com",
        "ads.adroll.com", "pixel.adroll.com", "d.adroll.com",
        "ads.quantserve.com", "pixel.quantserve.com", "b.quantserve.com",
        "ads.scorecardresearch.com", "b.scorecardresearch.com", "c.scorecardresearch.com",
        "ads.outbrain.com", "pixel.outbrain.com", "odb.outbrain.com",
        "ads.taboola.com", "trc.taboola.com", "cdn.taboola.com",
        "ads.revcontent.com", "pixel.revcontent.com", "trk.revcontent.com",
        "ads.mgid.com", "pixel.mgid.com", "t.mgid.com",
        "ads.popads.net", "c.popads.net", "t.popads.net",
        "ads.propellerads.com", "pixel.propellerads.com", "track.propellerads.com",
        "ads.adsterra.com", "pixel.adsterra.com", "track.adsterra.com"
    ));

    private final List<String> BLOCK_PATH_PREFIXES = Arrays.asList(
        "/ad-logic/", "/audio-ad/", "/adserver/", "/gampad/", "/pagead/", "/vast", "/vmap",
        "/ads/", "/ad/", "/banner/", "/interstitial/", "/reward/", "/native/",
        "/prebid/", "/hb/", "/header-bidding/", "/rtb/", "/ssp/", "/dsp/",
        "/collect/", "/event/", "/track/", "/pixel/", "/beacon/", "/impression/",
        "/click/", "/conversion/", "/attribution/", "/analytics/", "/telemetry/"
    );

    private final Set<String> WHITELIST = new HashSet<>(Arrays.asList(
        "spotify.com", "scdn.co", "spclient.wg.spotify.com", "apresolve.spotify.com", "api.spotify.com",
        "open.spotify.com", "accounts.spotify.com", "auth-callback.spotify.com",
        "scontentcdn.spotify.com", "audio-fa.scdn.co", "audio-ec.scdn.co", "audio-sp.scdn.co",
        "heads.scdn.co", "image-cdn-fa.spotifycdn.com", "image-cdn-ec.spotifycdn.com",
        "mosaic.scdn.co", "seed-mix.scdn.co", "charts-spotify-com.s3.amazonaws.com",
        "i.scdn.co", "geo.gate.spotify.com", "guc-spclient.spotify.com",
        "www.googleapis.com", "oauth2.googleapis.com", "securetoken.googleapis.com",
        "firebaselogging.googleapis.com", "firebaseremoteconfig.googleapis.com",
        "crashlytics.googleapis.com", "firebaseinstallations.googleapis.com"
    ));

    public WebPlayerHost(Context context) {
        this.context = context.getApplicationContext();
    }

    public void load() {
        mainHandler.post(this::createAndLoadWebView);
    }

    private void createAndLoadWebView() {
        webView = createWebView();
        if (webViewChangedListener != null && webView != null) {
            webViewChangedListener.accept(webView);
        }
        loadPlayerPage();
    }

    public void destroy() {
        mainHandler.post(() -> {
            if (webView != null) {
                webView.stopLoading();
                webView.destroy();
                webView = null;
            }
        });
    }

    public void reload() {
        mainHandler.post(() -> {
            if (webView != null && !mainFrameFailed && currentUrl.startsWith(PLAYER_URL)) {
                webView.loadUrl(currentUrl);
            }
        });
    }

    public void setAdBlockEnabled(boolean enabled) {
        adBlockEnabled = enabled;
    }

    public WebView getWebView() {
        return webView;
    }

    @SuppressLint("SetJavaScriptEnabled")
    private WebView createWebView() {
        WebView view = new WebView(context);
        
        WebSettings settings = view.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        
        // Mobile User Agent - makes Spotify serve mobile Web Player UI
        settings.setUserAgentString(getMobileUserAgent());
        
        // Client Hints for mobile
        setupClientHints(settings);
        
        // Remove X-Requested-With header (helps with Spotify Web Player)
        if (WebViewFeature.isFeatureSupported(WebViewFeature.REQUESTED_WITH_HEADER_ALLOW_LIST)) {
            WebSettingsCompat.setRequestedWithHeaderOriginAllowList(settings, Collections.emptySet());
        }
        
        // Renderer priority for background playback
        view.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false);
        
        // Cookies - critical for Spotify auth
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(view, true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookieManager.setAcceptThirdPartyCookies(view, true);
        }
        CookieManager.setAcceptFileSchemeCookies(true);
        
        // WebViewClient with ad blocking
        view.setWebViewClient(new AdBlockingWebViewClient());
        
        // WebChromeClient for permissions
        view.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(PermissionRequest request) {
                if (request.getResources().length > 0) {
                    for (String resource : request.getResources()) {
                        if (PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID.equals(resource)) {
                            drmAsked = true;
                            request.grant(new String[]{resource});
                            return;
                        }
                    }
                }
                request.deny();
            }
            
            @Override
            public boolean onConsoleMessage(android.webkit.ConsoleMessage message) {
                if (message.messageLevel() == android.webkit.ConsoleMessage.MessageLevel.ERROR ||
                    message.messageLevel() == android.webkit.ConsoleMessage.MessageLevel.WARNING) {
                    String msg = message.message();
                    String CONSOLE_NOISE = ".*(font|preload|storage|access|refused|blocked|cors|mixed|content).*";
                    if (!msg.matches(CONSOLE_NOISE)) {
                        Log.d(TAG, "Console " + message.messageLevel() + ": " + msg.substring(0, Math.min(300, msg.length())));
                    }
                }
                return false;
            }
        });
        
        // Inject bridge script
        view.addJavascriptInterface(new BridgeInterface(), "MyspotiHost");
        
        return view;
    }

    private String getMobileUserAgent() {
        // Mobile Chrome UA - makes Spotify serve mobile Web Player UI
        return "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.144 Mobile Safari/537.36";
    }

    private void setupClientHints(WebSettings settings) {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.USER_AGENT_METADATA)) {
            List<UserAgentMetadata.BrandVersion> brands = Arrays.asList(
                new UserAgentMetadata.BrandVersion.Builder()
                    .setBrand("Not_A Brand")
                    .setMajorVersion("99")
                    .setFullVersion("99.0.0.0")
                    .build(),
                new UserAgentMetadata.BrandVersion.Builder()
                    .setBrand("Chromium")
                    .setMajorVersion("120")
                    .setFullVersion("120.0.6099.144")
                    .build(),
                new UserAgentMetadata.BrandVersion.Builder()
                    .setBrand("Google Chrome")
                    .setMajorVersion("120")
                    .setFullVersion("120.0.6099.144")
                    .build()
            );
            
            UserAgentMetadata metadata = new UserAgentMetadata.Builder()
                .setBrandVersionList(brands)
                .setFullVersion("120.0.6099.144")
                .setPlatform("Android")
                .setPlatformVersion("14")
                .setArchitecture("")
                .setBitness(64)
                .setMobile(true)
                .setModel("Pixel 8")
                .setWow64(false)
                .build();
            
            WebSettingsCompat.setUserAgentMetadata(settings, metadata);
        }
    }

    private void loadPlayerPage() {
        if (webView == null) return;
        
        mainHandler.post(() -> {
            pageStarts = 0;
            loadAsked = true;
            mainFrameFailed = false;
            pageFailures = 0;
            drmAsked = false;
            licenseAnswersLogged = 0;
            heldLicenses = 0;
            
            webView.loadUrl(PLAYER_URL);
            watchLoad(PLAYER_URL, 1, LOAD_WATCH_MS);
        });
    }

    private void watchLoad(String url, int attempt, long firstWatchMs) {
        mainHandler.postDelayed(() -> {
            if (pageStarts > 0 || !loadAsked || attempt > LOAD_RETRIES) return;
            Log.w(TAG, "Page never started: loading again (attempt " + attempt + ")");
            if (attempt > 1 && webView != null) webView.stopLoading();
            if (webView != null) webView.loadUrl(url);
            watchLoad(url, attempt + 1, firstWatchMs);
        }, attempt == 1 ? firstWatchMs : LOAD_WATCH_MS * attempt);
    }

    private class AdBlockingWebViewClient extends WebViewClient {
        
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            String url = request.getUrl().toString();
            String host = request.getUrl().getHost();
            
            // Allow Spotify and related domains
            boolean allowed = host != null && (
                host.equals(PLAYER_HOST) ||
                host.endsWith(".spotify.com") ||
                host.endsWith(".spotify.net") ||
                host.endsWith(".scdn.co") ||
                host.equals("accounts.spotify.com") ||
                host.endsWith(".google.com") ||
                host.endsWith(".facebook.com") ||
                host.endsWith(".apple.com")
            );
            
            // Only HTTPS
            boolean secure = "https".equals(request.getUrl().getScheme());
            
            if (!allowed || !secure) {
                Log.d(TAG, "Navigation refused: " + host + (secure ? "" : " (not https)"));
                return true;
            }
            
            return false;
        }

        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            String url = request.getUrl().toString();
            
            // Check ad blocking
            if (adBlockEnabled && isBlocked(url)) {
                Log.d(TAG, "Blocked ad request: " + url);
                return new WebResourceResponse(
                    "text/plain", "utf-8", 403, "Blocked",
                    Collections.emptyMap(),
                    new ByteArrayInputStream(new byte[0])
                );
            }
            
            // Handle license limiting (429)
            if (request.getMethod().equals("POST") && isLicenseLimited() && url.contains(LICENSE_PATH)) {
                if (heldLicenses++ == 0) {
                    Log.w(TAG, "Audio licence requests answered locally while Spotify is limiting");
                }
                long left = Math.max(1, (getLicenseLimitedUntil() - System.currentTimeMillis()) / 1000);
                Map<String, String> headers = new HashMap<>();
                headers.put("Access-Control-Allow-Origin", PLAYER_URL);
                headers.put("Access-Control-Allow-Credentials", "true");
                headers.put("Access-Control-Expose-Headers", "Retry-After");
                headers.put("Retry-After", String.valueOf(left));
                headers.put("Content-Type", "application/octet-stream");
                
                return new WebResourceResponse(
                    "application/octet-stream", "utf-8", 429, "Too Many Requests",
                    headers,
                    new ByteArrayInputStream(new byte[0])
                );
            }
            
            return null;
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            Log.d(TAG, "Page started: " + url);
            pageStarts++;
            currentUrl = url;
            mainFrameFailed = false;
            
            if (url.startsWith(PLAYER_URL)) {
                drmAsked = false;
                licenseAnswersLogged = 0;
                heldLicenses = 0;
                // Re-inject bridge
                injectBridge();
            }
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            Log.d(TAG, "Page finished: " + url);
            currentUrl = url;
            
            if (url.startsWith(PLAYER_URL) && !mainFrameFailed) {
                pageFailures = 0;
                // Inject bridge and cosmetic filter
                injectBridge();
                injectCosmeticFilter();
                
                // Start DRM watch
                if (!drmAsked) {
                    mainHandler.postDelayed(drmWatchRunnable, DRM_WATCH_MS);
                }
            }
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request, android.webkit.WebResourceError error) {
            String url = request.getUrl().toString();
            if (request.getUrl().getHost() != null && request.getUrl().getHost().contains("spotify")) {
                Log.w(TAG, "Load error: " + error.getErrorCode() + " " + error.getDescription() + " " + url);
            }
            
            if (request.isForMainFrame() && PLAYER_HOST.equals(request.getUrl().getHost()) && 
                !error.getDescription().toString().contains("ERR_ABORTED")) {
                playerPageFailed("load error " + error.getErrorCode());
            }
        }

        @Override
        public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse response) {
            int statusCode = response.getStatusCode();
            String url = request.getUrl().toString();
            
            if (request.getUrl().getHost() != null && request.getUrl().getHost().contains("spotify") && 
                !request.getUrl().getPath().contains("widevine-license")) {
                Log.d(TAG, "HTTP " + statusCode + " " + url);
            }
            
            if (request.getUrl().getPath() != null && request.getUrl().getPath().contains("widevine-license")) {
                if (statusCode == 429) {
                    onLicenseLimited();
                } else if (statusCode >= 400 && statusCode < 500) {
                    licenceRefusedAt = System.currentTimeMillis();
                }
                
                if (statusCode >= 400 && licenseAnswersLogged++ < LICENSE_ANSWERS_LOGGED) {
                    // Log license headers
                    Map<String, String> headers = response.getResponseHeaders();
                    StringBuilder h = new StringBuilder();
                    for (Map.Entry<String, String> entry : headers.entrySet()) {
                        String key = entry.getKey().toLowerCase();
                        if (LICENSE_HEADERS.contains(key)) {
                            if (h.length() > 0) h.append(", ");
                            h.append(key).append("=").append(entry.getValue().substring(0, Math.min(60, entry.getValue().length())));
                        }
                    }
                    if (h.length() > 0) {
                        Log.d(TAG, "Licence answer " + statusCode + ": " + h);
                    }
                }
            }
            
            if (request.isForMainFrame() && PLAYER_HOST.equals(request.getUrl().getHost()) && 
                (statusCode >= 500 || statusCode == 429)) {
                playerPageFailed("http " + statusCode);
            }
        }

        @Override
        public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
            Log.w(TAG, "Renderer gone (crashed=" + detail.didCrash() + "): rebuilding");
            rebuildWebView();
            return true;
        }
    }

    private String surfaceScript;

    private String loadSurfaceScript() {
        if (surfaceScript != null) {
            return surfaceScript;
        }
        try (InputStream in = context.getAssets().open("myspoti-surface.js")) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
            surfaceScript = sb.toString();
            return surfaceScript;
        } catch (Exception e) {
            Log.e(TAG, "Failed to load surface observer", e);
            return null;
        }
    }

    private void injectBridge() {
        if (webView == null) return;

        String bridgeScript = loadSurfaceScript();
        if (bridgeScript == null) return;
        webView.evaluateJavascript(bridgeScript, null);
    }

    private void injectCosmeticFilter() {
        if (webView == null) return;
        
        String cosmeticScript = COSMETIC_FILTER_SCRIPT;
        webView.evaluateJavascript(cosmeticScript, null);
        
        // Re-run every 2 seconds
        mainHandler.postDelayed(this::injectCosmeticFilter, 2000);
    }

    private final Runnable drmWatchRunnable = new Runnable() {
        @Override
        public void run() {
            if (drmAsked || !loadAsked || !currentUrl.startsWith(PLAYER_URL)) return;
            if (!newViewAllowed()) {
                Log.w(TAG, "Player never started; new WebViews used up");
                return;
            }
            Log.w(TAG, "Player never started (no DRM request): rebuilding");
            rebuildWebView();
        }
    };

    private boolean newViewAllowed() {
        long now = System.currentTimeMillis();
        while (!drmRebuilds.isEmpty() && now - drmRebuilds.peek() > DRM_REBUILD_WINDOW_MS) {
            drmRebuilds.poll();
        }
        if (drmRebuilds.size() >= DRM_REBUILDS_MAX) return false;
        drmRebuilds.add(now);
        return true;
    }

    private void rebuildWebView() {
        mainHandler.post(() -> {
            if (webView != null) {
                webView.stopLoading();
                webView.destroy();
            }
            drmAsked = false;
            createAndLoadWebView();
        });
    }

    private void playerPageFailed(String reason) {
        mainFrameFailed = true;
        long wait = RETRY_PAGE_MS.get(Math.min(pageFailures, RETRY_PAGE_MS.size() - 1));
        pageFailures++;
        Log.w(TAG, "Player page failed (" + reason + "): retry in " + (wait / 1000) + "s");
        mainHandler.postDelayed(() -> {
            if (loadAsked && webView != null) {
                webView.loadUrl(PLAYER_URL);
            }
        }, wait);
    }

    private void onLicenseLimited() {
        long now = System.currentTimeMillis();
        license429.addLast(now);
        while (!license429.isEmpty() && now - license429.peek() > LIMIT_WINDOW_MS) {
            license429.poll();
        }
        if (license429.size() < LIMIT_COUNT || isLicenseLimited()) return;
        if (now - lastLimitAt > LIMIT_FORGET_MS) limitStreak = 0;
        lastLimitAt = now;
        int hold = Math.min(LIMIT_HOLD_MS << Math.min(limitStreak, 4), LIMIT_HOLD_MAX_MS);
        limitStreak++;
        license429.clear();
        Log.w(TAG, "Spotify limiting audio licences: holding playback " + (hold / 1000) + "s");
        heldLicenses = 0;
        setLicenseLimitedUntil(now + hold);
    }

    private boolean isLicenseLimited() {
        return getLicenseLimitedUntil() > System.currentTimeMillis();
    }

    private long getLicenseLimitedUntil() {
        return 0; // Simplified
    }

    private void setLicenseLimitedUntil(long until) {
        // Store in SharedPreferences or memory
    }

    private boolean isBlocked(String url) {
        return AdBlockRules.isBlocked(url);
    }

    private boolean matchesRule(String url, String rule) {
        // Simplified ||domain^ matching
        if (rule.startsWith("||") && rule.endsWith("^")) {
            String domain = rule.substring(2, rule.length() - 1);
            return url.contains(domain);
        }
        return url.contains(rule);
    }

    // JavaScript Interface for bridge communication
    private class BridgeInterface {
        @android.webkit.JavascriptInterface
        public void postMessage(String json) {
            try {
                JSONObject msg = new JSONObject(json);
                String type = msg.optString("type");
                
                switch (type) {
                    case "result":
                        handleResult(msg);
                        break;
                    case "state":
                        if (stateCallback != null) {
                            mainHandler.post(() -> stateCallback.onStateChange(msg.optJSONObject("state")));
                        }
                        break;
                    case "queue":
                    case "error":
                        // State/queue updates are handled via Capacitor bridge from JS side
                        break;
                    case "ready":
                        Log.d(TAG, "Bridge ready v" + msg.optInt("version"));
                        break;
                }
            } catch (JSONException e) {
                Log.e(TAG, "Error parsing bridge message", e);
            }
        }
        
        private void handleResult(JSONObject msg) {
            String id = msg.optString("id");
            Consumer<JSONObject> callback = pendingCallbacks.remove(id);
            if (callback != null) {
                mainHandler.post(() -> callback.accept(msg));
            }
        }
    }

    // Bridge JavaScript - Myspoti-style
    private static final String BRIDGE_SCRIPT = 
        "(function(){" +
        "  if (window.Myspoti && window.Myspoti.version) {" +
        "    if (window.Myspoti.announce && window.__mspReannounce) {" +
        "      window.__mspReannounce = false;" +
        "      window.Myspoti.announce();" +
        "    }" +
        "    return;" +
        "  }" +
        "  try {" +
        "    Object.defineProperty(Document.prototype, 'hidden', { configurable: true, get: () => false });" +
        "    Object.defineProperty(Document.prototype, 'visibilityState', { configurable: true, get: () => 'visible' });" +
        "    document.hasFocus = () => true;" +
        "    for (const t of ['visibilitychange', 'webkitvisibilitychange']) {" +
        "      document.addEventListener(t, e => e.stopImmediatePropagation(), true);" +
        "      window.addEventListener(t, e => e.stopImmediatePropagation(), true);" +
        "    }" +
        "  } catch (e) {}" +
        "  const post = msg => {" +
        "    const json = JSON.stringify(msg);" +
        "    try {" +
        "      if (window.MyspotiHost && typeof window.MyspotiHost.postMessage === 'function') return window.MyspotiHost.postMessage(json);" +
        "    } catch (e) {}" +
        "  };" +
        "  const findPlatform = () => {" +
        "    const host = document.getElementById('main') || document.body;" +
        "    const roots = [host, ...host.querySelectorAll('*')];" +
        "    for (const el of roots) {" +
        "      const key = Object.keys(el).find(k => k.startsWith('__reactContainer$'));" +
        "      if (!key) continue;" +
        "      const stack = [el[key]];" +
        "      while (stack.length) {" +
        "        const f = stack.pop();" +
        "        const v = f.memoizedProps && f.memoizedProps.value;" +
        "        if (v && typeof v === 'object' && typeof v.getRegistry === 'function') return v;" +
        "        if (f.sibling) stack.push(f.sibling);" +
        "        if (f.child) stack.push(f.child);" +
        "      }" +
        "    }" +
        "    return null;" +
        "  };" +
        "  let services = null;" +
        "  const resolveServices = () => {" +
        "    const platform = findPlatform();" +
        "    if (!platform) return null;" +
        "    const registry = platform.getRegistry();" +
        "    const byName = {};" +
        "    for (const key of registry._map.keys()) {" +
        "      if (key && key.description) byName[key.description] = key;" +
        "    }" +
        "    const get = name => {" +
        "      if (!byName[name]) throw new Error('service ' + name + ' not found');" +
        "      return registry.resolve(byName[name]);" +
        "    };" +
        "    const player = get('PlayerAPI');" +
        "    if (!player || typeof player.getState !== 'function') return null;" +
        "    return {" +
        "      platform, player, playback: get('PlaybackAPI'), library: get('LibraryAPI')," +
        "      token: get('TokenProvider'), graphql: platform.getGraphQLLoader()," +
        "      byName, resolve: name => get(name)" +
        "    };" +
        "  };" +
        "  const need = () => { if (!services) throw new Error('not ready'); return services; };" +
        "  const track = item => item && {" +
        "    kind: item.type === 'ad' || /^spotify:ad:/.test(item.uri || '') || item.provider === 'ad' || !!item.advertiser ? 'ad' :" +
        "         /^spotify:episode:/.test(item.uri) ? 'episode' : 'track'," +
        "    uri: item.uri, uid: item.uid || null, name: item.name || item.title || ''," +
        "    advertiser: item.advertiser || null, durationMs: item.duration ? item.duration.milliseconds : null," +
        "    artists: (item.artists || []).map(a => ({ uri: a.uri, name: a.name }))," +
        "    album: item.album ? { uri: item.album.uri, name: item.album.name } : null," +
        "    image: (item.images || (item.album && item.album.images) || []).sort((a,b)=>(b.width||0)-(a.width||0))[0]?.url || null," +
        "    explicit: !!item.isExplicit, mediaType: item.mediaType || 'audio', provider: item.provider || null" +
        "  };" +
        "  const lastReal = items => (items || []).filter(i => (i.type !== 'ad' && !/^spotify:ad:/.test(i.uri || '') && i.provider !== 'ad' && !i.advertiser));" +
        "  const mapState = s => s && {" +
        "    track: track(s.item), contextUri: s.context ? s.context.uri : null," +
        "    contextName: s.context && s.context.metadata ? s.context.metadata.context_description || null : null," +
        "    previous: track(lastReal(s.previousItems).slice(-1)[0]), next: track(lastReal(s.nextItems)[0])," +
        "    isPaused: !!s.isPaused, isBuffering: !!s.isBuffering, positionMs: s.positionAsOfTimestamp," +
        "    timestamp: s.timestamp, durationMs: s.duration, shuffle: !!s.shuffle, repeat: s.repeat," +
        "    speed: s.speed, restrictions: s.restrictions || {}, playbackId: s.playbackId || null" +
        "  };" +
        "  const mapQueue = q => q && {" +
        "    current: track(q.current), queued: (q.queued || []).map(track), nextUp: (q.nextUp || []).map(track)" +
        "  };" +
        "  const toJson = (v, d=0, s=new WeakSet()) => {" +
        "    if (v===null||v===undefined) return null;" +
        "    const t=typeof v;" +
        "    if (t==='bigint') return v.toString();" +
        "    if (t==='function'||t==='symbol') return undefined;" +
        "    if (t!=='object') return v;" +
        "    if (d>12||s.has(v)) return null;" +
        "    s.add(v);" +
        "    if (v instanceof Date) return v.toISOString();" +
        "    if (v instanceof Map) v = Object.fromEntries(v);" +
        "    if (v instanceof Set) v = [...v];" +
        "    if (ArrayBuffer.isView(v)) return Array.from(v);" +
        "    if (Array.isArray(v)) return v.map(x=>{const j=toJson(x,d+1,s);return j===undefined?null:j;});" +
        "    const o={}; for(const k of Object.keys(v)){if(!k.startsWith('_' )){const j=toJson(v[k],d+1,s);if(j!==undefined)o[k]=j;}}return o;" +
        "  };" +
        "  let adsLatched=false;" +
        "  const adsOff = () => {" +
        "    if (window.__mspKeepAds) return 'kept (Premium)';" +
        "    if (!services) return 'not ready';" +
        "    if (adsLatched) return 'already off';" +
        "    try {" +
        "      const audio = services.resolve('AdManagers').audio;" +
        "      if (!audio) return 'no ad manager';" +
        "      const done=[];" +
        "      for (const [label,mgr] of [['audio',audio],['inStream',audio.inStreamApi]]) {" +
        "        if (!mgr) continue;" +
        "        try { if (typeof mgr.disable==='function') { mgr.disable(); done.push(label); }" +
        "        if (typeof mgr.enable==='function') Object.defineProperty(mgr,'enable',{value:()=>false,writable:true,configurable:true});" +
        "        if ('enabled' in mgr) mgr.enabled=false;" +
        "        } catch(e) {}" +
        "      }" +
        "      return done.length ? (adsLatched=true, done.join(',')) : 'nothing to disable';" +
        "    } catch(e) { return 'failed: '+e; }" +
        "  };" +
        "  const commands = {" +
        "    'bridge.version': () => 1," +
        "    'ads.off': adsOff," +
        "    'player.state': () => mapState(need().player.getState())," +
        "    'player.queue': () => mapQueue(need().player.getQueue())," +
        "    'player.play': a => {" +
        "      const skipTo={};" +
        "      if (a.trackUri) skipTo.uri=a.trackUri;" +
        "      if (a.trackUid) skipTo.uid=a.trackUid;" +
        "      if (Number.isInteger(a.index)) skipTo.index=a.index;" +
        "      const extra={};" +
        "      if (Object.keys(skipTo).length) extra.skipTo=skipTo;" +
        "      if (a.positionMs) extra.seekTo=a.positionMs;" +
        "      const context=Array.isArray(a.order)&&a.order.length?{uri:a.contextUri,url:'context://'+a.contextUri+'?order='+Date.now(),pages:[{items:a.order.map(uri=>({uri}))}]}:{uri:a.contextUri};" +
        "      return need().player.play(context,{},extra);" +
        "    }," +
        "    'player.pause': () => need().player.pause()," +
        "    'player.resume': () => need().player.resume()," +
        "    'player.seek': a => need().player.seekTo(a.positionMs)," +
        "    'player.next': () => need().player.skipToNext()," +
        "    'player.previous': () => need().player.skipToPrevious()," +
        "    'player.setShuffle': a => need().player.setShuffle(!!a.on)," +
        "    'player.setRepeat': a => need().player.setRepeat(a.mode)," +
        "    'player.setSpeed': a => need().player.setSpeed(a.speed)," +
        "    'player.audioInfo': async () => {" +
        "      const sdk=need().resolve('PlayerSDK');" +
        "      const streamer=sdk&&sdk.harmony&&sdk.harmony._streamer;" +
        "      const player=streamer&&streamer._playerPromise?await streamer._playerPromise:null;" +
        "      const content=player&&player._currentContent;" +
        "      return {bitrate:content&&content._bitrate||0,mime:content&&content._mimeType||'',level:player&&player._preferredMediaPlaybackQuality&&player._preferredMediaPlaybackQuality.audio||''};" +
        "    }," +
        "    'player.setAudioQuality': async a => {" +
        "      const sdk=need().resolve('PlayerSDK');" +
        "      const harmony=sdk&&sdk.harmony;" +
        "      const target=[harmony&&harmony._streamer,harmony].find(t=>t&&typeof t.setMediaPlaybackQuality==='function');" +
        "      if (!target) throw new Error('no audio quality setting');" +
        "      return !!(await target.setMediaPlaybackQuality({audio:a.level}));" +
        "    }," +
        "    'player.preloadNext': async () => {" +
        "      const sdk=need().resolve('PlayerSDK');" +
        "      const lp=sdk&&sdk.harmony&&sdk.harmony._streamer&&sdk.harmony._streamer._listPlayer;" +
        "      if (!lp||typeof lp._preloadUpcomingTrack!=='function') return null;" +
        "      return String(await lp._preloadUpcomingTrack());" +
        "    }," +
        "    'queue.add': a => need().player.addToQueue(a.uris.map(uri=>({uri})))," +
        "    'queue.remove': a => need().player.removeFromQueue(a.items)," +
        "    'queue.clear': () => need().player.clearQueue()," +
        "    'queue.skipTo': a => need().player.skipToNext({uri:a.uri,uid:a.uid})," +
        "    'queue.insert': a => need().player.insertIntoQueue(a.uris.map(uri=>({uri})),{before:a.before})," +
        "    'queue.reorder': a => need().player.reorderQueue(a.items,a.after?{after:a.after}:{before:a.before})," +
        "    'media.mute': async a => {" +
        "      const els=new Set([...document.querySelectorAll('audio,video')]);" +
        "      for (const el of els) { el.muted=false; el.volume=a.on?0.001:1; }" +
        "      return els.size;" +
        "    }," +
        "    'media.hush': async a => {" +
        "      const els=new Set([...document.querySelectorAll('audio,video')]);" +
        "      for (const el of els) { if (a.on) { el.muted=false; el.volume=0.001; } else { el.volume=1; } }" +
        "      return els.size;" +
        "    }," +
        "    'media.state': async () => {" +
        "      const player=need().player;" +
        "      return {paused:true,muted:false,volume:1,level:1,own:1,time:0,readyState:4,networkState:1,error:null,keys:0,keyWaits:0,ended:false,playerVolume:1,active:true};" +
        "    }," +
        "    'media.volume': async a => {" +
        "      const els=document.querySelectorAll('audio,video');" +
        "      const vol=a.restore?1:Math.min(1,Math.max(0.001,Number(a.level)||0));" +
        "      for(const el of els) el.volume=vol;" +
        "      return vol;" +
        "    }," +
        "    'volume.get': () => need().playback.getVolume()," +
        "    'volume.set': a => need().playback.setVolume(a.level)," +
        "    'library.contains': a => need().library.contains(...a.uris)," +
        "    'library.add': a => need().library.add({uris:a.uris})," +
        "    'library.remove': a => need().library.remove({uris:a.uris})," +
        "    'service.list': () => {" +
        "      const out={}; for(const name of Object.keys(need().byName)){try{const v=need().resolve(name);if(v&&typeof v==='object')out[name]=Object.getOwnPropertyNames(v).filter(n=>typeof v[n]==='function'&&!n.startsWith('_'));}catch(e){}}return out;" +
        "    }," +
        "    'service.call': async a => toJson(await need().resolve(a.service)[a.method](...(a.args||[])))," +
        "    'playlist.setCover': async a => {" +
        "      const playlists=need().resolve('PlaylistAPI');" +
        "      let token=null; if(a.jpeg){const bytes=Uint8Array.from(atob(a.jpeg),c=>c.charCodeAt(0));token=await playlists.uploadImage(new Blob([bytes],{type:'image/jpeg'}));}" +
        "      await playlists.updateDetails(a.uri,{imageUploadToken:token}); return true;" +
        "    }," +
        "    'gql.query': async a => need().graphql({name:a.name,operation:a.operation||'query',sha256Hash:a.hash,value:null},a.variables||{})," +
        "    'token.get': async () => {" +
        "      const t=await need().token.getToken();" +
        "      return t&&{accessToken:t.accessToken,expiresAtMs:t.accessTokenExpirationTimestampMs,clientId:t.clientId,appVersion:need().platform.version,isAnonymous:!!t.isAnonymous};" +
        "    }" +
        "  };" +
        "  let calling=0;" +
        "  const call=async(id,method,args)=>{try{const fn=commands[method];if(!fn)throw new Error('unknown method '+method);let p;calling++;try{p=fn(args||{});}finally{calling--;}const v=await p;post({type:'result',id,ok:true,value:v===undefined?null:v});}catch(e){post({type:'result',id,ok:false,error:String(e)});}};" +
        "  window.Myspoti={version:1,call,announce:()=>{if(services){post({type:'ready',version:1});const s=mapState(services.player.getState());post({type:'state',state:s});}},adsOff,methods:Object.keys(commands)};" +
        "  const boot=()=>{try{services=resolveServices();}catch(e){services=null;}if(services){post({type:'ready',version:1});const s=mapState(services.player.getState());post({type:'state',state:s});const ev=services.player.getEvents();ev.addListener('update',()=>{const s=mapState(services.player.getState());if(s&&s.track&&s.track.kind==='ad'){adsOff();}post({type:'state',state:s});});ev.addListener('queue_update',()=>post({type:'queue',queue:mapQueue(services.player.getQueue())}));adsOff();return;}setTimeout(boot,250);};" +
        "  boot();" +
        "})()";

    // Cosmetic filter script (Brave-style)
    private static final String COSMETIC_FILTER_SCRIPT =
        "(function(){" +
        "  try {" +
        "    var selectors = [" +
        "      '[data-testid*=\"ad\"]', '[data-testid*=\"Ad\"]', '[data-testid*=\"AD\"]'," +
        "      '[data-encore-id*=\"ad\"]', '[data-encore-id*=\"Ad\"]'," +
        "      '.AdBanner', '.ad-banner', '.advertisement', '.sponsored'," +
        "      '[aria-label*=\"advertisement\" i]', '[aria-label*=\"sponsored\" i]'," +
        "      'div[id^=\"google_ads_\"]', 'iframe[src*=\"doubleclick\"]'," +
        "      'iframe[src*=\"googlesyndication\"]', 'iframe[src*=\"adnxs\"]'" +
        "    ];" +
        "    selectors.forEach(function(sel) {" +
        "      try {" +
        "        document.querySelectorAll(sel).forEach(function(el) {" +
        "          el.style.setProperty('display', 'none', 'important');" +
        "          el.style.setProperty('visibility', 'hidden', 'important');" +
        "          el.style.setProperty('opacity', '0', 'important');" +
        "          el.style.setProperty('height', '0', 'important');" +
        "          el.style.setProperty('width', '0', 'important');" +
        "          el.remove();" +
        "        });" +
        "      } catch(e) {}" +
        "    });" +
        "    var adTexts = ['iklan', 'advertisement', 'sponsored', 'advertising', 'promoted'];" +
        "    document.querySelectorAll('span,div,h1,h2,h3,p,header,button').forEach(function(el) {" +
        "      var txt = (el.textContent || '').trim().toLowerCase();" +
        "      if (adTexts.includes(txt) && el.offsetWidth < 200 && el.offsetHeight < 100) {" +
        "        el.style.setProperty('display', 'none', 'important');" +
        "        el.remove();" +
        "      }" +
        "    });" +
        "  } catch(e) {}" +
        "})()";
}