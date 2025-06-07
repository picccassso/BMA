package com.bma.android

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bma.android.api.ApiClient
import com.bma.android.databinding.ActivityPlayerBinding
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.LazyHeaders
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource

class PlayerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlayerBinding
    private var exoPlayer: ExoPlayer? = null
    
    // Playlist and state management
    private var originalPlaylist: List<Song> = emptyList()
    private var currentPlaylist: List<Song> = emptyList()
    private var currentSongIndex: Int = 0

    // Shuffle and Repeat states
    private var isShuffleEnabled = false
    private sealed class RepeatState {
        object OFF : RepeatState()
        object ALL : RepeatState()
        object ONE : RepeatState()
    }
    private var currentRepeatState: RepeatState = RepeatState.OFF

    private val handler = Handler(Looper.getMainLooper())
    private val updateSeekBarAction = object : Runnable {
        override fun run() {
            exoPlayer?.let {
                binding.seekBar.progress = it.currentPosition.toInt()
                binding.positionText.text = formatDuration(it.currentPosition)
                handler.postDelayed(this, 1000)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupExoPlayer()
        extractPlaylistFromIntent()
        setupUI()

        playSongAtIndex(currentSongIndex)
    }
    
    private fun setupExoPlayer() {
        val authHeader = ApiClient.getAuthHeader()
        if (authHeader == null) {
            Toast.makeText(this, "Authentication error", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(mapOf("Authorization" to authHeader))

        val mediaSourceFactory = DefaultMediaSourceFactory(this)
            .setDataSourceFactory(httpDataSourceFactory)

        exoPlayer = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
            
        exoPlayer?.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                binding.playPauseButton.setImageResource(
                    if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
                )
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    // onPlaybackStateChanged is called when a song ends, but before onMediaItemTransition.
                    // The player's repeat mode will handle repeating one or all.
                    // If repeat is off, we need to manually move to the next song.
                    if (exoPlayer?.repeatMode == Player.REPEAT_MODE_OFF) {
                        playNextSong()
                    }
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                // This is called when the player automatically moves to the next item
                mediaItem?.mediaId?.let { songId ->
                    val newIndex = currentPlaylist.indexOfFirst { it.id == songId }
                    if (newIndex != -1) {
                        currentSongIndex = newIndex
                        updateUIForNewSong()
                    }
                }
            }
        })
    }
    
    private fun extractPlaylistFromIntent() {
        val songIds = intent.getStringArrayExtra("playlist_song_ids") ?: emptyArray()
        val songTitles = intent.getStringArrayExtra("playlist_song_titles") ?: emptyArray()
        val songArtists = intent.getStringArrayExtra("playlist_song_artists") ?: emptyArray()
        val currentSongId = intent.getStringExtra("song_id")

        if (songIds.isEmpty() || songTitles.isEmpty() || songArtists.isEmpty() || currentSongId == null) {
            Toast.makeText(this, "Playlist data missing", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        originalPlaylist = songIds.mapIndexed { index, id ->
            Song(id, songTitles.getOrNull(index) ?: "", songArtists.getOrNull(index) ?: "", "", 0)
        }
        currentPlaylist = originalPlaylist
        currentSongIndex = originalPlaylist.indexOfFirst { it.id == currentSongId }.coerceAtLeast(0)
    }

    private fun setupUI() {
        binding.backButton.setOnClickListener { finish() }
        binding.playPauseButton.setOnClickListener { togglePlayPause() }
        binding.nextButton.setOnClickListener { playNextSong() }
        binding.previousButton.setOnClickListener { playPreviousSong() }
        binding.shuffleButton.setOnClickListener { toggleShuffle() }
        binding.repeatButton.setOnClickListener { cycleRepeatMode() }

        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    exoPlayer?.seekTo(progress.toLong())
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun playSongAtIndex(index: Int) {
        if (index < 0 || index >= currentPlaylist.size) return
        
        currentSongIndex = index
        val song = currentPlaylist[currentSongIndex]
        val songUrl = "${ApiClient.getServerUrl()}/song/${song.id}"
        val mediaItem = MediaItem.Builder()
            .setUri(songUrl)
            .setMediaId(song.id)
            .build()
            
        exoPlayer?.setMediaItem(mediaItem)
        exoPlayer?.prepare()
        exoPlayer?.play()

        updateUIForNewSong()
    }

    private fun updateUIForNewSong() {
        val song = currentPlaylist[currentSongIndex]
        binding.titleText.text = song.title.replace(Regex("^\\d+\\.?\\s*"), "")
        binding.artistText.text = song.artist

        val artworkUrl = "${ApiClient.getServerUrl()}/artwork/${song.id}"
        val glideUrl = GlideUrl(artworkUrl, LazyHeaders.Builder().addHeader("Authorization", ApiClient.getAuthHeader()!!).build())

        Glide.with(this)
            .load(glideUrl)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .placeholder(R.drawable.ic_folder)
            .into(binding.albumArt)

        handler.post(object : Runnable {
            override fun run() {
                if (exoPlayer?.duration ?: -1 > 0) {
                    binding.durationText.text = formatDuration(exoPlayer!!.duration)
                    binding.seekBar.max = exoPlayer!!.duration.toInt()
                    handler.post(updateSeekBarAction)
                } else {
                    // Wait until duration is available
                    handler.postDelayed(this, 100)
                }
            }
        })
    }
    
    private fun togglePlayPause() {
        exoPlayer?.let {
            if (it.isPlaying) {
                it.pause()
            } else {
                it.play()
            }
        }
    }

    private fun playNextSong() {
        // ExoPlayer handles next in repeat modes
        if (exoPlayer?.repeatMode != Player.REPEAT_MODE_OFF) {
            exoPlayer?.seekToNextMediaItem()
        } else {
            // Manual handling for REPEAT_OFF
            if (currentSongIndex < currentPlaylist.size - 1) {
                playSongAtIndex(currentSongIndex + 1)
            } else {
                // End of playlist
                exoPlayer?.stop()
                exoPlayer?.seekTo(0)
                binding.seekBar.progress = 0
            }
        }
    }

    private fun playPreviousSong() {
         if (exoPlayer?.currentPosition ?: 0 > 3000) {
            exoPlayer?.seekTo(0)
        } else {
            exoPlayer?.seekToPreviousMediaItem()
        }
    }
    
    private fun toggleShuffle() {
        isShuffleEnabled = !isShuffleEnabled
        if (isShuffleEnabled) {
            val currentSong = currentPlaylist[currentSongIndex]
            val shuffled = originalPlaylist.shuffled().toMutableList()
            // Make sure current song is first in the shuffled list
            shuffled.remove(currentSong)
            shuffled.add(0, currentSong)
            currentPlaylist = shuffled
            currentSongIndex = 0
            
            binding.shuffleButton.setImageResource(R.drawable.ic_shuffle_on)
            Toast.makeText(this, "Shuffle On", Toast.LENGTH_SHORT).show()
        } else {
            val currentSong = currentPlaylist[currentSongIndex]
            currentPlaylist = originalPlaylist
            currentSongIndex = currentPlaylist.indexOf(currentSong)
            
            binding.shuffleButton.setImageResource(R.drawable.ic_shuffle_off)
            Toast.makeText(this, "Shuffle Off", Toast.LENGTH_SHORT).show()
        }
        
        // Re-build ExoPlayer's playlist
        updateExoPlayerPlaylist()
    }

    private fun cycleRepeatMode() {
        currentRepeatState = when (currentRepeatState) {
            is RepeatState.OFF -> {
                exoPlayer?.repeatMode = Player.REPEAT_MODE_ALL
                binding.repeatButton.setImageResource(R.drawable.ic_repeat_all)
                Toast.makeText(this, "Repeat All", Toast.LENGTH_SHORT).show()
                RepeatState.ALL
            }
            is RepeatState.ALL -> {
                exoPlayer?.repeatMode = Player.REPEAT_MODE_ONE
                binding.repeatButton.setImageResource(R.drawable.ic_repeat_one)
                Toast.makeText(this, "Repeat One", Toast.LENGTH_SHORT).show()
                RepeatState.ONE
            }
            is RepeatState.ONE -> {
                exoPlayer?.repeatMode = Player.REPEAT_MODE_OFF
                binding.repeatButton.setImageResource(R.drawable.ic_repeat_off)
                Toast.makeText(this, "Repeat Off", Toast.LENGTH_SHORT).show()
                RepeatState.OFF
            }
        }
    }
    
    private fun updateExoPlayerPlaylist() {
        val mediaItems = currentPlaylist.map { song ->
            val songUrl = "${ApiClient.getServerUrl()}/song/${song.id}"
            MediaItem.Builder()
                .setUri(songUrl)
                .setMediaId(song.id)
                .build()
        }
        exoPlayer?.setMediaItems(mediaItems, currentSongIndex, 0)
    }

    private fun formatDuration(duration: Long): String {
        val minutes = (duration / 1000) / 60
        val seconds = (duration / 1000) % 60
        return String.format("%d:%02d", minutes, seconds)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(updateSeekBarAction)
        exoPlayer?.release()
        exoPlayer = null
    }

    // A simple data class to hold playlist info
    data class Song(val id: String, val title: String, val artist: String, val album: String, val sortOrder: Int)
}