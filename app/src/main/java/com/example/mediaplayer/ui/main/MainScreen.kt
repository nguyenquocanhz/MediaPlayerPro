package com.example.mediaplayer.ui.main

import android.Manifest
import android.content.Context
import android.os.Build
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.ui.viewinterop.AndroidView
import com.example.mediaplayer.data.NctScraper
import com.example.mediaplayer.ui.player.PlayerMusicView
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import com.example.mediaplayer.Player
import com.example.mediaplayer.Settings
import com.example.mediaplayer.data.MusicItem
import com.example.mediaplayer.data.MusicPlayerManager
import com.example.mediaplayer.data.NctSong
import com.example.mediaplayer.data.VideoItem
import com.example.mediaplayer.data.VideoScanner
import com.example.mediaplayer.data.AdultScrapers
import com.example.mediaplayer.theme.Loc
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onNavigate: (NavKey) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MainScreenViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // Video-frame decoder config for Coil
    val imageLoader = remember {
        ImageLoader.Builder(context)
            .components {
                add(VideoFrameDecoder.Factory())
            }
            .build()
    }

    // Permission handling states
    var hasVideoPermission by remember { mutableStateOf(false) }
    var hasAudioPermission by remember { mutableStateOf(false) }
    var hasNotificationPermission by remember { mutableStateOf(false) }

    val videoPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_VIDEO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    val audioPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    val videoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasVideoPermission = isGranted
        if (isGranted) {
            viewModel.scanVideos(context)
        } else {
            Toast.makeText(context, "Cần quyền đọc video để hiển thị danh sách!", Toast.LENGTH_SHORT).show()
        }
    }

    val audioLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasAudioPermission = isGranted
        if (isGranted) {
            viewModel.scanMusic(context)
        } else {
            Toast.makeText(context, "Cần quyền đọc âm thanh để hiển thị nhạc!", Toast.LENGTH_SHORT).show()
        }
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasNotificationPermission = isGranted
    }

    LaunchedEffect(Unit) {
        viewModel.loadImportedStreams(context)
        val isVideoGranted = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            videoPermission
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        hasVideoPermission = isVideoGranted
        if (isVideoGranted) {
            viewModel.scanVideos(context)
        } else {
            videoLauncher.launch(videoPermission)
        }


        // Notification permission check for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val isNotiGranted = androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            hasNotificationPermission = isNotiGranted
            if (!isNotiGranted) {
                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            hasNotificationPermission = true
        }
    }

    // Active bottom navigation tab (0: Videos, 1: Music, 2: YouTube)
    var activeBottomTab by remember { mutableIntStateOf(0) }
    
    // Switch to Music Tab triggers audio permission check
    LaunchedEffect(activeBottomTab) {
        if (activeBottomTab == 1) {
            val isGranted = androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                audioPermission
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            hasAudioPermission = isGranted
            if (isGranted) {
                viewModel.scanMusic(context)
            } else {
                audioLauncher.launch(audioPermission)
            }
        }
    }

    // States for Video view
    val isVideoLoading by viewModel.isLoading.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val sortBy by viewModel.sortBy.collectAsState()
    val sortOrder by viewModel.sortOrder.collectAsState()
    val isGridView by viewModel.isGridView.collectAsState()
    val selectedFolder by viewModel.selectedFolder.collectAsState()
    var videoFolderTab by remember { mutableIntStateOf(0) } // 0: Video List, 1: Folders
    var showHlsDialog by remember { mutableStateOf(false) }
    var hlsUrl by remember { mutableStateOf("") }
    var hlsTitle by remember { mutableStateOf("") }
    var videoToRename by remember { mutableStateOf<VideoItem?>(null) }
    var videoToDelete by remember { mutableStateOf<VideoItem?>(null) }
    var renameNewName by remember { mutableStateOf("") }

    // States for Music view
    val localMusicList by viewModel.music.collectAsState()
    val isMusicLoading by viewModel.isMusicLoading.collectAsState()
    val musicSearchQuery by viewModel.musicSearchQuery.collectAsState()
    val filteredMusicList = remember(localMusicList, musicSearchQuery) {
        viewModel.getFilteredMusic()
    }
    val nctSearchQuery by viewModel.nctSearchQuery.collectAsState()
    val nctResults by viewModel.nctResults.collectAsState()
    val isNctSearching by viewModel.isNctSearching.collectAsState()
    val downloadingSongs by viewModel.downloadingSongs.collectAsState()
    var musicSectionTab by remember { mutableIntStateOf(0) } // 0: Local tracks, 1: Online NCT
    var isResolvingOnlineUrl by remember { mutableStateOf(false) }
    var isPlayerMusicViewVisible by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Premium Player",
                        fontWeight = FontWeight.Black,
                        fontSize = 20.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                actions = {
                    if (activeBottomTab == 0) {
                        IconButton(onClick = { showHlsDialog = true }) {
                            Icon(Icons.Default.Link, contentDescription = Loc.hlsStream, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    // Settings button
                    IconButton(onClick = { onNavigate(Settings) }) {
                        Icon(Icons.Default.Settings, contentDescription = Loc.settings)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = activeBottomTab == 0,
                    onClick = { activeBottomTab = 0 },
                    icon = { Icon(Icons.Default.Movie, contentDescription = null) },
                    label = { Text(Loc.videosBottomTab) }
                )
                NavigationBarItem(
                    selected = activeBottomTab == 1,
                    onClick = { activeBottomTab = 1 },
                    icon = { Icon(Icons.Default.MusicNote, contentDescription = null) },
                    label = { Text(Loc.musicBottomTab) }
                )
                NavigationBarItem(
                    selected = activeBottomTab == 2,
                    onClick = { activeBottomTab = 2 },
                    icon = { Icon(Icons.Default.SmartDisplay, contentDescription = null) },
                    label = { Text("AV JAV") }
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Tab 0: Videos
            if (activeBottomTab == 0) {
                if (!hasVideoPermission) {
                    PermissionDeniedView(
                        message = "Để hiển thị các video trên thiết bị của bạn, vui lòng cấp quyền truy cập bộ nhớ.",
                        onRequestPermission = { videoLauncher.launch(videoPermission) }
                    )
                } else {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Video search field
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { viewModel.setSearchQuery(it) },
                            placeholder = { Text(Loc.searchPlaceholder) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                        Icon(Icons.Default.Close, contentDescription = null)
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            shape = RoundedCornerShape(12.dp)
                        )

                        ScrollableTabRow(
                            selectedTabIndex = videoFolderTab,
                            edgePadding = 0.dp
                        ) {
                            Tab(
                                selected = videoFolderTab == 0,
                                onClick = { videoFolderTab = 0 },
                                text = { Text(Loc.videosTab, fontWeight = FontWeight.Bold) }
                            )
                            Tab(
                                selected = videoFolderTab == 1,
                                onClick = {
                                    videoFolderTab = 1
                                    viewModel.selectFolder(null)
                                },
                                text = { Text(Loc.foldersTab, fontWeight = FontWeight.Bold) }
                            )
                            Tab(
                                selected = videoFolderTab == 2,
                                onClick = { videoFolderTab = 2 },
                                text = { Text(Loc.recentVideosSubTab, fontWeight = FontWeight.Bold) }
                            )
                            Tab(
                                selected = videoFolderTab == 3,
                                onClick = { videoFolderTab = 3 },
                                text = { Text(Loc.onlineMoviesTab, fontWeight = FontWeight.Bold) }
                            )
                        }

                        if (videoFolderTab == 3) {
                            OnlineMoviesSection(
                                viewModel = viewModel,
                                context = context,
                                onNavigate = onNavigate,
                                scope = scope
                            )
                        } else if (videoFolderTab == 2) {
                            RecentVideosSection(
                                viewModel = viewModel,
                                context = context,
                                onNavigate = onNavigate,
                                scope = scope
                            )
                        } else {
                            if (videoFolderTab == 0 || selectedFolder != null) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (selectedFolder != null) {
                                        InputChip(
                                            selected = true,
                                            onClick = { viewModel.selectFolder(null) },
                                            label = { Text("Thư mục: $selectedFolder") },
                                            trailingIcon = { Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                        )
                                    } else {
                                        Spacer(modifier = Modifier.width(8.dp))
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        TextButton(
                                            onClick = {
                                                val nextType = when (sortBy) {
                                                    SortType.DATE -> SortType.SIZE
                                                    SortType.SIZE -> SortType.FORMAT
                                                    SortType.FORMAT -> SortType.DATE
                                                }
                                                viewModel.setSortType(nextType)
                                            }
                                        ) {
                                            val sortText = when (sortBy) {
                                                SortType.DATE -> Loc.dateAdded
                                                SortType.SIZE -> Loc.size
                                                SortType.FORMAT -> Loc.format
                                            }
                                            val icon = if (sortOrder == SortOrder.ASC) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward
                                            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(sortText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }

                                        IconButton(onClick = { viewModel.toggleViewMode() }) {
                                            Icon(
                                                imageVector = if (isGridView) Icons.Default.List else Icons.Default.GridView,
                                                contentDescription = null
                                            )
                                        }
                                    }
                                }
                            }

                            // Video List View with PullToRefresh
                            PullToRefreshBox(
                                isRefreshing = isVideoLoading,
                                onRefresh = { viewModel.scanVideos(context) },
                                modifier = Modifier.fillMaxSize()
                            ) {
                                if (videoFolderTab == 0 || selectedFolder != null) {
                                    val filteredVideos = viewModel.getFilteredVideos()
                                    if (filteredVideos.isEmpty()) {
                                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            Text("Không tìm thấy video nào.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    } else {
                                        if (isGridView) {
                                            LazyVerticalGrid(
                                                columns = GridCells.Adaptive(minSize = 150.dp),
                                                contentPadding = PaddingValues(8.dp),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                                modifier = Modifier.fillMaxSize()
                                            ) {
                                                items(filteredVideos, key = { it.id }) { video ->
                                                    VideoGridCard(
                                                        video = video,
                                                        imageLoader = imageLoader,
                                                        onClick = {
                                                            viewModel.setActivePlaylist(filteredVideos.map { VideoPlaylistItem(it.title, it.path, isOnline = false) })
                                                            viewModel.addRecentVideo(context, video.title, video.path, false)
                                                            onNavigate(Player(videoPath = video.path, videoTitle = video.title, isOnline = false))
                                                        },
                                                        onRenameClick = {
                                                            videoToRename = video
                                                            renameNewName = java.io.File(video.path).nameWithoutExtension
                                                        },
                                                        onDeleteClick = {
                                                            videoToDelete = video
                                                        }
                                                    )
                                                }
                                            }
                                        } else {
                                            LazyColumn(
                                                contentPadding = PaddingValues(12.dp),
                                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                                modifier = Modifier.fillMaxSize()
                                            ) {
                                                items(filteredVideos, key = { it.id }) { video ->
                                                     VideoListCard(
                                                         video = video,
                                                         imageLoader = imageLoader,
                                                         onClick = {
                                                             viewModel.setActivePlaylist(filteredVideos.map { VideoPlaylistItem(it.title, it.path, isOnline = false) })
                                                             viewModel.addRecentVideo(context, video.title, video.path, false)
                                                             onNavigate(Player(videoPath = video.path, videoTitle = video.title, isOnline = false))
                                                         },
                                                         onRenameClick = {
                                                             videoToRename = video
                                                             renameNewName = java.io.File(video.path).nameWithoutExtension
                                                         },
                                                         onDeleteClick = {
                                                             videoToDelete = video
                                                         }
                                                     )
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    val folders = viewModel.getFolders()
                                    if (folders.isEmpty()) {
                                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            Text("Không tìm thấy thư mục chứa video.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    } else {
                                        LazyColumn(
                                            contentPadding = PaddingValues(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.fillMaxSize()
                                        ) {
                                            items(folders.keys.toList()) { folderName ->
                                                FolderCard(
                                                    folderName = folderName,
                                                    videoCount = folders[folderName] ?: 0,
                                                    onClick = {
                                                        viewModel.selectFolder(folderName)
                                                        videoFolderTab = 0
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Tab 1: Music (Local Scanner & NhacCuaTui search & download)
            if (activeBottomTab == 1) {
                if (!hasAudioPermission) {
                    PermissionDeniedView(
                        message = "Để hiển thị và phát các bài hát trên thiết bị, vui lòng cấp quyền truy cập bộ nhớ.",
                        onRequestPermission = { audioLauncher.launch(audioPermission) }
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = if (MusicPlayerManager.currentSong.collectAsState().value != null) 96.dp else 0.dp)
                    ) {
                        ScrollableTabRow(
                            selectedTabIndex = musicSectionTab,
                            edgePadding = 0.dp
                        ) {
                            Tab(
                                selected = musicSectionTab == 0,
                                onClick = { musicSectionTab = 0 },
                                text = { Text(Loc.localMusicHeader, fontWeight = FontWeight.Bold) }
                            )
                            Tab(
                                selected = musicSectionTab == 1,
                                onClick = { musicSectionTab = 1 },
                                text = { Text(Loc.nctSearchHeader, fontWeight = FontWeight.Bold) }
                            )
                        }

                        if (musicSectionTab == 0) {
                            // Local Music search field
                            OutlinedTextField(
                                value = musicSearchQuery,
                                onValueChange = { viewModel.setMusicSearchQuery(it) },
                                placeholder = { Text(Loc.searchPlaceholder) },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                trailingIcon = {
                                    if (musicSearchQuery.isNotEmpty()) {
                                        IconButton(onClick = { viewModel.setMusicSearchQuery("") }) {
                                            Icon(Icons.Default.Close, contentDescription = null)
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                shape = RoundedCornerShape(12.dp)
                            )

                            // Local Music scan with PullToRefresh
                            PullToRefreshBox(
                                isRefreshing = isMusicLoading,
                                onRefresh = { viewModel.scanMusic(context) },
                                modifier = Modifier.fillMaxSize()
                            ) {
                                if (filteredMusicList.isEmpty()) {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text(
                                            text = if (musicSearchQuery.isNotEmpty()) "Không tìm thấy bài hát nào." else "Không có bài hát nào trên máy.",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                } else {
                                    LazyColumn(
                                        contentPadding = PaddingValues(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        itemsIndexed(filteredMusicList) { index, song ->
                                            MusicListCard(
                                                song = song,
                                                onClick = {
                                                    MusicPlayerManager.playPlaylist(filteredMusicList, index)
                                                },
                                                onDownloadClick = null,
                                                downloadState = null
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            // Online NCT Music Search
                            Column(modifier = Modifier.fillMaxSize()) {
                                OutlinedTextField(
                                    value = nctSearchQuery,
                                    onValueChange = { viewModel.setNctSearchQuery(it) },
                                    placeholder = { Text(Loc.searchMusicPlaceholder) },
                                    leadingIcon = { Icon(Icons.Default.MusicNote, contentDescription = null) },
                                    trailingIcon = {
                                        if (nctSearchQuery.isNotEmpty()) {
                                            IconButton(onClick = { viewModel.setNctSearchQuery("") }) {
                                                Icon(Icons.Default.Close, contentDescription = null)
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    shape = RoundedCornerShape(12.dp)
                                )

                                Button(
                                    onClick = { viewModel.searchMusic(nctSearchQuery) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.Search, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Tìm kiếm trực tuyến")
                                }

                                PullToRefreshBox(
                                    isRefreshing = isNctSearching,
                                    onRefresh = { viewModel.searchMusic(nctSearchQuery) },
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(top = 10.dp)
                                ) {
                                    if (nctResults.isEmpty()) {
                                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            Text("Nhập từ khóa để tìm nhạc trực tuyến.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    } else {
                                        LazyColumn(
                                            contentPadding = PaddingValues(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.fillMaxSize()
                                        ) {
                                            items(nctResults) { song ->
                                                val dlState = downloadingSongs[song.title]
                                                MusicListCard(
                                                    song = MusicItem(
                                                        title = song.title,
                                                        artist = song.artist,
                                                        path = song.pageUrl,
                                                        isOnline = true,
                                                        thumbnailUrl = song.thumbnailUrl
                                                    ),
                                                    onClick = {
                                                        scope.launch {
                                                            isResolvingOnlineUrl = true
                                                            val streamUrl = NctScraper.getStreamUrl(song.pageUrl)
                                                            isResolvingOnlineUrl = false
                                                            if (streamUrl.isNotBlank()) {
                                                                MusicPlayerManager.play(
                                                                    MusicItem(
                                                                        title = song.title,
                                                                        artist = song.artist,
                                                                        path = streamUrl,
                                                                        isOnline = true,
                                                                        thumbnailUrl = song.thumbnailUrl
                                                                    )
                                                                )
                                                            } else {
                                                                Toast.makeText(context, "Không thể lấy link phát nhạc!", Toast.LENGTH_SHORT).show()
                                                            }
                                                        }
                                                    },
                                                    onDownloadClick = {
                                                        viewModel.downloadMusic(context, song)
                                                    },
                                                    downloadState = dlState
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Tab 2: AV JAV (AVDB API)
            if (activeBottomTab == 2) {
                var isResolvingUrl by remember { mutableStateOf(false) }
                val avdbMovies by viewModel.avdbMovies.collectAsState()
                val avdbIsSearching by viewModel.avdbIsSearching.collectAsState()
                val avdbCurrentPage by viewModel.avdbCurrentPage.collectAsState()
                val avdbTotalPages by viewModel.avdbTotalPages.collectAsState()
                val avdbTotalItems by viewModel.avdbTotalItems.collectAsState()
                val avdbSearchQuery by viewModel.avdbSearchQuery.collectAsState()
                val avdbSelectedMovieDetail by viewModel.avdbSelectedMovieDetail.collectAsState()
                val avdbIsLoadingDetail by viewModel.avdbIsLoadingDetail.collectAsState()

                LaunchedEffect(Unit) {
                    if (avdbMovies.isEmpty()) {
                        viewModel.fetchAvdbMovies(1)
                    }
                }

                AvdbMoviesSection(
                    viewModel = viewModel,
                    movies = avdbMovies,
                    isSearching = avdbIsSearching,
                    currentPage = avdbCurrentPage,
                    totalPages = avdbTotalPages,
                    totalItems = avdbTotalItems,
                    searchQuery = avdbSearchQuery,
                    selectedMovieDetail = avdbSelectedMovieDetail,
                    isLoadingDetail = avdbIsLoadingDetail,
                    context = context,
                    onNavigate = onNavigate,
                    scope = scope,
                    onPlayClick = { embedUrl, title ->
                        scope.launch {
                            isResolvingUrl = true
                            val streamUrl = AdultScrapers.getAvdbStreamUrl(embedUrl)
                            isResolvingUrl = false
                            if (streamUrl.isNotBlank()) {
                                viewModel.addRecentVideo(context, title, streamUrl, true)
                                onNavigate(com.example.mediaplayer.Player(videoPath = streamUrl, videoTitle = title, isOnline = true))
                            } else {
                                Toast.makeText(context, "Không thể lấy link phát video!", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )

                if (isResolvingUrl) {
                    AdultUrlResolvingOverlay()
                }
            }

            // --- Active HLS Recordings Progress Banner ---
            val hlsRecordState by viewModel.hlsRecordState.collectAsState()
            if (hlsRecordState.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(horizontal = 16.dp, vertical = 80.dp)
                        .fillMaxWidth()
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f)),
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(6.dp),
                        modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("🔴 Đang ghi luồng stream ngầm...", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                            hlsRecordState.forEach { (url, state) ->
                                val (progress, statusText) = state
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        val displayTitle = url.substringBefore("?").substringAfterLast("/")
                                        Text(
                                            text = if (displayTitle.length > 30) displayTitle.take(30) + "..." else displayTitle,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(statusText, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    CircularProgressIndicator(
                                        progress = progress,
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.5.dp,
                                        color = MaterialTheme.colorScheme.primary,
                                        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    IconButton(
                                        onClick = { viewModel.removeHlsRecording(url) },
                                        modifier = Modifier.size(20.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Hủy bỏ / Đóng",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // --- Sticky Music Player Overlay ---
            val currentPlayingSong by MusicPlayerManager.currentSong.collectAsState()
            AnimatedVisibility(
                visible = currentPlayingSong != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                currentPlayingSong?.let { song ->
                    MusicPlayerOverlay(
                        song = song,
                        onClick = { isPlayerMusicViewVisible = true }
                    )
                }
            }

            // Fullscreen Music Player View Overlay
            AnimatedVisibility(
                visible = isPlayerMusicViewVisible,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it }),
                modifier = Modifier.fillMaxSize()
            ) {
                PlayerMusicView(
                    onDismiss = { isPlayerMusicViewVisible = false }
                )
            }

            // Resolving indicator overlay
            if (isResolvingOnlineUrl) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .clickable(enabled = false) {},
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Đang tải nguồn phát trực tuyến...", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // Play online HLS Dialog
    if (showHlsDialog) {
        AlertDialog(
            onDismissRequest = { showHlsDialog = false },
            title = { Text(Loc.hlsStream) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = hlsTitle,
                        onValueChange = { hlsTitle = it },
                        label = { Text("Tên video / luồng") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = hlsUrl,
                        onValueChange = {
                            hlsUrl = it
                            val detected = detectMovieName(it)
                            if (detected.isNotEmpty()) {
                                hlsTitle = detected
                            }
                        },
                        label = { Text(Loc.hlsUrlPlaceholder) },
                        placeholder = { Text("https://example.com/stream.m3u8") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            if (hlsUrl.trim().isNotEmpty()) {
                                val title = if (hlsTitle.trim().isEmpty()) "HLS Stream" else hlsTitle
                                showHlsDialog = false
                                viewModel.startHlsRecording(context, hlsUrl.trim(), title)
                            } else {
                                Toast.makeText(context, "Vui lòng nhập đường dẫn URL!", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Text("Ghi luồng (Record)", fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = {
                            if (hlsUrl.trim().isNotEmpty()) {
                                val title = if (hlsTitle.trim().isEmpty()) "HLS Stream" else hlsTitle
                                showHlsDialog = false
                                viewModel.addRecentVideo(context, title, hlsUrl.trim(), true)
                                onNavigate(Player(videoPath = hlsUrl.trim(), videoTitle = title, isOnline = true))
                            } else {
                                Toast.makeText(context, "Vui lòng nhập đường dẫn URL!", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Text(Loc.playNow)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showHlsDialog = false }) {
                    Text(Loc.cancel)
                }
            }
        )
    }

    if (videoToRename != null) {
        AlertDialog(
            onDismissRequest = { videoToRename = null },
            title = { Text("Đổi tên video") },
            text = {
                OutlinedTextField(
                    value = renameNewName,
                    onValueChange = { renameNewName = it },
                    label = { Text("Tên video mới") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val currentVideo = videoToRename
                        if (currentVideo != null && renameNewName.trim().isNotEmpty()) {
                            viewModel.renameVideo(context, currentVideo, renameNewName.trim())
                            videoToRename = null
                        }
                    }
                ) {
                    Text("Đồng ý")
                }
            },
            dismissButton = {
                TextButton(onClick = { videoToRename = null }) {
                    Text(Loc.cancel)
                }
            }
        )
    }

    if (videoToDelete != null) {
        AlertDialog(
            onDismissRequest = { videoToDelete = null },
            title = { Text("Xóa video") },
            text = {
                Text("Bạn có chắc chắn muốn xóa video '${videoToDelete?.title}' vĩnh viễn? Hành động này không thể hoàn tác.")
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        val currentVideo = videoToDelete
                        if (currentVideo != null) {
                            viewModel.deleteVideo(context, currentVideo)
                            videoToDelete = null
                        }
                    }
                ) {
                    Text("Xóa", color = MaterialTheme.colorScheme.onError)
                }
            },
            dismissButton = {
                TextButton(onClick = { videoToDelete = null }) {
                    Text(Loc.cancel)
                }
            }
        )
    }
}

@Composable
fun PermissionDeniedView(
    message: String,
    onRequestPermission: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(24.dp)
        ) {
            Icon(
                imageVector = Icons.Default.FolderDelete,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.error
            )
            Text(
                text = "Chưa cấp quyền truy cập",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
            Text(
                text = message,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Button(onClick = onRequestPermission) {
                Text("Cấp quyền truy cập")
            }
        }
    }
}

@Composable
fun MusicListCard(
    song: MusicItem,
    onClick: () -> Unit,
    onDownloadClick: (() -> Unit)?,
    downloadState: String?
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (song.isOnline) Icons.Default.CloudQueue else Icons.Default.LibraryMusic,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = song.artist,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            if (song.isOnline && onDownloadClick != null) {
                Spacer(modifier = Modifier.width(8.dp))
                when (downloadState) {
                    "downloading" -> {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                    "success" -> {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.Green, modifier = Modifier.size(26.dp))
                    }
                    "failed" -> {
                        IconButton(onClick = onDownloadClick) {
                            Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = Color.Red)
                        }
                    }
                    else -> {
                        IconButton(onClick = onDownloadClick) {
                            Icon(Icons.Default.FileDownload, contentDescription = Loc.download, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}
@Composable
fun MusicPlayerOverlay(song: MusicItem, onClick: () -> Unit) {
    val isPlaying by MusicPlayerManager.isPlaying.collectAsState()
    val position by MusicPlayerManager.position.collectAsState()
    val duration by MusicPlayerManager.duration.collectAsState()
    
    // rotating CD animation
    val infiniteTransition = rememberInfiniteTransition(label = "cd")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "cd_rotate"
    )
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xEB1A1A2E) // sleeker dark theme overlay
        ),
        elevation = CardDefaults.cardElevation(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Rotating album CD
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF222831))
                        .rotate(if (isPlaying) rotation else 0f),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = Color(0xFF4ECCA3),
                        modifier = Modifier.size(24.dp)
                    )
                }
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = song.artist,
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                // Audio controls
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    IconButton(onClick = { MusicPlayerManager.previous() }) {
                        Icon(Icons.Default.SkipPrevious, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                    }
                    IconButton(
                        onClick = { MusicPlayerManager.togglePlayPause() },
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color(0xFF4ECCA3), CircleShape)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color(0xFF1A1A2E),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(onClick = { MusicPlayerManager.next() }) {
                        Icon(Icons.Default.SkipNext, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(6.dp))
            
            // Audio Seek Slider
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = VideoScanner.formatDuration(position),
                    fontSize = 9.sp,
                    color = Color.White.copy(alpha = 0.7f)
                )
                Slider(
                    value = if (duration > 0) position.toFloat() else 0f,
                    onValueChange = { MusicPlayerManager.seekTo(it.toLong()) },
                    valueRange = 0f..max(1f, duration.toFloat()),
                    modifier = Modifier
                        .weight(1f)
                        .height(28.dp)
                        .padding(horizontal = 8.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF4ECCA3),
                        activeTrackColor = Color(0xFF4ECCA3),
                        inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                    )
                )
                Text(
                    text = VideoScanner.formatDuration(duration),
                    fontSize = 9.sp,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }
        }
    }
}

// Helpers
@Composable
fun Modifier.fillGridOrColumn(activeTab: Int) = this
    .fillMaxWidth()
    .fillMaxHeight()

@Composable
fun VideoListCard(
    video: VideoItem,
    imageLoader: ImageLoader,
    onClick: () -> Unit,
    onRenameClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(90.dp, 60.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black)
            ) {
                AsyncImage(
                    model = video.uriString,
                    imageLoader = imageLoader,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = VideoScanner.formatDuration(video.duration),
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = video.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                // Display Scan Date added
                Text(
                    text = "Ngày quét: ${VideoScanner.formatDate(video.dateAdded)}",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = VideoScanner.formatSize(video.size),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Box(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = video.extension.uppercase(),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
            
            var showMenu by remember { mutableStateOf(false) }
            
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Tùy chọn"
                    )
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Đổi tên") },
                        onClick = {
                            showMenu = false
                            onRenameClick()
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Xóa", color = Color.Red) },
                        onClick = {
                            showMenu = false
                            onDeleteClick()
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red, modifier = Modifier.size(16.dp))
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun VideoGridCard(
    video: VideoItem,
    imageLoader: ImageLoader,
    onClick: () -> Unit,
    onRenameClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.5f)
                    .background(Color.Black)
            ) {
                AsyncImage(
                    model = video.uriString,
                    imageLoader = imageLoader,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = VideoScanner.formatDuration(video.duration),
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = video.title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                // Scan Date
                Text(
                    text = "Ngày quét: ${VideoScanner.formatDate(video.dateAdded)}",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = VideoScanner.formatSize(video.size),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Box(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = video.extension.uppercase(),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                    
                    var showMenu by remember { mutableStateOf(false) }
                    
                    Box {
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Tùy chọn",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Đổi tên") },
                                onClick = {
                                    showMenu = false
                                    onRenameClick()
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Xóa", color = Color.Red) },
                                onClick = {
                                    showMenu = false
                                    onDeleteClick()
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red, modifier = Modifier.size(16.dp))
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FolderCard(
    folderName: String,
    videoCount: Int,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = folderName,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "$videoCount video",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
fun OnlineStreamCard(
    title: String,
    url: String,
    isImported: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (isImported) MaterialTheme.colorScheme.secondaryContainer 
                        else MaterialTheme.colorScheme.primaryContainer
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isImported) Icons.Default.PlayCircleOutline else Icons.Default.Movie,
                    contentDescription = null,
                    tint = if (isImported) MaterialTheme.colorScheme.onSecondaryContainer 
                           else MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = url,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Phát ngay",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun OnlineMoviesSection(
    viewModel: MainScreenViewModel,
    context: Context,
    onNavigate: (androidx.navigation3.runtime.NavKey) -> Unit,
    scope: kotlinx.coroutines.CoroutineScope
) {
    val importedStreams by viewModel.importedStreams.collectAsState()
    
    val kkMovies by viewModel.kkMovies.collectAsState()
    val kkIsSearching by viewModel.kkIsSearching.collectAsState()
    val kkCurrentPage by viewModel.kkCurrentPage.collectAsState()
    val kkTotalPages by viewModel.kkTotalPages.collectAsState()
    val kkTotalItems by viewModel.kkTotalItems.collectAsState()
    val kkMovieSearchQuery by viewModel.kkMovieSearchQuery.collectAsState()
    
    val kkSortField by viewModel.kkSortField.collectAsState()
    val kkSortType by viewModel.kkSortType.collectAsState()
    val kkSortLang by viewModel.kkSortLang.collectAsState()
    val kkCategory by viewModel.kkCategory.collectAsState()
    val kkCountry by viewModel.kkCountry.collectAsState()
    val kkYear by viewModel.kkYear.collectAsState()
    val kkType by viewModel.kkType.collectAsState()
    val kkLimit by viewModel.kkLimit.collectAsState()

    val selectedMovieDetail by viewModel.kkSelectedMovieDetail.collectAsState()
    val isLoadingDetail by viewModel.kkIsLoadingDetail.collectAsState()

    var showFilters by remember { mutableStateOf(false) }

    LaunchedEffect(kkCategory, kkCountry, kkSortField, kkSortType, kkSortLang, kkYear, kkType) {
        viewModel.fetchLatestKkMovies(1)
    }

    val txtImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            scope.launch {
                try {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val text = inputStream?.bufferedReader()?.use { it.readText() } ?: ""
                    if (text.isNotBlank()) {
                        val parsed = parseImportedText(text)
                        if (parsed.isNotEmpty()) {
                            viewModel.addImportedStreams(context, parsed)
                            Toast.makeText(context, "Đã nhập thành công ${parsed.size} tập phim!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Định dạng file không hợp lệ hoặc không có dữ liệu!", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(context, "Lỗi khi nhập file playlist!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        // Control buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = { txtImportLauncher.launch("text/plain") },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.Folder, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(Loc.importPlaylist, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }

            if (importedStreams.isNotEmpty()) {
                Button(
                    onClick = { viewModel.clearImportedStreams(context) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(Loc.clearImported, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            if (importedStreams.isNotEmpty()) {
                item {
                    Text(
                        text = "DANH SÁCH ĐÃ NHẬP (${importedStreams.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                }

                items(importedStreams) { item ->
                    OnlineStreamCard(
                        title = item.title,
                        url = item.url,
                        isImported = true,
                        onClick = {
                            val allStreams = importedStreams.map {
                                VideoPlaylistItem(it.title, it.url, isOnline = true)
                            }
                            viewModel.setActivePlaylist(allStreams)
                            viewModel.addRecentVideo(context, item.title, item.url, true)
                            onNavigate(com.example.mediaplayer.Player(videoPath = item.url, videoTitle = item.title, isOnline = true))
                        }
                    )
                }
            }

            // Search and filters Section
            item {
                Text(
                    text = "TÌM KIẾM PHIM ONLINE (API KKPHIM)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
                )
            }

            item {
                OutlinedTextField(
                    value = kkMovieSearchQuery,
                    onValueChange = { viewModel.setKkMovieSearchQuery(it) },
                    placeholder = { Text("Nhập tên phim...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = { showFilters = !showFilters }) {
                            Icon(
                                imageVector = Icons.Default.FilterList,
                                contentDescription = "Bộ lọc",
                                tint = if (showFilters) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { viewModel.searchKkMovies(1) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Search, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Tìm kiếm", fontSize = 13.sp)
                    }

                    if (kkMovieSearchQuery.isNotEmpty() || kkCategory.isNotEmpty() || kkCountry.isNotEmpty() || kkYear.isNotEmpty() || kkType.isNotEmpty()) {
                        OutlinedButton(
                            onClick = {
                                viewModel.setKkMovieSearchQuery("")
                                viewModel.kkCategory.value = ""
                                viewModel.kkCountry.value = ""
                                viewModel.kkYear.value = ""
                                viewModel.kkType.value = ""
                                viewModel.kkSortField.value = "modified.time"
                                viewModel.kkSortType.value = "desc"
                                viewModel.kkSortLang.value = ""
                                viewModel.fetchLatestKkMovies(1)
                            },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Đặt lại", fontSize = 13.sp)
                        }
                    }
                }
            }

            item {
                AnimatedVisibility(
                    visible = showFilters,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Bộ lọc nâng cao",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(modifier = Modifier.weight(1f)) {
                                    FilterDropdown(
                                        label = "Thể loại",
                                        selectedValue = kkCategory,
                                        options = CATEGORIES,
                                        onValueChange = { viewModel.kkCategory.value = it }
                                    )
                                }
                                Box(modifier = Modifier.weight(1f)) {
                                    FilterDropdown(
                                        label = "Quốc gia",
                                        selectedValue = kkCountry,
                                        options = COUNTRIES,
                                        onValueChange = { viewModel.kkCountry.value = it }
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(modifier = Modifier.weight(1f)) {
                                    FilterDropdown(
                                        label = "Sắp xếp",
                                        selectedValue = kkSortField,
                                        options = SORT_FIELDS,
                                        onValueChange = { viewModel.kkSortField.value = it }
                                    )
                                }
                                Box(modifier = Modifier.weight(1f)) {
                                    FilterDropdown(
                                        label = "Thứ tự",
                                        selectedValue = kkSortType,
                                        options = SORT_TYPES,
                                        onValueChange = { viewModel.kkSortType.value = it }
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(modifier = Modifier.weight(1f)) {
                                    FilterDropdown(
                                        label = "Ngôn ngữ",
                                        selectedValue = kkSortLang,
                                        options = SORT_LANGS,
                                        onValueChange = { viewModel.kkSortLang.value = it }
                                    )
                                }
                                Box(modifier = Modifier.weight(1f)) {
                                    FilterDropdown(
                                        label = "Định dạng",
                                        selectedValue = kkType,
                                        options = MOVIE_TYPES,
                                        onValueChange = { viewModel.kkType.value = it }
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(modifier = Modifier.weight(0.5f)) {
                                    OutlinedTextField(
                                        value = kkYear,
                                        onValueChange = { viewModel.kkYear.value = it },
                                        label = { Text("Năm phát hành", fontSize = 11.sp) },
                                        placeholder = { Text("2024") },
                                        shape = RoundedCornerShape(8.dp),
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                     )
                                }
                                Spacer(modifier = Modifier.weight(0.5f))
                            }
                        }
                    }
                }
            }

            // Results grid or loading indicator
            if (kkIsSearching) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            } else if (kkMovies.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Không tìm thấy kết quả nào.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                val movieChunks = kkMovies.chunked(2)
                items(movieChunks) { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        rowItems.forEach { movie ->
                            Box(modifier = Modifier.weight(1f)) {
                                KkMovieGridCard(
                                    movie = movie,
                                    onClick = {
                                        viewModel.fetchKkMovieDetail(movie.slug)
                                    }
                                )
                            }
                        }
                        if (rowItems.size < 2) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }

                if (kkTotalPages > 1) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                enabled = kkCurrentPage > 1 && !kkIsSearching,
                                onClick = {
                                    val prev = kkCurrentPage - 1
                                    if (kkMovieSearchQuery.isEmpty()) {
                                        viewModel.fetchLatestKkMovies(prev)
                                    } else {
                                        viewModel.searchKkMovies(prev)
                                    }
                                }
                            ) {
                                Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "Trang trước")
                            }

                            Text(
                                text = "$kkCurrentPage / $kkTotalPages ($kkTotalItems phim)",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )

                            IconButton(
                                enabled = kkCurrentPage < kkTotalPages && !kkIsSearching,
                                onClick = {
                                    val next = kkCurrentPage + 1
                                    if (kkMovieSearchQuery.isEmpty()) {
                                        viewModel.fetchLatestKkMovies(next)
                                    } else {
                                        viewModel.searchKkMovies(next)
                                    }
                                }
                            ) {
                                Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Trang sau")
                            }
                        }
                    }
                }
            }
        }
    }

    // Detail Episode Dialog
    selectedMovieDetail?.let { detail ->
        KkMovieEpisodeDialog(
            detail = detail,
            onDismiss = { viewModel.clearKkSelectedMovieDetail() },
            onPlayEpisode = { episode ->
                val playlistItem = VideoPlaylistItem(
                    title = "${detail.movie?.name} - ${episode.name}",
                    path = episode.link_m3u8,
                    isOnline = true
                )
                viewModel.setActivePlaylist(listOf(playlistItem))
                viewModel.addRecentVideo(context, "${detail.movie?.name} - ${episode.name}", episode.link_m3u8, true)
                onNavigate(com.example.mediaplayer.Player(videoPath = episode.link_m3u8, videoTitle = "${detail.movie?.name} - ${episode.name}", isOnline = true))
            }
        )
    }

    // Resolving details indicator
    if (isLoadingDetail) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(enabled = false) {},
            contentAlignment = Alignment.Center
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.padding(24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Đang tải chi tiết phim...", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

fun parseImportedText(text: String): List<HlsStreamItem> {
    val streams = mutableListOf<HlsStreamItem>()
    text.lineSequence().forEach { line ->
        val trimmed = line.trim()
        if (trimmed.isNotEmpty() && trimmed.contains("|")) {
            val parts = trimmed.split("|", limit = 2)
            if (parts.size == 2) {
                val title = parts[0].trim()
                var rawUrl = parts[1].trim()
                if (rawUrl.contains("url=")) {
                    try {
                        val uri = android.net.Uri.parse(rawUrl)
                        val urlParam = uri.getQueryParameter("url")
                        if (urlParam != null) {
                            rawUrl = urlParam
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                streams.add(HlsStreamItem(title, rawUrl, isImported = true))
            }
        }
    }
    return streams
}

val CATEGORIES = listOf(
    "" to "Tất cả thể loại",
    "hanh-dong" to "Hành động",
    "co-trang" to "Cổ trang",
    "chien-tranh" to "Chiến tranh",
    "vien-tuong" to "Viễn tưởng",
    "kinh-di" to "Kinh dị",
    "tai-lieu" to "Tài liệu",
    "hai-huoc" to "Hài hước",
    "tinh-cam" to "Tình cảm",
    "tam-ly" to "Tâm lý",
    "the-thao" to "Thể thao",
    "hinh-su" to "Hình sự",
    "am-nhac" to "Âm nhạc",
    "phieu-luu" to "Phiêu lưu",
    "gia-dinh" to "Gia đình",
    "than-thoai" to "Thần thoại"
)

val COUNTRIES = listOf(
    "" to "Tất cả quốc gia",
    "trung-quoc" to "Trung Quốc",
    "han-quoc" to "Hàn Quốc",
    "au-my" to "Âu Mỹ",
    "nhat-ban" to "Nhật Bản",
    "viet-nam" to "Việt Nam",
    "thai-lan" to "Thái Lan",
    "an-do" to "Ấn Độ",
    "hong-kong" to "Hồng Kông",
    "dai-loan" to "Đài Loan"
)

val MOVIE_TYPES = listOf(
    "" to "Mọi định dạng",
    "phim-bo" to "Phim bộ",
    "phim-le" to "Phim lẻ",
    "hoat-hinh" to "Hoạt hình",
    "tv-shows" to "TV Shows"
)

val SORT_FIELDS = listOf(
    "modified.time" to "Mới cập nhật",
    "_id" to "Theo ID",
    "year" to "Năm phát hành"
)

val SORT_TYPES = listOf(
    "desc" to "Giảm dần",
    "asc" to "Tăng dần"
)

val SORT_LANGS = listOf(
    "" to "Mọi ngôn ngữ",
    "vietsub" to "Vietsub",
    "thuyet-minh" to "Thuyết Minh",
    "long-tieng" to "Lồng Tiếng"
)

val AVDB_CATEGORIES = listOf(
    "" to "Tất cả thể loại",
    "1" to "Censored",
    "2" to "Uncensored",
    "3" to "Uncensored Leaked",
    "4" to "Amateur",
    "5" to "Chinese AV",
    "6" to "Hentai",
    "7" to "English subtitle"
)

val AVDB_SORT_DIRECTIONS = listOf(
    "desc" to "Mới nhất",
    "asc" to "Cũ nhất"
)

val AVDB_UPDATE_HOURS = listOf(
    "" to "Mọi lúc",
    "24" to "24 giờ qua",
    "48" to "48 giờ qua",
    "168" to "7 ngày qua"
)

@Composable
fun FilterDropdown(
    label: String,
    selectedValue: String,
    options: List<Pair<String, String>>,
    onValueChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val displayLabel = options.firstOrNull { it.first == selectedValue }?.second ?: selectedValue

    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedCard(
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(text = displayLabel, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.fillMaxWidth(0.9f)
        ) {
            options.forEach { (valStr, labelStr) ->
                DropdownMenuItem(
                    text = { Text(labelStr) },
                    onClick = {
                        onValueChange(valStr)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun KkMovieGridCard(
    movie: KkMovie,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.7f)
                    .background(Color.DarkGray)
            ) {
                AsyncImage(
                    model = movie.posterUrl.ifBlank { movie.thumbUrl },
                    contentDescription = movie.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                
                // Badges
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    contentAlignment = Alignment.TopStart
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (movie.quality.isNotBlank() && movie.quality != "N/A") {
                            Badge(containerColor = MaterialTheme.colorScheme.primary) {
                                Text(text = movie.quality, color = Color.White, fontSize = 9.sp)
                            }
                        }
                        if (movie.lang.isNotBlank() && movie.lang != "N/A") {
                            Badge(containerColor = MaterialTheme.colorScheme.secondary) {
                                Text(text = movie.lang, color = Color.White, fontSize = 9.sp)
                            }
                        }
                    }
                }

                // Episode Current Badge
                if (movie.episodeCurrent.isNotBlank() && movie.episodeCurrent != "N/A") {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                        contentAlignment = Alignment.BottomEnd
                    ) {
                        Badge(containerColor = Color.Black.copy(alpha = 0.6f)) {
                            Text(text = movie.episodeCurrent, color = Color.White, fontSize = 9.sp)
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = movie.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${movie.originName} (${movie.year})",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun KkMovieEpisodeDialog(
    detail: PhimApiDetailResponse,
    onDismiss: () -> Unit,
    onPlayEpisode: (PhimApiEpisodeData) -> Unit
) {
    val movie = detail.movie ?: return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = movie.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AsyncImage(
                        model = movie.poster_url ?: movie.thumb_url,
                        contentDescription = movie.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(width = 100.dp, height = 145.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.DarkGray)
                    )
                    
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Tên gốc: ${movie.origin_name}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Năm: ${movie.year} | Chất lượng: ${movie.quality}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "Ngôn ngữ: ${movie.lang}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "Thể loại: ${movie.category?.joinToString { it.name } ?: "N/A"}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "Quốc gia: ${movie.country?.joinToString { it.name } ?: "N/A"}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                movie.content?.let { content ->
                    if (content.isNotBlank()) {
                        Text(
                            text = "Nội dung phim",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = content,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (detail.episodes.isEmpty()) {
                    Text(
                        text = "Không tìm thấy nguồn phát trực tiếp nào.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    detail.episodes.forEach { server ->
                        Text(
                            text = "Nguồn: ${server.server_name}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                        
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            server.server_data.forEach { ep ->
                                Button(
                                    onClick = { onPlayEpisode(ep) },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                    ),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = ep.name,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Đóng")
            }
        }
    )
}

@Composable
fun RecentVideosSection(
    viewModel: MainScreenViewModel,
    context: Context,
    onNavigate: (androidx.navigation3.runtime.NavKey) -> Unit,
    scope: kotlinx.coroutines.CoroutineScope
) {
    val recentVideos by viewModel.recentVideos.collectAsState()
    
    LaunchedEffect(Unit) {
        viewModel.loadRecentVideos(context)
    }
    
    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        if (recentVideos.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Không có video xem gần đây.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Lịch sử xem (${recentVideos.size})",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                TextButton(onClick = { viewModel.clearRecentVideos(context) }) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Xóa lịch sử", fontSize = 13.sp)
                }
            }
            
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(recentVideos) { item ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onNavigate(Player(videoPath = item.path, videoTitle = item.title, isOnline = item.isOnline))
                            },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (item.isOnline) Icons.Default.CloudQueue else Icons.Default.Movie,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.title,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (item.isOnline) "Nguồn: Trực tuyến" else "Nguồn: Thiết bị",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AvdbMoviesSection(
    viewModel: MainScreenViewModel,
    movies: List<com.example.mediaplayer.data.AvdbMovieItem>,
    isSearching: Boolean,
    currentPage: Int,
    totalPages: Int,
    totalItems: Int,
    searchQuery: String,
    selectedMovieDetail: com.example.mediaplayer.data.AvdbMovieItem?,
    isLoadingDetail: Boolean,
    context: Context,
    onNavigate: (androidx.navigation3.runtime.NavKey) -> Unit,
    scope: kotlinx.coroutines.CoroutineScope,
    onPlayClick: (String, String) -> Unit
) {
    var isGridView by remember { mutableStateOf(true) }
    var showFilters by remember { mutableStateOf(false) }

    val avdbT by viewModel.avdbT.collectAsState()
    val avdbYear by viewModel.avdbYear.collectAsState()
    val avdbSortDirection by viewModel.avdbSortDirection.collectAsState()
    val avdbH by viewModel.avdbH.collectAsState()

    LaunchedEffect(avdbT, avdbYear, avdbSortDirection, avdbH) {
        viewModel.fetchAvdbMovies(1)
    }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        // Search bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.setAvdbSearchQuery(it) },
            placeholder = { Text("Tìm kiếm JAV (mã, diễn viên, từ khóa)...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setAvdbSearchQuery("") }) {
                            Icon(Icons.Default.Close, contentDescription = null)
                        }
                    }
                    IconButton(onClick = { showFilters = !showFilters }) {
                        Icon(
                            imageVector = Icons.Default.FilterList,
                            contentDescription = "Bộ lọc",
                            tint = if (showFilters) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )
        
        Spacer(modifier = Modifier.height(10.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = { viewModel.searchAvdbMovies(1) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Search, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Tìm kiếm")
            }

            IconButton(onClick = { isGridView = !isGridView }) {
                Icon(
                    imageVector = if (isGridView) Icons.Default.List else Icons.Default.GridView,
                    contentDescription = null
                )
            }
        }
        
        Spacer(modifier = Modifier.height(10.dp))

        AnimatedVisibility(
            visible = showFilters,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Bộ lọc AV JAV",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (avdbT.isNotEmpty() || avdbYear.isNotEmpty() || avdbSortDirection != "desc" || avdbH.isNotEmpty()) {
                            TextButton(
                                onClick = {
                                    viewModel.avdbT.value = ""
                                    viewModel.avdbYear.value = ""
                                    viewModel.avdbSortDirection.value = "desc"
                                    viewModel.avdbH.value = ""
                                }
                            ) {
                                Text("Đặt lại", fontSize = 11.sp)
                            }
                        }
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            FilterDropdown(
                                label = "Thể loại",
                                selectedValue = avdbT,
                                options = AVDB_CATEGORIES,
                                onValueChange = { viewModel.avdbT.value = it }
                            )
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            FilterDropdown(
                                label = "Sắp xếp",
                                selectedValue = avdbSortDirection,
                                options = AVDB_SORT_DIRECTIONS,
                                onValueChange = { viewModel.avdbSortDirection.value = it }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            FilterDropdown(
                                label = "Cập nhật",
                                selectedValue = avdbH,
                                options = AVDB_UPDATE_HOURS,
                                onValueChange = { viewModel.avdbH.value = it }
                            )
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            OutlinedTextField(
                                value = avdbYear,
                                onValueChange = { viewModel.avdbYear.value = it },
                                label = { Text("Năm phát hành", fontSize = 11.sp) },
                                placeholder = { Text("2024") },
                                shape = RoundedCornerShape(8.dp),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
        
        PullToRefreshBox(
            isRefreshing = isSearching,
            onRefresh = {
                if (searchQuery.isNotBlank()) {
                    viewModel.searchAvdbMovies(currentPage)
                } else {
                    viewModel.fetchAvdbMovies(currentPage)
                }
            },
            modifier = Modifier.weight(1f)
        ) {
            if (movies.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isSearching) "Đang tải dữ liệu..." else "Không tìm thấy phim JAV nào.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    if (isGridView) {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 150.dp),
                            contentPadding = PaddingValues(bottom = 80.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            items(movies) { movie ->
                                AvdbMovieGridCard(
                                    movie = movie,
                                    onClick = {
                                        viewModel.fetchAvdbMovieDetail(movie.id)
                                    }
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(bottom = 80.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            items(movies) { movie ->
                                AvdbMovieListCard(
                                    movie = movie,
                                    onClick = {
                                        viewModel.fetchAvdbMovieDetail(movie.id)
                                    }
                                )
                            }
                        }
                    }
                    
                    // Pagination Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val prev = max(1, currentPage - 1)
                        val next = if (currentPage < totalPages) currentPage + 1 else totalPages
                        
                        IconButton(
                            onClick = {
                                if (searchQuery.isNotBlank()) {
                                    viewModel.searchAvdbMovies(prev)
                                } else {
                                    viewModel.fetchAvdbMovies(prev)
                                }
                            },
                            enabled = currentPage > 1
                        ) {
                            Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "Trang trước")
                        }
                        
                        Text(
                            text = "Trang $currentPage / $totalPages ($totalItems phim)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        IconButton(
                            onClick = {
                                if (searchQuery.isNotBlank()) {
                                    viewModel.searchAvdbMovies(next)
                                } else {
                                    viewModel.fetchAvdbMovies(next)
                                }
                            },
                            enabled = currentPage < totalPages
                        ) {
                            Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Trang sau")
                        }
                    }
                }
            }
        }
    }

    if (isLoadingDetail) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(enabled = false) {},
            contentAlignment = Alignment.Center
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.padding(24.dp)
            ) {
                Row(
                    modifier = Modifier.padding(24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Text("Đang tải chi tiết phim...", fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    // Detail dialog
    if (selectedMovieDetail != null) {
        AvdbMovieEpisodeDialog(
            movie = selectedMovieDetail,
            onDismiss = { viewModel.clearAvdbSelectedMovieDetail() },
            onPlayEpisode = { embedUrl ->
                viewModel.clearAvdbSelectedMovieDetail()
                onPlayClick(embedUrl, selectedMovieDetail.name)
            }
        )
    }
}

@Composable
fun AvdbMovieGridCard(
    movie: com.example.mediaplayer.data.AvdbMovieItem,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                val imgUrl = movie.thumb_url ?: movie.poster_url ?: ""
                if (imgUrl.startsWith("http")) {
                    AsyncImage(
                        model = imgUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.SmartDisplay,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(40.dp)
                    )
                }
                
                // Top badge for type (e.g. "Có che" or "Censored")
                val type = movie.type_name ?: ""
                if (type.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                            .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = type,
                            color = Color.White,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            
            Column(modifier = Modifier.padding(8.dp)) {
                val code = movie.movie_code ?: movie.slug.uppercase()
                Text(
                    text = code,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = movie.name,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 14.sp
                )
            }
        }
    }
}

@Composable
fun AvdbMovieListCard(
    movie: com.example.mediaplayer.data.AvdbMovieItem,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(width = 90.dp, height = 65.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                val imgUrl = movie.thumb_url ?: movie.poster_url ?: ""
                if (imgUrl.startsWith("http")) {
                    AsyncImage(
                        model = imgUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.SmartDisplay,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(36.dp)
                    )
                }
                
                val type = movie.type_name ?: ""
                if (type.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(4.dp)
                            .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = type,
                            color = Color.White,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                val code = movie.movie_code ?: movie.slug.uppercase()
                Text(
                    text = code,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = movie.name,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (!movie.year.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Năm phát hành: ${movie.year}",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AvdbMovieEpisodeDialog(
    movie: com.example.mediaplayer.data.AvdbMovieItem,
    onDismiss: () -> Unit,
    onPlayEpisode: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            val code = movie.movie_code ?: movie.slug.uppercase()
            Text(
                text = code,
                fontWeight = FontWeight.Black,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.primary
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Movie Name
                Text(
                    text = movie.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )

                // Info Section
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(90.dp, 130.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black)
                    ) {
                        val imgUrl = movie.poster_url ?: movie.thumb_url ?: ""
                        if (imgUrl.isNotBlank()) {
                            AsyncImage(
                                model = imgUrl,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                    
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (!movie.type_name.isNullOrBlank()) {
                            Text(text = "Phân loại: ${movie.type_name}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        if (!movie.quality.isNullOrBlank()) {
                            Text(text = "Chất lượng: ${movie.quality}", fontSize = 12.sp)
                        }
                        if (!movie.time.isNullOrBlank()) {
                            Text(text = "Thời lượng: ${movie.time}", fontSize = 12.sp)
                        }
                        if (movie.actor.isNotEmpty()) {
                            val actors = movie.actor.joinToString { it.trim() }
                            Text(text = "Diễn viên: $actors", fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        if (movie.category.isNotEmpty()) {
                            val cats = movie.category.joinToString { it.trim() }
                            Text(text = "Thể loại: $cats", fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }

                // Story/Description
                if (!movie.description.isNullOrBlank()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(text = "Tóm tắt nội dung:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(
                            text = movie.description,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                // Episodes Section
                Text(text = "Danh sách tập:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                val serverData = movie.episodes?.server_data ?: emptyMap()
                if (serverData.isNotEmpty()) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        serverData.forEach { (epName, epLink) ->
                            Button(
                                onClick = {
                                    if (epLink.link_embed.isNotBlank()) {
                                        onPlayEpisode(epLink.link_embed)
                                    }
                                },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(text = epName)
                            }
                        }
                    }
                } else {
                    Text(
                        text = "Không có tập phim nào khả dụng.",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Đóng")
            }
        }
    )
}

@Composable
fun AdultUrlResolvingOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
            .clickable(enabled = false) {}, // Block clicks behind
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            modifier = Modifier.padding(32.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Đang giải mã luồng video...",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Vui lòng chờ trong giây lát",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun detectMovieName(url: String): String {
    // Try to find /play/index/([^/?#\s]+)
    val playIndexRegex = Regex("""/play/index/([^/?#\s]+)""")
    val match = playIndexRegex.find(url)
    if (match != null) {
        return sanitizeFilename(match.groupValues[1])
    }
    
    // Try to find v=<name> query parameter
    try {
        val uri = java.net.URI(url)
        val query = uri.query
        if (query != null) {
            val params = query.split("&")
            for (param in params) {
                val parts = param.split("=")
                if (parts.size == 2 && parts[0] == "v") {
                    return sanitizeFilename(parts[1])
                }
            }
        }
        
        // Fallback to last path segment if it's not a generic word
        val path = uri.path ?: ""
        val segments = path.split("/").filter { it.isNotEmpty() }
        if (segments.isNotEmpty()) {
            val lastSeg = segments.last()
            if (lastSeg != "index" && lastSeg != "token_hash" && lastSeg != "play") {
                return sanitizeFilename(lastSeg)
            }
        }
    } catch (e: Exception) {
        // Fallback simple regex parsing if URI parsing fails
        val vParamRegex = Regex("""[?&]v=([^&#\s]+)""")
        val vMatch = vParamRegex.find(url)
        if (vMatch != null) {
            return sanitizeFilename(vMatch.groupValues[1])
        }
    }
    return ""
}

private fun sanitizeFilename(name: String): String {
    val withUnderscores = name.replace(Regex("""\s+"""), "_")
    return withUnderscores.replace(Regex("""[^a-zA-Z0-9_-]"""), "")
}

