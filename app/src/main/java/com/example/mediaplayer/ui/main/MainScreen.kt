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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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

    // --- Persisted BackgroundPlayWebView for YouTube ---
    val webView = remember {
        BackgroundPlayWebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.databaseEnabled = true
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            settings.userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
            
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    // Auto block ads and bypass overlays, and mock Page Visibility API
                    view?.evaluateJavascript(
                        """
                        (function() {
                          Object.defineProperty(document, 'hidden', { get: function() { return false; }, configurable: true });
                          Object.defineProperty(document, 'visibilityState', { get: function() { return 'visible'; }, configurable: true });
                          window.addEventListener('visibilitychange', function(e) { e.stopImmediatePropagation(); }, true);
                          document.addEventListener('visibilitychange', function(e) { e.stopImmediatePropagation(); }, true);

                          var style = document.createElement('style');
                          style.type = 'text/css';
                          style.innerHTML = '.video-ads, .ytp-ad-module, .ytp-ad-overlay-container, .promoted-sparkles-text-search-root, ytd-promoted-sparkles-web-renderer { display: none !important; }';
                          document.head.appendChild(style);

                          setInterval(function() {
                            var skipButton = document.querySelector('.ytp-ad-skip-button, .ytp-ad-skip-button-modern, .ytp-ad-skip-button-slot');
                            if (skipButton) {
                              skipButton.click();
                              console.log('Ad skipped by Premium Player!');
                            }
                            var video = document.querySelector('video');
                            if (video && document.querySelector('.ad-showing')) {
                              video.playbackRate = 16.0;
                              video.muted = true;
                            }
                          }, 500);
                        })();
                        """.trimIndent(),
                        null
                    )
                }
            }
            loadUrl("https://m.youtube.com")
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
                    if (activeBottomTab == 2) {
                        // YouTube WebView Navigation
                        IconButton(
                            onClick = {
                                if (webView.canGoBack()) webView.goBack()
                            }
                        ) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Trang trước", tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(
                            onClick = {
                                webView.loadUrl("https://m.youtube.com")
                            }
                        ) {
                            Icon(Icons.Default.Home, contentDescription = "YouTube Home", tint = MaterialTheme.colorScheme.primary)
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
                    icon = { Icon(Icons.Default.PlayCircleOutline, contentDescription = null) },
                    label = { Text(Loc.youtubeBottomTab) }
                )
                NavigationBarItem(
                    selected = activeBottomTab == 3,
                    onClick = { activeBottomTab = 3 },
                    icon = { Icon(Icons.Default.Album, contentDescription = null) },
                    label = { Text(Loc.djMixerBottomTab) }
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

                        TabRow(selectedTabIndex = videoFolderTab) {
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
                                text = { Text(Loc.onlineMoviesTab, fontWeight = FontWeight.Bold) }
                            )
                        }

                        if (videoFolderTab == 2) {
                            OnlineMoviesSection(
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
                                        .padding(horizontal = 16.dp, vertical = 6.dp),
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
                                                columns = GridCells.Fixed(2),
                                                contentPadding = PaddingValues(12.dp),
                                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                                modifier = Modifier.fillMaxSize()
                                            ) {
                                                items(filteredVideos) { video ->
                                                    VideoGridCard(
                                                        video = video,
                                                        imageLoader = imageLoader,
                                                        onClick = {
                                                            viewModel.setActivePlaylist(filteredVideos.map { VideoPlaylistItem(it.title, it.path, isOnline = false) })
                                                            onNavigate(Player(videoPath = video.path, videoTitle = video.title, isOnline = false))
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
                                                items(filteredVideos) { video ->
                                                    VideoListCard(
                                                        video = video,
                                                        imageLoader = imageLoader,
                                                        onClick = {
                                                            viewModel.setActivePlaylist(filteredVideos.map { VideoPlaylistItem(it.title, it.path, isOnline = false) })
                                                            onNavigate(Player(videoPath = video.path, videoTitle = video.title, isOnline = false))
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
                        TabRow(selectedTabIndex = musicSectionTab) {
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

            // Tab 2: YouTube WebView (Always attached, but hidden to maintain background execution)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (activeBottomTab == 2) {
                            Modifier.fillMaxSize()
                        } else {
                            Modifier.size(1.dp).absoluteOffset(y = (-5000).dp)
                        }
                    )
            ) {
                AndroidView(
                    factory = { webView },
                    update = { view ->
                        // Keep VISIBLE so Android doesn't pause the WebView thread
                        view.visibility = android.view.View.VISIBLE
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Tab 3: Producer DJ Mixer Screen
            if (activeBottomTab == 3) {
                com.example.mediaplayer.ui.mixer.DjMixerScreen(viewModel = viewModel)
            }

            // --- Sticky Music Player Overlay ---
            val currentPlayingSong by MusicPlayerManager.currentSong.collectAsState()
            AnimatedVisibility(
                visible = currentPlayingSong != null && activeBottomTab != 3,
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
                        onValueChange = { hlsUrl = it },
                        label = { Text(Loc.hlsUrlPlaceholder) },
                        placeholder = { Text("https://example.com/stream.m3u8") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (hlsUrl.trim().isNotEmpty()) {
                            val title = if (hlsTitle.trim().isEmpty()) "HLS Stream" else hlsTitle
                            showHlsDialog = false
                            onNavigate(Player(videoPath = hlsUrl.trim(), videoTitle = title, isOnline = true))
                        } else {
                            Toast.makeText(context, "Vui lòng nhập đường dẫn URL!", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text(Loc.playNow)
                }
            },
            dismissButton = {
                TextButton(onClick = { showHlsDialog = false }) {
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
                        .size(44.dp)
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
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoListCard(
    video: VideoItem,
    imageLoader: ImageLoader,
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
        }
    }
}

@Composable
fun VideoGridCard(
    video: VideoItem,
    imageLoader: ImageLoader,
    onClick: () -> Unit
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

@Composable
fun OnlineMoviesSection(
    viewModel: MainScreenViewModel,
    context: Context,
    onNavigate: (androidx.navigation3.runtime.NavKey) -> Unit,
    scope: kotlinx.coroutines.CoroutineScope
) {
    val importedStreams by viewModel.importedStreams.collectAsState()
    
    val predefinedStreams = remember {
        listOf(
            HlsStreamItem("Sintel (Phim hoạt hình HLS)", "https://bitdash-a.akamaihd.net/content/sintel/hls/playlist.m3u8", isImported = false),
            HlsStreamItem("Tears of Steel (Khoa học viễn tưởng)", "https://demo.unified-streaming.com/k8s/features/stable/video/tears-of-steel/tears-of-steel.ism/.m3u8", isImported = false),
            HlsStreamItem("Big Buck Bunny (Hoạt hình mẫu)", "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8", isImported = false),
            HlsStreamItem("IPTV Oceans Test (Live stream)", "https://playertest.longtailvideo.com/adaptive/oceans/oceans.m3u8", isImported = false)
        )
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
                            val allStreams = (importedStreams + predefinedStreams).map {
                                VideoPlaylistItem(it.title, it.url, isOnline = true)
                            }
                            viewModel.setActivePlaylist(allStreams)
                            onNavigate(Player(videoPath = item.url, videoTitle = item.title, isOnline = true))
                        }
                    )
                }
            }

            item {
                Text(
                    text = "DANH SÁCH PHIM MẪU",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 10.dp, bottom = 6.dp)
                )
            }

            items(predefinedStreams) { item ->
                OnlineStreamCard(
                    title = item.title,
                    url = item.url,
                    isImported = false,
                    onClick = {
                        val allStreams = (importedStreams + predefinedStreams).map {
                            VideoPlaylistItem(it.title, it.url, isOnline = true)
                        }
                        viewModel.setActivePlaylist(allStreams)
                        onNavigate(Player(videoPath = item.url, videoTitle = item.title, isOnline = true))
                    }
                )
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
