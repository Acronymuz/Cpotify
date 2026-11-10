import type { SpotifyTrack, SpotifyAlbum, SpotifyArtist, SpotifyPlaylist, SpotifyUser, SpotifySearchResponse, PagingObject, SpotifyDevice, SpotifyPlaybackState, SpotifyQueue } from '@/types/spotify'

const API_BASE = 'https://api.spotify.com/v1'

class SpotifyApi {
  private async getHeaders() { const { useAuthStore } = await import('@/stores/auth'); const t = await useAuthStore().ensureValidToken(); if (!t) throw new Error('Not authenticated'); return { Authorization: `Bearer ${t}`, 'Content-Type': 'application/json' } }
  private async request<T>(ep: string, opt: RequestInit = {}): Promise<T> { const h = await this.getHeaders(); const r = await fetch(`${API_BASE}${ep}`, { ...opt, headers: { ...h, ...opt.headers } }); if (r.status === 401) { const { useAuthStore } = await import('@/stores/auth'); const nt = await useAuthStore().ensureValidToken(); if (nt) { const rh = await this.getHeaders(); const rr = await fetch(`${API_BASE}${ep}`, { ...opt, headers: { ...rh, ...opt.headers } }); if (rr.ok) return rr.json() } throw new Error('Authentication failed') } if (!r.ok) { const e = await r.json().catch(() => ({})); throw new Error(e.error?.message || `Request failed: ${r.status}`) } if (r.status === 204) return undefined as T; return r.json() }

