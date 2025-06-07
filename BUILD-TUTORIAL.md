# BMA-Go Build Tutorial 🏗️

Complete guide for building BMA-Go (Basic Music App) for different platforms and architectures.

## 📋 Prerequisites

### Required Tools
- **Go 1.21+** - [Download from golang.org](https://golang.org/downloads/)
- **C Compiler** - Required for CGO and Fyne GUI framework
- **Git** (optional) - For version control

### Platform-Specific Requirements

#### Windows
```powershell
# Option A: TDM-GCC (Recommended)
# Download from: https://jmeubank.github.io/tdm-gcc/

# Option B: Visual Studio Build Tools
# Download from: https://visualstudio.microsoft.com/downloads/

# Option C: MSYS2
choco install msys2
# Then: pacman -S mingw-w64-x86_64-gcc
```

#### macOS
```bash
# Install Xcode Command Line Tools
xcode-select --install

# Or install Xcode from App Store
```

#### Linux
```bash
# Ubuntu/Debian
sudo apt update
sudo apt install build-essential

# Fedora/CentOS
sudo dnf groupinstall "Development Tools"

# Arch Linux
sudo pacman -S base-devel
```

---

## 🪟 Windows Builds

### Method 1: Using Makefile (Recommended)

```bash
# Clean previous builds
make clean

# Build for Windows (requires mingw-w64)
make windows

# Output: build/bma-windows-amd64.exe
```

### Method 2: Manual Build Commands

#### Windows AMD64 (64-bit)
```bash
# From macOS/Linux with cross-compilation
GOOS=windows GOARCH=amd64 CGO_ENABLED=1 CC=x86_64-w64-mingw32-gcc CXX=x86_64-w64-mingw32-g++ go build -ldflags "-s -w -H windowsgui" -o bma-windows-amd64.exe .

# From Windows (native)
go build -ldflags "-s -w -H windowsgui" -o bma-windows-amd64.exe .
```

#### Windows 386 (32-bit)
```bash
# Cross-compilation
GOOS=windows GOARCH=386 CGO_ENABLED=1 CC=i686-w64-mingw32-gcc CXX=i686-w64-mingw32-g++ go build -ldflags "-s -w -H windowsgui" -o bma-windows-386.exe .

# Native Windows
set GOARCH=386
go build -ldflags "-s -w -H windowsgui" -o bma-windows-386.exe .
```

#### Quick Windows Build (No CGO)
```bash
# Faster build but may miss some GUI features
GOOS=windows GOARCH=amd64 CGO_ENABLED=0 go build -ldflags "-s -w" -o bma-windows-quick.exe .
```

### Windows Build Flags Explained
- `-ldflags "-s -w"` - Strip debug info (smaller file)
- `-H windowsgui` - Hide console window (GUI app)
- `CGO_ENABLED=1` - Enable C interop (required for Fyne)

---

## 🍎 macOS Builds

### Method 1: Using Makefile

```bash
# Build for macOS (both Intel and Apple Silicon)
make macos

# Output: 
# build/bma-macos-amd64 (Intel)
# build/bma-macos-arm64 (Apple Silicon)
```

### Method 2: Manual Build Commands

#### Universal Binary (Recommended)
```bash
# Step 1: Build for Intel (x86_64)
GOOS=darwin GOARCH=amd64 CGO_ENABLED=1 go build -ldflags "-s -w" -o bma-intel .

# Step 2: Build for Apple Silicon (arm64)
GOOS=darwin GOARCH=arm64 CGO_ENABLED=1 go build -ldflags "-s -w" -o bma-arm64 .

# Step 3: Combine into universal binary
lipo -create -output bma-universal bma-intel bma-arm64

# Step 4: Clean up
rm bma-intel bma-arm64

# Step 5: Verify
lipo -info bma-universal
# Output: Architectures in the fat file: bma-universal are: x86_64 arm64
```

#### Intel Only (x86_64)
```bash
# For older Intel Macs
GOOS=darwin GOARCH=amd64 CGO_ENABLED=1 go build -ldflags "-s -w" -o bma-intel .
```

#### Apple Silicon Only (arm64)
```bash
# For M1/M2/M3/M4 Macs
GOOS=darwin GOARCH=arm64 CGO_ENABLED=1 go build -ldflags "-s -w" -o bma-arm64 .
```

#### Native Build (Current Architecture)
```bash
# Builds for the Mac you're currently on
go build -ldflags "-s -w" -o bma .
```

### macOS Architecture Guide

| Mac Model | Year | Architecture | Binary to Use |
|-----------|------|--------------|---------------|
| MacBook Pro (Intel) | 2016-2020 | x86_64 | Intel or Universal |
| MacBook Air (Intel) | 2017-2020 | x86_64 | Intel or Universal |
| MacBook Pro (M1/M2/M3) | 2021+ | arm64 | Apple Silicon or Universal |
| MacBook Air (M1/M2) | 2020+ | arm64 | Apple Silicon or Universal |
| iMac (Intel) | 2017-2020 | x86_64 | Intel or Universal |
| iMac (M1/M3) | 2021+ | arm64 | Apple Silicon or Universal |
| Mac mini (Intel) | 2018-2020 | x86_64 | Intel or Universal |
| Mac mini (M1/M2) | 2020+ | arm64 | Apple Silicon or Universal |
| Mac Studio | 2022+ | arm64 | Apple Silicon or Universal |
| Mac Pro (Intel) | 2019 | x86_64 | Intel or Universal |
| Mac Pro (M2) | 2023+ | arm64 | Apple Silicon or Universal |

---

## 🐧 Linux Builds

### Method 1: Using Makefile

```bash
# Build for Linux
make linux

# Output: build/bma-linux-amd64
```

### Method 2: Manual Build Commands

#### Linux AMD64
```bash
GOOS=linux GOARCH=amd64 CGO_ENABLED=1 go build -ldflags "-s -w" -o bma-linux-amd64 .
```

#### Linux ARM64 (Raspberry Pi, etc.)
```bash
GOOS=linux GOARCH=arm64 CGO_ENABLED=1 go build -ldflags "-s -w" -o bma-linux-arm64 .
```

#### Linux ARM (32-bit)
```bash
GOOS=linux GOARCH=arm CGO_ENABLED=1 go build -ldflags "-s -w" -o bma-linux-arm .
```

---

## 🚀 Build All Platforms

### Using Makefile
```bash
# Build for all supported platforms
make all-platforms

# Output:
# build/bma-windows-amd64.exe
# build/bma-macos-amd64
# build/bma-macos-arm64  
# build/bma-linux-amd64
```

### Manual Script
```bash
#!/bin/bash
# Build script for all platforms

echo "🏗️ Building BMA-Go for all platforms..."

# Create build directory
mkdir -p build

# Windows
echo "🪟 Building Windows..."
GOOS=windows GOARCH=amd64 CGO_ENABLED=1 CC=x86_64-w64-mingw32-gcc go build -ldflags "-s -w -H windowsgui" -o build/bma-windows-amd64.exe .

# macOS Intel
echo "🍎 Building macOS Intel..."
GOOS=darwin GOARCH=amd64 CGO_ENABLED=1 go build -ldflags "-s -w" -o build/bma-macos-intel .

# macOS Apple Silicon  
echo "🍎 Building macOS Apple Silicon..."
GOOS=darwin GOARCH=arm64 CGO_ENABLED=1 go build -ldflags "-s -w" -o build/bma-macos-arm64 .

# macOS Universal
echo "🍎 Creating macOS Universal..."
lipo -create -output build/bma-macos-universal build/bma-macos-intel build/bma-macos-arm64

# Linux
echo "🐧 Building Linux..."
GOOS=linux GOARCH=amd64 CGO_ENABLED=1 go build -ldflags "-s -w" -o build/bma-linux-amd64 .

echo "✅ All builds complete!"
ls -la build/
```

---

## 🔧 Troubleshooting

### Common Issues

#### CGO Compilation Errors
```bash
# Error: C compiler not found
# Solution: Install build tools for your platform

# Windows: Install TDM-GCC or Visual Studio Build Tools
# macOS: xcode-select --install  
# Linux: sudo apt install build-essential
```

#### Cross-Compilation Issues
```bash
# Error: Cross-compilation not working
# Solution: Install cross-compilers

# For Windows cross-compilation on macOS/Linux:
brew install mingw-w64

# For ARM cross-compilation:
sudo apt install gcc-aarch64-linux-gnu
```

#### Fyne Build Issues
```bash
# Error: Package fyne.io/fyne/v2 not found
# Solution: Download dependencies
go mod download
go mod tidy

# Error: Graphics libraries missing
# Linux: sudo apt install libgl1-mesa-dev xorg-dev
# macOS: Included with Xcode
# Windows: Included with TDM-GCC
```

### Performance Optimization

#### Smaller Binaries
```bash
# Use build tags to exclude unused features
go build -tags "netgo" -ldflags "-s -w" .

# Use UPX compression (optional)
upx --best bma.exe
```

#### Faster Builds
```bash
# Disable CGO for faster builds (loses some GUI features)
CGO_ENABLED=0 go build .

# Use build cache
go build -a .
```

---

## 📦 File Sizes Reference

| Platform | Architecture | Typical Size | With UPX |
|----------|-------------|--------------|----------|
| Windows | amd64 | ~20MB | ~7MB |
| Windows | 386 | ~18MB | ~6MB |
| macOS | Universal | ~40MB | ~14MB |
| macOS | Intel | ~20MB | ~7MB |
| macOS | Apple Silicon | ~20MB | ~7MB |
| Linux | amd64 | ~20MB | ~7MB |
| Linux | arm64 | ~18MB | ~6MB |

---

## 🎯 Distribution Tips

### Windows
- Package as `.exe` or use installer tools like NSIS
- Consider code signing for security
- Test on Windows 10/11

### macOS  
- Universal binaries work on all Macs (recommended)
- Consider notarization for distribution
- Test on both Intel and Apple Silicon if possible

### Linux
- Build Flatpak for universal compatibility
- Consider AppImage for portable distribution
- Test on different distributions

---

## 📝 Quick Reference Commands

```bash
# Current platform
go build .

# Windows 64-bit
GOOS=windows GOARCH=amd64 CGO_ENABLED=1 go build -ldflags "-s -w -H windowsgui" -o app.exe .

# macOS Universal
GOOS=darwin GOARCH=amd64 CGO_ENABLED=1 go build -o app-intel .
GOOS=darwin GOARCH=arm64 CGO_ENABLED=1 go build -o app-arm64 .
lipo -create -output app-universal app-intel app-arm64

# Linux 64-bit  
GOOS=linux GOARCH=amd64 CGO_ENABLED=1 go build -o app .

# All platforms
make all-platforms
```

---
