<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { usePlayerStore } from '@/stores/player'
import { usePlaybackSDK } from '@/composables/usePlaybackSDK'
import { Haptics, ImpactStyle } from '@capacitor/haptics'

const router = useRouter()
const authStore = useAuthStore()
const playerStore = usePlayerStore()
const playbackSDK = usePlaybackSDK()

const showProfileMenu = ref(false)
const showDeviceMenu = ref(false)
const devices = ref<Array<{ id: string; name: string; type: string; is_active: boolean }>>([])
const isLoadingDevices = ref(false)

async function loadDevices() { if (isLoadingDevices.value) return; isLoadingDevices.value = true; try { const { spotifyApi } = await import('@/services/spotifyApi'); const r = await spotifyApi.getAvailableDevices(); devices.value = r.devices.map(d => ({ id: d.id || '', name: d.name, type: d.type, is_active: d.is_active })); const a = r.devices.find(d => d.is_active); if (a) playerStore.activeDevice = a } catch {} finally { isLoadingDevices.value = false } }
async function selectDevice(id: string) { await Haptics.impact({ style: ImpactStyle.Light }); try { await playbackSDK.transferPlayback(); showDeviceMenu.value = false } catch {} }
function nav(p: string) { router.push(p); showProfileMenu.value = false }
function logout() { authStore.logout(); router.push('/login'); showProfileMenu.value = false }
const profileImg = computed(() => authStore.user?.images?.[0]?.url)
const userName = computed(() => authStore.user?.display_name || authStore.user?.id || 'User')
</script>

