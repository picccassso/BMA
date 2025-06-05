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

### Phase 2: HTTP Server Implementation ✅
- [x] Server manager with start/stop functionality
- [x] All API endpoints
- [x] Bearer token authentication middleware
- [x] Tailscale integration

### Phase 3: Music Library System (TODO)
- [ ] Song metadata extraction
- [ ] Album organization and sorting
- [ ] Folder scanning and monitoring

### Phase 4: QR Code & Device Management ✅
- [x] QR code generation
- [x] Device tracking
- [x] Token management

### Phase 5: Complete Fyne GUI (TODO)
- [ ] Folder selection dialog
- [ ] Album folder organization display
- [ ] Real-time updates

### Phase 6: Testing & Cross-Platform Builds ⚠️
- [ ] Android app compatibility testing  
- [x] Windows/Linux builds (cross-compilation ready)
- [x] Build scripts and Makefile
- [ ] Integration testing

## Installation

### Prerequisites
- Go 1.21+
- C compiler (for Fyne)

### Build

#### Option 1: Quick Build (Current Platform)
```bash
go mod tidy
go build -o bma .
```

#### Option 2: Using Makefile (Cross-Platform)
```bash
# Build for current platform
make build

# Build for Windows (from macOS/Linux)
make install-cross-deps  # Install mingw-w64
make windows

# Build for all platforms
make all-platforms

# See all options
make help
```

#### Option 3: Windows-Specific

**Cross-compilation from macOS/Linux:**
```bash
# Install cross-compilation tools
brew install mingw-w64

# Run build script
./build-windows.sh
```

**Native Windows build:**
```powershell
# PowerShell (requires TDM-GCC or Visual Studio Build Tools)
.\build-windows.ps1

# Or manually:
go mod tidy
go build -ldflags "-s -w -H windowsgui" -o bma.exe .
```

#### Windows Prerequisites

**Option A: TDM-GCC (Recommended for simplicity)**
1. Download from: https://jmeubank.github.io/tdm-gcc/
2. Install with default settings
3. Restart PowerShell/Command Prompt

**Option B: Visual Studio Build Tools**
1. Download VS Build Tools from Microsoft
2. Install with "C++ build tools" workload
3. Open "Developer PowerShell for VS"

**Option C: MSYS2 (Alternative)**
```powershell
# Install via Chocolatey
choco install msys2

# Or download from: https://www.msys2.org/
# Then install gcc: pacman -S mingw-w64-x86_64-gcc
```

### Run
```bash
# macOS/Linux
./bma

# Windows
.\bma.exe
```

## Compatibility

This Go+Fyne version is designed to be 100% compatible with:
- Existing BMA Android app
- Same QR pairing system
- Identical API endpoints
- Same Bearer token authentication

## License

Personal use music streaming application. 