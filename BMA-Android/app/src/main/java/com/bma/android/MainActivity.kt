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
    
    private val qrScannerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            // QR code scanning was successful, reload the UI
            loadSavedConnection()
            connectToServer()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupUI()
        setupRecyclerView()
        loadSavedConnection()
    }
    
    private fun setupUI() {
        binding.connectButton.setOnClickListener {
            val url = binding.serverUrlInput.text.toString()
            if (url.isNotEmpty()) {
                ApiClient.setServerUrl(url)
                saveServerUrl(url)
                connectToServer()
            } else {
                Toast.makeText(this, "Please enter server URL", Toast.LENGTH_SHORT).show()
            }
        }
        
        binding.refreshButton.setOnClickListener {
            connectToServer()
        }
        
        binding.scanQrButton.setOnClickListener {
            startQRScanner()
        }
    }
    
    private fun setupRecyclerView() {
        songAdapter = SongAdapter(songs) { song ->
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
        val prefs = getSharedPreferences("BMA", MODE_PRIVATE)
        val savedUrl = prefs.getString("server_url", "")
        val savedToken = prefs.getString("auth_token", null)
        
        if (!savedUrl.isNullOrEmpty()) {
            binding.serverUrlInput.setText(savedUrl)
            ApiClient.setServerUrl(savedUrl)
        } else {
            // Set a default placeholder for manual entry
            binding.serverUrlInput.hint = "e.g., http://192.168.1.100:8008 or https://hostname.ts.net:8443"
        }
        
        if (!savedToken.isNullOrEmpty()) {
            ApiClient.setAuthToken(savedToken)
        }
        
        updateConnectionStatus()
    }
    
    private fun updateConnectionStatus() {
        val isAuthenticated = ApiClient.isAuthenticated()
        val serverUrl = ApiClient.getServerUrl()
        
        if (isAuthenticated && serverUrl.isNotEmpty()) {
            val protocol = when {
                serverUrl.contains(".ts.net") -> "HTTP via Tailscale (Secure)"
                serverUrl.startsWith("https://") -> "HTTPS (Secure)"
                else -> "HTTP (Local)"
            }
            binding.connectionStatus.text = "Connected via $protocol to: $serverUrl"
            binding.connectionStatus.setTextColor(getColor(android.R.color.holo_green_dark))
        } else if (serverUrl.isNotEmpty()) {
            val protocol = when {
                serverUrl.contains(".ts.net") -> "HTTP via Tailscale"
                serverUrl.startsWith("https://") -> "HTTPS"
                else -> "HTTP"
            }
            binding.connectionStatus.text = "Server set ($protocol) but not authenticated"
            binding.connectionStatus.setTextColor(getColor(android.R.color.holo_orange_dark))
        } else {
            binding.connectionStatus.text = "Not connected - Scan QR code or enter server URL"
            binding.connectionStatus.setTextColor(getColor(android.R.color.holo_red_dark))
        }
    }
    
    private fun startQRScanner() {
        val intent = Intent(this, QRScannerActivity::class.java)
        qrScannerLauncher.launch(intent)
    }
    
    private fun connectToServer() {
        val serverUrl = ApiClient.getServerUrl()
        if (serverUrl.isEmpty()) {
            showError("Please scan QR code or enter a server URL first")
            return
        }
        
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
                binding.statusText.text = "Checking server health..."
                val health = ApiClient.api.checkHealth()
                
                if (health["status"] == "healthy") {
                    binding.statusText.text = "Server is healthy, checking authentication..."
                    
                    if (ApiClient.isAuthenticated()) {
                        binding.statusText.text = "Connected to BMA server!"
                        loadSongs()
                    } else {
                        binding.statusText.text = "Server healthy but not authenticated. Please scan QR code."
                        binding.progressBar.visibility = View.GONE
                        showError("Authentication required. Please scan QR code to get access token.")
                    }
                } else {
                    showError("Server is not healthy: ${health}")
                }
            } catch (e: Exception) {
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
                println("Connection error details: ${e}")
                e.printStackTrace()
            }
        }
    }
    
    private fun loadSongs() {
        lifecycleScope.launch {
            try {
                val authHeader = ApiClient.getAuthHeader()
                if (authHeader == null) {
                    showError("Not authenticated. Please scan QR code.")
                    return@launch
                }
                
                val songList = ApiClient.api.getSongs(authHeader)
                songs.clear()
                songs.addAll(songList)
                songAdapter.notifyDataSetChanged()
                
                binding.statusText.text = "Loaded ${songs.size} songs"
                binding.progressBar.visibility = View.GONE
                updateConnectionStatus()
                
                if (songs.isEmpty()) {
                    binding.emptyText.visibility = View.VISIBLE
                    binding.songsRecyclerView.visibility = View.GONE
                } else {
                    binding.emptyText.visibility = View.GONE
                    binding.songsRecyclerView.visibility = View.VISIBLE
                }
            } catch (e: Exception) {
                if (e.message?.contains("401") == true || e.message?.contains("Unauthorized") == true) {
                    // Clear invalid token
                    ApiClient.setAuthToken(null)
                    getSharedPreferences("BMA", MODE_PRIVATE).edit()
                        .remove("auth_token")
                        .apply()
                    updateConnectionStatus()
                    showError("Authentication expired. Please scan QR code again.")
                } else {
                    showError("Failed to load songs: ${e.message}")
                }
            }
        }
    }
    
    private fun showError(message: String) {
        binding.progressBar.visibility = View.GONE
        binding.statusText.text = message
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
    
    private fun saveServerUrl(url: String) {
        getSharedPreferences("BMA", MODE_PRIVATE).edit()
            .putString("server_url", url)
            .apply()
    }
} 