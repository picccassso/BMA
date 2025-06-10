package ui

import (
	"bytes"
	"image"
	_ "image/png"
	"log"
	"os"
	"os/exec"
	"strings"

	"fyne.io/fyne/v2"
	"fyne.io/fyne/v2/canvas"
	"fyne.io/fyne/v2/container"
	"fyne.io/fyne/v2/dialog"
	"fyne.io/fyne/v2/widget"

	"bma-go/internal/models"
)

// WelcomeStep - Step 1: Welcome screen
type WelcomeStep struct {
	content fyne.CanvasObject
}

func NewWelcomeStep() *WelcomeStep {
	return &WelcomeStep{}
}

func (s *WelcomeStep) GetContent() fyne.CanvasObject {
	if s.content == nil {
		title := widget.NewLabelWithStyle("Hello! 👋", fyne.TextAlignCenter, fyne.TextStyle{Bold: true})
		
		subtitle := widget.NewLabelWithStyle("Welcome to BMA - Basic Music App", fyne.TextAlignCenter, fyne.TextStyle{})
		
		description := widget.NewLabelWithStyle(
			"Let's get you set up to stream your music library.\n\nThis quick setup will help you:",
			fyne.TextAlignCenter, 
			fyne.TextStyle{},
		)
		
		features := widget.NewLabelWithStyle(
			"• Install Tailscale for remote access\n• Download the Android app\n• Select your music folder\n• Automatic server startup & pairing",
			fyne.TextAlignLeading,
			fyne.TextStyle{},
		)
		
		s.content = container.NewVBox(
			widget.NewCard("", "", container.NewVBox(
				title,
				subtitle,
				widget.NewSeparator(),
				description,
				features,
			)),
		)
	}
	return s.content
}

func (s *WelcomeStep) GetTitle() string { return "Welcome" }
func (s *WelcomeStep) OnEnter()         {}
func (s *WelcomeStep) OnExit()          {}
func (s *WelcomeStep) CanContinue() bool { return true }
func (s *WelcomeStep) GetNextAction() func() { return nil }

// TailscaleStep - Step 2: Tailscale installation
type TailscaleStep struct {
	content        fyne.CanvasObject
	isInstalled    bool
	statusLabel    *widget.Label
	downloadButton *widget.Button
	recheckButton  *widget.Button
	onStateChange  func() // Callback when installation state changes
}

func NewTailscaleStep() *TailscaleStep {
	return &TailscaleStep{}
}

func (s *TailscaleStep) GetContent() fyne.CanvasObject {
	if s.content == nil {
		title := widget.NewLabelWithStyle("Tailscale Setup", fyne.TextAlignCenter, fyne.TextStyle{Bold: true})
		
		description := widget.NewLabelWithStyle(
			"Tailscale enables secure remote access to your music server.\nInstall Tailscale to stream your music from anywhere.",
			fyne.TextAlignCenter,
			fyne.TextStyle{},
		)
		
		s.statusLabel = widget.NewLabel("Checking Tailscale installation...")
		
		s.downloadButton = widget.NewButton("Open Download Page", func() {
			// Open browser to Tailscale download page
			exec.Command("open", "https://tailscale.com/download").Start()
		})
		
		s.recheckButton = widget.NewButton("I've Downloaded It", func() {
			s.checkTailscaleInstallation()
		})
		
		s.content = container.NewVBox(
			widget.NewCard("", "", container.NewVBox(
				title,
				description,
				widget.NewSeparator(),
				s.statusLabel,
				container.NewHBox(s.downloadButton, s.recheckButton),
			)),
		)
	}
	return s.content
}

func (s *TailscaleStep) checkTailscaleInstallation() {
	// Check multiple ways Tailscale might be installed
	s.isInstalled = s.isTailscaleInstalled()
	
	if s.isInstalled {
		// Tailscale found
		s.statusLabel.SetText("✅ Tailscale is installed!")
		s.downloadButton.Hide()
		s.recheckButton.Hide()
	} else {
		// Tailscale not found
		s.statusLabel.SetText("❌ Tailscale not found. Please download and install it.")
		s.downloadButton.Show()
		s.recheckButton.Show()
	}
	
	// Notify wizard that state has changed
	if s.onStateChange != nil {
		s.onStateChange()
	}
}

