package com.bma.android

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bma.android.adapters.SongAdapter
import com.bma.android.api.ApiClient
import com.bma.android.databinding.ActivityMainBinding
import com.bma.android.models.Song
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private lateinit var songAdapter: SongAdapter
    private var songs = mutableListOf<Song>()
    
    // DEBUG: Add logging for connection state changes
    private fun debugLog(message: String) {
        println("🔍 [MainActivity] $message")
    }
    
    private val qrScannerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            debugLog("QR code scanning successful, reloading connection")
            // QR code scanning was successful, reload the UI
            loadSavedConnection()
            connectToServer()
        } else {
            debugLog("QR code scanning cancelled or failed")
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        debugLog("MainActivity created, initializing UI")
        setupUI()
        setupRecyclerView()
        loadSavedConnection()
        
        // Start periodic connection monitoring for auto-detection
        startConnectionMonitoring()
    }
    
    private fun setupUI() {
        debugLog("Setting up UI event handlers")
        
        // Smart connection button - behavior changes based on state
        binding.connectButton.setOnClickListener {
            val hasServerUrl = ApiClient.getServerUrl().isNotEmpty()
            val hasAuthToken = ApiClient.isAuthenticated()
            
            when {
                // Case 1: No server URL - manual entry mode
                !hasServerUrl -> {
                    val url = binding.serverUrlInput.text.toString()
                    if (url.isNotEmpty()) {
                        debugLog("Manual connection attempt to: $url")
                        ApiClient.setServerUrl(url)
                        saveServerUrl(url)
                        connectToServer()
                    } else {
                        debugLog("Connect button clicked but no URL provided")
                        Toast.makeText(this, "Please enter server URL", Toast.LENGTH_SHORT).show()
                    }
                }
                
                // Case 2: Has server URL but no auth token - smart reconnect mode
                hasServerUrl && !hasAuthToken -> {
                    debugLog("Smart reconnect: Launching QR scanner for reconnection")
                    startQRScanner()
                }
                
                // Case 3: Has both URL and token - normal connection attempt
                hasServerUrl && hasAuthToken -> {
                    debugLog("Normal connection attempt with existing credentials")
                    connectToServer()
                }
            }
        }
        
        // Refresh connection (reconnect with current settings)
        binding.refreshButton.setOnClickListener {
            debugLog("Refresh button clicked - attempting reconnection")
            connectToServer()
        }
        
        // QR Code scanner
        binding.scanQrButton.setOnClickListener {
            debugLog("QR scan button clicked")
            startQRScanner()
        }
        
        // NEW: Disconnect functionality
        binding.disconnectButton.setOnClickListener {
            debugLog("Disconnect button clicked")
            disconnectFromServer()
        }
    }
    
    private fun setupRecyclerView() {
        songAdapter = SongAdapter(songs) { song ->
            debugLog("Song selected: ${song.title} by ${song.artist}")
            // Open player activity
            val intent = Intent(this, PlayerActivity::class.java).apply {
                putExtra("song_id", song.id)
                putExtra("song_title", song.title)
                putExtra("song_artist", song.artist)
            }
            startActivity(intent)
        }
        
        binding.songsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = songAdapter
        }
    }
    
    private fun loadSavedConnection() {
        debugLog("Loading saved connection details from preferences")
        val prefs = getSharedPreferences("BMA", MODE_PRIVATE)
        val savedUrl = prefs.getString("server_url", "")
        val savedToken = prefs.getString("auth_token", null)
        
        if (!savedUrl.isNullOrEmpty()) {
            debugLog("Found saved server URL: $savedUrl")
            binding.serverUrlInput.setText(savedUrl)
            ApiClient.setServerUrl(savedUrl)
        } else {
            debugLog("No saved server URL found")
            // Set a default placeholder for manual entry
            binding.serverUrlInput.hint = "e.g., http://192.168.1.100:8008 or https://hostname.ts.net:8443"
        }
        
        if (!savedToken.isNullOrEmpty()) {
            debugLog("Found saved auth token: ${savedToken.take(8)}...")
            ApiClient.setAuthToken(savedToken)
        } else {
            debugLog("No saved auth token found")
        }
        
        // Update UI based on connection state
        updateConnectionStatus()
        updateUIVisibility()
    }
    
    /**
     * NEW: Smart UI visibility management based on connection state
     * When connected: Hide connection controls, show disconnect button
     * When disconnected: Show connection controls, hide disconnect button
     * ENHANCED: Smart connect button text and behavior
     */
    private fun updateUIVisibility() {
        val isConnected = ApiClient.isAuthenticated() && ApiClient.getServerUrl().isNotEmpty()
        val hasServerUrl = ApiClient.getServerUrl().isNotEmpty()
        val hasAuthToken = ApiClient.isAuthenticated()
        
        debugLog("Updating UI visibility - Connected: $isConnected, HasURL: $hasServerUrl, HasToken: $hasAuthToken")
        
        if (isConnected) {
            // CONNECTED STATE: Hide connection controls
            debugLog("UI State: CONNECTED - Hiding connection controls")
            binding.scanQrButton.visibility = View.GONE
            binding.manualEntryText.visibility = View.GONE
            binding.manualEntryHelpText.visibility = View.GONE
            binding.serverUrlInputLayout.visibility = View.GONE
            binding.connectButton.visibility = View.GONE
            
            // Show disconnect controls
            binding.disconnectButton.visibility = View.VISIBLE
            
            // Show refresh button for reconnection if needed
            binding.refreshButton.visibility = View.VISIBLE
            
        } else {
            // DISCONNECTED STATE: Show connection controls with smart button behavior
            debugLog("UI State: DISCONNECTED - Showing connection controls")
            binding.scanQrButton.visibility = View.VISIBLE
            binding.manualEntryText.visibility = View.VISIBLE
            binding.manualEntryHelpText.visibility = View.VISIBLE
            binding.serverUrlInputLayout.visibility = View.VISIBLE
            binding.connectButton.visibility = View.VISIBLE
            
            // Hide disconnect controls
            binding.disconnectButton.visibility = View.GONE
            
            // SMART BUTTON: Change text and behavior based on state
            when {
                // Case 1: No server URL - show manual entry mode
                !hasServerUrl -> {
                    binding.connectButton.text = "Connect"
                    debugLog("Connect button mode: MANUAL ENTRY")
                }
                
                // Case 2: Has server URL but no auth token - smart reconnect mode
                hasServerUrl && !hasAuthToken -> {
                    binding.connectButton.text = "📱 Scan QR to Reconnect"
                    debugLog("Connect button mode: SMART RECONNECT")
                }
                
                // Case 3: Has both (shouldn't happen in disconnected state, but just in case)
                hasServerUrl && hasAuthToken -> {
                    binding.connectButton.text = "Connect"
                    debugLog("Connect button mode: NORMAL CONNECTION")
                }
            }
            
            // Show refresh only if we have server details
            binding.refreshButton.visibility = if (hasServerUrl) View.VISIBLE else View.GONE
        }
    }
    
    /**
     * NEW: Disconnect from server with proper cleanup
     * Now includes server notification for proper device tracking
     */
    private fun disconnectFromServer() {
        debugLog("Disconnecting from server and clearing credentials")
        
        // Start async disconnect process
        lifecycleScope.launch {
            var serverNotified = false
            
            // First, try to notify server if we have auth token
            if (ApiClient.isAuthenticated()) {
                debugLog("Attempting to notify server about disconnect...")
                
                try {
                    val authHeader = ApiClient.getAuthHeader()
                    if (authHeader != null) {
                        val response = ApiClient.api.disconnect(authHeader)
                        debugLog("Server disconnect response: $response")
                        serverNotified = true
                    }
                } catch (e: Exception) {
                    debugLog("Failed to notify server about disconnect: ${e.message}")
                    // Continue with local disconnect even if server notification fails
                }
            }
            
            // Then clear local data (regardless of server notification result)
            runOnUiThread {
                performLocalDisconnect(serverNotified)
            }
        }
    }
    
    /**
     * NEW: Perform local disconnect cleanup
     */
    private fun performLocalDisconnect(serverNotified: Boolean) {
        debugLog("Performing local disconnect cleanup (server notified: $serverNotified)")
        
        // Clear authentication token
        ApiClient.setAuthToken(null)
        
        // Clear saved credentials from preferences
        getSharedPreferences("BMA", MODE_PRIVATE).edit()
            .remove("auth_token")
            .apply()
        
        // Clear songs list
        songs.clear()
        songAdapter.notifyDataSetChanged()
        
        // Update status and UI
        val statusMessage = if (serverNotified) {
            "Disconnected from server"
        } else {
            "Disconnected locally (server may still show as connected)"
        }
        
        binding.statusText.text = statusMessage
        updateConnectionStatus()
        updateUIVisibility()
        
        // Show empty state
        binding.emptyText.visibility = View.VISIBLE
        binding.emptyText.text = "Disconnected - Scan QR code or enter server URL to connect"
        binding.songsRecyclerView.visibility = View.GONE
        
        debugLog("Local disconnection complete - UI updated with smart reconnect button")
        Toast.makeText(this, statusMessage, Toast.LENGTH_SHORT).show()
    }
    
    /**
     * NEW: Periodic connection monitoring for auto-detection of disconnection
     */
    private fun startConnectionMonitoring() {
        debugLog("Starting periodic connection monitoring")
        
        lifecycleScope.launch {
            while (true) {
                // Check connection every 30 seconds if authenticated
                kotlinx.coroutines.delay(30000)
                
                if (ApiClient.isAuthenticated() && ApiClient.getServerUrl().isNotEmpty()) {
                    debugLog("Monitoring: Checking connection health")
                    
                    try {
                        // Quick health check to detect disconnection
                        val health = ApiClient.api.checkHealth()
                        if (health["status"] != "healthy") {
                            debugLog("Monitoring: Server not healthy - triggering disconnect")
                            handleConnectionLost()
                        }
                    } catch (e: Exception) {
                        debugLog("Monitoring: Connection lost - ${e.message}")
                        handleConnectionLost()
                    }
                }
            }
        }
    }
    
    /**
     * NEW: Handle automatic disconnection detection
     */
    private fun handleConnectionLost() {
        debugLog("Connection lost detected - updating UI")
        
        runOnUiThread {
            binding.statusText.text = "Connection lost - Reconnect or scan QR code"
            binding.connectionStatus.text = "Connection lost"
            binding.connectionStatus.setTextColor(getColor(android.R.color.holo_red_dark))
            
            // Keep auth token but show connection controls for reconnection
            updateUIVisibility()
            
            Toast.makeText(this, "Connection lost. Please reconnect.", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun updateConnectionStatus() {
        val isAuthenticated = ApiClient.isAuthenticated()
        val serverUrl = ApiClient.getServerUrl()
        
        debugLog("Updating connection status - Auth: $isAuthenticated, URL: $serverUrl")
        
        if (isAuthenticated && serverUrl.isNotEmpty()) {
            val protocol = when {
                serverUrl.contains(".ts.net") -> "HTTP via Tailscale (Secure)"
                serverUrl.startsWith("https://") -> "HTTPS (Secure)"
                else -> "HTTP (Local)"
            }
            binding.connectionStatus.text = "✅ Connected via $protocol to: $serverUrl"
            binding.connectionStatus.setTextColor(getColor(android.R.color.holo_green_dark))
        } else if (serverUrl.isNotEmpty()) {
            val protocol = when {
                serverUrl.contains(".ts.net") -> "HTTP via Tailscale"
                serverUrl.startsWith("https://") -> "HTTPS"
                else -> "HTTP"
            }
            binding.connectionStatus.text = "⚠️ Server set ($protocol) but not authenticated"
            binding.connectionStatus.setTextColor(getColor(android.R.color.holo_orange_dark))
        } else {
            binding.connectionStatus.text = "❌ Not connected - Scan QR code or enter server URL"
            binding.connectionStatus.setTextColor(getColor(android.R.color.holo_red_dark))
        }
    }
    
    private fun startQRScanner() {
        debugLog("Starting QR scanner activity")
        val intent = Intent(this, QRScannerActivity::class.java)
        qrScannerLauncher.launch(intent)
    }
    
    private fun connectToServer() {
        val serverUrl = ApiClient.getServerUrl()
        if (serverUrl.isEmpty()) {
            debugLog("Connection attempted but no server URL set")
            showError("Please scan QR code or enter a server URL first")
            return
        }
        
        debugLog("Attempting connection to: $serverUrl")
        binding.progressBar.visibility = View.VISIBLE
        val protocol = when {
            serverUrl.contains(".ts.net") -> "HTTP via Tailscale"
            serverUrl.startsWith("https://") -> "HTTPS"
            else -> "HTTP"
        }
        binding.statusText.text = "Connecting via $protocol..."
        
        lifecycleScope.launch {
            try {
                // Check server health first
                debugLog("Checking server health...")
                binding.statusText.text = "Checking server health..."
                val health = ApiClient.api.checkHealth()
                
                if (health["status"] == "healthy") {
                    debugLog("Server is healthy, checking authentication...")
                    binding.statusText.text = "Server is healthy, checking authentication..."
                    
                    if (ApiClient.isAuthenticated()) {
                        debugLog("Authentication valid, loading songs...")
                        binding.statusText.text = "Connected to BMA server!"
                        loadSongs()
                        // Update UI visibility after successful connection
                        updateUIVisibility()
                    } else {
                        debugLog("Server healthy but not authenticated")
                        binding.statusText.text = "Server healthy but not authenticated. Please scan QR code."
                        binding.progressBar.visibility = View.GONE
                        showError("Authentication required. Please scan QR code to get access token.")
                    }
                } else {
                    debugLog("Server health check failed: $health")
                    showError("Server is not healthy: ${health}")
                }
            } catch (e: Exception) {
                debugLog("Connection failed: ${e.message}")
                val errorMessage = when {
                    e.message?.contains("CLEARTEXT communication") == true -> {
                        "HTTP connections blocked by Android security policy. Please use HTTPS or configure network security."
                    }
                    e.message?.contains("Failed to connect") == true -> {
                        "Cannot reach server. Check if:\n• Server is running\n• URL is correct\n• Network connectivity is available\n• Firewall allows connection"
                    }
                    e.message?.contains("timeout") == true -> {
                        "Connection timeout. Server may be overloaded or network is slow."
                    }
                    e.message?.contains("SSL") == true || e.message?.contains("certificate") == true -> {
                        "HTTPS certificate issue. This is normal for local/development servers."
                    }
                    e.message?.contains("401") == true || e.message?.contains("Unauthorized") == true -> {
                        "Authentication failed. Please scan QR code again to get a new access token."
                    }
                    else -> {
                        "Connection failed: ${e.message}"
                    }
                }
                showError(errorMessage)
                
                // Log detailed error for debugging
                println("🔍 [MainActivity] Connection error details: ${e}")
                e.printStackTrace()
            }
        }
    }
    
    private fun loadSongs() {
        debugLog("Loading songs from server...")
        lifecycleScope.launch {
            try {
                val authHeader = ApiClient.getAuthHeader()
                if (authHeader == null) {
                    debugLog("No auth header available for song loading")
                    showError("Not authenticated. Please scan QR code.")
                    return@launch
                }
                
                debugLog("Fetching songs with authentication...")
                val songList = ApiClient.api.getSongs(authHeader)
                songs.clear()
                songs.addAll(songList)
                songAdapter.notifyDataSetChanged()
                
                debugLog("Successfully loaded ${songs.size} songs")
                binding.statusText.text = "Loaded ${songs.size} songs"
                binding.progressBar.visibility = View.GONE
                updateConnectionStatus()
                
                if (songs.isEmpty()) {
                    binding.emptyText.visibility = View.VISIBLE
                    binding.emptyText.text = "No songs found on server"
                    binding.songsRecyclerView.visibility = View.GONE
                } else {
                    binding.emptyText.visibility = View.GONE
                    binding.songsRecyclerView.visibility = View.VISIBLE
                }
            } catch (e: Exception) {
                debugLog("Failed to load songs: ${e.message}")
                if (e.message?.contains("401") == true || e.message?.contains("Unauthorized") == true) {
                    debugLog("Authentication expired - clearing token")
                    // Clear invalid token
                    ApiClient.setAuthToken(null)
                    getSharedPreferences("BMA", MODE_PRIVATE).edit()
                        .remove("auth_token")
                        .apply()
                    updateConnectionStatus()
                    updateUIVisibility()
                    showError("Authentication expired. Please scan QR code again.")
                } else {
                    showError("Failed to load songs: ${e.message}")
                }
            }
        }
    }
    
    private fun showError(message: String) {
        debugLog("Showing error: $message")
        binding.progressBar.visibility = View.GONE
        binding.statusText.text = message
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
    
    private fun saveServerUrl(url: String) {
        debugLog("Saving server URL to preferences: $url")
        getSharedPreferences("BMA", MODE_PRIVATE).edit()
            .putString("server_url", url)
            .apply()
    }
} 