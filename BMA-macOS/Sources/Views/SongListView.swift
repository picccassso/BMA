import SwiftUI

struct SongListView: View {
    @EnvironmentObject var musicLibrary: MusicLibrary
    @State private var selectedSongId: UUID?
    @State private var expandedAlbums: Set<UUID> = []
    
    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            // Header
            HStack {
                Text("Music Library")
                    .font(.headline)
                
                Spacer()
                
                if musicLibrary.isScanning {
                    ProgressView()
                        .scaleEffect(0.7)
                }
                
                Text("\(musicLibrary.albums.count) albums • \(musicLibrary.songs.count) songs")
                    .font(.caption)
                    .foregroundColor(.secondary)
            }
            .padding()
            
            Divider()
            
            // Album folder structure
            if musicLibrary.albums.isEmpty {
                VStack {
                    Spacer()
                    Image(systemName: "folder.badge.questionmark")
                        .font(.system(size: 48))
                        .foregroundColor(.secondary)
                        .padding(.bottom, 8)
                    
                    Text("No albums found")
                        .font(.headline)
                        .foregroundColor(.secondary)
                    
                    Text("Select a folder to scan for music organized by albums")
                        .font(.caption)
                        .foregroundColor(.secondary)
                        .multilineTextAlignment(.center)
                    
                    Spacer()
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                ScrollView {
                    VStack(spacing: 0) {
                        ForEach(musicLibrary.albums) { album in
                            AlbumFolderView(
                                album: album,
                                isExpanded: expandedAlbums.contains(album.id),
                                selectedSongId: $selectedSongId,
                                onToggle: {
                                    if expandedAlbums.contains(album.id) {
                                        expandedAlbums.remove(album.id)
                                    } else {
                                        expandedAlbums.insert(album.id)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

struct AlbumFolderView: View {
    let album: Album
    let isExpanded: Bool
    @Binding var selectedSongId: UUID?
    let onToggle: () -> Void
    
    var body: some View {
        VStack(spacing: 0) {
            // Album folder header
            Button(action: onToggle) {
                HStack {
                    // Folder icon
                    Image(systemName: isExpanded ? "folder.fill" : "folder")
                        .font(.system(size: 16))
                        .foregroundColor(.accentColor)
                        .frame(width: 20)
                    
                    // Album info
                    VStack(alignment: .leading, spacing: 2) {
                        Text(album.name)
                            .font(.system(size: 14, weight: .medium))
                            .lineLimit(1)
                        
                        HStack(spacing: 8) {
                            if let artist = album.artist {
                                Text(artist)
                                    .font(.caption)
                                    .foregroundColor(.secondary)
                                    .lineLimit(1)
                            }
                            
                            Text("• \(album.trackCount) tracks")
                                .font(.caption)
                                .foregroundColor(.secondary)
                        }
                    }
                    
                    Spacer()
                    
                    // Expand/collapse indicator
                    Image(systemName: isExpanded ? "chevron.down" : "chevron.right")
                        .font(.caption)
                        .foregroundColor(.secondary)
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 10)
                .background(Color.gray.opacity(0.1))
                .contentShape(Rectangle())
            }
            .buttonStyle(PlainButtonStyle())
            
            // Songs in album (when expanded)
            if isExpanded {
                VStack(spacing: 0) {
                    ForEach(album.songs) { song in
                        SongRow(
                            song: song,
                            isSelected: selectedSongId == song.id,
                            isInFolder: true
                        )
                        .onTapGesture {
                            selectedSongId = song.id
                            // Library management: Just select for information display
                            // No audio playback on Mac anymore
                        }
                    }
                }
                .background(Color.gray.opacity(0.05))
            }
            
            // Separator between albums
            Divider()
                .opacity(0.3)
        }
    }
}

struct SongRow: View {
    let song: Song
    let isSelected: Bool
    let isInFolder: Bool
    
    var body: some View {
        HStack {
            // Indentation for songs in folders
            if isInFolder {
                Rectangle()
                    .fill(Color.clear)
                    .frame(width: 32)
            }
            
            VStack(alignment: .leading, spacing: 2) {
                Text(song.displayTitle)
                    .font(.system(size: 13))
                    .lineLimit(1)
                    .fontWeight(isSelected ? .medium : .regular)
                
                if !isInFolder {
                    HStack(spacing: 8) {
                        if let artist = song.artist ?? song.inferredArtist {
                            Text(artist)
                                .font(.caption)
                                .foregroundColor(.secondary)
                                .lineLimit(1)
                        }
                        
                        if let album = song.album ?? song.inferredAlbum {
                            if (song.artist ?? song.inferredArtist) != nil {
                                Text("•")
                                    .font(.caption)
                                    .foregroundColor(.secondary)
                            }
                            Text(album)
                                .font(.caption)
                                .foregroundColor(.secondary)
                                .lineLimit(1)
                        }
                    }
                }
            }
            
            Spacer()
            
            // Track indicator
            Image(systemName: "music.note")
                .font(.caption)
                .foregroundColor(.secondary)
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 6)
        .background(isSelected ? Color.accentColor.opacity(0.2) : Color.clear)
        .contentShape(Rectangle())
    }
} 