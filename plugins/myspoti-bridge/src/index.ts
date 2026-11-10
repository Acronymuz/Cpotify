import { registerPlugin, WebPlugin } from '@capacitor/core';
import type { MyspotiBridgePlugin, MyspotiBridgeWebPlugin, PlayerState, QueueState, Track, AudioInfo, MediaState } from './definitions';

class MyspotiBridgeWeb extends WebPlugin implements MyspotiBridgeWebPlugin {
  async loadPlayer(): Promise<void> {
    console.warn('MyspotiBridge: loadPlayer not implemented on web');
  }

  async play(): Promise<void> {
    console.warn('MyspotiBridge: play not implemented on web');
  }

  async pause(): Promise<void> {
    console.warn('MyspotiBridge: pause not implemented on web');
  }

  async resume(): Promise<void> {
    console.warn('MyspotiBridge: resume not implemented on web');
  }

  async seek(): Promise<void> {
    console.warn('MyspotiBridge: seek not implemented on web');
  }

  async next(): Promise<void> {
    console.warn('MyspotiBridge: next not implemented on web');
  }

  async previous(): Promise<void> {
    console.warn('MyspotiBridge: previous not implemented on web');
  }

  async setShuffle(): Promise<void> {
    console.warn('MyspotiBridge: setShuffle not implemented on web');
  }

  async setRepeat(): Promise<void> {
    console.warn('MyspotiBridge: setRepeat not implemented on web');
  }

  async setSpeed(): Promise<void> {
    console.warn('MyspotiBridge: setSpeed not implemented on web');
  }

  async getState(): Promise<PlayerState> {
    console.warn('MyspotiBridge: getState not implemented on web');
    return this.getEmptyState();
  }

  async getQueue(): Promise<QueueState> {
    console.warn('MyspotiBridge: getQueue not implemented on web');
    return { current: null, queued: [], nextUp: [] };
  }

  async getAudioInfo(): Promise<AudioInfo> {
    console.warn('MyspotiBridge: getAudioInfo not implemented on web');
    return { bitrate: 0, mime: '', level: '' };
  }

  async setAudioQuality(): Promise<void> {
    console.warn('MyspotiBridge: setAudioQuality not implemented on web');
  }

  async preloadNext(): Promise<string | null> {
    console.warn('MyspotiBridge: preloadNext not implemented on web');
    return null;
  }

  async addToQueue(): Promise<void> {
    console.warn('MyspotiBridge: addToQueue not implemented on web');
  }

  async removeFromQueue(): Promise<void> {
    console.warn('MyspotiBridge: removeFromQueue not implemented on web');
  }

  async clearQueue(): Promise<void> {
    console.warn('MyspotiBridge: clearQueue not implemented on web');
  }

  async skipTo(): Promise<void> {
    console.warn('MyspotiBridge: skipTo not implemented on web');
  }

  async insertIntoQueue(): Promise<void> {
    console.warn('MyspotiBridge: insertIntoQueue not implemented on web');
  }

  async reorderQueue(): Promise<void> {
    console.warn('MyspotiBridge: reorderQueue not implemented on web');
  }

  async mute(): Promise<{ count: number }> {
    console.warn('MyspotiBridge: mute not implemented on web');
    return { count: 0 };
  }

  async hush(): Promise<{ count: number }> {
    console.warn('MyspotiBridge: hush not implemented on web');
    return { count: 0 };
  }

  async getMediaState(): Promise<MediaState | null> {
    console.warn('MyspotiBridge: getMediaState not implemented on web');
    return null;
  }

  async setMediaVolume(): Promise<{ level: number }> {
    console.warn('MyspotiBridge: setMediaVolume not implemented on web');
    return { level: 0 };
  }

  async restoreMediaVolume(): Promise<{ level: number }> {
    console.warn('MyspotiBridge: restoreMediaVolume not implemented on web');
    return { level: 0 };
  }

  async fadeMediaVolume(): Promise<boolean> {
    console.warn('MyspotiBridge: fadeMediaVolume not implemented on web');
    return false;
  }

  async rampMediaVolume(): Promise<boolean> {
    console.warn('MyspotiBridge: rampMediaVolume not implemented on web');
    return false;
  }

  async riseMediaVolume(): Promise<boolean> {
    console.warn('MyspotiBridge: riseMediaVolume not implemented on web');
    return false;
  }

  async getVolume(): Promise<number> {
    console.warn('MyspotiBridge: getVolume not implemented on web');
    return 0;
  }

  async setVolume(): Promise<void> {
    console.warn('MyspotiBridge: setVolume not implemented on web');
  }

  async checkLibrary(): Promise<boolean> {
    console.warn('MyspotiBridge: checkLibrary not implemented on web');
    return false;
  }

  async addToLibrary(): Promise<void> {
    console.warn('MyspotiBridge: addToLibrary not implemented on web');
  }

  async removeFromLibrary(): Promise<void> {
    console.warn('MyspotiBridge: removeFromLibrary not implemented on web');
  }

  async listServices(): Promise<Record<string, string[]>> {
    console.warn('MyspotiBridge: listServices not implemented on web');
    return {};
  }

  async callService(): Promise<unknown> {
    console.warn('MyspotiBridge: callService not implemented on web');
    return null;
  }

  async setPlaylistCover(): Promise<void> {
    console.warn('MyspotiBridge: setPlaylistCover not implemented on web');
  }

  async gqlQuery(): Promise<unknown> {
    console.warn('MyspotiBridge: gqlQuery not implemented on web');
    return null;
  }

  async getToken(): Promise<{
    accessToken: string;
    expiresAtMs: number;
    clientId: string;
    appVersion: string;
    isAnonymous: boolean;
  }> {
    console.warn('MyspotiBridge: getToken not implemented on web');
    return { accessToken: '', expiresAtMs: 0, clientId: '', appVersion: '', isAnonymous: false };
  }

  async setAdBlock(): Promise<void> {
    console.warn('MyspotiBridge: setAdBlock not implemented on web');
  }

  async setBlockRules(): Promise<void> {
    console.warn('MyspotiBridge: setBlockRules not implemented on web');
  }

  async reload(): Promise<void> {
    console.warn('MyspotiBridge: reload not implemented on web');
  }

  private getEmptyState(): PlayerState {
    return {
      track: null,
      contextUri: null,
      contextName: null,
      previous: null,
      next: null,
      isPaused: true,
      isBuffering: false,
      positionMs: 0,
      timestamp: 0,
      durationMs: 0,
      shuffle: false,
      repeat: 0,
      speed: 1,
      restrictions: {},
      playbackId: null,
    };
  }
}

const MyspotiBridge = registerPlugin<MyspotiBridgePlugin>('MyspotiBridge', {
  web: () => new MyspotiBridgeWeb(),
});

export { MyspotiBridge };
export type { MyspotiBridgePlugin, PlayerState, QueueState, Track, AudioInfo, MediaState };