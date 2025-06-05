package ui

import (
	"log"
	
	"fyne.io/fyne/v2"
	"fyne.io/fyne/v2/container"
	"fyne.io/fyne/v2/widget"

	"bma-go/internal/models"
	"bma-go/internal/server"
)

// MainUI represents the main application UI, equivalent to ContentView in Swift
type MainUI struct {
	serverManager *server.ServerManager
	musicLibrary  *models.MusicLibrary
	serverStatus  *ServerStatusBar
	songList      *SongListView
	libraryStatus *LibraryStatusBar
	content       *fyne.Container
}

// NewMainUI creates a new main UI instance
func NewMainUI() *MainUI {
	ui := &MainUI{}
	ui.initialize()
	return ui
}

// initialize sets up the UI components
func (ui *MainUI) initialize() {
	// Create a real ServerManager instance
	ui.serverManager = server.NewServerManager()
	
	// Create a MusicLibrary instance
	ui.musicLibrary = models.NewMusicLibrary()
	
	// Connect the MusicLibrary to the ServerManager
	ui.serverManager.SetMusicLibrary(ui.musicLibrary)
	
	// Create UI components connected to the real server manager and music library
	ui.serverStatus = NewServerStatusBar(ui.serverManager)
	ui.songList = NewSongListView(ui.musicLibrary)
	ui.libraryStatus = NewLibraryStatusBar(ui.musicLibrary, ui.serverManager)

	// Create the main layout matching ContentView.swift structure:
	// VStack(spacing: 0) {
	//   ServerStatusBar().padding().background(Color.gray.opacity(0.1))
	//   HStack { SongListView().frame(minWidth: 400) }.frame(maxHeight: .infinity)
	//   LibraryStatusBar().padding().background(Color.gray.opacity(0.1))
	// }

	ui.content = container.NewVBox(
		// Server status bar - more compact
		ui.serverStatus.GetContent(),
		
		widget.NewSeparator(), // Visual separation
		
		// Main content area - song list (expandable)
		ui.songList.GetContent(),
		
		widget.NewSeparator(), // Visual separation
		
		// Library status bar - more compact  
		ui.libraryStatus.GetContent(),
	)
}

// LoadMusicLibrary loads the music library from the configured folder
func (ui *MainUI) LoadMusicLibrary() {
	// Load config to get music folder path
	config, err := models.LoadConfig()
	if err != nil {
		log.Printf("❌ Failed to load config for music library: %v", err)
		return
	}
	
	// Check if music folder is configured
	if config.MusicFolder == "" {
		log.Println("⚠️ No music folder configured")
		return
	}
	
	log.Printf("🎵 Loading music library from: %s", config.MusicFolder)
	
	// Use SelectFolder which sets the path and scans the library
	go ui.musicLibrary.SelectFolder(config.MusicFolder)
	
	// Automatically start the server after music library loading
	go ui.AutoStartServer()
}

// AutoStartServer automatically starts the server and generates QR code for seamless UX
func (ui *MainUI) AutoStartServer() {
	// Wait a moment for music library to start loading
	log.Println("🚀 Auto-starting server for seamless experience...")
	
	// Start the server automatically
	err := ui.serverManager.StartServer()
	if err != nil {
		log.Printf("❌ Auto-start server failed: %v", err)
		return
	}
	
	log.Println("✅ Server auto-started successfully!")
	
	// Automatically generate and show QR code
	ui.serverStatus.AutoGenerateQR()
}

// GetContent returns the main UI content for display
func (ui *MainUI) GetContent() fyne.CanvasObject {
	return ui.content
}

// SetMainWindow sets the main window reference for dialog components
func (ui *MainUI) SetMainWindow(window fyne.Window) {
	ui.songList.SetParentWindow(window)
}

// Cleanup handles application termination cleanup
func (ui *MainUI) Cleanup() {
	// Ensure server is stopped before app terminates (like SwiftUI onReceive)
	if ui.serverManager != nil {
		ui.serverManager.Cleanup()
	}
} 