package com.example.mediaplayer.data

data class MusicItem(
    val title: String,
    val artist: String,
    val path: String, // local file path or online stream url
    val isOnline: Boolean,
    val duration: Long = 0L,
    val size: Long = 0L,
    val thumbnailUrl: String = ""
)
