// @ts-nocheck
<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { spotifyApi } from '@/services/spotifyApi'
import { getImageUrl, formatDuration, debounce } from '@/utils/helpers'
import { Haptics, ImpactStyle } from '@capacitor/haptics'

const router = useRouter()

const query = ref('')
const debouncedQuery = ref('')
const searchResults = ref({
  tracks: [],
  artists: [],
  albums: [],
  playlists: []
} as any)
const isSearching = ref(false)
const searchError = ref<string | null>(null)
const activeTab = ref<'tracks' | 'artists' | 'albums' | 'playlists'>('tracks')
const recentSearches = ref<string[]>([])

const tabs = [
  { id: 'tracks', label: 'Songs', count: computed(() => searchResults.value.tracks.length) },
  { id: 'artists', label: 'Artists', count: computed(() => searchResults.value.artists.length) },
  { id: 'albums', label: 'Albums', count: computed(() => searchResults.value.albums.length) },
  { id: 'playlists', label: 'Playlists', count: computed(() => searchResults.value.playlists.length) }
] as const

function loadRecent() {
  try {
    const s = localStorage.getItem('recent_searches')
    if (s) recentSearches.value = JSON.parse(s)
  } catch {}
}

function saveRecent() {
  try {
    localStorage.setItem('recent_searches', JSON.stringify(recentSearches.value.slice(0, 10)))
  } catch {}
}

function addRecent(s: string) {
  const t = s.trim()
  if (!t) return
  recentSearches.value = [t, ...recentSearches.value.filter(x => x !== t)].slice(0, 10)
  saveRecent()
}

const doSearch = debounce(async (q: string) => {
  if (!q.trim()) {
    searchResults.value = { tracks: [], artists: [], albums: [], playlists: [] }
    return
  }
  isSearching.value = true
  searchError.value = null
  debouncedQuery.value = q
  try {
    const r = await spotifyApi.search(q, ['track', 'artist', 'album', 'playlist'], 20)
    searchResults.value = {
      tracks: r.tracks?.items || [],
      artists: r.artists?.items || [],
      albums: r.albums?.items || [],
      playlists: r.playlists?.items || []
    }
  } catch {
    searchError.value = 'Search failed'
    console.error
  } finally {
    isSearching.value = false
  }
}, 300)

watch(query, n => doSearch(n))

function handleSubmit() {
  if (query.value.trim()) {
    addRecent(query.value)
    doSearch(query.value)
  }
}

function clear() {
  query.value = ''
  debouncedQuery.value = ''
  searchResults.value = { tracks: [], artists: [], albums: [], playlists: [] }
}

function playTrack(tr: any, ctx: any[]) {
  Haptics.impact({ style: ImpactStyle.Light })
  const u = ctx.map(x => 'spotify:track:' + x.id)
  const i = ctx.findIndex(x => x.id === tr.id)
}

function navArtist(id: string) {
  router.push('/artist/' + id)
}

function navAlbum(id: string) {
  router.push('/album/' + id)
}

function navPlaylist(id: string) {
  router.push('/playlist/' + id)
}

function pickRecent(s: string) {
  query.value = s
  handleSubmit()
}

function removeRecent(s: string, e: Event) {
  e.stopPropagation()
  recentSearches.value = recentSearches.value.filter(x => x !== s)
  saveRecent()
}

function removeRecentByString(s: string) {
  recentSearches.value = recentSearches.value.filter(x => x !== s)
  saveRecent()
}

const showRecent = computed(() => !query.value.trim() && recentSearches.value.length > 0)

onMounted(loadRecent)
</script>

