#!/bin/bash

# Build script for Windows (cross-compilation from macOS/Linux)
echo "🏗️ Building BMA for Windows..."

# Set Windows target
export GOOS=windows
export GOARCH=amd64
export CGO_ENABLED=1

# Set up Windows cross-compilation toolchain
# You'll need mingw-w64 installed: brew install mingw-w64
export CC=x86_64-w64-mingw32-gcc
export CXX=x86_64-w64-mingw32-g++

# Build
echo "🔨 Compiling for Windows AMD64..."
go build -ldflags "-s -w" -o bma-windows.exe .

if [ $? -eq 0 ]; then
    echo "✅ Windows build successful: bma-windows.exe"
    echo "📦 Size: $(ls -lh bma-windows.exe | awk '{print $5}')"
else
    echo "❌ Windows build failed"
    exit 1
fi

# Optional: Build for Windows ARM64 (for newer Windows on ARM devices)
echo "🔨 Compiling for Windows ARM64..."
export GOARCH=arm64
export CC=aarch64-w64-mingw32-gcc
export CXX=aarch64-w64-mingw32-g++

go build -ldflags "-s -w" -o bma-windows-arm64.exe .

if [ $? -eq 0 ]; then
    echo "✅ Windows ARM64 build successful: bma-windows-arm64.exe"
else
    echo "⚠️ Windows ARM64 build failed (optional)"
fi

echo "�� Build complete!" 