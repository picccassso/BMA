#!/bin/bash

# BMA-Go Flatpak Build Script
# Creates a universal Linux Flatpak package

set -e  # Exit on any error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
APP_ID="com.bma.BasicMusicApp"
MANIFEST_FILE="com.bma.BasicMusicApp.json"
DESKTOP_FILE="com.bma.BasicMusicApp.desktop"
BUILD_DIR="build-dir"
REPO_DIR="repo"
FLATPAK_FILE="bma-go-linux.flatpak"
RUNTIME_VERSION="23.08"

# Function to print colored output
print_status() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

print_step() {
    echo -e "${BLUE}[STEP]${NC} $1"
}

# Function to check if command exists
command_exists() {
    command -v "$1" >/dev/null 2>&1
}

# Function to install Flatpak dependencies
install_flatpak_deps() {
    print_step "Installing Flatpak dependencies..."
    
    if ! command_exists flatpak; then
        print_error "Flatpak is not installed. Please install it first:"
        echo "  Ubuntu/Debian: sudo apt install flatpak"
        echo "  Fedora: sudo dnf install flatpak"
        echo "  Arch: sudo pacman -S flatpak"
        exit 1
    fi
    
    if ! command_exists flatpak-builder; then
        print_error "flatpak-builder is not installed. Please install it first:"
        echo "  Ubuntu/Debian: sudo apt install flatpak-builder"
        echo "  Fedora: sudo dnf install flatpak-builder"
        echo "  Arch: sudo pacman -S flatpak-builder"
        exit 1
    fi
    
    # Add Flathub if not already added
    if ! flatpak remote-list | grep -q flathub; then
        print_status "Adding Flathub repository..."
        flatpak remote-add --if-not-exists flathub https://dl.flathub.org/repo/flathub.flatpakrepo
    fi
    
    # Install runtime and SDK
    print_status "Installing Flatpak runtime and SDK..."
    flatpak install --user -y flathub org.freedesktop.Platform//${RUNTIME_VERSION} || true
    flatpak install --user -y flathub org.freedesktop.Sdk//${RUNTIME_VERSION} || true
    flatpak install --user -y flathub org.freedesktop.Sdk.Extension.golang//${RUNTIME_VERSION} || true
    
    print_status "Flatpak dependencies ready!"
}

