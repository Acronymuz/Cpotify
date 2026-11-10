const API_BASE = 'https://api.spotify.com/v1';
class SpotifyApi {
    async getHeaders() { const { useAuthStore } = await import('@/stores/auth'); const t = await useAuthStore().ensureValidToken(); if (!t)
        throw new Error('Not authenticated'); return { Authorization: `Bearer ${t}`, 'Content-Type': 'application/json' }; }
    async request(ep, opt = {}) { const h = await this.getHeaders(); const r = await fetch(`${API_BASE}${ep}`, { ...opt, headers: { ...h, ...opt.headers } }); if (r.status === 401) {
        const { useAuthStore } = await import('@/stores/auth');
        const nt = await useAuthStore().ensureValidToken();
        if (nt) {
            const rh = await this.getHeaders();
            const rr = await fetch(`${API_BASE}${ep}`, { ...opt, headers: { ...rh, ...opt.headers } });
            if (rr.ok)
                return rr.json();
        }
        throw new Error('Authentication failed');
    } if (!r.ok) {
        const e = await r.json().catch(() => ({}));
        throw new Error(e.error?.message || `Request failed: ${r.status}`);
    } if (r.status === 204)
        return undefined; return r.json(); }
    async getCurrentUser() { return this.request('/me'); }
    async getUserPlaylists(limit = 50, offset = 0) { return this.request(`/me/playlists?limit=${limit}&offset=${offset}`); }
    async getPlaylist(id) { return this.request(`/playlists/${id}`); }
    async getPlaylistTracks(id, limit = 100, offset = 0) { return this.request(`/playlists/${id}/tracks?limit=${limit}&offset=${offset}&fields=items(track(id,name,artists,album,duration_ms,external_urls,uri,is_playable,preview_url,track_number,disc_number,explicit,popularity)),next,total`); }
    async getAlbum(id) { return this.request(`/albums/${id}`); }
    async getAlbumTracks(id, limit = 50, offset = 0) { return this.request(`/albums/${id}/tracks?limit=${limit}&offset=${offset}`); }
    async getArtist(id) { return this.request(`/artists/${id}`); }
    async getArtistTopTracks(id, market = 'from_token') { return this.request(`/artists/${id}/top-tracks?market=${market}`); }
    async getArtistAlbums(id, limit = 20, offset = 0) { return this.request(`/artists/${id}/albums?limit=${limit}&offset=${offset}&include_groups=album,single,compilation`); }
    async search(q, types = ['track', 'artist', 'album', 'playlist'], limit = 20, offset = 0) { return this.request(`/search?q=${encodeURIComponent(q)}&type=${types.join(',')}&limit=${limit}&offset=${offset}`); }
    async getRecommendations(st, sa, sg, limit = 20) { const p = new URLSearchParams({ limit: limit.toString() }); if (st.length)
        p.append('seed_tracks', st.slice(0, 5).join(',')); if (sa.length)
        p.append('seed_artists', sa.slice(0, 5).join(',')); if (sg.length)
        p.append('seed_genres', sg.slice(0, 5).join(',')); return this.request(`/recommendations?${p.toString()}`); }
    async getAvailableDevices() { return this.request('/me/player/devices'); }
    async getPlaybackState() { try {
        return await this.request('/me/player');
    }
    catch (e) {
        if (e instanceof Error && e.message.includes('204'))
            return null;
        throw e;
    } }
    async getCurrentlyPlaying() { try {
        return await this.request('/me/player/currently-playing');
    }
    catch (e) {
        if (e instanceof Error && e.message.includes('204'))
            return null;
        throw e;
    } }
    async getQueue() { return this.request('/me/player/queue'); }
    async startPlayback(ctxUri, uris, offset, deviceId) { const b = {}; if (ctxUri)
        b.context_uri = ctxUri; if (uris)
        b.uris = uris; if (offset)
        b.offset = offset; const p = deviceId ? `?device_id=${deviceId}` : ''; await this.request(`/me/player/play${p}`, { method: 'PUT', body: JSON.stringify(b) }); }
    async pausePlayback(deviceId) { const p = deviceId ? `?device_id=${deviceId}` : ''; await this.request(`/me/player/pause${p}`, { method: 'PUT' }); }
    async nextTrack(deviceId) { const p = deviceId ? `?device_id=${deviceId}` : ''; await this.request(`/me/player/next${p}`, { method: 'POST' }); }
    async previousTrack(deviceId) { const p = deviceId ? `?device_id=${deviceId}` : ''; await this.request(`/me/player/previous${p}`, { method: 'POST' }); }
    async seek(pos, deviceId) { const p = deviceId ? `&device_id=${deviceId}` : ''; await this.request(`/me/player/seek?position_ms=${pos}${p}`, { method: 'PUT' }); }
    async setVolume(v, deviceId) { const p = deviceId ? `&device_id=${deviceId}` : ''; await this.request(`/me/player/volume?volume_percent=${v}${p}`, { method: 'PUT' }); }
    async setRepeat(s, deviceId) { const p = deviceId ? `&device_id=${deviceId}` : ''; await this.request(`/me/player/repeat?state=${s}${p}`, { method: 'PUT' }); }
    async setShuffle(s, deviceId) { const p = deviceId ? `&device_id=${deviceId}` : ''; await this.request(`/me/player/shuffle?state=${s}${p}`, { method: 'PUT' }); }
    async addToQueue(uri, deviceId) { const p = deviceId ? `&device_id=${deviceId}` : ''; await this.request(`/me/player/queue?uri=${encodeURIComponent(uri)}${p}`, { method: 'POST' }); }
    async getRecentlyPlayed(limit = 50) { return this.request(`/me/player/recently-played?limit=${limit}`); }
    async getSavedTracks(limit = 50, offset = 0) { return this.request(`/me/tracks?limit=${limit}&offset=${offset}`); }
    async saveTracks(ids) { await this.request('/me/tracks', { method: 'PUT', body: JSON.stringify({ ids }) }); }
    async removeSavedTracks(ids) { await this.request(`/me/tracks?ids=${ids.join(',')}`, { method: 'DELETE' }); }
    async checkSavedTracks(ids) { return this.request(`/me/tracks/contains?ids=${ids.join(',')}`); }
    async getSavedAlbums(limit = 50, offset = 0) { return this.request(`/me/albums?limit=${limit}&offset=${offset}`); }
    async getTopTracks(limit = 50, tr = 'medium_term') { return this.request(`/me/top/tracks?limit=${limit}&time_range=${tr}`); }
    async getTopArtists(limit = 50, tr = 'medium_term') { return this.request(`/me/top/artists?limit=${limit}&time_range=${tr}`); }
    async createPlaylist(uid, name, desc = '', pub = false) { return this.request(`/users/${uid}/playlists`, { method: 'POST', body: JSON.stringify({ name, description: desc, public: pub }) }); }
    async addTracksToPlaylist(pid, uris, pos) { return this.request(`/playlists/${pid}/tracks`, { method: 'POST', body: JSON.stringify({ uris, position: pos }) }); }
    async removeTracksFromPlaylist(pid, uris) { return this.request(`/playlists/${pid}/tracks`, { method: 'DELETE', body: JSON.stringify({ tracks: uris.map(u => ({ uri: u })) }) }); }
    async getNewReleases(limit = 20, offset = 0) { return this.request(`/browse/new-releases?limit=${limit}&offset=${offset}`); }
    async getFeaturedPlaylists(limit = 20, offset = 0) { return this.request(`/browse/featured-playlists?limit=${limit}&offset=${offset}`); }
    async getCategories(limit = 20, offset = 0) { return this.request(`/browse/categories?limit=${limit}&offset=${offset}`); }
    async getCategoryPlaylists(cid, limit = 20, offset = 0) { return this.request(`/browse/categories/${cid}/playlists?limit=${limit}&offset=${offset}`); }
}
export const spotifyApi = new SpotifyApi();
