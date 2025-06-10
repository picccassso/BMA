package main

import (
	"fmt"
	"log"
	"os"
	"os/signal"
	"strings"
	"syscall"

	"bma-cli/internal/models"
	"bma-cli/internal/server"
)

func main() {
	log.Println("🚀 Starting BMA CLI (Basic Music App) - Headless Server Edition")

	// Load configuration
	config, err := models.LoadConfig()
	if err != nil {
		log.Printf("⚠️ Error loading config: %v", err)
		config = &models.Config{SetupComplete: false}
	}

	// Check if setup is complete
	if !config.SetupComplete {
		log.Println("🔧 First run detected - starting setup server")
		startSetupServer(config)
	} else {
		log.Println("✅ Setup complete - starting main streaming server")
		startMainServer(config)
	}
}

func startSetupServer(config *models.Config) {
	log.Println("🌐 Starting setup web server at http://localhost:8080/setup")
	
	// Create setup server
	setupServer := server.NewSetupServer(config)
	
	// Handle graceful shutdown
	c := make(chan os.Signal, 1)
	signal.Notify(c, os.Interrupt, syscall.SIGTERM)
	
	go func() {
		<-c
		log.Println("🛑 Received shutdown signal, stopping setup server...")
		setupServer.Shutdown()
		os.Exit(0)
	}()
	
	fmt.Println("\n" + strings.Repeat("=", 50))
	fmt.Println("🎵 BMA CLI Setup")
	fmt.Println(strings.Repeat("=", 50))
	fmt.Println("Setup server is running at: http://localhost:8080/setup")
	fmt.Println("Open this URL in your web browser to configure BMA CLI")
	fmt.Println(strings.Repeat("=", 50) + "\n")
	
	// Start the setup server (this will block)
	if err := setupServer.Start(); err != nil {
		log.Fatalf("❌ Failed to start setup server: %v", err)
	}
}

func startMainServer(config *models.Config) {
	log.Println("🌐 Starting main streaming server")
	
	// Create music library
	musicLibrary := models.NewMusicLibrary()
	
	// Load music from configured folder
	if config.MusicFolder != "" {
		log.Printf("📁 Loading music from: %s", config.MusicFolder)
		musicLibrary.SelectFolder(config.MusicFolder)
	}
	
	// Create main server
	mainServer := server.NewMusicServer(config, musicLibrary)
	
	// Handle graceful shutdown
	c := make(chan os.Signal, 1)
	signal.Notify(c, os.Interrupt, syscall.SIGTERM)
	
	go func() {
		<-c
		log.Println("🛑 Received shutdown signal, stopping music server...")
		mainServer.Shutdown()
		os.Exit(0)
	}()
	
	fmt.Println("\n" + strings.Repeat("=", 60))
	fmt.Println("🎵 BMA CLI Music Server")
	fmt.Println(strings.Repeat("=", 60))
	fmt.Printf("Music Library: %s\n", config.MusicFolder)
	fmt.Printf("Server running at: http://localhost:8080\n")
	if config.TailscaleIP != "" {
		fmt.Printf("Tailscale access: http://%s:8080\n", config.TailscaleIP)
	}
	fmt.Println("Ready for connections from BMA mobile apps")
	fmt.Println(strings.Repeat("=", 60) + "\n")
	
	// Start the music server (this will block)
	if err := mainServer.Start(); err != nil {
		log.Fatalf("❌ Failed to start music server: %v", err)
	}
}