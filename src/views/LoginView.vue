<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { useRouter } from 'vue-router'
import { Capacitor } from '@capacitor/core'
import { Haptics, ImpactStyle } from '@capacitor/haptics'

const authStore = useAuthStore()
const router = useRouter()
const isLoading = ref(false)

async function handleLogin() { isLoading.value = true; try { await Haptics.impact({ style: ImpactStyle.Medium }); await authStore.initiateLogin() } catch (e) { console.error(e) } finally { isLoading.value = false } }
onMounted(() => { if (authStore.isAuthenticated) router.push('/home') })
</script>

<template>
  <div class="login-view flex flex-col items-center justify-center h-full px-6 text-center">
    <div class="max-w-md w-full">
      <div class="mb-12"><svg width="120" height="120" viewBox="0 0 24 24" fill="#1DB954" class="mx-auto"><path d="M12 0C5.374 0 0 5.373 0 12c0 6.627 5.373 12 12 12s12-5.373 12-12C24 5.373 18.627 0 12 0zm5.521 17.34c-.13.381-.394.688-.77.87-1.834.877-3.996 1.48-6.5.647-.39-.128-.75-.365-.94-.746-.12-.25-.09-.554.05-.77 2.215-1.923 4.685-3.76 6.506-4.786.502-.28 1.1.008 1.158.655.02.213-.07.423-.24.56zm-1.612-3.67c.27-.59.33-1.295.17-1.91-3.075-5.832-7.135-10.452-11.79-13.55-.17-.11-.38-.13-.55.02-.39.36-.6.85-.6 1.35 0 4.867 3.99 8.765 9.07 10.09.27.07.55-.05.73-.31zm-12.555-2.197c-.51.72-.75 1.56-.7 2.36 4.8 2.63 10.16 2.5 13.49-.3.33-1.3-.33-2.62-.93-3.1-1.38-1.1-3.06-1.13-4.52-.07-1.63.6-3.1 1.53-4.28 2.75-.49.51-.9 1.11-1.06 1.78z"/></svg></div>
      <h1 class="text-3xl font-bold mb-4">Spotify Mobile</h1>
      <p class="text-gray mb-8">Your music, your way. Native playback, ad skip, and more.</p>
      <div class="space-y-4">
        <button @click="handleLogin" :disabled="isLoading" class="w-full play-btn mx-auto flex items-center justify-center gap-3 text-lg font-semibold"><svg width="24" height="24" viewBox="0 0 24 24" fill="currentColor"><path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-2 15l-5-5 1.41-1.41L10 14.17l7.59-7.59L19 8l-9 9z"/></svg><span>Continue with Spotify</span></button>
        <p class="text-xs text-gray">By continuing, you agree to Spotify's Terms of Service and Privacy Policy.</p>
      </div>
      <div class="mt-12 pt-8 border-t border-white/10"><h3 class="text-xs text-gray uppercase tracking-wider mb-4">Features</h3><div class="grid grid-cols-3 gap-4 text-center">
        <div class="p-4 rounded-lg bg-dark"><svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="#1DB954" stroke-width="2" class="mx-auto mb-2"><circle cx="12" cy="12" r="10"></circle><polygon points="10 8 16 12 10 16"></polygon></svg><p class="font-medium text-sm">Native Playback</p><p class="text-xs text-gray mt-1">Web Playback SDK</p></div>
        <div class="p-4 rounded-lg bg-dark"><svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="#1DB954" stroke-width="2" class="mx-auto mb-2"><path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"></path><path d="M13.73 21a2 2 0 0 1-3.46 0"></path></svg><p class="font-medium text-sm">Ad Skip</p><p class="text-xs text-gray mt-1">Auto-detect & skip</p></div>
        <div class="p-4 rounded-lg bg-dark"><svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="#1DB954" stroke-width="2" class="mx-auto mb-2"><path d="M17 18a5 5 0 0 0-10 0"></path><path d="M12 2v20"></path><path d="M5.91 10.91a15 15 0 0 1 12.18 0"></path></svg><p class="font-medium text-sm">Background Audio</p><p class="text-xs text-gray mt-1">Foreground service</p></div>
      </div></div>
    </div>
  </div>
</template>