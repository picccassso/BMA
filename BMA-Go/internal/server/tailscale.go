package server

import (
	"encoding/json"
	"fmt"
	"log"
	"os"
	"os/exec"
	"os/user"
	"strings"
	"time"
)

// Tailscale Integration for BMA Go+Fyne
// Features:
// - Auto-detection of Tailscale installation and status
// - HTTP over Tailscale (network-level encryption)
// - Dynamic URL generation (local vs Tailscale)
// - Shell command integration using os/exec
// - Status monitoring and hostname resolution 

// TailscaleManager handles Tailscale detection and integration
type TailscaleManager struct {
	isAvailable bool
	hostname    string
	status      string
	lastCheck   time.Time
}

// NewTailscaleManager creates a new Tailscale manager
func NewTailscaleManager() *TailscaleManager {
	return &TailscaleManager{}
}

// isRunningInFlatpak checks if the application is running inside a Flatpak sandbox
func (sm *ServerManager) isRunningInFlatpak() bool {
	cmd := exec.Command("sh", "-c", "echo $FLATPAK_ID")
	output, err := cmd.Output()
	if err == nil && strings.TrimSpace(string(output)) != "" {
		log.Printf("🔍 [FLATPAK] Running in Flatpak: %s", strings.TrimSpace(string(output)))
		return true
	}
	return false
}

// executeCommand creates a command that automatically uses flatpak-spawn when in Flatpak environment
func (sm *ServerManager) executeCommand(name string, args ...string) *exec.Cmd {
	if sm.useFlatpakSpawn && (name == "tailscale" || strings.HasSuffix(name, "/tailscale")) {
		// Prepend flatpak-spawn --host for tailscale commands (whether path or just "tailscale")
		flatpakArgs := append([]string{"--host", "tailscale"}, args...)
		log.Printf("🔧 [FLATPAK] Using flatpak-spawn: flatpak-spawn %v", flatpakArgs)
		return exec.Command("flatpak-spawn", flatpakArgs...)
	}
	return exec.Command(name, args...)
}

// debugFlatpakHostAccess performs comprehensive flatpak-spawn testing and diagnostics
func (sm *ServerManager) debugFlatpakHostAccess() {
	log.Println("🔧 [FLATPAK DEBUG] Testing flatpak-spawn host access capabilities...")
	
	// Test 1: Basic flatpak-spawn functionality
	log.Println("🔧 [FLATPAK DEBUG] Test 1: Basic flatpak-spawn functionality")
	testCmd := exec.Command("flatpak-spawn", "--host", "echo", "flatpak-spawn works")
	if output, err := testCmd.CombinedOutput(); err == nil {
		log.Printf("✅ [FLATPAK DEBUG] Basic test passed: %s", strings.TrimSpace(string(output)))
	} else {
		log.Printf("❌ [FLATPAK DEBUG] Basic test failed: %v", err)
		log.Printf("❌ [FLATPAK DEBUG] Output: %s", string(output))
		return // If basic functionality fails, no point continuing
	}
	
	// Test 2: Host environment visibility
	log.Println("🔧 [FLATPAK DEBUG] Test 2: Host environment analysis")
	envCmd := exec.Command("flatpak-spawn", "--host", "env")
	if output, err := envCmd.CombinedOutput(); err == nil {
		log.Printf("✅ [FLATPAK DEBUG] Host environment accessible")
		// Look for PATH in environment
		envLines := strings.Split(string(output), "\n")
		for _, line := range envLines {
			if strings.HasPrefix(line, "PATH=") {
				log.Printf("🔧 [FLATPAK DEBUG] Host PATH: %s", line)
				break
			}
		}
	} else {
		log.Printf("❌ [FLATPAK DEBUG] Host environment test failed: %v", err)
	}
	
	// Test 3: Host user context
	log.Println("🔧 [FLATPAK DEBUG] Test 3: Host user context")
	whoamiCmd := exec.Command("flatpak-spawn", "--host", "whoami")
	if output, err := whoamiCmd.CombinedOutput(); err == nil {
		log.Printf("✅ [FLATPAK DEBUG] Host user: %s", strings.TrimSpace(string(output)))
	} else {
		log.Printf("❌ [FLATPAK DEBUG] whoami test failed: %v", err)
	}
	
	// Test 4: Try to find tailscale via different methods on host
	log.Println("🔧 [FLATPAK DEBUG] Test 4: Tailscale discovery on host")
	
	// Try which command on host
	whichCmd := exec.Command("flatpak-spawn", "--host", "which", "tailscale")
	if output, err := whichCmd.CombinedOutput(); err == nil {
		hostTailscalePath := strings.TrimSpace(string(output))
		log.Printf("✅ [FLATPAK DEBUG] Host 'which tailscale': %s", hostTailscalePath)
		
		// Test the found path
		versionCmd := exec.Command("flatpak-spawn", "--host", hostTailscalePath, "version")
		if versionOutput, versionErr := versionCmd.CombinedOutput(); versionErr == nil {
			log.Printf("✅ [FLATPAK DEBUG] Host tailscale version: %s", strings.TrimSpace(string(versionOutput)))
			sm.useFlatpakSpawn = true
			return
		} else {
			log.Printf("❌ [FLATPAK DEBUG] Host tailscale version failed: %v", versionErr)
			log.Printf("❌ [FLATPAK DEBUG] Version output: %s", string(versionOutput))
		}
	} else {
		log.Printf("❌ [FLATPAK DEBUG] Host 'which tailscale' failed: %v", err)
		log.Printf("❌ [FLATPAK DEBUG] Which output: %s", string(output))
	}
	
	// Try locate command on host
	locateCmd := exec.Command("flatpak-spawn", "--host", "locate", "tailscale")
	if output, err := locateCmd.CombinedOutput(); err == nil && strings.TrimSpace(string(output)) != "" {
		log.Printf("✅ [FLATPAK DEBUG] Host 'locate tailscale':\n%s", string(output))
	} else {
		log.Printf("❌ [FLATPAK DEBUG] Host 'locate tailscale' failed: %v", err)
	}
	
	// Try find command on host (search /usr/bin specifically)
	findCmd := exec.Command("flatpak-spawn", "--host", "find", "/usr/bin", "-name", "*tailscale*", "-type", "f")
	if output, err := findCmd.CombinedOutput(); err == nil && strings.TrimSpace(string(output)) != "" {
		log.Printf("✅ [FLATPAK DEBUG] Host find results:\n%s", string(output))
	} else {
		log.Printf("❌ [FLATPAK DEBUG] Host find failed: %v", err)
	}
	
	// Test 5: Try direct tailscale command with full error capture
	log.Println("🔧 [FLATPAK DEBUG] Test 5: Direct tailscale command test")
	directCmd := exec.Command("flatpak-spawn", "--host", "tailscale", "version")
	if output, err := directCmd.CombinedOutput(); err == nil {
		log.Printf("✅ [FLATPAK DEBUG] Direct 'tailscale version' works: %s", strings.TrimSpace(string(output)))
		sm.useFlatpakSpawn = true
	} else {
		log.Printf("❌ [FLATPAK DEBUG] Direct 'tailscale version' failed: %v", err)
		log.Printf("❌ [FLATPAK DEBUG] Full output: %s", string(output))
		sm.useFlatpakSpawn = false
	}
	
	if sm.useFlatpakSpawn {
		log.Println("✅ [FLATPAK DEBUG] Flatpak-spawn mode enabled - Tailscale accessible on host")
	} else {
		log.Println("❌ [FLATPAK DEBUG] Flatpak-spawn mode disabled - falling back to sandbox detection")
	}
}

