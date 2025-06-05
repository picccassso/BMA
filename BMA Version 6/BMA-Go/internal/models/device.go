package models

import (
	"time"

	"github.com/google/uuid"
)

// ConnectedDevice represents a device connected to the BMA server
type ConnectedDevice struct {
	ID          uuid.UUID `json:"id"`
	Token       string    `json:"token"`
	DeviceName  string    `json:"deviceName,omitempty"`
	IPAddress   string    `json:"ipAddress"`
	UserAgent   string    `json:"userAgent,omitempty"`
	ConnectedAt time.Time `json:"connectedAt"`
	LastSeenAt  time.Time `json:"lastSeenAt"`
}

// TODO: Phase 2 & 4 Implementation
// - Device tracking and management
// - Activity monitoring
// - Cleanup of inactive devices 