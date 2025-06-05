package server

import (
	"context"
	"log"
	"net/http"
	"strings"
	"time"
)

// AuthContextKey is used for storing auth data in request context
type AuthContextKey string

const (
	TokenContextKey AuthContextKey = "token"
	UserAgentContextKey AuthContextKey = "userAgent"
	ClientIPContextKey AuthContextKey = "clientIP"
)

// AuthMiddleware provides Bearer token authentication for protected endpoints
type AuthMiddleware struct {
	serverManager *ServerManager
}

// NewAuthMiddleware creates a new authentication middleware
func NewAuthMiddleware(sm *ServerManager) *AuthMiddleware {
	return &AuthMiddleware{
		serverManager: sm,
	}
}

// RequireAuth returns a middleware function that enforces Bearer token authentication
func (am *AuthMiddleware) RequireAuth(next http.HandlerFunc) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		// Extract Authorization header
		authHeader := r.Header.Get("Authorization")
		if authHeader == "" {
			log.Println("❌ [AUTH] Missing authorization header")
			writeAuthError(w, "Missing authorization token", http.StatusUnauthorized)
			return
		}
		
		// Validate Bearer token format
		if !strings.HasPrefix(authHeader, "Bearer ") {
			log.Println("❌ [AUTH] Invalid authorization header format")
			writeAuthError(w, "Invalid authorization format", http.StatusUnauthorized)
			return
		}
		
		// Extract token
		token := strings.TrimPrefix(authHeader, "Bearer ")
		if len(token) == 0 {
			log.Println("❌ [AUTH] Empty token")
			writeAuthError(w, "Empty authorization token", http.StatusUnauthorized)
			return
		}
		
		// Validate token
		if !am.serverManager.IsValidToken(token) {
			log.Printf("❌ [AUTH] Invalid or expired token: %s...", truncateToken(token))
			writeAuthError(w, "Invalid or expired token", http.StatusUnauthorized)
			return
		}
		
		// Extract client information
		clientIP := extractClientIP(r)
		userAgent := r.Header.Get("User-Agent")
		if userAgent == "" {
			userAgent = "unknown"
		}
		
		log.Printf("✅ [AUTH] Valid token: %s... from %s", truncateToken(token), clientIP)
		
		// Track device connection
		am.serverManager.TrackDeviceConnection(token, clientIP, userAgent)
		
		// Add auth data to request context
		ctx := context.WithValue(r.Context(), TokenContextKey, token)
		ctx = context.WithValue(ctx, ClientIPContextKey, clientIP)
		ctx = context.WithValue(ctx, UserAgentContextKey, userAgent)
		
		// Call next handler with enriched context
		next(w, r.WithContext(ctx))
	}
}

// TokenValidator provides token validation functionality
type TokenValidator struct {
	serverManager *ServerManager
}

// NewTokenValidator creates a new token validator
func NewTokenValidator(sm *ServerManager) *TokenValidator {
	return &TokenValidator{
		serverManager: sm,
	}
}

// ValidateToken checks if a token is valid and not expired
func (tv *TokenValidator) ValidateToken(token string) bool {
	return tv.serverManager.IsValidToken(token)
}

// GetTokenInfo returns information about a token (for debugging)
func (tv *TokenValidator) GetTokenInfo(token string) map[string]interface{} {
	isValid := tv.ValidateToken(token)
	
	info := map[string]interface{}{
		"token":   truncateToken(token),
		"valid":   isValid,
		"created": time.Now().Format(time.RFC3339),
	}
	
	if !isValid {
		info["reason"] = "invalid or expired"
	}
	
	return info
}

// Helper functions

// extractClientIP gets the real client IP, considering proxy headers
func extractClientIP(r *http.Request) string {
	// Check X-Forwarded-For header (most common proxy header)
	if forwarded := r.Header.Get("X-Forwarded-For"); forwarded != "" {
		// X-Forwarded-For can contain multiple IPs, take the first one
		ips := strings.Split(forwarded, ",")
		return strings.TrimSpace(ips[0])
	}
	
	// Check X-Real-IP header (Nginx)
	if realIP := r.Header.Get("X-Real-IP"); realIP != "" {
		return realIP
	}
	
	// Check X-Original-Forwarded-For header
	if originalForwarded := r.Header.Get("X-Original-Forwarded-For"); originalForwarded != "" {
		ips := strings.Split(originalForwarded, ",")
		return strings.TrimSpace(ips[0])
	}
	
	// Fall back to RemoteAddr
	return r.RemoteAddr
}

// truncateToken safely truncates a token for logging
func truncateToken(token string) string {
	if len(token) <= 8 {
		return strings.Repeat("*", len(token))
	}
	return token[:8] + "..."
}

// writeAuthError writes a standardized authentication error response
func writeAuthError(w http.ResponseWriter, message string, statusCode int) {
	w.Header().Set("Content-Type", "application/json")
	w.Header().Set("WWW-Authenticate", "Bearer")
	w.WriteHeader(statusCode)
	
	response := map[string]interface{}{
		"error":   "authentication_failed",
		"message": message,
		"status":  statusCode,
	}
	
	// Don't log error if we can't write JSON response
	_ = writeJSONResponse(w, response)
}

// AuthenticatedRequest represents a request that has passed authentication
type AuthenticatedRequest struct {
	Token     string
	ClientIP  string
	UserAgent string
	Original  *http.Request
}

// NewAuthenticatedRequest creates an AuthenticatedRequest from context
func NewAuthenticatedRequest(r *http.Request) *AuthenticatedRequest {
	token, _ := r.Context().Value(TokenContextKey).(string)
	clientIP, _ := r.Context().Value(ClientIPContextKey).(string)
	userAgent, _ := r.Context().Value(UserAgentContextKey).(string)
	
	return &AuthenticatedRequest{
		Token:     token,
		ClientIP:  clientIP,
		UserAgent: userAgent,
		Original:  r,
	}
}

// LogAccess logs an authenticated request access
func (ar *AuthenticatedRequest) LogAccess(endpoint string) {
	log.Printf("🔓 [ACCESS] %s %s by %s (token: %s...)", 
		ar.Original.Method, 
		endpoint,
		ar.ClientIP,
		truncateToken(ar.Token))
} 