# Function to validate required files
validate_files() {
    print_step "Validating required files..."
    
    local missing_files=()
    
    if [[ ! -f "$MANIFEST_FILE" ]]; then
        missing_files+=("$MANIFEST_FILE")
    fi
    
    if [[ ! -f "$DESKTOP_FILE" ]]; then
        missing_files+=("$DESKTOP_FILE")
    fi
    
    if [[ ! -f "main.go" ]]; then
        missing_files+=("main.go")
    fi
    
    if [[ ! -f "go.mod" ]]; then
        missing_files+=("go.mod")
    fi
    
    if [[ ! -d "assets" ]]; then
        missing_files+=("assets/ directory")
    fi
    
    if [[ ${#missing_files[@]} -gt 0 ]]; then
        print_error "Missing required files:"
        for file in "${missing_files[@]}"; do
            echo "  - $file"
        done
        exit 1
    fi
    
    print_status "All required files present!"
}

# Function to clean previous builds
clean_build() {
    print_step "Cleaning previous builds..."
    
    rm -rf "$BUILD_DIR"
    rm -rf "$REPO_DIR"
    rm -rf ".flatpak-builder"
    rm -f "$FLATPAK_FILE"
    
    print_status "Build directories cleaned!"
}

# Function to create icon if missing
ensure_icon() {
    print_step "Checking application icon..."
    
    local icon_path="assets/com.bma.BasicMusicApp.svg"
    
    if [[ ! -f "$icon_path" ]]; then
        print_warning "Icon not found, creating placeholder..."
        mkdir -p assets
        cat > "$icon_path" << 'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<svg width="64" height="64" viewBox="0 0 64 64" xmlns="http://www.w3.org/2000/svg">
  <rect width="64" height="64" fill="#2196F3" rx="8"/>
  <text x="32" y="40" text-anchor="middle" fill="white" font-family="Arial" font-size="24" font-weight="bold">♪</text>
</svg>
EOF
        print_status "Placeholder icon created!"
    else
        print_status "Icon found!"
    fi
}

# Function to build the Flatpak
build_flatpak() {
    print_step "Building Flatpak package..."
    
    # First build to repo
    print_status "Building to repository..."
    flatpak-builder \
        --force-clean \
        --disable-rofiles-fuse \
        --repo="$REPO_DIR" \
        --install-deps-from=flathub \
        "$BUILD_DIR" \
        "$MANIFEST_FILE"
    
    # Create distributable bundle
    print_status "Creating distributable bundle..."
    flatpak build-bundle \
        "$REPO_DIR" \
        "$FLATPAK_FILE" \
        "$APP_ID"
    
    print_status "Flatpak build complete!"
}

# Function to test the build
test_build() {
    print_step "Testing the Flatpak package..."
    
    # Install locally for testing
    print_status "Installing locally for testing..."
    flatpak install --user -y "$FLATPAK_FILE" || {
        print_warning "Local installation failed, but package may still be valid"
        return
    }
    
    # Quick test - check if it can be run with --help or --version
    print_status "Running basic functionality test..."
    timeout 10s flatpak run "$APP_ID" --help >/dev/null 2>&1 || {
        print_warning "App test failed, but this might be expected for GUI apps"
    }
    
    print_status "Build test completed!"
}

# Function to show build information
show_build_info() {
    print_step "Build Information"
    
    if [[ -f "$FLATPAK_FILE" ]]; then
        local file_size=$(du -h "$FLATPAK_FILE" | cut -f1)
        print_status "Package created: $FLATPAK_FILE"
        print_status "Package size: $file_size"
        echo ""
        echo "Installation commands:"
        echo "  flatpak install --user $FLATPAK_FILE"
        echo "  flatpak run $APP_ID"
        echo ""
        echo "Uninstall command:"
        echo "  flatpak uninstall --user $APP_ID"
        echo ""
        print_status "Package ready for distribution!"
    else
        print_error "Package file not found!"
        exit 1
    fi
}

# Function to show usage
show_usage() {
    echo "Usage: $0 [OPTIONS]"
    echo ""
    echo "Options:"
    echo "  --clean-only    Only clean build directories and exit"
    echo "  --no-test       Skip testing the built package"
    echo "  --help          Show this help message"
    echo ""
    echo "This script builds a Flatpak package for BMA-Go that can be installed"
    echo "on any Linux system with Flatpak support."
}

# Main execution
main() {
    local clean_only=false
    local test_package=true
    
    # Parse command line arguments
    while [[ $# -gt 0 ]]; do
        case $1 in
            --clean-only)
                clean_only=true
                shift
                ;;
            --no-test)
                test_package=false
                shift
                ;;
            --help)
                show_usage
                exit 0
                ;;
            *)
                print_error "Unknown option: $1"
                show_usage
                exit 1
                ;;
        esac
    done
    
    echo "🏗️  BMA-Go Flatpak Builder"
    echo "=========================="
    echo ""
    
    # Clean build directories
    clean_build
    
    if [[ "$clean_only" == true ]]; then
        print_status "Clean completed!"
        exit 0
    fi
    
    # Validate environment and files
    validate_files
    install_flatpak_deps
    ensure_icon
    
    # Build the package
    build_flatpak
    
    # Test if requested
    if [[ "$test_package" == true ]]; then
        test_build
    fi
    
    # Show build information
    show_build_info
    
    echo ""
    print_status "🎉 Flatpak build completed successfully!"
}

# Run main function with all arguments
main "$@" 