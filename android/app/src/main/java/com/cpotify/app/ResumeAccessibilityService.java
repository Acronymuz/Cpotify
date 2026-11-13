package com.cpotify.app;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Intent;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

/**
 * Autoclicker-style resume: performs a trusted tap (dispatchGesture) on the Spotify
 * play button after a reload. A real touch satisfies the autoplay policy that blocks
 * synthetic JS clicks, so playback of the restored track actually starts.
 */
public class ResumeAccessibilityService extends AccessibilityService {

    private static final String TAG = "ResumeAccessibility";
    private static final long FIRST_DELAY_MS = 2000;
    private static final long RETRY_DELAY_MS = 800;
    private static final int MAX_ATTEMPTS = 30;

    private static ResumeAccessibilityService instance;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private int attempts = 0;

    public static boolean isReady() {
        return instance != null;
    }

    public static void requestResume() {
        ResumeAccessibilityService s = instance;
        if (s != null) s.startResume();
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        Log.d(TAG, "Resume accessibility service connected");
    }

    @Override
    public boolean onUnbind(Intent intent) {
        instance = null;
        handler.removeCallbacks(resumeRunnable);
        return super.onUnbind(intent);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Resume is triggered explicitly by MainActivity; no continuous scraping needed.
    }

    @Override
    public void onInterrupt() {
    }

    private void startResume() {
        attempts = 0;
        handler.removeCallbacks(resumeRunnable);
        handler.postDelayed(resumeRunnable, FIRST_DELAY_MS);
    }

    private final Runnable resumeRunnable = new Runnable() {
        @Override
        public void run() {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            Rect playBounds = findPlayBounds(root);
            if (root != null) root.recycle();
            if (playBounds != null) {
                tap(playBounds.centerX(), playBounds.centerY());
                Log.d(TAG, "Tapped play button at " + playBounds.centerX() + "," + playBounds.centerY());
                return;
            }
            if (++attempts < MAX_ATTEMPTS) {
                handler.postDelayed(this, RETRY_DELAY_MS);
            } else {
                Log.w(TAG, "Play button not found; giving up resume tap");
            }
        }
    };

    private Rect findPlayBounds(AccessibilityNodeInfo root) {
        if (root == null) {
            Log.w(TAG, "Active window root is null");
            return null;
        }
        Rect found = traverseForPlay(root);
        if (found == null) {
            Log.w(TAG, "No play button in tree; root pkg=" + root.getPackageName()
                + " children=" + root.getChildCount());
        }
        return found;
    }

    // Manual traversal: findAccessibilityNodeInfosByText is unreliable for WebView
    // virtual nodes, so walk the tree checking content-description directly.
    private Rect traverseForPlay(AccessibilityNodeInfo node) {
        if (node == null) return null;
        CharSequence desc = node.getContentDescription();
        CharSequence cls = node.getClassName();
        if (desc != null && cls != null && cls.toString().contains("Button") && node.isClickable()
            && (desc.toString().equalsIgnoreCase("Play") || desc.toString().equalsIgnoreCase("Putar"))) {
            Rect b = new Rect();
            node.getBoundsInScreen(b);
            if (b.width() > 0 && b.height() > 0) return b;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            Rect found = traverseForPlay(child);
            if (child != null) child.recycle();
            if (found != null) return found;
        }
        return null;
    }

    private void tap(float x, float y) {
        Path path = new Path();
        path.moveTo(x, y);
        GestureDescription.StrokeDescription stroke =
            new GestureDescription.StrokeDescription(path, 0, 60);
        GestureDescription gesture = new GestureDescription.Builder().addStroke(stroke).build();
        dispatchGesture(gesture, null, null);
    }
}