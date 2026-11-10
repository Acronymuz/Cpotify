# Cpotify

A native Android Spotify client built with Capacitor, Vue 3, and Spotify Web Playback SDK. Features native playback, background audio, ad skip, and media session integration.

## Features

- **Native WebView** - Full Spotify Web Player experience
- **Spotify Web Playback SDK** - Direct audio streaming
- **Ad Skip** - Automatically detects and skips ads (Premium-like experience)
- **Background Playback** - Foreground service with notification controls
- **Media Session** - Lock screen, notification shade, Bluetooth/headset controls
- **Offline-first** - Caches artwork and metadata
- **Modern UI** - Spotify-like dark theme with Vue 3

## Prerequisites

- Node.js 18+
- Android Studio (for native builds)
- Spotify Developer Account

## Setup

### 1. Clone and Install

```bash
cd Cpotify
npm install
```

### 2. Configure Spotify API

1. Go to [Spotify Developer Dashboard](https://developer.spotify.com/dashboard)
2. Create a new app
3. Copy the **Client ID**
4. Add Redirect URIs:
   - `http://localhost:5173/callback` (for web development)
   - `com.cpotify.app://callback` (for Android)
5. Copy `.env.example` to `.env` and fill in your credentials:

```bash
cp .env.example .env
# Edit .env with your Client ID
```

### 3. Web Development

```bash
npm run dev
# Opens at http://localhost:5173
```

### 4. Android Build

```bash
# Sync Capacitor
npm run sync

# Open in Android Studio
npm run android

# Or build APK
cd android
./gradlew assembleDebug
```

## Project Structure

```
spotify-mobile/
├── src/
│   ├── components/       # Vue components (Header, Sidebar, PlayerBar)
│   ├── views/           # Page views (Home, Search, Library, etc.)
│   ├── stores/          # Pinia stores (auth, player)
│   ├── services/        # Spotify API service
│   ├── composables/     # Vue composables (playback SDK, native controls)
│   ├── types/           # TypeScript types
│   └── utils/           # Helper functions
├── android/             # Android native project
│   └── app/
│       └── src/main/java/com/cpotify/app/
│           ├── MainActivity.java        # WebView + MediaSession
│           └── MediaPlaybackService.java # Background playback service
└── capacitor.config.json
```

## Key Components

### Web Playback SDK Integration (`usePlaybackSDK.ts`)
- Loads Spotify Web Playback SDK
- Handles device registration and token management
- Real-time state synchronization
- **Ad detection and auto-skip** for free tier users

### Native Media Session (`MediaPlaybackService.java`)
- Android Foreground Service for background audio
- MediaSessionCompat for lock screen/notification controls
- Bluetooth/headset media button support
- Artwork download and caching

### Capacitor Plugins
- `@capacitor/app` - App lifecycle
- `@capacitor/haptics` - Tactile feedback
- `@capacitor/network` - Connectivity monitoring
- `@capacitor/preferences` - Secure token storage
- `@capacitor/splash-screen` - Native splash
- `@capacitor/status-bar` - Themed status bar

## Ad Skip Implementation

The ad skip feature works by:

1. Monitoring Web Playback SDK `track_window.current_track.type === 'ad'`
2. When ad detected, immediately calls `player.nextTrack()` 
3. Skips to next non-ad track in queue
4. Only works on free tier (Premium users don't get ads)

```typescript
// In usePlaybackSDK.ts
function checkForAd(currentTrack, nextTracks) {
  if (currentTrack?.type === 'ad' && adSkipEnabled.value) {
    const nextTrack = nextTracks.find(t => t.type !== 'ad')
    if (nextTrack) {
      player.value?.nextTrack()
    }
  }
}
```

## Building for Production

### Android

```bash
# Generate signed APK/AAB
cd android
./gradlew bundleRelease

# Or APK
./gradlew assembleRelease
```

### Web (PWA)

```bash
npm run build
# Output in www/ directory
```

## Troubleshooting

### Web Playback SDK not loading
- Ensure you're on HTTPS (required for SDK)
- Check CSP headers allow `sdk.scdn.co`
- Verify Client ID is correct

### Audio not playing on Android
- Enable `android:usesCleartextTraffic="true"` in Manifest
- Check WebView settings allow autoplay
- Verify MediaPlaybackService is running

### OAuth redirect not working
- Ensure redirect URI matches exactly in Spotify Dashboard
- For Android: `com.cpotify.app://callback`
- Check intent filters in AndroidManifest.xml

## License

Apache 2.0 - See LICENSE for details.

## Disclaimer

This is an unofficial Spotify client. Not affiliated with Spotify AB. Use at your own risk. Spotify Premium recommended for best experience.