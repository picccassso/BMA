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
                .onReceive(NotificationCenter.default.publisher(for: NSApplication.willTerminateNotification)) { _ in
                    // Ensure server is stopped before app terminates
                    print("🛑 App terminating - ensuring server shutdown...")
                    if serverManager.isRunning {
                        serverManager.stopServerSync()
                    }
                    print("✅ App termination cleanup completed")
                }
                .onReceive(NotificationCenter.default.publisher(for: NSApplication.didBecomeActiveNotification)) { _ in
                    // App became active - could add any startup logic here if needed
                }
                .onReceive(NotificationCenter.default.publisher(for: NSApplication.willHideNotification)) { _ in
                    // App is being hidden - server should keep running
                }
                .onReceive(NotificationCenter.default.publisher(for: NSApplication.didResignActiveNotification)) { _ in
                    // App lost focus - server should keep running for background streaming
                }
        }
    }
} 