package server

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"log"
	"net/http"
	"os"
	"time"

	"bma-cli/internal/models"
	"github.com/gorilla/mux"
)

// MusicServer handles music streaming and API endpoints
type MusicServer struct {
	config       *models.Config
	musicLibrary *models.MusicLibrary
	server       *http.Server
	router       *mux.Router
}

// NewMusicServer creates a new music server
func NewMusicServer(config *models.Config, musicLibrary *models.MusicLibrary) *MusicServer {
	ms := &MusicServer{
		config:       config,
		musicLibrary: musicLibrary,
	}
	
	ms.setupRoutes()
	return ms
}

// setupRoutes configures all music streaming endpoints
func (ms *MusicServer) setupRoutes() {
	ms.router = mux.NewRouter()
	
	// Add CORS middleware for mobile app access
	ms.router.Use(ms.corsMiddleware)
	ms.router.Use(ms.requestLoggingMiddleware)
	
	// Public endpoints (no authentication required for now)
	ms.router.HandleFunc("/health", ms.handleHealth).Methods("GET")
	ms.router.HandleFunc("/info", ms.handleInfo).Methods("GET")
	ms.router.HandleFunc("/songs", ms.handleSongs).Methods("GET")
	ms.router.HandleFunc("/albums", ms.handleAlbums).Methods("GET")
	ms.router.HandleFunc("/stream/{songId}", ms.handleStream).Methods("GET")
	ms.router.HandleFunc("/artwork/{songId}", ms.handleArtwork).Methods("GET")
	
	log.Println("✅ Music server routes configured")
}

// Start starts the music server
func (ms *MusicServer) Start() error {
	ms.server = &http.Server{
		Addr:    ":8080",
		Handler: ms.router,
	}
	
	log.Println("🚀 Music server starting on :8080")
	return ms.server.ListenAndServe()
}

// Shutdown gracefully shuts down the server
func (ms *MusicServer) Shutdown() error {
	if ms.server == nil {
		return nil
	}
	
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	
	return ms.server.Shutdown(ctx)
}

// corsMiddleware adds CORS headers for mobile app access
func (ms *MusicServer) corsMiddleware(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Access-Control-Allow-Origin", "*")
		w.Header().Set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
		w.Header().Set("Access-Control-Allow-Headers", "Content-Type, Authorization")
		
		// Handle preflight requests
		if r.Method == "OPTIONS" {
			w.WriteHeader(http.StatusOK)
			return
		}
		
		next.ServeHTTP(w, r)
	})
}

// requestLoggingMiddleware logs all HTTP requests
func (ms *MusicServer) requestLoggingMiddleware(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		start := time.Now()
		
		// Extract client info
		clientIP := r.RemoteAddr
		if forwarded := r.Header.Get("X-Forwarded-For"); forwarded != "" {
			clientIP = forwarded
		}
		userAgent := r.Header.Get("User-Agent")
		if userAgent == "" {
			userAgent = "unknown"
		}
		
		log.Printf("📥 [REQUEST] %s %s from %s", r.Method, r.URL.Path, clientIP)
		log.Printf("   └─ User-Agent: %s", userAgent)
		
		// Wrap ResponseWriter to capture status code
		wrapped := &responseWriter{ResponseWriter: w, statusCode: 200}
		
		// Call next handler
		next.ServeHTTP(wrapped, r)
		
		// Log response
		duration := time.Since(start)
		log.Printf("📤 [RESPONSE] %d (%s)", wrapped.statusCode, duration)
	})
}

// responseWriter wraps http.ResponseWriter to capture status code
type responseWriter struct {
	http.ResponseWriter
	statusCode int
}

func (rw *responseWriter) WriteHeader(code int) {
	rw.statusCode = code
	rw.ResponseWriter.WriteHeader(code)
}

// handleHealth returns server health status
func (ms *MusicServer) handleHealth(w http.ResponseWriter, r *http.Request) {
	log.Printf("🔍 Health check requested from %s", r.RemoteAddr)
	
	response := map[string]string{
		"status": "healthy",
	}
	
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(response)
}

