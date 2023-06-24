import type { PluginListenerHandle } from "@capacitor/core";

export interface CompletedEvent {
  /**
   * Emit when a play completes
   *
   * @since  5.0.0
   */
  assetId: string;
}
export type CompletedListener = (state: CompletedEvent) => void;

export interface NativeAudio {
  configure(options: ConfigureOptions): Promise<void>;
  preload(options: PreloadOptions): Promise<void>;
  play(options: { assetId: string; time?: number; delay?: number; }): Promise<void>;
  pause(options: { assetId: string; }): Promise<void>;
  resume(options: { assetId: string; }): Promise<void>;
  loop(options: { assetId: string; }): Promise<void>;
  stop(options: { assetId: string; }): Promise<void>;
  unload(options: { assetId: string; }): Promise<void>;
  setVolume(options: { assetId: string; volume: number; }): Promise<void>;
  setRate(options: { assetId: string; rate: number; }): Promise<void>;
  getCurrentTime(options: {
    assetId: string;
  }): Promise<{ currentTime: number; }>;
  getDuration(options: { assetId: string; }): Promise<{ duration: number; }>;
  isPlaying(options: { assetId: string; }): Promise<{ isPlaying: boolean; }>;
  /**
   * Listen for complete event
   *
   * @since 5.0.0
   */
  addListener(
    eventName: "complete",
    listenerFunc: CompletedListener
  ): Promise<PluginListenerHandle> & PluginListenerHandle;
}

export interface ConfigureOptions {
  fade?: boolean;
  focus?: boolean;
  background?: boolean;
  ignoreSilent?: boolean;
}

export interface PreloadOptions {
  assetPath: string;
  assetId: string;
  volume?: number;
  audioChannelNum?: number;
  isUrl?: boolean;
}
