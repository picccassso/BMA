package ui

import (
	"fmt"
	"log"
	"os"
	"time"

	"fyne.io/fyne/v2"
	"fyne.io/fyne/v2/canvas"
	"fyne.io/fyne/v2/container"
	"fyne.io/fyne/v2/theme"
	"fyne.io/fyne/v2/widget"

	"bma-go/internal/server"
)

// ServerStatusBar represents the server status and controls, equivalent to ServerStatusBar.swift
type ServerStatusBar struct {
	serverManager   *server.ServerManager
	serverButton    *widget.Button
	serverLabel     *widget.Label
	qrButton        *widget.Button
	tailscaleLabel  *widget.Label
	refreshButton   *widget.Button
	content         *fyne.Container
	qrWindow        fyne.Window  // Track QR window for auto-hiding
}

// NewServerStatusBar creates a new server status bar
func NewServerStatusBar(serverManager *server.ServerManager) *ServerStatusBar {
	bar := &ServerStatusBar{
		serverManager: serverManager,
	}
	bar.initialize()
	bar.startPeriodicUpdates()
	return bar
}

// initialize sets up the UI components
func (bar *ServerStatusBar) initialize() {
	// Server control button - now shows status instead of manual control
	bar.serverButton = widget.NewButton("Server Status", bar.toggleServer)

	// Server status label - fixed width to prevent layout changes
	bar.serverLabel = widget.NewLabel("Server: Auto-starting...")
	bar.serverLabel.Resize(fyne.NewSize(200, 30)) // Fixed width and height

	// QR code generation button - now for manual regeneration
	bar.qrButton = widget.NewButton("New QR Code", bar.generateQR)
	bar.qrButton.Disable() // Disabled until server starts

	// Tailscale status label - fixed width
	bar.tailscaleLabel = widget.NewLabel("Tailscale: Checking...")
	bar.tailscaleLabel.Resize(fyne.NewSize(150, 30)) // Fixed width and height

	// Refresh button for manual status updates
	bar.refreshButton = widget.NewButton("Refresh", bar.refreshStatus)

	// Layout with consistent spacing - no separators to avoid layout shifts
	bar.content = container.NewHBox(
		bar.serverButton,
		container.NewBorder(nil, nil, nil, nil, bar.serverLabel), // Stable container
		bar.qrButton,
		container.NewBorder(nil, nil, nil, nil, bar.tailscaleLabel), // Stable container
		bar.refreshButton,
	)

	// Initial status update
	bar.updateUI()
	
	// Start monitoring for device connections to auto-hide QR codes
	bar.startDeviceMonitoring()
}

// toggleServer starts or stops the HTTP server
func (bar *ServerStatusBar) toggleServer() {
	if bar.serverManager.IsRunning {
		// Stop server
		bar.serverManager.StopServer()
		bar.serverButton.SetText("Server Status")
		bar.serverLabel.SetText("Server: Stopped")
		bar.qrButton.Disable()
		
		// Close QR window if open
		if bar.qrWindow != nil {
			bar.qrWindow.Close()
			bar.qrWindow = nil
		}
	} else {
		// Start server manually (in case auto-start failed)
		go func() {
			err := bar.serverManager.StartServer()
			if err != nil {
				bar.serverLabel.SetText(fmt.Sprintf("Error: %v", err))
			} else {
				bar.serverButton.SetText("Stop Server")
				bar.updateServerStatus()
				bar.qrButton.Enable()
				
				// Auto-generate QR code
				go bar.AutoGenerateQR()
			}
		}()
		bar.serverLabel.SetText("Server: Starting...")
	}
}

// generateQR shows the QR code for pairing
func (bar *ServerStatusBar) generateQR() {
	if !bar.serverManager.IsRunning {
		return
	}

	log.Println("🔑 Generating QR code for device pairing...")

	// Generate QR code using ServerManager
	qrBytes, jsonData, err := bar.serverManager.GenerateQRCode()
	if err != nil {
		log.Printf("❌ QR generation failed: %v", err)
		bar.showErrorDialog(fmt.Sprintf("QR Generation Error: %v", err))
		return
	}

	// Debug: Check if we actually have QR code data
	log.Printf("🔍 QR code generated: %d bytes", len(qrBytes))
	if len(qrBytes) == 0 {
		log.Println("❌ QR code is empty!")
		bar.showErrorDialog("QR code generation returned empty data")
		return
	}

	// Show QR code dialog
	bar.showQRCodeDialog(qrBytes, jsonData)
}

