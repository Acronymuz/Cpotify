package com.cpotify.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;

import androidx.annotation.Nullable;

import androidx.core.app.NotificationCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.support.v4.media.MediaMetadataCompat;
import androidx.media.app.NotificationCompat.MediaStyle;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
public class MediaPlaybackService extends Service {

    private static final String TAG = "MediaPlaybackService";
    private static final String CHANNEL_ID = "spotify_media_playback_v2";
    private static final int NOTIFICATION_ID = 1;
    private static final String UPDATE_MEDIA_ACTION = "com.cpotify.app.UPDATE_MEDIA";
    private static final String MEDIA_CONTROL_ACTION = "com.cpotify.app.MEDIA_CONTROL";

    private MediaSessionCompat mediaSession;
    private MediaControllerCompat mediaController;
    private PowerManager.WakeLock wakeLock;
    private BroadcastReceiver mediaUpdateReceiver;
    private BroadcastReceiver mediaControlReceiver;
    private NotificationManager notificationManager;

    private final ExecutorService artExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    
    private Bitmap artBitmap;
    private String currentArtUrl = "";
    private String pendingArtUrl = "";
    private String lastTitle = "";
    private String lastArtist = "";
    private long lastDuration = 0;
    private long lastPosition = 0;
    private boolean lastPlaying = true;

    private static MediaPlaybackService instance;

    public static MediaPlaybackService getInstance() {
        return instance;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        
        createNotificationChannel();
        setupMediaSession();
        registerReceivers();
        acquireWakeLock();
        
        // Start foreground service immediately with initial notification
        startForegroundService();
        
        Log.d(TAG, "Service created");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            if (MEDIA_CONTROL_ACTION.equals(action)) {
                String control = intent.getStringExtra("control");
                if (control != null) {
                    handleMediaControl(control);
                }
            }
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (mediaUpdateReceiver != null) {
            try {
                unregisterReceiver(mediaUpdateReceiver);
            } catch (IllegalArgumentException ignored) {}
        }
        if (mediaControlReceiver != null) {
            try {
                unregisterReceiver(mediaControlReceiver);
            } catch (IllegalArgumentException ignored) {}
        }
        if (mediaSession != null) {
            mediaSession.release();
        }
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        instance = null;
        super.onDestroy();
        Log.d(TAG, "Service destroyed");
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Initialize notificationManager here if not already done
            if (notificationManager == null) {
                notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            }
            
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                getString(R.string.spotify_media_playback),
                NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription(getString(R.string.spotify_media_playback_desc));
            channel.setShowBadge(true);
            channel.setSound(null, null);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    private void setupMediaSession() {
        mediaSession = new MediaSessionCompat(this, "SpotifyMobileMediaSession");
        mediaSession.setFlags(
            MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS |
            MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
        );

        mediaSession.setCallback(new MediaSessionCompat.Callback() {
            @Override
            public void onPlay() {
                sendMediaControl("play");
            }

            @Override
            public void onPause() {
                sendMediaControl("pause");
            }

            @Override
            public void onSkipToNext() {
                sendMediaControl("next");
            }

            @Override
            public void onSkipToPrevious() {
                sendMediaControl("prev");
            }

            @Override
            public void onSeekTo(long pos) {
                sendMediaControl("seek:" + pos);
            }
        });

        mediaController = mediaSession.getController();
        
        // Initial playback state
        PlaybackStateCompat state = new PlaybackStateCompat.Builder()
            .setState(PlaybackStateCompat.STATE_PLAYING, 0, 1.0f)
            .setActions(
                PlaybackStateCompat.ACTION_PLAY |
                PlaybackStateCompat.ACTION_PAUSE |
                PlaybackStateCompat.ACTION_PLAY_PAUSE |
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT |
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS |
                PlaybackStateCompat.ACTION_SEEK_TO
            )
            .build();
        mediaSession.setPlaybackState(state);

        // Initial metadata
        MediaMetadataCompat metadata = new MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "Spotify")
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, "Playing Music")
            .build();
        mediaSession.setMetadata(metadata);

        mediaSession.setActive(true);
        