<template>
  <div class="search-view flex-1 overflow-y-auto pb-[calc(var(--player-height)+16px)]">
    <div class="px-4 pt-[calc(var(--header-height)+16px)] pb-4">
      <div class="relative mb-6">
        <div class="relative">
          <svg class="absolute left-3 top-1/2 -translate-y-1/2 text-gray" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="11" cy="11" r="8"></circle>
            <line x1="21" y1="21" x2="16.65" y2="16.65"></line>
          </svg>
          <input
            v-model="query"
            @keyup.enter="handleSubmit"
            @blur="handleSubmit"
            type="text"
            placeholder="What do you want to listen to?"
            class="w-full pl-10 pr-10 py-3 bg-gray rounded-full text-white placeholder-gray text-base focus:outline-none focus:ring-2 focus:ring-green"
            autocorrect="off"
            autocapitalize="off"
            spellcheck="false"
          >
          <button v-if="query" @click="clear" class="absolute right-3 top-1/2 -translate-y-1/2 text-gray hover:text-white">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="18" y1="6" x2="6" y2="18"></line>
              <line x1="6" y1="6" x2="18" y2="18"></line>
            </svg>
          </button>
        </div>
      </div>

      <div v-if="showRecent" class="space-y-4">
        <div class="flex items-center justify-between">
          <h3 class="section-title">Recent Searches</h3>
          <button @click="recentSearches = []; saveRecent()" class="text-sm text-gray">Clear All</button>
        </div>
        <div class="flex flex-wrap gap-2">
          <div v-for="s in recentSearches" :key="s" class="flex items-center gap-2 px-3 py-2 bg-gray rounded-full text-sm">
            <button @click="pickRecent(s)" class="flex items-center gap-2 w-full text-left">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="text-gray">
                <circle cx="11" cy="11" r="8"></circle>
                <line x1="21" y1="21" x2="16.65" y2="16.65"></line>
              </svg>
              <span class="truncate max-w-[200px]">{{ s }}</span>
            </button>
            <span @click="removeRecent(s, $event)" class="ml-1 text-gray hover:text-white cursor-pointer">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <line x1="18" y1="6" x2="6" y2="18"></line>
                <line x1="6" y1="6" x2="18" y2="18"></line>
              </svg>
            </span>
          </div>
        </div>
      </div>

      <div v-else-if="debouncedQuery" class="space-y-6">
        <div v-if="isSearching" class="flex justify-center py-12">
          <div class="loading-spinner"></div>
        </div>
        <div v-else-if="searchError" class="empty-state">
          <svg class="empty-state-icon" viewBox="0 0 24 24" fill="none" stroke="#e22134" stroke-width="2">
            <circle cx="12" cy="12" r="10"></circle>
            <line x1="15" y1="9" x2="9" y2="15"></line>
            <line x1="9" y1="9" x2="15" y2="15"></line>
          </svg>
          <h3 class="text-lg font-medium mb-2">Search Failed</h3>
          <p class="text-gray text-sm">{{ searchError }}</p>
          <button @click="handleSubmit" class="mt-4 px-6 py-2 bg-green text-black rounded-full text-sm font-medium">Retry</button>
        </div>

        <div v-else>
          <div class="flex gap-2 overflow-x-auto pb-2 mb-4 -mx-4 px-4" role="tablist">
            <button
              v-for="tb in tabs"
              :key="tb.id"
              @click="activeTab = tb.id"
              :aria-selected="activeTab === tb.id"
              role="tab"
              class="flex items-center gap-2 px-4 py-2 rounded-full text-sm font-medium whitespace-nowrap transition-colors"
              :class="activeTab === tb.id ? 'bg-green text-black' : 'bg-gray text-gray hover:bg-gray/50'"
            >
              {{ tb.label }}
              <span class="text-xs opacity-75">{{ tb.count }}</span>
            </button>
          </div>

          <div v-if="activeTab === 'tracks'" class="space-y-2">
            <div
              v-for="t in searchResults.tracks"
              :key="t.id"
              @click="playTrack(t, searchResults.tracks)"
              class="row-item cursor-pointer group"
            >
              <img
                v-if="t.album?.images?.length"
                :src="getImageUrl(t.album.images, 64)"
                alt=""
                class="w-12 h-12 rounded object-cover"
              >
              <div v-else class="w-12 h-12 rounded bg-gray flex items-center justify-center">
                <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
                  <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-2 15l-5-5 1.41-1.41L10 14.17l7.59-7.59L19 8l-9 9z"></path>
                </svg>
              </div>
              <div class="flex-1 min-w-0">
                <p class="font-medium truncate">{{ t.name }}</p>
                <p class="text-xs text-gray truncate">{{ t.artists.map((a: any) => a.name).join(', ') }} · {{ t.album?.name }}</p>
              </div>
              <div class="flex items-center gap-3 text-gray">
                <span class="text-sm">{{ formatDuration(t.duration_ms) }}</span>
                <span v-if="t.explicit" class="text-xs font-medium px-1.5 py-0.5 bg-white/10 rounded">E</span>
              </div>
            </div>
          </div>

          <div v-if="activeTab === 'artists'" class="space-y-4">
            <div
              v-for="a in searchResults.artists"
              :key="a.id"
              @click="navArtist(a.id)"
              class="card cursor-pointer group"
            >
              <div class="flex items-center gap-4">
                <img
                  v-if="a.images?.length"
                  :src="getImageUrl(a.images, 80)"
                  alt=""
                  class="w-16 h-16 rounded-full object-cover"
                >
                <div v-else class="w-16 h-16 rounded-full bg-gray flex items-center justify-center">
                  <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
                    <circle cx="12" cy="12" r="3"></circle>
                    <path d="M12 2v2M12 20v2M4.22 4.22l1.42 1.42M18.36 18.36l1.42 1.42M1 12h2M21 12h2M4.22 19.78l1.42-1.42M18.36 5.64l1.42-1.42"></path>
                  </svg>
                </div>
                <div class="flex-1 min-w-0">
                  <p class="font-medium truncate">{{ a.name }}</p>
                  <p class="text-xs text-gray">
                    {{ a.followers?.total ? (a.followers.total / 1000000).toFixed(1) + 'M followers' : 'Artist' }}
                  </p>
                  <div v-if="a.genres?.length" class="flex gap-1 mt-1 flex-wrap">
                    <span
                      v-for="g in a.genres.slice(0, 3)"
                      :key="g"
                      class="text-xs px-2 py-0.5 bg-white/10 rounded-full"
                    >
                      {{ g }}
                    </span>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <div v-if="activeTab === 'albums'" class="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-4">
            <div
              v-for="a in searchResults.albums"
              :key="a.id"
              @click="navAlbum(a.id)"
              class="card cursor-pointer group"
            >
              <div class="aspect-square mb-3 overflow-hidden rounded">
                <img
                  v-if="a.images?.length"
                  :src="getImageUrl(a.images)"
                  alt=""
                  class="w-full h-full object-cover transition-transform duration-300 group-hover:scale-105"
                >
              </div>
              <p class="font-medium truncate">{{ a.name }}</p>
              <p class="text-xs text-gray truncate">{{ a.artists.map((x: any) => x.name).join(', ') }}</p>
            </div>
          </div>

          <div v-if="activeTab === 'playlists'" class="space-y-4">
            <div
              v-for="p in searchResults.playlists"
              :key="p.id"
              @click="navPlaylist(p.id)"
              class="card cursor-pointer group"
            >
              <div class="flex items-center gap-4">
                <div class="relative w-16 h-16 flex-shrink-0">
                  <img
                    v-if="p.images?.length"
                    :src="getImageUrl(p.images, 80)"
                    alt=""
                    class="w-full h-full rounded object-cover"
                  >
                  <div v-else class="w-full h-full rounded bg-gray flex items-center justify-center">
                    <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
                      <rect x="2" y="4" width="20" height="16" rx="2"></rect>
                    </svg>
                  </div>
                </div>
                <div class="flex-1 min-w-0">
                  <p class="font-medium truncate">{{ p.name }}</p>
                  <p class="text-xs text-gray truncate">{{ p.owner?.display_name || 'Unknown' }} · {{ p.tracks?.total }} songs</p>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>