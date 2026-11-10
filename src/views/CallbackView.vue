<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { useRouter, useRoute } from 'vue-router'
import { Capacitor } from '@capacitor/core'
import { Browser } from '@capacitor/browser'

const authStore = useAuthStore()
const router = useRouter()
const route = useRoute()
const isProcessing = ref(true)
const error = ref<string | null>(null)

async function handleCallback() {
  const code = route.query.code as string
  const state = route.query.state as string
  const err = route.query.error as string
  if (err) { error.value = err; isProcessing.value = false; return }
  if (!code || !state) { error.value = 'Invalid callback parameters'; isProcessing.value = false; return }
  try { await authStore.handleCallback(code, state); await router.push('/home') }
  catch (e) { error.value = e instanceof Error ? e.message : 'Authentication failed' }
  finally { isProcessing.value = false; if (Capacitor.isNativePlatform()) try { await Browser.close() } catch {} }
}

onMounted(handleCallback)
</script>

<template>
  <div class="callback-view flex flex-col items-center justify-center h-full px-6 text-center">
    <div class="max-w-md w-full">
      <div v-if="isProcessing" class="mb-8"><div class="loading-spinner mx-auto mb-6"></div><h1 class="text-2xl font-bold mb-2">Signing in...</h1><p class="text-gray">Please wait while we connect to Spotify</p></div>
      <div v-else-if="error" class="mb-8"><div class="w-20 h-20 mx-auto mb-6 rounded-full bg-error/20 flex items-center justify-center"><svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="#e22134" stroke-width="2"><circle cx="12" cy="12" r="10"></circle><line x1="15" y1="9" x2="9" y2="15"></line><line x1="9" y1="9" x2="15" y2="15"></line></svg></div><h1 class="text-2xl font-bold mb-2">Sign In Failed</h1><p class="text-gray mb-6">{{ error }}</p><button @click="handleCallback" class="play-btn mx-auto flex items-center justify-center gap-2 text-lg font-semibold"><svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor"><path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-2 15l-5-5 1.41-1.41L10 14.17l7.59-7.59L19 8l-9 9z"/></svg><span>Try Again</span></button></div>
      <div v-else class="mb-8"><div class="w-20 h-20 mx-auto mb-6 rounded-full bg-green/20 flex items-center justify-center"><svg width="40" height="40" viewBox="0 0 24 24" fill="#1DB954"><path d="M12 0C5.374 0 0 5.373 0 12c0 6.627 5.373 12 12 12s12-5.373 12-12C24 5.373 18.627 0 12 0zm5.521 17.34c-.13.381-.394.688-.77.87-1.834.877-3.996 1.48-6.5.647-.39-.128-.75-.365-.94-.746-.12-.25-.09-.554.05-.77 2.215-1.923 4.685-3.76 6.506-4.786.502-.28 1.1.008 1.158.655.02.213-.07.423-.24.56zm-1.612-3.67c.27-.59.33-1.295.17-1.91-3.075-5.832-7.135-10.452-11.79-13.55-.17-.11-.38-.13-.55.02-.39.36-.6.85-.6 1.35 0 4.867 3.99 8.765 9.07 10.09.27.07.55-.05.73-.31zm-12.555-2.197c-.51.72-.75 1.56-.7 2.36 4.8 2.63 10.16 2.5 13.49-.3.33-1.3-.33-2.62-.93-3.1-1.38-1.1-3.06-1.13-4.52-.07-1.63.6-3.1 1.53-4.28 2.75-.49.51-.9 1.11-1.06 1.78z"/></svg></div><h1 class="text-2xl font-bold mb-2">Welcome!</h1><p class="text-gray mb-6">Successfully connected to Spotify</p><div class="play-btn mx-auto flex items-center justify-center gap-2 text-lg font-semibold"><svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor"><path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-2 15l-5-5 1.41-1.41L10 14.17l7.59-7.59L19 8l-9 9z"/></svg><span>Loading...</span></div></div>
    </div>
  </div>
</template>