        // Set media button receiver
        Intent mediaButtonIntent = new Intent(Intent.ACTION_MEDIA_BUTTON);
        mediaButtonIntent.setPackage(getPackageName());
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
            this, 0, mediaButtonIntent, PendingIntent.FLAG_IMMUTABLE);
        mediaSession.setMediaButtonReceiver(pendingIntent);
    }

    private void acquireWakeLock() {
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (powerManager != null) {
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "SpotifyMobile::MediaPlaybackService");
            wakeLock.acquire();
        }
    }

    private void registerReceivers() {
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        // Receiver for media metadata updates from WebView
        mediaUpdateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (UPDATE_MEDIA_ACTION.equals(intent.getAction())) {
                    String title = intent.getStringExtra("title");
                    String artist = intent.getStringExtra("artist");
                    long position = intent.getLongExtra("current", 0);
                    long duration = intent.getLongExtra("duration", 0);
                    String artUrl = intent.getStringExtra("art");
                    boolean playing = intent.getBooleanExtra("playing", true);
                    updateNotification(title, artist, position, duration, artUrl, playing);
                    if (artUrl != null && !artUrl.isEmpty() && !"null".equals(artUrl)) {
                        downloadArtwork(artUrl);
                    }
                }
            }
        };

        IntentFilter updateFilter = new IntentFilter(UPDATE_MEDIA_ACTION);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(mediaUpdateReceiver, updateFilter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(mediaUpdateReceiver, updateFilter);
        }

        // Media control commands are handled by MainActivity against the player WebView.
    }

    private void startForegroundService() {
        // Build initial notification
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Cpotify")
            .setContentText("Ready to play")
            .setLargeIcon(BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setStyle(new MediaStyle()
                .setMediaSession(mediaSession.getSessionToken())
                .setShowActionsInCompactView(0, 1, 2));

        // Add actions
        builder.addAction(new NotificationCompat.Action(
            android.R.drawable.ic_media_play, "Play", getPendingIntent("play")));
        builder.addAction(new NotificationCompat.Action(
            android.R.drawable.ic_media_next, "Next", getPendingIntent("next")));
        builder.addAction(new NotificationCompat.Action(
            android.R.drawable.ic_media_previous, "Previous", getPendingIntent("prev")));

        Notification notification = builder.build();
        startForeground(NOTIFICATION_ID, notification);
    }

    private void handleMediaControl(String control) {
        String js = "";
        switch (control) {
            case "play":
                js = "window.dispatchEvent(new CustomEvent('spotify:play'))";
                break;
            case "pause":
                js = "window.dispatchEvent(new CustomEvent('spotify:pause'))";
                break;
            case "next":
                js = "window.dispatchEvent(new CustomEvent('spotify:next'))";
                break;
            case "prev":
                js = "window.dispatchEvent(new CustomEvent('spotify:prev'))";
                break;
            default:
                if (control.startsWith("seek:")) {
                    String pos = control.substring(5);
                    js = "window.dispatchEvent(new CustomEvent('spotify:seek', { detail: " + pos + " }))";
                }
                break;
        }
        
        if (!js.isEmpty()) {
            sendToWebView(js);
        }
    }

    private void sendMediaControl(String control) {
        Intent intent = new Intent(MEDIA_CONTROL_ACTION);
        intent.setPackage(getPackageName());
        intent.putExtra("control", control);
        sendBroadcast(intent);
    }

    private void sendToWebView(String js) {
        // This will be called from WebView via MainActivity
        // We just broadcast the action to the MainActivity
    }

    private void updateNotification(String title, String artist, long position, long duration, String artUrl, boolean playing) {
        // Update metadata
        MediaMetadataCompat metadata = new MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration)
            .build();
        mediaSession.setMetadata(metadata);

        // Update playback state
        PlaybackStateCompat state = new PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY |
                PlaybackStateCompat.ACTION_PAUSE |
                PlaybackStateCompat.ACTION_PLAY_PAUSE |
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT |
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS |
                PlaybackStateCompat.ACTION_SEEK_TO
            )
            .setState(playing ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED,
                    position, 1.0f)
            .build();
        mediaSession.setPlaybackState(state);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(artist)
            .setLargeIcon(artBitmap != null ? artBitmap : BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setOngoing(playing)
            .setShowWhen(true)
            .setUsesChronometer(false)
            .setContentIntent(PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE))
            .setProgress(duration > 0 ? (int) Math.min(duration, Integer.MAX_VALUE) : 0,
                (int) Math.min(position, Integer.MAX_VALUE), false)
            .setStyle(new MediaStyle()
                .setMediaSession(mediaSession.getSessionToken())
                .setShowActionsInCompactView(0, 1, 2));

        // Add actions
        if (playing) {
            builder.addAction(new NotificationCompat.Action(
                android.R.drawable.ic_media_pause, "Pause", getPendingIntent("pause")));
        } else {
            builder.addAction(new NotificationCompat.Action(
                android.R.drawable.ic_media_play, "Play", getPendingIntent("play")));
        }
        builder.addAction(new NotificationCompat.Action(
            android.R.drawable.ic_media_next, "Next", getPendingIntent("next")));
        builder.addAction(new NotificationCompat.Action(
            android.R.drawable.ic_media_previous, "Previous", getPendingIntent("prev")));

        if (notificationManager != null) {
            notificationManager.notify(NOTIFICATION_ID, builder.build());
        }
    }

    private PendingIntent getPendingIntent(String control) {
        Intent intent = new Intent(MEDIA_CONTROL_ACTION);
        intent.setPackage(getPackageName());
        intent.putExtra("control", control);
        // Use positive request code to avoid issues with negative hashCode
        int requestCode = Math.abs(control.hashCode());
        int flags = PendingIntent.FLAG_IMMUTABLE;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // On Android 12+, we can use FLAG_MUTABLE but not both
            // FLAG_IMMUTABLE is the default secure option
        }
        return PendingIntent.getBroadcast(this, requestCode, intent, flags);
    }

    public void updateArtwork(Bitmap bitmap) {
        artBitmap = bitmap;
        // Rebuild notification with new artwork
        if (mediaController != null) {
            MediaMetadataCompat metadata = mediaController.getMetadata();
            if (metadata != null) {
                String title = metadata.getString(MediaMetadataCompat.METADATA_KEY_TITLE);
                String artist = metadata.getString(MediaMetadataCompat.METADATA_KEY_ARTIST);
                long duration = metadata.getLong(MediaMetadataCompat.METADATA_KEY_DURATION);
                PlaybackStateCompat state = mediaController.getPlaybackState();
                long position = state != null ? state.getPosition() : 0;
                boolean playing = state != null && state.getState() == PlaybackStateCompat.STATE_PLAYING;
                updateNotification(title, artist, position, duration, null, playing);
            }
        }
    }

    public void downloadArtwork(String artUrl) {
        if (artUrl == null || artUrl.isEmpty() || artUrl.equals(currentArtUrl)) {
            return;
        }
        if (!isAllowedArtworkUrl(artUrl)) {
            Log.w(TAG, "Artwork URL rejected: " + artUrl);
            return;
        }

        currentArtUrl = artUrl;

        artExecutor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(artUrl);
                connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(10000);
                connection.setDoInput(true);
                connection.connect();
                try (InputStream input = connection.getInputStream()) {
                    Bitmap bitmap = BitmapFactory.decodeStream(input);
                    if (bitmap != null) {
                        mainHandler.post(() -> updateArtwork(bitmap));
                    }
                }
            } catch (Exception e) {
                currentArtUrl = "";
                Log.e(TAG, "Error downloading artwork", e);
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    private boolean isAllowedArtworkUrl(String artUrl) {
        try {
            URL url = new URL(artUrl);
            if (!"https".equalsIgnoreCase(url.getProtocol())) {
                return false;
            }
            String host = url.getHost();
            if (host == null) {
                return false;
            }
            host = host.toLowerCase(java.util.Locale.ROOT);
            return host.equals("scdn.co") || host.endsWith(".scdn.co") ||
                host.equals("spotifycdn.com") || host.endsWith(".spotifycdn.com");
        } catch (Exception e) {
            return false;
        }
    }

    private void updatePlaybackState(long position, boolean playing) {
        PlaybackStateCompat state = new PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY |
                PlaybackStateCompat.ACTION_PAUSE |
                PlaybackStateCompat.ACTION_PLAY_PAUSE |
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT |
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS |
                PlaybackStateCompat.ACTION_SEEK_TO
            )
            .setState(playing ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED,
                    position, 1.0f)
            .build();
        mediaSession.setPlaybackState(state);

        // Update notification
        if (mediaController != null) {
            MediaMetadataCompat metadata = mediaController.getMetadata();
            if (metadata != null) {
                String title = metadata.getString(MediaMetadataCompat.METADATA_KEY_TITLE);
                String artist = metadata.getString(MediaMetadataCompat.METADATA_KEY_ARTIST);
                long duration = metadata.getLong(MediaMetadataCompat.METADATA_KEY_DURATION);
                updateNotification(title, artist, position, duration, null, playing);
            }
        }
    }
}