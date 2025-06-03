import SwiftUI

struct PlayerControlsBar: View {
    @EnvironmentObject var audioPlayer: AudioPlayer
    
    var body: some View {
        HStack(spacing: 20) {
            // Play/Pause button
            Button(action: {
                audioPlayer.togglePlayPause()
            }) {
                Image(systemName: audioPlayer.isPlaying ? "pause.fill" : "play.fill")
                    .font(.title2)
            }
            .buttonStyle(.plain)
            .disabled(audioPlayer.currentSong == nil)
            
            // Stop button
            Button(action: {
                audioPlayer.stop()
            }) {
                Image(systemName: "stop.fill")
                    .font(.title2)
            }
            .buttonStyle(.plain)
            .disabled(audioPlayer.currentSong == nil)
            
            Spacer()
            
            // Volume control
            HStack {
                Image(systemName: "speaker.fill")
                    .font(.caption)
                    .foregroundColor(.secondary)
                
                Slider(value: Binding(
                    get: { Double(audioPlayer.volume) },
                    set: { audioPlayer.setVolume(Float($0)) }
                ), in: 0...1)
                .frame(width: 100)
                
                Image(systemName: "speaker.wave.3.fill")
                    .font(.caption)
                    .foregroundColor(.secondary)
            }
        }
    }
} 