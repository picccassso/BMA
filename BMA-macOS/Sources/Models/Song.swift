import Foundation

struct Song: Identifiable, Codable {
    let id: UUID
    let filename: String
    let path: String
    let title: String
    let artist: String?
    let album: String?
    let duration: TimeInterval?
    
    init(filename: String, path: String) {
        self.id = UUID()
        self.filename = filename
        self.path = path
        // Extract title from filename (remove .mp3 extension)
        self.title = filename.replacingOccurrences(of: ".mp3", with: "", options: .caseInsensitive)
        // TODO: Extract metadata from MP3 file
        self.artist = nil
        self.album = nil
        self.duration = nil
    }
} 