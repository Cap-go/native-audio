//
//  AudioAsset.swift
//  Plugin
//
//  Created by priyank on 2020-05-29.
//  Copyright © 2022 Martin Donadieu. All rights reserved.
//

import AVFoundation

public class AudioAsset: NSObject, AVAudioPlayerDelegate {

    var channels: [AVAudioPlayer] = []
    var playIndex: Int = 0
    var assetId: String = ""
    var initialVolume: Float = 1.0
    var fadeDelay: Float = 1.0
    var owner: NativeAudio

    let FADESTEP: Float = 0.05
    let FADEDELAY: Float = 0.08

    init(owner: NativeAudio, withAssetId assetId: String, withPath path: String!, withChannels channels: Int!, withVolume volume: Float!, withFadeDelay delay: Float!) {

        self.owner = owner
        self.assetId = assetId
        self.channels = []

        super.init()

        let pathUrl: URL = URL(string: path.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed)!)!
        for _ in 0..<channels {
            do {
                let player: AVAudioPlayer! = try AVAudioPlayer(contentsOf: pathUrl)

                if player != nil {
                    player.volume = volume
                    player.prepareToPlay()
                    self.channels.append(player)
                    if channels == 1 {
                        player.delegate = self
                    }
                }
            } catch {
                print("Error loading \(String(describing: path))")
            }
        }
    }

    func getCurrentTime() -> TimeInterval {
        if channels.count != 1 {
            return 0
        }
        let player: AVAudioPlayer = channels[playIndex]

        return player.currentTime
    }

    func getDuration() -> TimeInterval {
        if channels.count != 1 {
            return 0
        }

        let player: AVAudioPlayer = channels[playIndex]

        return player.duration
    }

    func play(time: TimeInterval) {
        let player: AVAudioPlayer = channels[playIndex]
        player.currentTime = time
        player.numberOfLoops = 0
        player.play()
        playIndex += 1
        playIndex = playIndex % channels.count
    }

    func playWithFade(time: TimeInterval) {
        let player: AVAudioPlayer = channels[playIndex]
        player.currentTime = time

        if !player.isPlaying {
            player.numberOfLoops = 0
            player.volume = 0
            player.play()
            playIndex += 1
            playIndex = playIndex % channels.count
        } else {
            if player.volume < initialVolume {
                player.volume += self.FADESTEP
            }
        }

    }

    func pause() {
        let player: AVAudioPlayer = channels[playIndex]
        player.pause()
    }

    func resume() {
        let player: AVAudioPlayer = channels[playIndex]

        let timeOffset = player.deviceCurrentTime + 0.01
        player.play(atTime: timeOffset)
    }

    func stop() {
        for player in channels {
            player.stop()
        }
    }

    func stopWithFade() {
        let player: AVAudioPlayer = channels[playIndex]

        if !player.isPlaying {
            player.currentTime = 0.0
            player.numberOfLoops = 0
            player.volume = 0
            player.play()
            playIndex += 1
            playIndex = playIndex % channels.count
        } else {
            if player.volume < initialVolume {
                player.volume += self.FADESTEP
            }
        }
    }

    func loop() {
        self.stop()

        let player: AVAudioPlayer = channels[playIndex]
        player.numberOfLoops = -1
        player.play()
        playIndex += 1
        playIndex = playIndex % channels.count
    }

    func unload() {
        self.stop()

        //        for i in 0..<channels.count {
        //            var player: AVAudioPlayer! = channels.object(at: i) as? AVAudioPlayer
        //
        //            player = nil
        //        }
        channels = []
    }

    func setVolume(volume: NSNumber!) {
        for player in channels {
            player.volume = volume.floatValue
        }
    }

    func setRate(rate: NSNumber!) {
        for player in channels {
            player.rate = rate.floatValue
        }
    }

    public func audioPlayerDidFinishPlaying(_ player: AVAudioPlayer, successfully flag: Bool) {
        NSLog("playerDidFinish")
        self.owner.notifyListeners("complete", data: [
            "assetId": self.assetId
        ])
    }

    func playerDecodeError(player: AVAudioPlayer!, error: NSError!) {

    }

    func isPlaying() -> Bool {
        if channels.count != 1 {
            return false
        }

        let player: AVAudioPlayer = channels[playIndex]
        return player.isPlaying
    }
}
