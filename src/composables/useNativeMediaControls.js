import { ref, onMounted, onUnmounted } from 'vue';
import { Capacitor } from '@capacitor/core';
import { usePlayerStore } from '@/stores/player';
import { usePlaybackSDK } from '@/composables/usePlaybackSDK';
export function useNativeMediaControls() {
    const playerStore = usePlayerStore();
    const playbackSDK = usePlaybackSDK();
    const isNative = Capacitor.isNativePlatform();
    const mediaSessionSupported = ref(false);
    async function initialize() {
        if (!isNative)
            return;
        try {
            if ('mediaSession' in navigator) {
                mediaSessionSupported.value = true;
                setupMediaSession();
                setupActionHandlers();
            }
        }
        catch (e) {
            console.error('Media session setup failed:', e);
            mediaSessionSupported.value = false;
        }
    }
    function setupMediaSession() {
        const currentTrack = playerStore.currentTrack;
        if (!currentTrack)
            return;
        const metadata = {
            title: currentTrack.name,
            artist: currentTrack.artists.map(a => a.name).join(', '),
            album: currentTrack.album?.name || '',
            artwork: currentTrack.album?.images?.map(img => ({ src: img.url, sizes: `${img.width}x${img.height}`, type: 'image/png' })) || []
        };
        navigator.mediaSession.metadata = new MediaMetadata(metadata);
        const playbackState = playerStore.isPlaying ? 'playing' : 'paused';
        navigator.mediaSession.playbackState = playbackState;
    }
    function setupActionHandlers() {
        ;
        navigator.mediaSession.setActionHandler('play', async () => {
            await playbackSDK.playbackControls.play();
            updatePlaybackState('playing');
        });
        navigator.mediaSession.setActionHandler('pause', async () => {
            await playbackSDK.playbackControls.pause();
            updatePlaybackState('paused');
        });
        navigator.mediaSession.setActionHandler('seekto', async (details) => {
            if (details.seekTime !== undefined) {
                await playbackSDK.playbackControls.seek(Math.floor(details.seekTime * 1000));
            }
        });
        navigator.mediaSession.setActionHandler('seekforward', async () => {
            await playbackSDK.playbackControls.seek(playerStore.progressMs + 10000);
        });
        navigator.mediaSession.setActionHandler('seekbackward', async () => {
            await playbackSDK.playbackControls.seek(Math.max(0, playerStore.progressMs - 10000));
        });
        navigator.mediaSession.setActionHandler('nexttrack', async () => {
            await playbackSDK.playbackControls.next();
        });
        navigator.mediaSession.setActionHandler('previoustrack', async () => {
            await playbackSDK.playbackControls.previous();
        });
        navigator.mediaSession.setActionHandler('stop', async () => {
            await playbackSDK.playbackControls.pause();
            updatePlaybackState('paused');
        });
    }
    function updatePlaybackState(state) {
        ;
        navigator.mediaSession.playbackState = state;
    }
    function updateMetadata() {
        if (!isNative || !mediaSessionSupported.value)
            return;
        setupMediaSession();
    }
    onMounted(() => {
        initialize();
    });
    onUnmounted(() => {
        if (isNative && mediaSessionSupported.value) {
            ;
            navigator.mediaSession.metadata = null;
        }
    });
    return {
        initialize,
        updateMetadata,
        updatePlaybackState
    };
}
