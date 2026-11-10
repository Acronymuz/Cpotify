<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import { usePlayerStore } from '@/stores/player'
import { usePlaybackSDK } from '@/composables/usePlaybackSDK'
import { Haptics, ImpactStyle } from '@capacitor/haptics'
import { formatDuration } from '@/utils/helpers'

const playerStore = usePlayerStore()
const playbackSDK = usePlaybackSDK()

const isExpanded = ref(false)
const volumeSliderVisible = ref(false)
const progress = ref(0)
let progressInterval: ReturnType<typeof setInterval> | null = null
const volumeBeforeMute = ref(0.7)

const currentTrack = computed(() => playerStore.currentTrack)
const isPlaying = computed(() => playerStore.isPlaying)
const trackImage = computed(() => currentTrack.value?.album?.images?.[0]?.url)
const trackName = computed(() => currentTrack.value?.name || 'Unknown Track')
const artistName = computed(() => currentTrack.value?.artists?.map(a => a.name).join(', ') || 'Unknown Artist')
const currentTime = computed(() => formatDuration(playerStore.progressMs))
const totalTime = computed(() => formatDuration(playerStore.durationMs))

function togglePlay() { Haptics.impact({ style: ImpactStyle.Light }); if (isPlaying.value) playbackSDK.playbackControls.pause(); else playbackSDK.playbackControls.play() }
function nextTrack() { Haptics.impact({ style: ImpactStyle.Light }); playbackSDK.playbackControls.next() }
function previousTrack() { Haptics.impact({ style: ImpactStyle.Light }); playbackSDK.playbackControls.previous() }
function handleProgressInput(e: Event) { const t = e.target as HTMLInputElement; progress.value = parseFloat(t.value); playbackSDK.playbackControls.seek(parseFloat(t.value) * playerStore.durationMs) }
function handleProgressChange(e: Event) { const t = e.target as HTMLInputElement; playbackSDK.playbackControls.seek(parseFloat(t.value) * playerStore.durationMs) }
function handleVolumeInput(e: Event) { const t = e.target as HTMLInputElement; const v = parseFloat(t.value); playerStore.setVolume(v); playbackSDK.playbackControls.setVolume(v) }
function toggleMute() { Haptics.impact({ style: ImpactStyle.Light }); if (playerStore.volume > 0) { volumeBeforeMute.value = playerStore.volume; playerStore.setVolume(0); playbackSDK.playbackControls.setVolume(0) } else { playerStore.setVolume(volumeBeforeMute.value); playbackSDK.playbackControls.setVolume(volumeBeforeMute.value) } }
function toggleRepeat() { Haptics.impact({ style: ImpactStyle.Light }); const m: ('off'|'track'|'context')[] = ['off','context','track']; playerStore.setRepeatMode(m[(m.indexOf(playerStore.repeatMode)+1)%3]) }
function toggleShuffle() { Haptics.impact({ style: ImpactStyle.Light }); playerStore.setShuffle(!playerStore.shuffle) }
function toggleExpand() { isExpanded.value = !isExpanded.value }
function navigateToNowPlaying() {}

watch(() => playerStore.isPlaying, p => { if (p && !progressInterval) progressInterval = setInterval(() => progress.value = playerStore.progress, 500); else if (!p && progressInterval) { clearInterval(progressInterval); progressInterval = null } })
watch(() => playerStore.currentTrack, () => progress.value = 0)
onMounted(() => progress.value = playerStore.progress)
onUnmounted(() => { if (progressInterval) clearInterval(progressInterval) })
</script>

