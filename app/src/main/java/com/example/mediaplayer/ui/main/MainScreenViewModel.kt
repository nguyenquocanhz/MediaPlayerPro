package com.example.mediaplayer.ui.main

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mediaplayer.data.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

@Serializable
data class HlsStreamItem(
    val title: String,
    val url: String,
    val isImported: Boolean = true
)

@Serializable
data class VideoPlaylistItem(
    val title: String,
    val path: String,
    val isOnline: Boolean
)

enum class SortType { DATE, SIZE, FORMAT }
enum class SortOrder { ASC, DESC }

class MainScreenViewModel : ViewModel() {

    private val _importedStreams = MutableStateFlow<List<HlsStreamItem>>(emptyList())
    val importedStreams: StateFlow<List<HlsStreamItem>> = _importedStreams.asStateFlow()

    private val _activePlaylist = MutableStateFlow<List<VideoPlaylistItem>>(emptyList())
    val activePlaylist: StateFlow<List<VideoPlaylistItem>> = _activePlaylist.asStateFlow()

    fun setActivePlaylist(list: List<VideoPlaylistItem>) {
        _activePlaylist.value = list
    }

    fun loadImportedStreams(context: Context) {
        val prefs = context.getSharedPreferences("media_player_prefs", Context.MODE_PRIVATE)
        val json = prefs.getString("imported_hls_streams", null) ?: return
        try {
            val list = Json.decodeFromString<List<HlsStreamItem>>(json)
            _importedStreams.value = list
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun addImportedStreams(context: Context, newStreams: List<HlsStreamItem>) {
        val currentList = _importedStreams.value
        val updatedList = (currentList + newStreams).distinctBy { it.url }
        _importedStreams.value = updatedList
        
        val json = Json.encodeToString(updatedList)
        val prefs = context.getSharedPreferences("media_player_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("imported_hls_streams", json).apply()
    }

    fun clearImportedStreams(context: Context) {
        _importedStreams.value = emptyList()
        val prefs = context.getSharedPreferences("media_player_prefs", Context.MODE_PRIVATE)
        prefs.edit().remove("imported_hls_streams").apply()
    }

    private val _videos = MutableStateFlow<List<VideoItem>>(emptyList())
    val videos: StateFlow<List<VideoItem>> = _videos.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _sortBy = MutableStateFlow(SortType.DATE)
    val sortBy: StateFlow<SortType> = _sortBy.asStateFlow()

    private val _sortOrder = MutableStateFlow(SortOrder.DESC)
    val sortOrder: StateFlow<SortOrder> = _sortOrder.asStateFlow()

    private val _isGridView = MutableStateFlow(false)
    val isGridView: StateFlow<Boolean> = _isGridView.asStateFlow()

    private val _selectedFolder = MutableStateFlow<String?>(null)
    val selectedFolder: StateFlow<String?> = _selectedFolder.asStateFlow()

    // --- Music features states ---
    private val _music = MutableStateFlow<List<MusicItem>>(emptyList())
    val music: StateFlow<List<MusicItem>> = _music.asStateFlow()

    private val _isMusicLoading = MutableStateFlow(false)
    val isMusicLoading: StateFlow<Boolean> = _isMusicLoading.asStateFlow()

    private val _musicSearchQuery = MutableStateFlow("")
    val musicSearchQuery: StateFlow<String> = _musicSearchQuery.asStateFlow()

    private val _nctResults = MutableStateFlow<List<NctSong>>(emptyList())
    val nctResults: StateFlow<List<NctSong>> = _nctResults.asStateFlow()

    private val _isNctSearching = MutableStateFlow(false)
    val isNctSearching: StateFlow<Boolean> = _isNctSearching.asStateFlow()

    private val _nctSearchQuery = MutableStateFlow("")
    val nctSearchQuery: StateFlow<String> = _nctSearchQuery.asStateFlow()

    private val _downloadingSongs = MutableStateFlow<Map<String, String>>(emptyMap()) // songTitle -> state ("downloading", "success", "failed")
    val downloadingSongs: StateFlow<Map<String, String>> = _downloadingSongs.asStateFlow()

    fun scanVideos(context: Context) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val scanned = VideoScanner.scanVideos(context)
                _videos.value = scanned
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSortType(type: SortType) {
        if (_sortBy.value == type) {
            _sortOrder.value = if (_sortOrder.value == SortOrder.ASC) SortOrder.DESC else SortOrder.ASC
        } else {
            _sortBy.value = type
            _sortOrder.value = if (type == SortType.FORMAT) SortOrder.ASC else SortOrder.DESC
        }
    }

    fun toggleViewMode() {
        _isGridView.value = !_isGridView.value
    }

    fun selectFolder(folderName: String?) {
        _selectedFolder.value = folderName
    }

    // --- Music Functions ---
    fun scanMusic(context: Context) {
        viewModelScope.launch {
            _isMusicLoading.value = true
            try {
                val scanned = MusicScanner.scanMusic(context)
                _music.value = scanned
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isMusicLoading.value = false
            }
        }
    }

    fun setMusicSearchQuery(query: String) {
        _musicSearchQuery.value = query
    }

    fun setNctSearchQuery(query: String) {
        _nctSearchQuery.value = query
    }

    fun searchMusic(query: String) {
        if (query.isBlank()) return
        viewModelScope.launch {
            _isNctSearching.value = true
            try {
                val results = NctScraper.searchSong(query)
                _nctResults.value = results
            } catch (e: Exception) {
                e.printStackTrace()
                _nctResults.value = emptyList()
            } finally {
                _isNctSearching.value = false
            }
        }
    }

    fun downloadMusic(context: Context, song: NctSong) {
        viewModelScope.launch(Dispatchers.IO) {
            _downloadingSongs.value = _downloadingSongs.value + (song.title to "downloading")
            try {
                var streamUrl = song.streamUrl
                if (streamUrl.isBlank()) {
                    streamUrl = NctScraper.getStreamUrl(song.pageUrl)
                }
                if (streamUrl.isBlank()) {
                    throw Exception("Unable to extract stream URL")
                }
                
                val client = HttpClientFactory.client
                val response = client.get(streamUrl)
                
                if (response.status == io.ktor.http.HttpStatusCode.OK) {
                    val dir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_MUSIC)
                    if (dir != null && !dir.exists()) {
                        dir.mkdirs()
                    }
                    
                    val cleanTitle = song.title.replace(Regex("[\\\\/:*?\"<>|]"), "_")
                    val cleanArtist = song.artist.replace(Regex("[\\\\/:*?\"<>|]"), "_")
                    val file = java.io.File(dir, "$cleanTitle - $cleanArtist.mp3")
                    
                    response.bodyAsChannel().toInputStream().use { input ->
                        file.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    
                    android.media.MediaScannerConnection.scanFile(
                        context.applicationContext,
                        arrayOf(file.absolutePath),
                        arrayOf("audio/mpeg"),
                        null
                    )
                    
                    withContext(Dispatchers.Main) {
                        _downloadingSongs.value = _downloadingSongs.value + (song.title to "success")
                        scanMusic(context)
                        android.widget.Toast.makeText(context, "Đã tải: ${song.title}", android.widget.Toast.LENGTH_SHORT).show()
                    }
                } else {
                    throw Exception("HTTP code ${response.status.value}")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    _downloadingSongs.value = _downloadingSongs.value + (song.title to "failed")
                    android.widget.Toast.makeText(context, "Tải lỗi: ${song.title}", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Returns filtered and sorted videos (accent-insensitive search)
    fun getFilteredVideos(): List<VideoItem> {
        var list = _videos.value

        val folder = _selectedFolder.value
        if (folder != null) {
            list = list.filter { it.folderName == folder }
        }

        val query = _searchQuery.value.trim()
        if (query.isNotEmpty()) {
            list = list.filter {
                it.title.matchesFuzzy(query) ||
                it.folderName.matchesFuzzy(query) ||
                it.extension.matchesFuzzy(query)
            }
        }

        val order = _sortOrder.value
        list = when (_sortBy.value) {
            SortType.DATE -> {
                if (order == SortOrder.ASC) list.sortedBy { it.dateAdded }
                else list.sortedByDescending { it.dateAdded }
            }
            SortType.SIZE -> {
                if (order == SortOrder.ASC) list.sortedBy { it.size }
                else list.sortedByDescending { it.size }
            }
            SortType.FORMAT -> {
                if (order == SortOrder.ASC) list.sortedBy { it.extension }
                else list.sortedByDescending { it.extension }
            }
        }

        return list
    }

    // Returns filtered local music list (accent-insensitive search)
    fun getFilteredMusic(): List<MusicItem> {
        var list = _music.value
        val query = _musicSearchQuery.value.trim()
        if (query.isNotEmpty()) {
            list = list.filter {
                it.title.matchesFuzzy(query) ||
                it.artist.matchesFuzzy(query)
            }
        }
        return list
    }

    fun getFolders(): Map<String, Int> {
        val folders = mutableMapOf<String, Int>()
        for (video in _videos.value) {
            folders[video.folderName] = folders.getOrDefault(video.folderName, 0) + 1
        }
        return folders
    }
}

