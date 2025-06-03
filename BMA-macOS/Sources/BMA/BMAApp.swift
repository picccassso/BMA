import SwiftUI

@main
struct BMAApp: App {
    @StateObject private var serverManager = ServerManager()
    @StateObject private var musicLibrary = MusicLibrary.shared
    @StateObject private var audioPlayer = AudioPlayer()
    
    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(serverManager)
                .environmentObject(musicLibrary)
                .environmentObject(audioPlayer)
                .frame(minWidth: 600, minHeight: 400)
        }
    }
} 