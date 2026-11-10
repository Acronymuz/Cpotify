import { ref, onMounted, onUnmounted } from 'vue'
import { Capacitor } from '@capacitor/core'
import { usePlayerStore } from '@/stores/player'
import { usePlaybackSDK } from '@/composables/usePlaybackSDK'

export function useNativeMediaControls() {
  const playerStore = usePlayerStore()
  const playbackSDK = usePlaybackSDK()
  const isNative = Capacitor.isNativePlatform()
  const mediaSessionSupported = ref(false)

  async function initialize() {
    if (!isNative) return

    try {
      if ('mediaSession' in navigator) {
        mediaSessionSupported.value = true
        setupMediaSession()
        setupActionHandlers()
      }
    } catch (e) {
      console.error('Media session setup failed:', e)
      mediaSessionSupported.value = false
    }
  }

  function setupMediaSession() {
    const currentTrack = playerStore.currentTrack
    if (!currentTrack) return

    const metadata = {
      title: currentTrack.name,
      artist: currentTrack.artists.map(a => a.name).join(', '),
      album: currentTrack.album?.name || '',
      artwork: currentTrack.album?.images?.map(img => ({ src: img.url, sizes: `${img.width}x${img.height}`, type: 'image/png' })) || []
    }

    ;(navigator as any).mediaSession.metadata = new MediaMetadata(metadata)

    const playbackState = playerStore.isPlaying ? 'playing' : 'paused'
    ;(navigator as any).mediaSession.playbackState = playbackState
  }

  function setupActionHandlers() {
    ;(navigator as any).mediaSession.setActionHandler('play', async () => {
      await playbackSDK.playbackControls.play()
      updatePlaybackState('playing')
    })

    ;(navigator as any).mediaSession.setActionHandler('pause', async () => {
      await playbackSDK.playbackControls.pause()
      updatePlaybackState('paused')
    })

    ;(navigator as any).mediaSession.setActionHandler('seekto', async (details: any) => {
      if (details.seekTime !== undefined) {
        await playbackSDK.playbackControls.seek(Math.floor(details.seekTime * 1000))
      }
    })

    ;(navigator as any).mediaSession.setActionHandler('seekforward', async () => {
      await playbackSDK.playbackControls.seek(playerStore.progressMs + 10000)
    })

    ;(navigator as any).mediaSession.setActionHandler('seekbackward', async () => {
      await playbackSDK.playbackControls.seek(Math.max(0, playerStore.progressMs - 10000))
    })

    ;(navigator as any).mediaSession.setActionHandler('nexttrack', async () => {
      await playbackSDK.playbackControls.next()
    })

    ;(navigator as any).mediaSession.setActionHandler('previoustrack', async () => {
      await playbackSDK.playbackControls.previous()
    })

    ;(navigator as any).mediaSession.setActionHandler('stop', async () => {
      await playbackSDK.playbackControls.pause()
      updatePlaybackState('paused')
    })
  }

  function updatePlaybackState(state: 'playing' | 'paused' | 'stopped') {
    ;(navigator as any).mediaSession.playbackState = state
  }

  function updateMetadata() {
    if (!isNative || !mediaSessionSupported.value) return
    setupMediaSession()
  }

  onMounted(() => {
    initialize()
  })

  onUnmounted(() => {
    if (isNative && mediaSessionSupported.value) {
      ;(navigator as any).mediaSession.metadata = null
    }
  })

  return {
    initialize,
    updateMetadata,
    updatePlaybackState
  }
}