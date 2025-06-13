package com.bma.android.storage

import android.content.Context
import android.net.Uri
import com.bma.android.models.Playlist
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Manages local storage of playlists using JSON files
 */
class PlaylistManager private constructor(private val context: Context) {
    
    companion object {
        private const val PLAYLISTS_FILE = "playlists.json"
        private const val BACKUP_VERSION = 1
        private const val MIN_BACKUP_VERSION = 1
        
        @Volatile
        private var INSTANCE: PlaylistManager? = null
        
        fun getInstance(context: Context): PlaylistManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PlaylistManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val playlistsFile = File(context.filesDir, PLAYLISTS_FILE)
    
    /**
     * Load all playlists from storage
     * @return List of all playlists
     */
    suspend fun loadPlaylists(): List<Playlist> = withContext(Dispatchers.IO) {
        try {
            if (!playlistsFile.exists()) {
                return@withContext emptyList()
            }
            
            val json = playlistsFile.readText()
            if (json.isBlank()) {
                return@withContext emptyList()
            }
            
            val type = object : TypeToken<List<Playlist>>() {}.type
            return@withContext gson.fromJson<List<Playlist>>(json, type) ?: emptyList()
        } catch (e: Exception) {
            android.util.Log.e("PlaylistManager", "Error loading playlists", e)
            return@withContext emptyList()
        }
    }
    
    /**
     * Save all playlists to storage
     * @param playlists List of playlists to save
     */
    suspend fun savePlaylists(playlists: List<Playlist>) = withContext(Dispatchers.IO) {
        try {
            val json = gson.toJson(playlists)
            playlistsFile.writeText(json)
            android.util.Log.d("PlaylistManager", "Saved ${playlists.size} playlists")
        } catch (e: Exception) {
            android.util.Log.e("PlaylistManager", "Error saving playlists", e)
            throw e
        }
    }
    
    /**
     * Create a new playlist
     * @param name Name of the new playlist
     * @param songIds Optional list of song IDs to initialize the playlist with
     * @return The created playlist
     */
    suspend fun createPlaylist(name: String, songIds: List<String> = emptyList()): Playlist {
        val playlists = loadPlaylists().toMutableList()
        
        // Check for duplicate names
        val baseName = name.trim()
        var finalName = baseName
        var counter = 1
        
        while (playlists.any { it.name == finalName }) {
            finalName = "$baseName ($counter)"
            counter++
        }
        
        val newPlaylist = Playlist(
            name = finalName,
            songIds = songIds
        )
        
        playlists.add(newPlaylist)
        savePlaylists(playlists)
        
        android.util.Log.d("PlaylistManager", "Created playlist: $finalName with ${songIds.size} songs")
        return newPlaylist
    }
    
    /**
     * Update an existing playlist
     * @param updatedPlaylist The playlist with updated information
     */
    suspend fun updatePlaylist(updatedPlaylist: Playlist) {
        val playlists = loadPlaylists().toMutableList()
        val index = playlists.indexOfFirst { it.id == updatedPlaylist.id }
        
        if (index >= 0) {
            playlists[index] = updatedPlaylist
            savePlaylists(playlists)
            android.util.Log.d("PlaylistManager", "Updated playlist: ${updatedPlaylist.name}")
        } else {
            android.util.Log.w("PlaylistManager", "Playlist not found for update: ${updatedPlaylist.id}")
        }
    }
    
    /**
     * Delete a playlist
     * @param playlistId ID of the playlist to delete
     */
    suspend fun deletePlaylist(playlistId: String) {
        val playlists = loadPlaylists().toMutableList()
        val removed = playlists.removeAll { it.id == playlistId }
        
        if (removed) {
            savePlaylists(playlists)
            android.util.Log.d("PlaylistManager", "Deleted playlist: $playlistId")
        } else {
            android.util.Log.w("PlaylistManager", "Playlist not found for deletion: $playlistId")
        }
    }
    
    /**
     * Add a song to a playlist
     * @param playlistId ID of the playlist
     * @param songId ID of the song to add
     */
    suspend fun addSongToPlaylist(playlistId: String, songId: String) {
        val playlists = loadPlaylists().toMutableList()
        val index = playlists.indexOfFirst { it.id == playlistId }
        
        if (index >= 0) {
            val updatedPlaylist = playlists[index].addSong(songId)
            playlists[index] = updatedPlaylist
            savePlaylists(playlists)
            android.util.Log.d("PlaylistManager", "Added song $songId to playlist: ${updatedPlaylist.name}")
        } else {
            android.util.Log.w("PlaylistManager", "Playlist not found: $playlistId")
        }
    }
    
    /**
     * Remove a song from a playlist
     * @param playlistId ID of the playlist
     * @param songId ID of the song to remove
     */
    suspend fun removeSongFromPlaylist(playlistId: String, songId: String) {
        val playlists = loadPlaylists().toMutableList()
        val index = playlists.indexOfFirst { it.id == playlistId }
        
        if (index >= 0) {
            val updatedPlaylist = playlists[index].removeSong(songId)
            playlists[index] = updatedPlaylist
            savePlaylists(playlists)
            android.util.Log.d("PlaylistManager", "Removed song $songId from playlist: ${updatedPlaylist.name}")
        } else {
            android.util.Log.w("PlaylistManager", "Playlist not found: $playlistId")
        }
    }
    
    /**
     * Get a specific playlist by ID
     * @param playlistId ID of the playlist
     * @return The playlist if found, null otherwise
     */
    suspend fun getPlaylist(playlistId: String): Playlist? {
        return loadPlaylists().find { it.id == playlistId }
    }
    
    /**
     * Check if a song is in any playlist
     * @param songId ID of the song to check
     * @return List of playlist IDs that contain this song
     */
    suspend fun getPlaylistsContainingSong(songId: String): List<String> {
        return loadPlaylists()
            .filter { it.containsSong(songId) }
            .map { it.id }
    }
    
    /**
     * Clear all playlists (for testing or reset purposes)
     */
    suspend fun clearAllPlaylists() = withContext(Dispatchers.IO) {
        try {
            if (playlistsFile.exists()) {
                playlistsFile.delete()
            }
            android.util.Log.d("PlaylistManager", "Cleared all playlists")
        } catch (e: Exception) {
            android.util.Log.e("PlaylistManager", "Error clearing playlists", e)
        }
    }
    
    /**
     * Export playlists to a backup file
     * @param outputUri URI where the backup should be saved
     * @return true if backup was successful, false otherwise
     */
    suspend fun exportBackup(outputUri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val playlists = loadPlaylists()
            val backupData = createBackupData(playlists)
            val json = gson.toJson(backupData)
            
            context.contentResolver.openOutputStream(outputUri)?.use { outputStream ->
                outputStream.write(json.toByteArray())
                outputStream.flush()
            }
            
            android.util.Log.d("PlaylistManager", "Successfully exported ${playlists.size} playlists to backup")
            return@withContext true
        } catch (e: Exception) {
            android.util.Log.e("PlaylistManager", "Error exporting backup", e)
            return@withContext false
        }
    }
    
