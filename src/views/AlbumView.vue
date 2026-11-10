<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { spotifyApi } from '@/services/spotifyApi'
import { getImageUrl, formatDuration, formatNumber, formatDate } from '@/utils/helpers'
import { Haptics, ImpactStyle } from '@capacitor/haptics'

const route = useRoute()
const router = useRouter()

const album = ref<any>(null)
const tracks = ref<any[]>([])
const isLoading = ref(false)
const isSaved = ref(false)

const aid = route.params.id as string

async function load() {
  isLoading.value = true
  try {
    const a = await spotifyApi.getAlbum(aid)
    album.value = a
    let all: any[] = []
    let off = 0
    const lim = 50
    let more = true
    while (more) {
      const r = await spotifyApi.getAlbumTracks(aid, lim, off)
      all.push(...r.items)
      more = r.next !== null
      off += lim
    }
    tracks.value = all
  } catch {
    console.error('Failed to load album')
  } finally {
    isLoading.value = false
  }
}

function playTrack(t: any, i: number) {
  Haptics.impact({ style: ImpactStyle.Light })
}

function totalDur() {
  return tracks.value.reduce((s, t) => s + (t.duration_ms || 0), 0)
}

onMounted(load)
</script>

<template>
  <div class="album-view flex-1 overflow-y-auto pb-[calc(var(--player-height)+16px)]">
    <div v-if="isLoading" class="flex justify-center items-center h-[calc(100vh-var(--header-height))]">
      <div class="loading-spinner"></div>
    </div>

    <div v-else-if="album" class="relative">
      <div class="relative h-64 sm:h-80 bg-gradient-to-b from-gray/50 to-black">
        <img
          v-if="album.images?.length"
          :src="getImageUrl(album.images, 640)"
          alt=""
          class="absolute inset-0 w-full h-full object-cover opacity-30"
        >
        <div class="absolute inset-0 bg-gradient-to-t from-black/80 via-black/20 to-transparent"></div>

        <div class="absolute bottom-0 left-0 right-0 p-6 pb-8">
          <div class="flex flex-col sm:flex-row items-start sm:items-center gap-4">
            <img
              v-if="album.images?.length"
              :src="getImageUrl(album.images, 300)"
              alt=""
              class="w-24 h-24 sm:w-32 sm:h-32 rounded shadow-xl object-cover"
            >

            <div class="flex-1 min-w-0 text-center sm:text-left">
              <p class="text-sm text-green font-medium mb-1">
                {{ album.album_type === 'single' ? 'SINGLE' : album.album_type === 'compilation' ? 'COMPILATION' : 'ALBUM' }}
              </p>
              <h1 class="text-3xl sm:text-4xl font-bold mb-2 truncate">{{ album.name }}</h1>
              <p class="text-sm text-gray mb-4">
                {{ album.artists?.map((a: any) => a.name).join(', ') }} ·
                {{ formatDate(album.release_date) }} ·
                {{ tracks.length }} songs ·
                {{ formatDuration(totalDur()) }}
              </p>
              <div class="flex flex-wrap items-center justify-center sm:justify-start gap-3 text-sm text-gray">
                <span v-if="album.label">{{ album.label }} · </span>
                <span>{{ album.copyrights?.[0]?.text || '' }}</span>
              </div>
            </div>

            <div class="flex items-center gap-3 w-full sm:w-auto">
              <button class="play-btn" @click="tracks.length && playTrack(tracks[0], 0)">
                <svg width="24" height="24" viewBox="0 0 24 24" fill="currentColor" style="margin-left: 2px;">
                  <path d="M8 5v14l11-7z"></path>
                </svg>
              </button>
              <button class="icon-btn p-2" aria-label="Save album" :class="isSaved ? 'text-green' : ''">
                <svg width="24" height="24" viewBox="0 0 24 24" fill="currentColor">
                  <path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"></path>
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
        <div class="space-y-1">
          <div class="row-item px-2 text-xs font-semibold text-gray uppercase tracking-wider">
            <div class="flex items-center w-16">
              <span class="w-6">#</span>
              <span>TITLE</span>
            </div>
            <div class="flex-1 min-w-0">ARTIST</div>
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
                v-if="album.images?.length"
                :src="getImageUrl(album.images, 64)"
                alt=""
                class="w-10 h-10 rounded object-cover"
              >
            </div>
            <div class="flex-1 min-w-0">
              <p class="font-medium truncate">{{ t.name }}</p>
              <p class="text-xs text-gray truncate">{{ t.artists?.map((a: any) => a.name).join(', ') }}</p>
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