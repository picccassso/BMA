package com.bma.android.storage

import android.content.Context
import android.content.SharedPreferences
import com.bma.android.models.SearchPlayHistory
import com.bma.android.models.Song
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class SearchPlayHistoryManager private constructor(private val context: Context) {
    
    companion object {
        @Volatile
        private var INSTANCE: SearchPlayHistoryManager? = null
        private const val PREFS_NAME = "search_play_history"
        private const val KEY_HISTORY = "played_songs"
        private const val MAX_HISTORY_SIZE = 50
        
        fun getInstance(context: Context): SearchPlayHistoryManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SearchPlayHistoryManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    
    fun addToHistory(song: Song, albumName: String) {
        val currentHistory = getHistory().toMutableList()
        
        // Remove existing entry for this song to avoid duplicates
        currentHistory.removeAll { it.songId == song.id }
        
        // Add new entry at the beginning
        val newEntry = SearchPlayHistory(
            songId = song.id,
            songTitle = song.title,
            artist = song.artist,
            albumName = albumName,
            playedAt = System.currentTimeMillis()
        )
        currentHistory.add(0, newEntry)
        
        // Keep only the most recent entries
        if (currentHistory.size > MAX_HISTORY_SIZE) {
            currentHistory.subList(MAX_HISTORY_SIZE, currentHistory.size).clear()
        }
        
        saveHistory(currentHistory)
    }
    
    fun getHistory(): List<SearchPlayHistory> {
        val historyJson = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<SearchPlayHistory>>() {}.type
            gson.fromJson(historyJson, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    fun clearHistory() {
        prefs.edit().remove(KEY_HISTORY).apply()
    }
    
    fun removeFromHistory(songId: String) {
        val currentHistory = getHistory().toMutableList()
        currentHistory.removeAll { it.songId == songId }
        saveHistory(currentHistory)
    }
    
    private fun saveHistory(history: List<SearchPlayHistory>) {
        val historyJson = gson.toJson(history)
        prefs.edit().putString(KEY_HISTORY, historyJson).apply()
    }
}