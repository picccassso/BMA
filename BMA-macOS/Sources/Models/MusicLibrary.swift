import Foundation
import SwiftUI

class MusicLibrary: ObservableObject {
    static let shared = MusicLibrary()
    
    @Published var songs: [Song] = []
    @Published var selectedFolderPath: String? = nil
    @Published var isScanning = false
    
    private init() {}
    
    func selectFolder() {
        let panel = NSOpenPanel()
        panel.title = "Select Music Folder"
        panel.allowsMultipleSelection = false
        panel.canChooseDirectories = true
        panel.canChooseFiles = false
        
        if panel.runModal() == .OK {
            selectedFolderPath = panel.url?.path
            scanFolder()
        }
    }
    
    func scanFolder() {
        guard let folderPath = selectedFolderPath else { return }
        
        isScanning = true
        songs.removeAll()
        
        Task {
            let fileManager = FileManager.default
            
            do {
                let items = try fileManager.contentsOfDirectory(atPath: folderPath)
                
                for item in items {
                    if item.lowercased().hasSuffix(".mp3") {
                        let fullPath = (folderPath as NSString).appendingPathComponent(item)
                        let song = Song(filename: item, path: fullPath)
                        
                        await MainActor.run {
                            self.songs.append(song)
                        }
                    }
                }
                
                await MainActor.run {
                    self.isScanning = false
                    print("Found \(self.songs.count) MP3 files")
                }
                
            } catch {
                print("Error scanning folder: \(error)")
                await MainActor.run {
                    self.isScanning = false
                }
            }
        }
    }
    
    func getSong(by id: String) -> Song? {
        return songs.first { $0.id.uuidString == id }
    }
} 