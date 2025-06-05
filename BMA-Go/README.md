# BMA - Basic Music App (Go+Fyne Edition)

A cross-platform repackaging of the BMA (Basic Music App) - a personal Spotify alternative with self-hosted music streaming capabilities.

## Overview

This is a Go+Fyne implementation of the original macOS Swift version, providing identical functionality across Windows, Linux, and macOS platforms.

## Features

- **HTTP Music Server** (Port 8008) - Stream MP3 files to connected devices
- **QR Code Pairing** - Secure device pairing with Bearer token authentication
- **Tailscale Integration** - Remote access over encrypted Tailscale networks
- **Album Organization** - Smart folder-based album detection and organization
- **Cross-Platform GUI** - Native-looking interface on Windows, Linux, and macOS
- **Android Compatible** - Works with the existing BMA Android app

## Architecture

```
BMA-Go/
├── main.go                    # Application entry point
├── internal/
│   ├── server/               # HTTP server components
│   │   ├── manager.go        # Server management
│   │   ├── routes.go         # API endpoints
│   │   ├── auth.go          # Bearer token authentication
│   │   └── tailscale.go     # Tailscale integration
│   ├── models/              # Data models
│   │   ├── song.go          # Song metadata
│   │   ├── library.go       # Music library management
│   │   ├── device.go        # Connected device tracking
│   │   └── qr.go            # QR code generation
│   └── ui/                  # Fyne GUI components
│       ├── app.go           # Main UI coordinator
│       ├── server_status.go # Server controls
│       ├── song_list.go     # Music library view
│       └── library_status.go # Statistics bar
└── assets/                  # Resources and icons
```

## API Endpoints

- `GET /health` - Health check (public)
- `GET /info` - Server information (public)
- `POST /pair` - Device pairing (public)
- `POST /disconnect` - Device disconnect (authenticated)
- `GET /songs` - List all songs (authenticated)
- `GET /stream/:songId` - Stream MP3 file (authenticated)
- `GET /artwork/:songId` - Get album artwork (authenticated)

## Development Status

### Phase 1: Project Setup & Core Structure ✅
- [x] Go module initialization
- [x] Basic UI structure with Fyne
- [x] Component placeholders

### Phase 2: HTTP Server Implementation (TODO)
- [ ] Server manager with start/stop functionality
- [ ] All API endpoints
- [ ] Bearer token authentication middleware
- [ ] Tailscale integration

### Phase 3: Music Library System (TODO)
- [ ] Song metadata extraction
- [ ] Album organization and sorting
- [ ] Folder scanning and monitoring

### Phase 4: QR Code & Device Management (TODO)
- [ ] QR code generation
- [ ] Device tracking
- [ ] Token management

### Phase 5: Complete Fyne GUI (TODO)
- [ ] Folder selection dialog
- [ ] Album folder organization display
- [ ] Real-time updates

### Phase 6: Testing & Cross-Platform Builds (TODO)
- [ ] Android app compatibility testing
- [ ] Windows/Linux builds
- [ ] Integration testing

## Installation

### Prerequisites
- Go 1.21+
- C compiler (for Fyne)

### Build
```bash
go mod tidy
go build -o bma .
```

### Run
```bash
./bma
```

## Compatibility

This Go+Fyne version is designed to be 100% compatible with:
- Existing BMA Android app
- Same QR pairing system
- Identical API endpoints
- Same Bearer token authentication

## License

Personal use music streaming application. 