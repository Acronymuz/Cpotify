import { Plugin, PluginListenerHandle } from '@capacitor/core';

export interface PlayerState {
  track: Track | null;
  contextUri: string | null;
  contextName: string | null;
  previous: Track | null;
  next: Track | null;
  isPaused: boolean;
  isBuffering: boolean;
  positionMs: number;
  timestamp: number;
  durationMs: number;
  shuffle: boolean;
  repeat: number; // 0 off, 1 context, 2 track
  speed: number;
  restrictions: Record<string, unknown>;
  playbackId: string | null;
}

export interface Track {
  kind: 'track' | 'episode' | 'ad';
  uri: string;
  uid: string | null;
  name: string;
  advertiser: string | null;
  durationMs: number | null;
  artists: Array<{ uri: string; name: string }>;
  album: { uri: string; name: string } | null;
  image: string | null;
  explicit: boolean;
  mediaType: string;
  provider: string | null;
}

export interface QueueState {
  current: Track | null;
  queued: Track[];
  nextUp: Track[];
}

export interface AudioInfo {
  bitrate: number;
  mime: string;
  level: string;
}

export interface MediaState {
  paused: boolean;
  muted: boolean;
  volume: number;
  level: number;
  own: number;
  time: number;
  readyState: number;
  networkState: number;
  error: string | null;
  keys: number;
  keyWaits: number;
  ended: boolean;
  playerVolume: number | null;
  active: boolean;
}

export interface MyspotiBridgePlugin extends Plugin {
  /**
   * Load the Spotify Web Player in the WebView
   */
  loadPlayer(): Promise<void>;

  /**
   * Play a context (playlist, album, etc.)
   */
  play(options: { contextUri: string; trackUri?: string; trackUid?: string; index?: number; positionMs?: number }): Promise<void>;

  /**
   * Pause playback
   */
  pause(): Promise<void>;

  /**
   * Resume playback
   */
  resume(): Promise<void>;

  /**
   * Seek to position
   */
  seek(options: { positionMs: number }): Promise<void>;

  /**
   * Skip to next track
   */
  next(): Promise<void>;

  /**
   * Skip to previous track
   */
  previous(): Promise<void>;

  /**
   * Set shuffle mode
   */
  setShuffle(options: { on: boolean }): Promise<void>;

  /**
   * Set repeat mode
   */
  setRepeat(options: { mode: 0 | 1 | 2 }): Promise<void>;

  /**
   * Set playback speed
   */
  setSpeed(options: { speed: number }): Promise<void>;

  /**
   * Get current player state
   */
  getState(): Promise<PlayerState>;

  /**
   * Get current queue state
   */
  getQueue(): Promise<QueueState>;

  /**
   * Get audio quality info
   */
  getAudioInfo(): Promise<AudioInfo>;

  /**
   * Set audio quality
   */
  setAudioQuality(options: { level: 'auto' | 'low' | 'normal' | 'high' | 'very_high' }): Promise<void>;

  /**
   * Preload next track
   */
  preloadNext(): Promise<string | null>;

  /**
   * Add tracks to queue
   */
  addToQueue(options: { uris: string[] }): Promise<void>;

  /**
   * Remove tracks from queue
   */
  removeFromQueue(options: { items: Array<{ uri: string; uid: string }> }): Promise<void>;

  /**
   * Clear queue
   */
  clearQueue(): Promise<void>;

  /**
   * Skip to specific queued item
   */
  skipTo(options: { uri: string; uid?: string }): Promise<void>;

  /**
   * Insert tracks into queue
   */
  insertIntoQueue(options: { uris: string[]; before: { uri: string; uid?: string } }): Promise<void>;

  /**
   * Reorder queue
   */
  reorderQueue(options: { items: Array<{ uri: string; uid: string }>; before?: { uri: string; uid: string }; after?: { uri: string; uid: string } }): Promise<void>;

  /**
   * Mute/unmute (for ads)
   */
  mute(options: { on: boolean }): Promise<{ count: number }>;

  /**
   * Hush/unhush (for music videos)
   */
  hush(options: { on: boolean }): Promise<{ count: number }>;

  /**
   * Get media element state
   */
  getMediaState(): Promise<MediaState | null>;

  /**
   * Set media volume (local only, no Spotify Connect)
   */
  setMediaVolume(options: { level: number }): Promise<{ level: number }>;

  /**
   * Restore media volume to player's own level
   */
  restoreMediaVolume(): Promise<{ level: number }>;

  /**
   * Fade media volume
   */
  fadeMediaVolume(options: { steps: number[]; ms: number; restore?: boolean; rise?: { ms: number; wait?: number } }): Promise<boolean>;

  /**
   * Ramp media volume
   */
  rampMediaVolume(options: { to: number; ms: number }): Promise<boolean>;

  /**
   * Rise media volume after pause
   */
  riseMediaVolume(options: { ms: number; wait: number; keep?: boolean; switch?: boolean }): Promise<boolean>;

  /**
   * Get volume via Spotify Connect
   */
  getVolume(): Promise<number>;

  /**
   * Set volume via Spotify Connect
   */
  setVolume(options: { level: number }): Promise<void>;

  /**
   * Check if track is in library
   */
  checkLibrary(options: { uris: string[] }): Promise<boolean>;

  /**
   * Add to library
   */
  addToLibrary(options: { uris: string[] }): Promise<void>;

  /**
   * Remove from library
   */
  removeFromLibrary(options: { uris: string[] }): Promise<void>;

  /**
   * List available services
   */
  listServices(): Promise<Record<string, string[]>>;

