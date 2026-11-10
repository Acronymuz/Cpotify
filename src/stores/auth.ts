import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { Preferences } from '@capacitor/preferences'
import { Capacitor } from '@capacitor/core'
import type { SpotifyUser, AuthTokens } from '@/types/spotify'

const STORAGE_KEYS = { TOKENS: 'spotify_tokens', USER: 'spotify_user', CODE_VERIFIER: 'spotify_code_verifier', STATE: 'spotify_state' }
const SPOTIFY_CONFIG = { clientId: import.meta.env.VITE_SPOTIFY_CLIENT_ID || 'YOUR_CLIENT_ID', redirectUri: import.meta.env.VITE_SPOTIFY_REDIRECT_URI || (Capacitor.isNativePlatform() ? 'com.cpotify.app://callback' : 'http://localhost:5173/callback'), scopes: ['user-read-private','user-read-email','user-read-playback-state','user-modify-playback-state','user-read-currently-playing','user-read-recently-played','user-top-read','user-library-read','user-library-modify','playlist-read-private','playlist-read-collaborative','playlist-modify-private','playlist-modify-public','streaming','app-remote-control'].join(' ') }

function generateCodeVerifier(): string { const a = new Uint8Array(32); crypto.getRandomValues(a); return btoa(String.fromCharCode(...a)).replace(/\+/g,'-').replace(/\//g,'_').replace(/=/g,'') }
async function generateCodeChallenge(v: string): Promise<string> { const d = new TextEncoder().encode(v); const h = await crypto.subtle.digest('SHA-256', d); return btoa(String.fromCharCode(...new Uint8Array(h))).replace(/\+/g,'-').replace(/\//g,'_').replace(/=/g,'') }

export const useAuthStore = defineStore('auth', () => {
  const user = ref<SpotifyUser | null>(null)
  const tokens = ref<AuthTokens | null>(null)
  const isLoading = ref(false)
  const error = ref<string | null>(null)
  const isAuthenticated = computed(() => !!tokens.value?.access_token)
  const isPremium = computed(() => user.value?.product === 'premium')
  const accessToken = computed(() => tokens.value?.access_token || '')

  async function loadStoredAuth() {
    try {
      const [t, u] = await Promise.all([Preferences.get({ key: STORAGE_KEYS.TOKENS }), Preferences.get({ key: STORAGE_KEYS.USER })])
      if (t.value) { const s = JSON.parse(t.value) as AuthTokens; if (isTokenValid(s)) tokens.value = s; else if (s.refresh_token) { await refreshAccessToken(s.refresh_token); return } }
      if (u.value) user.value = JSON.parse(u.value)
    } catch { await clearAuth() }
  }
  function isTokenValid(td: AuthTokens): boolean { return Date.now() < td.obtained_at + (td.expires_in - 60) * 1000 }
  async function saveAuth(td: AuthTokens, ud: SpotifyUser) { tokens.value = td; user.value = ud; await Promise.all([Preferences.set({ key: STORAGE_KEYS.TOKENS, value: JSON.stringify(td) }), Preferences.set({ key: STORAGE_KEYS.USER, value: JSON.stringify(ud) })]) }
  async function clearAuth() { tokens.value = null; user.value = null; await Promise.all([Preferences.remove({ key: STORAGE_KEYS.TOKENS }), Preferences.remove({ key: STORAGE_KEYS.USER })]) }
  async function initiateLogin() {
    isLoading.value = true; error.value = null
    try {
      const cv = generateCodeVerifier(); const cc = await generateCodeChallenge(cv); const st = crypto.randomUUID()
      await Promise.all([Preferences.set({ key: STORAGE_KEYS.CODE_VERIFIER, value: cv }), Preferences.set({ key: STORAGE_KEYS.STATE, value: st })])
      const u = new URL('https://accounts.spotify.com/authorize')
      u.searchParams.set('client_id', SPOTIFY_CONFIG.clientId); u.searchParams.set('response_type', 'code'); u.searchParams.set('redirect_uri', SPOTIFY_CONFIG.redirectUri); u.searchParams.set('scope', SPOTIFY_CONFIG.scopes); u.searchParams.set('code_challenge_method', 'S256'); u.searchParams.set('code_challenge', cc); u.searchParams.set('state', st); u.searchParams.set('show_dialog', 'true')
      if (Capacitor.isNativePlatform()) { const { Browser } = await import('@capacitor/browser'); await Browser.open({ url: u.toString(), presentationStyle: 'fullscreen' }) } else { window.location.href = u.toString() }
    } catch (e) { error.value = 'Failed to initiate login'; console.error(e); isLoading.value = false }
  }
  async function handleCallback(code: string, state: string) {
    isLoading.value = true; error.value = null
    try {
      const [{ value: sv }, { value: ss }] = await Promise.all([Preferences.get({ key: STORAGE_KEYS.CODE_VERIFIER }), Preferences.get({ key: STORAGE_KEYS.STATE })])
      if (state !== ss) throw new Error('Invalid state')
      const tr = await fetch('https://accounts.spotify.com/api/token', { method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded' }, body: new URLSearchParams({ grant_type: 'authorization_code', code, redirect_uri: SPOTIFY_CONFIG.redirectUri, client_id: SPOTIFY_CONFIG.clientId, code_verifier: sv || '' }) })
      if (!tr.ok) { const err = await tr.json(); throw new Error(err.error_description || 'Token exchange failed') }
      const td = await tr.json(); const at: AuthTokens = { access_token: td.access_token, refresh_token: td.refresh_token, expires_in: td.expires_in, token_type: td.token_type, scope: td.scope, obtained_at: Date.now() }
      const ur = await fetch('https://api.spotify.com/v1/me', { headers: { Authorization: `Bearer ${at.access_token}` } })
      if (!ur.ok) throw new Error('Failed to fetch user profile')
      await saveAuth(at, await ur.json())
      await Promise.all([Preferences.remove({ key: STORAGE_KEYS.CODE_VERIFIER }), Preferences.remove({ key: STORAGE_KEYS.STATE })])
    } catch (e) { error.value = e instanceof Error ? e.message : 'Authentication failed'; console.error(e); await clearAuth(); throw e } finally { isLoading.value = false }
  }
  async function refreshAccessToken(rt: string): Promise<boolean> {
    try {
      const r = await fetch('https://accounts.spotify.com/api/token', { method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded' }, body: new URLSearchParams({ grant_type: 'refresh_token', refresh_token: rt, client_id: SPOTIFY_CONFIG.clientId }) })
      if (!r.ok) { await clearAuth(); return false }
      const d = await r.json(); const nt: AuthTokens = { ...tokens.value!, access_token: d.access_token, expires_in: d.expires_in, obtained_at: Date.now() }
      if (d.refresh_token) nt.refresh_token = d.refresh_token
      await saveAuth(nt, user.value!); return true
    } catch { console.error('Token refresh failed'); await clearAuth(); return false }
  }
  async function ensureValidToken(): Promise<string | null> { if (!tokens.value) return null; if (isTokenValid(tokens.value)) return tokens.value.access_token; if (tokens.value.refresh_token) { const ok = await refreshAccessToken(tokens.value.refresh_token); return ok ? tokens.value?.access_token || null : null }; await clearAuth(); return null }
  async function logout() { await clearAuth() }
  return { user, tokens, isLoading, error, isAuthenticated, isPremium, accessToken, loadStoredAuth, initiateLogin, handleCallback, ensureValidToken, logout, refreshAccessToken }
})