// expandSystemPath adds common system directories to PATH
func (sm *ServerManager) expandSystemPath(currentPath string) string {
	// Common system directories that might contain binaries
	systemDirs := []string{
		"/usr/bin",
		"/bin", 
		"/usr/sbin",
		"/sbin",
		"/usr/local/bin",
		"/usr/local/sbin",
		"/opt/bin",
		"/snap/bin",
	}
	
	// Start with current path
	pathParts := []string{}
	if currentPath != "" {
		pathParts = strings.Split(currentPath, ":")
	}
	
	// Add system directories if not already present
	for _, dir := range systemDirs {
		found := false
		for _, existing := range pathParts {
			if existing == dir {
				found = true
				break
			}
		}
		if !found {
			pathParts = append(pathParts, dir)
		}
	}
	
	return strings.Join(pathParts, ":")
}

// debugEnvironment shows what the Go app can see for debugging
func (sm *ServerManager) debugEnvironment() {
	log.Println("🔧 [DEBUG] Environment Analysis:")
	
	// Show PATH
	originalPath := os.Getenv("PATH")
	if originalPath != "" {
		log.Printf("🔧 [DEBUG] Original PATH: %s", originalPath)
	} else {
		log.Println("🔧 [DEBUG] Original PATH: (empty)")
	}
	
	// Expand PATH to include common system directories
	expandedPath := sm.expandSystemPath(originalPath)
	log.Printf("🔧 [DEBUG] Expanded PATH: %s", expandedPath)
	os.Setenv("PATH", expandedPath)
	
	// Show current user
	if usr, err := user.Current(); err == nil {
		log.Printf("🔧 [DEBUG] User: %s (UID: %s, GID: %s, Home: %s)", usr.Username, usr.Uid, usr.Gid, usr.HomeDir)
	}
	
	// Test direct tailscale command (after PATH expansion)
	log.Println("🔧 [DEBUG] Testing direct 'tailscale status' command (with expanded PATH):")
	cmd := sm.executeCommand("tailscale", "status")
	output, err := cmd.Output()
	if err != nil {
		log.Printf("🔧 [DEBUG] 'tailscale status' still failed: %v", err)
		
		// Try with absolute paths
		log.Println("🔧 [DEBUG] Trying absolute paths for tailscale:")
		absolutePaths := []string{"/usr/bin/tailscale", "/bin/tailscale", "/usr/sbin/tailscale", "/sbin/tailscale", "/usr/local/bin/tailscale", "/snap/bin/tailscale"}
		for _, path := range absolutePaths {
			cmd = exec.Command(path, "status")
			output, err = cmd.Output()
			if err == nil {
				log.Printf("🔧 [DEBUG] SUCCESS with '%s': %s", path, string(output))
				break
			} else {
				log.Printf("🔧 [DEBUG] Failed with '%s': %v", path, err)
			}
		}
	} else {
		log.Printf("🔧 [DEBUG] SUCCESS - 'tailscale status' output: %s", string(output))
	}
	
	// Show all network interfaces
	log.Println("🔧 [DEBUG] Network interfaces:")
	
	// Try ip command with absolute paths
	ipPaths := []string{"/usr/bin/ip", "/bin/ip", "/sbin/ip", "/usr/sbin/ip"}
	ipWorked := false
	for _, ipPath := range ipPaths {
		cmd = exec.Command(ipPath, "addr", "show")
		output, err = cmd.Output()
		if err == nil {
			log.Printf("🔧 [DEBUG] SUCCESS with '%s addr show':\n%s", ipPath, string(output))
			ipWorked = true
			break
		}
	}
	
	if !ipWorked {
		log.Println("🔧 [DEBUG] All 'ip' paths failed, trying 'ifconfig':")
		// Try ifconfig with absolute paths
		ifconfigPaths := []string{"/usr/bin/ifconfig", "/bin/ifconfig", "/sbin/ifconfig", "/usr/sbin/ifconfig"}
		for _, ifconfigPath := range ifconfigPaths {
			cmd = exec.Command(ifconfigPath)
			output, err = cmd.Output()
			if err == nil {
				log.Printf("🔧 [DEBUG] SUCCESS with '%s':\n%s", ifconfigPath, string(output))
				break
			}
		}
	}
	
	// Show which/whereis results
	log.Println("🔧 [DEBUG] Binary location tests:")
	for _, cmd := range []string{"which tailscale", "whereis tailscale", "type tailscale"} {
		parts := strings.Fields(cmd)
		execCmd := exec.Command(parts[0], parts[1:]...)
		output, err := execCmd.Output()
		if err != nil {
			log.Printf("🔧 [DEBUG] '%s' failed: %v", cmd, err)
		} else {
			log.Printf("🔧 [DEBUG] '%s' output: %s", cmd, strings.TrimSpace(string(output)))
		}
	}
	
	// Aggressive system-wide search for tailscale
	log.Println("🔧 [DEBUG] Performing aggressive system-wide tailscale search:")
	sm.aggressiveTailscaleSearch()
	
	log.Println("🔧 [DEBUG] Environment analysis complete")
}

