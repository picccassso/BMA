package com.bma.android.models

import java.io.Serializable

data class Song(
    val id: String,
    val filename: String,
    val title: String,
    val artist: String = "",
    val album: String = "",
    val trackNumber: Int = 0,
    val parentDirectory: String = "",
    val hasArtwork: Boolean = false,
    val sortOrder: Int = 0  // Server's explicit ordering for proper track sequence
) : Serializable 