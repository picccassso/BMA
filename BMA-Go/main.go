package main

import (
	"log"

	"fyne.io/fyne/v2"
	"fyne.io/fyne/v2/app"

	"bma-go/internal/ui"
)

func main() {
	log.Println("🚀 Starting BMA (Basic Music App) - Go+Fyne Edition")

	// Create Fyne application
	fyneApp := app.New()

	// Create and show main window
	mainWindow := fyneApp.NewWindow("BMA - Basic Music App")
	mainWindow.Resize(fyne.NewSize(450, 320))  // More compact size
	mainWindow.SetFixedSize(true)              // Fixed size for neat appearance
	
	// Initialize the main UI
	mainUI := ui.NewMainUI()
	
	// Set the main window reference for dialogs
	mainUI.SetMainWindow(mainWindow)
	
	mainWindow.SetContent(mainUI.GetContent())

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