// showQRCodeDialog displays the QR code image and pairing information in a resizable window
func (bar *ServerStatusBar) showQRCodeDialog(qrBytes []byte, jsonData string) {
	app := fyne.CurrentApp()
	qrWindow := app.NewWindow("Device Pairing QR Code")
	bar.qrWindow = qrWindow  // Store window reference for auto-hiding
	
	qrWindow.SetCloseIntercept(func() {
		bar.qrWindow = nil  // Clear reference when window closes
		qrWindow.Close()
	})
	
	// Create QR code image with Mac app's approach
	qrResource := fyne.NewStaticResource("qr_code.png", qrBytes)
	qrImage := canvas.NewImageFromResource(qrResource)
	
	// Key settings from Mac app: no interpolation, fixed size
	qrImage.FillMode = canvas.ImageFillOriginal // Don't scale/stretch - keep original
	qrImage.Resize(fyne.NewSize(150, 150))      // Even smaller 150x150 size
	
	// Simple white card background like Mac app
	qrCard := widget.NewCard("", "", container.NewCenter(qrImage))
	qrCard.Resize(fyne.NewSize(180, 180)) // Adjust card size accordingly
	
	// Center the QR code card in the window
	centeredQR := container.NewCenter(qrCard)
	
	// Compact instructions at the bottom
	instructions := widget.NewRichTextFromMarkdown(
		"**1.** Open BMA Android app → 'Scan QR Code'  **2.** Point camera at QR code  **3.** Done!\n" +
		"💡 **QR code is fixed at optimal scanning size (150×150px)**")
	instructions.Wrapping = fyne.TextWrapWord
	
	// Buttons in a compact row
	copyBtn := widget.NewButton("Copy JSON", func() {
		qrWindow.Clipboard().SetContent(jsonData)
		log.Println("🔑 QR code JSON copied to clipboard")
	})
	
	saveBtn := widget.NewButton("Save PNG", func() {
		err := os.WriteFile("qr_code.png", qrBytes, 0644)
		if err != nil {
			log.Printf("❌ Failed to save QR: %v", err)
		} else {
			log.Println("✅ QR code saved as qr_code.png")
		}
	})
	
	refreshBtn := widget.NewButton("New QR", func() {
		qrWindow.Close()
		bar.generateQR()
	})
	
	closeBtn := widget.NewButton("Close", func() {
		qrWindow.Close()
	})
	closeBtn.Importance = widget.HighImportance
	
	buttonRow := container.NewHBox(
		copyBtn,
		widget.NewSeparator(),
		saveBtn, 
		widget.NewSeparator(),
		refreshBtn,
		widget.NewSeparator(),
		closeBtn,
	)
	
	// Layout like Mac app: QR code centered, controls at bottom
	bottomPanel := container.NewVBox(
		widget.NewSeparator(),
		instructions,
		widget.NewSeparator(),
		buttonRow,
	)
	
	// Main layout: Fixed QR code in center
	content := container.NewBorder(
		nil,          // top
		bottomPanel,  // bottom (compact)
		nil,          // left  
		nil,          // right
		centeredQR,   // center - QR code at fixed optimal size
	)
	
	qrWindow.SetContent(content)
	qrWindow.Resize(fyne.NewSize(380, 350)) // Smaller window for compact QR code
	qrWindow.SetFixedSize(true)             // Fixed size like Mac app
	qrWindow.Show()
	
	log.Println("✅ Mac-style QR code window displayed")
}

// showErrorDialog displays error messages
func (bar *ServerStatusBar) showErrorDialog(message string) {
	content := widget.NewCard("Error", "", 
		container.NewVBox(
			widget.NewIcon(theme.ErrorIcon()),
			widget.NewLabel(message),
			widget.NewButton("OK", func() {}),
		),
	)

	dialog := widget.NewModalPopUp(content, fyne.CurrentApp().Driver().AllWindows()[0].Canvas())
	dialog.Show()

	go func() {
		time.Sleep(5 * time.Second)
		dialog.Hide()
	}()
}

