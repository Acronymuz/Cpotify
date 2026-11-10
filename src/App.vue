<script setup lang="ts">
// @ts-nocheck
import { onMounted, onUnmounted, ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { usePlayerStore } from '@/stores/player'
import { usePlaybackSDK } from '@/composables/usePlaybackSDK'
import PlayerBar from '@/components/PlayerBar.vue'
import Header from '@/components/Header.vue'
import Sidebar from '@/components/Sidebar.vue'
import { Capacitor } from '@capacitor/core'
import { App } from '@capacitor/app'
import { Network } from '@capacitor/network'
import { StatusBar } from '@capacitor/status-bar'
import { SplashScreen } from '@capacitor/splash-screen'

const router = useRouter()
const authStore = useAuthStore()
const playerStore = usePlayerStore()
const playbackSDK = usePlaybackSDK()
const isOnline = ref(true)

const isAuthRoute = computed(() => 
  router.currentRoute.value.path === '/login' || router.currentRoute.value.path === '/callback'
)

const showSidebar = computed(() => 
  window.innerWidth >= 768 && !isAuthRoute.value
)

async function initApp() {
  if (Capacitor.isNativePlatform()) {
    try {
      await StatusBar.setOverlaysWebContent({ overlay: true })
      await StatusBar.setStyle({ style: 'DARK' })
      await StatusBar.setBackgroundColor({ color: '#121212' })
      await SplashScreen.hide()
    } catch {}
  }

  const networkStatus = await Network.getStatus()
  isOnline.value = networkStatus.connected

  Network.addListener('networkStatusChange', (status) => {
    isOnline.value = status.connected
    if (status.connected && authStore.isAuthenticated) {
      playbackSDK.initialize()
    }
  })

  App.addListener('appStateChange', ({ isActive }) => {
    if (isActive && authStore.isAuthenticated) {
      playbackSDK.syncState()
    }
  })

  App.addListener('backButton', ({ canGoBack }) => {
    if (canGoBack) {
      router.back()
    } else {
      App.exitApp()
    }
  })

  if (authStore.isAuthenticated) {
    await playbackSDK.initialize()
  }
}

onMounted(() => {
  initApp()
})

onUnmounted(() => {
  Network.removeAllListeners()
  App.removeAllListeners()
})
</script>

<template>
  <div class="app flex flex-col h-full">
    <Header v-if="!isAuthRoute" />
    
    <div class="main-content flex flex-1 overflow-hidden" :class="{ 'with-sidebar': showSidebar }">
      <Sidebar v-if="showSidebar" :modelValue="showSidebar" @close="showSidebar = false" />
      
      <main class="flex-1 overflow-y-auto" :class="{ 'sidebar-open': showSidebar }">
        <router-view v-slot="{ Component }">
          <transition name="fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </main>
    </div>

    <PlayerBar v-if="playerStore.hasActivePlayback" />
    
    <div v-if="!isOnline" class="offline-banner flex items-center justify-center gap-2 px-4 py-2 bg-gray text-xs">
      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <path d="M1 1l22 22"></path>
        <path d="M16.72 11.06A10.94 10.94 0 0 1 19 12.55"></path>
        <path d="M5 12.55a10.94 10.94 0 0 1 5.17-2.39"></path>
        <path d="M10.71 5.05A16 16 0 0 1 22.58 9"></path>
        <path d="M1.42 1.42l4.59 4.59"></path>
      </svg>
      <span>Offline - Reconnecting...</span>
    </div>
  </div>
</template>

<style scoped>
.app { height: 100vh; height: 100dvh; }
.main-content { min-height: 0; }
@media (max-width: 767px) { .sidebar-open { margin-left: var(--sidebar-width); } }
.offline-banner { position: fixed; bottom: var(--player-height); left: 0; right: 0; z-index: 1000; animation: slideUp 0.3s ease; }
@keyframes slideUp { from { transform: translateY(100%); opacity: 0; } to { transform: translateY(0); opacity: 1; } }
.fade-enter-active, .fade-leave-active { transition: opacity 0.2s ease; }
.fade-enter-from, .fade-leave-to { opacity: 0; }
</style>