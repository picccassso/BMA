package models

import (
	"fmt"
	"log"
	"os"
	"path/filepath"
	"regexp"
	"strconv"
	"strings"
	"time"

	"github.com/dhowden/tag"
	"github.com/google/uuid"
)

// Song represents a music file with metadata
type Song struct {
	ID              uuid.UUID     `json:"id"`
	Filename        string        `json:"filename"`
	Path            string        `json:"path"`
	Title           string        `json:"title"`
	Artist          string        `json:"artist,omitempty"`
	Album           string        `json:"album,omitempty"`
	Duration        time.Duration `json:"duration,omitempty"`
	ParentDirectory string        `json:"parentDirectory"`
	TrackNumber     int           `json:"trackNumber,omitempty"`
	ArtworkData     []byte        `json:"-"` // Exclude from JSON, store artwork bytes
}

// NewSongFromFile creates a Song from an MP3 file path with full metadata extraction
func NewSongFromFile(filePath string) (*Song, error) {
	log.Printf("🎵 [DEBUG] Creating song from file: %s", filePath)
	
	// Generate unique ID
	id := uuid.New()
	
	// Extract basic file info
	filename := filepath.Base(filePath)
	parentDir := filepath.Dir(filePath)
	
	log.Printf("🎵 [DEBUG] File info - name: %s, dir: %s", filename, parentDir)
	
	// Initialize song with basic info
	song := &Song{
		ID:              id,
		Filename:        filename,
		Path:            filePath,
		ParentDirectory: parentDir,
	}
	
	// Extract metadata from MP3 file
	log.Printf("🎵 [DEBUG] Attempting MP3 metadata extraction")
	if err := song.extractMP3Metadata(); err != nil {
		log.Printf("⚠️ [DEBUG] MP3 metadata extraction failed: %v, falling back to filename", err)
		// If MP3 metadata extraction fails, fall back to filename parsing
		song.extractMetadataFromFilename()
	} else {
		log.Printf("🎵 [DEBUG] MP3 metadata extraction successful")
	}
	
	// Apply folder-based inference if metadata is missing
	log.Printf("🎵 [DEBUG] Applying folder inference")
	song.applyFolderInference()
	
	log.Printf("🎵 [DEBUG] Song created successfully: %s by %s", song.Title, song.Artist)
	
	return song, nil
}

// extractMP3Metadata extracts metadata from MP3 ID3 tags using github.com/dhowden/tag
func (s *Song) extractMP3Metadata() error {
	log.Printf("🎵 [DEBUG] Opening file for metadata: %s", s.Path)
	
	file, err := os.Open(s.Path)
	if err != nil {
		return fmt.Errorf("failed to open file: %w", err)
	}
	defer file.Close()
	
	log.Printf("🎵 [DEBUG] Reading tags from file")
	
	metadata, err := tag.ReadFrom(file)
	if err != nil {
		return fmt.Errorf("failed to read tags: %w", err)
	}
	
	log.Printf("🎵 [DEBUG] Processing metadata tags")
	
	// Extract basic metadata
	if title := metadata.Title(); title != "" {
		s.Title = title
		log.Printf("🎵 [DEBUG] Found title: %s", title)
	} else {
		// Fallback to filename without extension
		s.Title = strings.TrimSuffix(s.Filename, filepath.Ext(s.Filename))
		log.Printf("🎵 [DEBUG] No title found, using filename: %s", s.Title)
	}
	
	if artist := metadata.Artist(); artist != "" {
		s.Artist = artist
		log.Printf("🎵 [DEBUG] Found artist: %s", artist)
	}
	
	if album := metadata.Album(); album != "" {
		s.Album = album
		log.Printf("🎵 [DEBUG] Found album: %s", album)
	}
	
	// Extract track number
	if track, _ := metadata.Track(); track != 0 {
		s.TrackNumber = track
		log.Printf("🎵 [DEBUG] Found track number: %d", track)
	}
	
	// Duration extraction is not available in this tag library
	// We'll leave duration as 0 for now
	
	// Extract artwork
	if picture := metadata.Picture(); picture != nil {
		s.ArtworkData = picture.Data
		log.Printf("🎵 [DEBUG] Found artwork: %d bytes", len(picture.Data))
	}
	
	log.Printf("🎵 [DEBUG] Metadata extraction completed")
	return nil
}

