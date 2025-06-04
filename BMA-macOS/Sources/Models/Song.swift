import Foundation

struct Song: Identifiable, Codable {
    let id: UUID
    let filename: String
    let path: String
    let title: String
    let artist: String?
    let album: String?
    let duration: TimeInterval?
    
    // ENHANCED: Album inference from folder structure
    let parentDirectory: String
    
    init(filename: String, path: String, parentDirectory: String? = nil) {
        self.id = UUID()
        self.filename = filename
        self.path = path
        self.parentDirectory = parentDirectory ?? path
        
        // Extract title from filename (remove .mp3 extension)
        self.title = filename.replacingOccurrences(of: ".mp3", with: "", options: .caseInsensitive)
        
        // ENHANCED: Try to extract metadata from filename and path
        let metadata = Self.extractMetadata(from: filename, path: path)
        self.artist = metadata.artist
        self.album = metadata.album
        self.duration = nil // TODO: Extract from MP3 metadata if needed
    }
    
    /**
     * ENHANCED: Infer album from folder structure if not in metadata
     */
    var inferredAlbum: String? {
        if let album = album {
            return album
        }
        
        // Try to infer album from parent directory name
        let parentURL = URL(fileURLWithPath: parentDirectory)
        let folderName = parentURL.lastPathComponent
        
        // Skip common non-album folder names
        let skipFolders = ["Music", "iTunes", "Songs", "MP3", "Audio", "Downloads"]
        if !skipFolders.contains(folderName) && !folderName.isEmpty {
            return folderName
        }
        
        return nil
    }
    
    /**
     * ENHANCED: Infer artist from folder structure if not in metadata
     */
    var inferredArtist: String? {
        if let artist = artist {
            return artist
        }
        
        // Try to infer artist from parent directory structure
        let pathComponents = parentDirectory.components(separatedBy: "/")
        
        // Look for Artist/Album structure
        if pathComponents.count >= 2 {
            let potentialArtist = pathComponents[pathComponents.count - 2]
            let skipFolders = ["Music", "iTunes", "Songs", "MP3", "Audio", "Downloads"]
            if !skipFolders.contains(potentialArtist) && !potentialArtist.isEmpty {
                return potentialArtist
            }
        }
        
        return nil
    }
    
    /**
     * ENHANCED: Extract metadata from filename patterns
     */
    private static func extractMetadata(from filename: String, path: String) -> (artist: String?, album: String?) {
        let title = filename.replacingOccurrences(of: ".mp3", with: "", options: .caseInsensitive)
        
        // Pattern 1: "Artist - Song Title"
        if let hyphenRange = title.range(of: " - ") {
            let artist = String(title[..<hyphenRange.lowerBound]).trimmingCharacters(in: .whitespaces)
            if !artist.isEmpty {
                return (artist: artist, album: nil)
            }
        }
        
        // Pattern 2: "01. Song Title" or "Track Number Song Title"
        let trackPattern = #"^\d+\.?\s*(.+)$"#
        if let regex = try? NSRegularExpression(pattern: trackPattern),
           let match = regex.firstMatch(in: title, range: NSRange(title.startIndex..., in: title)) {
            // This is a numbered track, album might be inferred from folder
            return (artist: nil, album: nil)
        }
        
        // Pattern 3: "Artist_Album_Track" (underscore separated)
        let underscoreComponents = title.components(separatedBy: "_")
        if underscoreComponents.count >= 3 {
            return (
                artist: underscoreComponents[0].trimmingCharacters(in: .whitespaces),
                album: underscoreComponents[1].trimmingCharacters(in: .whitespaces)
            )
        }
        
        return (artist: nil, album: nil)
    }
    
    /**
     * ENHANCED: Display name for sorting/display purposes
     */
    var displayTitle: String {
        // Clean up track numbers for display
        let cleanTitle = title.replacingOccurrences(of: #"^\d+\.?\s*"#, with: "", options: .regularExpression)
        return cleanTitle.isEmpty ? title : cleanTitle
    }
} 