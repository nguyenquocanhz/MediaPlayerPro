package com.example.mediaplayer.ui.player

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.net.Uri
import android.view.WindowManager
import androidx.annotation.OptIn
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mediaplayer.ui.main.VideoPlaylistItem
import com.example.mediaplayer.data.VideoScanner
import com.example.mediaplayer.data.HttpClientFactory
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import org.json.JSONObject
import org.json.JSONArray
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.text.style.TextOverflow
import kotlin.math.max
import kotlin.math.min
import kotlin.math.abs

enum class DragType { VOLUME, BRIGHTNESS, SEEK, SWITCH_VIDEO }

class TapBubble(
    val x: Float,
    val y: Float,
    val radius: androidx.compose.animation.core.Animatable<Float, *>,
    val alpha: androidx.compose.animation.core.Animatable<Float, *>
)

data class SrtCue(val start: Long, val end: Long, val text: String)

fun parseSrtFile(content: String): List<SrtCue> {
    val cues = mutableListOf<SrtCue>()
    val blocks = content.split(Regex("(\\r?\\n){2,}"))
    for (block in blocks) {
        val lines = block.trim().lines().filter { it.isNotBlank() }
        if (lines.size >= 3) {
            val timeLine = lines[1]
            val textLines = lines.drop(2).joinToString("\n")
            val times = timeLine.split("-->")
            if (times.size == 2) {
                val startMs = parseSrtTime(times[0].trim())
                val endMs = parseSrtTime(times[1].trim())
                cues.add(SrtCue(startMs, endMs, textLines))
            }
        }
    }
    return cues
}

fun parseSrtTime(timeStr: String): Long {
    try {
        val parts = timeStr.replace(',', '.').split(":")
        val hours = parts[0].toLong()
        val minutes = parts[1].toLong()
        val secondsParts = parts[2].split(".")
        val seconds = secondsParts[0].toLong()
        val ms = if (secondsParts.size > 1) secondsParts[1].toLong() else 0L
        return hours * 3600000L + minutes * 60000L + seconds * 1000L + ms
    } catch (e: Exception) {
        return 0L
    }
}

enum class AspectRatioOption(val label: String, val ratio: Float?) {
    FIT("Tự động (Fit)", null),
    ZOOM("Thu phóng (Zoom)", null),
    STRETCH("Kéo giãn (Stretch)", null),
    RATIO_16_9("16:9", 16f / 9f),
    RATIO_21_9("21:9 (Rạp phim)", 21f / 9f),
    SMART("Tự thích ứng (Smart)", null)
}

// Subtitle Languages for translation
val TRANSLATION_LANGUAGES = listOf(
    "vi" to "Tiếng Việt",
    "en" to "English",
    "es" to "Español",
    "fr" to "Français",
    "de" to "Deutsch",
    "ja" to "日本語",
    "zh" to "中文"
)

// Simulated captions for testing translation feature
val SIMULATED_CAPTIONS = listOf(
    0L..4000L to "Welcome to the advanced Android Media Player!",
    4000L..8000L to "This project has been updated with modern capabilities.",
    8000L..12000L to "It supports portrait view, landscape view, and fullscreen mode.",
    12000L..16000L to "You can swipe vertically to adjust volume and brightness.",
    16000L..20000L to "Swipe horizontally to seek backward or forward in the video.",
    20000L..24000L to "Double tap on the left or right to skip 10 seconds quickly.",
    24000L..28000L to "Check out the aspect ratio options: 16:9, 1:1, 9:16, and Cinema 21:9.",
    28000L..32000L to "Also supports Picture-in-Picture mode for multitasking.",
    32000L..36000L to "And of course, auto-translating subtitles in real time!",
    36000L..40000L to "We hope you enjoy this premium media player experience."
)

// Free translation helper via MyMemory API
suspend fun translateText(text: String, fromLang: String = "en", toLang: String = "vi"): String {
    if (text.isBlank()) return ""
    return try {
        val client = HttpClientFactory.client
        val encodedText = URLEncoder.encode(text, "UTF-8")
        val response = client.get("https://api.mymemory.translated.net/get?q=$encodedText&langpair=$fromLang|$toLang")
        if (response.status == HttpStatusCode.OK) {
            val responseText = response.bodyAsText()
            val json = JSONObject(responseText)
            val responseData = json.getJSONObject("responseData")
            val translatedText = responseData.getString("translatedText")
            if (translatedText.isNotBlank()) {
                return translatedText
            }
        }
        text
    } catch (e: Exception) {
        e.printStackTrace()
        mockTranslate(text, toLang)
    }
}

fun mockTranslate(text: String, toLang: String): String {
    val lower = text.lowercase().trim()
    return when (toLang) {
        "vi" -> {
            if (lower.contains("welcome")) "Chào mừng bạn đến với Trình phát nhạc nâng cao!"
            else if (lower.contains("updated")) "Dự án này đã được cập nhật với các tính năng hiện đại."
            else if (lower.contains("supports portrait")) "Nó hỗ trợ xem dọc, xem ngang và chế độ toàn màn hình."
            else if (lower.contains("swipe vertically")) "Bạn có thể vuốt dọc để điều chỉnh âm lượng và độ sáng."
            else if (lower.contains("swipe horizontally")) "Vuốt ngang để tua lùi hoặc tua tới trong video."
            else if (lower.contains("double tap")) "Chạm đúp vào bên trái hoặc phải để bỏ qua 10 giây nhanh chóng."
            else if (lower.contains("aspect ratio")) "Kiểm tra các tùy chọn tỷ lệ khung hình: 16:9, 1:1, 9:16 và Cinema 21:9."
            else if (lower.contains("picture-in-picture")) "Đồng thời hỗ trợ chế độ Ảnh trong ảnh để đa nhiệm."
            else if (lower.contains("auto-translating")) "Và tất nhiên, dịch phụ đề tự động trong thời gian thực!"
            else if (lower.contains("enjoy")) "Chúng tôi hy vọng bạn thích trải nghiệm trình phát phương tiện cao cấp này."
            else "Dịch: $text"
        }
        "es" -> "Traducción: $text"
        "fr" -> "Traduction: $text"
        "de" -> "Übersetzung: $text"
        "ja" -> "翻訳: $text"
        "zh" -> "翻译: $text"
        else -> text
    }
}

suspend fun translateWithGemini(text: String, targetLanguage: String, apiKey: String): String {
    if (text.isBlank() || apiKey.isBlank()) return ""
    return try {
        val client = HttpClientFactory.client
        val targetLangName = TRANSLATION_LANGUAGES.firstOrNull { it.first == targetLanguage }?.second ?: targetLanguage
        val prompt = "Translate the following subtitle text to $targetLangName. Only return the direct translation, nothing else, no explanations, no formatting:\n\n$text"
        
        val urlString = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$apiKey"
        
        val requestBody = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", prompt)
                        })
                    })
                })
            })
        }.toString()
        
        val response = client.post(urlString) {
            contentType(ContentType.Application.Json)
            setBody(requestBody)
        }
        
        if (response.status == HttpStatusCode.OK) {
            val responseText = response.bodyAsText()
            val json = JSONObject(responseText)
            val candidates = json.getJSONArray("candidates")
            val firstCandidate = candidates.getJSONObject(0)
            val content = firstCandidate.getJSONObject("content")
            val parts = content.getJSONArray("parts")
            val textResult = parts.getJSONObject(0).getString("text").trim()
            if (textResult.isNotBlank()) {
                return textResult
            }
        }
        ""
    } catch (e: Exception) {
        e.printStackTrace()
        ""
    }
}