  /**
   * Call a service method
   */
  callService(options: { service: string; method: string; args: unknown[] }): Promise<unknown>;

  /**
   * Set playlist cover
   */
  setPlaylistCover(options: { uri: string; jpegBase64: string | null }): Promise<void>;

  /**
   * Execute GraphQL query
   */
  gqlQuery(options: { name: string; hash: string; variables: Record<string, unknown> }): Promise<unknown>;

  /**
   * Get web token for HTTP API
   */
  getToken(): Promise<{
    accessToken: string;
    expiresAtMs: number;
    clientId: string;
    appVersion: string;
    isAnonymous: boolean;
  }>;

  /**
   * Enable/disable ad blocking
   */
  setAdBlock(options: { enabled: boolean }): Promise<void>;

  /**
   * Set custom block rules
   */
  setBlockRules(options: { rules: string[] }): Promise<void>;

  /**
   * Reload the player page
   */
  reload(): Promise<void>;

  /**
   * Listen for player state changes
   */
  addListener(eventName: 'stateChange', listener: (state: PlayerState) => void): Promise<PluginListenerHandle>;

  /**
   * Listen for queue changes
   */
  addListener(eventName: 'queueChange', listener: (queue: QueueState) => void): Promise<PluginListenerHandle>;

  /**
   * Listen for media state changes
   */
  addListener(eventName: 'mediaStateChange', listener: (state: MediaState) => void): Promise<PluginListenerHandle>;

  /**
   * Listen for skip events (diagnostics)
   */
  addListener(eventName: 'skip', listener: (info: { by: string; stack: string }) => void): Promise<PluginListenerHandle>;

  /**
   * Listen for network events
   */
  addListener(eventName: 'network', listener: (info: { url: string; status: number; bytes: number; ms: number }) => void): Promise<PluginListenerHandle>;

  /**
   * Listen for JS errors
   */
  addListener(eventName: 'jsError', listener: (info: { error: string }) => void): Promise<PluginListenerHandle>;

  /**
   * Listen for DRM probe results
   */
  addListener(eventName: 'drmProbe', listener: (info: { ok: boolean; error: string }) => void): Promise<PluginListenerHandle>;

  /**
   * Remove all listeners
   */
  removeAllListeners(): Promise<void>;
}

export interface MyspotiBridgeWebPlugin {
  loadPlayer(): Promise<void>;
  play(options: { contextUri: string; trackUri?: string; trackUid?: string; index?: number; positionMs?: number }): Promise<void>;
  pause(): Promise<void>;
  resume(): Promise<void>;
  seek(options: { positionMs: number }): Promise<void>;
  next(): Promise<void>;
  previous(): Promise<void>;
  setShuffle(options: { on: boolean }): Promise<void>;
  setRepeat(options: { mode: 0 | 1 | 2 }): Promise<void>;
  setSpeed(options: { speed: number }): Promise<void>;
  getState(): Promise<PlayerState>;
  getQueue(): Promise<QueueState>;
  getAudioInfo(): Promise<AudioInfo>;
  setAudioQuality(options: { level: 'auto' | 'low' | 'normal' | 'high' | 'very_high' }): Promise<void>;
  preloadNext(): Promise<string | null>;
  addToQueue(options: { uris: string[] }): Promise<void>;
  removeFromQueue(options: { items: Array<{ uri: string; uid: string }> }): Promise<void>;
  clearQueue(): Promise<void>;
  skipTo(options: { uri: string; uid?: string }): Promise<void>;
  insertIntoQueue(options: { uris: string[]; before: { uri: string; uid?: string } }): Promise<void>;
  reorderQueue(options: { items: Array<{ uri: string; uid: string }>; before?: { uri: string; uid: string }; after?: { uri: string; uid: string } }): Promise<void>;
  mute(options: { on: boolean }): Promise<{ count: number }>;
  hush(options: { on: boolean }): Promise<{ count: number }>;
  getMediaState(): Promise<MediaState | null>;
  setMediaVolume(options: { level: number }): Promise<{ level: number }>;
  restoreMediaVolume(): Promise<{ level: number }>;
  fadeMediaVolume(options: { steps: number[]; ms: number; restore?: boolean; rise?: { ms: number; wait?: number } }): Promise<boolean>;
  rampMediaVolume(options: { to: number; ms: number }): Promise<boolean>;
  riseMediaVolume(options: { ms: number; wait: number; keep?: boolean; switch?: boolean }): Promise<boolean>;
  getVolume(): Promise<number>;
  setVolume(options: { level: number }): Promise<void>;
  checkLibrary(options: { uris: string[] }): Promise<boolean>;
  addToLibrary(options: { uris: string[] }): Promise<void>;
  removeFromLibrary(options: { uris: string[] }): Promise<void>;
  listServices(): Promise<Record<string, string[]>>;
  callService(options: { service: string; method: string; args: unknown[] }): Promise<unknown>;
  setPlaylistCover(options: { uri: string; jpegBase64: string | null }): Promise<void>;
  gqlQuery(options: { name: string; hash: string; variables: Record<string, unknown> }): Promise<unknown>;
  getToken(): Promise<{
    accessToken: string;
    expiresAtMs: number;
    clientId: string;
    appVersion: string;
    isAnonymous: boolean;
  }>;
  setAdBlock(options: { enabled: boolean }): Promise<void>;
  setBlockRules(options: { rules: string[] }): Promise<void>;
  reload(): Promise<void>;
  addListener(eventName: string, listener: (...args: unknown[]) => void): Promise<PluginListenerHandle>;
  removeAllListeners(): Promise<void>;
}