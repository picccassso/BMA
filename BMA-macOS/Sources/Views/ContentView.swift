import SwiftUI

struct ContentView: View {
    @EnvironmentObject var serverManager: ServerManager
    @EnvironmentObject var musicLibrary: MusicLibrary
    
    var body: some View {
        VStack(spacing: 0) {
            // Server status bar
            ServerStatusBar()
                .padding()
                .background(Color.gray.opacity(0.1))
            
            // Main content - Library Management Focus
            HStack(spacing: 0) {
                // Song list (now focused on library management)
                SongListView()
                    .frame(minWidth: 400) // Expanded since no player view
            }
            .frame(maxHeight: .infinity)
            
            // Library status bar (replacing player controls)
            LibraryStatusBar()
                .padding()
                .background(Color.gray.opacity(0.1))
        }
    }
} 