  async getCurrentUser() { return this.request<SpotifyUser>('/me') }
  async getUserPlaylists(limit = 50, offset = 0) { return this.request<PagingObject<SpotifyPlaylist>>(`/me/playlists?limit=${limit}&offset=${offset}`) }
  async getPlaylist(id: string) { return this.request<SpotifyPlaylist>(`/playlists/${id}`) }
  async getPlaylistTracks(id: string, limit = 100, offset = 0) { return this.request<PagingObject<{ track: SpotifyTrack; added_at: string }>>(`/playlists/${id}/tracks?limit=${limit}&offset=${offset}&fields=items(track(id,name,artists,album,duration_ms,external_urls,uri,is_playable,preview_url,track_number,disc_number,explicit,popularity)),next,total`) }
  async getAlbum(id: string) { return this.request<SpotifyAlbum>(`/albums/${id}`) }
  async getAlbumTracks(id: string, limit = 50, offset = 0) { return this.request<PagingObject<SpotifyTrack>>(`/albums/${id}/tracks?limit=${limit}&offset=${offset}`) }
  async getArtist(id: string) { return this.request<SpotifyArtist>(`/artists/${id}`) }
  async getArtistTopTracks(id: string, market = 'from_token') { return this.request<{ tracks: SpotifyTrack[] }>(`/artists/${id}/top-tracks?market=${market}`) }
  async getArtistAlbums(id: string, limit = 20, offset = 0) { return this.request<PagingObject<SpotifyAlbum>>(`/artists/${id}/albums?limit=${limit}&offset=${offset}&include_groups=album,single,compilation`) }
  async search(q: string, types: ('track'|'artist'|'album'|'playlist'|'show'|'episode')[] = ['track','artist','album','playlist'], limit = 20, offset = 0) { return this.request<SpotifySearchResponse>(`/search?q=${encodeURIComponent(q)}&type=${types.join(',')}&limit=${limit}&offset=${offset}`) }
  async getRecommendations(st: string[], sa: string[], sg: string[], limit = 20) { const p = new URLSearchParams({ limit: limit.toString() }); if (st.length) p.append('seed_tracks', st.slice(0,5).join(',')); if (sa.length) p.append('seed_artists', sa.slice(0,5).join(',')); if (sg.length) p.append('seed_genres', sg.slice(0,5).join(',')); return this.request<{ tracks: SpotifyTrack[] }>(`/recommendations?${p.toString()}`) }
  async getAvailableDevices() { return this.request<{ devices: SpotifyDevice[] }>('/me/player/devices') }
  async getPlaybackState() { try { return await this.request<SpotifyPlaybackState>('/me/player') } catch (e) { if (e instanceof Error && e.message.includes('204')) return null; throw e } }
  async getCurrentlyPlaying() { try { return await this.request<SpotifyPlaybackState>('/me/player/currently-playing') } catch (e) { if (e instanceof Error && e.message.includes('204')) return null; throw e } }
  async getQueue() { return this.request<SpotifyQueue>('/me/player/queue') }
  async startPlayback(ctxUri?: string, uris?: string[], offset?: { position: number } | { uri: string }, deviceId?: string) { const b: any = {}; if (ctxUri) b.context_uri = ctxUri; if (uris) b.uris = uris; if (offset) b.offset = offset; const p = deviceId ? `?device_id=${deviceId}` : ''; await this.request<void>(`/me/player/play${p}`, { method: 'PUT', body: JSON.stringify(b) }) }
  async pausePlayback(deviceId?: string) { const p = deviceId ? `?device_id=${deviceId}` : ''; await this.request<void>(`/me/player/pause${p}`, { method: 'PUT' }) }
  async nextTrack(deviceId?: string) { const p = deviceId ? `?device_id=${deviceId}` : ''; await this.request<void>(`/me/player/next${p}`, { method: 'POST' }) }
  async previousTrack(deviceId?: string) { const p = deviceId ? `?device_id=${deviceId}` : ''; await this.request<void>(`/me/player/previous${p}`, { method: 'POST' }) }
  async seek(pos: number, deviceId?: string) { const p = deviceId ? `&device_id=${deviceId}` : ''; await this.request<void>(`/me/player/seek?position_ms=${pos}${p}`, { method: 'PUT' }) }
  async setVolume(v: number, deviceId?: string) { const p = deviceId ? `&device_id=${deviceId}` : ''; await this.request<void>(`/me/player/volume?volume_percent=${v}${p}`, { method: 'PUT' }) }
  async setRepeat(s: 'off'|'track'|'context', deviceId?: string) { const p = deviceId ? `&device_id=${deviceId}` : ''; await this.request<void>(`/me/player/repeat?state=${s}${p}`, { method: 'PUT' }) }
  async setShuffle(s: boolean, deviceId?: string) { const p = deviceId ? `&device_id=${deviceId}` : ''; await this.request<void>(`/me/player/shuffle?state=${s}${p}`, { method: 'PUT' }) }
  async addToQueue(uri: string, deviceId?: string) { const p = deviceId ? `&device_id=${deviceId}` : ''; await this.request<void>(`/me/player/queue?uri=${encodeURIComponent(uri)}${p}`, { method: 'POST' }) }
  async getRecentlyPlayed(limit = 50) { return this.request<PagingObject<{ track: SpotifyTrack; played_at: string; context: any }>>(`/me/player/recently-played?limit=${limit}`) }
  async getSavedTracks(limit = 50, offset = 0) { return this.request<PagingObject<{ track: SpotifyTrack; added_at: string }>>(`/me/tracks?limit=${limit}&offset=${offset}`) }
  async saveTracks(ids: string[]) { await this.request<void>('/me/tracks', { method: 'PUT', body: JSON.stringify({ ids }) }) }
  async removeSavedTracks(ids: string[]) { await this.request<void>(`/me/tracks?ids=${ids.join(',')}`, { method: 'DELETE' }) }
  async checkSavedTracks(ids: string[]) { return this.request<boolean[]>(`/me/tracks/contains?ids=${ids.join(',')}`) }
  async getSavedAlbums(limit = 50, offset = 0) { return this.request<PagingObject<{ album: SpotifyAlbum; added_at: string }>>(`/me/albums?limit=${limit}&offset=${offset}`) }
  async getTopTracks(limit = 50, tr: 'short_term'|'medium_term'|'long_term' = 'medium_term') { return this.request<PagingObject<SpotifyTrack>>(`/me/top/tracks?limit=${limit}&time_range=${tr}`) }
  async getTopArtists(limit = 50, tr: 'short_term'|'medium_term'|'long_term' = 'medium_term') { return this.request<PagingObject<SpotifyArtist>>(`/me/top/artists?limit=${limit}&time_range=${tr}`) }
  async createPlaylist(uid: string, name: string, desc = '', pub = false) { return this.request<SpotifyPlaylist>(`/users/${uid}/playlists`, { method: 'POST', body: JSON.stringify({ name, description: desc, public: pub }) }) }
  async addTracksToPlaylist(pid: string, uris: string[], pos?: number) { return this.request<{ snapshot_id: string }>(`/playlists/${pid}/tracks`, { method: 'POST', body: JSON.stringify({ uris, position: pos }) }) }
  async removeTracksFromPlaylist(pid: string, uris: string[]) { return this.request<{ snapshot_id: string }>(`/playlists/${pid}/tracks`, { method: 'DELETE', body: JSON.stringify({ tracks: uris.map(u => ({ uri: u })) }) }) }
  async getNewReleases(limit = 20, offset = 0) { return this.request<{ albums: PagingObject<SpotifyAlbum> }>(`/browse/new-releases?limit=${limit}&offset=${offset}`) }
  async getFeaturedPlaylists(limit = 20, offset = 0) { return this.request<{ playlists: PagingObject<SpotifyPlaylist> }>(`/browse/featured-playlists?limit=${limit}&offset=${offset}`) }
  async getCategories(limit = 20, offset = 0) { return this.request<{ categories: PagingObject<{ id: string; name: string; icons: { url: string; height: number; width: number }[] }> }>(`/browse/categories?limit=${limit}&offset=${offset}`) }
  async getCategoryPlaylists(cid: string, limit = 20, offset = 0) { return this.request<{ playlists: PagingObject<SpotifyPlaylist> }>(`/browse/categories/${cid}/playlists?limit=${limit}&offset=${offset}`) }
}

export const spotifyApi = new SpotifyApi()