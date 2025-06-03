# BMA - Basic Music App (macOS)

A minimalist music player app for macOS that serves as your personal Spotify alternative. Stream your MP3 collection from your Mac to other devices.

## Features

- Local MP3 file playback
- HTTP server for streaming to other devices (port 8008)
- Minimalist UI design
- Basic playback controls (play/pause/stop)
- Volume control
- Song progress tracking
- Network IP display for easy mobile connection

## Requirements

- macOS 13.0 or later
- Swift 5.9 or later
- Xcode 15.0 or later (recommended)

## Building

### Option 1: Using Xcode (Recommended)
1. Open Terminal and navigate to the BMA-macOS directory
2. Run `open Package.swift` to open in Xcode
3. Wait for packages to resolve
4. Click the Run button or press Cmd+R

### Option 2: Using Swift CLI
1. Navigate to the BMA-macOS directory
2. Run `swift build`
3. Run `.build/debug/BMA`

## Usage

1. Launch the app
2. Click "Select Folder" to choose your music directory
3. The app will scan for MP3 files
4. Click "Start Server" to enable streaming
5. Note the displayed IP address (e.g., http://192.168.1.100:8008)
6. Use this IP address to connect from your Android device

## API Endpoints

- `GET /health` - Server health check
- `GET /info` - Server information and URLs
- `GET /songs` - List all available songs
- `GET /stream/:songId` - Stream a specific song

## Testing the Server

Use the included test script:
```bash
./test-server.sh
```

Or manually test endpoints:
```bash
curl http://localhost:8008/health
curl http://localhost:8008/songs
``` 