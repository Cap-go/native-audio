import AVFoundation
import Capacitor
import CoreAudio
import Foundation

enum MyError: Error {
    case runtimeError(String)
}

/// Please read the Capacitor iOS Plugin Development Guide
/// here: https://capacitor.ionicframework.com/docs/plugins/ios
@objc(NativeAudio)
public class NativeAudio: CAPPlugin, AVAudioPlayerDelegate {

    private let audioQueue = DispatchQueue(label: "ee.forgr.audio.queue")
    private var audioList: [String: Any] = [:]
    private var fadeMusic = false
    private var session = AVAudioSession.sharedInstance()

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

        let focus = call.getBool(Constant.FocusAudio) ?? false
        do {
            if focus {
                try self.session.setCategory(AVAudioSession.Category.playback, options: .duckOthers)

            }

        } catch {

            print("Failed to set setCategory audio")

        }

        let background = call.getBool(Constant.Background) ?? false

        do {

            if background {

                try self.session.setActive(true)

            }

        } catch {

            print("Failed to set setSession true")

        }

        let ignoreSilent = call.getBool(Constant.IgnoreSilent) ?? true

        do {

            if ignoreSilent == false {

                if let focus = call.getBool(Constant.FocusAudio) {

                    do {

                        if focus {

                            try self.session.setCategory(AVAudioSession.Category.ambient, options: .duckOthers)

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
        call.resolve()
    }

    @objc func isPreloaded(_ call: CAPPluginCall) {
        guard let assetId = call.getString(Constant.AssetIdKey) else {
            call.reject("Missing assetId")
            return
        }
        call.resolve([
            "found": self.audioList[assetId] != nil
        ])
    }

    @objc func preload(_ call: CAPPluginCall) {
        preloadAsset(call, isComplex: true)
    }

    func activateSession() {
        do {
            try self.session.setActive(true)
        } catch {
            print("Failed to set session active")
        }
    }

    func endSession() {
        do {
            try self.session.setActive(false, options: .notifyOthersOnDeactivation)
        } catch {
            print("Failed to deactivate audio session")
        }
    }

    public func audioPlayerDidFinishPlaying(_ player: AVAudioPlayer, successfully flag: Bool) {
        self.endSession()
    }

    @objc func play(_ call: CAPPluginCall) {
        let audioId = call.getString(Constant.AssetIdKey) ?? ""
        let time = call.getDouble("time") ?? 0
        let delay = call.getDouble("delay") ?? 0
        if audioId == "" {
            call.reject(Constant.ErrorAssetId)
            return
        }
        if self.audioList.count == 0 {
            call.reject("Audio list is empty")
            return
        }
        let queue = DispatchQueue(label: "ee.forgr.audio.complex.queue", qos: .userInitiated)
        let asset = self.audioList[audioId]
        if asset == nil {
            call.reject(Constant.ErrorAssetNotFound)
            return
        }
        queue.async {
            if asset is AudioAsset {
                let audioAsset = asset as? AudioAsset
                self.activateSession()
                if self.fadeMusic {
                    audioAsset?.playWithFade(time: time)
                } else {
                    audioAsset?.play(time: time, delay: delay)
                }
                call.resolve()
            } else if asset is Int32 {
                let audioAsset = asset as? NSNumber ?? 0
                self.activateSession()
                AudioServicesPlaySystemSound(SystemSoundID(audioAsset.intValue))
                call.resolve()
            } else {
                call.reject(Constant.ErrorAssetNotFound)
            }
        }
    }

    @objc private func getAudioAsset(_ call: CAPPluginCall) -> AudioAsset? {
        let audioId = call.getString(Constant.AssetIdKey) ?? ""
        if audioId == "" {
            call.reject(Constant.ErrorAssetId)
            return nil
        }
        if self.audioList.count == 0 {
            call.reject("Audio list is empty")
            return nil
        }
        let asset = self.audioList[audioId]
        if asset == nil || !(asset is AudioAsset) {
            call.reject(Constant.ErrorAssetNotFound + " - " + audioId)
            return nil
        }
        return asset as? AudioAsset
    }

    @objc func setCurrentTime(_ call: CAPPluginCall) {
        guard let audioAsset: AudioAsset = self.getAudioAsset(call) else {
            return
        }

        let time = call.getDouble("time") ?? 0
        audioAsset.setCurrentTime(time: time)
        call.resolve()
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
        self.activateSession()
        audioAsset.resume()
        call.resolve()
    }

    @objc func pause(_ call: CAPPluginCall) {
        guard let audioAsset: AudioAsset = self.getAudioAsset(call) else {
            return
        }

        audioAsset.pause()
        self.endSession()
        call.resolve()
    }

    @objc func stop(_ call: CAPPluginCall) {
        let audioId = call.getString(Constant.AssetIdKey) ?? ""

        if self.audioList.count == 0 {
            call.reject("Audio list is empty")
            return
        }
        do {
            try stopAudio(audioId: audioId)
            self.endSession()
            call.resolve()
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
        if self.audioList.count == 0 {
            call.reject("Audio list is empty")
            return
        }
        let asset = self.audioList[audioId]
        if asset != nil && asset is AudioAsset {
            guard let audioAsset = asset as? AudioAsset else {
                call.reject("Cannot cast to AudioAsset")
                return
            }
            audioAsset.unload()
            self.audioList[audioId] = nil
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
        guard let audioId = call.getString(Constant.AssetIdKey), !audioId.isEmpty else {
            call.reject(Constant.ErrorAssetId)
            return
        }
        
        guard let assetPath = call.getString(Constant.AssetPathKey), !assetPath.isEmpty else {
            call.reject(Constant.ErrorAssetPath)
            return
        }
        
        let isLocalUrl = call.getBool("isUrl") ?? false
        let channels: Int
        let volume: Float
        let delay: Float
        
        if complex {
            volume = call.getFloat("volume") ?? 1.0
            channels = call.getInt("channels") ?? 1
            delay = call.getFloat("delay") ?? 1.0
        } else {
            channels = 0
            volume = 0
            delay = 0
        }
        
        audioQueue.async {
            // Check if asset already exists
            if let _ = self.audioList[audioId] {
                call.reject(Constant.ErrorAssetAlreadyLoaded + " - " + audioId)
                return
            }
            
            var basePath: String?
            
            // Handle remote URL
            if let url = URL(string: assetPath), url.scheme != nil {
                let remoteAudioAsset = RemoteAudioAsset(
                    owner: self,
                    withAssetId: audioId,
                    withPath: assetPath,
                    withChannels: channels,
                    withVolume: volume,
                    withFadeDelay: delay
                )
                self.audioQueue.sync {
                    self.audioList[audioId] = remoteAudioAsset
                }
                call.resolve()
                return
            }
            
            // Handle local paths
            if !isLocalUrl {
                // Handle public folder
                let publicPath = assetPath.starts(with: "public/") ? assetPath : "public/" + assetPath
                let components = publicPath.components(separatedBy: ".")
                guard components.count >= 2 else {
                    call.reject("Invalid asset path format")
                    return
                }
                basePath = Bundle.main.path(forResource: components[0], ofType: components[1])
            } else {
                // Handle local file URL
                let fileURL = URL(fileURLWithPath: assetPath)
                basePath = fileURL.path
            }
            
            // Verify file exists
            guard let finalPath = basePath, FileManager.default.fileExists(atPath: finalPath) else {
                if !FileManager.default.fileExists(atPath: assetPath) {
                    call.reject(Constant.ErrorAssetPath + " - " + assetPath)
                    return
                }
                // Use original assetPath if basePath doesn't exist
                self.loadAudioAsset(complex: complex, path: assetPath, audioId: audioId, channels: channels, volume: volume, delay: delay)
                call.resolve()
                return
            }
            
            // Load audio asset with verified path
            self.loadAudioAsset(complex: complex, path: finalPath, audioId: audioId, channels: channels, volume: volume, delay: delay)
            call.resolve()
        }
    }
    
    private func loadAudioAsset(complex: Bool, path: String, audioId: String, channels: Int, volume: Float, delay: Float) {
        let soundFileUrl = URL(fileURLWithPath: path)
        
        if !complex {
            var soundId = SystemSoundID()
            AudioServicesCreateSystemSoundID(soundFileUrl as CFURL, &soundId)
            audioQueue.sync {
                self.audioList[audioId] = NSNumber(value: Int32(soundId))
            }
        } else {
            let audioAsset = AudioAsset(
                owner: self,
                withAssetId: audioId,
                withPath: path,
                withChannels: channels,
                withVolume: volume,
                withFadeDelay: delay
            )
            audioQueue.sync {
                self.audioList[audioId] = audioAsset
            }
        }
    }

    private func stopAudio(audioId: String) throws {
        return try audioQueue.sync {
            guard let asset = self.audioList[audioId] else {
                throw MyError.runtimeError(Constant.ErrorAssetNotFound)
            }
            
            guard let audioAsset = asset as? AudioAsset else {
                throw MyError.runtimeError(Constant.ErrorAssetNotFound)
            }
            
            if self.fadeMusic {
                let currentTime = audioAsset.getCurrentTime()
                audioAsset.playWithFade(time: currentTime)
            } else {
                audioAsset.stop()
            }
        }
    }
}
