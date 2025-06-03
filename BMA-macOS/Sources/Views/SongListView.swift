import SwiftUI

struct SongListView: View {
    @EnvironmentObject var musicLibrary: MusicLibrary
    @EnvironmentObject var audioPlayer: AudioPlayer
    @State private var selectedSongId: UUID?
    
    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            // Header
            HStack {
                Text("Songs")
                    .font(.headline)
                
                Spacer()
                
                if musicLibrary.isScanning {
                    ProgressView()
                        .scaleEffect(0.7)
                }
                
                Text("\(musicLibrary.songs.count) songs")
                    .font(.caption)
                    .foregroundColor(.secondary)
            }
            .padding()
            
            Divider()
            
            // Song list
            if musicLibrary.songs.isEmpty {
                VStack {
                    Spacer()
                    Text("No songs found")
                        .foregroundColor(.secondary)
                    Text("Select a folder to scan for MP3 files")
                        .font(.caption)
                        .foregroundColor(.secondary)
                    Spacer()
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                ScrollView {
                    VStack(spacing: 0) {
                        ForEach(musicLibrary.songs) { song in
                            SongRow(song: song, isSelected: selectedSongId == song.id)
                                .onTapGesture {
                                    selectedSongId = song.id
                                    audioPlayer.play(song: song)
                                }
                        }
                    }
                }
            }
        }
    }
}

struct SongRow: View {
    let song: Song
    let isSelected: Bool
    
    var body: some View {
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text(song.title)
                    .font(.system(size: 13))
                    .lineLimit(1)
                
                if let artist = song.artist {
                    Text(artist)
                        .font(.caption)
                        .foregroundColor(.secondary)
                        .lineLimit(1)
                }
            }
            
            Spacer()
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 8)
        .background(isSelected ? Color.accentColor.opacity(0.2) : Color.clear)
        .contentShape(Rectangle())
    }
} 