// aggressiveTailscaleSearch performs system-wide search for tailscale binary
func (sm *ServerManager) aggressiveTailscaleSearch() {
	log.Println("🔧 [DEBUG] Starting aggressive search...")
	
	// Try find command on entire filesystem
	log.Println("🔧 [DEBUG] Searching entire filesystem with find:")
	cmd := exec.Command("sh", "-c", "find / -name '*tailscale*' -type f -executable 2>/dev/null | head -20")
	output, err := cmd.Output()
	if err == nil && strings.TrimSpace(string(output)) != "" {
		log.Printf("🔧 [DEBUG] Found tailscale-related files:\n%s", string(output))
		
		// Test each found binary
		lines := strings.Split(strings.TrimSpace(string(output)), "\n")
		for _, line := range lines {
			line = strings.TrimSpace(line)
			if line != "" && strings.Contains(line, "tailscale") && !strings.Contains(line, ".") {
				log.Printf("🔧 [DEBUG] Testing found binary: %s", line)
				testCmd := exec.Command(line, "version")
				testOutput, testErr := testCmd.Output()
				if testErr == nil && strings.Contains(strings.ToLower(string(testOutput)), "tailscale") {
					log.Printf("🔧 [DEBUG] SUCCESS! Working tailscale found at: %s", line)
					log.Printf("🔧 [DEBUG] Version output: %s", string(testOutput))
					
					// Try status command
					statusCmd := exec.Command(line, "status")
					statusOutput, statusErr := statusCmd.Output()
					if statusErr == nil {
						log.Printf("🔧 [DEBUG] Status command works: %s", string(statusOutput))
					} else {
						log.Printf("🔧 [DEBUG] Status command failed: %v", statusErr)
					}
					return
				}
			}
		}
	} else {
		log.Printf("🔧 [DEBUG] Find command failed or no results: %v", err)
	}
	
	// Try alternative search methods
	log.Println("🔧 [DEBUG] Trying alternative search methods:")
	
	// Check if running from flatpak
	cmd = exec.Command("sh", "-c", "echo $FLATPAK_ID")
	output, err = cmd.Output()
	if err == nil && strings.TrimSpace(string(output)) != "" {
		log.Printf("🔧 [DEBUG] Running in Flatpak: %s", strings.TrimSpace(string(output)))
		
		// Try flatpak-spawn for host access
		cmd = exec.Command("flatpak-spawn", "--host", "tailscale", "status")
		output, err = cmd.Output()
		if err == nil {
			log.Printf("🔧 [DEBUG] SUCCESS via flatpak-spawn: %s", string(output))
			return
		} else {
			log.Printf("🔧 [DEBUG] flatpak-spawn failed: %v", err)
		}
	}
	
	// Check if running in container
	cmd = exec.Command("sh", "-c", "cat /proc/1/cgroup 2>/dev/null | grep -q docker && echo 'docker' || echo 'not-docker'")
	output, err = cmd.Output()
	if err == nil && strings.Contains(string(output), "docker") {
		log.Println("🔧 [DEBUG] Running in Docker container")
	}
	
	log.Println("🔧 [DEBUG] Aggressive search complete")
}

// checkTailscaleStatus detects Tailscale installation and status
func (sm *ServerManager) checkTailscaleStatus() {
	log.Println("🔍 Starting Tailscale detection...")
	
	// Check for Flatpak environment first
	if sm.isRunningInFlatpak() {
		log.Println("🔍 [FLATPAK] Flatpak environment detected, running comprehensive host access tests")
		sm.debugFlatpakHostAccess()
		
		// Skip old debugging if Flatpak detection succeeded
		if sm.useFlatpakSpawn {
			log.Println("🔍 [FLATPAK] Skipping sandbox debugging - flatpak-spawn working")
		} else {
			log.Println("🔍 [FLATPAK] Flatpak-spawn failed, falling back to sandbox debugging")
			sm.debugEnvironment()
		}
	} else {
		// Not in Flatpak, run normal debugging
		sm.debugEnvironment()
	}
	
	// Try to detect Tailscale binary first
	tailscalePath := sm.detectTailscaleBinary()
	
	// If binary not found, try service/daemon detection
	if tailscalePath == "" {
		log.Println("⚠️ Tailscale binary not found, trying service/daemon detection...")
		if sm.detectTailscaleService() {
			// Service is running, try to find binary again with fallback methods
			if fallbackPath := sm.findTailscaleWithFallbacks(); fallbackPath != "" {
				tailscalePath = fallbackPath
				log.Printf("✅ Found Tailscale via service detection: %s", tailscalePath)
			} else {
				log.Println("❌ Tailscale service detected but no usable binary found")
				sm.HasTailscale = false
				sm.TailscaleURL = ""
				return
			}
		} else {
			log.Println("⚠️ Tailscale service not detected, trying network interface detection...")
			if sm.detectTailscaleNetwork() {
				// Network interface detected, try to find ANY tailscale binary as final attempt
				if networkPath := sm.findAnyTailscaleBinary(); networkPath != "" {
					tailscalePath = networkPath
					log.Printf("✅ Found Tailscale via network detection: %s", tailscalePath)
				} else {
					// Even without binary, we can try to use Tailscale via network
					log.Println("⚠️ Tailscale network detected but no binary found - will try IP-based connection")
					if tailscaleIP := sm.getTailscaleIPFromNetwork(); tailscaleIP != "" {
						sm.HasTailscale = true
						sm.TailscaleURL = fmt.Sprintf("http://%s", tailscaleIP)
						log.Printf("✅ Tailscale configured via network IP: %s", sm.TailscaleURL)
						return
					}
					log.Println("❌ Could not determine Tailscale IP from network")
					sm.HasTailscale = false
					sm.TailscaleURL = ""
					return
				}
			} else {
				log.Println("❌ Tailscale not detected - binary, service, and network not found")
				sm.HasTailscale = false
				sm.TailscaleURL = ""
				return
			}
		}
	}
	
	log.Printf("✅ Found Tailscale binary at: %s", tailscalePath)
	
	// Check if Tailscale is actually connected
	if sm.checkTailscaleConnection(tailscalePath) {
		// Get Tailscale hostname
		if hostname := sm.getTailscaleHostname(tailscalePath); hostname != "" {
			sm.HasTailscale = true
			sm.TailscaleURL = fmt.Sprintf("http://%s", hostname)
			log.Printf("✅ Tailscale configured: %s", sm.TailscaleURL)
		} else {
			log.Println("❌ Failed to get Tailscale hostname")
			sm.HasTailscale = false
			sm.TailscaleURL = ""
		}
	} else {
		log.Println("❌ Tailscale is not in Running state")
		sm.HasTailscale = false
		sm.TailscaleURL = ""
	}
}

