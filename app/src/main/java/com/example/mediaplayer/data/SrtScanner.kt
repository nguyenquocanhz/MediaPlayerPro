package com.example.mediaplayer.data

import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.util.Locale

data class SrtFileItem(
    val name: String,
    val path: String,
    val size: Long
)

object SrtScanner {

    fun scanSrtFiles(context: Context): List<SrtFileItem> {
        val srtList = mutableListOf<SrtFileItem>()
        
        // 1. Query via MediaStore.Files
        try {
            val uri = MediaStore.Files.getContentUri("external")
            val projection = arrayOf(
                MediaStore.Files.FileColumns.DISPLAY_NAME,
                MediaStore.Files.FileColumns.DATA,
                MediaStore.Files.FileColumns.SIZE
            )
            val selection = "${MediaStore.Files.FileColumns.DATA} LIKE '%.srt' OR ${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE '%.srt'"
            val query = context.contentResolver.query(
                uri,
                projection,
                selection,
                null,
                null
            )
            
            query?.use { cursor ->
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                
                while (cursor.moveToNext()) {
                    val displayName = cursor.getString(nameCol) ?: "Unknown.srt"
                    val path = cursor.getString(dataCol) ?: ""
                    val size = cursor.getLong(sizeCol)
                    if (path.lowercase(Locale.ROOT).endsWith(".srt") && File(path).exists()) {
                        srtList.add(SrtFileItem(displayName, path, size))
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Direct folder fallback scanning (Download, Documents, Movies)
        try {
            val publicDirs = listOf(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
            )
            for (dir in publicDirs) {
                if (dir.exists()) {
                    scanDir(dir, srtList)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return srtList.distinctBy { it.path }
    }

    private fun scanDir(dir: File, result: MutableList<SrtFileItem>) {
        val files = dir.listFiles() ?: return
        for (file in files) {
            if (file.isDirectory) {
                // Avoid hidden or android system dirs
                if (!file.name.startsWith(".") && file.name != "Android") {
                    scanDir(file, result)
                }
            } else if (file.name.lowercase(Locale.ROOT).endsWith(".srt")) {
                result.add(
                    SrtFileItem(
                        name = file.name,
                        path = file.absolutePath,
                        size = file.length()
                    )
                )
            }
        }
    }
}
