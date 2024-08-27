package ee.forgr.audio;

import android.media.MediaPlayer;
import android.net.Uri;
import android.util.Log;
import java.util.ArrayList;

public class RemoteAudioAsset extends AudioAsset {

  private static final String TAG = "RemoteAudioAsset";
  private final ArrayList<MediaPlayer> mediaPlayers;
  private int playIndex = 0;
  private final Uri uri;
  private float volume;
  private boolean isPrepared = false;

  public RemoteAudioAsset(
    NativeAudio owner,
    String assetId,
    Uri uri,
    int audioChannelNum,
    float volume
  ) throws Exception {
    super(owner, assetId, null, 0, volume);
    this.uri = uri;
    this.volume = volume;
    this.mediaPlayers = new ArrayList<>();

    if (audioChannelNum < 1) {
      audioChannelNum = 1;
    }

    for (int i = 0; i < audioChannelNum; i++) {
      MediaPlayer mediaPlayer = new MediaPlayer();
      mediaPlayers.add(mediaPlayer);
      initializeMediaPlayer(mediaPlayer);
    }
  }

  private void initializeMediaPlayer(MediaPlayer mediaPlayer) {
    try {
      mediaPlayer.setDataSource(owner.getContext(), uri);
      mediaPlayer.setVolume(volume, volume);
      mediaPlayer.setOnPreparedListener(mp -> {
        isPrepared = true;
        Log.d(TAG, "MediaPlayer prepared for " + uri.toString());
      });
      mediaPlayer.setOnErrorListener((mp, what, extra) -> {
        Log.e(TAG, "MediaPlayer error: " + what + ", " + extra);
        return false;
      });
      mediaPlayer.prepareAsync();
    } catch (Exception e) {
      Log.e(TAG, "Error initializing MediaPlayer", e);
    }
  }

  @Override
  public void play(Double time) throws Exception {
    if (mediaPlayers.isEmpty()) {
      throw new Exception("No MediaPlayer available");
    }

    MediaPlayer mediaPlayer = mediaPlayers.get(playIndex);
    if (!isPrepared) {
      Log.d(TAG, "MediaPlayer not yet prepared, waiting...");
      mediaPlayer.setOnPreparedListener(mp -> {
        isPrepared = true;
        try {
          playInternal(mediaPlayer, time);
        } catch (Exception e) {
          Log.e(TAG, "Error playing after prepare", e);
        }
      });
    } else {
      playInternal(mediaPlayer, time);
    }

    playIndex = (playIndex + 1) % mediaPlayers.size();
  }

  private void playInternal(MediaPlayer mediaPlayer, Double time)
    throws Exception {
    if (time != null) {
      mediaPlayer.seekTo((int) (time * 1000));
    }
    mediaPlayer.start();
  }

  @Override
  public boolean pause() throws Exception {
    boolean wasPlaying = false;
    for (MediaPlayer mediaPlayer : mediaPlayers) {
      if (mediaPlayer.isPlaying()) {
        mediaPlayer.pause();
        wasPlaying = true;
      }
    }
    return wasPlaying;
  }

  @Override
  public void resume() throws Exception {
    for (MediaPlayer mediaPlayer : mediaPlayers) {
      if (!mediaPlayer.isPlaying()) {
        mediaPlayer.start();
      }
    }
  }

  @Override
  public void stop() throws Exception {
    for (MediaPlayer mediaPlayer : mediaPlayers) {
      if (mediaPlayer.isPlaying()) {
        mediaPlayer.stop();
      }
      // Reset the MediaPlayer to make it ready for future playback
      mediaPlayer.reset();
      initializeMediaPlayer(mediaPlayer);
    }
    isPrepared = false;
  }

  @Override
  public void loop() throws Exception {
    if (!mediaPlayers.isEmpty()) {
      MediaPlayer mediaPlayer = mediaPlayers.get(playIndex);
      mediaPlayer.setLooping(true);
      mediaPlayer.start();
      playIndex = (playIndex + 1) % mediaPlayers.size();
    }
  }

  @Override
  public void unload() throws Exception {
    for (MediaPlayer mediaPlayer : mediaPlayers) {
      mediaPlayer.release();
    }
    mediaPlayers.clear();
  }

  @Override
  public void setVolume(float volume) throws Exception {
    this.volume = volume;
    for (MediaPlayer mediaPlayer : mediaPlayers) {
      mediaPlayer.setVolume(volume, volume);
    }
  }

  @Override
  public boolean isPlaying() throws Exception {
    for (MediaPlayer mediaPlayer : mediaPlayers) {
      if (mediaPlayer.isPlaying()) {
        return true;
      }
    }
    return false;
  }

  @Override
  public double getDuration() {
    if (!mediaPlayers.isEmpty() && isPrepared) {
      return mediaPlayers.get(0).getDuration() / 1000.0;
    }
    return 0;
  }

  @Override
  public double getCurrentPosition() {
    if (!mediaPlayers.isEmpty() && isPrepared) {
      return mediaPlayers.get(0).getCurrentPosition() / 1000.0;
    }
    return 0;
  }
}