// handleInfo returns server information
func (ms *MusicServer) handleInfo(w http.ResponseWriter, r *http.Request) {
	log.Println("ℹ️ Server info requested")
	
	// Get music library statistics
	var albumCount, songCount int
	if ms.musicLibrary != nil {
		albumCount = ms.musicLibrary.GetAlbumCount()
		songCount = ms.musicLibrary.GetSongCount()
		log.Printf("📊 Music library stats: %d albums, %d songs", albumCount, songCount)
	}
	
	response := map[string]interface{}{
		"server":      "BMA CLI Music Server",
		"version":     "1.0",
		"httpPort":    8080,
		"protocol":    "http",
		// Music library statistics
		"library": map[string]interface{}{
			"albumCount": albumCount,
			"songCount":  songCount,
			"hasLibrary": ms.musicLibrary != nil,
			"musicPath":  ms.config.MusicFolder,
		},
	}
	
	w.Header().Set("Content-Type", "application/json")
	if err := json.NewEncoder(w).Encode(response); err != nil {
		log.Printf("❌ Failed to encode server info: %v", err)
		http.Error(w, "Internal server error", http.StatusInternalServerError)
		return
	}
	
	log.Printf("✅ Server info sent successfully (albums: %d, songs: %d)", albumCount, songCount)
}

// handleSongs returns the list of all songs
func (ms *MusicServer) handleSongs(w http.ResponseWriter, r *http.Request) {
	log.Println("🎵 Songs list requested")
	
	// Check if music library is available
	if ms.musicLibrary == nil {
		log.Println("❌ No music library available")
		songs := []map[string]interface{}{}
		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(songs)
		return
	}
	
	// Get songs from the music library
	librarySongs := ms.musicLibrary.GetSongs()
	log.Printf("📊 Retrieved %d songs from music library", len(librarySongs))
	
	// Convert songs to JSON-compatible format
	songs := make([]map[string]interface{}, len(librarySongs))
	for i, song := range librarySongs {
		songs[i] = map[string]interface{}{
			"id":              song.ID.String(),
			"filename":        song.Filename,
			"title":           song.Title,
			"artist":          song.Artist,
			"album":           song.Album,
			"trackNumber":     song.TrackNumber,
			"parentDirectory": song.ParentDirectory,
			"hasArtwork":      song.HasArtwork(),
			"sortOrder":       i, // Explicit sort order
		}
	}
	
	log.Printf("📊 Returning %d songs to client", len(songs))
	
	w.Header().Set("Content-Type", "application/json")
	if err := json.NewEncoder(w).Encode(songs); err != nil {
		log.Printf("❌ Failed to encode songs data: %v", err)
		http.Error(w, "Internal server error", http.StatusInternalServerError)
		return
	}
	
	log.Println("✅ Songs list sent successfully")
}

// handleAlbums returns the list of all albums
func (ms *MusicServer) handleAlbums(w http.ResponseWriter, r *http.Request) {
	log.Println("📀 Albums list requested")
	
	// Check if music library is available
	if ms.musicLibrary == nil {
		log.Println("❌ No music library available")
		albums := []map[string]interface{}{}
		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(albums)
		return
	}
	
	// Get albums from the music library
	libraryAlbums := ms.musicLibrary.GetAlbums()
	log.Printf("📊 Retrieved %d albums from music library", len(libraryAlbums))
	
	// Convert albums to JSON-compatible format
	albums := make([]map[string]interface{}, len(libraryAlbums))
	for i, album := range libraryAlbums {
		// Convert songs in album
		songs := make([]map[string]interface{}, len(album.Songs))
		for j, song := range album.Songs {
			songs[j] = map[string]interface{}{
				"id":          song.ID.String(),
				"title":       song.Title,
				"artist":      song.Artist,
				"trackNumber": song.TrackNumber,
				"hasArtwork":  song.HasArtwork(),
			}
		}
		
		albums[i] = map[string]interface{}{
			"id":         album.ID.String(),
			"name":       album.Name,
			"artist":     album.Artist,
			"trackCount": album.TrackCount(),
			"songs":      songs,
		}
	}
	
	log.Printf("📊 Returning %d albums to client", len(albums))
	
	w.Header().Set("Content-Type", "application/json")
	if err := json.NewEncoder(w).Encode(albums); err != nil {
		log.Printf("❌ Failed to encode albums data: %v", err)
		http.Error(w, "Internal server error", http.StatusInternalServerError)
		return
	}
	
	log.Println("✅ Albums list sent successfully")
}

