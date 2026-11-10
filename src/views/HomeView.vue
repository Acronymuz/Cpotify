<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { usePlayerStore } from '@/stores/player'
import { spotifyApi } from '@/services/spotifyApi'
import { formatDuration, getImageUrl } from '@/utils/helpers'
import { useRouter } from 'vue-router'
import { Haptics, ImpactStyle } from '@capacitor/haptics'

const authStore = useAuthStore()
const playerStore = usePlayerStore()
const router = useRouter()
const recentlyPlayed = ref<Array<{ track: any; played_at: string }>>([])
const savedTracks = ref<any[]>([])
const topTracks = ref<any[]>([])
const isLoading = ref(false)

async function loadHomeData() { isLoading.value = true; try { const [r, s, t] = await Promise.allSettled([spotifyApi.getRecentlyPlayed(20), spotifyApi.getSavedTracks(20), spotifyApi.getTopTracks(10, 'short_term')]); if (r.status === 'fulfilled') recentlyPlayed.value = r.value.items; if (s.status === 'fulfilled') savedTracks.value = s.value.items.map(i => i.track); if (t.status === 'fulfilled') topTracks.value = t.value.items } catch { console.error('Failed to load home data') } finally { isLoading.value = false } }
function playTrack(tr: any, ctx?: any[], ctxUri?: string) { Haptics.impact({ style: ImpactStyle.Light }); if (ctx && ctxUri) { const u = ctx.map(x => x.uri || `spotify:track:${x.id}`); const i = ctx.findIndex(x => x.id === tr.id); spotifyApi.startPlayback(ctxUri, undefined, { position: i >= 0 ? i : 0 }) } else spotifyApi.startPlayback(undefined, [`spotify:track:${tr.id}`]) }
function playContext(uri: string) { Haptics.impact({ style: ImpactStyle.Light }); spotifyApi.startPlayback(uri) }
function navigate(p: string) { router.push(p) }
function formatPlayedAt(d: string) { const dt = new Date(d); const now = new Date(); const diff = now.getTime() - dt.getTime(); const m = Math.floor(diff/60000), h = Math.floor(diff/3600000), dy = Math.floor(diff/86400000); if (m < 1) return 'Just now'; if (m < 60) return `${m}m ago`; if (h < 24) return `${h}h ago`; return `${dy}d ago` }
onMounted(loadHomeData)
</script>

<template>
  <div class="home-view flex-1 overflow-y-auto pb-[calc(var(--player-height)+16px)]">
    <div class="px-4 pt-[calc(var(--header-height)+16px)] pb-8 space-y-8">
      <div><h1 class="text-2xl font-bold mb-1">Good {{ new Date().getHours() < 12 ? 'morning' : new Date().getHours() < 18 ? 'afternoon' : 'evening' }}, {{ authStore.user?.display_name?.split(' ')[0] || 'there' }}</h1><p class="text-gray text-sm">What do you want to listen to?</p></div>
      <div v-if="recentlyPlayed.length > 0" class="space-y-4"><div class="flex items-center justify-between"><h2 class="section-title">Recently Played</h2><button @click="navigate('/library')" class="text-sm text-green font-medium">See All</button></div><div class="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5 gap-4"><div v-for="it in recentlyPlayed.slice(0,10)" :key="it.track.id" class="card group"><div class="relative aspect-square mb-3 overflow-hidden rounded"><img v-if="it.track.album?.images?.length" :src="getImageUrl(it.track.album.images)" alt="" class="w-full h-full object-cover transition-transform duration-300 group-hover:scale-105"><div v-else class="w-full h-full bg-gray flex items-center justify-center"><svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><rect x="2" y="4" width="20" height="16" rx="2"></rect></svg></div><div class="absolute inset-0 bg-black/50 opacity-0 group-hover:opacity-100 transition-opacity flex items-center justify-center"><button @click="playTrack(it.track)" class="play-btn opacity-0 group-hover:opacity-100 transform group-hover:scale-100 scale-90 transition-all"><svg width="24" height="24" viewBox="0 0 24 24" fill="currentColor" style="margin-left: 2px;"><path d="M8 5v14l11-7z"/></svg></button></div></div><p class="font-medium truncate">{{ it.track.name }}</p><p class="text-xs text-gray truncate">{{ it.track.artists?.map((a:any)=>a.name).join(', ') }}</p><p class="text-xs text-gray mt-1">{{ formatPlayedAt(it.played_at) }}</p></div></div></div>
      <div v-if="savedTracks.length > 0" class="space-y-4"><div class="flex items-center justify-between"><h2 class="section-title">Liked Songs</h2><button @click="navigate('/library')" class="text-sm text-green font-medium">See All</button></div><div class="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5 gap-4"><div v-for="t in savedTracks.slice(0,10)" :key="t.id" class="card group"><div class="relative aspect-square mb-3 overflow-hidden rounded"><img v-if="t.album?.images?.length" :src="getImageUrl(t.album.images)" alt="" class="w-full h-full object-cover transition-transform duration-300 group-hover:scale-105"><div class="absolute inset-0 bg-black/50 opacity-0 group-hover:opacity-100 transition-opacity flex items-center justify-center"><button @click="playTrack(t, savedTracks)" class="play-btn opacity-0 group-hover:opacity-100 transform group-hover:scale-100 scale-90 transition-all"><svg width="24" height="24" viewBox="0 0 24 24" fill="currentColor" style="margin-left: 2px;"><path d="M8 5v14l11-7z"/></svg></button></div></div><p class="font-medium truncate">{{ t.name }}</p><p class="text-xs text-gray truncate">{{ t.artists?.map((a:any)=>a.name).join(', ') }}</p></div></div></div>
      <div v-if="topTracks.length > 0" class="space-y-4"><h2 class="section-title">Your Top Tracks</h2><div class="space-y-2"><div v-for="(t,i) in topTracks.slice(0,10)" :key="t.id" @click="playTrack(t, topTracks)" class="row-item cursor-pointer group"><div class="flex items-center gap-3 w-16 flex-shrink-0"><span class="text-gray text-sm font-medium w-6 text-right">{{ i+1 }}</span><img v-if="t.album?.images?.length" :src="getImageUrl(t.album.images, 64)" alt="" class="w-10 h-10 rounded object-cover"></div><div class="flex-1 min-w-0"><p class="font-medium truncate">{{ t.name }}</p><p class="text-xs text-gray truncate">{{ t.artists?.map((a:any)=>a.name).join(', ') }}</p></div><div class="flex items-center gap-3 text-gray opacity-0 group-hover:opacity-100 transition-opacity"><span class="text-sm">{{ formatDuration(t.duration_ms) }}</span><button class="icon-btn p-1 text-xs" @click.stop="playTrack(t, topTracks)"><svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor" style="margin-left: 2px;"><path d="M8 5v14l11-7z"/></svg></button></div></div></div></div>
      <div v-if="isLoading && recentlyPlayed.length === 0" class="flex justify-center py-12"><div class="loading-spinner"></div></div>
      <div v-if="!isLoading && recentlyPlayed.length === 0 && savedTracks.length === 0" class="empty-state"><svg class="empty-state-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-2 15l-5-5 1.41-1.41L10 14.17l7.59-7.59L19 8l-9 9z"/></svg><h3 class="text-lg font-medium mb-2">Start listening</h3><p class="text-gray text-sm">Your recently played and liked songs will appear here</p></div>
    </div>
  </div>
</template>