<template>
  <header class="header flex items-center justify-between px-4 h-[var(--header-height)] bg-black/80 backdrop-blur-sm border-b border-white/10 sticky top-0 z-50">
    <div class="flex items-center gap-3 flex-1 min-w-0">
      <button @click="nav('/home')" class="icon-btn p-2" aria-label="Home"><svg width="24" height="24" viewBox="0 0 24 24" fill="currentColor"><path d="M10 20v-6h4v6h5v-8h3L12 3 2 12h3v8z"/></svg></button>
      <router-link to="/home" class="flex items-center gap-2 text-white font-bold text-lg" aria-label="Spotify Mobile"><svg width="32" height="32" viewBox="0 0 24 24" fill="#1DB954"><path d="M12 0C5.374 0 0 5.373 0 12c0 6.627 5.373 12 12 12s12-5.373 12-12C24 5.373 18.627 0 12 0zm5.521 17.34c-.13.381-.394.688-.77.87-1.834.877-3.996 1.48-6.5.647-.39-.128-.75-.365-.94-.746-.12-.25-.09-.554.05-.77 2.215-1.923 4.685-3.76 6.506-4.786.502-.28 1.1.008 1.158.655.02.213-.07.423-.24.56zm-1.612-3.67c.27-.59.33-1.295.17-1.91-3.075-5.832-7.135-10.452-11.79-13.55-.17-.11-.38-.13-.55.02-.39.36-.6.85-.6 1.35 0 4.867 3.99 8.765 9.07 10.09.27.07.55-.05.73-.31zm-12.555-2.197c-.51.72-.75 1.56-.7 2.36 4.8 2.63 10.16 2.5 13.49-.3.33-1.3-.33-2.62-.93-3.1-1.38-1.1-3.06-1.13-4.52-.07-1.63.6-3.1 1.53-4.28 2.75-.49.51-.9 1.11-1.06 1.78z"/></svg><span class="hidden sm:inline">Spotify Mobile</span></router-link>
    </div>
    <div class="flex items-center gap-2">
      <button @click="loadDevices(); showDeviceMenu=!showDeviceMenu" class="icon-btn" aria-label="Devices"><svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="2" y="3" width="20" height="14" rx="2" ry="2"></rect><line x1="8" y1="21" x2="16" y2="21"></line><line x1="12" y1="17" x2="12" y2="21"></line></svg></button>
      <div v-if="showDeviceMenu" class="device-menu absolute right-4 top-full mt-2 w-64 bg-dark rounded-lg shadow-lg border border-white/10 overflow-hidden z-50"><div class="p-3 border-b border-white/10 flex items-center justify-between"><span class="font-semibold">Available Devices</span><button @click="showDeviceMenu=false" class="icon-btn text-xs" aria-label="Close"><svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="18" y1="6" x2="6" y2="18"></line><line x1="6" y1="6" x2="18" y2="18"></line></svg></button></div><div v-if="isLoadingDevices" class="p-4 flex justify-center"><div class="loading-spinner"></div></div><div v-else class="max-h-64 overflow-y-auto"><div v-for="d in devices" :key="d.id" @click="selectDevice(d.id)" class="flex items-center gap-3 px-3 py-2 hover:bg-gray cursor-pointer"><div class="w-8 h-8 rounded-full bg-gray flex items-center justify-center"><svg v-if="d.type==='smartphone'" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="5" y="2" width="14" height="20" rx="2" ry="2"></rect><line x1="12" y1="18" x2="12.01" y2="18"></line></svg><svg v-else-if="d.type==='computer'" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="2" y="3" width="20" height="14" rx="2" ry="2"></rect><line x1="8" y1="21" x2="16" y2="21"></line><line x1="12" y1="17" x2="12" y2="21"></line></svg><svg v-else width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M5 6H3a2 2 0 0 0-2 2v8a2 2 0 0 0 2 2h2"></path><path d="M19 6h2a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2h-2"></path><path d="M9 4v16"></path><path d="M15 4v16"></path></svg></div><div class="flex-1 min-w-0"><p class="font-medium truncate">{{ d.name }}</p><p class="text-xs text-gray capitalize">{{ d.type }}</p></div><div v-if="d.is_active" class="flex items-center gap-1 text-green"><svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor"><path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-2 15l-5-5 1.41-1.41L10 14.17l7.59-7.59L19 8l-9 9z"/></svg><span class="text-xs">Active</span></div></div></div></div>
      <button @click="showProfileMenu=!showProfileMenu" class="icon-btn relative" aria-label="Profile"><div v-if="profileImg" class="w-8 h-8 rounded-full overflow-hidden bg-gray"><img :src="profileImg" alt="" class="w-full h-full object-cover"></div><div v-else class="w-8 h-8 rounded-full bg-gray flex items-center justify-center"><svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"></path><circle cx="12" cy="7" r="4"></circle></svg></div></button>
      <div v-if="showProfileMenu" class="profile-menu absolute right-4 top-full mt-2 w-48 bg-dark rounded-lg shadow-lg border border-white/10 overflow-hidden z-50"><div class="p-3 border-b border-white/10"><p class="font-medium truncate">{{ userName }}</p><p class="text-xs text-gray">{{ authStore.user?.email }}</p><p class="text-xs text-green mt-1">{{ authStore.isPremium ? 'Premium' : 'Free' }}</p></div><button @click="nav('/settings')" class="w-full flex items-center gap-3 px-3 py-2 hover:bg-gray"><svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="3"></circle><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1 0 2.83 2 2 0 0 1-2.83 0l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-2 2 2 2 0 0 1-2-2v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83 0 2 2 0 0 1 0-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1-2-2 2 2 0 0 1 2-2h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 0-2.83 2 2 0 0 1 2.83 0l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 2-2 2 2 0 0 1 2 2v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 0 2 2 0 0 1 0 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 2 2 2 2 0 0 1-2 2h-.09a1.65 1.65 0 0 0-1.51 1z"></path></svg><span>Settings</span></button><button @click="logout" class="w-full flex items-center gap-3 px-3 py-2 hover:bg-gray text-error"><svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"></path><polyline points="16 17 21 12 16 7"></polyline><line x1="21" y1="12" x2="9" y2="12"></line></svg><span>Log Out</span></button></div>
    </div>
  </header>
</template>

<style scoped>
.header { position: fixed; top: 0; left: 0; right: 0; z-index: 100; }
.device-menu, .profile-menu { animation: slideDown 0.15s ease; }
@keyframes slideDown { from { opacity: 0; transform: translateY(-8px); } to { opacity: 1; transform: translateY(0); } }
@media (max-width: 767px) { .device-menu, .profile-menu { right: 0; left: auto; width: calc(100vw - 32px); max-width: 320px; } }
</style>