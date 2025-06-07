package com.bma.android

import android.content.*
import android.os.Bundle
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.bma.android.api.ApiClient
import com.bma.android.databinding.ActivityMainBinding
import com.bma.android.models.Song
import com.bma.android.ui.library.LibraryFragment
import com.bma.android.ui.search.SearchFragment
import com.bma.android.ui.settings.SettingsFragment
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.LazyHeaders

class MainActivity : AppCompatActivity(), MusicService.MusicServiceListener {

    private lateinit var binding: ActivityMainBinding

    private val libraryFragment = LibraryFragment()
    private val searchFragment = SearchFragment()
    private val settingsFragment = SettingsFragment()
    
    // Music service
    private var musicService: MusicService? = null
    private var serviceBound = false
    
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

        // Load the initial fragment
        if (savedInstanceState == null) {
            binding.bottomNavView.selectedItemId = R.id.navigation_library
            loadFragment(libraryFragment)
        }
        
        // Bind to music service
        android.util.Log.d("MainActivity", "About to bind music service...")
        bindMusicService()
        android.util.Log.d("MainActivity", "=== ONCREATE COMPLETED ===")
    }
    
    override fun onDestroy() {
        android.util.Log.d("MainActivity", "=== ONDESTROY CALLED ===")
        android.util.Log.d("MainActivity", "Service bound: $serviceBound")
        super.onDestroy()
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
}