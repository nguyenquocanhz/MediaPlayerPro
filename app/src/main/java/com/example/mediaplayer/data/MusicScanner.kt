package com.example.mediaplayer.data

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import java.io.File

object MusicScanner {

    fun scanMusic(context: Context): List<MusicItem> {
        val musicList = mutableListOf<MusicItem>()
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DURATION
        )

        // Select only files larger than 10KB and longer than 5 seconds
        val selection = "${MediaStore.Audio.Media.SIZE} > 10240 AND ${MediaStore.Audio.Media.DURATION} > 5000"
        val sortOrder = "${MediaStore.Audio.Media.DATE_ADDED} DESC"

        val query = context.contentResolver.query(
            collection,
            projection,
            selection,
            null,
            sortOrder
        )

        query?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val title = cursor.getString(titleColumn) ?: "Unknown Track"
                val artist = cursor.getString(artistColumn) ?: "Unknown Artist"
                val physicalPath = cursor.getString(dataColumn) ?: ""
                val size = cursor.getLong(sizeColumn)
                val duration = cursor.getLong(durationColumn)

                val uri = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    id
                )

                musicList.add(
                    MusicItem(
                        title = title,
                        artist = if (artist == "<unknown>") "Nghệ sĩ NCT" else artist,
                        path = physicalPath.ifBlank { uri.toString() },
                        isOnline = false,
                        duration = duration,
                        size = size
                    )
                )
            }
        }

        return musicList
    }
}
