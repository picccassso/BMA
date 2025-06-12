package com.bma.android.models

import java.io.Serializable

/**
 * Represents the organized library content with separate sections for playlists, albums and standalone songs
 */
data class LibraryContent(
    val playlists: List<Playlist> = emptyList(),
    val albums: List<Album>,
    val standaloneSongs: List<Song>
) : Serializable {
    val hasPlaylists: Boolean get() = playlists.isNotEmpty()
    val hasAlbums: Boolean get() = albums.isNotEmpty()
    val hasStandaloneSongs: Boolean get() = standaloneSongs.isNotEmpty()
    val isEmpty: Boolean get() = playlists.isEmpty() && albums.isEmpty() && standaloneSongs.isEmpty()
}