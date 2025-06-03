package com.bma.android.models

data class Song(
    val id: String,
    val filename: String,
    val title: String,
    val artist: String = "",
    val album: String = ""
) 