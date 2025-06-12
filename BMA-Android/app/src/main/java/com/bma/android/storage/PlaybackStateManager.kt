package com.bma.android.storage

import android.content.Context
import android.content.SharedPreferences
import com.bma.android.models.Song
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class PlaybackState(
    val currentSong: Song? = null,
    val currentPosition: Int = 0,
    val queue: List<Song> = emptyList(),
    val queuePosition: Int = 0,
    val isShuffled: Boolean = false,
    val repeatMode: Int = 0,
    val lastSavedAt: Long = System.currentTimeMillis()
)

class PlaybackStateManager private constructor(private val context: Context) {
    
    companion object {
        @Volatile
        private var INSTANCE: PlaybackStateManager? = null
        private const val PREFS_NAME = "playback_state"
        private const val KEY_PLAYBACK_STATE = "current_state"
        private const val RESTORE_THRESHOLD_MS = 2 * 60 * 60 * 1000L // 2 hours
        
        fun getInstance(context: Context): PlaybackStateManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PlaybackStateManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    
    fun savePlaybackState(
        currentSong: Song?,
        currentPosition: Int,
        queue: List<Song>,
        queuePosition: Int,
        isShuffled: Boolean,
        repeatMode: Int
    ) {
        val playbackState = PlaybackState(
            currentSong = currentSong,
            currentPosition = currentPosition,
            queue = queue,
            queuePosition = queuePosition,
            isShuffled = isShuffled,
            repeatMode = repeatMode,
            lastSavedAt = System.currentTimeMillis()
        )
        
        val stateJson = gson.toJson(playbackState)
        prefs.edit().putString(KEY_PLAYBACK_STATE, stateJson).apply()
        
        android.util.Log.d("PlaybackStateManager", "Saved playback state: ${currentSong?.title}, position: ${currentPosition}ms, queue size: ${queue.size}")
    }
    
    fun getPlaybackState(): PlaybackState? {
        val stateJson = prefs.getString(KEY_PLAYBACK_STATE, null) ?: return null
        
        return try {
            val state = gson.fromJson(stateJson, PlaybackState::class.java)
            
            // Check if the saved state is recent enough to restore
            val timeSinceLastSave = System.currentTimeMillis() - state.lastSavedAt
            if (timeSinceLastSave > RESTORE_THRESHOLD_MS) {
                android.util.Log.d("PlaybackStateManager", "Saved state is too old (${timeSinceLastSave}ms), not restoring")
                return null
            }
            
            android.util.Log.d("PlaybackStateManager", "Loaded playback state: ${state.currentSong?.title}, position: ${state.currentPosition}ms, queue size: ${state.queue.size}")
            state
        } catch (e: Exception) {
            android.util.Log.e("PlaybackStateManager", "Error loading playback state: ${e.message}", e)
            null
        }
    }
    
    fun clearPlaybackState() {
        prefs.edit().remove(KEY_PLAYBACK_STATE).apply()
        android.util.Log.d("PlaybackStateManager", "Cleared playback state")
    }
    
    fun hasValidPlaybackState(): Boolean {
        val state = getPlaybackState()
        return state != null && state.currentSong != null && state.queue.isNotEmpty()
    }
}