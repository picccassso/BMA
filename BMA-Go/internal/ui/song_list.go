package ui

import (
	"fyne.io/fyne/v2"
	"fyne.io/fyne/v2/container"
	"fyne.io/fyne/v2/widget"
	
	"bma-go/internal/models"
)

// SongListView displays the music library with album folder organization
type SongListView struct {
	musicLibrary  *models.MusicLibrary
	content       *fyne.Container
	folderButton  *widget.Button
	songList      *widget.List
	noMusicLabel  *widget.Label
	parentWindow  fyne.Window
}

// NewSongListView creates a new song list view
func NewSongListView(musicLibrary *models.MusicLibrary) *SongListView {
	slv := &SongListView{
		musicLibrary: musicLibrary,
	}
	slv.initialize()
	return slv
}

// initialize sets up the song list view components
func (slv *SongListView) initialize() {
	// Folder selection button
	slv.folderButton = widget.NewButton("Select Music Folder", func() {
		slv.onSelectFolder()
	})

	// No music selected label
	slv.noMusicLabel = widget.NewLabel("No music folder selected.\nClick 'Select Music Folder' to choose your music directory.")
	slv.noMusicLabel.Alignment = fyne.TextAlignCenter

	// Song list (will be populated with albums and songs)
	slv.songList = widget.NewList(
		func() int {
			// TODO: Return number of items (albums + songs)
			return 0
		},
		func() fyne.CanvasObject {
			// TODO: Create album folder or song item template
			return widget.NewLabel("Album/Song Item")
		},
		func(id widget.ListItemID, obj fyne.CanvasObject) {
			// TODO: Update album folder or song item
			if label, ok := obj.(*widget.Label); ok {
				label.SetText("Sample Item")
			}
		},
	)

	// Layout components
	slv.content = container.NewVBox(
		slv.folderButton,
		widget.NewSeparator(),
		container.NewMax(
			// Show either the song list or the "no music" message
			slv.noMusicLabel,
			// slv.songList, // Will be shown when music is loaded
		),
	)
}

// GetContent returns the song list view content
func (slv *SongListView) GetContent() fyne.CanvasObject {
	return slv.content
}

// SetParentWindow sets the parent window for dialogs
func (slv *SongListView) SetParentWindow(window fyne.Window) {
	slv.parentWindow = window
}

// onSelectFolder handles folder selection
func (slv *SongListView) onSelectFolder() {
	if slv.parentWindow == nil {
		slv.noMusicLabel.SetText("Error: No parent window set for dialog")
		return
	}
	
	// Open the folder selection dialog using the MusicLibrary
	slv.musicLibrary.ShowFolderSelectionDialog(slv.parentWindow)
}

// LoadMusicLibrary loads and displays the music library
func (slv *SongListView) LoadMusicLibrary(folderPath string) {
	// TODO: Implement music library loading
	// This will scan the folder, organize by albums, and populate the list
	// Will be implemented in Phase 3
}

// RefreshLibrary refreshes the current music library display
func (slv *SongListView) RefreshLibrary() {
	// TODO: Implement library refresh
	// This will re-scan and update the display
} 