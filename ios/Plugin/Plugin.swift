import AVFoundation
import Capacitor
import CoreAudio
import Foundation
import Foundation

enum MyError: Error {
  case runtimeError(String)
}

/// Please read the Capacitor iOS Plugin Development Guide
/// here: https://capacitor.ionicframework.com/docs/plugins/ios
@objc(NativeAudio)
public class NativeAudio: CAPPlugin {

  var audioList: [String: Any] = [:]
  var fadeMusic = false
  var session = AVAudioSession.sharedInstance()

  override public func load() {
    super.load()

    self.fadeMusic = false

    do {
      try self.session.setCategory(AVAudioSession.Category.playback, options: .mixWithOthers)
      try self.session.setActive(false)
    } catch {
      print("Failed to set session category")
    }
  }

  @objc func configure(_ call: CAPPluginCall) {
    if let fade = call.getBool(Constant.FadeKey) {
      self.fadeMusic = fade
    }

    if let focus = call.getBool(Constant.FocusAudio) {

      do {

        if focus {

          try self.session.setCategory(AVAudioSession.Category.playback)

        }

      } catch {

        print("Failed to set setCategory audio")

      }

    }

    if let background = call.getBool(Constant.Background) {

      do {

        if background {

          try self.session.setActive(true)

        }

      } catch {

        print("Failed to set setSession true")

      }

    }

    if let ignoreSilent = call.getBool(Constant.IgnoreSilent) {

      do {

        if ignoreSilent == false {

          if let focus = call.getBool(Constant.FocusAudio) {

            do {

              if focus {

                try self.session.setCategory(AVAudioSession.Category.ambient)

              } else {

                try self.session.setCategory(
                  AVAudioSession.Category.ambient, options: .mixWithOthers)

              }

            } catch {

              print("Failed to set setCategory audio")

            }

          }

        }
      }
    }
  }

  @objc func preload(_ call: CAPPluginCall) {
    preloadAsset(call, isComplex: true)
  }

  @objc func play(_ call: CAPPluginCall) {
    let audioId = call.getString(Constant.AssetIdKey) ?? ""
    let time = call.getDouble("time") ?? 0
    let delay = call.getDouble("delay") ?? 0
    if audioId != "" {
      let queue = DispatchQueue(label: "ee.forgr.audio.complex.queue", qos: .userInitiated)

      queue.async {
        if self.audioList.count > 0 {
          let asset = self.audioList[audioId]

          if asset != nil {
            if asset is AudioAsset {
              let audioAsset = asset as? AudioAsset

              if self.fadeMusic {
                audioAsset?.playWithFade(time: time)
              } else {
                audioAsset?.play(time: time)
              }
              call.resolve()
            } else if asset is Int32 {
              let audioAsset = asset as? NSNumber ?? 0
              AudioServicesPlaySystemSound(SystemSoundID(audioAsset.intValue))
              call.resolve()
            } else {
              call.reject(Constant.ErrorAssetNotFound)
            }
          }
        }
      }
    }
  }

  @objc private func getAudioAsset(_ call: CAPPluginCall) -> AudioAsset? {
    let audioId = call.getString(Constant.AssetIdKey) ?? ""
    if audioId == "" {
      call.reject(Constant.ErrorAssetId)
      return nil
    }
    if self.audioList.count > 0 {
      let asset = self.audioList[audioId]
      if asset != nil && asset is AudioAsset {
        return asset as? AudioAsset
      }
    }
    call.reject(Constant.ErrorAssetNotFound + " - " + audioId)
    return nil
  }

  @objc func getDuration(_ call: CAPPluginCall) {
    guard let audioAsset: AudioAsset = self.getAudioAsset(call) else {
      return
    }

    call.resolve([
      "duration": audioAsset.getDuration()
    ])
  }

  @objc func getCurrentTime(_ call: CAPPluginCall) {
    guard let audioAsset: AudioAsset = self.getAudioAsset(call) else {
      return
    }

    call.resolve([
      "currentTime": audioAsset.getCurrentTime()
    ])
  }

  @objc func resume(_ call: CAPPluginCall) {
    guard let audioAsset: AudioAsset = self.getAudioAsset(call) else {
      return
    }

    audioAsset.resume()
    call.resolve()
  }

  @objc func pause(_ call: CAPPluginCall) {
    guard let audioAsset: AudioAsset = self.getAudioAsset(call) else {
      return
    }

    audioAsset.pause()
    call.resolve()
  }