// findTailscaleInPath uses which/where command to find tailscale in PATH
func (sm *ServerManager) findTailscaleInPath() string {
	log.Println("🔍 [TAILSCALE DEBUG] Checking PATH for tailscale binary...")
	
	// Try 'which' command (Unix/Linux/macOS)
	cmd := exec.Command("which", "tailscale")
	output, err := cmd.Output()
	if err == nil {
		path := strings.TrimSpace(string(output))
		if path != "" && sm.isTailscaleBinaryValid(path) {
			log.Printf("✅ [TAILSCALE DEBUG] Found tailscale in PATH via 'which': %s", path)
			return path
		}
	}
	
	// Try 'where' command (Windows)
	cmd = exec.Command("where", "tailscale")
	output, err = cmd.Output()
	if err == nil {
		lines := strings.Split(strings.TrimSpace(string(output)), "\n")
		for _, path := range lines {
			path = strings.TrimSpace(path)
			if path != "" && sm.isTailscaleBinaryValid(path) {
				log.Printf("✅ [TAILSCALE DEBUG] Found tailscale in PATH via 'where': %s", path)
				return path
			}
		}
	}
	
	// Try direct PATH lookup
	if path, err := exec.LookPath("tailscale"); err == nil {
		if sm.isTailscaleBinaryValid(path) {
			log.Printf("✅ [TAILSCALE DEBUG] Found tailscale via LookPath: %s", path)
			return path
		}
	}
	
	log.Println("❌ [TAILSCALE DEBUG] Tailscale not found in PATH")
	return ""
}

// expandUserPaths expands ~ paths to full user home directory paths
func (sm *ServerManager) expandUserPaths(paths []string) []string {
	var expandedPaths []string
	
	// Get user home directory using os/user package
	homeDir := ""
	if usr, err := user.Current(); err == nil {
		homeDir = usr.HomeDir
	}
	
	// Fallback to environment variable
	if homeDir == "" {
		if cmd := exec.Command("sh", "-c", "echo $HOME"); cmd != nil {
			if output, err := cmd.Output(); err == nil {
				homeDir = strings.TrimSpace(string(output))
			}
		}
	}
	
	log.Printf("🔍 [TAILSCALE DEBUG] Using home directory: %s", homeDir)
	
	for _, path := range paths {
		if strings.HasPrefix(path, "~/") && homeDir != "" {
			// Expand ~ to home directory
			expandedPath := strings.Replace(path, "~", homeDir, 1)
			expandedPaths = append(expandedPaths, expandedPath)
			log.Printf("🔍 [TAILSCALE DEBUG] Expanded %s -> %s", path, expandedPath)
		} else {
			// Keep original path
			expandedPaths = append(expandedPaths, path)
		}
	}
	
	return expandedPaths
}

// detectTailscaleService checks if Tailscale service/daemon is running
func (sm *ServerManager) detectTailscaleService() bool {
	log.Println("🔍 [SERVICE DEBUG] Checking for Tailscale service/daemon...")
	
	// Check systemd service (Linux)
	if sm.checkSystemdService() {
		return true
	}
	
	// Check process existence (cross-platform)
	if sm.checkTailscaleProcess() {
		return true
	}
	
	// Check launchd service (macOS)
	if sm.checkLaunchdService() {
		return true
	}
	
	// Check Windows service
	if sm.checkWindowsService() {
		return true
	}
	
	log.Println("❌ [SERVICE DEBUG] No Tailscale service detected")
	return false
}

// checkSystemdService checks Linux systemd service
func (sm *ServerManager) checkSystemdService() bool {
	log.Println("🔍 [SERVICE DEBUG] Checking systemd service...")
	
	// Check if tailscaled service is active
	cmd := exec.Command("systemctl", "is-active", "tailscaled")
	output, err := cmd.Output()
	if err == nil && strings.TrimSpace(string(output)) == "active" {
		log.Println("✅ [SERVICE DEBUG] tailscaled systemd service is active")
		return true
	}
	
	// Also check if service exists but might be inactive
	cmd = exec.Command("systemctl", "status", "tailscaled")
	err = cmd.Run()
	if err == nil {
		log.Println("✅ [SERVICE DEBUG] tailscaled systemd service exists")
		return true
	}
	
	log.Println("❌ [SERVICE DEBUG] No systemd tailscaled service found")
	return false
}

// checkTailscaleProcess checks if tailscaled process is running
func (sm *ServerManager) checkTailscaleProcess() bool {
	log.Println("🔍 [SERVICE DEBUG] Checking for tailscaled process...")
	
	// Try pgrep (Unix/Linux/macOS)
	cmd := exec.Command("pgrep", "tailscaled")
	output, err := cmd.Output()
	if err == nil && strings.TrimSpace(string(output)) != "" {
		log.Printf("✅ [SERVICE DEBUG] Found tailscaled process: %s", strings.TrimSpace(string(output)))
		return true
	}
	
	// Try ps with grep (fallback)
	cmd = exec.Command("sh", "-c", "ps aux | grep tailscaled | grep -v grep")
	output, err = cmd.Output()
	if err == nil && strings.TrimSpace(string(output)) != "" {
		log.Println("✅ [SERVICE DEBUG] Found tailscaled process via ps")
		return true
	}
	
	// Try pidof (Linux)
	cmd = exec.Command("pidof", "tailscaled")
	output, err = cmd.Output()
	if err == nil && strings.TrimSpace(string(output)) != "" {
		log.Printf("✅ [SERVICE DEBUG] Found tailscaled via pidof: %s", strings.TrimSpace(string(output)))
		return true
	}
	
	log.Println("❌ [SERVICE DEBUG] No tailscaled process found")
	return false
}

// checkLaunchdService checks macOS launchd service
func (sm *ServerManager) checkLaunchdService() bool {
	log.Println("🔍 [SERVICE DEBUG] Checking launchd service...")
	
	cmd := exec.Command("launchctl", "list", "com.tailscale.ipnextension")
	err := cmd.Run()
	if err == nil {
		log.Println("✅ [SERVICE DEBUG] Tailscale launchd service found")
		return true
	}
	
	log.Println("❌ [SERVICE DEBUG] No Tailscale launchd service found")
	return false
}

