package com.bma.android

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.media.app.NotificationCompat.MediaStyle
import com.bma.android.api.ApiClient
import com.bma.android.models.Song
import com.bma.android.storage.PlaybackStateManager
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL

/**
 * Queue management class for handling both normal and shuffled playback order
 */
private class MusicQueue {
    private var originalPlaylist: List<Song> = emptyList()
    private var currentQueue: List<Song> = emptyList()
    private var queuePosition: Int = 0
    private var isShuffled: Boolean = false
    
    fun setPlaylist(songs: List<Song>, startIndex: Int = 0) {
        originalPlaylist = songs
        currentQueue = songs.toList()
        queuePosition = startIndex.coerceIn(0, songs.size - 1)
        isShuffled = false
    }
    
    fun shuffle() {
        if (originalPlaylist.isEmpty()) return
        
        // Get current song before shuffling
        val currentSong = getCurrentSong()
        
        if (currentSong != null) {
            // Create list of all songs except current song
            val songsToShuffle = originalPlaylist.filter { it.id != currentSong.id }.toMutableList()
            
            // Shuffle the remaining songs using Fisher-Yates algorithm
            for (i in songsToShuffle.size - 1 downTo 1) {
                val j = (0..i).random()
                val temp = songsToShuffle[i]
                songsToShuffle[i] = songsToShuffle[j]
                songsToShuffle[j] = temp
            }
            
            // Create new queue with current song at position 0, followed by shuffled songs
            currentQueue = mutableListOf<Song>().apply {
                add(currentSong)  // Current song at position 0
                addAll(songsToShuffle)  // Shuffled songs after
            }
            
            // Set position to 0 so current song is playing and all others are upcoming
            queuePosition = 0
        } else {
            // No current song - just shuffle entire playlist
            val shuffled = originalPlaylist.toMutableList()
            for (i in shuffled.size - 1 downTo 1) {
                val j = (0..i).random()
                val temp = shuffled[i]
                shuffled[i] = shuffled[j]
                shuffled[j] = temp
            }
            currentQueue = shuffled
            queuePosition = 0
        }
        
        isShuffled = true
    }
    
    fun unshuffle() {
        if (originalPlaylist.isEmpty()) return
        
        // Get current song before unshuffling
        val currentSong = getCurrentSong()
        
        // Restore original order
        currentQueue = originalPlaylist.toList()
        isShuffled = false
        
        // Find current song in original order and set position
        currentSong?.let { song ->
            queuePosition = currentQueue.indexOfFirst { it.id == song.id }
            if (queuePosition == -1) queuePosition = 0
        }
    }
    
    fun next(): Song? {
        android.util.Log.d("MusicQueue", "🎵 === NEXT() CALLED ===")
        android.util.Log.d("MusicQueue", "Current queue size: ${currentQueue.size}")
        android.util.Log.d("MusicQueue", "Current position: $queuePosition")
        android.util.Log.d("MusicQueue", "Can move next: ${queuePosition < currentQueue.size - 1}")
        
        if (currentQueue.isEmpty()) {
            android.util.Log.d("MusicQueue", "❌ Queue is empty")
            return null
        }
        
        if (queuePosition < currentQueue.size - 1) {
            queuePosition++
            val nextSong = currentQueue[queuePosition]
            android.util.Log.d("MusicQueue", "✅ Moving to position $queuePosition: ${nextSong.title}")
            
            // Log current queue state
            android.util.Log.d("MusicQueue", "📝 Current queue after next():")
            currentQueue.forEachIndexed { index, song ->
                val marker = if (index == queuePosition) "👉" else "  "
                android.util.Log.d("MusicQueue", "$marker[$index] ${song.title}")
            }
            
            return nextSong
        }
        
        android.util.Log.d("MusicQueue", "⏭️ Reached end of queue")
        return null // End of queue
    }
    
    fun previous(): Song? {
        if (currentQueue.isEmpty()) return null
        
        if (queuePosition > 0) {
            queuePosition--
            return currentQueue[queuePosition]
        }
        
        return null // Beginning of queue
    }
    
    fun getCurrentSong(): Song? {
        return if (currentQueue.isNotEmpty() && queuePosition in currentQueue.indices) {
            currentQueue[queuePosition]
        } else null
    }
    
