package models

import (
	"encoding/json"
	"os"
	"path/filepath"
)

// Config represents the application configuration
type Config struct {
	SetupComplete bool   `json:"setupComplete"`
	MusicFolder   string `json:"musicFolder,omitempty"`
}

// GetConfigPath returns the path to the config file
func GetConfigPath() (string, error) {
	homeDir, err := os.UserHomeDir()
	if err != nil {
		return "", err
	}
	
	configDir := filepath.Join(homeDir, ".bma")
	if err := os.MkdirAll(configDir, 0755); err != nil {
		return "", err
	}
	
	return filepath.Join(configDir, "config.json"), nil
}

// LoadConfig loads the configuration from file or returns default config
func LoadConfig() (*Config, error) {
	configPath, err := GetConfigPath()
	if err != nil {
		return nil, err
	}
	
	// If config file doesn't exist, return default config
	if _, err := os.Stat(configPath); os.IsNotExist(err) {
		return &Config{
			SetupComplete: false,
		}, nil
	}
	
	data, err := os.ReadFile(configPath)
	if err != nil {
		return nil, err
	}
	
	var config Config
	if err := json.Unmarshal(data, &config); err != nil {
		return nil, err
	}
	
	return &config, nil
}

// SaveConfig saves the configuration to file
func (c *Config) SaveConfig() error {
	configPath, err := GetConfigPath()
	if err != nil {
		return err
	}
	
	data, err := json.MarshalIndent(c, "", "  ")
	if err != nil {
		return err
	}
	
	return os.WriteFile(configPath, data, 0644)
}

// MarkSetupComplete marks the setup as complete and saves the config
func (c *Config) MarkSetupComplete() error {
	c.SetupComplete = true
	return c.SaveConfig()
}

// SetMusicFolder sets the music folder path and saves the config
func (c *Config) SetMusicFolder(folderPath string) error {
	c.MusicFolder = folderPath
	return c.SaveConfig()
} 