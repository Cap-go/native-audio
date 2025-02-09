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
public class NativeAudio: CAPPlugin, AVAudioPlayerDelegate, CAPBridgedPlugin {
    public let identifier = "NativeAudio"
    public let jsName = "NativeAudio"
    public let pluginMethods: [CAPPluginMethod] = [
        CAPPluginMethod(name: "configure", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "preload", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "isPreloaded", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "play", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "pause", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "stop", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "loop", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "unload", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "setVolume", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "setRate", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "isPlaying", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "getCurrentTime", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "getDuration", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "resume", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "setCurrentTime", returnType: CAPPluginReturnPromise)
    ]
    private let audioQueue = DispatchQueue(label: "ee.forgr.audio.queue", qos: .userInitiated)
    private var audioList: [String: Any] = [:] {
        didSet {
            // Ensure audioList modifications happen on audioQueue
            assert(DispatchQueue.getSpecific(key: queueKey) != nil)
        }
    }
    private let queueKey = DispatchSpecificKey<Bool>()
    var fadeMusic = false
    var session = AVAudioSession.sharedInstance()

    override public func load() {
        super.load()
        audioQueue.setSpecific(key: queueKey, value: true)

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

        audioQueue.sync {
            call.resolve([
                "found": self.audioList[assetId] != nil
            ])
        }
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

        if audioId.isEmpty {
            call.reject(Constant.ErrorAssetId)
            return
        }

        audioQueue.async {
            guard !self.audioList.isEmpty else {
                call.reject("Audio list is empty")
                return
            }

            guard let asset = self.audioList[audioId] else {
                call.reject(Constant.ErrorAssetNotFound)
                return
            }

            if let audioAsset = asset as? AudioAsset {
                self.activateSession()
                if self.fadeMusic {
                    audioAsset.playWithFade(time: time)
                } else {
                    audioAsset.play(time: time, delay: delay)
                }
                call.resolve()
            } else if let audioNumber = asset as? NSNumber {
                self.activateSession()
                AudioServicesPlaySystemSound(SystemSoundID(audioNumber.intValue))
                call.resolve()
            } else {
                call.reject(Constant.ErrorAssetNotFound)
            }
        }
    }

    @objc private func getAudioAsset(_ call: CAPPluginCall) -> AudioAsset? {
        let audioId = call.getString(Constant.AssetIdKey) ?? ""
        if audioId.isEmpty {
            call.reject(Constant.ErrorAssetId)
            return nil
        }

        var asset: AudioAsset?
        audioQueue.sync {
            if self.audioList.isEmpty {
                call.reject("Audio list is empty")
                return
            }

            guard let foundAsset = self.audioList[audioId] as? AudioAsset else {
                call.reject(Constant.ErrorAssetNotFound + " - " + audioId)
                return
            }
            asset = foundAsset
        }

        if asset == nil {
            call.reject("Failed to get audio asset")
            return nil
        }
        return asset
    }

    @objc func setCurrentTime(_ call: CAPPluginCall) {
        audioQueue.async {
            guard let audioAsset: AudioAsset = self.getAudioAsset(call) else {
                call.reject("Failed to get audio asset")
                return
            }

            let time = call.getDouble("time") ?? 0
            audioAsset.setCurrentTime(time: time)
            call.resolve()
        }
    }

    @objc func getDuration(_ call: CAPPluginCall) {
        audioQueue.async {
            guard let audioAsset: AudioAsset = self.getAudioAsset(call) else {
                call.reject("Failed to get audio asset")
                return
            }

            call.resolve([
                "duration": audioAsset.getDuration()
            ])
        }
    }

    @objc func getCurrentTime(_ call: CAPPluginCall) {
        audioQueue.async {
            guard let audioAsset: AudioAsset = self.getAudioAsset(call) else {
                call.reject("Failed to get audio asset")
                return
            }

            call.resolve([
                "currentTime": audioAsset.getCurrentTime()
            ])
        }
    }

    @objc func resume(_ call: CAPPluginCall) {
        audioQueue.async {
            guard let audioAsset: AudioAsset = self.getAudioAsset(call) else {
                call.reject("Failed to get audio asset")
                return
            }
            self.activateSession()
            audioAsset.resume()
            call.resolve()
        }
    }

    @objc func pause(_ call: CAPPluginCall) {
        audioQueue.async {
            guard let audioAsset: AudioAsset = self.getAudioAsset(call) else {
                call.reject("Failed to get audio asset")
                return
            }

            audioAsset.pause()
            self.endSession()
            call.resolve()
        }
    }

    @objc func stop(_ call: CAPPluginCall) {
        let audioId = call.getString(Constant.AssetIdKey) ?? ""

        audioQueue.async {
            guard !self.audioList.isEmpty else {
                call.reject("Audio list is empty")
                return
            }

            do {
                try self.stopAudio(audioId: audioId)
                self.endSession()
                call.resolve()
            } catch {
                call.reject(Constant.ErrorAssetNotFound)
            }
        }
    }

    @objc func loop(_ call: CAPPluginCall) {
        audioQueue.async {
            guard let audioAsset: AudioAsset = self.getAudioAsset(call) else {
                call.reject("Failed to get audio asset")
                return
            }

            audioAsset.loop()
            call.resolve()
        }
    }