// isTailscaleInstalled checks for Tailscale installation in multiple ways
func (s *TailscaleStep) isTailscaleInstalled() bool {
	// Method 1: Check if running in Flatpak and use flatpak-spawn
	if s.isRunningInFlatpak() {
		if s.checkTailscaleViaFlatpak() {
			return true
		}
	}
	
	// Method 2: Check if tailscale CLI is in PATH
	if _, err := exec.LookPath("tailscale"); err == nil {
		return true
	}
	
	// Method 3: Check for macOS app installation
	if s.checkMacOSApp() {
		return true
	}
	
	// Method 4: Check for Homebrew installation
	if s.checkHomebrewInstallation() {
		return true
	}
	
	return false
}

// checkMacOSApp checks if Tailscale is installed as a macOS app
func (s *TailscaleStep) checkMacOSApp() bool {
	// Check for standard macOS app installation
	appPaths := []string{
		"/Applications/Tailscale.app/Contents/MacOS/Tailscale",
		"/System/Applications/Tailscale.app/Contents/MacOS/Tailscale",
	}
	
	for _, path := range appPaths {
		if _, err := os.Stat(path); err == nil {
			return true
		}
	}
	
	return false
}

// checkHomebrewInstallation checks for Homebrew-installed tailscale
func (s *TailscaleStep) checkHomebrewInstallation() bool {
	brewPaths := []string{
		"/usr/local/bin/tailscale",
		"/opt/homebrew/bin/tailscale",
	}
	
	for _, path := range brewPaths {
		if _, err := os.Stat(path); err == nil {
			return true
		}
	}
	
	return false
}

// isRunningInFlatpak checks if the application is running inside a Flatpak sandbox
func (s *TailscaleStep) isRunningInFlatpak() bool {
	cmd := exec.Command("sh", "-c", "echo $FLATPAK_ID")
	output, err := cmd.Output()
	return err == nil && strings.TrimSpace(string(output)) != ""
}

// checkTailscaleViaFlatpak checks if Tailscale is accessible via flatpak-spawn --host
func (s *TailscaleStep) checkTailscaleViaFlatpak() bool {
	// Test if flatpak-spawn can access tailscale on host
	cmd := exec.Command("flatpak-spawn", "--host", "tailscale", "version")
	err := cmd.Run()
	return err == nil
}

// SetStateChangeCallback sets the callback for when installation state changes
func (s *TailscaleStep) SetStateChangeCallback(callback func()) {
	s.onStateChange = callback
}

func (s *TailscaleStep) GetTitle() string { return "Tailscale Setup" }
func (s *TailscaleStep) OnEnter() {
	s.checkTailscaleInstallation()
}
func (s *TailscaleStep) OnExit()          {}
func (s *TailscaleStep) CanContinue() bool { return s.isInstalled }
func (s *TailscaleStep) GetNextAction() func() { return nil }

// AndroidAppStep - Step 3: Android app download
type AndroidAppStep struct {
	content fyne.CanvasObject
}

func NewAndroidAppStep() *AndroidAppStep {
	return &AndroidAppStep{}
}

func (s *AndroidAppStep) GetContent() fyne.CanvasObject {
	if s.content == nil {
		title := widget.NewLabelWithStyle("Download Android App", fyne.TextAlignCenter, fyne.TextStyle{Bold: true})
		
		description := widget.NewLabelWithStyle(
			"Scan the QR code below with your Android device\nto download the BMA Android app from GitHub.",
			fyne.TextAlignCenter,
			fyne.TextStyle{},
		)
		
		// Generate QR code for GitHub repository
		qrCode := s.createGitHubQRCode()
		
		instructions := widget.NewLabelWithStyle(
			"1. Open your camera app\n2. Point it at the QR code\n3. Tap the notification to open GitHub\n4. Download the APK file",
			fyne.TextAlignLeading,
			fyne.TextStyle{},
		)
		
		s.content = container.NewVBox(
			widget.NewCard("", "", container.NewVBox(
				title,
				description,
				widget.NewSeparator(),
				container.NewCenter(qrCode),
				widget.NewSeparator(),
				instructions,
			)),
		)
	}
	return s.content
}

