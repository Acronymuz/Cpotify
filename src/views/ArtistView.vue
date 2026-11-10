<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { spotifyApi } from '@/services/spotifyApi'
import { getImageUrl, formatNumber, formatDuration, formatDate } from '@/utils/helpers'
import { Haptics, ImpactStyle } from '@capacitor/haptics'

const route = useRoute()
const router = useRouter()

const artist = ref<any>(null)
const topTracks = ref<any[]>([])
const albums = ref<any[]>([])
const isLoading = ref(false)
const activeTab = ref<'popular' | 'albums'>('popular')

const aid = route.params.id as string

async function load() {
  isLoading.value = true
  try {
    const [a, t, al] = await Promise.all([
      spotifyApi.getArtist(aid),
      spotifyApi.getArtistTopTracks(aid),
      spotifyApi.getArtistAlbums(aid, 20)
    ])
    artist.value = a
    topTracks.value = t.tracks
    albums.value = al.items
  } catch {
    console.error('Failed to load artist')
  } finally {
    isLoading.value = false
  }
}

function playTrack(t: any, i: number, ctx: any[]) {
  Haptics.impact({ style: ImpactStyle.Light })
}

function navAlbum(id: string) {
  router.push(`/album/${id}`)
}

onMounted(load)
</script>

<template>
  <div class="artist-view flex-1 overflow-y-auto pb-[calc(var(--player-height)+16px)]">
    <div v-if="isLoading" class="flex justify-center items-center h-[calc(100vh-var(--header-height))]">
      <div class="loading-spinner"></div>
    </div>

    <div v-else-if="artist" class="relative">
      <div class="relative h-64 sm:h-80 bg-gradient-to-b from-gray/50 to-black">
        <img
          v-if="artist.images?.length"
          :src="getImageUrl(artist.images, 640)"
          alt=""
          class="absolute inset-0 w-full h-full object-cover opacity-30"
        >
        <div class="absolute inset-0 bg-gradient-to-t from-black/80 via-black/20 to-transparent"></div>

        <div class="absolute bottom-0 left-0 right-0 p-6 pb-8">
          <div class="flex flex-col sm:flex-row items-start sm:items-center gap-4">
            <img
              v-if="artist.images?.length"
              :src="getImageUrl(artist.images, 300)"
              alt=""
              class="w-24 h-24 sm:w-32 sm:h-32 rounded-full shadow-xl object-cover"
            >
            <div v-else class="w-24 h-24 sm:w-32 sm:h-32 rounded-full bg-gray flex items-center justify-center shadow-xl">
              <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
                <circle cx="12" cy="12" r="3"></circle>
                <path d="M12 2v2M12 20v2M4.22 4.22l1.42 1.42M18.36 18.36l1.42 1.42M1 12h2M21 12h2M4.22 19.78l1.42-1.42M18.36 5.64l1.42-1.42"></path>
              </svg>
            </div>

            <div class="flex-1 min-w-0 text-center sm:text-left">
              <h1 class="text-3xl sm:text-4xl font-bold mb-2 truncate">{{ artist.name }}</h1>
              <p class="text-sm text-gray mb-4">
                {{ artist.followers?.total ? formatNumber(artist.followers.total) + ' followers' : 'Artist' }}
              </p>
              <div class="flex flex-wrap items-center justify-center sm:justify-start gap-3 text-sm text-gray">
                <span
                  v-for="g in artist.genres?.slice(0, 5) || []"
                  :key="g"
                  class="px-2 py-0.5 bg-white/10 rounded-full text-xs"
                >
                  {{ g }}
                </span>
              </div>
            </div>

            <div class="flex items-center gap-3 w-full sm:w-auto">
              <button class="play-btn" @click="topTracks.length && playTrack(topTracks[0], 0, topTracks)">
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

      <div class="px-4 pt-4 pb-[calc(var(--player-height)+16px)]">
        <div class="flex gap-2 overflow-x-auto pb-4 mb-4 -mx-4 px-4" role="tablist">
          <button
            @click="activeTab = 'popular'"
            :aria-selected="activeTab === 'popular'"
            role="tab"
            class="px-4 py-2 rounded-full text-sm font-medium whitespace-nowrap transition-colors"
            :class="activeTab === 'popular' ? 'bg-green text-black' : 'bg-gray text-gray hover:bg-gray/50'"
          >
            Popular
          </button>
          <button
            @click="activeTab = 'albums'"
            :aria-selected="activeTab === 'albums'"
            role="tab"
            class="px-4 py-2 rounded-full text-sm font-medium whitespace-nowrap transition-colors"
            :class="activeTab === 'albums' ? 'bg-green text-black' : 'bg-gray text-gray hover:bg-gray/50'"
          >
            Albums
          </button>
        </div>

        <div v-if="activeTab === 'popular'" class="space-y-2">
          <div
            v-for="(t, i) in topTracks"
            :key="t.id"
            @click="playTrack(t, i, topTracks)"
            class="row-item cursor-pointer group"
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
              <p class="text-xs text-gray truncate">{{ t.album?.name }}</p>
            </div>
            <div class="flex items-center gap-3 text-gray">
              <span class="text-sm">{{ formatDuration(t.duration_ms) }}</span>
              <span class="text-xs opacity-75">{{ formatNumber(t.popularity * 1000) }}</span>
              <button class="icon-btn p-1 text-xs opacity-0 group-hover:opacity-100 transition-opacity" @click.stop="playTrack(t, i, topTracks)">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor" style="margin-left: 2px;">
                  <path d="M8 5v14l11-7z"></path>
                </svg>
              </button>
            </div>
          </div>
        </div>

        <div v-if="activeTab === 'albums'" class="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-4">
          <div
            v-for="a in albums"
            :key="a.id"
            @click="navAlbum(a.id)"
            class="card cursor-pointer group"
          >
            <div class="relative aspect-square mb-3 overflow-hidden rounded">
              <img
                v-if="a.images?.length"
                :src="getImageUrl(a.images)"
                alt=""
                class="w-full h-full object-cover transition-transform duration-300 group-hover:scale-105"
              >
            </div>
            <p class="font-medium truncate">{{ a.name }}</p>
            <p class="text-xs text-gray truncate">{{ formatDate(a.release_date) }}</p>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>