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
  /**
   * Asset Id, unique identifier of the file
   */
  assetId: string;
}
export interface AssetVolume {
  /**
   * Asset Id, unique identifier of the file
   */
  assetId: string;
  /**
   * Volume of the audio, between 0.1 and 1.0
   */
  volume: number;
}

export interface AssetRate {
  /**
   * Asset Id, unique identifier of the file
   */
  assetId: string;
  /**
   * Rate of the audio, between 0.1 and 1.0
   */
  rate: number;
}

export interface AssetPlayOptions {
  /**
   * Asset Id, unique identifier of the file
   */
  assetId: string;
  /**
   * Time to start playing the audio, in milliseconds
   */
  time?: number;
  /**
   * Delay to start playing the audio, in milliseconds
   */
  delay?: number;
}

export interface ConfigureOptions {
  /**
   * Play the audio with Fade effect, only available for IOS
   */
  fade?: boolean;
  /**
   * focus the audio with Audio Focus
   */
  focus?: boolean;
  /**
   * Play the audio in the background
   */
  background?: boolean;
}

export interface PreloadOptions {
  /**
   * Path to the audio file, relative path of the file, absolute url (file://) or remote url (https://)
   */
  assetPath: string;
  /**
   * Asset Id, unique identifier of the file
   */
  assetId: string;
  /**
   * Volume of the audio, between 0.1 and 1.0
   */
  volume?: number;
  /**
   * Audio channel number, default is 1
   */
  audioChannelNum?: number;
  /**
   * Is the audio file a URL, pass true if assetPath is a `file://` url
   */
  isUrl?: boolean;
}

export interface NativeAudio {
  /**
   * Configure the audio player
   * @since 5.0.0
   * @param option {@link ConfigureOptions}
   * @returns
   */
  configure(options: ConfigureOptions): Promise<void>;
  /**
   * Load an audio file
   * @since 5.0.0
   * @param option {@link PreloadOptions}
   * @returns
   */
  preload(options: PreloadOptions): Promise<void>;
  /**
   * Check if an audio file is preloaded
   *
   * @since 6.1.0
   * @param option {@link Assets}
   * @returns {Promise<boolean>}
   */
  isPreloaded(options: PreloadOptions): Promise<{ found: boolean }>;
  /**
   * Play an audio file
   * @since 5.0.0
   * @param option {@link PlayOptions}
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
   * @param option {@link Assets}
   * @returns
   */
  pause(options: Assets): Promise<void>;
  /**
   * Resume an audio file
   * @since 5.0.0
   * @param option {@link Assets}
   * @returns
   */
  resume(options: Assets): Promise<void>;
  /**
   * Stop an audio file
   * @since 5.0.0
   * @param option {@link Assets}
   * @returns
   */
  loop(options: Assets): Promise<void>;
  /**
   * Stop an audio file
   * @since 5.0.0
   * @param option {@link Assets}
   * @returns
   */
  stop(options: Assets): Promise<void>;
  /**
   * Unload an audio file
   * @since 5.0.0
   * @param option {@link Assets}
   * @returns
   */
  unload(options: Assets): Promise<void>;
  /**
   * Set the volume of an audio file
   * @since 5.0.0
   * @param option {@link AssetVolume}
   * @returns {Promise<void>}
   */
  setVolume(options: { assetId: string; volume: number }): Promise<void>;
  /**
   * Set the rate of an audio file
   * @since 5.0.0
   * @param option {@link AssetPlayOptions}
   * @returns {Promise<void>}
   */
  setRate(options: { assetId: string; rate: number }): Promise<void>;
  /**
   * Set the current time of an audio file
   * @since 5.0.0
   * @param option {@link AssetPlayOptions}
   * @returns {Promise<{ currentTime: number }>}
   */
  getCurrentTime(options: {
    assetId: string;
  }): Promise<{ currentTime: number }>;
  /**
   * Get the duration of an audio file
   * @since 5.0.0
   * @param option {@link AssetPlayOptions}
   * @returns {Promise<{ duration: number }>}
   */
  getDuration(options: Assets): Promise<{ duration: number }>;
  /**
   * Check if an audio file is playing
   *
   * @since 5.0.0
   * @param option {@link AssetPlayOptions}
   * @returns {Promise<boolean>}
   */
  isPlaying(options: Assets): Promise<{ isPlaying: boolean }>;
  /**
   * Listen for complete event
   *
   * @since 5.0.0
   * return {@link CompletedEvent}
   */
  addListener(
    eventName: "complete",
    listenerFunc: CompletedListener,
  ): Promise<PluginListenerHandle>;
}
