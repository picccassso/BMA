package com.bma.android

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.bma.android.api.ApiClient
import com.bma.android.databinding.ActivityMainBinding
import com.bma.android.models.Song
import com.bma.android.storage.PlaybackStateManager
import com.bma.android.ui.library.LibraryFragment
import com.bma.android.ui.search.SearchFragment
import com.bma.android.ui.settings.SettingsFragment
import com.bma.android.ui.disconnection.DisconnectionFragment
import com.bma.android.ui.album.AlbumDetailFragment
import com.bma.android.ui.playlist.PlaylistDetailFragment
import com.bma.android.models.Album
import com.bma.android.models.Playlist
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
    
    // Album detail overlay
    private var albumDetailFragment: AlbumDetailFragment? = null
    private var playlistDetailFragment: PlaylistDetailFragment? = null
    private var albumTransitionAnimator: AlbumTransitionAnimator? = null
    private var currentDisplayMode = DisplayMode.NORMAL
    private var preventMiniPlayerUpdates = false
    
    enum class DisplayMode {
        NORMAL,
        ALBUM_DETAIL,
        PLAYLIST_DETAIL
    }
    
    // Music service
    private var musicService: MusicService? = null
    private var serviceBound = false
    
    // Auth failure handling
    private var isHandlingAuthFailure = false
    
    // Health check timer
    private var healthCheckHandler: Handler? = null
    private var healthCheckRunnable: Runnable? = null
    private var isInNormalMode = false
    
    // Notification permission request
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            android.util.Log.d("MainActivity", "Notification permission granted")
        } else {
            android.util.Log.w("MainActivity", "Notification permission denied")
        }
    }
    
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
            
            // Try to restore playback state if no current song is playing
            val currentSong = musicService?.getCurrentSong()
            val isPlaying = musicService?.isPlaying() ?: false
            android.util.Log.d("MainActivity", "Current service state - Song: ${currentSong?.title}, Playing: $isPlaying")
            
            // If no song is currently playing, try to restore from saved state
            if (currentSong == null) {
                val playbackStateManager = PlaybackStateManager.getInstance(this@MainActivity)
                if (playbackStateManager.hasValidPlaybackState()) {
                    android.util.Log.d("MainActivity", "Attempting to restore playback state...")
                    musicService?.restorePlaybackState()
                }
            }
            
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
        
        // Request notification permission on Android 13+ (API 33+)
        requestNotificationPermission()
        
        // Load saved credentials so the app can auto-connect
        loadConnectionDetails()
        
        // Setup mini-player
        setupMiniPlayer()
        
        // Initialize album transition animator - use fragment container instead of root
        albumTransitionAnimator = AlbumTransitionAnimator(binding.fragmentContainer)

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
        
        // Check if there's a specific tab to select from intent
        val selectedTab = intent.getStringExtra("selected_tab")
        when (selectedTab) {
            "search" -> {
                binding.bottomNavView.selectedItemId = R.id.navigation_search
                loadFragment(searchFragment)
            }
            "settings" -> {
                binding.bottomNavView.selectedItemId = R.id.navigation_settings
                loadFragment(settingsFragment)
            }
            else -> {
                // Default to library
                binding.bottomNavView.selectedItemId = R.id.navigation_library
                loadFragment(libraryFragment)
            }
        }
        
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
    
    private fun requestNotificationPermission() {
        // Only request permission on Android 13+ (API 33+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    // Permission already granted
                    android.util.Log.d("MainActivity", "Notification permission already granted")
                }
                else -> {
                    // Request permission
                    android.util.Log.d("MainActivity", "Requesting notification permission")
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        } else {
            // On Android 12 and below, notification permission is granted by default
            android.util.Log.d("MainActivity", "Notification permission not required on Android < 13")
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
        // Prevent updates during album transition animations
        if (preventMiniPlayerUpdates) {
            return
        }
        
        musicService?.let { service ->
            val currentSong = service.getCurrentSong()
            val isPlaying = service.isPlaying()
            
            if (currentSong != null) {
                // Show mini-player
                binding.miniPlayer.root.isVisible = true
                
                // Update song info
                binding.miniPlayer.miniPlayerTitle.text = currentSong.title
                binding.miniPlayer.miniPlayerArtist.text = currentSong.artist.ifEmpty { "Unknown Artist" }
                
                // Update play/pause button
                val playPauseIcon = if (isPlaying) R.drawable.ic_pause_circle else R.drawable.ic_play_circle
                binding.miniPlayer.miniPlayerPlayPause.setImageResource(playPauseIcon)
                
                // Load album artwork
                loadMiniPlayerArtwork(currentSong)
                
                // Update progress
                val currentPos = service.getCurrentPosition()
                val duration = service.getDuration()
                val progress = if (duration > 0) {
                    (currentPos * 100) / duration
                } else 0
                binding.miniPlayer.miniPlayerProgress.progress = progress
                
            } else {
                // Hide mini-player
                binding.miniPlayer.root.isVisible = false
            }
        } ?: run {
            // Hide mini-player when no service
            binding.miniPlayer.root.isVisible = false
        }
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
        // Handle album detail back navigation first
        if (currentDisplayMode == DisplayMode.ALBUM_DETAIL) {
            onAlbumDetailBackPressed()
            return
        }
        
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
    
    // Store reference to background fragment to avoid lookups
    private var backgroundFragment: Fragment? = null
    
    // Public method for album clicks from fragments
    fun showAlbumDetail(album: Album) {
        if (albumTransitionAnimator?.isCurrentlyAnimating() == true) {
            return // Don't start new transitions while animating
        }
        
        // Store reference to current fragment and hide it
        backgroundFragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
        backgroundFragment?.view?.visibility = android.view.View.INVISIBLE // Use INVISIBLE instead of GONE
        
        // Create album detail fragment
        albumDetailFragment = AlbumDetailFragment.newInstance(album)
        
        // Add fragment to container
        supportFragmentManager.beginTransaction()
            .add(R.id.fragment_container, albumDetailFragment!!, "album_detail")
            .commit() // Use commit instead of commitNow for smoother transitions
        
        // Wait for fragment to be added then start animation
        supportFragmentManager.executePendingTransactions()
        
        // Start animation after ensuring view is ready
        albumDetailFragment?.view?.let { fragmentView ->
            // Ensure the fragment view is fully laid out
            fragmentView.post {
                albumTransitionAnimator?.fadeToBlackAndShowContent(fragmentView) {
                    currentDisplayMode = DisplayMode.ALBUM_DETAIL
                    // Keep mini-player visible during album detail
                }
            }
        }
    }
    
    // Method called by AlbumDetailFragment when back is pressed
    fun onAlbumDetailBackPressed() {
        if (albumTransitionAnimator?.isCurrentlyAnimating() == true) {
            return // Don't start new transitions while animating
        }
        
        albumDetailFragment?.let { fragment ->
            // Prevent mini-player updates during back animation
            preventMiniPlayerUpdates = true
            
            // Start reverse animation
            albumTransitionAnimator?.fadeToBlackAndHideContent(fragment.requireView()) {
                // Remove fragment after animation completes
                try {
                    if (fragment.isAdded && !isFinishing && !isDestroyed) {
                        supportFragmentManager.beginTransaction()
                            .remove(fragment)
                            .commitNowAllowingStateLoss() // Prevent IllegalStateException
                    }
                } catch (e: Exception) {
                    // Ignore any fragment transaction exceptions during cleanup
                }
                
                albumDetailFragment = null
                currentDisplayMode = DisplayMode.NORMAL
                
                // Restore the background fragment visibility using stored reference
                backgroundFragment?.view?.visibility = android.view.View.VISIBLE
                backgroundFragment = null
                
                // Re-enable mini-player updates after animation completes
                preventMiniPlayerUpdates = false
            }
        }
    }
    
    // Public method for playlist clicks from fragments
    fun showPlaylistDetail(playlist: Playlist) {
        if (albumTransitionAnimator?.isCurrentlyAnimating() == true) {
            return // Don't start new transitions while animating
        }
        
        // Store reference to current fragment and hide it
        backgroundFragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
        backgroundFragment?.view?.visibility = android.view.View.INVISIBLE
        
        // Create playlist detail fragment
        playlistDetailFragment = PlaylistDetailFragment.newInstance(playlist)
        
        // Add fragment to container
        supportFragmentManager.beginTransaction()
            .add(R.id.fragment_container, playlistDetailFragment!!, "playlist_detail")
            .commit()
        
        // Wait for fragment to be added then start animation
        supportFragmentManager.executePendingTransactions()
        
        // Start animation after ensuring view is ready
        playlistDetailFragment?.view?.let { fragmentView ->
            fragmentView.post {
                albumTransitionAnimator?.fadeToBlackAndShowContent(fragmentView) {
                    currentDisplayMode = DisplayMode.PLAYLIST_DETAIL
                }
            }
        }
    }
    
    // Method called by PlaylistDetailFragment when back is pressed
    fun onPlaylistDetailBackPressed() {
        if (albumTransitionAnimator?.isCurrentlyAnimating() == true) {
            return // Don't start new transitions while animating
        }
        
        playlistDetailFragment?.let { fragment ->
            // Prevent mini-player updates during back animation
            preventMiniPlayerUpdates = true
            
            // Start reverse animation
            albumTransitionAnimator?.fadeToBlackAndHideContent(fragment.requireView()) {
                // Remove fragment after animation completes
                try {
                    if (fragment.isAdded && !isFinishing && !isDestroyed) {
                        supportFragmentManager.beginTransaction()
                            .remove(fragment)
                            .commitNowAllowingStateLoss()
                    }
                } catch (e: Exception) {
                    // Ignore any fragment transaction exceptions during cleanup
                }
                
                playlistDetailFragment = null
                currentDisplayMode = DisplayMode.NORMAL
                
                // Restore the background fragment visibility
                backgroundFragment?.view?.visibility = android.view.View.VISIBLE
                backgroundFragment = null
                
                // Re-enable mini-player updates after animation completes
                preventMiniPlayerUpdates = false
            }
        }
    }
    
    // Public method to get all songs for PlaylistDetailFragment
    fun getAllSongs(): List<Song> {
        return libraryFragment.getAllSongs()
    }
}