// refreshStatus manually refreshes all status information
func (bar *ServerStatusBar) refreshStatus() {
	bar.serverManager.RefreshTailscaleStatus()
	bar.updateUI()
	
	bar.tailscaleLabel.SetText("Tailscale: Refreshing...")
	go func() {
		time.Sleep(2 * time.Second)
		bar.updateTailscaleStatus()
	}()
}

// updateUI updates all UI elements based on current server state
func (bar *ServerStatusBar) updateUI() {
	bar.updateServerStatus()
	bar.updateTailscaleStatus()
}

// updateServerStatus updates the server status display
func (bar *ServerStatusBar) updateServerStatus() {
	if bar.serverManager.IsRunning {
		bar.serverButton.SetText("Stop Server")
		// Show clean status instead of long URL
		if bar.serverManager.IsTailscaleConfigured() {
			bar.serverLabel.SetText("Server: Running")
		} else {
			bar.serverLabel.SetText("Server: Running (Local)")
		}
		bar.qrButton.Enable()
	} else {
		bar.serverButton.SetText("Start Server")
		bar.serverLabel.SetText("Server: Stopped")
		bar.qrButton.Disable()
	}
}

// updateTailscaleStatus updates the Tailscale status display
func (bar *ServerStatusBar) updateTailscaleStatus() {
	if bar.serverManager.IsTailscaleConfigured() {
		bar.tailscaleLabel.SetText("Tailscale: Connected")
	} else {
		bar.tailscaleLabel.SetText("Tailscale: Not detected")
	}
}

// startPeriodicUpdates starts background UI updates
func (bar *ServerStatusBar) startPeriodicUpdates() {
	go func() {
		ticker := time.NewTicker(5 * time.Second)
		defer ticker.Stop()

		for range ticker.C {
			bar.updateTailscaleStatus()
		}
	}()
}

// GetContent returns the UI content for display
func (bar *ServerStatusBar) GetContent() fyne.CanvasObject {
	return bar.content
}

// AutoGenerateQR automatically generates and shows QR code for seamless UX
func (bar *ServerStatusBar) AutoGenerateQR() {
	// Wait a moment for server to fully start
	time.Sleep(1 * time.Second)
	
	if !bar.serverManager.IsRunning {
		log.Println("⚠️ Cannot auto-generate QR: server not running")
		return
	}

	log.Println("🔑 Auto-generating QR code for seamless device pairing...")

	// Update UI to reflect server running state
	bar.updateServerStatus()

	// Generate QR code using ServerManager
	qrBytes, jsonData, err := bar.serverManager.GenerateQRCode()
	if err != nil {
		log.Printf("❌ Auto QR generation failed: %v", err)
		return
	}

	// Debug: Check if we actually have QR code data
	log.Printf("🔍 Auto-generated QR code: %d bytes", len(qrBytes))
	if len(qrBytes) == 0 {
		log.Println("❌ Auto-generated QR code is empty!")
		return
	}

	// Automatically show QR code dialog
	bar.showQRCodeDialog(qrBytes, jsonData)
	log.Println("✅ QR code auto-displayed - ready for device pairing!")
}

// startDeviceMonitoring monitors for device connections to auto-hide QR codes
func (bar *ServerStatusBar) startDeviceMonitoring() {
	log.Println("📱 [DEBUG] Starting device monitoring for QR auto-hide")
	
	go func() {
		var lastDeviceCount int
		
		for {
			time.Sleep(2 * time.Second) // Check every 2 seconds
			
			// Get current device count
			connectedDevices := bar.serverManager.GetConnectedDevices()
			currentDeviceCount := len(connectedDevices)
			
			// If device count increased (new device connected) and QR window is open
			if currentDeviceCount > lastDeviceCount && bar.qrWindow != nil {
				log.Printf("📱 [QR AUTO-HIDE] Device connected! Closing QR code window (devices: %d → %d)", lastDeviceCount, currentDeviceCount)
				
				// Close QR window on UI thread
				go func() {
					if bar.qrWindow != nil {
						bar.qrWindow.Close()
						bar.qrWindow = nil
					}
				}()
			}
			
			lastDeviceCount = currentDeviceCount
		}
	}()
} 