func (s *AndroidAppStep) createGitHubQRCode() fyne.CanvasObject {
	// Generate QR code for GitHub repository
	qrBytes, err := models.GenerateSimpleQR("https://github.com/picccassso/BasicMusicStreamingApp", 200)
	if err != nil {
		log.Printf("Error generating QR code: %v", err)
		return widget.NewLabel("📱 QR Code Generation Failed\n(Visit GitHub manually)")
	}
	
	// Convert bytes to image
	img, _, err := image.Decode(bytes.NewReader(qrBytes))
	if err != nil {
		log.Printf("Error decoding QR image: %v", err)
		return widget.NewLabel("📱 QR Code Display Failed\n(Visit GitHub manually)")
	}
	
	// Create canvas image
	qrImage := canvas.NewImageFromImage(img)
	qrImage.FillMode = canvas.ImageFillOriginal
	qrImage.SetMinSize(fyne.NewSize(200, 200))
	
	return qrImage
}

func (s *AndroidAppStep) GetTitle() string { return "Android App" }
func (s *AndroidAppStep) OnEnter()         {}
func (s *AndroidAppStep) OnExit()          {}
func (s *AndroidAppStep) CanContinue() bool { return true }
func (s *AndroidAppStep) GetNextAction() func() { return nil }

// PairingStep - Step 4: Device pairing
type PairingStep struct {
	content fyne.CanvasObject
}

func NewPairingStep() *PairingStep {
	return &PairingStep{}
}

func (s *PairingStep) GetContent() fyne.CanvasObject {
	if s.content == nil {
		title := widget.NewLabelWithStyle("Pair Your Device", fyne.TextAlignCenter, fyne.TextStyle{Bold: true})
		
		description := widget.NewLabelWithStyle(
			"Scan this QR code with the BMA Android app\nto pair your device with this music server.",
			fyne.TextAlignCenter,
			fyne.TextStyle{},
		)
		
		// Generate pairing QR code
		qrCode := s.createPairingQRCode()
		
		instructions := widget.NewLabelWithStyle(
			"1. Open the BMA app on your Android device\n2. Tap 'Scan QR Code'\n3. Point your camera at this code",
			fyne.TextAlignLeading,
			fyne.TextStyle{},
		)
		
		s.content = container.NewVBox(
			widget.NewCard("", "", container.NewVBox(
				title,
				description,
				widget.NewSeparator(),
				container.NewCenter(qrCode),
				widget.NewSeparator(),
				instructions,
			)),
		)
	}
	return s.content
}

func (s *PairingStep) createPairingQRCode() fyne.CanvasObject {
	// For setup wizard, create a simple placeholder QR code
	// In real implementation, this would generate actual pairing data
	placeholderData := `{"serverUrl": "http://localhost:8008", "token": "setup-placeholder", "expiresAt": "2024-12-31T23:59:59Z"}`
	
	qrBytes, err := models.GenerateSimpleQR(placeholderData, 200)
	if err != nil {
		log.Printf("Error generating pairing QR code: %v", err)
		return widget.NewLabel("🔗 Pairing QR Code\n(Will be generated when server starts)")
	}
	
	// Convert bytes to image
	img, _, err := image.Decode(bytes.NewReader(qrBytes))
	if err != nil {
		log.Printf("Error decoding pairing QR image: %v", err)
		return widget.NewLabel("🔗 Pairing QR Display Failed\n(Manual pairing available)")
	}
	
	// Create canvas image
	qrImage := canvas.NewImageFromImage(img)
	qrImage.FillMode = canvas.ImageFillOriginal
	qrImage.SetMinSize(fyne.NewSize(200, 200))
	
	return qrImage
}

func (s *PairingStep) GetTitle() string { return "Device Pairing" }
func (s *PairingStep) OnEnter()         {}
func (s *PairingStep) OnExit()          {}
func (s *PairingStep) CanContinue() bool { return true }
func (s *PairingStep) GetNextAction() func() { return nil }

