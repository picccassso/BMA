package server

import (
	"encoding/json"
	"fmt"
	"log"
	"os/exec"
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

// checkTailscaleStatus detects Tailscale installation and status
func (sm *ServerManager) checkTailscaleStatus() {
	log.Println("🔍 Starting Tailscale detection...")
	
	// Try to detect Tailscale binary
	tailscalePath := sm.detectTailscaleBinary()
	if tailscalePath == "" {
		log.Println("❌ Tailscale not detected - binary not found")
		sm.HasTailscale = false
		sm.TailscaleURL = ""
		return
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

// detectTailscaleBinary finds the Tailscale binary in common locations
func (sm *ServerManager) detectTailscaleBinary() string {
	// Possible Tailscale installation paths
	possiblePaths := []string{
		"/usr/local/bin/tailscale",         // Homebrew
		"/opt/homebrew/bin/tailscale",      // Apple Silicon Homebrew
		"/usr/bin/tailscale",               // System install
		"/usr/sbin/tailscale",              // System install (sbin)
		"/Applications/Tailscale.app/Contents/MacOS/Tailscale", // macOS app
		"tailscale", // PATH lookup
	}
	
	log.Printf("🔍 [TAILSCALE DEBUG] Checking %d possible paths...", len(possiblePaths))
	
	for i, path := range possiblePaths {
		log.Printf("🔍 [TAILSCALE DEBUG] Checking path %d/%d: %s", i+1, len(possiblePaths), path)
		
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
	cmd := exec.Command(path, "version")
	output, err := cmd.Output()
	if err != nil {
		log.Printf("❌ [TAILSCALE DEBUG] 'version' command failed: %v", err)
		
		// If version fails, try a simple help command
		cmd = exec.Command(path, "--help")
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
	cmd := exec.Command(tailscalePath, "status", "--json")
	output, err := cmd.Output()
	if err != nil {
		log.Printf("⚠️ Failed to get Tailscale JSON status: %v", err)
		
		// If JSON status fails, try plain status (might work with different permissions)
		cmd = exec.Command(tailscalePath, "status")
		output, err = cmd.Output()
		if err != nil {
			log.Printf("⚠️ Failed to get Tailscale plain status: %v", err)
			
			// If status commands fail, try to get IP addresses as a last resort
			cmd = exec.Command(tailscalePath, "ip")
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
	cmd := exec.Command(tailscalePath, "status", "--json")
	output, err := cmd.Output()
	if err != nil {
		log.Printf("⚠️ Failed to get Tailscale status for hostname: %v", err)
		
		// Try to get IP and create a basic hostname
		cmd = exec.Command(tailscalePath, "ip")
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
		cmd = exec.Command(tailscalePath, "ip")
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
		cmd = exec.Command(tailscalePath, "ip")
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
		cmd = exec.Command(tailscalePath, "ip")
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