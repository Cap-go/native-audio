package ee.forgr.audio;

import static ee.forgr.audio.Constant.ASSET_ID;
import static ee.forgr.audio.Constant.ASSET_PATH;
import static ee.forgr.audio.Constant.AUDIO_CHANNEL_NUM;
import static ee.forgr.audio.Constant.ERROR_ASSET_NOT_LOADED;
import static ee.forgr.audio.Constant.ERROR_ASSET_PATH_MISSING;
import static ee.forgr.audio.Constant.ERROR_AUDIO_ASSET_MISSING;
import static ee.forgr.audio.Constant.ERROR_AUDIO_EXISTS;
import static ee.forgr.audio.Constant.ERROR_AUDIO_ID_MISSING;
import static ee.forgr.audio.Constant.LOOP;
import static ee.forgr.audio.Constant.OPT_FADE_MUSIC;
import static ee.forgr.audio.Constant.OPT_FOCUS_AUDIO;
import static ee.forgr.audio.Constant.RATE;
import static ee.forgr.audio.Constant.VOLUME;

import android.Manifest;
import android.app.Application;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;

@CapacitorPlugin(
  permissions = {
    @Permission(strings = { Manifest.permission.MODIFY_AUDIO_SETTINGS }),
    @Permission(strings = { Manifest.permission.WRITE_EXTERNAL_STORAGE }),
    @Permission(strings = { Manifest.permission.READ_PHONE_STATE }),
  }
)
public class NativeAudio
  extends Plugin
  implements AudioManager.OnAudioFocusChangeListener {

  public static final String TAG = "NativeAudio";

  private static HashMap<String, AudioAsset> audioAssetList;
  private static ArrayList<AudioAsset> resumeList;
  private boolean fadeMusic = false;
  private AudioManager audioManager;

  @Override
  public void load() {
    super.load();

    this.audioManager = (AudioManager) this.getActivity()
      .getSystemService(Context.AUDIO_SERVICE);
  }

  @Override
  public void onAudioFocusChange(int focusChange) {
    if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {} else if (
      focusChange == AudioManager.AUDIOFOCUS_GAIN
    ) {} else if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {}
  }

  @Override
  protected void handleOnPause() {
    super.handleOnPause();

    try {
      if (audioAssetList != null) {
        for (HashMap.Entry<
          String,
          AudioAsset
        > entry : audioAssetList.entrySet()) {
          AudioAsset audio = entry.getValue();

          if (audio != null) {
            boolean wasPlaying = audio.pause();

            if (wasPlaying) {
              resumeList.add(audio);
            }
          }
        }
      }
    } catch (Exception ex) {
      Log.d(
        TAG,
        "Exception caught while listening for handleOnPause: " +
        ex.getLocalizedMessage()
      );
    }
  }

  @Override
  protected void handleOnResume() {
    super.handleOnResume();

    try {
      if (resumeList != null) {
        while (!resumeList.isEmpty()) {
          AudioAsset audio = resumeList.remove(0);

          if (audio != null) {
            audio.resume();
          }
        }
      }
    } catch (Exception ex) {
      Log.d(
        TAG,
        "Exception caught while listening for handleOnResume: " +
        ex.getLocalizedMessage()
      );
    }
  }

  @PluginMethod
  public void configure(PluginCall call) {
    initSoundPool();

    if (call.hasOption(OPT_FADE_MUSIC)) this.fadeMusic = call.getBoolean(
      OPT_FADE_MUSIC
    );

    if (call.hasOption(OPT_FOCUS_AUDIO) && this.audioManager != null) {
      if (call.getBoolean(OPT_FOCUS_AUDIO)) {
        this.audioManager.requestAudioFocus(
            this,
            AudioManager.STREAM_MUSIC,
            AudioManager.AUDIOFOCUS_GAIN
          );
      } else {
        this.audioManager.abandonAudioFocus(this);
      }
    }
    call.resolve();
  }

  @PluginMethod
  public void preload(final PluginCall call) {
    new Thread(
      new Runnable() {
        @Override
        public void run() {
          preloadAsset(call);
        }
      }
    ).start();
  }

  @PluginMethod
  public void play(final PluginCall call) {
    this.getActivity()
      .runOnUiThread(
        new Runnable() {
          @Override
          public void run() {
            playOrLoop("play", call);
          }
        }
      );
  }

  @PluginMethod
  public void getCurrentTime(final PluginCall call) {
    try {
      initSoundPool();

      String audioId = call.getString(ASSET_ID);

      if (!isStringValid(audioId)) {
        call.reject(ERROR_AUDIO_ID_MISSING + " - " + audioId);
        return;
      }

      if (audioAssetList.containsKey(audioId)) {
        AudioAsset asset = audioAssetList.get(audioId);
        if (asset != null) {
          call.resolve(
            new JSObject().put("currentTime", asset.getCurrentPosition())
          );
        }
      } else {
        call.reject(ERROR_AUDIO_ASSET_MISSING + " - " + audioId);
      }
    } catch (Exception ex) {
      call.reject(ex.getMessage());
    }
  }

  @PluginMethod
  public void getDuration(final PluginCall call) {
    try {
      initSoundPool();

      String audioId = call.getString(ASSET_ID);

      if (!isStringValid(audioId)) {
        call.reject(ERROR_AUDIO_ID_MISSING + " - " + audioId);
        return;
      }

      if (audioAssetList.containsKey(audioId)) {
        AudioAsset asset = audioAssetList.get(audioId);
        if (asset != null) {
          call.resolve(new JSObject().put("duration", asset.getDuration()));
        }
      } else {
        call.reject(ERROR_AUDIO_ASSET_MISSING + " - " + audioId);
      }
    } catch (Exception ex) {
      call.reject(ex.getMessage());
    }
  }

  @PluginMethod
  public void loop(final PluginCall call) {
    this.getActivity()
      .runOnUiThread(
        new Runnable() {
          @Override
          public void run() {
            playOrLoop("loop", call);
          }
        }
      );
  }

  @PluginMethod
  public void pause(PluginCall call) {
    try {
      initSoundPool();
      String audioId = call.getString(ASSET_ID);

      if (audioAssetList.containsKey(audioId)) {
        AudioAsset asset = audioAssetList.get(audioId);
        if (asset != null) {
          boolean wasPlaying = asset.pause();

          if (wasPlaying) {
            resumeList.add(asset);
          }
          call.resolve();
        } else {
          call.reject(ERROR_ASSET_NOT_LOADED + " - " + audioId);
        }
      } else {
        call.reject(ERROR_ASSET_NOT_LOADED + " - " + audioId);
      }
    } catch (Exception ex) {
      call.reject(ex.getMessage());
    }
  }

  @PluginMethod
  public void resume(PluginCall call) {
    try {
      initSoundPool();
      String audioId = call.getString(ASSET_ID);

      if (audioAssetList.containsKey(audioId)) {
        AudioAsset asset = audioAssetList.get(audioId);
        if (asset != null) {
          asset.resume();
          resumeList.add(asset);
          call.resolve();
        } else {
          call.reject(ERROR_ASSET_NOT_LOADED + " - " + audioId);
        }
      } else {
        call.reject(ERROR_ASSET_NOT_LOADED + " - " + audioId);
      }
    } catch (Exception ex) {
      call.reject(ex.getMessage());
    }
  }

  @PluginMethod
  public void stop(PluginCall call) {
    try {
      initSoundPool();
      String audioId = call.getString(ASSET_ID);

      if (audioAssetList.containsKey(audioId)) {
        AudioAsset asset = audioAssetList.get(audioId);
        if (asset != null) {
          asset.stop();
          call.resolve();
        } else {
          call.reject(ERROR_ASSET_NOT_LOADED + " - " + audioId);
        }
      } else {
        call.reject(ERROR_ASSET_NOT_LOADED + " - " + audioId);
      }
    } catch (Exception ex) {
      call.reject(ex.getMessage());
    }
  }

  @PluginMethod
  public void unload(PluginCall call) {
    try {
      initSoundPool();
      new JSObject();
      JSObject status;

      if (isStringValid(call.getString(ASSET_ID))) {
        String audioId = call.getString(ASSET_ID);

        if (audioAssetList.containsKey(audioId)) {
          AudioAsset asset = audioAssetList.get(audioId);
          if (asset != null) {
            asset.unload();
            audioAssetList.remove(audioId);
            status = new JSObject();
            status.put("status", "OK");
            call.resolve(status);
          } else {
            status = new JSObject();
            status.put("status", false);
            call.resolve(status);
          }
        } else {
          status = new JSObject();
          status.put("status", ERROR_AUDIO_ASSET_MISSING + " - " + audioId);
          call.resolve(status);
        }
      } else {
        status = new JSObject();
        status.put("status", ERROR_AUDIO_ID_MISSING);
        call.resolve(status);
      }
    } catch (Exception ex) {
      call.reject(ex.getMessage());
    }
  }

  @PluginMethod
  public void setVolume(PluginCall call) {
    try {
      initSoundPool();

      String audioId = call.getString(ASSET_ID);
      float volume = call.getFloat(VOLUME);

      if (audioAssetList.containsKey(audioId)) {
        AudioAsset asset = audioAssetList.get(audioId);
        if (asset != null) {
          asset.setVolume(volume);
          call.resolve();
        } else {
          call.reject(ERROR_AUDIO_ASSET_MISSING);
        }
      } else {
        call.reject(ERROR_AUDIO_ASSET_MISSING);
      }
    } catch (Exception ex) {
      call.reject(ex.getMessage());
    }
  }

  @PluginMethod
  public void setRate(PluginCall call) {
    try {
      initSoundPool();

      String audioId = call.getString(ASSET_ID);
      float rate = call.getFloat(RATE);

      if (audioAssetList.containsKey(audioId)) {
        AudioAsset asset = audioAssetList.get(audioId);
        if (asset != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
          asset.setRate(rate);
        }
      } else {
        call.reject(ERROR_AUDIO_ASSET_MISSING);
      }
    } catch (Exception ex) {
      call.reject(ex.getMessage());
    }
  }

  @PluginMethod
  public void isPlaying(final PluginCall call) {
    try {
      initSoundPool();

      String audioId = call.getString(ASSET_ID);

      if (!isStringValid(audioId)) {
        call.reject(ERROR_AUDIO_ID_MISSING + " - " + audioId);
        return;
      }

      if (audioAssetList.containsKey(audioId)) {
        AudioAsset asset = audioAssetList.get(audioId);
        if (asset != null) {
          call.resolve(new JSObject().put("isPlaying", asset.isPlaying()));
        } else {
          call.reject(ERROR_AUDIO_ASSET_MISSING + " - " + audioId);
        }
      } else {
        call.reject(ERROR_AUDIO_ASSET_MISSING + " - " + audioId);
      }
    } catch (Exception ex) {
      call.reject(ex.getMessage());
    }
  }

  public void dispatchComplete(String assetId) {
    JSObject ret = new JSObject();
    ret.put("assetId", assetId);
    notifyListeners("complete", ret);
  }

  private void preloadAsset(PluginCall call) {
    double volume = 1.0;
    int audioChannelNum = 1;
    JSObject status = new JSObject();
    status.put("STATUS", "OK");

    try {
      initSoundPool();

      String audioId = call.getString(ASSET_ID);

      boolean isLocalUrl = Boolean.TRUE.equals(call.getBoolean("isUrl", false));

      if (!isStringValid(audioId)) {
        call.reject(ERROR_AUDIO_ID_MISSING + " - " + audioId);
        return;
      }

      if (!audioAssetList.containsKey(audioId)) {
        String assetPath = call.getString(ASSET_PATH);

        if (!isStringValid(assetPath)) {
          call.reject(
            ERROR_ASSET_PATH_MISSING + " - " + audioId + " - " + assetPath
          );
          return;
        }

        String fullPath = assetPath; //"raw/".concat(assetPath);

        if (call.getDouble(VOLUME) == null) {
          volume = 1.0;
        } else {
          volume = call.getDouble(VOLUME, 0.5);
        }

        if (call.getInt(AUDIO_CHANNEL_NUM) == null) {
          audioChannelNum = 1;
        } else {
          audioChannelNum = call.getInt(AUDIO_CHANNEL_NUM);
        }

        AssetFileDescriptor assetFileDescriptor;
        if (isLocalUrl) {
          File f = new File(new URI(fullPath));
          ParcelFileDescriptor p = ParcelFileDescriptor.open(
            f,
            ParcelFileDescriptor.MODE_READ_ONLY
          );
          assetFileDescriptor = new AssetFileDescriptor(p, 0, -1);
        } else {
          try {
            Uri uri = Uri.parse(fullPath); // Now Uri class should be recognized
            if (
              uri.getScheme() != null &&
              (uri.getScheme().equals("http") ||
                uri.getScheme().equals("https"))
            ) {
              // It's a remote URL
              RemoteAudioAsset remoteAudioAsset = new RemoteAudioAsset(
                this,
                audioId,
                uri,
                audioChannelNum,
                (float) volume
              );
              audioAssetList.put(audioId, remoteAudioAsset);
              call.resolve(status);
              return;
            } else {
              // It's a local file path
              // Check if fullPath starts with "public/" and prepend if necessary
              if (!fullPath.startsWith("public/")) {
                fullPath = "public/".concat(fullPath);
              }
              Context ctx = getContext().getApplicationContext(); // Use getContext() directly
              AssetManager am = ctx.getResources().getAssets();
              // Remove the redefinition of assetFileDescriptor
              assetFileDescriptor = am.openFd(fullPath);
            }
          } catch (Exception e) {
            call.reject("Error loading audio", e);
            return;
          }
        }

        AudioAsset asset = new AudioAsset(
          this,
          audioId,
          assetFileDescriptor,
          audioChannelNum,
          (float) volume
        );
        audioAssetList.put(audioId, asset);

        call.resolve(status);
      } else {
        call.reject(ERROR_AUDIO_EXISTS);
      }
    } catch (Exception ex) {
      call.reject(ex.getMessage());
    }
  }

  private void playOrLoop(String action, final PluginCall call) {
    try {
      initSoundPool();

      final String audioId = call.getString(ASSET_ID);
      final Double time = call.getDouble("time", 0.0);
      if (audioAssetList.containsKey(audioId)) {
        AudioAsset asset = audioAssetList.get(audioId);
        if (LOOP.equals(action) && asset != null) {
          asset.loop();
          call.resolve();
        } else if (asset != null) {
          asset.play(time);
          call.resolve();
        } else {
          call.reject("Error with asset");
        }
      } else {
        call.reject("Error with asset");
      }
    } catch (Exception ex) {
      call.reject(ex.getMessage());
    }
  }

  private void initSoundPool() {
    if (audioAssetList == null) {
      audioAssetList = new HashMap<>();
    }

    if (resumeList == null) {
      resumeList = new ArrayList<>();
    }
  }

  private boolean isStringValid(String value) {
    return (value != null && !value.isEmpty() && !value.equals("null"));
  }
}
