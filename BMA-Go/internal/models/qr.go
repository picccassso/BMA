package models

import (
	"encoding/base64"
	"encoding/json"
	"time"

	"github.com/skip2/go-qrcode"
)

// PairingData represents the data structure for QR code pairing
// Matches the JSON format expected by the Android app
type PairingData struct {
	ServerURL string    `json:"serverUrl"`
	Token     string    `json:"token"`
	ExpiresAt time.Time `json:"expiresAt"`
}

// QRCodeGenerator handles QR code generation for device pairing
// Equivalent to QRCodeGenerator.swift in the macOS version
type QRCodeGenerator struct{}

// NewQRCodeGenerator creates a new QR code generator instance
func NewQRCodeGenerator() *QRCodeGenerator {
	return &QRCodeGenerator{}
}

// GeneratePairingQR creates a QR code containing pairing information
func (qr *QRCodeGenerator) GeneratePairingQR(serverURL, token string, expiresAt time.Time) ([]byte, error) {
	// Create pairing data structure (same as macOS version)
	pairingData := PairingData{
		ServerURL: serverURL,
		Token:     token,
		ExpiresAt: expiresAt,
	}
	
	// Convert to JSON string
	jsonData, err := json.Marshal(pairingData)
	if err != nil {
		return nil, err
	}
	
	// Generate QR code image (PNG format)
	// Size 512x512 pixels with medium error correction for better scanning
	qrCode, err := qrcode.Encode(string(jsonData), qrcode.Medium, 512)
	if err != nil {
		return nil, err
	}
	
	return qrCode, nil
}

// GeneratePairingQRString creates a QR code as base64 string for easy display
func (qr *QRCodeGenerator) GeneratePairingQRString(serverURL, token string, expiresAt time.Time) (string, error) {
	qrBytes, err := qr.GeneratePairingQR(serverURL, token, expiresAt)
	if err != nil {
		return "", err
	}
	
	// Convert to base64 for easy embedding in UI
	return base64.StdEncoding.EncodeToString(qrBytes), nil
}

// GetPairingDataJSON returns just the JSON string for debugging/testing
func (qr *QRCodeGenerator) GetPairingDataJSON(serverURL, token string, expiresAt time.Time) (string, error) {
	pairingData := PairingData{
		ServerURL: serverURL,
		Token:     token,
		ExpiresAt: expiresAt,
	}
	
	jsonData, err := json.MarshalIndent(pairingData, "", "  ")
	if err != nil {
		return "", err
	}
	
	return string(jsonData), nil
}

// TODO: Phase 4 Implementation
// - QR code generation using github.com/skip2/go-qrcode
// - Token generation and management
// - Expiration handling
// - Display in Fyne GUI 