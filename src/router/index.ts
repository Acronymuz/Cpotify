import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const routes = [
  { path: '/', redirect: '/home' },
  { path: '/login', name: 'login', component: () => import('@/views/LoginView.vue'), meta: { requiresAuth: false } },
  { path: '/callback', name: 'callback', component: () => import('@/views/CallbackView.vue'), meta: { requiresAuth: false } },
  { path: '/home', name: 'home', component: () => import('@/views/HomeView.vue'), meta: { requiresAuth: true } },
  { path: '/search', name: 'search', component: () => import('@/views/SearchView.vue'), meta: { requiresAuth: true } },
  { path: '/library', name: 'library', component: () => import('@/views/LibraryView.vue'), meta: { requiresAuth: true } },
  { path: '/playlist/:id', name: 'playlist', component: () => import('@/views/PlaylistView.vue'), meta: { requiresAuth: true }, props: true },
  { path: '/album/:id', name: 'album', component: () => import('@/views/AlbumView.vue'), meta: { requiresAuth: true }, props: true },
  { path: '/artist/:id', name: 'artist', component: () => import('@/views/ArtistView.vue'), meta: { requiresAuth: true }, props: true },
  { path: '/settings', name: 'settings', component: () => import('@/views/SettingsView.vue'), meta: { requiresAuth: true } },
  { path: '/:pathMatch(.*)*', redirect: '/home' }
]

const router = createRouter({ history: createWebHistory(import.meta.env.BASE_URL), routes, scrollBehavior() { return { top: 0 } } })

router.beforeEach(async (_to, _from, next) => { const authStore = useAuthStore(); if (_to.meta.requiresAuth && !authStore.isAuthenticated) { if (_to.path !== '/login' && _to.path !== '/callback') { next('/login'); return } } if (authStore.isAuthenticated && (_to.path === '/login' || _to.path === '/callback')) { next('/home'); return } next() })

export default router