  @objc func stop(_ call: CAPPluginCall) {
    let audioId = call.getString(Constant.AssetIdKey) ?? ""

    do {
      try stopAudio(audioId: audioId)
    } catch {
      call.reject(Constant.ErrorAssetNotFound)
    }
  }

  @objc func loop(_ call: CAPPluginCall) {
    guard let audioAsset: AudioAsset = self.getAudioAsset(call) else {
      return
    }

    audioAsset.loop()
    call.resolve()
  }

  @objc func unload(_ call: CAPPluginCall) {
    let audioId = call.getString(Constant.AssetIdKey) ?? ""
    if self.audioList.count > 0 {
      let asset = self.audioList[audioId]
      if asset != nil && asset is AudioAsset {
        guard let audioAsset = asset as? AudioAsset else {
          call.reject("Cannot cast to AudioAsset")
          return
        }
        audioAsset.unload()
        self.audioList[audioId] = nil
      }
    }
    call.resolve()
  }

  @objc func setVolume(_ call: CAPPluginCall) {
    guard let audioAsset: AudioAsset = self.getAudioAsset(call) else {
      return
    }

    let volume = call.getFloat(Constant.Volume) ?? 1.0

    audioAsset.setVolume(volume: volume as NSNumber)
    call.resolve()
  }

  @objc func setRate(_ call: CAPPluginCall) {
    guard let audioAsset: AudioAsset = self.getAudioAsset(call) else {
      return
    }

    let rate = call.getFloat(Constant.Rate) ?? 1.0
    audioAsset.setRate(rate: rate as NSNumber)
    call.resolve()
  }

  @objc func isPlaying(_ call: CAPPluginCall) {
    guard let audioAsset: AudioAsset = self.getAudioAsset(call) else {
      return
    }

    call.resolve([
      "isPlaying": audioAsset.isPlaying()
    ])
  }

  private func preloadAsset(_ call: CAPPluginCall, isComplex complex: Bool) {
    let audioId = call.getString(Constant.AssetIdKey) ?? ""
    let channels: Int?
    let volume: Float?
    let delay: Float?
    let isUrl: Bool?

    if audioId != "" {
      var assetPath: String = call.getString(Constant.AssetPathKey) ?? ""

      if complex {
        volume = call.getFloat("volume") ?? 1.0
        channels = call.getInt("channels") ?? 1
        delay = call.getFloat("delay") ?? 1.0
        isUrl = call.getBool("isUrl") ?? false
      } else {
        channels = 0
        volume = 0
        delay = 0
        isUrl = false
      }

      if audioList.isEmpty {
        audioList = [:]
      }

      let asset = audioList[audioId]
      let queue = DispatchQueue(label: "ee.forgr.audio.simple.queue", qos: .userInitiated)
      queue.async {
        if asset == nil {
          var basePath: String?
          if isUrl == false {
            // if assetPath dont start with public/ add it
            assetPath = assetPath.starts(with: "public/") ? assetPath : "public/" + assetPath

            let assetPathSplit = assetPath.components(separatedBy: ".")
            basePath = Bundle.main.path(forResource: assetPathSplit[0], ofType: assetPathSplit[1])
          } else {
            let url = URL(string: assetPath)
            basePath = url!.path
          }

          if FileManager.default.fileExists(atPath: basePath ?? "") {
            if !complex {
              let soundFileUrl = URL(fileURLWithPath: basePath ?? "")
              var soundId = SystemSoundID()
              AudioServicesCreateSystemSoundID(soundFileUrl as CFURL, &soundId)
              self.audioList[audioId] = NSNumber(value: Int32(soundId))
              call.resolve()
            } else {
              let audioAsset: AudioAsset = AudioAsset(
                owner: self,
                withAssetId: audioId, withPath: basePath, withChannels: channels,
                withVolume: volume, withFadeDelay: delay)
              self.audioList[audioId] = audioAsset
              call.resolve()
            }
          } else {
            call.reject(Constant.ErrorAssetPath + " - " + assetPath)
          }
        } else {
          call.reject(Constant.ErrorAssetAlreadyLoaded + " - " + audioId)
        }
      }
    }
  }

  private func stopAudio(audioId: String) throws {
    if self.audioList.count > 0 {
      let asset = self.audioList[audioId]

      if asset != nil {
        if asset is AudioAsset {
          let audioAsset = asset as? AudioAsset

          if self.fadeMusic {
            audioAsset?.playWithFade(time: audioAsset?.getCurrentTime() ?? 0)
          } else {
            audioAsset?.stop()
          }
        }
      } else {
        throw MyError.runtimeError(Constant.ErrorAssetNotFound)
      }
    }
  }
}
