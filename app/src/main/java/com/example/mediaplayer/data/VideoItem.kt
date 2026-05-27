package com.example.mediaplayer.data

import kotlinx.serialization.Serializable

@Serializable
data class VideoItem(
    val id: Long,
    val title: String,
    val path: String,
    val size: Long,
    val duration: Long,
    val dateAdded: Long,
    val folderName: String,
    val extension: String,
    val uriString: String
)
