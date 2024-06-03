package ee.forgr.audio;

import android.content.res.AssetFileDescriptor;
import android.os.Build;
import androidx.annotation.RequiresApi;
import java.util.ArrayList;

public class AudioAsset {

  private final String TAG = "AudioAsset";

  private final ArrayList<AudioDispatcher> audioList;
  private int playIndex = 0;
  private final String assetId;
  private final NativeAudio owner;

  AudioAsset(
    NativeAudio owner,
    String assetId,
    AssetFileDescriptor assetFileDescriptor,
    int audioChannelNum,
    float volume
  ) throws Exception {
    audioList = new ArrayList<>();
    this.owner = owner;
    this.assetId = assetId;

    if (audioChannelNum < 0) {
      audioChannelNum = 1;
    }

    for (int x = 0; x < audioChannelNum; x++) {
      AudioDispatcher audioDispatcher = new AudioDispatcher(
        assetFileDescriptor,
        volume
      );
      audioList.add(audioDispatcher);
      if (audioChannelNum == 1) audioDispatcher.setOwner(this);
    }
  }

  public void dispatchComplete() {
    this.owner.dispatchComplete(this.assetId);
  }

  public void play(Double time) throws Exception {
    AudioDispatcher audio = audioList.get(playIndex);

    if (audio != null) {
      audio.play(time);
      playIndex++;
      playIndex = playIndex % audioList.size();
    } else {
      throw new Exception("AudioDispatcher is null");
    }
  }

  public double getDuration() {
    if (audioList.size() != 1) return 0;

    AudioDispatcher audio = audioList.get(playIndex);

    if (audio != null) {
      return audio.getDuration();
    }
    return 0;
  }

  public double getCurrentPosition() {
    if (audioList.size() != 1) return 0;

    AudioDispatcher audio = audioList.get(playIndex);

    if (audio != null) {
      return audio.getCurrentPosition();
    }
    return 0;
  }

  public boolean pause() throws Exception {
    boolean wasPlaying = false;

    for (int x = 0; x < audioList.size(); x++) {
      AudioDispatcher audio = audioList.get(x);
      wasPlaying |= audio.pause();
    }

    return wasPlaying;
  }

  public void resume() throws Exception {
    if (!audioList.isEmpty()) {
      AudioDispatcher audio = audioList.get(0);

      if (audio != null) {
        audio.resume();
      } else {
        throw new Exception("AudioDispatcher is null");
      }
    }
  }

  public void stop() throws Exception {
    for (int x = 0; x < audioList.size(); x++) {
      AudioDispatcher audio = audioList.get(x);

      if (audio != null) {
        audio.stop();
      } else {
        throw new Exception("AudioDispatcher is null");
      }
    }
  }

  public void loop() throws Exception {
    AudioDispatcher audio = audioList.get(playIndex);

    if (audio != null) {
      audio.loop();
      playIndex++;
      playIndex = playIndex % audioList.size();
    } else {
      throw new Exception("AudioDispatcher is null");
    }
  }

  public void unload() throws Exception {
    this.stop();

    for (int x = 0; x < audioList.size(); x++) {
      AudioDispatcher audio = audioList.get(x);

      if (audio != null) {
        audio.unload();
      } else {
        throw new Exception("AudioDispatcher is null");
      }
    }

    audioList.clear();
  }

  public void setVolume(float volume) throws Exception {
    for (int x = 0; x < audioList.size(); x++) {
      AudioDispatcher audio = audioList.get(x);

      if (audio != null) {
        audio.setVolume(volume);
      } else {
        throw new Exception("AudioDispatcher is null");
      }
    }
  }

  @RequiresApi(api = Build.VERSION_CODES.M)
  public void setRate(float rate) throws Exception {
    for (int x = 0; x < audioList.size(); x++) {
      AudioDispatcher audio = audioList.get(x);
      if (audio != null) {
        audio.setRate(rate);
      }
    }
  }

  public boolean isPlaying() throws Exception {
    if (audioList.size() != 1) return false;

    return audioList.get(playIndex).isPlaying();
  }
}