    fun hasNext(): Boolean = queuePosition < currentQueue.size - 1
    
    fun hasPrevious(): Boolean = queuePosition > 0
    
    fun getIsShuffled(): Boolean = isShuffled
    
    fun getCurrentPosition(): Int = queuePosition
    
    fun size(): Int = currentQueue.size
    
    fun isEmpty(): Boolean = currentQueue.isEmpty()
    
    fun getOriginalPlaylist(): List<Song> = originalPlaylist.toList()
    
    // Dynamic queue management methods
    fun addToQueue(song: Song) {
        if (currentQueue.isEmpty()) return
        
        currentQueue = currentQueue.toMutableList().apply { add(song) }
        if (!isShuffled) {
            originalPlaylist = originalPlaylist.toMutableList().apply { add(song) }
        }
    }
    
    fun addToQueue(songs: List<Song>) {
        if (currentQueue.isEmpty() || songs.isEmpty()) return
        
        currentQueue = currentQueue.toMutableList().apply { addAll(songs) }
        if (!isShuffled) {
            originalPlaylist = originalPlaylist.toMutableList().apply { addAll(songs) }
        }
    }
    
    fun addNext(song: Song) {
        if (currentQueue.isEmpty()) return
        
        val nextPosition = queuePosition + 1
        currentQueue = currentQueue.toMutableList().apply { 
            add(nextPosition.coerceAtMost(size), song) 
        }
        if (!isShuffled) {
            // For original playlist, add after current song's original position
            val currentSong = getCurrentSong()
            val originalIndex = originalPlaylist.indexOfFirst { it.id == currentSong?.id }
            if (originalIndex >= 0) {
                originalPlaylist = originalPlaylist.toMutableList().apply { 
                    add(originalIndex + 1, song) 
                }
            }
        }
    }
    
    /**
     * Remove a song from the queue at the specified position
     * @param position The position in the queue to remove (0-based)
     * @return true if removal was successful, false if position invalid or current song
     */
    fun removeFromQueue(position: Int): Boolean {
        if (position < 0 || position >= currentQueue.size) return false
        if (position == queuePosition) return false // Don't remove currently playing song
        
        val mutableQueue = currentQueue.toMutableList()
        val songToRemove = mutableQueue[position]
        mutableQueue.removeAt(position)
        currentQueue = mutableQueue
        
        // Adjust queue position if removing before current position
        if (position < queuePosition) {
            queuePosition--
        }
        
        // Also remove from original playlist if not shuffled
        if (!isShuffled) {
            originalPlaylist = originalPlaylist.toMutableList().apply { 
                removeAll { it.id == songToRemove.id }
            }
        }
        
        return true
    }
    
    /**
     * Move a song in the queue from one position to another
     * @param fromPosition Source position (0-based)
     * @param toPosition Target position (0-based)
     * @return true if move was successful, false if positions invalid
     */
    fun moveQueueItem(fromPosition: Int, toPosition: Int): Boolean {
        if (fromPosition < 0 || fromPosition >= currentQueue.size) return false
        if (toPosition < 0 || toPosition >= currentQueue.size) return false
        if (fromPosition == toPosition) return false
        
        // Update the current queue
        val mutableQueue = currentQueue.toMutableList()
        val songToMove = mutableQueue.removeAt(fromPosition)
        mutableQueue.add(toPosition, songToMove)
        currentQueue = mutableQueue
        
        // CRITICAL FIX: Also update the original playlist if not shuffled
        // This ensures the queue stays consistent when skipping tracks
        if (!isShuffled) {
            val mutableOriginal = originalPlaylist.toMutableList()
            val originalSongToMove = mutableOriginal.removeAt(fromPosition)
            mutableOriginal.add(toPosition, originalSongToMove)
            originalPlaylist = mutableOriginal
            
            android.util.Log.d("MusicQueue", "📝 Updated original playlist:")
            originalPlaylist.forEachIndexed { index, song ->
                android.util.Log.d("MusicQueue", "  [$index] ${song.title}")
            }
        }
        
        // Update queue position if needed
        when {
            fromPosition == queuePosition -> queuePosition = toPosition
            fromPosition < queuePosition && toPosition >= queuePosition -> queuePosition--
            fromPosition > queuePosition && toPosition <= queuePosition -> queuePosition++
        }
        
        return true
    }
    