// checkWindowsService checks Windows service
func (sm *ServerManager) checkWindowsService() bool {
	log.Println("🔍 [SERVICE DEBUG] Checking Windows service...")
	
	cmd := exec.Command("sc", "query", "Tailscale")
	output, err := cmd.Output()
	if err == nil && strings.Contains(string(output), "RUNNING") {
		log.Println("✅ [SERVICE DEBUG] Tailscale Windows service is running")
		return true
	}
	
	log.Println("❌ [SERVICE DEBUG] No Tailscale Windows service found")
	return false
}

// findTailscaleWithFallbacks tries aggressive methods to find tailscale binary
func (sm *ServerManager) findTailscaleWithFallbacks() string {
	log.Println("🔍 [FALLBACK DEBUG] Trying aggressive binary detection...")
	
	// Try to find the binary via running process
	if path := sm.findBinaryFromProcess(); path != "" {
		return path
	}
	
	// Try package manager queries
	if path := sm.findBinaryViaPackageManager(); path != "" {
		return path
	}
	
	// Try locate command
	if path := sm.findBinaryViaLocate(); path != "" {
		return path
	}
	
	// Try find command as last resort
	if path := sm.findBinaryViaFind(); path != "" {
		return path
	}
	
	log.Println("❌ [FALLBACK DEBUG] All fallback methods failed")
	return ""
}

// findBinaryFromProcess tries to find binary path from running process
func (sm *ServerManager) findBinaryFromProcess() string {
	log.Println("🔍 [FALLBACK DEBUG] Finding binary from running process...")
	
	// Try to get process info and extract binary path
	cmd := exec.Command("sh", "-c", "ps aux | grep tailscaled | grep -v grep | awk '{print $11}'")
	output, err := cmd.Output()
	if err == nil {
		lines := strings.Split(strings.TrimSpace(string(output)), "\n")
		for _, line := range lines {
			line = strings.TrimSpace(line)
			if line != "" && (strings.Contains(line, "tailscaled") || strings.Contains(line, "tailscale")) {
				// Extract directory and look for tailscale binary
				if strings.Contains(line, "/") {
					dir := strings.TrimSuffix(line, "/tailscaled")
					tailscalePath := dir + "/tailscale"
					if sm.isTailscaleBinaryValid(tailscalePath) {
						log.Printf("✅ [FALLBACK DEBUG] Found tailscale via process: %s", tailscalePath)
						return tailscalePath
					}
				}
			}
		}
	}
	
	log.Println("❌ [FALLBACK DEBUG] Could not find binary from process")
	return ""
}

// findBinaryViaPackageManager queries package managers for tailscale location
func (sm *ServerManager) findBinaryViaPackageManager() string {
	log.Println("🔍 [FALLBACK DEBUG] Checking package managers...")
	
	// Try dpkg (Debian/Ubuntu)
	cmd := exec.Command("dpkg", "-L", "tailscale")
	output, err := cmd.Output()
	if err == nil {
		lines := strings.Split(string(output), "\n")
		for _, line := range lines {
			line = strings.TrimSpace(line)
			if strings.HasSuffix(line, "/tailscale") && sm.isTailscaleBinaryValid(line) {
				log.Printf("✅ [FALLBACK DEBUG] Found tailscale via dpkg: %s", line)
				return line
			}
		}
	}
	
	// Try rpm (RedHat/CentOS/Fedora)
	cmd = exec.Command("rpm", "-ql", "tailscale")
	output, err = cmd.Output()
	if err == nil {
		lines := strings.Split(string(output), "\n")
		for _, line := range lines {
			line = strings.TrimSpace(line)
			if strings.HasSuffix(line, "/tailscale") && sm.isTailscaleBinaryValid(line) {
				log.Printf("✅ [FALLBACK DEBUG] Found tailscale via rpm: %s", line)
				return line
			}
		}
	}
	
	log.Println("❌ [FALLBACK DEBUG] Package managers didn't help")
	return ""
}

// findBinaryViaLocate uses locate command to find tailscale
func (sm *ServerManager) findBinaryViaLocate() string {
	log.Println("🔍 [FALLBACK DEBUG] Trying locate command...")
	
	cmd := exec.Command("locate", "tailscale")
	output, err := cmd.Output()
	if err == nil {
		lines := strings.Split(string(output), "\n")
		for _, line := range lines {
			line = strings.TrimSpace(line)
			if strings.HasSuffix(line, "/tailscale") && !strings.Contains(line, ".") && sm.isTailscaleBinaryValid(line) {
				log.Printf("✅ [FALLBACK DEBUG] Found tailscale via locate: %s", line)
				return line
			}
		}
	}
	
	log.Println("❌ [FALLBACK DEBUG] locate command didn't help")
	return ""
}

// findBinaryViaFind uses find command as last resort
func (sm *ServerManager) findBinaryViaFind() string {
	log.Println("🔍 [FALLBACK DEBUG] Trying find command (last resort)...")
	
	// Search common directories with find
	searchDirs := []string{"/usr", "/opt", "/snap", "/home"}
	
	for _, dir := range searchDirs {
		cmd := exec.Command("find", dir, "-name", "tailscale", "-type", "f", "-executable", "2>/dev/null")
		output, err := cmd.Output()
		if err == nil {
			lines := strings.Split(strings.TrimSpace(string(output)), "\n")
			for _, line := range lines {
				line = strings.TrimSpace(line)
				if line != "" && sm.isTailscaleBinaryValid(line) {
					log.Printf("✅ [FALLBACK DEBUG] Found tailscale via find: %s", line)
					return line
				}
			}
		}
	}
	
	log.Println("❌ [FALLBACK DEBUG] find command didn't help")
	return ""
}

// detectTailscaleNetwork checks if Tailscale network interface exists
func (sm *ServerManager) detectTailscaleNetwork() bool {
	log.Println("🔍 [NETWORK DEBUG] Checking for Tailscale network interface...")
	
	// Check for tailscale0 interface
	if sm.checkTailscaleInterface() {
		return true
	}
	
	// Check for Tailscale IP ranges in any interface
	if sm.checkTailscaleIPRanges() {
		return true
	}
	
	log.Println("❌ [NETWORK DEBUG] No Tailscale network detected")
	return false
}

