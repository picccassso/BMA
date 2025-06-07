package com.bma.android.models

import java.io.Serializable

data class Album(
    val name: String,
    val artist: String?,
    val songs: List<Song>
) : Serializable {
    val trackCount: Int get() = songs.size
    val id: String get() = "${artist}_${name}".replace(" ", "_")
} 