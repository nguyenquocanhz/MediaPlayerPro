package com.example.mediaplayer.data

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import java.io.File
import java.util.Locale

object VideoScanner {

    fun scanVideos(context: Context): List<VideoItem> {
        val videoList = mutableListOf<VideoItem>()

        val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI

        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME
        )

        // Select only files larger than 0 bytes
        val selection = "${MediaStore.Video.Media.SIZE} > 0"
        val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"

        val query = context.contentResolver.query(
            collection,
            projection,
            selection,
            null,
            sortOrder
        )

        query?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
            val bucketColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val displayName = cursor.getString(nameColumn) ?: "Unknown Video"
                val physicalPath = cursor.getString(dataColumn) ?: ""
                val size = cursor.getLong(sizeColumn)
                val duration = cursor.getLong(durationColumn)
                val dateAdded = cursor.getLong(dateAddedColumn)
                val folderName = cursor.getString(bucketColumn) ?: "Internal Memory"

                val uri = ContentUris.withAppendedId(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    id
                )

                // Get extension
                val extension = getFileExtension(physicalPath, displayName)

                videoList.add(
                    VideoItem(
                        id = id,
                        title = displayName,
                        path = physicalPath,
                        size = size,
                        duration = duration,
                        dateAdded = dateAdded,
                        folderName = folderName,
                        extension = extension,
                        uriString = uri.toString()
                    )
                )
            }
        }

        return videoList
    }

    private fun getFileExtension(path: String, name: String): String {
        val file = File(path)
        val extFromPath = file.extension.lowercase(Locale.ROOT)
        if (extFromPath.isNotEmpty()) return extFromPath

        val lastDot = name.lastIndexOf('.')
        if (lastDot != -1 && lastDot < name.length - 1) {
            return name.substring(lastDot + 1).lowercase(Locale.ROOT)
        }
        return "mp4"
    }

    // Helpers to format data in views
    fun formatDuration(durationMs: Long): String {
        if (durationMs <= 0) return "00:00"
        val totalSeconds = durationMs / 1000
        val seconds = totalSeconds % 60
        val minutes = (totalSeconds / 60) % 60
        val hours = totalSeconds / 3600
        return if (hours > 0) {
            String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
        }
    }

    fun formatSize(sizeInBytes: Long): String {
        if (sizeInBytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(sizeInBytes.toDouble()) / Math.log10(1024.0)).toInt()
        val index = if (digitGroups < units.size) digitGroups else units.size - 1
        return String.format(Locale.getDefault(), "%.1f %s", sizeInBytes / Math.pow(1024.0, index.toDouble()), units[index])
    }

    fun formatDate(dateAddedSeconds: Long): String {
        if (dateAddedSeconds <= 0) return "N/A"
        val sdf = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        return sdf.format(java.util.Date(dateAddedSeconds * 1000L))
    }
}
