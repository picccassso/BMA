package main

import (
	"log"

	"fyne.io/fyne/v2"
	"fyne.io/fyne/v2/app"

	"bma-go/internal/models"
	"bma-go/internal/ui"
)

func main() {
	log.Println("🚀 Starting BMA (Basic Music App) - Go+Fyne Edition")

	// Load configuration
	config, err := models.LoadConfig()
	if err != nil {
		log.Printf("⚠️ Error loading config: %v", err)
		config = &models.Config{SetupComplete: false}
	}

	// Create Fyne application
	fyneApp := app.New()

	// Check if setup is complete
	if !config.SetupComplete {
		log.Println("🔧 First run detected - starting setup wizard")
		showSetupWizard(fyneApp, config)
	} else {
		log.Println("✅ Setup complete - starting main application")
		showMainApplication(fyneApp, config)
	}
}

func showSetupWizard(fyneApp fyne.App, config *models.Config) {
	// Create setup window
	setupWindow := fyneApp.NewWindow("BMA Setup")
	setupWindow.Resize(fyne.NewSize(600, 500))
	setupWindow.SetFixedSize(true)
	
	// Create main window (but don't show it yet)
	mainWindow := fyneApp.NewWindow("BMA - Basic Music App")
	mainWindow.Resize(fyne.NewSize(450, 320))
	mainWindow.SetFixedSize(true)
	
	// Initialize the main UI
	mainUI := ui.NewMainUI()
	mainUI.SetMainWindow(mainWindow)
	mainWindow.SetContent(mainUI.GetContent())
	
	// Handle app termination cleanup
	mainWindow.SetCloseIntercept(func() {
		log.Println("🛑 App terminating - ensuring server shutdown...")
		mainUI.Cleanup()
		log.Println("✅ App termination cleanup completed")
		fyneApp.Quit()
	})
	
	// Create setup wizard with transition callback
	wizard := ui.NewSetupWizard(config, func() {
		// On setup completion, hide setup window and show main app
		log.Println("✅ Setup completed - transitioning to main application")
		
		// Load the music library now that setup is complete
		mainUI.LoadMusicLibrary()
		
		setupWindow.Hide()
		mainWindow.Show()
	})
	
	wizard.SetWindow(setupWindow)
	setupWindow.SetContent(wizard.GetContent())
	
	// Show setup window and run (this is the only ShowAndRun call)
	setupWindow.ShowAndRun()
}

func showMainApplication(fyneApp fyne.App, config *models.Config) {
	// Create and show main window
	mainWindow := fyneApp.NewWindow("BMA - Basic Music App")
	mainWindow.Resize(fyne.NewSize(450, 320))  // More compact size
	mainWindow.SetFixedSize(true)              // Fixed size for neat appearance
	
	// Initialize the main UI
	mainUI := ui.NewMainUI()
	
	// Set the main window reference for dialogs
	mainUI.SetMainWindow(mainWindow)
	
	mainWindow.SetContent(mainUI.GetContent())

	// Load the music library from config
	mainUI.LoadMusicLibrary()

	// Handle app termination cleanup
	mainWindow.SetCloseIntercept(func() {
		log.Println("🛑 App terminating - ensuring server shutdown...")
		mainUI.Cleanup()
		log.Println("✅ App termination cleanup completed")
		fyneApp.Quit()
	})

	// Show window and run
	mainWindow.ShowAndRun()
} 