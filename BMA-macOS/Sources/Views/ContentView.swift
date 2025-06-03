import SwiftUI

struct ContentView: View {
    @EnvironmentObject var serverManager: ServerManager
    @EnvironmentObject var musicLibrary: MusicLibrary
    @EnvironmentObject var audioPlayer: AudioPlayer
    
    var body: some View {
        VStack(spacing: 0) {
            // Server status bar
            ServerStatusBar()
                .padding()
                .background(Color.gray.opacity(0.1))
            
            // Main content
            HStack(spacing: 0) {
                // Song list
                SongListView()
                    .frame(minWidth: 200)
                
                Divider()
                
                // Player view
                PlayerView()
                    .frame(minWidth: 300)
            }
            .frame(maxHeight: .infinity)
            
            // Player controls bar
            PlayerControlsBar()
                .padding()
                .background(Color.gray.opacity(0.1))
        }
    }
} 