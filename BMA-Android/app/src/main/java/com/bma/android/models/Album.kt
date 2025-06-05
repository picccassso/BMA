package com.bma.android.models

data class Album(
    val name: String,
    val artist: String?,
    val songs: List<Song>
) {
    val trackCount: Int get() = songs.size
    val id: String get() = "${artist}_${name}".replace(" ", "_")
} 