package com.bma.android

import android.os.Bundle
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bma.android.api.ApiClient
import com.bma.android.databinding.ActivityPlayerBinding
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource
import kotlinx.coroutines.*

class PlayerActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityPlayerBinding
    private lateinit var player: ExoPlayer
    private var updateJob: Job? = null
    
    private lateinit var songId: String
    private lateinit var songTitle: String
    private var songArtist: String = ""
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Get song info from intent
        songId = intent.getStringExtra("song_id") ?: ""
        songTitle = intent.getStringExtra("song_title") ?: "Unknown"
        songArtist = intent.getStringExtra("song_artist") ?: ""
        
        setupUI()
        setupPlayer()
    }
    
    private fun setupUI() {
        binding.titleText.text = songTitle
        binding.artistText.text = songArtist.ifEmpty { "Unknown Artist" }
        
        binding.backButton.setOnClickListener {
            finish()
        }
        
        binding.playPauseButton.setOnClickListener {
            if (player.isPlaying) {
                player.pause()
            } else {
                player.play()
            }
        }
        
        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    player.seekTo(progress.toLong())
                }
            }
            
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }
    
    private fun setupPlayer() {
        // Check if we have an auth token
        val authHeader = ApiClient.getAuthHeader()
        if (authHeader == null) {
            Toast.makeText(this, "Not authenticated. Please scan QR code first.", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        
        // Create HTTP data source with authorization header
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(mapOf("Authorization" to authHeader))
            .setAllowCrossProtocolRedirects(true)
        
        val dataSourceFactory = DataSource.Factory { httpDataSourceFactory.createDataSource() }
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)
        
        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
        
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_READY -> {
                        binding.seekBar.max = player.duration.toInt()
                        binding.durationText.text = formatTime(player.duration)
                        startProgressUpdate()
                    }
                    Player.STATE_ENDED -> {
                        player.seekTo(0)
                        player.pause()
                    }
                }
            }
            
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                binding.playPauseButton.setImageResource(
                    if (isPlaying) android.R.drawable.ic_media_pause
                    else android.R.drawable.ic_media_play
                )
            }
            
            override fun onPlayerError(error: com.google.android.exoplayer2.PlaybackException) {
                Toast.makeText(this@PlayerActivity, "Playback error: ${error.message}", Toast.LENGTH_LONG).show()
            }
        })
        
        // Load and play the song
        val streamUrl = "${ApiClient.getServerUrl()}stream/$songId"
        val mediaItem = MediaItem.fromUri(streamUrl)
        player.setMediaItem(mediaItem)
        player.prepare()
        player.play()
    }
    
    private fun startProgressUpdate() {
        updateJob?.cancel()
        updateJob = CoroutineScope(Dispatchers.Main).launch {
            while (isActive) {
                val position = player.currentPosition
                binding.seekBar.progress = position.toInt()
                binding.positionText.text = formatTime(position)
                delay(100)
            }
        }
    }
    
    private fun formatTime(milliseconds: Long): String {
        val seconds = (milliseconds / 1000).toInt()
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60
        return String.format("%d:%02d", minutes, remainingSeconds)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        updateJob?.cancel()
        player.release()
    }
} 