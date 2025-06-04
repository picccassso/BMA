import SwiftUI

struct LibraryStatusBar: View {
    @EnvironmentObject var musicLibrary: MusicLibrary
    @EnvironmentObject var serverManager: ServerManager
    
    var body: some View {
        HStack(spacing: 20) {
            // Library stats
            HStack(spacing: 12) {
                Image(systemName: "music.note.list")
                    .font(.title3)
                    .foregroundColor(.secondary)
                
                if musicLibrary.isScanning {
                    HStack(spacing: 6) {
                        ProgressView()
                            .scaleEffect(0.7)
                        Text("Scanning library...")
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
                } else {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("\(musicLibrary.songs.count) songs")
                            .font(.caption)
                            .fontWeight(.medium)
                        
                        if let folderPath = musicLibrary.selectedFolderPath {
                            Text("From: \(URL(fileURLWithPath: folderPath).lastPathComponent)")
                                .font(.caption2)
                                .foregroundColor(.secondary)
                        } else {
                            Text("No folder selected")
                                .font(.caption2)
                                .foregroundColor(.secondary)
                        }
                    }
                }
            }
            
            Spacer()
            
            // Server streaming status
            if serverManager.isRunning {
                HStack(spacing: 8) {
                    Image(systemName: "antenna.radiowaves.left.and.right")
                        .font(.caption)
                        .foregroundColor(.green)
                    
                    Text("Streaming to \(serverManager.connectedDevices.count) device(s)")
                        .font(.caption)
                        .foregroundColor(.secondary)
                }
            } else {
                HStack(spacing: 8) {
                    Image(systemName: "antenna.radiowaves.left.and.right.slash")
                        .font(.caption)
                        .foregroundColor(.secondary)
                    
                    Text("Server stopped")
                        .font(.caption)
                        .foregroundColor(.secondary)
                }
            }
            
            Spacer()
            
            // Refresh library button
            Button("Refresh Library") {
                musicLibrary.scanFolder()
            }
            .buttonStyle(.bordered)
            .controlSize(.small)
            .disabled(musicLibrary.selectedFolderPath == nil || musicLibrary.isScanning)
        }
    }
} 