suspend fun explainSubtitleWithGemini(text: String, targetLanguage: String, apiKey: String): String {
    if (text.isBlank() || apiKey.isBlank()) return "Vui lòng nhập API Key trong cấu hình phụ đề."
    return try {
        val client = HttpClientFactory.client
        val targetLangName = TRANSLATION_LANGUAGES.firstOrNull { it.first == targetLanguage }?.second ?: targetLanguage
        val prompt = "Explain the following subtitle phrase or words in detail (vocabulary breakdown, idioms, grammar details and contextual meaning) in $targetLangName:\n\n$text"
        
        val urlString = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$apiKey"
        
        val requestBody = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", prompt)
                        })
                    })
                })
            })
        }.toString()
        
        val response = client.post(urlString) {
            contentType(ContentType.Application.Json)
            setBody(requestBody)
        }
        
        if (response.status == HttpStatusCode.OK) {
            val responseText = response.bodyAsText()
            val json = JSONObject(responseText)
            val candidates = json.getJSONArray("candidates")
            val firstCandidate = candidates.getJSONObject(0)
            val content = firstCandidate.getJSONObject("content")
            val parts = content.getJSONArray("parts")
            val textResult = parts.getJSONObject(0).getString("text").trim()
            if (textResult.isNotBlank()) {
                return textResult
            }
        }
        "Không nhận được phản hồi từ Gemini API. Mã lỗi: ${response.status}"
    } catch (e: Exception) {
        e.printStackTrace()
        "Lỗi kết nối Gemini API: ${e.message}"
    }
}

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    videoPath: String,
    videoTitle: String,
    isOnline: Boolean,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) { context as? Activity }
    val componentActivity = remember(context) { context as? ComponentActivity }
    val scope = rememberCoroutineScope()

    val mainViewModel = viewModel<com.example.mediaplayer.ui.main.MainScreenViewModel>(
        viewModelStoreOwner = context as androidx.activity.ComponentActivity
    )
    val activePlaylist by mainViewModel.activePlaylist.collectAsState()

    var currentVideoPath by remember { mutableStateOf(videoPath) }
    var currentVideoTitle by remember { mutableStateOf(videoTitle) }

    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.toFloat()
    val screenHeight = configuration.screenHeightDp.toFloat()

    val currentIndex = remember(activePlaylist, currentVideoPath) {
        activePlaylist.indexOfFirst { it.path == currentVideoPath }
    }

    fun playNextVideo() {
        if (activePlaylist.isNotEmpty() && currentIndex != -1) {
            val nextIdx = (currentIndex + 1) % activePlaylist.size
            val nextVideo = activePlaylist[nextIdx]
            currentVideoPath = nextVideo.path
            currentVideoTitle = nextVideo.title
        }
    }

    fun playPrevVideo() {
        if (activePlaylist.isNotEmpty() && currentIndex != -1) {
            val prevIdx = if (currentIndex - 1 < 0) activePlaylist.size - 1 else currentIndex - 1
            val prevVideo = activePlaylist[prevIdx]
            currentVideoPath = prevVideo.path
            currentVideoTitle = prevVideo.title
        }
    }

    // 1. Keep screen on during playback
    DisposableEffect(Unit) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            // Restore default orientation and screen bars on exit
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            activity?.let { act ->
                val window = act.window
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // 2. Initialize ExoPlayer
    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
        }
    }

    // Release player on exit
    DisposableEffect(player) {
        onDispose {
            player.release()
        }
    }

    LaunchedEffect(currentVideoPath) {
        player.stop()
        player.clearMediaItems()
        val mediaItem = if (currentVideoPath.contains("m3u8", ignoreCase = true)) {
            MediaItem.Builder()
                .setUri(Uri.parse(currentVideoPath))
                .setMimeType(androidx.media3.common.MimeTypes.APPLICATION_M3U8)
                .build()
        } else {
            MediaItem.fromUri(Uri.parse(currentVideoPath))
        }
        player.setMediaItem(mediaItem)
        player.prepare()
        player.play()
    }

    // 3. States for UI Controller
    var isPlaying by remember { mutableStateOf(true) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var totalDuration by remember { mutableLongStateOf(0L) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    var showControls by remember { mutableStateOf(true) }
    
    // Swipe gestures
    var showVolumeOverlay by remember { mutableStateOf(false) }
    var volumeLevel by remember { mutableStateOf(0) }
    var maxVolumeLevel by remember { mutableStateOf(15) }

    var showBrightnessOverlay by remember { mutableStateOf(false) }
    var brightnessLevel by remember { mutableStateOf(0.5f) }

    var showSeekOverlay by remember { mutableStateOf(false) }
    var seekOffsetLabel by remember { mutableStateOf("") }

    // Double tap feedbacks
    var showDoubleTapLeft by remember { mutableStateOf(false) }
    var showDoubleTapRight by remember { mutableStateOf(false) }

    // Screen controls lock
    var isLocked by remember { mutableStateOf(false) }

    // Screen configuration: orientation and fullscreen
    var isLandscape by remember { mutableStateOf(true) }
    var isFullscreen by remember { mutableStateOf(false) }

    // Aspect ratio selection
    var currentAspectRatioOption by remember { mutableStateOf(AspectRatioOption.FIT) }

    var isOrientationLocked by remember { mutableStateOf(false) }
    var videoWidth by remember { mutableIntStateOf(0) }
    var videoHeight by remember { mutableIntStateOf(0) }
    var showAudioTrackDialog by remember { mutableStateOf(false) }
    var autoNextCountdown by remember { mutableIntStateOf(-1) }

    // Swipe switch states
    var totalDragY by remember { mutableStateOf(0f) }
    var showSwitchVideoOverlay by remember { mutableStateOf(false) }
    var targetVideoTitle by remember { mutableStateOf("") }
    var isNextTarget by remember { mutableStateOf(true) }

    // Subtitle translation states
    var isSubtitlesEnabled by remember { mutableStateOf(true) }
    var isSimulatedSubtitlesEnabled by remember { mutableStateOf(true) }
    var isTranslateEnabled by remember { mutableStateOf(true) }
    var targetLanguage by remember { mutableStateOf("vi") }
    var showSubtitleSettingsDialog by remember { mutableStateOf(false) }

    // Gemini AI states
    var isGeminiActive by remember { mutableStateOf(false) }
    var geminiApiKey by remember { mutableStateOf("") }
    var showGeminiExplainDialog by remember { mutableStateOf(false) }
    var geminiExplainText by remember { mutableStateOf("") }
    var isGeminiExplaining by remember { mutableStateOf(false) }
    val prefs = remember(context) { context.getSharedPreferences("media_player_prefs", Context.MODE_PRIVATE) }

    // SRT File Manager states
    var showSrtManagerDialog by remember { mutableStateOf(false) }
    var srtFilesList by remember { mutableStateOf<List<com.example.mediaplayer.data.SrtFileItem>>(emptyList()) }
    var isSrtScanning by remember { mutableStateOf(false) }
    var srtSearchQuery by remember { mutableStateOf("") }

    var originalSubtitle by remember { mutableStateOf("") }
    var translatedSubtitle by remember { mutableStateOf("") }

    var customSrtCues by remember { mutableStateOf<List<SrtCue>>(emptyList()) }
    var isCustomSrtActive by remember { mutableStateOf(false) }

    val srtLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            scope.launch {
                try {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val srtText = inputStream?.bufferedReader()?.use { it.readText() } ?: ""
                    if (srtText.isNotBlank()) {
                        val parsed = parseSrtFile(srtText)
                        if (parsed.isNotEmpty()) {
                            customSrtCues = parsed
                            isSimulatedSubtitlesEnabled = false
                            isCustomSrtActive = true
                            originalSubtitle = ""
                            translatedSubtitle = ""
                            android.widget.Toast.makeText(context, "Đã nạp ${parsed.size} dòng phụ đề SRT!", android.widget.Toast.LENGTH_SHORT).show()
                        } else {
                            android.widget.Toast.makeText(context, "Không thể đọc hoặc định dạng SRT không hợp lệ!", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    android.widget.Toast.makeText(context, "Lỗi khi đọc file SRT!", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Picture in Picture state
    var isInPipMode by remember { mutableStateOf(false) }

    // Tap Water Bubbles Effect State
    val tapBubbles = remember { mutableStateListOf<TapBubble>() }
    val bubbleScope = rememberCoroutineScope()
    fun spawnBubble(offset: androidx.compose.ui.geometry.Offset) {
        bubbleScope.launch {
            val radius = androidx.compose.animation.core.Animatable(0f)
            val alpha = androidx.compose.animation.core.Animatable(0.7f)
            val bubble = TapBubble(offset.x, offset.y, radius, alpha)
            tapBubbles.add(bubble)
            launch {
                radius.animateTo(
                    targetValue = 180f,
                    animationSpec = androidx.compose.animation.core.tween(
                        durationMillis = 650,
                        easing = androidx.compose.animation.core.FastOutSlowInEasing
                    )
                )
            }
            launch {
                alpha.animateTo(
                    targetValue = 0f,
                    animationSpec = androidx.compose.animation.core.tween(
                        durationMillis = 650,
                        easing = androidx.compose.animation.core.LinearEasing
                    )
                )
            }
            delay(650)
            tapBubbles.remove(bubble)
        }
    }

    // Audio Manager for Volume controls
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    
    // Register PiP listener
    DisposableEffect(componentActivity) {
        if (componentActivity == null) return@DisposableEffect onDispose {}
        val listener = androidx.core.util.Consumer<androidx.core.app.PictureInPictureModeChangedInfo> { info ->
            isInPipMode = info.isInPictureInPictureMode
        }
        componentActivity.addOnPictureInPictureModeChangedListener(listener)
        onDispose {
            componentActivity.removeOnPictureInPictureModeChangedListener(listener)
        }
    }

    LaunchedEffect(Unit) {
        maxVolumeLevel = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        volumeLevel = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        
        // Initial brightness
        val lp = activity?.window?.attributes
        brightnessLevel = if (lp != null && lp.screenBrightness >= 0) lp.screenBrightness else 0.5f

        // Set initial orientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

        // Load Gemini Preferences
        geminiApiKey = prefs.getString("gemini_api_key", "") ?: ""
        isGeminiActive = prefs.getBoolean("is_gemini_active", false)
    }

    // Handle orientation updates
    LaunchedEffect(isLandscape, isOrientationLocked) {
        if (isOrientationLocked) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED
        } else {
            activity?.requestedOrientation = if (isLandscape) {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            } else {
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
        }
    }

    // Handle fullscreen updates
    LaunchedEffect(isFullscreen) {
        activity?.let { act ->
            val window = act.window
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            if (isFullscreen) {
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // Auto next countdown runner
    LaunchedEffect(autoNextCountdown) {
        if (autoNextCountdown > 0) {
            delay(1000L)
            autoNextCountdown -= 1
            if (autoNextCountdown == 0) {
                autoNextCountdown = -1
                playNextVideo()
            }
        }
    }

    // Periodic time updates
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            currentPosition = player.currentPosition
            totalDuration = player.duration
            delay(250L)
        }
    }

    // Auto-hide controls
    LaunchedEffect(showControls, isLocked) {
        if (showControls && !isLocked) {
            delay(4000L)
            showControls = false
        }
    }

    // Setup listener for play/pause states & subtitle cues
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                if (playing && autoNextCountdown != -1) {
                    autoNextCountdown = -1
                }
            }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    totalDuration = player.duration
                }
                if (state == Player.STATE_ENDED) {
                    if (activePlaylist.size > 1 && currentIndex != -1) {
                        autoNextCountdown = 3
                    }
                }
            }
            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    videoWidth = videoSize.width
                    videoHeight = videoSize.height
                }
            }
            override fun onCues(cueGroup: androidx.media3.common.text.CueGroup) {
                if (isSubtitlesEnabled && !isSimulatedSubtitlesEnabled && !isCustomSrtActive) {
                    val builder = java.lang.StringBuilder()
                    for (cue in cueGroup.cues) {
                        cue.text?.let { builder.append(it).append("\n") }
                    }
                    originalSubtitle = builder.toString().trim()
                } else if (!isSubtitlesEnabled) {
                    originalSubtitle = ""
                }
            }
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                android.widget.Toast.makeText(
                    context,
                    "Lỗi phát luồng HLS/M3U8: ${error.localizedMessage ?: error.message}",
                    android.widget.Toast.LENGTH_LONG
                ).show()
                isPlaying = false
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
        }
    }

    // Handle Custom SRT or Simulated Subtitles Cues based on video playback progress
    LaunchedEffect(currentPosition, isSimulatedSubtitlesEnabled, isCustomSrtActive, customSrtCues, isSubtitlesEnabled) {
        if (!isSubtitlesEnabled) {
            originalSubtitle = ""
            return@LaunchedEffect
        }
        if (isCustomSrtActive && customSrtCues.isNotEmpty()) {
            val currentCue = customSrtCues.firstOrNull { currentPosition in it.start..it.end }
            originalSubtitle = currentCue?.text ?: ""
        } else if (isSimulatedSubtitlesEnabled) {
            val matchingCue = SIMULATED_CAPTIONS.firstOrNull { currentPosition in it.first }
            originalSubtitle = matchingCue?.second ?: ""
        } else {
            originalSubtitle = ""
        }
    }

    // Handle Subtitle Translation
    LaunchedEffect(originalSubtitle, isTranslateEnabled, targetLanguage, isSubtitlesEnabled, isGeminiActive, geminiApiKey) {
        if (!isSubtitlesEnabled || originalSubtitle.isBlank()) {
            translatedSubtitle = ""
            return@LaunchedEffect
        }
        if (isTranslateEnabled) {
            translatedSubtitle = "..."
            var translated = ""
            if (isGeminiActive && geminiApiKey.isNotBlank()) {
                translated = translateWithGemini(originalSubtitle, targetLanguage, geminiApiKey)
            }
            if (translated.isBlank()) {
                translated = translateText(originalSubtitle, "en", targetLanguage)
            }
            translatedSubtitle = translated
        } else {
            translatedSubtitle = ""
        }
    }

    // Hide control overlays in Picture-in-Picture mode
    val finalShowControls = showControls && !isInPipMode

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            // Gesture handling: left half = brightness, right half = volume, horizontal = seek
            .pointerInput(isLocked, activePlaylist, currentIndex) {
                if (isLocked) {
                    detectTapGestures(
                        onTap = { showControls = !showControls }
                    )
                } else {
                    var dragDirectionChecked = false
                    var dragTypeState: DragType? = null
                    var startX = 0f
                    var startVolume = 0
                    var startBrightness = 0.5f
                    var startPositionSeek = 0L

                    detectDragGestures(
                        onDragStart = { offset ->
                            dragDirectionChecked = false
                            dragTypeState = null
                            startX = offset.x
                            startVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                            val lp = activity?.window?.attributes
                            startBrightness = if (lp != null && lp.screenBrightness >= 0) lp.screenBrightness else 0.5f
                            startPositionSeek = player.currentPosition
                            showControls = false
                            totalDragY = 0f
                        },
                        onDragEnd = {
                            if (dragTypeState == DragType.SEEK) {
                                player.seekTo(currentPosition)
                            }
                            if (dragTypeState == DragType.SWITCH_VIDEO) {
                                showSwitchVideoOverlay = false
                                if (abs(totalDragY) > 150) {
                                    if (totalDragY < 0) {
                                        playNextVideo()
                                    } else {
                                        playPrevVideo()
                                    }
                                }
                                totalDragY = 0f
                            }
                            showVolumeOverlay = false
                            showBrightnessOverlay = false
                            showSeekOverlay = false
                            dragTypeState = null
                        },
                        onDragCancel = {
                            showVolumeOverlay = false
                            showBrightnessOverlay = false
                            showSeekOverlay = false
                            showSwitchVideoOverlay = false
                            dragTypeState = null
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val width = size.width
                            val height = size.height
                            
                            if (!dragDirectionChecked) {
                                if (abs(dragAmount.x) > abs(dragAmount.y)) {
                                    dragTypeState = DragType.SEEK
                                    showSeekOverlay = true
                                } else {
                                    val isCenterZone = startX >= width * 0.35f && startX <= width * 0.65f
                                    if (isCenterZone) {
                                        dragTypeState = DragType.SWITCH_VIDEO
                                        showSwitchVideoOverlay = true
                                    } else if (startX < width / 2) {
                                        dragTypeState = DragType.BRIGHTNESS
                                        showBrightnessOverlay = true
                                    } else {
                                        dragTypeState = DragType.VOLUME
                                        showVolumeOverlay = true
                                    }
                                }
                                dragDirectionChecked = true
                            }

                            when (dragTypeState) {
                                DragType.SEEK -> {
                                    val totalDragX = change.position.x - startX
                                    val seekOffset = (totalDragX / width * 120000L).toLong()
                                    val target = max(0L, min(totalDuration, startPositionSeek + seekOffset))
                                    currentPosition = target
                                    seekOffsetLabel = "${if (seekOffset >= 0) "+" else ""}${seekOffset / 1000}s (${VideoScanner.formatDuration(target)})"
                                }
                                DragType.SWITCH_VIDEO -> {
                                    totalDragY += dragAmount.y
                                    showSwitchVideoOverlay = true
                                    if (totalDragY < 0) {
                                        isNextTarget = true
                                        if (activePlaylist.isNotEmpty() && currentIndex != -1) {
                                            val targetIdx = (currentIndex + 1) % activePlaylist.size
                                            targetVideoTitle = "Tiếp theo: ${activePlaylist[targetIdx].title}"
                                        } else {
                                            targetVideoTitle = "Không có tập tiếp theo"
                                        }
                                    } else {
                                        isNextTarget = false
                                        if (activePlaylist.isNotEmpty() && currentIndex != -1) {
                                            val targetIdx = if (currentIndex - 1 < 0) activePlaylist.size - 1 else currentIndex - 1
                                            targetVideoTitle = "Trước đó: ${activePlaylist[targetIdx].title}"
                                        } else {
                                            targetVideoTitle = "Không có tập trước đó"
                                        }
                                    }
                                }
                                DragType.VOLUME -> {
                                    val totalDragYVal = change.position.y - change.previousPosition.y
                                    val delta = -totalDragYVal / height
                                    val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                                    val deltaVol = (delta * maxVolumeLevel * 2.0f).toInt()
                                    val nextVol = min(maxVolumeLevel, max(0, currentVol + deltaVol))
                                    if (nextVol != currentVol) {
                                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, nextVol, 0)
                                        volumeLevel = nextVol
                                    }
                                }
                                DragType.BRIGHTNESS -> {
                                    val totalDragYVal = change.position.y - change.previousPosition.y
                                    val delta = -totalDragYVal / height
                                    val lp = activity?.window?.attributes
                                    val current = if (lp != null && lp.screenBrightness >= 0) lp.screenBrightness else 0.5f
                                    val next = min(1.0f, max(0.0f, current + delta * 2.0f))
                                    brightnessLevel = next
                                    lp?.screenBrightness = next
                                    activity?.window?.attributes = lp
                                }
                                null -> {}
                            }
                        }
                    )
                }
            }
            .pointerInput(isLocked) {
                if (!isLocked) {
                    detectTapGestures(
                        onDoubleTap = { offset ->
                            spawnBubble(offset)
                            val width = size.width
                            if (offset.x < width / 2) {
                                // Double tap left -> rewind 10s
                                val target = max(0L, player.currentPosition - 10000L)
                                player.seekTo(target)
                                currentPosition = target
                                showDoubleTapLeft = true
                                scope.launch {
                                    delay(650L)
                                    showDoubleTapLeft = false
                                }
                            } else {
                                // Double tap right -> fast forward 10s
                                val target = min(player.duration, player.currentPosition + 10000L)
                                player.seekTo(target)
                                currentPosition = target
                                showDoubleTapRight = true
                                scope.launch {
                                    delay(650L)
                                    showDoubleTapRight = false
                                }
                            }
                        },
                        onTap = { offset ->
                            spawnBubble(offset)
                            showControls = !showControls
                        }
                    )
                }
            }
    ) {
        // 4. Video Render Surface (Media3 PlayerView with custom aspect ratio constraints)
        val selectedRatio = currentAspectRatioOption.ratio
        val calculatedResizeMode = remember(currentAspectRatioOption, videoWidth, videoHeight, screenWidth, screenHeight) {
            if (currentAspectRatioOption == AspectRatioOption.SMART && videoWidth > 0 && videoHeight > 0) {
                val videoAspect = videoWidth.toFloat() / videoHeight.toFloat()
                val screenAspect = screenWidth / screenHeight
                val diff = abs(videoAspect - screenAspect)
                if (diff < 0.15f) {
                    androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                } else if (videoAspect > 2.0f && screenAspect > 1.5f) {
                    androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                } else {
                    androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            } else {
                when (currentAspectRatioOption) {
                    AspectRatioOption.FIT -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                    AspectRatioOption.ZOOM -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    AspectRatioOption.STRETCH -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL
                    else -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (selectedRatio != null) {
                        Modifier.aspectRatio(selectedRatio)
                    } else {
                        Modifier.fillMaxSize()
                    }
                )
                .align(Alignment.Center)
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        this.player = player
                        this.subtitleView?.let { it.visibility = android.view.View.GONE }
                    }
                },
                update = { playerView ->
                    playerView.resizeMode = calculatedResizeMode
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Water Bubble Tap Overlay Canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            tapBubbles.forEach { bubble ->
                // Outer ring
                drawCircle(
                    color = Color(0xFF80DEEA).copy(alpha = bubble.alpha.value), // Cyan 200
                    radius = bubble.radius.value,
                    center = androidx.compose.ui.geometry.Offset(bubble.x, bubble.y),
                    style = Stroke(width = 4f)
                )
                // Inner ring
                drawCircle(
                    color = Color.White.copy(alpha = bubble.alpha.value * 0.6f),
                    radius = bubble.radius.value * 0.6f,
                    center = androidx.compose.ui.geometry.Offset(bubble.x, bubble.y),
                    style = Stroke(width = 2.5f)
                )
                // Center glow
                drawCircle(
                    color = Color(0xFF00E5FF).copy(alpha = bubble.alpha.value * 0.2f), // Cyan Accent
                    radius = bubble.radius.value * 0.2f,
                    center = androidx.compose.ui.geometry.Offset(bubble.x, bubble.y)
                )
            }
        }

        // Double-tap visual feedbacks (Sleek left/right glowing skip alerts)
        AnimatedVisibility(
            visible = showDoubleTapLeft,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.4f)
                .align(Alignment.CenterStart)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = 0.15f))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.FastRewind, contentDescription = null, tint = Color.White, modifier = Modifier.size(48.dp))
                    Text("-10 giây", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }

        AnimatedVisibility(
            visible = showDoubleTapRight,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.4f)
                .align(Alignment.CenterEnd)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = 0.15f))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.FastForward, contentDescription = null, tint = Color.White, modifier = Modifier.size(48.dp))
                    Text("+10 giây", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }

        // Subtitles Overlay (High visibility text: customizable Compose subtitle view)
        if (!isInPipMode && isSubtitlesEnabled) {
            val showSubtitle = (isTranslateEnabled && translatedSubtitle.isNotBlank()) || originalSubtitle.isNotBlank()
            if (showSubtitle) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(bottom = if (finalShowControls) 110.dp else 40.dp)
                        .padding(horizontal = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (finalShowControls && (originalSubtitle.isNotBlank() || translatedSubtitle.isNotBlank())) {
                            AssistChip(
                                onClick = {
                                    isGeminiExplaining = true
                                    showGeminiExplainDialog = true
                                    scope.launch {
                                        val textToExplain = if (originalSubtitle.isNotBlank()) originalSubtitle else translatedSubtitle
                                        geminiExplainText = explainSubtitleWithGemini(textToExplain, targetLanguage, geminiApiKey)
                                        isGeminiExplaining = false
                                    }
                                },
                                label = { Text("AI Giải thích", color = Color(0xFF4ECCA3), fontWeight = FontWeight.Bold, fontSize = 11.sp) },
                                leadingIcon = { Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color(0xFF4ECCA3), modifier = Modifier.size(14.dp)) },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = Color.Black.copy(alpha = 0.8f),
                                    labelColor = Color(0xFF4ECCA3)
                                ),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF4ECCA3).copy(alpha = 0.5f)),
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.7f), shape = RoundedCornerShape(8.dp))
                                .border(1.dp, Color.White.copy(alpha = 0.15f), shape = RoundedCornerShape(8.dp))
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                        if (isTranslateEnabled && translatedSubtitle.isNotBlank()) {
                            Text(
                                text = translatedSubtitle,
                                color = Color(0xFFFDD835), // Sleek golden yellow
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            if (originalSubtitle.isNotBlank() && originalSubtitle != translatedSubtitle) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = originalSubtitle,
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Normal,
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else {
                            Text(
                                text = originalSubtitle,
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }

        // 5. Volume Gesture Display
        if (showVolumeOverlay) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black.copy(alpha = 0.75f))
                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = if (volumeLevel == 0) Icons.Default.VolumeMute else Icons.Default.VolumeUp,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Âm lượng",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp
                    )
                    Text(
                        text = "${(volumeLevel * 100 / maxVolumeLevel)}%",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }
        }

        // 6. Brightness Gesture Display
        if (showBrightnessOverlay) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black.copy(alpha = 0.75f))
                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Brightness5,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Độ sáng",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp
                    )
                    Text(
                        text = "${(brightnessLevel * 100).toInt()}%",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }
        }

        // 7. Swipe Seek Overlay
        if (showSeekOverlay) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black.copy(alpha = 0.75f))
                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.FastForward,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Tua video",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp
                    )
                    Text(
                        text = seekOffsetLabel,
                        color = Color(0xFF64B5F6),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }
        }

        // 8. Custom Controller Overlay
        AnimatedVisibility(
            visible = finalShowControls,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
            ) {
                if (isLocked) {
                    // Lock-only Screen Overlay
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        IconButton(
                            onClick = { isLocked = false },
                            modifier = Modifier
                                .size(50.dp)
                                .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape)
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = "Mở khóa màn hình", tint = Color.White)
                        }
                    }
                } else {
                    // Regular Control Overlay

                    // Top Bar (Back, Title, Subtitles, PiP, Touch Lock)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .background(Color.Black.copy(alpha = 0.6f))
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Quay lại", tint = Color.White)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = currentVideoTitle,
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            modifier = Modifier.weight(1f)
                        )

                        // TV Screen Mirroring (Cast / Smart View)
                        IconButton(onClick = {
                            try {
                                val intent = android.content.Intent(android.provider.Settings.ACTION_CAST_SETTINGS)
                                intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                try {
                                    val intent = android.content.Intent("android.settings.WIFI_DISPLAY_SETTINGS")
                                    intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                                    context.startActivity(intent)
                                } catch (e2: Exception) {
                                    android.widget.Toast.makeText(context, "Thiết bị không hỗ trợ truyền màn hình!", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Default.Cast,
                                contentDescription = "Mirror Screen / Smart View",
                                tint = Color.White
                            )
                        }

                        // Audio Track Selector Button
                        IconButton(onClick = { showAudioTrackDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Audiotrack,
                                contentDescription = "Chọn track âm thanh",
                                tint = Color.White
                            )
                        }

                        // Subtitle Settings Button
                        IconButton(onClick = { showSubtitleSettingsDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Subtitles,
                                contentDescription = "Cài đặt phụ đề",
                                tint = if (isTranslateEnabled) Color(0xFFFDD835) else Color.White
                            )
                        }

                        // Picture in Picture Button
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N &&
                            activity != null &&
                            activity.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)
                        ) {
                            IconButton(onClick = {
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                    val builder = android.app.PictureInPictureParams.Builder()
                                    val format = player.videoFormat
                                    if (format != null && format.width > 0 && format.height > 0) {
                                        val aspect = format.width.toFloat() / format.height.toFloat()
                                        if (aspect in 0.4184f..2.39f) {
                                            builder.setAspectRatio(android.util.Rational(format.width, format.height))
                                        }
                                    }
                                    activity.enterPictureInPictureMode(builder.build())
                                } else {
                                    @Suppress("DEPRECATION")
                                    activity.enterPictureInPictureMode()
                                }
                            }) {
                                Icon(Icons.Default.PictureInPicture, contentDescription = "Xem ảnh trong ảnh", tint = Color.White)
                            }
                        }

                        // Lock Button
                        IconButton(onClick = { isLocked = true }) {
                            Icon(Icons.Default.LockOpen, contentDescription = "Khóa điều khiển", tint = Color.White)
                        }
                    }

                    // Middle controls: Prev, Rewind, Play/Pause, Forward, Next
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Skip Previous
                        IconButton(
                            onClick = { playPrevVideo() },
                            enabled = activePlaylist.size > 1,
                            modifier = Modifier
                                .size(48.dp)
                                .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipPrevious,
                                contentDescription = "Tập trước",
                                tint = if (activePlaylist.size > 1) Color.White else Color.Gray,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        // Rewind 10s
                        IconButton(
                            onClick = {
                                val target = max(0, player.currentPosition - 10000)
                                player.seekTo(target)
                                currentPosition = target
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                        ) {
                            Icon(Icons.Default.Replay10, contentDescription = "Tua lại 10 giây", tint = Color.White, modifier = Modifier.size(24.dp))
                        }

                        // Play/Pause
                        Box(
                            modifier = Modifier
                                .size(76.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                                .border(2.dp, Color.White.copy(alpha = 0.3f), CircleShape)
                                .clickable {
                                    if (player.playbackState == Player.STATE_ENDED) {
                                        player.seekTo(0)
                                        player.play()
                                    } else {
                                        if (player.isPlaying) {
                                            player.pause()
                                        } else {
                                            player.play()
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Tạm dừng" else "Tiếp tục",
                                tint = Color.White,
                                modifier = Modifier.size(38.dp)
                            )
                        }

                        // Forward 10s
                        IconButton(
                            onClick = {
                                val target = min(player.duration, player.currentPosition + 10000)
                                player.seekTo(target)
                                currentPosition = target
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                        ) {
                            Icon(Icons.Default.Forward10, contentDescription = "Tua tới 10 giây", tint = Color.White, modifier = Modifier.size(24.dp))
                        }

                        // Skip Next
                        IconButton(
                            onClick = { playNextVideo() },
                            enabled = activePlaylist.size > 1,
                            modifier = Modifier
                                .size(48.dp)
                                .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = "Tập tiếp theo",
                                tint = if (activePlaylist.size > 1) Color.White else Color.Gray,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    // Bottom bar: Seek bar & controls (Aspect Ratio, Speed, Rotate, Fullscreen)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .background(Color.Black.copy(alpha = 0.6f))
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        // Slider & Time Display
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = VideoScanner.formatDuration(currentPosition),
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            
                            // Seek Slider
                            Slider(
                                value = if (totalDuration > 0) currentPosition.toFloat() else 0f,
                                onValueChange = { value ->
                                    scope.launch {
                                        player.seekTo(value.toLong())
                                        currentPosition = value.toLong()
                                    }
                                },
                                valueRange = 0f..max(1f, totalDuration.toFloat()),
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                                ),
                                modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                            )

                            Text(
                                text = VideoScanner.formatDuration(if (totalDuration < 0) 0L else totalDuration),
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Controls Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Left-side bottom controls: Speed and Aspect Ratio
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Playback Speed
                                TextButton(
                                    onClick = {
                                        playbackSpeed = when (playbackSpeed) {
                                            1.0f -> 1.25f
                                            1.25f -> 1.5f
                                            1.5f -> 2.0f
                                            2.0f -> 0.5f
                                            0.5f -> 0.75f
                                            else -> 1.0f
                                        }
                                        player.playbackParameters = PlaybackParameters(playbackSpeed)
                                    },
                                    colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
                                    modifier = Modifier.background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                                ) {
                                    Text(
                                        text = "Tốc độ: ${playbackSpeed}x",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }

                                // Aspect Ratio Selector
                                TextButton(
                                    onClick = {
                                        val nextOption = when (currentAspectRatioOption) {
                                            AspectRatioOption.FIT -> AspectRatioOption.ZOOM
                                            AspectRatioOption.ZOOM -> AspectRatioOption.STRETCH
                                            AspectRatioOption.STRETCH -> AspectRatioOption.RATIO_16_9
                                            AspectRatioOption.RATIO_16_9 -> AspectRatioOption.RATIO_21_9
                                            AspectRatioOption.RATIO_21_9 -> AspectRatioOption.SMART
                                            AspectRatioOption.SMART -> AspectRatioOption.FIT
                                        }
                                        currentAspectRatioOption = nextOption
                                    },
                                    colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
                                    modifier = Modifier.background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                                ) {
                                    Icon(Icons.Default.AspectRatio, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Tỷ lệ: ${currentAspectRatioOption.label}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            }

                            // Right-side bottom controls: Orientation & Fullscreen
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Rotate Orientation Button
                                IconButton(
                                    onClick = { isLandscape = !isLandscape },
                                    enabled = !isOrientationLocked,
                                    modifier = Modifier.background(
                                        if (isOrientationLocked) Color.White.copy(alpha = 0.05f)
                                        else Color.White.copy(alpha = 0.1f),
                                        CircleShape
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ScreenRotation,
                                        contentDescription = "Xoay màn hình",
                                        tint = if (isOrientationLocked) Color.Gray else Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                // Lock Rotation Button
                                IconButton(
                                    onClick = { isOrientationLocked = !isOrientationLocked },
                                    modifier = Modifier.background(
                                        if (isOrientationLocked) MaterialTheme.colorScheme.primary
                                        else Color.White.copy(alpha = 0.1f),
                                        CircleShape
                                    )
                                ) {
                                    Icon(
                                        imageVector = if (isOrientationLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                                        contentDescription = "Khóa xoay tự động",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                // Fullscreen Button
                                IconButton(
                                    onClick = { isFullscreen = !isFullscreen },
                                    modifier = Modifier.background(Color.White.copy(alpha = 0.1f), CircleShape)
                                ) {
                                    Icon(
                                        imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                        contentDescription = "Toàn màn hình",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Subtitle & Translation Settings Dialog
        if (showSubtitleSettingsDialog) {
            AlertDialog(
                onDismissRequest = { showSubtitleSettingsDialog = false },
                title = { Text("Cấu hình Phụ đề & Dịch thuật", color = Color.White, fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        // Global Subtitle Switch
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Hiển thị phụ đề", color = Color.White, fontWeight = FontWeight.Bold)
                            Switch(
                                checked = isSubtitlesEnabled,
                                onCheckedChange = { isSubtitlesEnabled = it }
                            )
                        }

                        if (isSubtitlesEnabled) {
                            // Custom SRT import button
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (isCustomSrtActive) "Đã nạp SRT (${customSrtCues.size} dòng)" else "Nạp phụ đề (.srt) từ máy",
                                    color = if (isCustomSrtActive) Color(0xFF64B5F6) else Color.White,
                                    fontSize = 14.sp
                                )
                                Button(
                                    onClick = { srtLauncher.launch("*/*") },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text("Chọn file SRT", fontSize = 12.sp)
                                }
                            }

                            if (isCustomSrtActive) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Đang sử dụng phụ đề ngoài", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                                    TextButton(
                                        onClick = {
                                            isCustomSrtActive = false
                                            customSrtCues = emptyList()
                                            originalSubtitle = ""
                                            translatedSubtitle = ""
                                        },
                                        colors = ButtonDefaults.textButtonColors(contentColor = Color.Red),
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text("Gỡ bỏ file", fontSize = 12.sp)
                                    }
                                }
                            }

                            // Simulated captions switch
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Bật phụ đề mô phỏng (Demo)", color = Color.White)
                                Switch(
                                    checked = isSimulatedSubtitlesEnabled,
                                    onCheckedChange = { isSimulatedSubtitlesEnabled = it }
                                )
                            }

                            // SRT File Manager option
                            Divider(color = Color.White.copy(alpha = 0.15f))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Quản lý phụ đề (.srt) thiết bị", color = Color.White)
                                Button(
                                    onClick = {
                                        showSubtitleSettingsDialog = false
                                        showSrtManagerDialog = true
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Quản lý", fontSize = 12.sp)
                                }
                            }

                            // Translate switch
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Dịch tự động (Auto Translate)", color = Color.White)
                                Switch(
                                    checked = isTranslateEnabled,
                                    onCheckedChange = { isTranslateEnabled = it }
                                )
                            }

                            // Gemini Translation config
                            Divider(color = Color.White.copy(alpha = 0.15f))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color(0xFF4ECCA3), modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Dịch bằng Gemini AI", color = Color.White)
                                }
                                Switch(
                                    checked = isGeminiActive,
                                    onCheckedChange = {
                                        isGeminiActive = it
                                        prefs.edit().putBoolean("is_gemini_active", it).apply()
                                    }
                                )
                            }

                            if (isGeminiActive) {
                                OutlinedTextField(
                                    value = geminiApiKey,
                                    onValueChange = {
                                        geminiApiKey = it
                                        prefs.edit().putString("gemini_api_key", it).apply()
                                    },
                                    label = { Text("Gemini API Key", color = Color.White.copy(alpha = 0.6f)) },
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedBorderColor = Color(0xFF4ECCA3),
                                        unfocusedBorderColor = Color.White.copy(alpha = 0.3f)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            Divider(color = Color.White.copy(alpha = 0.15f))

                            // Language Selection
                            if (isTranslateEnabled) {
                                Text("Dịch sang ngôn ngữ:", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                                
                                var showLangDropdown by remember { mutableStateOf(false) }
                                val currentLangName = TRANSLATION_LANGUAGES.firstOrNull { it.first == targetLanguage }?.second ?: targetLanguage
                                
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    OutlinedButton(
                                        onClick = { showLangDropdown = true },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                                    ) {
                                        Text(currentLangName)
                                    }
                                    
                                    DropdownMenu(
                                        expanded = showLangDropdown,
                                        onDismissRequest = { showLangDropdown = false },
                                        modifier = Modifier.fillMaxWidth(0.8f).background(Color(0xFF212121))
                                    ) {
                                        TRANSLATION_LANGUAGES.forEach { lang ->
                                            DropdownMenuItem(
                                                text = { Text(lang.second, color = Color.White) },
                                                onClick = {
                                                    targetLanguage = lang.first
                                                    showLangDropdown = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSubtitleSettingsDialog = false }) {
                        Text("Xác nhận", fontWeight = FontWeight.Bold)
                    }
                },
                containerColor = Color(0xFF1E1E1E),
                textContentColor = Color.White
            )
        }

        // Gemini AI Explain Dialog
        if (showGeminiExplainDialog) {
            AlertDialog(
                onDismissRequest = { showGeminiExplainDialog = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color(0xFF4ECCA3))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("AI Giải thích phụ đề", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 250.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        if (isGeminiExplaining) {
                            Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(color = Color(0xFF4ECCA3))
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text("AI đang phân tích...", color = Color.White.copy(alpha = 0.7f))
                                }
                            }
                        } else {
                            Text(
                                text = geminiExplainText,
                                color = Color.White,
                                fontSize = 14.sp,
                                lineHeight = 20.sp
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showGeminiExplainDialog = false }) {
                        Text("Đóng", color = Color(0xFF4ECCA3), fontWeight = FontWeight.Bold)
                    }
                },
                containerColor = Color(0xFF1E1E1E),
                textContentColor = Color.White
            )
        }

        // SRT File Manager Dialog
        if (showSrtManagerDialog) {
            // Trigger scan initially
            LaunchedEffect(Unit) {
                isSrtScanning = true
                scope.launch(Dispatchers.IO) {
                    val files = com.example.mediaplayer.data.SrtScanner.scanSrtFiles(context)
                    withContext(Dispatchers.Main) {
                        srtFilesList = files
                        isSrtScanning = false
                    }
                }
            }

            AlertDialog(
                onDismissRequest = { showSrtManagerDialog = false },
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Subtitles, contentDescription = null, tint = Color(0xFF4ECCA3))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Quản lý phụ đề SRT", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        }
                        IconButton(
                            onClick = {
                                isSrtScanning = true
                                scope.launch(Dispatchers.IO) {
                                    val files = com.example.mediaplayer.data.SrtScanner.scanSrtFiles(context)
                                    withContext(Dispatchers.Main) {
                                        srtFilesList = files
                                        isSrtScanning = false
                                    }
                                }
                            }
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Quét lại", tint = Color(0xFF4ECCA3))
                        }
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = srtSearchQuery,
                            onValueChange = { srtSearchQuery = it },
                            placeholder = { Text("Tìm kiếm file .srt...", color = Color.White.copy(alpha = 0.5f)) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.White.copy(alpha = 0.5f)) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF4ECCA3),
                                unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 280.dp)
                        ) {
                            if (isSrtScanning) {
                                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = Color(0xFF4ECCA3))
                            } else {
                                val filtered = srtFilesList.filter { it.name.contains(srtSearchQuery, ignoreCase = true) }
                                if (filtered.isEmpty()) {
                                    Text(
                                        text = "Không tìm thấy file .srt nào trong bộ nhớ.",
                                        color = Color.White.copy(alpha = 0.5f),
                                        fontSize = 13.sp,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.align(Alignment.Center).padding(24.dp)
                                    )
                                } else {
                                    LazyColumn(
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        items(filtered) { item ->
                                            Card(
                                                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        scope.launch {
                                                            try {
                                                                val file = java.io.File(item.path)
                                                                val srtText = file.readText()
                                                                val parsed = parseSrtFile(srtText)
                                                                if (parsed.isNotEmpty()) {
                                                                    customSrtCues = parsed
                                                                    isSimulatedSubtitlesEnabled = false
                                                                    isCustomSrtActive = true
                                                                    originalSubtitle = ""
                                                                    translatedSubtitle = ""
                                                                    showSrtManagerDialog = false
                                                                    android.widget.Toast.makeText(context, "Đã nạp phụ đề: ${item.name}", android.widget.Toast.LENGTH_SHORT).show()
                                                                } else {
                                                                    android.widget.Toast.makeText(context, "File srt trống hoặc không hợp lệ!", android.widget.Toast.LENGTH_SHORT).show()
                                                                }
                                                            } catch (e: Exception) {
                                                                e.printStackTrace()
                                                                android.widget.Toast.makeText(context, "Lỗi đọc file srt!", android.widget.Toast.LENGTH_SHORT).show()
                                                            }
                                                        }
                                                    }
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(10.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(Icons.Default.Subtitles, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(24.dp))
                                                    Spacer(modifier = Modifier.width(10.dp))
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(item.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                        Text(
                                                            text = "${item.path} (${com.example.mediaplayer.data.VideoScanner.formatSize(item.size)})",
                                                            color = Color.White.copy(alpha = 0.5f),
                                                            fontSize = 10.sp,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                    IconButton(
                                                        onClick = {
                                                            scope.launch(Dispatchers.IO) {
                                                                try {
                                                                    val file = java.io.File(item.path)
                                                                    if (file.exists()) {
                                                                        file.delete()
                                                                    }
                                                                    val updated = com.example.mediaplayer.data.SrtScanner.scanSrtFiles(context)
                                                                    withContext(Dispatchers.Main) {
                                                                        srtFilesList = updated
                                                                        android.widget.Toast.makeText(context, "Đã xóa file!", android.widget.Toast.LENGTH_SHORT).show()
                                                                    }
                                                                } catch (e: Exception) {
                                                                    e.printStackTrace()
                                                                    withContext(Dispatchers.Main) {
                                                                        android.widget.Toast.makeText(context, "Không thể xóa file!", android.widget.Toast.LENGTH_SHORT).show()
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    ) {
                                                        Icon(Icons.Default.Delete, contentDescription = "Xóa file", tint = Color.Red.copy(alpha = 0.8f), modifier = Modifier.size(18.dp))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSrtManagerDialog = false }) {
                        Text("Đóng", color = Color(0xFF4ECCA3), fontWeight = FontWeight.Bold)
                    }
                },
                containerColor = Color(0xFF1E1E1E),
                textContentColor = Color.White
            )
        }
    }

    // 10. Switch Video Overlay Display
    if (showSwitchVideoOverlay) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 80.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black.copy(alpha = 0.75f))
                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = if (isNextTarget) Icons.Default.SkipNext else Icons.Default.SkipPrevious,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (isNextTarget) "Vuốt lên để chuyển tiếp" else "Vuốt xuống để chuyển lùi",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp
                    )
                    Text(
                        text = targetVideoTitle,
                        color = Color(0xFF81C784),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 240.dp)
                    )
                }
            }
        }
    }

    // 11. Auto-Next Countdown Overlay
    if (autoNextCountdown != -1) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f)),
            contentAlignment = Alignment.Center
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.padding(24.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Tự động chuyển tập sau",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "$autoNextCountdown",
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 24.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    TextButton(
                        onClick = { autoNextCountdown = -1 }
                    ) {
                        Text("Hủy", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // 12. Audio Track Selection Dialog
    if (showAudioTrackDialog) {
        val audioTracks = remember(player, showAudioTrackDialog) { getAudioTracks(player) }
        AlertDialog(
            onDismissRequest = { showAudioTrackDialog = false },
            title = { Text("Chọn nguồn âm thanh", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    if (audioTracks.isEmpty()) {
                        Text("Không tìm thấy track âm thanh bổ sung.", color = Color.White.copy(alpha = 0.5f))
                    } else {
                        audioTracks.forEach { track ->
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (track.isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                                     else Color.White.copy(alpha = 0.05f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable {
                                        selectAudioTrack(player, track)
                                        showAudioTrackDialog = false
                                        android.widget.Toast.makeText(context, "Đã chuyển đổi kênh âm thanh", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Audiotrack,
                                        contentDescription = null,
                                        tint = if (track.isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.6f)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = getAudioTrackLabel(track.format, track.trackIndex),
                                        color = Color.White,
                                        fontWeight = if (track.isSelected) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAudioTrackDialog = false }) {
                    Text("Đóng", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = Color(0xFF1E1E1E),
            textContentColor = Color.White
        )
    }
}

data class AudioTrackInfo(
    val groupIndex: Int,
    val trackIndex: Int,
    val format: androidx.media3.common.Format,
    val isSelected: Boolean
)

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
fun getAudioTracks(player: ExoPlayer): List<AudioTrackInfo> {
    val tracksList = mutableListOf<AudioTrackInfo>()
    val currentTracks = player.currentTracks
    val groups = currentTracks.groups
    
    for (i in 0 until groups.size) {
        val group = groups[i]
        if (group.type == androidx.media3.common.C.TRACK_TYPE_AUDIO) {
            for (j in 0 until group.length) {
                val format = group.getTrackFormat(j)
                val isSelected = group.isTrackSelected(j)
                tracksList.add(AudioTrackInfo(i, j, format, isSelected))
            }
        }
    }
    return tracksList
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
fun selectAudioTrack(player: ExoPlayer, trackInfo: AudioTrackInfo) {
    val currentTracks = player.currentTracks
    val groups = currentTracks.groups
    val targetGroup = groups[trackInfo.groupIndex]
    
    player.trackSelectionParameters = player.trackSelectionParameters
        .buildUpon()
        .clearOverridesOfType(androidx.media3.common.C.TRACK_TYPE_AUDIO)
        .addOverride(androidx.media3.common.TrackSelectionOverride(targetGroup.mediaTrackGroup, trackInfo.trackIndex))
        .build()
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
fun getAudioTrackLabel(format: androidx.media3.common.Format, index: Int): String {
    val lang = format.language ?: "vn"
    val channels = when (format.channelCount) {
        1 -> "Mono"
        2 -> "Stereo"
        6 -> "5.1 Surround"
        else -> "${format.channelCount} Channels"
    }
    val bitRateStr = if (format.bitrate > 0) " | ${format.bitrate / 1000} kbps" else ""
    val mime = format.sampleMimeType?.substringAfter("/")?.uppercase() ?: "Unknown"
    return "Track ${index + 1}: ${lang.uppercase()} [$mime, $channels$bitRateStr]"
}
