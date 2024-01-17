package ee.forgr.audio;

import android.media.MediaPlayer;
import android.net.Uri;

public class RemoteAudioAsset extends AudioAsset {

  private MediaPlayer mediaPlayer;

  public RemoteAudioAsset(
    NativeAudio owner,
    String assetId,
    Uri uri,
    int audioChannelNum,
    float volume
  ) throws Exception {
    super(owner, assetId, null, audioChannelNum, volume);
    mediaPlayer = new MediaPlayer();
    mediaPlayer.setDataSource(owner.getContext(), uri);
    mediaPlayer.setVolume(volume, volume);
    mediaPlayer.prepareAsync(); // Prepare asynchronously to not block the main thread
  }

  @Override
  public void play(Double time) throws Exception {
    if (mediaPlayer != null) {
      if (time != null) {
        mediaPlayer.seekTo((int) (time * 1000));
      }
      mediaPlayer.start();
    } else {
      throw new Exception("MediaPlayer is null");
    }
  }

  @Override
  public boolean pause() throws Exception {
    if (mediaPlayer != null && mediaPlayer.isPlaying()) {
      mediaPlayer.pause();
      return true; // Return true to indicate that the audio was paused
    }
    return false;
  }

  @Override
  public void resume() throws Exception {
    if (mediaPlayer != null && !mediaPlayer.isPlaying()) {
      mediaPlayer.start();
    }
  }

  @Override
  public void stop() throws Exception {
    if (mediaPlayer != null) {
      mediaPlayer.stop();
      mediaPlayer.prepare(); // Call prepare to reset the MediaPlayer to the Idle state
    }
  }

  @Override
  public void loop() throws Exception {
    if (mediaPlayer != null) {
      mediaPlayer.setLooping(true);
      mediaPlayer.start();
    }
  }

  @Override
  public void unload() throws Exception {
    if (mediaPlayer != null) {
      mediaPlayer.release();
      mediaPlayer = null;
    }
  }

  @Override
  public void setVolume(float volume) throws Exception {
    if (mediaPlayer != null) {
      mediaPlayer.setVolume(volume, volume);
    }
  }

  @Override
  public boolean isPlaying() throws Exception {
    return mediaPlayer != null && mediaPlayer.isPlaying();
  }
}