// handleStream serves MP3 file content for a given song ID
func (ms *MusicServer) handleStream(w http.ResponseWriter, r *http.Request) {
	vars := mux.Vars(r)
	songID := vars["songId"]
	
	if songID == "" {
		http.Error(w, "Missing song ID", http.StatusBadRequest)
		return
	}
	
	log.Printf("🎵 Stream requested for song ID: %s", songID)
	
	// Check if music library is available
	if ms.musicLibrary == nil {
		log.Println("❌ No music library available")
		http.Error(w, "Music library not available", http.StatusServiceUnavailable)
		return
	}
	
	// Find the song in the music library
	song := ms.musicLibrary.GetSongByID(songID)
	if song == nil {
		log.Printf("❌ Song not found: %s", songID)
		http.Error(w, "Song not found", http.StatusNotFound)
		return
	}
	
	log.Printf("🎵 Streaming song: %s - %s", song.Artist, song.Title)
	log.Printf("🎵 File path: %s", song.Path)
	
	// Check if file exists
	if _, err := os.Stat(song.Path); os.IsNotExist(err) {
		log.Printf("❌ MP3 file not found at path: %s", song.Path)
		http.Error(w, "Music file not found", http.StatusNotFound)
		return
	}
	
	// Stream the MP3 file
	if err := ms.writeFileResponse(w, song.Path, "audio/mpeg"); err != nil {
		log.Printf("❌ Failed to stream MP3 file: %v", err)
		http.Error(w, "Failed to stream file", http.StatusInternalServerError)
		return
	}
	
	log.Printf("✅ Successfully streamed: %s", song.Title)
}

// handleArtwork serves album artwork for a given song ID  
func (ms *MusicServer) handleArtwork(w http.ResponseWriter, r *http.Request) {
	vars := mux.Vars(r)
	songID := vars["songId"]
	
	if songID == "" {
		http.Error(w, "Missing song ID", http.StatusBadRequest)
		return
	}
	
	log.Printf("🎨 Artwork requested for song ID: %s", songID)
	
	// Check if music library is available
	if ms.musicLibrary == nil {
		log.Println("❌ No music library available")
		http.Error(w, "Music library not available", http.StatusServiceUnavailable)
		return
	}
	
	// Find the song in the music library
	song := ms.musicLibrary.GetSongByID(songID)
	if song == nil {
		log.Printf("❌ Song not found: %s", songID)
		http.Error(w, "Song not found", http.StatusNotFound)
		return
	}
	
	// Check if song has artwork
	artworkData := song.GetArtwork()
	if len(artworkData) == 0 {
		log.Printf("❌ No artwork found for song: %s - %s", song.Artist, song.Title)
		http.Error(w, "Artwork not found", http.StatusNotFound)
		return
	}
	
	log.Printf("🎨 Serving artwork for: %s - %s (%d bytes)", song.Artist, song.Title, len(artworkData))
	
	// Determine content type (most MP3 artwork is JPEG, but could be PNG)
	contentType := "image/jpeg"
	if len(artworkData) >= 8 {
		// Check for PNG signature
		if artworkData[0] == 0x89 && artworkData[1] == 0x50 && artworkData[2] == 0x4E && artworkData[3] == 0x47 {
			contentType = "image/png"
		}
	}
	
	// Set headers and serve artwork
	w.Header().Set("Content-Type", contentType)
	w.Header().Set("Content-Length", fmt.Sprintf("%d", len(artworkData)))
	w.Header().Set("Cache-Control", "public, max-age=3600") // Cache for 1 hour
	
	if _, err := w.Write(artworkData); err != nil {
		log.Printf("❌ Failed to serve artwork: %v", err)
		return
	}
	
	log.Printf("✅ Successfully served artwork for: %s", song.Title)
}

// writeFileResponse streams a file as HTTP response
func (ms *MusicServer) writeFileResponse(w http.ResponseWriter, filePath, contentType string) error {
	file, err := os.Open(filePath)
	if err != nil {
		return err
	}
	defer file.Close()
	
	// Get file info for content length
	fileInfo, err := file.Stat()
	if err != nil {
		return err
	}
	
	// Set headers
	w.Header().Set("Content-Type", contentType)
	w.Header().Set("Content-Length", fmt.Sprintf("%d", fileInfo.Size()))
	w.Header().Set("Accept-Ranges", "bytes")
	
	// Stream file content
	_, err = io.Copy(w, file)
	return err
}