<template>
  <div class="player-bar fixed bottom-0 left-0 right-0 h-[var(--player-height)] bg-black/90 backdrop-blur-xl border-t border-white/10 z-50 flex items-center" :class="{ expanded: isExpanded }"><div class="track-info flex items-center gap-3 px-4 min-w-0 flex-1" @click="navigateToNowPlaying"><div v-if="trackImage" class="track-image w-14 h-14 rounded flex-shrink-0 relative overflow-hidden bg-gray"><img :src="trackImage" alt="" class="w-full h-full object-cover"><div v-if="playerStore.webPlaybackState?.track?.type === 'ad'" class="absolute top-1 right-1"><span class="badge badge-ad text-xs px-1.5 py-0.5">AD</span></div></div><div v-else class="track-image w-14 h-14 rounded flex-shrink-0 bg-gray flex items-center justify-center"><svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" class="text-gray"><path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-2 15l-5-5 1.41-1.41L10 14.17l7.59-7.59L19 8l-9 9z"/></svg></div><div class="track-details min-w-0"><p class="font-medium truncate text-white">{{ trackName }}</p><p class="text-xs text-gray truncate">{{ artistName }}</p></div></div><div class="controls flex flex-col items-center gap-2 px-4 flex-1"><div class="control-buttons flex items-center gap-4"><button @click="toggleShuffle" :class="['icon-btn p-2 text-xs', playerStore.shuffle?'text-green':'text-gray']" aria-label="Shuffle" :aria-pressed="playerStore.shuffle"><svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="16 3 21 3 21 8"></polyline><line x1="4" y1="20" x2="21" y2="3"></line><polyline points="21 16 21 21 16 21"></polyline><line x1="15" y1="15" x2="21" y2="21"></line><line x1="4" y1="4" x2="21" y2="21"></line></svg></button><button @click="previousTrack" class="icon-btn p-2" aria-label="Previous" :disabled="!playerStore.canSkipPrev"><svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor"><path d="M6 6h2v12H6zm3.5 6l8.5 6V6z"/></svg></button><button @click="togglePlay" class="play-btn" aria-label="Play/Pause"><svg v-if="!isPlaying" width="24" height="24" viewBox="0 0 24 24" fill="currentColor" style="margin-left: 2px;"><path d="M8 5v14l11-7z"/></svg><svg v-else width="24" height="24" viewBox="0 0 24 24" fill="currentColor" style="margin-left: 2px;"><path d="M6 19h4V5H6v14zm8-14v14h4V5h-4z"/></svg></button><button @click="nextTrack" class="icon-btn p-2" aria-label="Next" :disabled="!playerStore.canSkipNext"><svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor"><path d="M6 18l8.5-6L6 6v12zM16 6v12h2V6h-2z"/></svg></button><button @click="toggleRepeat" :class="['icon-btn p-2 text-xs', playerStore.repeatMode!=='off'?'text-green':'text-gray']" aria-label="Repeat" :aria-pressed="playerStore.repeatMode!=='off'"><svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="17 1 21 5 17 9"></polyline><path d="M3 11V9a4 4 0 0 1 4-4h14"></path><polyline points="7 23 3 19 7 15"></polyline><path d="M21 13v2a4 4 0 0 1-4 4H3"></path></svg><span v-if="playerStore.repeatMode==='track'" class="absolute -top-1 -right-1 text-[10px] text-green">1</span></button></div><div class="progress-bar flex items-center gap-2 w-full max-w-md"><span class="text-xs text-gray w-10 text-right">{{ currentTime }}</span><input type="range" class="slider flex-1 h-1" :value="progress" min="0" max="1" step="0.001" @input="handleProgressInput" @change="handleProgressChange" aria-label="Playback progress"><span class="text-xs text-gray w-10">{{ totalTime }}</span></div></div><div class="right-controls flex items-center gap-2 px-4 flex-1 justify-end"><div class="volume-control flex items-center gap-2" @mouseenter="volumeSliderVisible=true" @mouseleave="volumeSliderVisible=false"><button @click="toggleMute" class="icon-btn p-2" aria-label="Volume"><svg v-if="playerStore.volume===0" width="20" height="20" viewBox="0 0 24 24" fill="currentColor"><path d="M16.5 12c0-1.77-1.02-3.29-2.5-4.03v2.21l2.45 2.45c.03-.2.05-.41.05-.63zm2.5 0c0 .94-.2 1.82-.54 2.64l1.51 1.51C20.63 14.91 21 13.5 21 12c0-4.28-2.99-7.86-7-8.77v2.06c2.89.86 5 3.54 5 6.71zM4.27 3L3 4.27 7.73 9H3v6h4l5 5v-6.73l4.25 4.25c-.67.52-1.42.93-2.25 1.18v2.06c1.38-.31 2.63-.95 3.69-1.81L19.73 21 21 19.73l-9-9L4.27 3zM12 4L9.91 6.09 12 8.18V4z"/></svg><svg v-else-if="playerStore.volume<0.5" width="20" height="20" viewBox="0 0 24 24" fill="currentColor"><path d="M3 9v6h4l5 5V4L7 9H3zm13.5 3c0-1.77-1.02-3.29-2.5-4.03v8.05c1.48-.73 2.5-2.25 2.5-4.02z"/></svg><svg v-else width="20" height="20" viewBox="0 0 24 24" fill="currentColor"><path d="M3 9v6h4l5 5V4L7 9H3zm13.5 3c0-1.77-1.02-3.29-2.5-4.03v8.05c1.48-.73 2.5-2.25 2.5-4.02zM14 3.23v2.06c2.89.86 5 3.54 5 6.71s-2.11 5.85-5 6.71v2.06c4.01-.91 7-4.49 7-8.77s-2.99-7.86-7-8.77z"/></svg></button><div v-show="volumeSliderVisible" class="volume-slider absolute bottom-full left-1/2 -translate-x-1/2 mb-2 flex items-center gap-2 px-2 py-1 bg-dark rounded-lg border border-white/10 shadow-lg"><input type="range" class="slider w-32 h-1" :value="playerStore.volume" min="0" max="1" step="0.01" @input="handleVolumeInput" aria-label="Volume level"></div></div><button @click="toggleExpand" class="icon-btn p-2" aria-label="Expand player"><svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="23 4 23 10 17 10"></polyline><path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"></path></svg></button></div></div>
</template>

<style scoped>
.player-bar { height: var(--player-height); }
.track-image { width: 56px; height: 56px; }
.control-buttons button:disabled { opacity: 0.4; pointer-events: none; }
.volume-slider { animation: slideUp 0.15s ease; }
@keyframes slideUp { from { opacity: 0; transform: translateX(-50%) translateY(8px); } to { opacity: 1; transform: translateX(-50%) translateY(0); } }
.progress-bar input[type="range"] { height: 4px; }
.progress-bar input[type="range"]::-webkit-slider-thumb { width: 12px; height: 12px; transform: scale(0); }
.progress-bar:hover input[type="range"]::-webkit-slider-thumb { transform: scale(1); }
@media (max-width: 767px) { .track-details { display: none; } .control-buttons { gap: 2; } .right-controls { gap: 1; } }
</style>