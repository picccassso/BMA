import SwiftUI

struct PlayerView: View {
    @EnvironmentObject var audioPlayer: AudioPlayer
    
    var body: some View {
        VStack {
            if let currentSong = audioPlayer.currentSong {
                VStack(spacing: 20) {
                    // Album art placeholder
                    RoundedRectangle(cornerRadius: 8)
                        .fill(Color.gray.opacity(0.2))
                        .frame(width: 200, height: 200)
                        .overlay(
                            Image(systemName: "music.note")
                                .font(.system(size: 60))
                                .foregroundColor(.gray.opacity(0.5))
                        )
                    
                    // Song info
                    VStack(spacing: 4) {
                        Text(currentSong.title)
                            .font(.title2)
                            .lineLimit(1)
                        
                        if let artist = currentSong.artist {
                            Text(artist)
                                .font(.subheadline)
                                .foregroundColor(.secondary)
                        }
                    }
                    
                    // Progress bar
                    VStack(spacing: 4) {
                        ProgressBar(value: audioPlayer.currentTime, 
                                  maxValue: audioPlayer.duration,
                                  onSeek: { newTime in
                                      audioPlayer.seek(to: newTime)
                                  })
                        
                        HStack {
                            Text(formatTime(audioPlayer.currentTime))
                                .font(.caption)
                                .foregroundColor(.secondary)
                            
                            Spacer()
                            
                            Text(formatTime(audioPlayer.duration))
                                .font(.caption)
                                .foregroundColor(.secondary)
                        }
                    }
                    .padding(.horizontal, 20)
                }
                .padding()
            } else {
                VStack {
                    Image(systemName: "music.note")
                        .font(.system(size: 60))
                        .foregroundColor(.gray.opacity(0.3))
                    
                    Text("No song playing")
                        .foregroundColor(.secondary)
                }
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
    
    private func formatTime(_ time: TimeInterval) -> String {
        guard time.isFinite && !time.isNaN else { return "0:00" }
        
        let minutes = Int(time) / 60
        let seconds = Int(time) % 60
        return String(format: "%d:%02d", minutes, seconds)
    }
}

struct ProgressBar: View {
    let value: Double
    let maxValue: Double
    let onSeek: (Double) -> Void
    
    @State private var isDragging = false
    @State private var dragValue: Double = 0
    
    var normalizedValue: Double {
        guard maxValue > 0 else { return 0 }
        return isDragging ? dragValue : (value / maxValue)
    }
    
    var body: some View {
        GeometryReader { geometry in
            ZStack(alignment: .leading) {
                // Background
                Capsule()
                    .fill(Color.gray.opacity(0.3))
                    .frame(height: 4)
                
                // Progress
                Capsule()
                    .fill(Color.accentColor)
                    .frame(width: geometry.size.width * normalizedValue, height: 4)
                
                // Thumb
                Circle()
                    .fill(Color.accentColor)
                    .frame(width: 12, height: 12)
                    .offset(x: geometry.size.width * normalizedValue - 6)
            }
            .contentShape(Rectangle())
            .gesture(
                DragGesture(minimumDistance: 0)
                    .onChanged { value in
                        isDragging = true
                        let progress = min(max(0, value.location.x / geometry.size.width), 1)
                        dragValue = progress
                    }
                    .onEnded { value in
                        let progress = min(max(0, value.location.x / geometry.size.width), 1)
                        onSeek(progress * maxValue)
                        isDragging = false
                    }
            )
        }
        .frame(height: 12)
    }
} 