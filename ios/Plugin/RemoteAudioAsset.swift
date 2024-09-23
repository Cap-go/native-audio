import AVFoundation

public class RemoteAudioAsset: AudioAsset {
    var playerItems: [AVPlayerItem] = []
    var players: [AVPlayer] = []
    var playerObservers: [NSKeyValueObservation] = []

    override init(owner: NativeAudio, withAssetId assetId: String, withPath path: String!, withChannels channels: Int!, withVolume volume: Float!, withFadeDelay delay: Float!) {
        super.init(owner: owner, withAssetId: assetId, withPath: path, withChannels: channels, withVolume: volume, withFadeDelay: delay)

        if let url = URL(string: path) {
            for _ in 0..<channels {
                let playerItem = AVPlayerItem(url: url)
                let player = AVPlayer(playerItem: playerItem)
                player.volume = volume
                self.playerItems.append(playerItem)
                self.players.append(player)

                // Add observer for playback finished
                let observer = player.observe(\.timeControlStatus) { [weak self] player, _ in
                    if player.timeControlStatus == .paused && player.currentItem?.currentTime() == player.currentItem?.duration {
                        self?.playerDidFinishPlaying(player: player)
                    }
                }
                self.playerObservers.append(observer)
            }
        }
    }

    func playerDidFinishPlaying(player: AVPlayer) {
        self.owner.notifyListeners("complete", data: [
            "assetId": self.assetId
        ])
    }

    override func play(time: TimeInterval, delay: TimeInterval) {
        guard !players.isEmpty else { return }
        let player = players[playIndex]
        if delay > 0 {
            let timeToPlay = CMTimeAdd(CMTimeMakeWithSeconds(player.currentTime().seconds, preferredTimescale: 1), CMTimeMakeWithSeconds(delay, preferredTimescale: 1))
            player.seek(to: timeToPlay)
        } else {
            player.seek(to: CMTimeMakeWithSeconds(time, preferredTimescale: 1))
        }
        player.play()
        playIndex = (playIndex + 1) % players.count
    }

    override func pause() {
        guard !players.isEmpty else { return }
        let player = players[playIndex]
        player.pause()
    }

    override func resume() {
        guard !players.isEmpty else { return }
        let player = players[playIndex]
        player.play()
    }

    override func stop() {
        for player in players {
            player.pause()
            player.seek(to: CMTime.zero)
        }
    }

    override func loop() {
        for player in players {
            player.actionAtItemEnd = .none
            NotificationCenter.default.addObserver(self,
                                                   selector: #selector(playerItemDidReachEnd(notification:)),
                                                   name: .AVPlayerItemDidPlayToEndTime,
                                                   object: player.currentItem)
            player.play()
        }
    }

    @objc func playerItemDidReachEnd(notification: Notification) {
        guard let player = notification.object as? AVPlayer else { return }
        player.seek(to: CMTime.zero)
        player.play()
    }

    override func unload() {
        stop()
        NotificationCenter.default.removeObserver(self)
        // Remove KVO observers
        for observer in playerObservers {
            observer.invalidate()
        }
        playerObservers = []
        players = []
        playerItems = []
    }

    override func setVolume(volume: NSNumber!) {
        for player in players {
            player.volume = volume.floatValue
        }
    }

    override func setRate(rate: NSNumber!) {
        for player in players {
            player.rate = rate.floatValue
        }
    }

    override func isPlaying() -> Bool {
        return players.contains { $0.timeControlStatus == .playing }
    }

    override func getCurrentTime() -> TimeInterval {
        guard !players.isEmpty else { return 0 }
        let player = players[playIndex]
        return player.currentTime().seconds
    }
}
