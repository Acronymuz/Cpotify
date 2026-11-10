export interface SpotifyUser {
  id: string
  display_name: string
  email: string
  images: SpotifyImage[]
  product: 'free' | 'premium'
  country: string
}

export interface SpotifyImage {
  url: string
  height: number | null
  width: number | null
}

export interface SpotifyTrack {
  id: string
  name: string
  artists: SpotifyArtist[]
  album: SpotifyAlbum
  duration_ms: number
  external_urls: { spotify: string }
  uri: string
  is_playable: boolean
  preview_url: string | null
  track_number: number
  disc_number: number
  explicit: boolean
  popularity: number
  type: 'track'
  href: string
  external_ids?: { isrc: string }
  linked_from?: SpotifyTrack
  restrictions?: { reason: string }
  available_markets?: string[]
}

export interface SpotifyArtist {
  id: string
  name: string
  type: 'artist'
  uri: string
  external_urls: { spotify: string }
  href: string
  images?: SpotifyImage[]
  genres?: string[]
  popularity?: number
  followers?: { total: number }
}

export interface SpotifyAlbum {
  id: string
  name: string
  type: 'album'
  uri: string
  external_urls: { spotify: string }
  href: string
  images: SpotifyImage[]
  artists: SpotifyArtist[]
  release_date: string
  release_date_precision: string
  total_tracks: number
  album_type: 'album' | 'single' | 'compilation'
  available_markets?: string[]
  copyrights?: { text: string; type: string }[]
  label?: string
  popularity?: number
  tracks?: PagingObject<SpotifyTrack>
}

export interface SpotifyPlaylist {
  id: string
  name: string
  description: string
  images: SpotifyImage[]
  external_urls: { spotify: string }
  uri: string
  href: string
  owner: SpotifyUser
  public: boolean
  collaborative: boolean
  tracks: { href: string; total: number }
  type: 'playlist'
  followers: { total: number }
  snapshot_id: string
}

export interface PagingObject<T> {
  href: string
  items: T[]
  limit: number
  next: string | null
  offset: number
  previous: string | null
  total: number
}

export interface SpotifySearchResponse {
  tracks?: PagingObject<SpotifyTrack>
  artists?: PagingObject<SpotifyArtist>
  albums?: PagingObject<SpotifyAlbum>
  playlists?: PagingObject<SpotifyPlaylist>
}

export interface SpotifyPlaybackState {
  device: SpotifyDevice
  repeat_state: 'off' | 'track' | 'context'
  shuffle_state: boolean
  context: SpotifyContext | null
  timestamp: number
  progress_ms: number
  is_playing: boolean
  item: SpotifyTrack | null
  currently_playing_type: 'track' | 'episode' | 'ad' | 'unknown'
  actions: SpotifyActions
}

export interface SpotifyDevice {
  id: string | null
  is_active: boolean
  is_private_session: boolean
  is_restricted: boolean
  name: string
  type: string
  volume_percent: number | null
  supports_volume: boolean
}

export interface SpotifyContext {
  type: 'artist' | 'playlist' | 'album' | 'show' | 'episode'
  href: string
  external_urls: { spotify: string }
  uri: string
}

export interface SpotifyActions {
  disallows: {
    pausing?: boolean
    resuming?: boolean
    seeking?: boolean
    skipping_next?: boolean
    skipping_prev?: boolean
    toggling_repeat_context?: boolean
    toggling_shuffle?: boolean
    toggling_repeat_track?: boolean
    transferring_playback?: boolean
  }
}

export interface SpotifyQueue {
  currently_playing: SpotifyTrack | null
  queue: SpotifyTrack[]
}

export interface AuthTokens {
  access_token: string
  refresh_token: string
  expires_in: number
  token_type: string
  scope: string
  obtained_at: number
}

export interface WebPlaybackTrack {
  id: string
  uri: string
  type: 'track' | 'episode' | 'ad'
  name: string
  artist_name: string
  album_name: string
  duration_ms: number
  is_paused: boolean
  is_playing: boolean
  is_saved: boolean
  linked_track_uri: string | null
  linked_from_uri: string | null
  images: Array<{ url: string; width: number; height: number }>
  album_uri: string
  artist_uris: string[]
  playable: boolean
  disc_number: number
  track_number: number
  explicit: boolean
  preview_url: string | null
  external_urls: { spotify: string }
}

export interface WebPlaybackState {
  context: { uri: string; metadata: { current_page: number; page_size: number; identifiers: string[] } }
  disallows: { pausing: boolean; peeking_next: boolean; peeking_prev: boolean; resuming: boolean; seeking: boolean; skipping_next: boolean; skipping_prev: boolean }
  paused: boolean
  position: number
  duration: number
  track_window: { current_track: WebPlaybackTrack; next_tracks: WebPlaybackTrack[]; prev_tracks: WebPlaybackTrack[] }
  repeat_mode: number
  shuffle: boolean
  timestamp: number
  restrictions: { disallow_pausing: boolean; disallow_peeking_next: boolean; disallow_peeking_prev: boolean; disallow_resuming: boolean; disallow_seeking: boolean; disallow_skipping_next: boolean; disallow_skipping_prev: boolean }
}

export interface PlayerInstance {
  connect(): Promise<boolean>
  disconnect(): void
  addListener(event: string, callback: (...args: any[]) => void): void
  removeListener(event: string, callback: (...args: any[]) => void): void
  getCurrentState(): Promise<WebPlaybackState | null>
  setName(name: string): Promise<void>
  getVolume(): Promise<number>
  setVolume(volume: number): Promise<void>
  pause(): Promise<void>
  resume(): Promise<void>
  togglePlay(): Promise<void>
  seek(position_ms: number): Promise<void>
  previousTrack(): Promise<void>
  nextTrack(): Promise<void>
  addToQueue(uri: string): Promise<void>
  enableMediaSession(): Promise<void>
}