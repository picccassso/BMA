import Foundation
import SwiftUI

class MusicLibrary: ObservableObject {
    static let shared = MusicLibrary()
    
    @Published var songs: [Song] = []
    @Published var albums: [Album] = []
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
        albums.removeAll()
        
        Task {
            let fileManager = FileManager.default
            
            do {
                print("🔍 [LIBRARY] Starting enhanced music library scan...")
                var discoveredSongs: [Song] = []
                
                // Scan recursively for better album detection
                try scanDirectory(path: folderPath, songs: &discoveredSongs)
                
                await MainActor.run {
                    // Apply enhanced sorting and organization
                    self.songs = self.organizeAndSortSongs(discoveredSongs)
                    self.albums = self.organizeIntoAlbums(self.songs)
                    self.isScanning = false
                    
                    print("🔍 [LIBRARY] Scan complete: \(self.songs.count) songs in \(self.albums.count) albums")
                    self.printLibraryDebugInfo()
                }
                
            } catch {
                print("❌ [LIBRARY] Error scanning folder: \(error)")
                await MainActor.run {
                    self.isScanning = false
                }
            }
        }
    }
    
    /**
     * ENHANCED: Recursive directory scanning for better album detection
     */
    private func scanDirectory(path: String, songs: inout [Song]) throws {
        let fileManager = FileManager.default
        let items = try fileManager.contentsOfDirectory(atPath: path)
        
        for item in items {
            let fullPath = (path as NSString).appendingPathComponent(item)
            
            var isDirectory: ObjCBool = false
            fileManager.fileExists(atPath: fullPath, isDirectory: &isDirectory)
            
            if isDirectory.boolValue {
                // Recursively scan subdirectories (album folders)
                try scanDirectory(path: fullPath, songs: &songs)
            } else if item.lowercased().hasSuffix(".mp3") {
                let song = Song(filename: item, path: fullPath, parentDirectory: path)
                songs.append(song)
            }
        }
    }
    
    /**
     * ENHANCED: Smart sorting with numbered track priority
     */
    private func organizeAndSortSongs(_ songs: [Song]) -> [Song] {
        print("🔍 [LIBRARY] Applying enhanced sorting algorithm...")
        
        return songs.sorted { song1, song2 in
            // First sort by album
            let album1 = song1.album ?? song1.inferredAlbum ?? ""
            let album2 = song2.album ?? song2.inferredAlbum ?? ""
            
            if album1 != album2 {
                return album1.localizedCaseInsensitiveCompare(album2) == .orderedAscending
            }
            
            // Within same album, apply numbered track priority
            return compareTracksWithNumberPriority(song1.title, song2.title)
        }
    }
    
    /**
     * ENHANCED: Lexicographic sorting with numbered track priority (01, 02, 10)
     */
    private func compareTracksWithNumberPriority(_ title1: String, _ title2: String) -> Bool {
        // Extract leading numbers for comparison
        let number1 = extractLeadingNumber(from: title1)
        let number2 = extractLeadingNumber(from: title2)
        
        switch (number1, number2) {
        case (.some(let num1), .some(let num2)):
            // Both have numbers - compare lexicographically (01 comes before 10)
            let str1 = String(format: "%02d", num1)
            let str2 = String(format: "%02d", num2)
            if str1 != str2 {
                return str1.localizedCaseInsensitiveCompare(str2) == .orderedAscending
            }
            // If numbers are same, compare rest of title
            return title1.localizedCaseInsensitiveCompare(title2) == .orderedAscending
            
        case (.some(_), .none):
            // First has number, second doesn't - numbered comes first
            return true
            
        case (.none, .some(_)):
            // Second has number, first doesn't - numbered comes first
            return false
            
        case (.none, .none):
            // Neither has numbers - normal alphabetical
            return title1.localizedCaseInsensitiveCompare(title2) == .orderedAscending
        }
    }
    
    /**
     * ENHANCED: Extract leading number from track title
     */
    private func extractLeadingNumber(from title: String) -> Int? {
        let pattern = #"^(\d+)"#
        if let regex = try? NSRegularExpression(pattern: pattern),
           let match = regex.firstMatch(in: title, range: NSRange(title.startIndex..., in: title)) {
            let numberString = String(title[Range(match.range(at: 1), in: title)!])
            return Int(numberString)
        }
        return nil
    }
    
    /**
     * ENHANCED: Organize songs into albums
     */
    private func organizeIntoAlbums(_ songs: [Song]) -> [Album] {
        print("🔍 [LIBRARY] Organizing songs into albums...")
        
        let grouped = Dictionary(grouping: songs) { song in
            song.album ?? song.inferredAlbum ?? "Unknown Album"
        }
        
        return grouped.map { (albumName, albumSongs) in
            Album(
                name: albumName,
                songs: albumSongs,
                artist: albumSongs.first?.artist ?? albumSongs.first?.inferredArtist
            )
        }.sorted { $0.name.localizedCaseInsensitiveCompare($1.name) == .orderedAscending }
    }
    
    /**
     * DEBUG: Print library organization info
     */
    private func printLibraryDebugInfo() {
        print("🔍 [LIBRARY DEBUG] ===== LIBRARY ORGANIZATION =====")
        for album in albums.prefix(3) { // Show first 3 albums
            print("🔍 [LIBRARY DEBUG] Album: \(album.name) (\(album.songs.count) songs)")
            for song in album.songs.prefix(5) { // Show first 5 songs per album
                print("🔍 [LIBRARY DEBUG]   - \(song.title)")
            }
        }
        print("🔍 [LIBRARY DEBUG] ===================================")
    }
    
    func getSong(by id: String) -> Song? {
        return songs.first { $0.id.uuidString == id }
    }
}

/**
 * NEW: Album model for enhanced organization
 */
struct Album: Identifiable {
    let id = UUID()
    let name: String
    let songs: [Song]
    let artist: String?
    
    var trackCount: Int { songs.count }
} 