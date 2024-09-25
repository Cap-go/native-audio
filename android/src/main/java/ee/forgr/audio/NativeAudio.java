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
import static ee.forgr.audio.Constant.OPT_FOCUS_AUDIO;
import static ee.forgr.audio.Constant.RATE;
import static ee.forgr.audio.Constant.VOLUME;

import android.Manifest;
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
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
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
  private AudioManager audioManager;

  @Override
  public void load() {
    super.load();

    this.audioManager = (AudioManager) this.getActivity()
      .getSystemService(Context.AUDIO_SERVICE);

    audioAssetList = new HashMap<>();
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

    if (this.audioManager == null) {
      call.resolve();
      return;
    }

    if (Boolean.TRUE.equals(call.getBoolean(OPT_FOCUS_AUDIO, false))) {
      this.audioManager.requestAudioFocus(
          this,
          AudioManager.STREAM_MUSIC,
          AudioManager.AUDIOFOCUS_GAIN
        );
    } else {
      this.audioManager.abandonAudioFocus(this);
    }
    call.resolve();
  }

  @PluginMethod
  public void isPreloaded(final PluginCall call) {
    new Thread(
      new Runnable() {
        @Override
        public void run() {
          initSoundPool();

          String audioId = call.getString(ASSET_ID);

          if (!isStringValid(audioId)) {
            call.reject(ERROR_AUDIO_ID_MISSING + " - " + audioId);
            return;
          }
          call.resolve(
            new JSObject().put("found", audioAssetList.containsKey(audioId))
          );
        }
      }
    ).start();
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
            call.resolve();
          } else {
            call.reject(ERROR_AUDIO_ASSET_MISSING + " - " + audioId);
          }
        } else {
          call.reject(ERROR_AUDIO_ASSET_MISSING + " - " + audioId);
        }
      } else {
        call.reject(ERROR_AUDIO_ID_MISSING);
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
      float volume = call.getFloat(VOLUME, 1F);

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
      float rate = call.getFloat(RATE, 1F);

      if (audioAssetList.containsKey(audioId)) {
        AudioAsset asset = audioAssetList.get(audioId);
        if (asset != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
          asset.setRate(rate);
        }
        call.resolve();
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
    float volume = 1F;
    int audioChannelNum = 1;
    JSObject status = new JSObject();
    status.put("STATUS", "OK");

    try {
      initSoundPool();

      String audioId = call.getString(ASSET_ID);
      if (!isStringValid(audioId)) {
        call.reject(ERROR_AUDIO_ID_MISSING + " - " + audioId);
        return;
      }

      String assetPath = call.getString(ASSET_PATH);
      if (!isStringValid(assetPath)) {
        call.reject(
          ERROR_ASSET_PATH_MISSING + " - " + audioId + " - " + assetPath
        );
        return;
      }

      boolean isLocalUrl = call.getBoolean("isUrl", false);
      boolean isComplex = call.getBoolean("isComplex", false);

      Log.d(
        "AudioPlugin",
        "Debug: audioId = " +
        audioId +
        ", assetPath = " +
        assetPath +
        ", isLocalUrl = " +
        isLocalUrl
      );

      if (audioAssetList.containsKey(audioId)) {
        call.reject(ERROR_AUDIO_EXISTS + " - " + audioId);
        return;
      }

      if (isComplex) {
        volume = call.getFloat(VOLUME, 1F);
        audioChannelNum = call.getInt(AUDIO_CHANNEL_NUM, 1);
      }

      if (isLocalUrl) {
        // Handle URL (both remote and local file URLs)
        Log.d("AudioPlugin", "Debug: Handling URL");
        try {
          Uri uri = Uri.parse(assetPath);
          if (
            uri.getScheme() != null &&
            (uri.getScheme().equals("http") || uri.getScheme().equals("https"))
          ) {
            // Remote URL
            Log.d(
              "AudioPlugin",
              "Debug: Remote URL detected: " + uri.toString()
            );
            RemoteAudioAsset remoteAudioAsset = new RemoteAudioAsset(
              this,
              audioId,
              uri,
              audioChannelNum,
              volume
            );
            remoteAudioAsset.setCompletionListener(this::dispatchComplete);
            audioAssetList.put(audioId, remoteAudioAsset);
          } else if (
            uri.getScheme() != null && uri.getScheme().equals("file")
          ) {
            // Local file URL
            Log.d("AudioPlugin", "Debug: Local file URL detected");
            File file = new File(uri.getPath());
            if (!file.exists()) {
              Log.e(
                "AudioPlugin",
                "Error: File does not exist - " + file.getAbsolutePath()
              );
              call.reject(ERROR_ASSET_PATH_MISSING + " - " + assetPath);
              return;
            }
            ParcelFileDescriptor pfd = ParcelFileDescriptor.open(
              file,
              ParcelFileDescriptor.MODE_READ_ONLY
            );
            AssetFileDescriptor afd = new AssetFileDescriptor(
              pfd,
              0,
              AssetFileDescriptor.UNKNOWN_LENGTH
            );
            AudioAsset asset = new AudioAsset(
              this,
              audioId,
              afd,
              audioChannelNum,
              volume
            );
            asset.setCompletionListener(this::dispatchComplete);
            audioAssetList.put(audioId, asset);
          } else {
            throw new IllegalArgumentException(
              "Invalid URL scheme: " + uri.getScheme()
            );
          }
          call.resolve(status);
        } catch (Exception e) {
          Log.e("AudioPlugin", "Error handling URL", e);
          call.reject("Error handling URL: " + e.getMessage());
        }
      } else {
        // Handle asset in public folder
        Log.d("AudioPlugin", "Debug: Handling asset in public folder");
        if (!assetPath.startsWith("public/")) {
          assetPath = "public/" + assetPath;
        }
        try {
          Context ctx = getContext().getApplicationContext();
          AssetManager am = ctx.getResources().getAssets();
          AssetFileDescriptor assetFileDescriptor = am.openFd(assetPath);
          AudioAsset asset = new AudioAsset(
            this,
            audioId,
            assetFileDescriptor,
            audioChannelNum,
            volume
          );
          audioAssetList.put(audioId, asset);
          call.resolve(status);
        } catch (IOException e) {
          Log.e("AudioPlugin", "Error opening asset: " + assetPath, e);
          call.reject(ERROR_ASSET_PATH_MISSING + " - " + assetPath);
        }
      }
    } catch (Exception ex) {
      Log.e("AudioPlugin", "Error in preloadAsset", ex);
      call.reject("Error in preloadAsset: " + ex.getMessage());
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
