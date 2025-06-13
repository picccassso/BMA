# Makefile for BMA - Basic Music App (Go+Fyne Edition)

# Variables
APP_NAME=bma
VERSION=1.0.0
BUILD_DIR=build
GO_FLAGS=-ldflags "-s -w"
GO_FLAGS_WINDOWS=-ldflags "-s -w -H windowsgui"

# Default target
.PHONY: all
all: build

# Clean build directory
.PHONY: clean
clean:
	@echo "🧹 Cleaning build directory..."
	@rm -rf $(BUILD_DIR)
	@rm -f $(APP_NAME) $(APP_NAME).exe $(APP_NAME)-*

# Dependencies
.PHONY: deps
deps:
	@echo "📦 Installing dependencies..."
	@go mod download
	@go mod tidy

# Build for current platform
.PHONY: build
build: deps
	@echo "🔨 Building for current platform..."
	@go build $(GO_FLAGS) -o $(APP_NAME) .
	@echo "✅ Build complete: $(APP_NAME)"

# Build for Linux
.PHONY: linux
linux: deps
	@echo "🐧 Building for Linux..."
	@mkdir -p $(BUILD_DIR)
	@GOOS=linux GOARCH=amd64 CGO_ENABLED=1 go build $(GO_FLAGS) -o $(BUILD_DIR)/$(APP_NAME)-linux-amd64 .
	@echo "✅ Linux build complete: $(BUILD_DIR)/$(APP_NAME)-linux-amd64"

# Build for macOS
.PHONY: macos darwin
macos darwin: deps
	@echo "🍎 Building for macOS..."
	@mkdir -p $(BUILD_DIR)
	@echo "Building Intel (amd64) binary..."
	@GOOS=darwin GOARCH=amd64 CGO_ENABLED=1 go build $(GO_FLAGS) -o $(BUILD_DIR)/$(APP_NAME)-macos-amd64 .
	@echo "Building Apple Silicon (arm64) binary..."
	@GOOS=darwin GOARCH=arm64 CGO_ENABLED=1 go build $(GO_FLAGS) -o $(BUILD_DIR)/$(APP_NAME)-macos-arm64 .
	@echo "Creating universal binary..."
	@lipo -create -output $(BUILD_DIR)/$(APP_NAME)-macos-universal $(BUILD_DIR)/$(APP_NAME)-macos-amd64 $(BUILD_DIR)/$(APP_NAME)-macos-arm64
	@echo "✅ macOS universal binary complete: $(BUILD_DIR)/$(APP_NAME)-macos-universal"

# Build for Windows (requires mingw-w64)
.PHONY: windows
windows: deps
	@echo "🪟 Building for Windows..."
	@mkdir -p $(BUILD_DIR)
	@echo "Building Windows AMD64..."
	@GOOS=windows GOARCH=amd64 CGO_ENABLED=1 CC=x86_64-w64-mingw32-gcc CXX=x86_64-w64-mingw32-g++ go build $(GO_FLAGS_WINDOWS) -o $(BUILD_DIR)/$(APP_NAME)-windows-amd64.exe .
	@echo "✅ Windows build complete: $(BUILD_DIR)/$(APP_NAME)-windows-amd64.exe"

# Build for Windows ARM64 (experimental)
.PHONY: windows-arm64
windows-arm64: deps
	@echo "🪟 Building for Windows ARM64..."
	@mkdir -p $(BUILD_DIR)
	@GOOS=windows GOARCH=arm64 CGO_ENABLED=1 CC=aarch64-w64-mingw32-gcc CXX=aarch64-w64-mingw32-g++ go build $(GO_FLAGS_WINDOWS) -o $(BUILD_DIR)/$(APP_NAME)-windows-arm64.exe .
	@echo "✅ Windows ARM64 build complete: $(BUILD_DIR)/$(APP_NAME)-windows-arm64.exe"

# Build all platforms
.PHONY: all-platforms
all-platforms: linux macos windows
	@echo "🎉 All platform builds complete!"
	@ls -la $(BUILD_DIR)/

# Install prerequisites for cross-compilation (macOS)
.PHONY: install-cross-deps
install-cross-deps:
	@echo "📋 Installing cross-compilation dependencies..."
	@if command -v brew >/dev/null 2>&1; then \
		echo "Installing mingw-w64 for Windows cross-compilation..."; \
		brew install mingw-w64; \
	else \
		echo "⚠️ Homebrew not found. Please install mingw-w64 manually."; \
	fi

# Quick Windows test (cross-compile from current platform)
.PHONY: windows-quick
windows-quick: deps
	@echo "🪟 Quick Windows build (native Go, no GUI optimizations)..."
	@GOOS=windows GOARCH=amd64 CGO_ENABLED=0 go build -o $(APP_NAME)-windows.exe .
	@echo "✅ Quick Windows build complete: $(APP_NAME)-windows.exe"
	@echo "⚠️ Note: This build may not include all GUI features"

# Development build with debug info
.PHONY: dev
dev:
	@echo "🔧 Building development version..."
	@go build -race -o $(APP_NAME)-dev .
	@echo "✅ Development build complete: $(APP_NAME)-dev"

# Run the application
.PHONY: run
run: build
	@echo "🚀 Running BMA..."
	@./$(APP_NAME)

# Test
.PHONY: test
test:
	@echo "🧪 Running tests..."
	@go test ./...

# Show help
.PHONY: help
help:
	@echo "BMA Build System"
	@echo "=================="
	@echo ""
	@echo "Available targets:"
	@echo "  build              - Build for current platform"
	@echo "  linux              - Build for Linux"
	@echo "  macos/darwin       - Build for macOS (Intel + Apple Silicon)"
	@echo "  windows            - Build for Windows (requires mingw-w64)"
	@echo "  windows-arm64      - Build for Windows ARM64"
	@echo "  windows-quick      - Quick Windows build (no CGO)"
	@echo "  all-platforms      - Build for all platforms"
	@echo "  clean              - Clean build artifacts"
	@echo "  deps               - Install/update dependencies"
	@echo "  dev                - Development build with race detection"
	@echo "  run                - Build and run the application"
	@echo "  test               - Run tests"
	@echo "  install-cross-deps - Install cross-compilation dependencies"
	@echo "  help               - Show this help" 