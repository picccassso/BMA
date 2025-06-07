package com.bma.android

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.bma.android.api.ApiClient
import com.bma.android.models.Song
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource

class MusicService : Service() {
    
    companion object {
        const val CHANNEL_ID = "MusicServiceChannel"
        const val NOTIFICATION_ID = 1
        
        // Playback states
        const val STATE_IDLE = 0
        const val STATE_PLAYING = 1
        const val STATE_PAUSED = 2
        const val STATE_STOPPED = 3
    }
    
    private var exoPlayer: ExoPlayer? = null
    private var mediaSession: MediaSessionCompat? = null
    private var currentSong: Song? = null
    private var playlist: List<Song> = emptyList()
    private var currentPosition = 0
    private var playbackState = STATE_IDLE
    private var isShuffleEnabled = false
    private var repeatMode = 0 // 0 = off, 1 = all, 2 = one
    
    // Audio focus management
    private lateinit var audioManager: AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private var hasAudioFocus = false
    
    // Binder for UI communication
    private val binder = MusicBinder()
    
    inner class MusicBinder : Binder() {
        fun getService(): MusicService = this@MusicService
    }
    
    // Listener interface for UI updates
    interface MusicServiceListener {
        fun onPlaybackStateChanged(state: Int)
        fun onSongChanged(song: Song?)
        fun onProgressChanged(progress: Int, duration: Int)
    }
    
    private val listeners = mutableListOf<MusicServiceListener>()
    
    // Progress update handler
    private val handler = Handler(Looper.getMainLooper())
    private val progressUpdateRunnable = object : Runnable {
        override fun run() {
            if (isPlaying() && listeners.isNotEmpty()) {
                listeners.forEach { listener ->
                    listener.onProgressChanged(getCurrentPosition(), getDuration())
                }
                handler.postDelayed(this, 1000) // Update every second
            }
        }
    }
    
    fun addListener(listener: MusicServiceListener) {
        android.util.Log.d("MusicService", "=== ADD LISTENER ===")
        android.util.Log.d("MusicService", "Listener type: ${listener.javaClass.simpleName}")
        android.util.Log.d("MusicService", "Total listeners before: ${listeners.size}")
        
        if (!listeners.contains(listener)) {
            listeners.add(listener)
            android.util.Log.d("MusicService", "Listener added successfully")
        } else {
            android.util.Log.d("MusicService", "Listener already exists, not adding duplicate")
        }
        
        android.util.Log.d("MusicService", "Total listeners after: ${listeners.size}")
        
        if (listeners.isNotEmpty() && isPlaying()) {
            android.util.Log.d("MusicService", "Starting progress updates for listeners")
            startProgressUpdates()
        }
        
        // If we have a current song, immediately notify the new listener
        if (currentSong != null) {
            android.util.Log.d("MusicService", "Immediately notifying new listener of current state")
            listener.onSongChanged(currentSong)
            listener.onPlaybackStateChanged(playbackState)
        }
    }
    
    fun removeListener(listener: MusicServiceListener) {
        android.util.Log.d("MusicService", "=== REMOVE LISTENER ===")
        android.util.Log.d("MusicService", "Listener type: ${listener.javaClass.simpleName}")
        android.util.Log.d("MusicService", "Total listeners before: ${listeners.size}")
        
        listeners.remove(listener)
        
        android.util.Log.d("MusicService", "Total listeners after: ${listeners.size}")
        
        if (listeners.isEmpty()) {
            android.util.Log.d("MusicService", "No more listeners, stopping progress updates")
            stopProgressUpdates()
        }
    }
    
    // Legacy method for backward compatibility
    fun setListener(listener: MusicServiceListener?) {
        android.util.Log.d("MusicService", "=== SET LISTENER (LEGACY) ===")
        android.util.Log.d("MusicService", "This method is deprecated, use addListener/removeListener instead")
        
        if (listener != null) {
            addListener(listener)
        } else {
            android.util.Log.d("MusicService", "Null listener passed to setListener - this may cause issues")
        }
    }
    