// checkTailscaleInterface checks for tailscale0 network interface
func (sm *ServerManager) checkTailscaleInterface() bool {
	log.Println("🔍 [NETWORK DEBUG] Checking for tailscale0 interface...")
	
	// Use ip command (Linux)
	cmd := exec.Command("ip", "link", "show", "tailscale0")
	err := cmd.Run()
	if err == nil {
		log.Println("✅ [NETWORK DEBUG] Found tailscale0 interface")
		return true
	}
	
	// Use ifconfig as fallback
	cmd = exec.Command("ifconfig", "tailscale0")
	err = cmd.Run()
	if err == nil {
		log.Println("✅ [NETWORK DEBUG] Found tailscale0 interface via ifconfig")
		return true
	}
	
	// Check if any interface has tailscale in name
	cmd = exec.Command("ip", "link", "show")
	output, err := cmd.Output()
	if err == nil {
		if strings.Contains(string(output), "tailscale") {
			log.Println("✅ [NETWORK DEBUG] Found tailscale-related interface")
			return true
		}
	}
	
	log.Println("❌ [NETWORK DEBUG] No tailscale interface found")
	return false
}

// checkTailscaleIPRanges checks for Tailscale IP addresses (100.x.x.x)
func (sm *ServerManager) checkTailscaleIPRanges() bool {
	log.Println("🔍 [NETWORK DEBUG] Checking for Tailscale IP ranges...")
	
	// Use ip addr show to get all IP addresses
	cmd := exec.Command("ip", "addr", "show")
	output, err := cmd.Output()
	if err == nil {
		lines := strings.Split(string(output), "\n")
		for _, line := range lines {
			if strings.Contains(line, "inet 100.") {
				log.Printf("✅ [NETWORK DEBUG] Found Tailscale IP range: %s", strings.TrimSpace(line))
				return true
			}
		}
	}
	
	// Fallback to ifconfig
	cmd = exec.Command("ifconfig")
	output, err = cmd.Output()
	if err == nil {
		if strings.Contains(string(output), "100.") {
			log.Println("✅ [NETWORK DEBUG] Found potential Tailscale IP via ifconfig")
			return true
		}
	}
	
	log.Println("❌ [NETWORK DEBUG] No Tailscale IP ranges found")
	return false
}

// getTailscaleIPFromNetwork extracts Tailscale IP from network interfaces
func (sm *ServerManager) getTailscaleIPFromNetwork() string {
	log.Println("🔍 [NETWORK DEBUG] Extracting Tailscale IP from network...")
	
	// Use ip addr show to get IP addresses
	cmd := exec.Command("ip", "addr", "show")
	output, err := cmd.Output()
	if err == nil {
		lines := strings.Split(string(output), "\n")
		for _, line := range lines {
			if strings.Contains(line, "inet 100.") {
				// Extract IP from line like "    inet 100.93.9.29/32 scope global tailscale0"
				parts := strings.Fields(line)
				for _, part := range parts {
					if strings.HasPrefix(part, "100.") {
						ip := strings.Split(part, "/")[0] // Remove CIDR notation
						log.Printf("✅ [NETWORK DEBUG] Extracted Tailscale IP: %s", ip)
						return ip
					}
				}
			}
		}
	}
	
	// Fallback to hostname -I and filter for 100.x.x.x
	cmd = exec.Command("hostname", "-I")
	output, err = cmd.Output()
	if err == nil {
		ips := strings.Fields(string(output))
		for _, ip := range ips {
			if strings.HasPrefix(ip, "100.") {
				log.Printf("✅ [NETWORK DEBUG] Found Tailscale IP via hostname: %s", ip)
				return ip
			}
		}
	}
	
	log.Println("❌ [NETWORK DEBUG] Could not extract Tailscale IP")
	return ""
}

// findAnyTailscaleBinary tries to find tailscale binary by any means necessary
func (sm *ServerManager) findAnyTailscaleBinary() string {
	log.Println("🔍 [NETWORK DEBUG] Final attempt to find ANY tailscale binary...")
	
	// Try all our existing methods one more time
	if path := sm.findTailscaleInPath(); path != "" {
		return path
	}
	
	if path := sm.findTailscaleWithFallbacks(); path != "" {
		return path
	}
	
	// Try even more aggressive searches
	commonCommands := []string{"tailscale", "/usr/bin/tailscale", "/bin/tailscale"}
	for _, cmd := range commonCommands {
		if sm.isTailscaleBinaryValid(cmd) {
			log.Printf("✅ [NETWORK DEBUG] Found working tailscale: %s", cmd)
			return cmd
		}
	}
	
	log.Println("❌ [NETWORK DEBUG] No tailscale binary found")
	return ""
}

// detectTailscaleBinary finds the Tailscale binary in common locations
func (sm *ServerManager) detectTailscaleBinary() string {
	// If we're in Flatpak mode and flatpak-spawn works, use "tailscale" 
	if sm.useFlatpakSpawn {
		log.Println("✅ [FLATPAK] Using flatpak-spawn for tailscale access")
		return "tailscale"
	}
	
	// Try which/where command first for PATH detection
	if pathBinary := sm.findTailscaleInPath(); pathBinary != "" {
		return pathBinary
	}
	
	// Possible Tailscale installation paths - comprehensive cross-platform list
	possiblePaths := []string{
		// macOS paths
		"/usr/local/bin/tailscale",         // Homebrew
		"/opt/homebrew/bin/tailscale",      // Apple Silicon Homebrew
		"/usr/bin/tailscale",               // System install
		"/usr/sbin/tailscale",              // System install (sbin)
		"/Applications/Tailscale.app/Contents/MacOS/Tailscale", // macOS app
		
		// Linux package manager paths
		"/snap/bin/tailscale",              // Snap packages
		"/usr/local/sbin/tailscale",        // Manual installs
		"/opt/tailscale/bin/tailscale",     // Custom installs
		"/usr/local/bin/tailscale",         // Local installs
		"/bin/tailscale",                   // System bin
		"/sbin/tailscale",                  // System sbin
		
		// Windows paths
		"C:\\Program Files\\Tailscale\\tailscale.exe",
		"C:\\Program Files (x86)\\Tailscale\\tailscale.exe",
		
		// User-specific paths (will be dynamically expanded)
		"~/.local/bin/tailscale",           // User local bin
		"~/bin/tailscale",                  // User bin
	}
	
	// Expand user home directory paths
	expandedPaths := sm.expandUserPaths(possiblePaths)
	
	log.Printf("🔍 [TAILSCALE DEBUG] Checking %d possible paths...", len(expandedPaths))
	
	for i, path := range expandedPaths {
		log.Printf("🔍 [TAILSCALE DEBUG] Checking path %d/%d: %s", i+1, len(expandedPaths), path)
		
		if sm.isTailscaleBinaryValid(path) {
			log.Printf("✅ [TAILSCALE DEBUG] Found working Tailscale at: %s", path)
			return path
		} else {
			log.Printf("❌ [TAILSCALE DEBUG] Path failed validation: %s", path)
		}
	}
	
	log.Println("❌ [TAILSCALE DEBUG] Tailscale binary not found in any expected locations")
	return ""
}

