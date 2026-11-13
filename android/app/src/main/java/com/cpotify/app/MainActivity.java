package com.cpotify.app;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.webkit.WebView;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.getcapacitor.BridgeActivity;
import com.cpotify.app.myspoti.WebPlayerHost;

import org.json.JSONArray;
import org.json.JSONObject;

public class MainActivity extends BridgeActivity implements WebPlayerHost.StateCallback {

    private static final String TAG = "SpotifyMobile";
    private static final String MEDIA_CONTROL_ACTION = "com.cpotify.app.MEDIA_CONTROL";
    private static final String UPDATE_MEDIA_ACTION = "com.cpotify.app.UPDATE_MEDIA";
    private static final String PREFS_NAME = "cpotify_ad_restart";
    private static final String KEY_LAST_AD_RESTART = "last_ad_restart";
    private static final long AUTO_RESUME_WINDOW_MS = 30000;
    public static final String EXTRA_RESUME_AD = "com.cpotify.app.RESUME_AD";
    private static final int AD_RESUME_NOTIFICATION_ID = 2;

    private BroadcastReceiver mediaControlReceiver;
    private WebPlayerHost webPlayerHost;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private long adFirstSeenAt = 0;
    private boolean pendingAutoResume = false;
    private long autoResumeDeadline = 0;
    private boolean isForeground = false;
    private String lastTrackName = "";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getIntent() != null && getIntent().getBooleanExtra(EXTRA_RESUME_AD, false)) {
            armAutoResume();
            ResumeAccessibilityService.requestResume();
        }

        // The background ad-resume notification (unlike the foreground-service media
        // notification) needs the runtime POST_NOTIFICATIONS grant on Android 13+.
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 1001);
        }

        boolean debuggable = (getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        if (debuggable && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(true);
        }

        Intent serviceIntent = new Intent(this, MediaPlaybackService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }

        registerMediaControlReceiver();
        initWebPlayerHost();
    }

    @Override
    public void onResume() {
        super.onResume();
        isForeground = true;
    }

    @Override
    public void onPause() {
        super.onPause();
        isForeground = false;
    }

    private void initWebPlayerHost() {
        webPlayerHost = new WebPlayerHost(this);
        webPlayerHost.setStateCallback(this);
        webPlayerHost.setWebViewChangedListener(this::setContentView);

        WebView webView = webPlayerHost.getWebView();
        if (webView != null) {
            setContentView(webView);
        } else {
            mainHandler.postDelayed(() -> {
                WebView wv = webPlayerHost.getWebView();
                if (wv != null) {
                    setContentView(wv);
                }
            }, 100);
        }

        webPlayerHost.load();
    }

    private void registerMediaControlReceiver() {
        mediaControlReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (MEDIA_CONTROL_ACTION.equals(intent.getAction())) {
                    String control = intent.getStringExtra("control");
                    if (control != null) {
                        handleMediaControl(control);
                    }
                }
            }
        };

        IntentFilter filter = new IntentFilter(MEDIA_CONTROL_ACTION);
        ContextCompat.registerReceiver(this, mediaControlReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    private void handleMediaControl(String control) {
        if (webPlayerHost == null) return;

        String jsAction = MediaControlCommand.toJavaScript(control);
        if (jsAction == null || jsAction.isEmpty()) return;
        final String js = jsAction;
        runOnUiThread(() -> {
            WebView playerWebView = webPlayerHost.getWebView();
            if (playerWebView != null) {
                playerWebView.evaluateJavascript(js, null);
            } else {
                Log.w(TAG, "Media control ignored: player WebView not ready");
            }
        });
    }

    @Override
    public void onStateChange(JSONObject state) {
        if (state == null) return;

        runOnUiThread(() -> {
            try {
                boolean isPaused = state.optBoolean("isPaused", true);
                long positionMs = state.optLong("positionMs", 0);
                long durationMs = state.optLong("durationMs", 0);

                JSONObject track = state.optJSONObject("track");
                if (track == null) return;

                String kind = track.optString("kind", "track");
                handleAdWatchdog(kind);
                if (!"ad".equals(kind)) {
                    String nm = track.optString("name", "");
                    if (!nm.isEmpty()) lastTrackName = nm;
                    cancelAdResumeNotification();
                    maybeAutoResume(isPaused);
                }

                String title = track.optString("name", "Unknown");
                String artist = "";
                JSONArray artists = track.optJSONArray("artists");
                if (artists != null && artists.length() > 0) {
                    artist = artists.optJSONObject(0).optString("name", "");
                }
                String imageUrl = track.isNull("image") ? null : track.optString("image", null);
                if (imageUrl != null && (imageUrl.isEmpty() || "null".equals(imageUrl))) {
                    imageUrl = null;
                }

                Intent broadcastIntent = new Intent(UPDATE_MEDIA_ACTION);
                broadcastIntent.setPackage(getPackageName());
                broadcastIntent.putExtra("title", title);
                broadcastIntent.putExtra("artist", artist);
                broadcastIntent.putExtra("current", positionMs);
                broadcastIntent.putExtra("duration", durationMs);
                broadcastIntent.putExtra("art", imageUrl);
                broadcastIntent.putExtra("playing", !isPaused);
                sendBroadcast(broadcastIntent);
            } catch (Exception e) {
                Log.e(TAG, "Error forwarding player state", e);
            }
        });
    }

    private void handleAdWatchdog(String kind) {
        long now = System.currentTimeMillis();
        if (!"ad".equals(kind)) {
            adFirstSeenAt = 0;
            return;
        }
        if (adFirstSeenAt == 0) adFirstSeenAt = now;
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        long lastRestart = prefs.getLong(KEY_LAST_AD_RESTART, 0);
        if (AdRestartPolicy.shouldRestart(adFirstSeenAt, lastRestart, now)) {
            prefs.edit().putLong(KEY_LAST_AD_RESTART, now).apply();
            if (isForeground) {
                // Foreground: renderer is active, so reload + auto-resume works.
                Log.d(TAG, "Sustained ad (foreground); reloading player to resume ad-free");
                reloadAndResume();
            } else {
                // Background: a reload cannot restart playback (renderer throttled), so
                // ask the user to tap; the tap brings us foreground and resumes.
                Log.d(TAG, "Sustained ad (background); posting resume notification");
                adFirstSeenAt = 0;
                postAdResumeNotification();
            }
        }
    }

    private void armAutoResume() {
        pendingAutoResume = true;
        autoResumeDeadline = System.currentTimeMillis() + AUTO_RESUME_WINDOW_MS;
        adFirstSeenAt = 0;
        cancelAdResumeNotification();
    }

    private void reloadAndResume() {
        armAutoResume();
        WebView playerWebView = webPlayerHost != null ? webPlayerHost.getWebView() : null;
        if (playerWebView != null) {
            // A fresh page load drops the in-page ad session and restores the track,
            // without killing the process (which Android 14+ blocks from relaunching).
            playerWebView.reload();
        }
        // Trusted autoclicker tap resumes playback where synthetic JS clicks fail.
        ResumeAccessibilityService.requestResume();
    }

    private void postAdResumeNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        Intent resumeIntent = new Intent(this, MainActivity.class);
        resumeIntent.putExtra(EXTRA_RESUME_AD, true);
        resumeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pending = PendingIntent.getActivity(
            this, 1, resumeIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        String text = lastTrackName.isEmpty()
            ? "Ketuk untuk melanjutkan lagu terakhir"
            : "Ketuk untuk lanjut: " + lastTrackName;
        Notification notification = new NotificationCompat.Builder(this, MediaPlaybackService.CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Iklan terdeteksi")
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build();
        nm.notify(AD_RESUME_NOTIFICATION_ID, notification);
    }

    private void cancelAdResumeNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.cancel(AD_RESUME_NOTIFICATION_ID);
    }

    private void maybeAutoResume(boolean isPaused) {
        if (!pendingAutoResume) return;
        if (System.currentTimeMillis() > autoResumeDeadline || !isPaused) {
            pendingAutoResume = false;
            return;
        }
        // When the accessibility service is enabled it performs the trusted tap;
        // only fall back to synthetic JS clicks when it is unavailable.
        if (ResumeAccessibilityService.isReady()) return;
        WebView playerWebView = webPlayerHost != null ? webPlayerHost.getWebView() : null;
        if (playerWebView != null) {
            // Open NPV play button when expanded; otherwise tap the floating-bar cover,
            // which expands the NPV and reactivates the restored (paused) track.
            playerWebView.evaluateJavascript(
                "(function(){var b=document.querySelector('[data-testid=\"npv-play-button\"]');" +
                "if(b){b.click();return;}" +
                "var bar=document.querySelector('[data-testid=\"floating-now-playing-bar\"]');" +
                "var img=bar&&bar.querySelector('img');if(img)img.click();})()", null);
        }
    }

    @Override
    public void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent == null) return;
        if (intent.getBooleanExtra(EXTRA_RESUME_AD, false)) {
            reloadAndResume();
            return;
        }
        if (intent.getData() == null) return;

        Uri data = intent.getData();
        if (!"com.cpotify.app".equals(data.getScheme()) || !data.toString().contains("callback")) {
            return;
        }

        String url = data.toString();
        String js = "window.dispatchEvent(new CustomEvent('spotify:auth-callback', { detail: " + JSONObject.quote(url) + " }))";
        WebView playerWebView = webPlayerHost != null ? webPlayerHost.getWebView() : null;
        if (playerWebView != null) {
            playerWebView.evaluateJavascript(js, null);
        } else if (getBridge() != null && getBridge().getWebView() != null) {
            getBridge().getWebView().evaluateJavascript(js, null);
        }
    }

    @Override
    public void onDestroy() {
        if (mediaControlReceiver != null) {
            try {
                unregisterReceiver(mediaControlReceiver);
            } catch (IllegalArgumentException ignored) {}
        }
        if (webPlayerHost != null) {
            webPlayerHost.destroy();
        }
        super.onDestroy();
    }
}