    /**
     * Import playlists from a backup file
     * @param inputUri URI of the backup file to import
     * @param mergeWithExisting if true, merge with existing playlists; if false, replace all
     * @return ImportResult indicating success and details
     */
    suspend fun importBackup(inputUri: Uri, mergeWithExisting: Boolean = true): ImportResult = withContext(Dispatchers.IO) {
        try {
            val json = context.contentResolver.openInputStream(inputUri)?.use { inputStream ->
                inputStream.readBytes().toString(Charsets.UTF_8)
            } ?: return@withContext ImportResult.Error("Could not read backup file")
            
            val backupData = gson.fromJson(json, BackupData::class.java)
                ?: return@withContext ImportResult.Error("Invalid backup file format")
            
            // Validate backup data
            if (!isValidBackupData(backupData)) {
                return@withContext ImportResult.Error("Invalid or corrupted backup data")
            }
            
            val existingPlaylists = if (mergeWithExisting) loadPlaylists().toMutableList() else mutableListOf()
            val importedPlaylists = backupData.playlists.toMutableList()
            
            // Handle duplicate names when merging
            if (mergeWithExisting) {
                importedPlaylists.forEach { importedPlaylist ->
                    var newName = importedPlaylist.name
                    var counter = 1
                    
                    while (existingPlaylists.any { it.name == newName }) {
                        newName = "${importedPlaylist.name} ($counter)"
                        counter++
                    }
                    
                    if (newName != importedPlaylist.name) {
                        val index = importedPlaylists.indexOf(importedPlaylist)
                        importedPlaylists[index] = importedPlaylist.copy(
                            name = newName,
                            updatedAt = System.currentTimeMillis()
                        )
                    }
                }
            }
            
            val finalPlaylists = existingPlaylists + importedPlaylists
            savePlaylists(finalPlaylists)
            
            val message = if (mergeWithExisting) {
                "Successfully imported ${importedPlaylists.size} playlists (merged with existing)"
            } else {
                "Successfully imported ${importedPlaylists.size} playlists (replaced existing)"
            }
            
            android.util.Log.d("PlaylistManager", message)
            return@withContext ImportResult.Success(importedPlaylists.size, message)
            
        } catch (e: Exception) {
            android.util.Log.e("PlaylistManager", "Error importing backup", e)
            return@withContext ImportResult.Error("Error importing backup: ${e.message}")
        }
    }
    
    /**
     * Generate a suggested filename for backup
     */
    fun generateBackupFilename(): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
        val timestamp = dateFormat.format(Date())
        return "BMA_Playlists_Backup_$timestamp.json"
    }
    
    /**
     * Create backup data structure with metadata
     */
    private suspend fun createBackupData(playlists: List<Playlist>): BackupData {
        return BackupData(
            version = BACKUP_VERSION,
            exportDate = System.currentTimeMillis(),
            playlistCount = playlists.size,
            playlists = playlists
        )
    }
    
    /**
     * Validate backup data structure
     */
    private fun isValidBackupData(backupData: BackupData): Boolean {
        return try {
            backupData.version >= MIN_BACKUP_VERSION &&
            backupData.playlists.isNotEmpty() &&
            backupData.playlistCount == backupData.playlists.size &&
            backupData.playlists.all { it.name.isNotBlank() }
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Data class for backup file structure
     */
    data class BackupData(
        val version: Int,
        val exportDate: Long,
        val playlistCount: Int,
        val playlists: List<Playlist>
    )
    
    /**
     * Result of import operation
     */
    sealed class ImportResult {
        data class Success(val importedCount: Int, val message: String) : ImportResult()
        data class Error(val message: String) : ImportResult()
    }
}