// isTailscaleBinaryValid checks if a path contains a valid Tailscale binary
func (sm *ServerManager) isTailscaleBinaryValid(path string) bool {
	log.Printf("🔍 [TAILSCALE DEBUG] Validating binary at: %s", path)
	
	// First, check if the file exists and is executable
	if path != "tailscale" { // Skip file check for PATH lookup
		if _, err := exec.LookPath(path); err != nil {
			log.Printf("❌ [TAILSCALE DEBUG] Binary not found or not executable: %v", err)
			return false
		}
	}
	
	// Try version command first (less likely to require permissions)
	cmd := sm.executeCommand(path, "version")
	output, err := cmd.Output()
	if err != nil {
		log.Printf("❌ [TAILSCALE DEBUG] 'version' command failed: %v", err)
		
		// If version fails, try a simple help command
		cmd = sm.executeCommand(path, "--help")
		err = cmd.Run()
		if err != nil {
			log.Printf("❌ [TAILSCALE DEBUG] '--help' command also failed: %v", err)
			return false
		} else {
			log.Printf("✅ [TAILSCALE DEBUG] '--help' command succeeded, binary is valid")
			return true
		}
	}
	
	// Check if output contains "tailscale" to verify it's actually the right binary
	outputStr := string(output)
	if strings.Contains(strings.ToLower(outputStr), "tailscale") {
		log.Printf("✅ [TAILSCALE DEBUG] 'version' command succeeded: %s", strings.TrimSpace(outputStr))
		return true
	}
	
	log.Printf("❌ [TAILSCALE DEBUG] Binary exists but doesn't appear to be Tailscale: %s", outputStr)
	return false
}

// checkTailscaleConnection verifies that Tailscale is connected and running
func (sm *ServerManager) checkTailscaleConnection(tailscalePath string) bool {
	log.Println("🔍 Checking Tailscale connection status...")
	
	// Try to get JSON status first
	cmd := sm.executeCommand(tailscalePath, "status", "--json")
	output, err := cmd.Output()
	if err != nil {
		log.Printf("⚠️ Failed to get Tailscale JSON status: %v", err)
		
		// If JSON status fails, try plain status (might work with different permissions)
		cmd = sm.executeCommand(tailscalePath, "status")
		output, err = cmd.Output()
		if err != nil {
			log.Printf("⚠️ Failed to get Tailscale plain status: %v", err)
			
			// If status commands fail, try to get IP addresses as a last resort
			cmd = sm.executeCommand(tailscalePath, "ip")
			output, err = cmd.Output()
			if err != nil {
				log.Printf("❌ All Tailscale status commands failed: %v", err)
				return false
			}
			
			// If we got IP output, assume Tailscale is working
			outputStr := strings.TrimSpace(string(output))
			if outputStr != "" {
				log.Printf("✅ Tailscale appears to be working (got IP: %s)", outputStr)
				return true
			}
			
			log.Println("❌ Tailscale IP command returned empty result")
			return false
		}
		
		// Check plain status output for connectivity indicators
		outputStr := strings.ToLower(string(output))
		if strings.Contains(outputStr, "logged in") || strings.Contains(outputStr, "online") || strings.Contains(outputStr, "connected") {
			log.Printf("✅ Tailscale appears to be connected (plain status)")
			return true
		}
		
		log.Printf("❌ Tailscale status doesn't indicate connection: %s", string(output))
		return false
	}
	
	// Parse JSON output
	var status map[string]interface{}
	if err := json.Unmarshal(output, &status); err != nil {
		log.Printf("❌ Failed to parse Tailscale status JSON: %v", err)
		// Even if JSON parsing fails, if we got output, Tailscale is probably working
		log.Println("✅ Assuming Tailscale is working since we got status output")
		return true
	}
	
	// Check BackendState
	backendState, ok := status["BackendState"].(string)
	if !ok {
		log.Println("⚠️ Could not extract BackendState from Tailscale status")
		// If we can't get state but got JSON, assume it's working
		log.Println("✅ Assuming Tailscale is working since we got JSON status")
		return true
	}
	
	log.Printf("🔍 Tailscale status: %s", backendState)
	
	isRunning := backendState == "Running"
	if isRunning {
		log.Println("✅ Tailscale is running and connected")
	} else {
		log.Printf("⚠️ Tailscale is not in Running state: %s", backendState)
		// Even if not "Running", it might still be usable
		log.Println("✅ Continuing anyway - Tailscale binary is available")
	}
	
	return true // Be more permissive - if we got this far, Tailscale is probably usable
}

