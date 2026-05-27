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

@Serializable
data class PhimApiPagination(
    val totalItems: Int = 0,
    val totalItemsPerPage: Int = 10,
    val currentPage: Int = 1,
    val totalPages: Int = 1
)

@Serializable
data class PhimApiParam(
    val sortField: String = "",
    val sortType: String = "",
    val pagination: PhimApiPagination = PhimApiPagination()
)

@Serializable
data class PhimApiCategory(
    val id: String = "",
    val name: String = "",
    val slug: String = ""
)

@Serializable
data class PhimApiCountry(
    val id: String = "",
    val name: String = "",
    val slug: String = ""
)

@Serializable
data class PhimApiMovieItem(
    val _id: String,
    val name: String,
    val slug: String,
    val origin_name: String,
    val poster_url: String = "",
    val thumb_url: String = "",
    val year: Int = 0,
    val lang: String = "",
    val quality: String = "",
    val episode_current: String = "",
    val category: List<PhimApiCategory> = emptyList(),
    val country: List<PhimApiCountry> = emptyList()
)

@Serializable
data class PhimApiData(
    val items: List<PhimApiMovieItem> = emptyList(),
    val params: PhimApiParam = PhimApiParam(),
    val APP_DOMAIN_CDN_IMAGE: String = "https://phimimg.com"
)

@Serializable
data class PhimApiSearchResponse(
    val status: String = "",
    val msg: String = "",
    val data: PhimApiData = PhimApiData()
)

@Serializable
data class PhimNewMovieItem(
    val _id: String,
    val name: String,
    val slug: String,
    val origin_name: String,
    val poster_url: String = "",
    val thumb_url: String = "",
    val year: Int = 0
)

@Serializable
data class PhimNewResponse(
    val status: Boolean = false,
    val msg: String = "",
    val items: List<PhimNewMovieItem> = emptyList(),
    val pagination: PhimApiPagination? = null
)

@Serializable
data class PhimApiDetailMovie(
    val _id: String,
    val name: String,
    val slug: String,
    val origin_name: String,
    val content: String? = "",
    val poster_url: String? = "",
    val thumb_url: String? = "",
    val year: Int? = 0,
    val lang: String? = "",
    val quality: String? = "",
    val episode_current: String? = "",
    val episode_total: String? = "",
    val actor: List<String>? = emptyList(),
    val director: List<String>? = emptyList(),
    val category: List<PhimApiCategory>? = emptyList(),
    val country: List<PhimApiCountry>? = emptyList()
)

@Serializable
data class PhimApiEpisodeData(
    val name: String,
    val slug: String,
    val filename: String = "",
    val link_embed: String = "",
    val link_m3u8: String = ""
)

@Serializable
data class PhimApiEpisodeServer(
    val server_name: String = "",
    val server_data: List<PhimApiEpisodeData> = emptyList()
)

@Serializable
data class PhimApiDetailResponse(
    val status: Boolean = false,
    val msg: String = "",
    val movie: PhimApiDetailMovie? = null,
    val episodes: List<PhimApiEpisodeServer> = emptyList()
)

data class KkMovie(
    val id: String,
    val name: String,
    val slug: String,
    val originName: String,
    val posterUrl: String,
    val thumbUrl: String,
    val year: Int,
    val lang: String = "",
    val quality: String = "",
    val episodeCurrent: String = ""
)

enum class SortType { DATE, SIZE, FORMAT }
enum class SortOrder { ASC, DESC }

class MainScreenViewModel : ViewModel() {
    private val apiJson = Json { ignoreUnknownKeys = true }

    private val _importedStreams = MutableStateFlow<List<HlsStreamItem>>(emptyList())
    val importedStreams: StateFlow<List<HlsStreamItem>> = _importedStreams.asStateFlow()

    private val _activePlaylist = MutableStateFlow<List<VideoPlaylistItem>>(emptyList())
    val activePlaylist: StateFlow<List<VideoPlaylistItem>> = _activePlaylist.asStateFlow()

    fun setActivePlaylist(list: List<VideoPlaylistItem>) {
        _activePlaylist.value = list
    }

    // --- PhimAPI (kkphim) states ---
    private val _kkMovies = MutableStateFlow<List<KkMovie>>(emptyList())
    val kkMovies: StateFlow<List<KkMovie>> = _kkMovies.asStateFlow()

    private val _kkIsSearching = MutableStateFlow(false)
    val kkIsSearching: StateFlow<Boolean> = _kkIsSearching.asStateFlow()

    private val _kkCurrentPage = MutableStateFlow(1)
    val kkCurrentPage: StateFlow<Int> = _kkCurrentPage.asStateFlow()

    private val _kkTotalPages = MutableStateFlow(1)
    val kkTotalPages: StateFlow<Int> = _kkTotalPages.asStateFlow()

    private val _kkTotalItems = MutableStateFlow(0)
    val kkTotalItems: StateFlow<Int> = _kkTotalItems.asStateFlow()

    private val _kkSelectedMovieDetail = MutableStateFlow<PhimApiDetailResponse?>(null)
    val kkSelectedMovieDetail: StateFlow<PhimApiDetailResponse?> = _kkSelectedMovieDetail.asStateFlow()

    private val _kkIsLoadingDetail = MutableStateFlow(false)
    val kkIsLoadingDetail: StateFlow<Boolean> = _kkIsLoadingDetail.asStateFlow()

    private val _kkMovieSearchQuery = MutableStateFlow("")
    val kkMovieSearchQuery: StateFlow<String> = _kkMovieSearchQuery.asStateFlow()