// MusicLibraryStep - Step 5: Music folder selection
type MusicLibraryStep struct {
	content      fyne.CanvasObject
	config       *models.Config
	folderPath   string
	pathLabel    *widget.Label
	selectButton *widget.Button
	window       fyne.Window
	onStateChange func() // Callback when folder selection changes
}

func NewMusicLibraryStep(config *models.Config) *MusicLibraryStep {
	return &MusicLibraryStep{
		config: config,
	}
}

func (s *MusicLibraryStep) GetContent() fyne.CanvasObject {
	if s.content == nil {
		title := widget.NewLabelWithStyle("Select Music Library", fyne.TextAlignCenter, fyne.TextStyle{Bold: true})
		
		description := widget.NewLabelWithStyle(
			"Choose the folder containing your music files.\nThe app will scan for MP3 files in this folder and subfolders.",
			fyne.TextAlignCenter,
			fyne.TextStyle{},
		)
		
		s.pathLabel = widget.NewLabel("No folder selected")
		
		s.selectButton = widget.NewButton("Select Music Folder", func() {
			s.showFolderDialog()
		})
		
		s.content = container.NewVBox(
			widget.NewCard("", "", container.NewVBox(
				title,
				description,
				widget.NewSeparator(),
				s.pathLabel,
				s.selectButton,
			)),
		)
	}
	return s.content
}

func (s *MusicLibraryStep) showFolderDialog() {
	if s.window == nil {
		return
	}
	
	dialog.ShowFolderOpen(func(folder fyne.ListableURI, err error) {
		if err != nil || folder == nil {
			return
		}
		
		s.folderPath = folder.Path()
		s.pathLabel.SetText("Selected: " + s.folderPath)
		
		// Save to config
		s.config.SetMusicFolder(s.folderPath)
		
		// Notify wizard that state has changed
		if s.onStateChange != nil {
			s.onStateChange()
		}
	}, s.window)
}

func (s *MusicLibraryStep) SetWindow(window fyne.Window) {
	s.window = window
}

// SetStateChangeCallback sets the callback for when folder selection changes
func (s *MusicLibraryStep) SetStateChangeCallback(callback func()) {
	s.onStateChange = callback
}

func (s *MusicLibraryStep) GetTitle() string { return "Music Library" }
func (s *MusicLibraryStep) OnEnter()         {}
func (s *MusicLibraryStep) OnExit()          {}
func (s *MusicLibraryStep) CanContinue() bool { return s.folderPath != "" }
func (s *MusicLibraryStep) GetNextAction() func() { return nil }

// SetupCompleteStep - Step 6: Setup completion
type SetupCompleteStep struct {
	content fyne.CanvasObject
}

func NewSetupCompleteStep() *SetupCompleteStep {
	return &SetupCompleteStep{}
}

func (s *SetupCompleteStep) GetContent() fyne.CanvasObject {
	if s.content == nil {
		title := widget.NewLabelWithStyle("Setup Complete! 🎉", fyne.TextAlignCenter, fyne.TextStyle{Bold: true})
		
		description := widget.NewLabelWithStyle(
			"Your music server is ready to go!\n\nAfter clicking 'Start Streaming', the server will start automatically\nand a QR code will appear for device pairing.",
			fyne.TextAlignCenter,
			fyne.TextStyle{},
		)
		
		features := widget.NewLabelWithStyle(
			"✅ Tailscale installed for remote access\n✅ Android app ready for download\n✅ Music library selected\n✅ Automatic server startup & QR code generation",
			fyne.TextAlignLeading,
			fyne.TextStyle{},
		)
		
		s.content = container.NewVBox(
			widget.NewCard("", "", container.NewVBox(
				title,
				description,
				widget.NewSeparator(),
				features,
			)),
		)
	}
	return s.content
}

func (s *SetupCompleteStep) GetTitle() string { return "Setup Complete" }
func (s *SetupCompleteStep) OnEnter()         {}
func (s *SetupCompleteStep) OnExit()          {}
func (s *SetupCompleteStep) CanContinue() bool { return true }
func (s *SetupCompleteStep) GetNextAction() func() { return nil } 