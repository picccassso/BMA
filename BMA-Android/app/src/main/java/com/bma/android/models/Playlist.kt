package com.bma.android.models

import com.google.gson.annotations.SerializedName
import java.util.UUID

/**
 * Represents a user-created playlist containing a collection of songs
 */
data class Playlist(
    @SerializedName("id")
    val id: String = UUID.randomUUID().toString(),
    
    @SerializedName("name")
    val name: String,
    
    @SerializedName("songIds")
    val songIds: List<String> = emptyList(),
    
    @SerializedName("createdAt")
    val createdAt: Long = System.currentTimeMillis(),
    
    @SerializedName("updatedAt")
    val updatedAt: Long = System.currentTimeMillis()
) {
    /**
     * Get the songs for this playlist from a collection of all songs
     * @param allSongs List of all available songs
     * @return List of songs that are in this playlist
     */
    fun getSongs(allSongs: List<Song>): List<Song> {
        return songIds.mapNotNull { songId ->
            allSongs.find { it.id == songId }
        }
    }
    
    /**
     * Create a new playlist with an additional song
     * @param songId ID of the song to add
     * @return New playlist instance with the song added
     */
    fun addSong(songId: String): Playlist {
        if (songIds.contains(songId)) return this
        
        return copy(
            songIds = songIds + songId,
            updatedAt = System.currentTimeMillis()
        )
    }
    
    /**
     * Create a new playlist with a song removed
     * @param songId ID of the song to remove
     * @return New playlist instance with the song removed
     */
    fun removeSong(songId: String): Playlist {
        return copy(
            songIds = songIds.filter { it != songId },
            updatedAt = System.currentTimeMillis()
        )
    }
    
    /**
     * Create a new playlist with reordered songs
     * @param newSongIds The new order of song IDs
     * @return New playlist instance with songs reordered
     */
    fun reorderSongs(newSongIds: List<String>): Playlist {
        return copy(
            songIds = newSongIds,
            updatedAt = System.currentTimeMillis()
        )
    }
    
    /**
     * Check if this playlist contains a specific song
     * @param songId ID of the song to check
     * @return true if playlist contains the song
     */
    fun containsSong(songId: String): Boolean {
        return songIds.contains(songId)
    }
    
    /**
     * Get the number of songs in this playlist
     */
    val songCount: Int
        get() = songIds.size
    
    /**
     * Check if the playlist is empty
     */
    val isEmpty: Boolean
        get() = songIds.isEmpty()
}