// extractMetadataFromFilename falls back to parsing filename patterns
func (s *Song) extractMetadataFromFilename() {
	title := strings.TrimSuffix(s.Filename, ".mp3")
	s.Title = title
	
	// Pattern 1: "Artist - Song Title"
	if strings.Contains(title, " - ") {
		parts := strings.SplitN(title, " - ", 2)
		if len(parts) == 2 {
			artist := strings.TrimSpace(parts[0])
			songTitle := strings.TrimSpace(parts[1])
			if artist != "" {
				s.Artist = artist
				s.Title = songTitle
			}
		}
		return
	}
	
	// Pattern 2: "01. Song Title" or "Track Number Song Title"
	trackPattern := regexp.MustCompile(`^(\d+)\.?\s*(.+)$`)
	if matches := trackPattern.FindStringSubmatch(title); len(matches) == 3 {
		if trackNum, err := strconv.Atoi(matches[1]); err == nil {
			s.TrackNumber = trackNum
			s.Title = strings.TrimSpace(matches[2])
		}
		return
	}
	
	// Pattern 3: "Artist_Album_Track" (underscore separated)
	underscoreParts := strings.Split(title, "_")
	if len(underscoreParts) >= 3 {
		s.Artist = strings.TrimSpace(underscoreParts[0])
		s.Album = strings.TrimSpace(underscoreParts[1])
		s.Title = strings.TrimSpace(underscoreParts[2])
		return
	}
}

// applyFolderInference infers album and artist from folder structure
func (s *Song) applyFolderInference() {
	// Infer album from parent directory if not set
	if s.Album == "" {
		s.Album = s.InferredAlbum()
	}
	
	// Infer artist from folder structure if not set
	if s.Artist == "" {
		s.Artist = s.InferredArtist()
	}
}

// InferredAlbum tries to infer album from folder structure
func (s *Song) InferredAlbum() string {
	if s.Album != "" {
		return s.Album
	}
	
	// Try to infer album from parent directory name
	folderName := filepath.Base(s.ParentDirectory)
	
	// Skip common non-album folder names
	skipFolders := []string{"Music", "iTunes", "Songs", "MP3", "Audio", "Downloads"}
	for _, skip := range skipFolders {
		if strings.EqualFold(folderName, skip) {
			return ""
		}
	}
	
	if folderName != "" && folderName != "." {
		return folderName
	}
	
	return ""
}

// InferredArtist tries to infer artist from folder structure  
func (s *Song) InferredArtist() string {
	if s.Artist != "" {
		return s.Artist
	}
	
	// Try to infer artist from parent directory structure
	pathComponents := strings.Split(s.ParentDirectory, string(filepath.Separator))
	
	// Look for Artist/Album structure (parent's parent directory)
	if len(pathComponents) >= 2 {
		potentialArtist := pathComponents[len(pathComponents)-2]
		
		// Skip common non-artist folder names
		skipFolders := []string{"Music", "iTunes", "Songs", "MP3", "Audio", "Downloads"}
		for _, skip := range skipFolders {
			if strings.EqualFold(potentialArtist, skip) {
				return ""
			}
		}
		
		if potentialArtist != "" && potentialArtist != "." {
			return potentialArtist
		}
	}
	
	return ""
}

// DisplayTitle returns a cleaned up title for display (removes track numbers)
func (s *Song) DisplayTitle() string {
	// Clean up track numbers for display
	trackPattern := regexp.MustCompile(`^\d+\.?\s*`)
	cleanTitle := trackPattern.ReplaceAllString(s.Title, "")
	
	if cleanTitle == "" {
		return s.Title
	}
	
	return cleanTitle
}

// GetArtwork returns the album artwork bytes if available
func (s *Song) GetArtwork() []byte {
	return s.ArtworkData
}

// HasArtwork returns true if the song has embedded artwork
func (s *Song) HasArtwork() bool {
	return len(s.ArtworkData) > 0
}

// SortingTitle returns a title suitable for sorting (with proper numeric handling)
func (s *Song) SortingTitle() string {
	// Enhanced sorting: numbered track priority (01, 02, 10)
	if s.TrackNumber > 0 {
		return fmt.Sprintf("%03d_%s", s.TrackNumber, s.DisplayTitle())
	}
	return s.DisplayTitle()
}