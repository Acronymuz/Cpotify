// @ts-nocheck
<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { spotifyApi } from '@/services/spotifyApi'
import { getImageUrl, formatDuration, formatNumber } from '@/utils/helpers'
import { Haptics, ImpactStyle } from '@capacitor/haptics'

const route = useRoute()
const router = useRouter()

const playlist = ref<any>(null)
const tracks = ref<any[]>([])
const isLoading = ref(false)
const isOwner = ref(false)
const currentUserId = ref('')

const pid = route.params.id as string

async function load() {
  isLoading.value = true
  try {
    const [p, u] = await Promise.all([
      spotifyApi.getPlaylist(pid),
      spotifyApi.getCurrentUser()
    ])
    playlist.value = p
    currentUserId.value = u.id
    isOwner.value = p.owner?.id === u.id

    let all: any[] = []
    let off = 0
    const lim = 100
    let more = true
    while (more && all.length < 500) {
      const r = await spotifyApi.getPlaylistTracks(pid, lim, off)
      const ti = r.items.map(i => i.track).filter(Boolean)
      all.push(...ti)
      more = r.next !== null
      off += lim
    }
    tracks.value = all
  } catch {
    console.error('Failed to load playlist')
  } finally {
    isLoading.value = false
  }
}

function playTrack(t: any, i: number) {
  Haptics.impact({ style: ImpactStyle.Light })
}

function fmtFollowers(c: number) {
  if (c >= 1000000) return (c / 1000000).toFixed(1) + 'M followers'
  if (c >= 1000) return (c / 1000).toFixed(1) + 'K followers'
  return c + ' followers'
}

onMounted(load)
</script>

<template>
  <div class="playlist-view flex-1 overflow-y-auto pb-[calc(var(--player-height)+16px)]">
    <div v-if="isLoading" class="flex justify-center items-center h-[calc(100vh-var(--header-height))]">
      <div class="loading-spinner"></div>
    </div>

    <div v-else-if="playlist" class="relative">
      <div class="relative h-64 sm:h-80 bg-gradient-to-b from-gray/50 to-black">
        <img
          v-if="playlist.images?.length"
          :src="getImageUrl(playlist.images, 640)"
          alt=""
          class="absolute inset-0 w-full h-full object-cover opacity-30"
        >
        <div class="absolute inset-0 bg-gradient-to-t from-black/80 via-black/20 to-transparent"></div>

        <div class="absolute bottom-0 left-0 right-0 p-6 pb-8">
          <div class="flex flex-col sm:flex-row items-start sm:items-center gap-4">
            <img
              v-if="playlist.images?.length"
              :src="getImageUrl(playlist.images, 300)"
              alt=""
              class="w-24 h-24 sm:w-32 sm:h-32 rounded shadow-xl object-cover"
            >
            <div v-else class="w-24 h-24 sm:w-32 sm:h-32 rounded bg-gray flex items-center justify-center shadow-xl">
              <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
                <rect x="2" y="4" width="20" height="16" rx="2"></rect>
              </svg>
            </div>

            <div class="flex-1 min-w-0 text-center sm:text-left">
              <p v-if="playlist.owner?.display_name" class="text-sm text-gray mb-1">
                Playlist · {{ playlist.owner.display_name }}
              </p>
              <h1 class="text-3xl sm:text-4xl font-bold mb-2 truncate">{{ playlist.name }}</h1>
              <p v-if="playlist.description" class="text-sm text-gray mb-4 truncate">{{ playlist.description }}</p>
              <div class="flex flex-wrap items-center justify-center sm:justify-start gap-3 text-sm text-gray">
                <span>{{ tracks.length }} songs</span>
                <span>·</span>
                <span>{{ fmtFollowers(playlist.followers?.total || 0) }}</span>
                <span v-if="playlist.public" class="px-2 py-0.5 bg-white/10 rounded-full text-xs">Public</span>
              </div>
            </div>

            <div class="flex items-center gap-3 w-full sm:w-auto">
              <button class="play-btn" @click="tracks.length && playTrack(tracks[0], 0)">
                <svg width="24" height="24" viewBox="0 0 24 24" fill="currentColor" style="margin-left: 2px;">
                  <path d="M8 5v14l11-7z"></path>
                </svg>
              </button>
              <button class="icon-btn p-2" aria-label="More options">
                <svg width="24" height="24" viewBox="0 0 24 24" fill="currentColor">
                  <circle cx="12" cy="12" r="1.5"></circle>
                  <circle cx="19" cy="12" r="1.5"></circle>
                  <circle cx="5" cy="12" r="1.5"></circle>
                </svg>
              </button>
            </div>
          </div>
        </div>
      </div>

      <div class="px-4 pt-6 pb-[calc(var(--player-height)+16px)]">
        <div class="flex items-center justify-between mb-4 px-2">
          <h2 class="section-title">{{ tracks.length }} songs</h2>
          <div class="flex items-center gap-2">
            <button class="icon-btn p-2 text-xs" aria-label="Filter">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <polygon points="22 3 2 3 10 12.46 10 19 14 21 14 12.46 22 3"></polygon>
              </svg>
            </button>
            <button class="icon-btn p-2 text-xs" aria-label="Sort">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <line x1="3" y1="6" x2="21" y2="6"></line>
                <line x1="3" y1="12" x2="21" y2="12"></line>
                <line x1="3" y1="18" x2="21" y2="18"></line>
              </svg>
            </button>
          </div>
        </div>

        <div class="space-y-1">
          <div class="row-item px-2 text-xs font-semibold text-gray uppercase tracking-wider">
            <div class="flex items-center w-16">
              <span class="w-6">#</span>
              <span>TITLE</span>
            </div>
            <div class="flex-1 min-w-0">ARTIST</div>
            <div class="w-24 text-right">ALBUM</div>
            <div class="w-16 text-right">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <circle cx="12" cy="12" r="10"></circle>
                <polygon points="10 8 16 12 10 16"></polygon>
              </svg>
            </div>
          </div>

          <div
            v-for="(t, i) in tracks"
            :key="t.id"
            @click="playTrack(t, i)"
            class="row-item group cursor-pointer"
          >
            <div class="flex items-center gap-3 w-16 flex-shrink-0">
              <span class="text-gray text-sm font-medium w-6 text-right">{{ i + 1 }}</span>
              <img
                v-if="t.album?.images?.length"
                :src="getImageUrl(t.album.images, 64)"
                alt=""
                class="w-10 h-10 rounded object-cover"
              >
            </div>
            <div class="flex-1 min-w-0">
              <p class="font-medium truncate">{{ t.name }}</p>
              <p class="text-xs text-gray truncate">{{ t.artists?.map((a: any) => a.name).join(', ') }}</p>
            </div>
            <div class="flex-1 min-w-0 hidden md:block">
              <p class="text-xs text-gray truncate">{{ t.album?.name }}</p>
            </div>
            <div class="flex items-center gap-3 text-gray">
              <span class="text-sm">{{ formatDuration(t.duration_ms) }}</span>
              <button class="icon-btn p-1 text-xs opacity-0 group-hover:opacity-100 transition-opacity" @click.stop="playTrack(t, i)">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor" style="margin-left: 2px;">
                  <path d="M8 5v14l11-7z"></path>
                </svg>
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>