    // Advanced filters
    val kkSortField = MutableStateFlow("modified.time") // modified.time, _id, year
    val kkSortType = MutableStateFlow("desc") // desc, asc
    val kkSortLang = MutableStateFlow("") // "", vietsub, thuyet-minh, long-tieng
    val kkCategory = MutableStateFlow("")
    val kkCountry = MutableStateFlow("")
    val kkYear = MutableStateFlow("")
    val kkLimit = MutableStateFlow(20)

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

    fun setKkMovieSearchQuery(query: String) {
        _kkMovieSearchQuery.value = query
    }

    fun clearKkSelectedMovieDetail() {
        _kkSelectedMovieDetail.value = null
    }

    fun fetchLatestKkMovies(page: Int = 1) {
        viewModelScope.launch {
            _kkIsSearching.value = true
            _kkCurrentPage.value = page
            try {
                val client = HttpClientFactory.client
                val response = client.get("https://phimapi.com/danh-sach/phim-moi-cap-nhat?page=$page")
                if (response.status == io.ktor.http.HttpStatusCode.OK) {
                    val responseText = response.bodyAsText()
                    val parsed = apiJson.decodeFromString<PhimNewResponse>(responseText)
                    if (parsed.status) {
                        _kkMovies.value = parsed.items.map { item ->
                            KkMovie(
                                id = item._id,
                                name = item.name,
                                slug = item.slug,
                                originName = item.origin_name,
                                posterUrl = item.poster_url,
                                thumbUrl = item.thumb_url,
                                year = item.year,
                                lang = "Full",
                                quality = "HD",
                                episodeCurrent = "Full"
                            )
                        }
                        parsed.pagination?.let { pag ->
                            _kkTotalPages.value = pag.totalPages
                            _kkTotalItems.value = pag.totalItems
                        }
                    } else {
                        _kkMovies.value = emptyList()
                    }
                } else {
                    _kkMovies.value = emptyList()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _kkMovies.value = emptyList()
            } finally {
                _kkIsSearching.value = false
            }
        }
    }

    fun searchKkMovies(page: Int = 1) {
        val query = _kkMovieSearchQuery.value.trim()
        if (query.isEmpty()) {
            fetchLatestKkMovies(page)
            return
        }
        viewModelScope.launch {
            _kkIsSearching.value = true
            _kkCurrentPage.value = page
            try {
                val client = HttpClientFactory.client
                val urlBuilder = java.lang.StringBuilder("https://phimapi.com/v1/api/tim-kiem")
                urlBuilder.append("?keyword=").append(java.net.URLEncoder.encode(query, "UTF-8"))
                urlBuilder.append("&page=").append(page)
                urlBuilder.append("&limit=").append(kkLimit.value)
                
                if (kkSortField.value.isNotEmpty()) {
                    urlBuilder.append("&sort_field=").append(kkSortField.value)
                }
                if (kkSortType.value.isNotEmpty()) {
                    urlBuilder.append("&sort_type=").append(kkSortType.value)
                }
                if (kkSortLang.value.isNotEmpty()) {
                    urlBuilder.append("&sort_lang=").append(kkSortLang.value)
                }
                if (kkCategory.value.isNotEmpty()) {
                    urlBuilder.append("&category=").append(kkCategory.value)
                }
                if (kkCountry.value.isNotEmpty()) {
                    urlBuilder.append("&country=").append(kkCountry.value)
                }
                if (kkYear.value.isNotEmpty()) {
                    urlBuilder.append("&year=").append(kkYear.value)
                }
                
                val response = client.get(urlBuilder.toString())
                if (response.status == io.ktor.http.HttpStatusCode.OK) {
                    val responseText = response.bodyAsText()
                    val parsed = apiJson.decodeFromString<PhimApiSearchResponse>(responseText)
                    if (parsed.status == "success") {
                        val cdn = parsed.data.APP_DOMAIN_CDN_IMAGE
                        _kkMovies.value = parsed.data.items.map { item ->
                            val absolutePosterUrl = if (item.poster_url.startsWith("http")) item.poster_url else "$cdn/${item.poster_url}"
                            val absoluteThumbUrl = if (item.thumb_url.startsWith("http")) item.thumb_url else "$cdn/${item.thumb_url}"
                            KkMovie(
                                id = item._id,
                                name = item.name,
                                slug = item.slug,
                                originName = item.origin_name,
                                posterUrl = absolutePosterUrl,
                                thumbUrl = absoluteThumbUrl,
                                year = item.year,
                                lang = item.lang,
                                quality = item.quality,
                                episodeCurrent = item.episode_current
                            )
                        }
                        _kkTotalPages.value = parsed.data.params.pagination.totalPages
                        _kkTotalItems.value = parsed.data.params.pagination.totalItems
                    } else {
                        _kkMovies.value = emptyList()
                    }
                } else {
                    _kkMovies.value = emptyList()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _kkMovies.value = emptyList()
            } finally {
                _kkIsSearching.value = false
            }
        }
    }

    fun fetchKkMovieDetail(slug: String, onComplete: (PhimApiDetailResponse?) -> Unit = {}) {
        viewModelScope.launch {
            _kkIsLoadingDetail.value = true
            try {
                val client = HttpClientFactory.client
                val response = client.get("https://phimapi.com/phim/$slug")
                if (response.status == io.ktor.http.HttpStatusCode.OK) {
                    val responseText = response.bodyAsText()
                    val parsed = apiJson.decodeFromString<PhimApiDetailResponse>(responseText)
                    if (parsed.status) {
                        _kkSelectedMovieDetail.value = parsed
                        onComplete(parsed)
                    } else {
                        onComplete(null)
                    }
                } else {
                    onComplete(null)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                onComplete(null)
            } finally {
                _kkIsLoadingDetail.value = false
            }
        }
    }
}