    @objc func unload(_ call: CAPPluginCall) {
        let audioId = call.getString(Constant.AssetIdKey) ?? ""

        audioQueue.async {
            guard !self.audioList.isEmpty else {
                call.reject("Audio list is empty")
                return
            }

            if let asset = self.audioList[audioId] as? AudioAsset {
                asset.unload()
                self.audioList[audioId] = nil
                call.resolve()
            } else {
                call.reject("Cannot cast to AudioAsset")
            }
        }
    }

    @objc func setVolume(_ call: CAPPluginCall) {
        audioQueue.async {
            guard let audioAsset: AudioAsset = self.getAudioAsset(call) else {
                call.reject("Failed to get audio asset")
                return
            }

            let volume = call.getFloat(Constant.Volume) ?? 1.0
            audioAsset.setVolume(volume: volume as NSNumber)
            call.resolve()
        }
    }

    @objc func setRate(_ call: CAPPluginCall) {
        audioQueue.async {
            guard let audioAsset: AudioAsset = self.getAudioAsset(call) else {
                call.reject("Failed to get audio asset")
                return
            }

            let rate = call.getFloat(Constant.Rate) ?? 1.0
            audioAsset.setRate(rate: rate as NSNumber)
            call.resolve()
        }
    }

    @objc func isPlaying(_ call: CAPPluginCall) {
        audioQueue.async {
            guard let audioAsset: AudioAsset = self.getAudioAsset(call) else {
                call.reject("Failed to get audio asset")
                return
            }

            call.resolve([
                "isPlaying": audioAsset.isPlaying()
            ])
        }
    }

    private func preloadAsset(_ call: CAPPluginCall, isComplex complex: Bool) {
        let audioId = call.getString(Constant.AssetIdKey) ?? ""
        let channels: Int?
        let volume: Float?
        let delay: Float?
        var isLocalUrl: Bool = call.getBool("isUrl") ?? false
        if audioId == "" {
            call.reject(Constant.ErrorAssetId)
            return
        }
        var assetPath: String = call.getString(Constant.AssetPathKey) ?? ""

        if complex {
            volume = call.getFloat("volume") ?? 1.0
            channels = call.getInt("channels") ?? 1
            delay = call.getFloat("delay") ?? 1.0
        } else {
            channels = 0
            volume = 0
            delay = 0
            isLocalUrl = false
        }

        if audioList.isEmpty {
            audioList = [:]
        }

        let asset = audioList[audioId]
        let queue = DispatchQueue(label: "ee.forgr.audio.simple.queue", qos: .userInitiated)
        if asset != nil {
            call.reject(Constant.ErrorAssetAlreadyLoaded + " - " + audioId)
            return
        }
        queue.async {
            var basePath: String?
            if let url = URL(string: assetPath), url.scheme != nil {
                // Handle remote URL
                let remoteAudioAsset = RemoteAudioAsset(owner: self, withAssetId: audioId, withPath: assetPath, withChannels: channels, withVolume: volume, withFadeDelay: delay)
                self.audioList[audioId] = remoteAudioAsset
                call.resolve()
                return
            } else if isLocalUrl == false {
                // Handle public folder
                // if assetPath doesnt start with public/ add it
                assetPath = assetPath.starts(with: "public/") ? assetPath : "public/" + assetPath

                let assetPathSplit = assetPath.components(separatedBy: ".")
                basePath = Bundle.main.path(forResource: assetPathSplit[0], ofType: assetPathSplit[1])
            } else {
                // Handle local file URL
                let fileURL = URL(fileURLWithPath: assetPath)
                basePath = fileURL.path
            }

            if let basePath = basePath, FileManager.default.fileExists(atPath: basePath) {
                if !complex {
                    let soundFileUrl = URL(fileURLWithPath: basePath)
                    var soundId = SystemSoundID()
                    AudioServicesCreateSystemSoundID(soundFileUrl as CFURL, &soundId)
                    self.audioList[audioId] = NSNumber(value: Int32(soundId))
                } else {
                    let audioAsset = AudioAsset(
                        owner: self,
                        withAssetId: audioId, withPath: basePath, withChannels: channels,
                        withVolume: volume, withFadeDelay: delay)
                    self.audioList[audioId] = audioAsset
                }
            } else {
                if !FileManager.default.fileExists(atPath: assetPath) {
                    call.reject(Constant.ErrorAssetPath + " - " + assetPath)
                    return
                }
                // Use the original assetPath
                if !complex {
                    let soundFileUrl = URL(fileURLWithPath: assetPath)
                    var soundId = SystemSoundID()
                    AudioServicesCreateSystemSoundID(soundFileUrl as CFURL, &soundId)
                    self.audioList[audioId] = NSNumber(value: Int32(soundId))
                } else {
                    let audioAsset = AudioAsset(
                        owner: self,
                        withAssetId: audioId, withPath: assetPath, withChannels: channels,
                        withVolume: volume, withFadeDelay: delay)
                    self.audioList[audioId] = audioAsset
                }
            }
            call.resolve()
        }
    }

    private func stopAudio(audioId: String) throws {
        var asset: AudioAsset?

        audioQueue.sync {
            asset = self.audioList[audioId] as? AudioAsset
        }

        guard let audioAsset = asset else {
            throw MyError.runtimeError(Constant.ErrorAssetNotFound)
        }

        if self.fadeMusic {
            audioAsset.playWithFade(time: audioAsset.getCurrentTime())
        } else {
            audioAsset.stop()
        }
    }
}
