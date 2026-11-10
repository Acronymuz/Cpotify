// @ts-nocheck
import { ref, computed, onUnmounted } from 'vue'
import { Capacitor } from '@capacitor/core'
import { useAuthStore } from '@/stores/auth'
import { usePlayerStore } from '@/stores/player'
import type { PlayerInstance, WebPlaybackState, WebPlaybackTrack } from '@/types/spotify'

const PLAYER_NAME = 'Cpotify Web Player'

export function usePlaybackSDK() {
  const authStore = useAuthStore()
  const playerStore = usePlayerStore()
  const player = ref<PlayerInstance | null>(null)
  const deviceId = ref<string | null>(null)
  const isReady = ref(false)
  const isConnecting = ref(false)
  const error = ref<string | null>(null)
  const adSkipAttempted = ref(false)
  const isPremium = computed(() => authStore.isPremium)

  function loadSDK(): Promise<void> { return new Promise((resolve, reject) => { if ((window as any).Spotify) { resolve(); return } const s = document.createElement('script'); s.src = 'https://sdk.scdn.co/spotify-player.js'; s.async = true; s.onload = () => resolve(); s.onerror = () => reject(new Error('Failed to load Spotify Web Playback SDK')); document.head.appendChild(s) }) }

  async function initialize(): Promise<boolean> { if (!authStore.isAuthenticated) { error.value = 'Not authenticated'; return false } if (isReady.value && player.value) return true; isConnecting.value = true; error.value = null; try { await loadSDK(); const S = (window as any).Spotify; player.value = new S.Player({ name: PLAYER_NAME, getOAuthToken: (cb: (t: string) => void) => { authStore.ensureValidToken().then(t => { if (t) cb(t) }) }, volume: playerStore.volume, enableMediaSession: true }); setupEventListeners(player.value); const connected = await player.value.connect(); if (connected) { isReady.value = true; const st = await player.value.getCurrentState(); if (st) { deviceId.value = st.device_id || null; handleStateChange(st) } await playerStore.loadPreferences(); return true } else throw new Error('Failed to connect to Spotify') } catch (e) { error.value = e instanceof Error ? e.message : 'Failed to initialize playback'; console.error(e); return false } finally { isConnecting.value = false } }

  function setupEventListeners(p: PlayerInstance) {
    p.addListener('initialization_error', ({ message }: { message: string }) => { console.error('SDK Init Error:', message); error.value = message; isReady.value = false })
    p.addListener('authentication_error', ({ message }: { message: string }) => { console.error('SDK Auth Error:', message); error.value = message; handleAuthError() })
    p.addListener('account_error', ({ message }: { message: string }) => { console.error('SDK Account Error:', message); error.value = message })
    p.addListener('playback_error', ({ message }: { message: string }) => { console.error('SDK Playback Error:', message); error.value = message })
    p.addListener('player_state_changed', (st: WebPlaybackState | null) => { if (st) handleStateChange(st); else playerStore.clearPlayback() })
    p.addListener('ready', ({ device_id }: { device_id: string }) => { console.log('Player ready:', device_id); deviceId.value = device_id; isReady.value = true })
    p.addListener('not_ready', ({ device_id }: { device_id: string }) => { console.log('Player not ready:', device_id); deviceId.value = null; isReady.value = false })
  }

  function handleStateChange(st: WebPlaybackState) { const ct = st.track_window.current_track; const nt = st.track_window.next_tracks; playerStore.updateFromWebPlayback({ position: st.position, duration: st.duration, paused: st.paused, track: ct, nextTracks: nt, disallows: st.disallows }); checkForAd(ct, nt); syncWithNative(st) }

  function checkForAd(ct: WebPlaybackTrack | null, nt: WebPlaybackTrack[]) { if (!ct) return; if (ct.type === 'ad' && playerStore.adSkipEnabled && !adSkipAttempted.value) { adSkipAttempted.value = true; const nxt = nt.find(t => t.type !== 'ad'); if (nxt) { console.log('Ad detected, skip to:', nxt.name); player.value?.nextTrack().then(() => adSkipAttempted.value = false).catch(() => adSkipAttempted.value = false) } } else if (ct.type !== 'ad') adSkipAttempted.value = false }

  async function handleAuthError() { const ok = await authStore.refreshAccessToken(authStore.tokens?.refresh_token || ''); if (ok) await initialize(); else await authStore.logout() }

  function syncWithNative(st: WebPlaybackState) { if (Capacitor.isNativePlatform()) { const ct = st.track_window.current_track; if (ct) { const intent = { action: 'com.cpotify.app.UPDATE_MEDIA', title: ct.name, artist: ct.artist_name, current: st.position, duration: st.duration, art: ct.images[0]?.url || '', playing: !st.paused }; try { (window as any).Android?.sendBroadcast?.(JSON.stringify(intent)) } catch {} } } }

  async function syncState() { if (player.value && isReady.value) { const st = await player.value.getCurrentState(); if (st) handleStateChange(st) } }
  async function transferPlayback(): Promise<boolean> { if (!deviceId.value) return false; try { await authStore.ensureValidToken(); const r = await fetch('https://api.spotify.com/v1/me/player', { method: 'PUT', headers: { Authorization: `Bearer ${authStore.accessToken}`, 'Content-Type': 'application/json' }, body: JSON.stringify({ device_ids: [deviceId.value] }) }); return r.ok } catch { return false } }

  const playbackControls = { play: () => player.value?.resume(), pause: () => player.value?.pause(), toggle: () => player.value?.togglePlay(), next: () => player.value?.nextTrack(), previous: () => player.value?.previousTrack(), seek: (ms: number) => player.value?.seek(ms), setVolume: (v: number) => player.value?.setVolume(v), addToQueue: (uri: string) => player.value?.addToQueue(uri) }
  function getCurrentState() { return player.value?.getCurrentState() }
  function disconnect() { if (player.value) { player.value.disconnect(); player.value = null; isReady.value = false; deviceId.value = null } }
  onUnmounted(disconnect)
  return { player, deviceId, isReady, isConnecting, error, isPremium, initialize, disconnect, transferPlayback, syncState, playbackControls, getCurrentState }
}