#!/bin/bash

# BMA-Go Flatpak Build Script for Raspberry Pi
# This script builds a Flatpak package for ARM64 architecture

set -e  # Exit on any error

echo "🚀 Building BMA-Go Flatpak for Raspberry Pi (ARM64)"
echo "=================================================="

# Check if flatpak-builder is installed
if ! command -v flatpak-builder &> /dev/null; then
    echo "❌ flatpak-builder not found. Please install it:"
    echo "   sudo apt install flatpak-builder"
    exit 1
fi

# Check if Flatpak is installed
if ! command -v flatpak &> /dev/null; then
    echo "❌ flatpak not found. Please install it:"
    echo "   sudo apt install flatpak"
    exit 1
fi

# Add Flathub repository if not already added
echo "🔧 Setting up Flatpak repositories..."
flatpak remote-add --if-not-exists flathub https://dl.flathub.org/repo/flathub.flatpakrepo --user

# Install required runtime and SDK
echo "📦 Installing Flatpak runtime and SDK..."
flatpak install --user flathub org.freedesktop.Platform//23.08 org.freedesktop.Sdk//23.08 org.freedesktop.Sdk.Extension.golang//23.08 -y

# Clean previous builds
echo "🧹 Cleaning previous builds..."
rm -rf .flatpak-builder build-dir repo

# Build the Flatpak
echo "🔨 Building Flatpak package..."
flatpak-builder --force-clean --user --install-deps-from=flathub build-dir com.bma.BasicMusicApp.json

# Create a local repository
echo "📚 Creating local repository..."
flatpak-builder --force-clean --repo=repo build-dir com.bma.BasicMusicApp.json

# Install the built package
echo "📦 Installing the package locally..."
flatpak --user remote-add --no-gpg-verify bma-local repo
flatpak --user install bma-local com.bma.BasicMusicApp -y

# Create distributable bundle
echo "📦 Creating distributable bundle..."
flatpak build-bundle repo bma-go-raspberry-pi.flatpak com.bma.BasicMusicApp

echo ""
echo "✅ Build completed successfully!"
echo ""
echo "📦 Flatpak bundle created: bma-go-raspberry-pi.flatpak"
echo ""
echo "🚀 To install on Raspberry Pi:"
echo "   flatpak install --user bma-go-raspberry-pi.flatpak"
echo ""
echo "🎵 To run the application:"
echo "   flatpak run com.bma.BasicMusicApp"
echo "   or find 'Basic Music App' in your applications menu"
echo ""
echo "🔧 To uninstall:"
echo "   flatpak uninstall --user com.bma.BasicMusicApp" 