/// <reference types="vite/client" />
/// <reference types="@capacitor/core" />
/// <reference types="vue/macros-global" />

declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<{}, {}, any>
  export default component
}

declare global {
  interface Window {
    Spotify: any;
    Android: {
      sendBroadcast: (data: string) => void;
    };
  }
  
  namespace Spotify {
    class Player {
      constructor(options: {
        name: string;
        getOAuthToken: (cb: (token: string) => void) => void;
        volume?: number;
        enableMediaSession?: boolean;
      });
      connect(): Promise<boolean>;
      disconnect(): void;
      addListener(event: string, callback: (...args: any[]) => void): void;
      removeListener(event: string, callback: (...args: any[]) => void): void;
      getCurrentState(): Promise<WebPlaybackState | null>;
      setName(name: string): Promise<void>;
      getVolume(): Promise<number>;
      setVolume(volume: number): Promise<void>;
      pause(): Promise<void>;
      resume(): Promise<void>;
      togglePlay(): Promise<void>;
      seek(position_ms: number): Promise<void>;
      previousTrack(): Promise<void>;
      nextTrack(): Promise<void>;
      addToQueue(uri: string): Promise<void>;
      enableMediaSession(): Promise<void>;
    }
    const Ready: unique symbol;
    const NotReady: unique symbol;
  }
  
  interface WebPlaybackTrack {
    id: string; uri: string; type: 'track' | 'episode' | 'ad';
    name: string; artist_name: string; album_name: string;
    duration_ms: number; is_paused: boolean; is_playing: boolean;
    is_saved: boolean; linked_track_uri: string | null; linked_from_uri: string | null;
    images: Array<{ url: string; width: number; height: number }>;
    album_uri: string; artist_uris: string[]; playable: boolean;
    disc_number: number; track_number: number; explicit: boolean;
    preview_url: string | null; external_urls: { spotify: string };
  }
  
  interface WebPlaybackState {
    context: { uri: string; metadata: { current_page: number; page_size: number; identifiers: string[] } };
    disallows: { pausing: boolean; peeking_next: boolean; peeking_prev: boolean; resuming: boolean; seeking: boolean; skipping_next: boolean; skipping_prev: boolean };
    paused: boolean; position: number; duration: number;
    track_window: { current_track: WebPlaybackTrack; next_tracks: WebPlaybackTrack[]; prev_tracks: WebPlaybackTrack[] };
    repeat_mode: number; shuffle: boolean; timestamp: number;
    restrictions: { disallow_pausing: boolean; disallow_peeking_next: boolean; disallow_peeking_prev: boolean; disallow_resuming: boolean; disallow_seeking: boolean; disallow_skipping_next: boolean; disallow_skipping_prev: boolean };
  }
}