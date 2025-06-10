package com.bma.android

import android.content.*
import android.os.Bundle
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.bma.android.api.ApiClient
import com.bma.android.databinding.ActivityMainBinding
import com.bma.android.models.Song
import com.bma.android.ui.library.LibraryFragment
import com.bma.android.ui.search.SearchFragment
import com.bma.android.ui.settings.SettingsFragment
import com.bma.android.ui.disconnection.DisconnectionFragment
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.LazyHeaders
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity(), MusicService.MusicServiceListener {

    private lateinit var binding: ActivityMainBinding

    private val libraryFragment = LibraryFragment()
    private val searchFragment = SearchFragment()
    private val settingsFragment = SettingsFragment()
    private val disconnectionFragment = DisconnectionFragment()
    
    // Music service
    private var musicService: MusicService? = null
    private var serviceBound = false
    
    // Auth failure handling
    private var isHandlingAuthFailure = false
    
    // Health check timer
    private var healthCheckHandler: Handler? = null
    private var healthCheckRunnable: Runnable? = null
    private var isInNormalMode = false
    
    // Service connection
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            android.util.Log.d("MainActivity", "=== SERVICE CONNECTED ===")
            android.util.Log.d("MainActivity", "Service component: ${name?.className}")
            val binder = service as MusicService.MusicBinder
            musicService = binder.getService()
            serviceBound = true
            musicService?.addListener(this@MainActivity)
            android.util.Log.d("MainActivity", "Service bound successfully, listener added")
            
            // Immediately check current state and update mini-player
            val currentSong = musicService?.getCurrentSong()
            val isPlaying = musicService?.isPlaying() ?: false
            android.util.Log.d("MainActivity", "Current service state - Song: ${currentSong?.title}, Playing: $isPlaying")
            updateMiniPlayer()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            android.util.Log.w("MainActivity", "=== SERVICE DISCONNECTED ===")
            android.util.Log.w("MainActivity", "Service component: ${name?.className}")
            serviceBound = false
            // Note: Don't call removeListener here since service is already disconnected
            musicService = null
            
            // Hide mini-player when service disconnects
            binding.miniPlayer.root.isVisible = false
            
            // Try to rebind after a short delay
            android.util.Log.d("MainActivity", "Attempting to rebind to service...")
            Handler(Looper.getMainLooper()).postDelayed({
                if (!serviceBound) {
                    bindMusicService()
                }
            }, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        android.util.Log.d("MainActivity", "=== ONCREATE STARTED ===")
        
        // Load saved credentials so the app can auto-connect
        loadConnectionDetails()
        
        // Setup mini-player
        setupMiniPlayer()

        binding.bottomNavView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_library -> {
                    loadFragment(libraryFragment)
                    true
                }   
                R.id.navigation_search -> {
                    loadFragment(searchFragment)
                    true
                }
                R.id.navigation_settings -> {
                    loadFragment(settingsFragment)
                    true
                }
                else -> false
            }
        }

        // Initial fragment loading will be handled by connection check
        // Don't load fragments until we know connection status
        
        // Bind to music service
        android.util.Log.d("MainActivity", "About to bind music service...")
        bindMusicService()
        android.util.Log.d("MainActivity", "=== ONCREATE COMPLETED ===")
    }
    
    override fun onDestroy() {
        android.util.Log.d("MainActivity", "=== ONDESTROY CALLED ===")
        android.util.Log.d("MainActivity", "Service bound: $serviceBound")
        super.onDestroy()
        
        // Stop health check timer
        stopHealthCheckTimer()
        
        if (serviceBound) {
            android.util.Log.d("MainActivity", "Unbinding from service...")
            musicService?.removeListener(this@MainActivity)
            unbindService(serviceConnection)
            serviceBound = false
            android.util.Log.d("MainActivity", "Service unbound")
        } else {
            android.util.Log.d("MainActivity", "Service was not bound, no unbinding needed")
        }
        android.util.Log.d("MainActivity", "=== ONDESTROY COMPLETED ===")
    }
    
    override fun onPause() {
        super.onPause()
        // Pause health checks when app goes to background
        if (isInNormalMode) {
            stopHealthCheckTimer()
            android.util.Log.d("MainActivity", "Health checks paused (app going to background)")
        }
    }
    
    override fun onResume() {
        super.onResume()
        // Resume health checks when app comes to foreground
        if (isInNormalMode) {
            startHealthCheckTimer()
            android.util.Log.d("MainActivity", "Health checks resumed (app coming to foreground)")
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    private fun loadConnectionDetails() {
        val prefs = getSharedPreferences("BMA", Context.MODE_PRIVATE)
        val savedUrl = prefs.getString("server_url", null)
        val savedToken = prefs.getString("auth_token", null)

        if (!savedUrl.isNullOrEmpty()) {
            ApiClient.setServerUrl(savedUrl)
        }
        if (!savedToken.isNullOrEmpty()) {
            ApiClient.setAuthToken(savedToken)
        }
        
        // Set up authentication failure callback
        ApiClient.onAuthFailure = {
            runOnUiThread {
                handleAuthenticationFailure()
            }
        }
        
        // Check connection status
        lifecycleScope.launch {
            when (ApiClient.checkConnection(this@MainActivity)) {
                ApiClient.ConnectionStatus.CONNECTED -> {
                    runOnUiThread {
                        setupNormalMode()
                    }
                }
                ApiClient.ConnectionStatus.DISCONNECTED -> {
                    runOnUiThread {
                        showDisconnectionScreen()
                    }
                }
                ApiClient.ConnectionStatus.TOKEN_EXPIRED -> {
                    runOnUiThread {
                        showDisconnectionScreen()
                    }
                }
                ApiClient.ConnectionStatus.NO_CREDENTIALS -> {
                    runOnUiThread {
                        redirectToSetup()
                    }
                }
            }
        }
    }
    
    private fun handleAuthenticationFailure() {
        if (isHandlingAuthFailure) {
            android.util.Log.d("MainActivity", "Already handling auth failure, ignoring duplicate call")
            return
        }
        
        isHandlingAuthFailure = true
        android.util.Log.e("MainActivity", "Authentication failure detected, clearing stored credentials")
        
        // Clear stored credentials to prevent loop
        val prefs = getSharedPreferences("BMA", Context.MODE_PRIVATE)
        prefs.edit()
            .remove("auth_token")
            .remove("token_expires_at")
            .apply()
        
        // Clear from ApiClient
        ApiClient.setAuthToken(null)
        
        // Redirect to setup
        val intent = Intent(this, com.bma.android.setup.SetupActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
    
    private fun setupNormalMode() {
        // Show normal UI
        binding.bottomNavView.isVisible = true
        binding.fragmentContainer.isVisible = true
        
        // Load the initial fragment
        binding.bottomNavView.selectedItemId = R.id.navigation_library
        loadFragment(libraryFragment)
        
        // Start health check timer
        isInNormalMode = true
        startHealthCheckTimer()
    }
    
    private fun showDisconnectionScreen() {
        // Hide bottom navigation but keep fragment container
        binding.bottomNavView.isVisible = false
        binding.fragmentContainer.isVisible = true
        
        // Stop health check timer since we're no longer in normal mode
        isInNormalMode = false
        stopHealthCheckTimer()
        
        // Show disconnection fragment
        loadFragment(disconnectionFragment)
    }
    
    private fun redirectToSetup() {
        val intent = Intent(this, com.bma.android.setup.SetupActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
    
    private fun startHealthCheckTimer() {
        stopHealthCheckTimer() // Stop any existing timer
        
        healthCheckHandler = Handler(Looper.getMainLooper())
        healthCheckRunnable = object : Runnable {
            override fun run() {
                performHealthCheck()
                // Schedule next check in 30 seconds if still in normal mode
                if (isInNormalMode && healthCheckHandler != null) {
                    healthCheckHandler?.postDelayed(this, 30000) // 30 seconds
                }
            }
        }
        
        // Start first check after 30 seconds
        healthCheckHandler?.postDelayed(healthCheckRunnable!!, 30000)
        android.util.Log.d("MainActivity", "Health check timer started")
    }
    
    private fun stopHealthCheckTimer() {
        healthCheckHandler?.removeCallbacks(healthCheckRunnable ?: return)
        healthCheckHandler = null
        healthCheckRunnable = null
        android.util.Log.d("MainActivity", "Health check timer stopped")
    }
    
    private fun performHealthCheck() {
        android.util.Log.d("MainActivity", "Performing health check...")
        
        lifecycleScope.launch {
            try {
                when (ApiClient.checkConnection(this@MainActivity)) {
                    ApiClient.ConnectionStatus.CONNECTED -> {
                        android.util.Log.d("MainActivity", "Health check: Still connected")
                        // All good, continue
                    }
                    ApiClient.ConnectionStatus.DISCONNECTED,
                    ApiClient.ConnectionStatus.TOKEN_EXPIRED -> {
                        android.util.Log.w("MainActivity", "Health check: Server disconnected, showing disconnect screen")
                        runOnUiThread {
                            showDisconnectionScreen()
                        }
                    }
                    ApiClient.ConnectionStatus.NO_CREDENTIALS -> {
                        android.util.Log.w("MainActivity", "Health check: No credentials, redirecting to setup")
                        runOnUiThread {
                            redirectToSetup()
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Health check failed", e)
                // On exception, assume disconnected
                runOnUiThread {
                    showDisconnectionScreen()
                }
            }
        }
    }
    
    private fun bindMusicService() {
        android.util.Log.d("MainActivity", "=== BINDING TO MUSIC SERVICE ===")
        val intent = Intent(this, MusicService::class.java)
        android.util.Log.d("MainActivity", "Starting service...")
        val serviceStartResult = startService(intent) // Start the service first
        android.util.Log.d("MainActivity", "Service start result: $serviceStartResult")
        android.util.Log.d("MainActivity", "Attempting to bind service...")
        val bindResult = bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        android.util.Log.d("MainActivity", "Bind service result: $bindResult")
    }
    
    private fun setupMiniPlayer() {
        // Click mini-player to open PlayerActivity
        binding.miniPlayer.root.setOnClickListener {
            musicService?.getCurrentSong()?.let {
                val intent = Intent(this, PlayerActivity::class.java)
                startActivity(intent)
            }
        }
        
        // Mini-player controls
        binding.miniPlayer.miniPlayerPlayPause.setOnClickListener {
            musicService?.let { service ->
                if (service.isPlaying()) {
                    service.pause()
                } else {
                    service.play()
                }
            }
        }
        
        binding.miniPlayer.miniPlayerNext.setOnClickListener {
            musicService?.skipToNext()
        }
        
        binding.miniPlayer.miniPlayerPrevious.setOnClickListener {
            musicService?.skipToPrevious()
        }
    }
    
    private fun updateMiniPlayer() {
        android.util.Log.d("MainActivity", "=== UPDATE MINI-PLAYER CALLED ===")
        android.util.Log.d("MainActivity", "Service bound: $serviceBound")
        android.util.Log.d("MainActivity", "Service instance: ${musicService != null}")
        
        musicService?.let { service ->
            val currentSong = service.getCurrentSong()
            val isPlaying = service.isPlaying()
            val playbackState = service.getPlaybackState()
            
            android.util.Log.d("MainActivity", "Service data - Song: ${currentSong?.title}, Playing: $isPlaying, State: $playbackState")
            
            if (currentSong != null) {
                android.util.Log.d("MainActivity", "SHOWING mini-player for: ${currentSong.title} by ${currentSong.artist}")
                
                // Show mini-player
                binding.miniPlayer.root.isVisible = true
                android.util.Log.d("MainActivity", "Mini-player visibility set to true")
                
                // Update song info
                binding.miniPlayer.miniPlayerTitle.text = currentSong.title
                binding.miniPlayer.miniPlayerArtist.text = currentSong.artist.ifEmpty { "Unknown Artist" }
                android.util.Log.d("MainActivity", "Song info updated")
                
                // Update play/pause button
                val playPauseIcon = if (isPlaying) R.drawable.ic_pause_circle else R.drawable.ic_play_circle
                binding.miniPlayer.miniPlayerPlayPause.setImageResource(playPauseIcon)
                android.util.Log.d("MainActivity", "Play/pause button updated - isPlaying: $isPlaying")
                
                // Load album artwork
                loadMiniPlayerArtwork(currentSong)
                android.util.Log.d("MainActivity", "Loading artwork for song: ${currentSong.id}")
                
                // Update progress
                val currentPos = service.getCurrentPosition()
                val duration = service.getDuration()
                val progress = if (duration > 0) {
                    (currentPos * 100) / duration
                } else 0
                binding.miniPlayer.miniPlayerProgress.progress = progress
                android.util.Log.d("MainActivity", "Progress updated: $currentPos/$duration ($progress%)")
                
            } else {
                android.util.Log.d("MainActivity", "HIDING mini-player - no current song")
                // Hide mini-player
                binding.miniPlayer.root.isVisible = false
            }
        } ?: run {
            android.util.Log.w("MainActivity", "updateMiniPlayer called but musicService is NULL")
            android.util.Log.w("MainActivity", "Service bound: $serviceBound")
            // Hide mini-player when no service
            binding.miniPlayer.root.isVisible = false
        }
        
        android.util.Log.d("MainActivity", "=== UPDATE MINI-PLAYER COMPLETED ===")
    }
    
    private fun loadMiniPlayerArtwork(song: Song) {
        val artworkUrl = "${ApiClient.getServerUrl()}/artwork/${song.id}"
        val authHeader = ApiClient.getAuthHeader()
        
        if (authHeader != null) {
            val glideUrl = GlideUrl(
                artworkUrl, 
                LazyHeaders.Builder()
                    .addHeader("Authorization", authHeader)
                    .build()
            )
            
            Glide.with(this)
                .load(glideUrl)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .placeholder(R.drawable.ic_music_note)
                .error(R.drawable.ic_music_note)
                .into(binding.miniPlayer.miniPlayerArtwork)
        } else {
            binding.miniPlayer.miniPlayerArtwork.setImageResource(R.drawable.ic_music_note)
        }
    }
    
    // MusicService.MusicServiceListener implementation
    override fun onPlaybackStateChanged(state: Int) {
        android.util.Log.d("MainActivity", "=== PLAYBACK STATE CHANGED ===")
        android.util.Log.d("MainActivity", "New state: $state")
        android.util.Log.d("MainActivity", "Service bound: $serviceBound, Service: ${musicService != null}")
        updateMiniPlayer()
    }
    
    override fun onSongChanged(song: Song?) {
        android.util.Log.d("MainActivity", "=== SONG CHANGED ===")
        android.util.Log.d("MainActivity", "New song: ${song?.title} by ${song?.artist}")
        android.util.Log.d("MainActivity", "Service bound: $serviceBound, Service: ${musicService != null}")
        updateMiniPlayer()
    }
    
    override fun onProgressChanged(progress: Int, duration: Int) {
        android.util.Log.v("MainActivity", "Progress changed: $progress/$duration")
        if (duration > 0) {
            val progressPercent = (progress * 100) / duration
            binding.miniPlayer.miniPlayerProgress.progress = progressPercent
        }
    }
    
    fun getMusicService(): MusicService? = musicService
    
    override fun onBackPressed() {
        // If user is on Search or Settings tab, navigate back to Library
        when (binding.bottomNavView.selectedItemId) {
            R.id.navigation_search, R.id.navigation_settings -> {
                binding.bottomNavView.selectedItemId = R.id.navigation_library
            }
            else -> {
                // If already on Library or any other state, use default back behavior (exit app)
                super.onBackPressed()
            }
        }
    }
}