package com.bma.android.models

data class SearchPlayHistory(
    val songId: String,
    val songTitle: String,
    val artist: String,
    val albumName: String,
    val playedAt: Long = System.currentTimeMillis()
)