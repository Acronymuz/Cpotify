import { defineStore } from 'pinia';
import { ref, computed, watch } from 'vue';
import { Preferences } from '@capacitor/preferences';
const STORAGE_KEYS = { VOLUME: 'player_volume', REPEAT_MODE: 'player_repeat_mode', SHUFFLE: 'player_shuffle', AD_SKIP_ENABLED: 'player_ad_skip_enabled' };
function createTrackFromWebPlayback(t) {
    return {
        id: t.id,
        name: t.name,
        artists: [{ id: '', name: t.artist_name, type: 'artist', uri: '', external_urls: { spotify: '' }, href: '' }],
        album: {
            id: '',
            name: t.album_name,
            type: 'album',
            uri: t.album_uri,
            external_urls: { spotify: '' },
            href: '',
            images: t.images.map(i => ({ url: i.url, height: i.height, width: i.width })),
            artists: [],
            release_date: '',
            release_date_precision: 'year',
            total_tracks: 0,
            album_type: 'album'
        },
        duration_ms: t.duration_ms,
        external_urls: { spotify: t.external_urls.spotify },
        uri: t.uri,
        is_playable: t.playable,
        preview_url: t.preview_url,
        track_number: t.track_number,
        disc_number: t.disc_number,
        explicit: t.explicit,
        popularity: 0,
        type: 'track',
        href: ''
    };
}
export const usePlayerStore = defineStore('player', () => {
    const currentTrack = ref(null);
    const playbackState = ref(null);
    const devices = ref([]);
    const activeDevice = ref(null);
    const queue = ref([]);
    const volume = ref(0.7);
    const repeatMode = ref('off');
    const shuffle = ref(false);
    const isLoading = ref(false);
    const error = ref(null);
    const adSkipEnabled = ref(true);
    const lastAdSkipTime = ref(0);
    const webPlaybackState = ref(null);
    const hasActivePlayback = computed(() => currentTrack.value !== null);
    const isPlaying = computed(() => playbackState.value?.is_playing ?? webPlaybackState.value?.paused === false);
    const progress = computed(() => { if (webPlaybackState.value)
        return webPlaybackState.value.duration > 0 ? webPlaybackState.value.position / webPlaybackState.value.duration : 0; if (playbackState.value?.progress_ms && currentTrack.value?.duration_ms)
        return playbackState.value.progress_ms / currentTrack.value.duration_ms; return 0; });
    const progressMs = computed(() => webPlaybackState.value?.position ?? playbackState.value?.progress_ms ?? 0);
    const durationMs = computed(() => webPlaybackState.value?.duration ?? currentTrack.value?.duration_ms ?? 0);
    const currentTrackUri = computed(() => currentTrack.value?.uri);
    const nextTrack = computed(() => queue.value[0]);
    const canSkipNext = computed(() => playbackState.value?.actions.disallows.skipping_next === false || webPlaybackState.value?.disallows.skipping_next === false);
    const canSkipPrev = computed(() => playbackState.value?.actions.disallows.skipping_prev === false || webPlaybackState.value?.disallows.skipping_prev === false);
    const canSeek = computed(() => playbackState.value?.actions.disallows.seeking === false && webPlaybackState.value?.disallows.seeking === false);
    async function loadPreferences() { try {
        const [v, r, s, a] = await Promise.all([Preferences.get({ key: STORAGE_KEYS.VOLUME }), Preferences.get({ key: STORAGE_KEYS.REPEAT_MODE }), Preferences.get({ key: STORAGE_KEYS.SHUFFLE }), Preferences.get({ key: STORAGE_KEYS.AD_SKIP_ENABLED })]);
        if (v.value)
            volume.value = parseFloat(v.value);
        if (r.value)
            repeatMode.value = r.value;
        if (s.value)
            shuffle.value = s.value === 'true';
        if (a.value)
            adSkipEnabled.value = a.value === 'true';
    }
    catch { } }
    async function savePreferences() { await Promise.all([Preferences.set({ key: STORAGE_KEYS.VOLUME, value: volume.value.toString() }), Preferences.set({ key: STORAGE_KEYS.REPEAT_MODE, value: repeatMode.value }), Preferences.set({ key: STORAGE_KEYS.SHUFFLE, value: shuffle.value.toString() }), Preferences.set({ key: STORAGE_KEYS.AD_SKIP_ENABLED, value: adSkipEnabled.value.toString() })]); }
    function updateFromWebPlayback(st) {
        webPlaybackState.value = { position: st.position, duration: st.duration, paused: st.paused, track: st.track, nextTracks: st.nextTracks, disallows: st.disallows };
        if (st.track)
            currentTrack.value = createTrackFromWebPlayback(st.track);
        queue.value = st.nextTracks.map(t => ({ ...t, type: t.type }));
    }
    function updateFromPlaybackState(st) { playbackState.value = st; activeDevice.value = st.device; if (st.item)
        currentTrack.value = st.item; repeatMode.value = st.repeat_state; shuffle.value = st.shuffle_state; volume.value = st.device.volume_percent ? st.device.volume_percent / 100 : volume.value; }
    async function setVolume(v) { volume.value = Math.max(0, Math.min(1, v)); await savePreferences(); }
    async function setRepeatMode(m) { repeatMode.value = m; await savePreferences(); }
    async function setShuffle(e) { shuffle.value = e; await savePreferences(); }
    async function setAdSkipEnabled(e) { adSkipEnabled.value = e; await Preferences.set({ key: STORAGE_KEYS.AD_SKIP_ENABLED, value: e.toString() }); }
    function checkAndSkipAd() { if (!adSkipEnabled.value)
        return false; if (!webPlaybackState.value?.track)
        return false; const t = webPlaybackState.value.track; if (t.type === 'ad') {
        const now = Date.now();
        if (now - lastAdSkipTime.value > 5000) {
            lastAdSkipTime.value = now;
            return true;
        }
    } return false; }
    function clearPlayback() { currentTrack.value = null; playbackState.value = null; queue.value = []; webPlaybackState.value = null; }
    watch(volume, v => Preferences.set({ key: STORAGE_KEYS.VOLUME, value: v.toString() }));
    watch(repeatMode, v => Preferences.set({ key: STORAGE_KEYS.REPEAT_MODE, value: v }));
    watch(shuffle, v => Preferences.set({ key: STORAGE_KEYS.SHUFFLE, value: v.toString() }));
    return { currentTrack, playbackState, devices, activeDevice, queue, volume, repeatMode, shuffle, isLoading, error, adSkipEnabled, webPlaybackState, hasActivePlayback, isPlaying, progress, progressMs, durationMs, currentTrackUri, nextTrack, canSkipNext, canSkipPrev, canSeek, loadPreferences, savePreferences, updateFromWebPlayback, updateFromPlaybackState, setVolume, setRepeatMode, setShuffle, setAdSkipEnabled, checkAndSkipAd, clearPlayback };
});