    private fun startProgressUpdates() {
        stopProgressUpdates()
        handler.post(progressUpdateRunnable)
    }
    
    private fun stopProgressUpdates() {
        handler.removeCallbacks(progressUpdateRunnable)
    }
    
    override fun onCreate() {
        super.onCreate()
        android.util.Log.d("MusicService", "=== SERVICE ONCREATE ===")
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        createNotificationChannel()
        initializeMediaSession()
        android.util.Log.d("MusicService", "Service created successfully")
    }
    
    // Audio focus change listener
    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        android.util.Log.d("MusicService", "Audio focus changed: $focusChange")
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                // Resume playback or restore volume
                hasAudioFocus = true
                exoPlayer?.volume = 1.0f
                android.util.Log.d("MusicService", "Audio focus gained, resuming if paused")
                if (playbackState == STATE_PAUSED) {
                    play()
                }
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                // Stop playback and abandon focus
                android.util.Log.d("MusicService", "Audio focus lost permanently")
                hasAudioFocus = false
                pause()
                // Don't abandon focus here - keep it for potential regain
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                // Pause playback temporarily
                android.util.Log.d("MusicService", "Audio focus lost temporarily")
                hasAudioFocus = false
                pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // Lower volume instead of pausing
                android.util.Log.d("MusicService", "Audio focus lost, ducking volume")
                hasAudioFocus = false
                exoPlayer?.volume = 0.3f
            }
        }
    }
    
    private fun requestAudioFocus(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
                
            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(audioAttributes)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener(audioFocusChangeListener)
                .build()
                
            val result = audioManager.requestAudioFocus(audioFocusRequest!!)
            hasAudioFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            android.util.Log.d("MusicService", "Audio focus request result: $result, hasAudioFocus: $hasAudioFocus")
            hasAudioFocus
        } else {
            @Suppress("DEPRECATION")
            val result = audioManager.requestAudioFocus(
                audioFocusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
            hasAudioFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            android.util.Log.d("MusicService", "Audio focus request result (legacy): $result, hasAudioFocus: $hasAudioFocus")
            hasAudioFocus
        }
    }
    
    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(audioFocusChangeListener)
        }
        hasAudioFocus = false
        android.util.Log.d("MusicService", "Audio focus abandoned")
    }
    
    override fun onBind(intent: Intent): IBinder {
        android.util.Log.d("MusicService", "=== SERVICE ONBIND CALLED ===")
        android.util.Log.d("MusicService", "Intent: ${intent.action}")
        android.util.Log.d("MusicService", "Current song: ${currentSong?.title}")
        android.util.Log.d("MusicService", "Current state: $playbackState")
        android.util.Log.d("MusicService", "Returning binder...")
        return binder
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        android.util.Log.d("MusicService", "=== SERVICE ONSTARTCOMMAND ===")
        android.util.Log.d("MusicService", "Intent: ${intent?.action}")
        android.util.Log.d("MusicService", "Flags: $flags, StartId: $startId")
        
        // Handle notification actions
        when (intent?.action) {
            "PLAY_PAUSE" -> {
                android.util.Log.d("MusicService", "Handling PLAY_PAUSE action")
                if (isPlaying()) pause() else play()
            }
            "NEXT" -> {
                android.util.Log.d("MusicService", "Handling NEXT action")
                skipToNext()
            }
            "PREVIOUS" -> {
                android.util.Log.d("MusicService", "Handling PREVIOUS action")
                skipToPrevious()
            }
            else -> {
                android.util.Log.d("MusicService", "No specific action, service started normally")
            }
        }
        
        // Return START_STICKY to ensure service is restarted if killed
        android.util.Log.d("MusicService", "Returning START_STICKY")
        return START_STICKY
    }
    
    override fun onDestroy() {
        android.util.Log.w("MusicService", "=== SERVICE ONDESTROY ===")
        android.util.Log.w("MusicService", "Current song: ${currentSong?.title}")
        android.util.Log.w("MusicService", "Playback state: $playbackState")
        android.util.Log.w("MusicService", "Is playing: ${isPlaying()}")
        
        super.onDestroy()
        stopProgressUpdates()
        abandonAudioFocus()
        releasePlayer()
        mediaSession?.release()
        
        android.util.Log.w("MusicService", "Service destroyed - all resources released")
    }
    
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Music Playback",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Controls for music playback"
            setShowBadge(false)
        }
        
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }
    
    private fun initializeMediaSession() {
        mediaSession = MediaSessionCompat(this, "MusicService").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() { play() }
                override fun onPause() { pause() }
                override fun onSkipToNext() { skipToNext() }
                override fun onSkipToPrevious() { skipToPrevious() }
                override fun onStop() { stop() }
            })
            isActive = true
        }
    }
    
    fun loadAndPlay(song: Song, songList: List<Song>, position: Int) {
        android.util.Log.d("MusicService", "=== LOAD AND PLAY ===")
        android.util.Log.d("MusicService", "Song: ${song.title} (${song.id})")
        android.util.Log.d("MusicService", "Playlist size: ${songList.size}, Position: $position")
        android.util.Log.d("MusicService", "Current listener: ${listeners.isNotEmpty()}")
        
        currentSong = song
        playlist = songList
        currentPosition = position
        
        // Temporarily disable audio focus to prevent conflicts
        // requestAudioFocus()
        android.util.Log.d("MusicService", "Proceeding with playback...")
        
        releasePlayer()
        createExoPlayer()
        
        val streamUrl = "${ApiClient.getServerUrl()}/stream/${song.id}"
        val authHeader = ApiClient.getAuthHeader()
        
        android.util.Log.d("MusicService", "Stream URL: $streamUrl")
        android.util.Log.d("MusicService", "Auth header present: ${authHeader != null}")
        
        if (authHeader != null) {
            val mediaItem = MediaItem.Builder()
                .setUri(streamUrl)
                .setMediaId(song.id)
                .build()
                
            android.util.Log.d("MusicService", "Setting media item and preparing...")
            exoPlayer?.apply {
                setMediaItem(mediaItem)
                prepare()
                playWhenReady = true
                android.util.Log.d("MusicService", "ExoPlayer prepared, playWhenReady set to true")
            }
        } else {
            // Handle auth error
            android.util.Log.e("MusicService", "No auth header available")
        }
    }
    
    private fun createExoPlayer() {
        val authHeader = ApiClient.getAuthHeader()
        if (authHeader == null) {
            android.util.Log.e("MusicService", "No auth header for ExoPlayer setup")
            return
        }

        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(mapOf("Authorization" to authHeader))

        val mediaSourceFactory = DefaultMediaSourceFactory(httpDataSourceFactory)

        exoPlayer = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
            
        // Set audio attributes for music playback
        val exoAudioAttributes = com.google.android.exoplayer2.audio.AudioAttributes.Builder()
            .setUsage(com.google.android.exoplayer2.C.USAGE_MEDIA)
            .setContentType(com.google.android.exoplayer2.C.CONTENT_TYPE_MUSIC)
            .build()
        exoPlayer?.setAudioAttributes(exoAudioAttributes, true)
            
        exoPlayer?.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                android.util.Log.d("MusicService", "ExoPlayer state changed: $state")
                when (state) {
                    Player.STATE_READY -> {
                        android.util.Log.d("MusicService", "ExoPlayer ready, playWhenReady: ${exoPlayer?.playWhenReady}")
                        android.util.Log.d("MusicService", "Notifying listener of song change: ${currentSong?.title}")
                        listeners.forEach { listener ->
                            listener.onSongChanged(currentSong)
                        }
                    }
                    Player.STATE_BUFFERING -> {
                        android.util.Log.d("MusicService", "ExoPlayer buffering")
                    }
                    Player.STATE_ENDED -> {
                        android.util.Log.d("MusicService", "ExoPlayer ended")
                        skipToNext()
                    }
                    Player.STATE_IDLE -> {
                        android.util.Log.d("MusicService", "ExoPlayer idle")
                    }
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                android.util.Log.d("MusicService", "ExoPlayer isPlaying changed: $isPlaying")
                playbackState = if (isPlaying) STATE_PLAYING else STATE_PAUSED
                if (isPlaying) {
                    // Temporarily disable foreground service to prevent crashes
                    // startForeground(NOTIFICATION_ID, createNotification())
                    startProgressUpdates()
                } else {
                    // stopForeground(false)
                    // updateNotification()
                    stopProgressUpdates()
                }
                android.util.Log.d("MusicService", "Notifying listener of playback state change: $playbackState")
                listeners.forEach { listener ->
                    listener.onPlaybackStateChanged(playbackState)
                }
                updatePlaybackState()
            }
            
            override fun onPlayerError(error: PlaybackException) {
                android.util.Log.e("MusicService", "ExoPlayer error: ${error.message}", error)
                android.util.Log.e("MusicService", "Error code: ${error.errorCode}")
                
                // Handle different types of errors
                when (error.errorCode) {
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> {
                        android.util.Log.e("MusicService", "Network error, retrying in 2 seconds...")
                        // Retry after a short delay
                        handler.postDelayed({
                            currentSong?.let { song ->
                                android.util.Log.d("MusicService", "Retrying playback for ${song.title}")
                                loadAndPlay(song, playlist, currentPosition)
                            }
                        }, 2000)
                    }
                    else -> {
                        // For other errors, skip to next song
                        android.util.Log.e("MusicService", "Unrecoverable error, skipping to next song")
                        handler.postDelayed({
                            skipToNext()
                        }, 1000)
                    }
                }
                
                // Notify UI of error state
                playbackState = STATE_STOPPED
                listeners.forEach { listener ->
                    listener.onPlaybackStateChanged(playbackState)
                }
            }
        })
    }
    
        fun play() {
        android.util.Log.d("MusicService", "play() called...")
        // Temporarily disable audio focus to prevent conflicts
        // requestAudioFocus()
        
        exoPlayer?.let {
            if (!it.isPlaying) {
                android.util.Log.d("MusicService", "Starting ExoPlayer playback")
                it.play()
            } else {
                android.util.Log.d("MusicService", "ExoPlayer already playing")
            }
        }
    }

    fun pause() {
        exoPlayer?.let {
            if (it.isPlaying) {
                it.pause()
            }
        }
    }

    fun stop() {
        exoPlayer?.stop()
        playbackState = STATE_STOPPED
        // Temporarily disable foreground service to prevent crashes
        // stopForeground(true)
        listeners.forEach { listener ->
            listener.onPlaybackStateChanged(playbackState)
        }
        updatePlaybackState()
    }
    
    fun skipToNext() {
        if (repeatMode == 2) { // Repeat one
            seekTo(0)
            return
        }
        
        if (isShuffleEnabled) {
            // Pick a random song from the playlist (excluding current song)
            val availableIndices = playlist.indices.filter { it != currentPosition }
            if (availableIndices.isNotEmpty()) {
                currentPosition = availableIndices.random()
                loadAndPlay(playlist[currentPosition], playlist, currentPosition)
            } else if (repeatMode == 1) { // Repeat all - pick any random song
                currentPosition = playlist.indices.random()
                loadAndPlay(playlist[currentPosition], playlist, currentPosition)
            } else {
                stop()
            }
        } else {
            // Normal linear progression
            if (currentPosition < playlist.size - 1) {
                currentPosition++
                loadAndPlay(playlist[currentPosition], playlist, currentPosition)
            } else if (repeatMode == 1) { // Repeat all
                currentPosition = 0
                loadAndPlay(playlist[currentPosition], playlist, currentPosition)
            } else {
                stop()
            }
        }
    }
    
    fun skipToPrevious() {
        if (isShuffleEnabled) {
            // Pick a random song from the playlist (excluding current song)
            val availableIndices = playlist.indices.filter { it != currentPosition }
            if (availableIndices.isNotEmpty()) {
                currentPosition = availableIndices.random()
                loadAndPlay(playlist[currentPosition], playlist, currentPosition)
            } else if (repeatMode == 1) { // Repeat all - pick any random song
                currentPosition = playlist.indices.random()
                loadAndPlay(playlist[currentPosition], playlist, currentPosition)
            }
        } else {
            // Normal linear progression
            if (currentPosition > 0) {
                currentPosition--
                loadAndPlay(playlist[currentPosition], playlist, currentPosition)
            } else if (repeatMode == 1) { // Repeat all
                currentPosition = playlist.size - 1
                loadAndPlay(playlist[currentPosition], playlist, currentPosition)
            }
        }
    }
    
    fun seekTo(position: Int) {
        exoPlayer?.seekTo(position.toLong())
    }
    
    fun getCurrentPosition(): Int = exoPlayer?.currentPosition?.toInt() ?: 0
    fun getDuration(): Int = exoPlayer?.duration?.toInt() ?: 0
    fun isPlaying(): Boolean = exoPlayer?.isPlaying ?: false
    fun getCurrentSong(): Song? = currentSong
    fun getPlaybackState(): Int = playbackState
    
    // Shuffle and repeat controls
    fun toggleShuffle(): Boolean {
        isShuffleEnabled = !isShuffleEnabled
        android.util.Log.d("MusicService", "Shuffle toggled: $isShuffleEnabled")
        return isShuffleEnabled
    }
    
    fun isShuffleEnabled(): Boolean = isShuffleEnabled
    
    fun cycleRepeatMode(): Int {
        repeatMode = when (repeatMode) {
            0 -> 1  // off -> repeat all
            1 -> 2  // repeat all -> repeat one
            2 -> 0  // repeat one -> off
            else -> 0
        }
        android.util.Log.d("MusicService", "Repeat mode changed: $repeatMode")
        return repeatMode
    }
    
    fun getRepeatMode(): Int = repeatMode
    
    private fun releasePlayer() {
        exoPlayer?.apply {
            if (isPlaying) stop()
            release()
        }
        exoPlayer = null
    }
    
    private fun createNotification(): Notification {
        val intent = Intent(this, PlayerActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(currentSong?.title ?: "Unknown")
            .setContentText(currentSong?.artist ?: "Unknown Artist")
            .setSmallIcon(R.drawable.ic_music_note)
            .setContentIntent(pendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(
                R.drawable.ic_skip_previous, "Previous",
                createActionPendingIntent("PREVIOUS")
            )
            .addAction(
                if (isPlaying()) R.drawable.ic_pause else R.drawable.ic_play_arrow,
                if (isPlaying()) "Pause" else "Play",
                createActionPendingIntent("PLAY_PAUSE")
            )
            .addAction(
                R.drawable.ic_skip_next, "Next",
                createActionPendingIntent("NEXT")
            )
            .setStyle(androidx.media.app.NotificationCompat.MediaStyle()
                .setMediaSession(mediaSession?.sessionToken)
                .setShowActionsInCompactView(0, 1, 2))
            .build()
    }
    
    private fun updateNotification() {
        if (playbackState != STATE_STOPPED) {
            val notification = createNotification()
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
        }
    }
    
    private fun createActionPendingIntent(action: String): PendingIntent {
        val intent = Intent(this, MusicService::class.java).apply {
            this.action = action
        }
        return PendingIntent.getService(
            this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
    
    private fun updatePlaybackState() {
        val state = when (playbackState) {
            STATE_PLAYING -> PlaybackStateCompat.STATE_PLAYING
            STATE_PAUSED -> PlaybackStateCompat.STATE_PAUSED
            else -> PlaybackStateCompat.STATE_STOPPED
        }
        
        val playbackState = PlaybackStateCompat.Builder()
            .setState(state, getCurrentPosition().toLong(), 1f)
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
            )
            .build()
            
        mediaSession?.setPlaybackState(playbackState)
    }
} 