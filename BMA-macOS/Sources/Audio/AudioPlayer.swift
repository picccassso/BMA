import Foundation
import AVFoundation
import SwiftUI

class AudioPlayer: ObservableObject {
    @Published var isPlaying = false
    @Published var currentSong: Song? = nil
    @Published var currentTime: TimeInterval = 0
    @Published var duration: TimeInterval = 0
    @Published var volume: Float = 0.7
    
    private var player: AVPlayer?
    private var timeObserver: Any?
    
    init() {
        // No audio session setup needed for macOS
    }
    
    func play(song: Song) {
        // Stop current playback
        stop()
        
        // Create player item
        let url = URL(fileURLWithPath: song.path)
        let playerItem = AVPlayerItem(url: url)
        
        // Create player
        player = AVPlayer(playerItem: playerItem)
        player?.volume = volume
        
        // Set current song
        currentSong = song
        
        // Add time observer
        addTimeObserver()
        
        // Start playback
        player?.play()
        isPlaying = true
    }
    
    func togglePlayPause() {
        if isPlaying {
            pause()
        } else {
            resume()
        }
    }
    
    func pause() {
        player?.pause()
        isPlaying = false
    }
    
    func resume() {
        player?.play()
        isPlaying = true
    }
    
    func stop() {
        player?.pause()
        player = nil
        isPlaying = false
        currentSong = nil
        currentTime = 0
        duration = 0
        removeTimeObserver()
    }
    
    func setVolume(_ newVolume: Float) {
        volume = newVolume
        player?.volume = volume
    }
    
    func seek(to time: TimeInterval) {
        player?.seek(to: CMTime(seconds: time, preferredTimescale: 1000))
    }
    
    private func addTimeObserver() {
        let interval = CMTime(seconds: 0.1, preferredTimescale: 1000)
        timeObserver = player?.addPeriodicTimeObserver(forInterval: interval, queue: .main) { [weak self] time in
            self?.currentTime = time.seconds
            
            if let duration = self?.player?.currentItem?.duration {
                self?.duration = duration.seconds
            }
        }
    }
    
    private func removeTimeObserver() {
        if let observer = timeObserver {
            player?.removeTimeObserver(observer)
            timeObserver = nil
        }
    }
    
    deinit {
        stop()
    }
} 