    /**
     * Jump to a specific position in the queue
     * @param position The queue position to jump to (0-based)
     * @return The song at that position, or null if position invalid
     */
    fun jumpToQueuePosition(position: Int): Song? {
        if (position < 0 || position >= currentQueue.size) return null
        
        queuePosition = position
        return getCurrentSong()
    }
    
    fun getQueueContents(): List<Song> = currentQueue.toList()
    
    fun getQueueFromPosition(position: Int = queuePosition): List<Song> {
        return if (position < currentQueue.size) {
            currentQueue.drop(position)
        } else {
            emptyList()
        }
    }
}

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
    private var musicQueue = MusicQueue()
    private var playbackState = STATE_IDLE
    private var repeatMode = 0 // 0 = off, 1 = all, 2 = one
    private var currentAlbumArt: Bitmap? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    private var isForegroundStarted = false
    private lateinit var playbackStateManager: PlaybackStateManager
    
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
        fun onQueueChanged(queue: List<Song>) {}  // Optional method with default implementation
    }
    
    private val listeners = mutableListOf<MusicServiceListener>()
    
    // Progress update handler
    private val handler = Handler(Looper.getMainLooper())
    private var lastStateSaveTime = 0L
    private val progressUpdateRunnable = object : Runnable {
        override fun run() {
            if (isPlaying() && listeners.isNotEmpty()) {
                listeners.forEach { listener ->
                    listener.onProgressChanged(getCurrentPosition(), getDuration())
                }
                
                // Save playback state every 10 seconds during playback
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastStateSaveTime > 10000) { // 10 seconds
                    saveCurrentPlaybackState()
                    lastStateSaveTime = currentTime
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
        playbackStateManager = PlaybackStateManager.getInstance(this)
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
    
    override fun onTaskRemoved(rootIntent: Intent?) {
        android.util.Log.w("MusicService", "=== TASK REMOVED (APP SWIPED AWAY) ===")
        android.util.Log.w("MusicService", "Current song: ${currentSong?.title}")
        android.util.Log.w("MusicService", "Is playing: ${isPlaying()}")
        
        // Save current playback state before stopping
        saveCurrentPlaybackState()
        
        // Stop music playback immediately
        exoPlayer?.stop()
        playbackState = STATE_STOPPED
        
        // Remove notification and stop foreground service
        stopForeground(true)
        isForegroundStarted = false
        
        // Stop the service completely
        stopSelf()
        
        android.util.Log.w("MusicService", "Music stopped and service stopping due to app being swiped away")
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        android.util.Log.w("MusicService", "=== SERVICE ONDESTROY ===")
        android.util.Log.w("MusicService", "Current song: ${currentSong?.title}")
        android.util.Log.w("MusicService", "Playback state: $playbackState")
        android.util.Log.w("MusicService", "Is playing: ${isPlaying()}")
        
        // Save current playback state before destroying
        saveCurrentPlaybackState()
        
        super.onDestroy()
        stopProgressUpdates()
        abandonAudioFocus()
        releasePlayer()
        mediaSession?.release()
        
        android.util.Log.w("MusicService", "Service destroyed - all resources released")
    }
    
    private fun saveCurrentPlaybackState() {
        try {
            if (currentSong != null && !musicQueue.isEmpty()) {
                playbackStateManager.savePlaybackState(
                    currentSong = currentSong,
                    currentPosition = getCurrentPosition(),
                    queue = musicQueue.getQueueContents(),
                    queuePosition = musicQueue.getCurrentPosition(),
                    isShuffled = musicQueue.getIsShuffled(),
                    repeatMode = repeatMode
                )
                android.util.Log.d("MusicService", "Playback state saved successfully")
            } else {
                android.util.Log.d("MusicService", "No playback state to save (no song or empty queue)")
            }
        } catch (e: Exception) {
            android.util.Log.e("MusicService", "Error saving playback state: ${e.message}", e)
        }
    }
    
    fun restorePlaybackState(): Boolean {
        try {
            val savedState = playbackStateManager.getPlaybackState()
            if (savedState != null && savedState.currentSong != null && savedState.queue.isNotEmpty()) {
                android.util.Log.d("MusicService", "Restoring playback state: ${savedState.currentSong.title}")
                
                // Restore the queue and position
                musicQueue.setPlaylist(savedState.queue, savedState.queuePosition)
                if (savedState.isShuffled) {
                    musicQueue.shuffle()
                }
                
                // Restore other settings
                repeatMode = savedState.repeatMode
                currentSong = savedState.currentSong
                
                // Prepare the player for the saved song
                releasePlayer()
                createExoPlayer()
                
                val streamUrl = "${ApiClient.getServerUrl()}/stream/${savedState.currentSong.id}"
                val authHeader = ApiClient.getAuthHeader()
                
                if (authHeader != null) {
                    val mediaItem = MediaItem.Builder()
                        .setUri(streamUrl)
                        .setMediaId(savedState.currentSong.id)
                        .build()
                    
                    exoPlayer?.apply {
                        setMediaItem(mediaItem)
                        prepare()
                        // Don't start playing automatically - let user choose
                        playWhenReady = false
                        
                        // Seek to saved position once ready
                        addListener(object : Player.Listener {
                            override fun onPlaybackStateChanged(state: Int) {
                                if (state == Player.STATE_READY) {
                                    seekTo(savedState.currentPosition)
                                    // Remove this listener after seeking
                                    removeListener(this)
                                    
                                    // Notify listeners of restored state
                                    listeners.forEach { listener ->
                                        listener.onSongChanged(currentSong)
                                        listener.onPlaybackStateChanged(playbackState)
                                    }
                                }
                            }
                        })
                    }
                    
                    android.util.Log.d("MusicService", "Playback state restored successfully")
                    return true
                } else {
                    android.util.Log.e("MusicService", "No auth header available for restoration")
                }
            } else {
                android.util.Log.d("MusicService", "No valid playback state to restore")
            }
        } catch (e: Exception) {
            android.util.Log.e("MusicService", "Error restoring playback state: ${e.message}", e)
        }
        return false
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Music Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Controls for music playback"
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
                setSound(null, null) // No sound for updates
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
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
        
        // Initialize queue with new playlist
        musicQueue.setPlaylist(songList, position)
        currentSong = song
        
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
            .setContentType(com.google.android.exoplayer2.C.AUDIO_CONTENT_TYPE_MUSIC)
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
                
                try {
                    // Always ensure we have a foreground service when there's music activity
                    if (!isForegroundStarted && currentSong != null) {
                        android.util.Log.d("MusicService", "=== STARTING FOREGROUND SERVICE (ONCE) ===")
                        val basicNotification = createBasicNotification()
                        startForeground(NOTIFICATION_ID, basicNotification)
                        isForegroundStarted = true
                        android.util.Log.d("MusicService", "✅ Foreground service started successfully")
                    }
                    
                    // Always update notification content for any state change
                    if (isForegroundStarted) {
                        currentSong?.let { song ->
                            android.util.Log.d("MusicService", "Updating notification for: ${song.title} (playing: $isPlaying)")
                            try {
                                updateMediaMetadata(song)
                                if (isPlaying) {
                                    loadAlbumArt(song) // Only load album art when playing
                                }
                                updateNotification()
                            } catch (e: Exception) {
                                android.util.Log.e("MusicService", "Error updating notification: ${e.message}", e)
                            }
                        }
                    }
                    
                    // Handle progress updates
                    if (isPlaying) {
                        startProgressUpdates()
                    } else {
                        stopProgressUpdates()
                    }
                    
                } catch (e: Exception) {
                    android.util.Log.e("MusicService", "❌ CRITICAL ERROR in onIsPlayingChanged: ${e.message}", e)
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
                
                // Check if this is an authentication error
                val isAuthError = error.message?.contains("401") == true || 
                                error.message?.contains("403") == true ||
                                error.message?.contains("Unauthorized") == true ||
                                error.message?.contains("Forbidden") == true
                
                if (isAuthError) {
                    android.util.Log.e("MusicService", "Authentication error detected, triggering auth failure callback")
                    ApiClient.onAuthFailure?.invoke()
                    playbackState = STATE_STOPPED
                    listeners.forEach { listener ->
                        listener.onPlaybackStateChanged(playbackState)
                    }
                    return
                }
                
                // Handle different types of errors
                when (error.errorCode) {
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> {
                        android.util.Log.e("MusicService", "Network error, retrying in 2 seconds...")
                        // Retry after a short delay
                        handler.postDelayed({
                            currentSong?.let { song ->
                                android.util.Log.d("MusicService", "Retrying playback for ${song.title}")
                                playNewSong(song)
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
        stopForeground(true)
        isForegroundStarted = false
        listeners.forEach { listener ->
            listener.onPlaybackStateChanged(playbackState)
        }
        updatePlaybackState()
    }
    
    fun skipToNext() {
        android.util.Log.d("MusicService", "🎵 === SKIP TO NEXT ===")
        android.util.Log.d("MusicService", "Repeat mode: $repeatMode")
        android.util.Log.d("MusicService", "Current queue position: ${musicQueue.getCurrentPosition()}")
        android.util.Log.d("MusicService", "Queue size: ${musicQueue.size()}")
        android.util.Log.d("MusicService", "Has next: ${musicQueue.hasNext()}")
        
        if (repeatMode == 2) { // Repeat one
            android.util.Log.d("MusicService", "🔂 Repeat one - seeking to 0")
            seekTo(0)
            return
        }
        
        val nextSong = musicQueue.next()
        if (nextSong != null) {
            android.util.Log.d("MusicService", "▶️ Playing next song: ${nextSong.title}")
            // Move to next song in queue - don't reinitialize the queue
            currentSong = nextSong
            playNewSong(nextSong)
        } else if (repeatMode == 1) { // Repeat all - go back to beginning
            android.util.Log.d("MusicService", "🔁 Repeat all - resetting to beginning")
            
            // POTENTIAL ISSUE: This might be resetting our reordered queue!
            val playlist = getCurrentPlaylistFromQueue()
            android.util.Log.d("MusicService", "📝 Playlist from queue (size: ${playlist.size}):")
            playlist.forEachIndexed { index, song ->
                android.util.Log.d("MusicService", "  [$index] ${song.title}")
            }
            
            val wasShuffled = musicQueue.getIsShuffled()
            android.util.Log.d("MusicService", "Was shuffled: $wasShuffled")
            
            musicQueue.setPlaylist(playlist, 0)
            if (wasShuffled) {
                musicQueue.shuffle()
            }
            val firstSong = musicQueue.getCurrentSong()
            if (firstSong != null) {
                android.util.Log.d("MusicService", "▶️ Playing first song: ${firstSong.title}")
                currentSong = firstSong
                playNewSong(firstSong)
            }
        } else {
            android.util.Log.d("MusicService", "⏹️ End of queue, no repeat - stopping")
            // End of queue, no repeat
            stop()
        }
        
        android.util.Log.d("MusicService", "🏁 === SKIP TO NEXT COMPLETE ===")
    }
    
    fun skipToPrevious() {
        val previousSong = musicQueue.previous()
        if (previousSong != null) {
            // Move to previous song in queue - don't reinitialize the queue
            currentSong = previousSong
            playNewSong(previousSong)
        } else if (repeatMode == 1) { // Repeat all - go to end
            // Reset queue position to end
            val playlist = getCurrentPlaylistFromQueue()
            val wasShuffled = musicQueue.getIsShuffled()
            musicQueue.setPlaylist(playlist, playlist.size - 1)
            if (wasShuffled) {
                musicQueue.shuffle()
                // Navigate to last position in shuffled queue
                repeat(playlist.size - 1) { musicQueue.next() }
            }
            val lastSong = musicQueue.getCurrentSong()
            if (lastSong != null) {
                currentSong = lastSong
                playNewSong(lastSong)
            }
        }
        // If no previous and no repeat, just stay at current song
    }
    
    private fun getCurrentPlaylistFromQueue(): List<Song> {
        android.util.Log.d("MusicService", "🔍 getCurrentPlaylistFromQueue() called")
        val originalPlaylist = musicQueue.getOriginalPlaylist()
        android.util.Log.d("MusicService", "📋 Original playlist size: ${originalPlaylist.size}")
        originalPlaylist.forEachIndexed { index, song ->
            android.util.Log.d("MusicService", "  [$index] ${song.title}")
        }
        return originalPlaylist
    }
    
    private fun playNewSong(song: Song) {
        android.util.Log.d("MusicService", "=== PLAY NEW SONG ===")
        android.util.Log.d("MusicService", "Song: ${song.title} (${song.id})")
        
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
        if (musicQueue.getIsShuffled()) {
            // Currently shuffled, turn off shuffle
            musicQueue.unshuffle()
            android.util.Log.d("MusicService", "Shuffle disabled - restored original order")
        } else {
            // Currently not shuffled, turn on shuffle
            musicQueue.shuffle()
            android.util.Log.d("MusicService", "Shuffle enabled - created shuffled order")
        }
        
        val isShuffleEnabled = musicQueue.getIsShuffled()
        android.util.Log.d("MusicService", "Shuffle toggled: $isShuffleEnabled")
        
        // CRITICAL FIX: Notify Queue UI of the shuffle change
        notifyQueueChanged()
        
        return isShuffleEnabled
    }
    
    fun isShuffleEnabled(): Boolean = musicQueue.getIsShuffled()
    
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
    
    // Queue management methods
    fun addToQueue(song: Song) {
        musicQueue.addToQueue(song)
        android.util.Log.d("MusicService", "Added to queue: ${song.title}")
        notifyQueueChanged()
    }
    
    fun addToQueue(songs: List<Song>) {
        musicQueue.addToQueue(songs)
        android.util.Log.d("MusicService", "Added ${songs.size} songs to queue")
        notifyQueueChanged()
    }
    
    fun addNext(song: Song) {
        musicQueue.addNext(song)
        android.util.Log.d("MusicService", "Added next: ${song.title}")
        notifyQueueChanged()
    }
    
    fun getCurrentQueue(): List<Song> = musicQueue.getQueueContents()
    
    fun getUpcomingQueue(): List<Song> {
        val currentPos = musicQueue.getCurrentPosition()
        val upcomingQueue = musicQueue.getQueueFromPosition(currentPos + 1)
        android.util.Log.d("MusicService", "📋 getUpcomingQueue() - currentPos=$currentPos, upcoming size=${upcomingQueue.size}")
        return upcomingQueue
    }
    
    /**
     * Remove a song from the queue at the specified position
     * @param position The position in the queue to remove (0-based)
     * @return true if removal was successful
     */
    fun removeFromQueue(position: Int): Boolean {
        val success = musicQueue.removeFromQueue(position)
        if (success) {
            android.util.Log.d("MusicService", "Removed song from queue at position: $position")
            notifyQueueChanged()
        }
        return success
    }
    
    /**
     * Move a song in the queue from one position to another
     * @param fromPosition Source position (0-based)
     * @param toPosition Target position (0-based)
     * @return true if move was successful
     */
    fun moveQueueItem(fromPosition: Int, toPosition: Int): Boolean {
        android.util.Log.d("MusicService", "🎯 === MOVE QUEUE ITEM ===")
        android.util.Log.d("MusicService", "fromPosition=$fromPosition, toPosition=$toPosition")
        android.util.Log.d("MusicService", "Current queue size: ${musicQueue.size()}")
        android.util.Log.d("MusicService", "Current position in queue: ${musicQueue.getCurrentPosition()}")
        
        try {
            // Log current queue before move
            android.util.Log.d("MusicService", "📝 Queue before move:")
            val currentQueueContents = musicQueue.getQueueContents()
            currentQueueContents.forEachIndexed { index, song ->
                android.util.Log.d("MusicService", "  [$index] ${song.title}")
            }
            
            val success = musicQueue.moveQueueItem(fromPosition, toPosition)
            
            if (success) {
                android.util.Log.d("MusicService", "✅ Successfully moved queue item from $fromPosition to $toPosition")
                
                // Log queue after move
                android.util.Log.d("MusicService", "📝 Queue after move:")
                val newQueueContents = musicQueue.getQueueContents()
                newQueueContents.forEachIndexed { index, song ->
                    android.util.Log.d("MusicService", "  [$index] ${song.title}")
                }
                
                // Immediate notification to ensure UI updates quickly
                android.util.Log.d("MusicService", "🔔 Notifying queue changed immediately")
                notifyQueueChanged()
            } else {
                android.util.Log.w("MusicService", "❌ Failed to move queue item from $fromPosition to $toPosition")
            }
            
            android.util.Log.d("MusicService", "🏁 === MOVE QUEUE ITEM COMPLETE ===")
            return success
        } catch (e: Exception) {
            android.util.Log.e("MusicService", "💥 Error moving queue item: ${e.message}", e)
            return false
        }
    }
    
    /**
     * Jump to a specific position in the queue and start playing
     * @param position The queue position to jump to (0-based)
     * @return true if jump was successful
     */
    fun jumpToQueuePosition(position: Int): Boolean {
        val song = musicQueue.jumpToQueuePosition(position)
        if (song != null) {
            android.util.Log.d("MusicService", "Jumped to queue position: $position, song: ${song.title}")
            currentSong = song
            // Start playing the new song
            playNewSong(song)
            // Notify listeners of song change
            listeners.forEach { listener ->
                listener.onSongChanged(song)
            }
            notifyQueueChanged()
            return true
        }
        return false
    }
    
    private fun notifyQueueChanged() {
        val queue = getCurrentQueue()
        android.util.Log.d("MusicService", "🔔 === NOTIFYING QUEUE CHANGED ===")
        android.util.Log.d("MusicService", "Queue size: ${queue.size}")
        android.util.Log.d("MusicService", "Number of listeners: ${listeners.size}")
        
        listeners.forEach { listener ->
            android.util.Log.d("MusicService", "📢 Notifying listener: ${listener.javaClass.simpleName}")
            listener.onQueueChanged(queue)
        }
        
        android.util.Log.d("MusicService", "🏁 === QUEUE CHANGE NOTIFICATION COMPLETE ===")
    }
    
    private fun releasePlayer() {
        exoPlayer?.apply {
            if (isPlaying) stop()
            release()
        }
        exoPlayer = null
    }
    
    private fun createNotification(): Notification {
        android.util.Log.d("MusicService", "Creating notification for song: ${currentSong?.title}")
        
        val intent = Intent(this, PlayerActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val song = currentSong ?: return createBasicNotification()
        
        return try {
            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(song.title)
                .setContentText(song.artist)
                .setSubText(song.album)
                .setSmallIcon(R.drawable.ic_music_note)
                .setContentIntent(pendingIntent)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(isPlaying()) // Only ongoing when actually playing
                .setShowWhen(false)
                .setOnlyAlertOnce(true) // Prevent notification sound/vibration on updates
                .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
                .setPriority(NotificationCompat.PRIORITY_LOW) // Low priority to reduce interruptions
                .apply {
                    currentAlbumArt?.let { bitmap ->
                        setLargeIcon(bitmap)
                    }
                }
                .addAction(
                    R.drawable.ic_skip_previous, 
                    "Previous",
                    createActionPendingIntent("PREVIOUS")
                )
                .addAction(
                    if (isPlaying()) R.drawable.ic_pause else R.drawable.ic_play_arrow,
                    if (isPlaying()) "Pause" else "Play",
                    createActionPendingIntent("PLAY_PAUSE")
                )
                .addAction(
                    R.drawable.ic_skip_next, 
                    "Next",
                    createActionPendingIntent("NEXT")
                )
                .setStyle(MediaStyle()
                    .setMediaSession(mediaSession?.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
                    .setShowCancelButton(false))
                .build()
                
            android.util.Log.d("MusicService", "Notification created successfully")
            notification
        } catch (e: Exception) {
            android.util.Log.e("MusicService", "Error creating notification: ${e.message}", e)
            createBasicNotification()
        }
    }
    
    private fun createBasicNotification(): Notification {
        android.util.Log.d("MusicService", "Creating basic notification")
        
        val intent = Intent(this, PlayerActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return try {
            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Music Player")
                .setContentText("Ready to play")
                .setSmallIcon(R.drawable.ic_music_note)
                .setContentIntent(pendingIntent)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .build()
                
            android.util.Log.d("MusicService", "Basic notification created successfully")
            notification
        } catch (e: Exception) {
            android.util.Log.e("MusicService", "Error creating basic notification: ${e.message}", e)
            // Fallback to absolute minimum notification
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Music")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .build()
        }
    }
    
    private fun updateNotification() {
        try {
            if (playbackState != STATE_STOPPED && currentSong != null) {
                val notification = createNotification()
                
                // Check notification permission on API 33+
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == 
                        android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
                    } else {
                        android.util.Log.w("MusicService", "Notification permission not granted")
                    }
                } else {
                    // Below API 33, no explicit permission needed
                    NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
                }
                android.util.Log.d("MusicService", "Notification updated")
            }
        } catch (e: Exception) {
            android.util.Log.e("MusicService", "Failed to update notification: ${e.message}", e)
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
    
    private fun loadAlbumArt(song: Song) {
        android.util.Log.d("MusicService", "🎨 === ALBUM ART LOADING DEBUG ===")
        android.util.Log.d("MusicService", "Song: ${song.title}")
        android.util.Log.d("MusicService", "Song ID: ${song.id}")
        android.util.Log.d("MusicService", "Has artwork flag: ${song.hasArtwork}")
        android.util.Log.d("MusicService", "Server URL: ${ApiClient.getServerUrl()}")
        android.util.Log.d("MusicService", "Auth header present: ${ApiClient.getAuthHeader() != null}")
        
        // Always try loading album art for debugging (ignore hasArtwork flag)
        android.util.Log.d("MusicService", "🚀 Starting coroutine...")
        
        coroutineScope.launch {
            android.util.Log.d("MusicService", "🔄 Inside coroutine - starting album art load")
            try {
                val artworkUrl = "${ApiClient.getServerUrl()}/artwork/${song.id}"
                val authHeader = ApiClient.getAuthHeader()
                
                android.util.Log.d("MusicService", "🌐 Attempting to load from: $artworkUrl")
                android.util.Log.d("MusicService", "🔐 Auth header: ${authHeader?.take(20)}...")
                
                if (authHeader != null) {
                    val bitmap = withContext(Dispatchers.IO) {
                        try {
                            android.util.Log.d("MusicService", "📡 Making HTTP request...")
                            val connection = URL(artworkUrl).openConnection()
                            connection.setRequestProperty("Authorization", authHeader)
                            connection.connectTimeout = 5000
                            connection.readTimeout = 10000
                            
                            android.util.Log.d("MusicService", "📊 Response code: ${(connection as java.net.HttpURLConnection).responseCode}")
                            
                            if (connection.responseCode == 200) {
                                val inputStream = connection.getInputStream()
                                val result = BitmapFactory.decodeStream(inputStream)
                                inputStream.close()
                                android.util.Log.d("MusicService", "🖼️ Bitmap decoded: ${result != null}")
                                if (result != null) {
                                    android.util.Log.d("MusicService", "📏 Bitmap size: ${result.width}x${result.height}")
                                }
                                result
                            } else {
                                android.util.Log.w("MusicService", "❌ HTTP ${connection.responseCode}: ${connection.responseMessage}")
                                null
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("MusicService", "💥 Network error: ${e.message}", e)
                            null
                        }
                    }
                    
                    if (bitmap != null) {
                        android.util.Log.d("MusicService", "✅ Album art loaded successfully!")
                        currentAlbumArt = bitmap
                        
                        // Only update if we're still playing the same song
                        if (currentSong?.id == song.id) {
                            android.util.Log.d("MusicService", "🔄 Updating notification with album art...")
                            updateMediaMetadata(song)
                            updateNotification()
                        } else {
                            android.util.Log.w("MusicService", "⚠️ Song changed, not updating notification")
                        }
                    } else {
                        android.util.Log.w("MusicService", "❌ Album art bitmap is null")
                    }
                } else {
                    android.util.Log.e("MusicService", "❌ No auth header available for album art")
                }
                
            } catch (e: Exception) {
                android.util.Log.e("MusicService", "💥 Critical error loading album art: ${e.message}", e)
            }
        }
    }
    
    private fun updateMediaMetadata(song: Song) {
        try {
            val metadata = MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, song.title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, song.artist)
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, song.album)
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, getDuration().toLong())
                .apply {
                    currentAlbumArt?.let { bitmap ->
                        putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, bitmap)
                    }
                }
                .build()
                
            mediaSession?.setMetadata(metadata)
            android.util.Log.d("MusicService", "MediaMetadata updated for: ${song.title}")
        } catch (e: Exception) {
            android.util.Log.e("MusicService", "Failed to update MediaMetadata: ${e.message}", e)
        }
    }
} 