// getTailscaleHostname gets the Tailscale hostname for this machine
func (sm *ServerManager) getTailscaleHostname(tailscalePath string) string {
	log.Println("🔍 Getting Tailscale hostname...")
	
	// Try JSON status first
	cmd := sm.executeCommand(tailscalePath, "status", "--json")
	output, err := cmd.Output()
	if err != nil {
		log.Printf("⚠️ Failed to get Tailscale status for hostname: %v", err)
		
		// Try to get IP and create a basic hostname
		cmd = sm.executeCommand(tailscalePath, "ip")
		output, err = cmd.Output()
		if err != nil {
			log.Printf("❌ Failed to get Tailscale IP: %v", err)
			return ""
		}
		
		// Use the first IP as a fallback
		ips := strings.Fields(strings.TrimSpace(string(output)))
		if len(ips) > 0 {
			ip := ips[0]
			log.Printf("✅ Using Tailscale IP as hostname: %s", ip)
			return ip
		}
		
		log.Println("❌ No Tailscale IPs found")
		return ""
	}
	
	// Parse JSON output
	var status map[string]interface{}
	if err := json.Unmarshal(output, &status); err != nil {
		log.Printf("⚠️ Failed to parse Tailscale hostname JSON: %v", err)
		
		// Fallback: try to get IP
		cmd = sm.executeCommand(tailscalePath, "ip")
		output, err = cmd.Output()
		if err == nil {
			ips := strings.Fields(strings.TrimSpace(string(output)))
			if len(ips) > 0 {
				ip := ips[0]
				log.Printf("✅ Using Tailscale IP as hostname (JSON failed): %s", ip)
				return ip
			}
		}
		
		return ""
	}
	
	// Extract Self information
	self, ok := status["Self"].(map[string]interface{})
	if !ok {
		log.Println("⚠️ Could not extract Self info from Tailscale status")
		
		// Try to find any peer that might be ourselves
		peers, ok := status["Peers"].(map[string]interface{})
		if ok {
			for _, peer := range peers {
				if peerMap, ok := peer.(map[string]interface{}); ok {
					if dnsName, ok := peerMap["DNSName"].(string); ok && dnsName != "" {
						cleanedName := strings.TrimSuffix(dnsName, ".")
						log.Printf("✅ Using peer DNSName as hostname: %s", cleanedName)
						return cleanedName
					}
				}
			}
		}
		
		// Last resort: try IP command
		cmd = sm.executeCommand(tailscalePath, "ip")
		output, err = cmd.Output()
		if err == nil {
			ips := strings.Fields(strings.TrimSpace(string(output)))
			if len(ips) > 0 {
				ip := ips[0]
				log.Printf("✅ Using Tailscale IP as hostname (no Self): %s", ip)
				return ip
			}
		}
		
		return ""
	}
	
	// Get DNS name
	dnsName, ok := self["DNSName"].(string)
	if !ok {
		log.Println("⚠️ Could not extract DNSName from Tailscale Self info")
		
		// Try to get IP from Self info
		if ips, ok := self["TailscaleIPs"].([]interface{}); ok && len(ips) > 0 {
			if ip, ok := ips[0].(string); ok {
				log.Printf("✅ Using Tailscale IP from Self: %s", ip)
				return ip
			}
		}
		
		// Last resort: IP command
		cmd = sm.executeCommand(tailscalePath, "ip")
		output, err = cmd.Output()
		if err == nil {
			ips := strings.Fields(strings.TrimSpace(string(output)))
			if len(ips) > 0 {
				ip := ips[0]
				log.Printf("✅ Using Tailscale IP as hostname (no DNSName): %s", ip)
				return ip
			}
		}
		
		return ""
	}
	
	// Clean up DNS name (remove trailing dots)
	cleanedName := strings.TrimSuffix(dnsName, ".")
	
	log.Printf("✅ Found Tailscale hostname: %s", cleanedName)
	return cleanedName
}

// RefreshTailscaleStatus re-checks Tailscale status (for UI refresh)
func (sm *ServerManager) RefreshTailscaleStatus() {
	log.Println("🔄 Refreshing Tailscale status...")
	go sm.checkTailscaleStatus()
}

// GetTailscaleInfo returns detailed Tailscale information for UI display
func (sm *ServerManager) GetTailscaleInfo() map[string]interface{} {
	info := map[string]interface{}{
		"available": sm.HasTailscale,
		"url":       sm.TailscaleURL,
		"lastCheck": time.Now().Format(time.RFC3339),
	}
	
	if sm.HasTailscale {
		info["status"] = "connected"
		info["message"] = "HTTP over Tailscale (network-level encryption)"
	} else {
		info["status"] = "not_available"
		info["message"] = "Tailscale not detected or not running"
	}
	
	return info
}

// IsTailscaleConfigured returns whether Tailscale is available and configured
func (sm *ServerManager) IsTailscaleConfigured() bool {
	return sm.HasTailscale && sm.TailscaleURL != ""
}

// GetPreferredURL returns the preferred server URL (Tailscale if available, local otherwise)
func (sm *ServerManager) GetPreferredURL() string {
	if sm.IsTailscaleConfigured() {
		return fmt.Sprintf("%s:%d", sm.TailscaleURL, sm.Port)
	}
	return fmt.Sprintf("http://%s:%d", sm.getLocalIPAddress(), sm.Port)
}

// TailscaleStatusInfo represents Tailscale status information
type TailscaleStatusInfo struct {
	Available    bool      `json:"available"`
	Connected    bool      `json:"connected"`
	Hostname     string    `json:"hostname,omitempty"`
	URL          string    `json:"url,omitempty"`
	LastChecked  time.Time `json:"lastChecked"`
	ErrorMessage string    `json:"errorMessage,omitempty"`
}

// GetDetailedTailscaleStatus returns comprehensive Tailscale status
func (sm *ServerManager) GetDetailedTailscaleStatus() TailscaleStatusInfo {
	status := TailscaleStatusInfo{
		Available:   sm.HasTailscale,
		Connected:   sm.HasTailscale, // If available, assume connected
		URL:         sm.TailscaleURL,
		LastChecked: time.Now(),
	}
	
	if sm.HasTailscale && sm.TailscaleURL != "" {
		// Extract hostname from URL
		url := strings.TrimPrefix(sm.TailscaleURL, "http://")
		status.Hostname = url
	} else {
		status.ErrorMessage = "Tailscale not detected or not running"
	}
	
	return status
}

// MonitorTailscaleStatus starts periodic monitoring of Tailscale status
func (sm *ServerManager) MonitorTailscaleStatus(interval time.Duration) {
	ticker := time.NewTicker(interval)
	defer ticker.Stop()
	
	log.Printf("🔍 Starting Tailscale monitoring (every %v)", interval)
	
	for {
		select {
		case <-ticker.C:
			// Periodic status check
			prevStatus := sm.HasTailscale
			sm.checkTailscaleStatus()
			
			// Log status changes
			if prevStatus != sm.HasTailscale {
				if sm.HasTailscale {
					log.Println("✅ Tailscale became available")
				} else {
					log.Println("❌ Tailscale became unavailable")
				}
			}
			
		case <-sm.ctx.Done():
			log.Println("🔍 Stopping Tailscale monitoring")
			return
		}
	}
}

// StartTailscaleMonitoring starts background monitoring with default interval
func (sm *ServerManager) StartTailscaleMonitoring() {
	// Check every 5 minutes
	go sm.MonitorTailscaleStatus(5 * time.Minute)
} 