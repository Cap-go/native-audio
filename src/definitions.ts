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
export interface Assets {
  assetId: string;
}
export interface AssetVolume {
  assetId: string;
  volume: number;
}

export interface AssetRate {
  assetId: string;
  rate: number;
}

export interface AssetPlayOptions {
  assetId: string;
  time?: number;
  delay?: number;
}

export interface ConfigureOptions {
  fade?: boolean;
  focus?: boolean;
  background?: boolean;
}

export interface PreloadOptions {
  assetPath: string;
  assetId: string;
  volume?: number;
  audioChannelNum?: number;
  isUrl?: boolean;
}

export interface NativeAudio {
  /**
   * Configure the audio player
   * @since 5.0.0
   * @param ConfigureOptions
   * @returns
   */
  configure(options: ConfigureOptions): Promise<void>;
  /**
   * Load an audio file
   * @since 5.0.0
   * @param PreloadOptions
   * @returns
   */
  preload(options: PreloadOptions): Promise<void>;
  /**
   * Play an audio file
   * @since 5.0.0
   * @param AssetPlayOptions
   * @returns
   */
  play(options: {
    assetId: string;
    time?: number;
    delay?: number;
  }): Promise<void>;
  /**
   * Pause an audio file
   * @since 5.0.0
   * @param Assets
   * @returns
   */
  pause(options: { assetId: string }): Promise<void>;
  /**
   * Resume an audio file
   * @since 5.0.0
   * @param Assets
   * @returns
   */
  resume(options: { assetId: string }): Promise<void>;
  /**
   * Stop an audio file
   * @since 5.0.0
   * @param Assets
   * @returns
   */
  loop(options: { assetId: string }): Promise<void>;
  /**
   * Stop an audio file
   * @since 5.0.0
   * @param Assets
   * @returns
   */
  stop(options: { assetId: string }): Promise<void>;
  /**
   * Unload an audio file
   * @since 5.0.0
   * @param Assets
   * @returns
   */
  unload(options: { assetId: string }): Promise<void>;
  /**
   * Set the volume of an audio file
   * @since 5.0.0
   * @param AssetVolume
   * @returns {Promise<void>}
   */
  setVolume(options: { assetId: string; volume: number }): Promise<void>;
  /**
   * Set the rate of an audio file
   * @since 5.0.0
   * @param AssetPlayOptions
   * @returns {Promise<void>}
   */
  setRate(options: { assetId: string; rate: number }): Promise<void>;
  /**
   * Set the current time of an audio file
   * @since 5.0.0
   * @param AssetPlayOptions
   * @returns {Promise<{ currentTime: number }>}
   */
  getCurrentTime(options: {
    assetId: string;
  }): Promise<{ currentTime: number }>;
  /**
   * Get the duration of an audio file
   * @since 5.0.0
   * @param AssetPlayOptions
   * @returns {Promise<{ duration: number }>}
   */
  getDuration(options: { assetId: string }): Promise<{ duration: number }>;
  /**
   * Check if an audio file is playing
   *
   * @since 5.0.0
   * @param AssetPlayOptions
   * @returns
   */
  isPlaying(options: { assetId: string }): Promise<{ isPlaying: boolean }>;
  /**
   * Listen for complete event
   *
   * @since 5.0.0
   * return CompletedEvent
   */
  addListener(
    eventName: "complete",
    listenerFunc: CompletedListener,
  ): Promise<PluginListenerHandle>;
}
