package com.example.mediaplayer.ui.mixer

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.mediaplayer.data.DjMixerManager
import com.example.mediaplayer.data.MusicItem
import com.example.mediaplayer.ui.main.MainScreenViewModel
import kotlinx.coroutines.delay
import kotlin.random.Random

@Composable
fun DjMixerScreen(viewModel: MainScreenViewModel) {
    val context = LocalContext.current
    
    // Initialize Mixer Players
    LaunchedEffect(Unit) {
        DjMixerManager.init(context)
        viewModel.scanMusic(context)
    }

    // Observe state from DjMixerManager
    val songA by DjMixerManager.currentSongA.collectAsState()
    val songB by DjMixerManager.currentSongB.collectAsState()

    val isPlayingA by DjMixerManager.isPlayingA.collectAsState()
    val isPlayingB by DjMixerManager.isPlayingB.collectAsState()

    val volumeA by DjMixerManager.volumeA.collectAsState()
    val volumeB by DjMixerManager.volumeB.collectAsState()

    val crossfader by DjMixerManager.crossfader.collectAsState()

    val pitchA by DjMixerManager.pitchA.collectAsState()
    val pitchB by DjMixerManager.pitchB.collectAsState()

    val positionA by DjMixerManager.positionA.collectAsState()
    val durationA by DjMixerManager.durationA.collectAsState()

    val positionB by DjMixerManager.positionB.collectAsState()
    val durationB by DjMixerManager.durationB.collectAsState()

    // Dialog state
    var showLoadDialogA by remember { mutableStateOf(false) }
    var showLoadDialogB by remember { mutableStateOf(false) }

    // Dynamic rotation angle tracking for turntable speeds
    var rotationAngleA by remember { mutableStateOf(0f) }
    var rotationAngleB by remember { mutableStateOf(0f) }

    LaunchedEffect(isPlayingA, pitchA) {
        if (isPlayingA) {
            var lastTime = System.currentTimeMillis()
            while (isPlayingA) {
                val now = System.currentTimeMillis()
                val elapsed = now - lastTime
                lastTime = now
                // 33.3 RPM means 1 rotation every 1.8 seconds (1800 ms) at pitch = 1.0
                val delta = (elapsed / 1800f * 360f * pitchA)
                rotationAngleA = (rotationAngleA + delta) % 360f
                delay(16)
            }
        }
    }

    LaunchedEffect(isPlayingB, pitchB) {
        if (isPlayingB) {
            var lastTime = System.currentTimeMillis()
            while (isPlayingB) {
                val now = System.currentTimeMillis()
                val elapsed = now - lastTime
                lastTime = now
                val delta = (elapsed / 1800f * 360f * pitchB)
                rotationAngleB = (rotationAngleB + delta) % 360f
                delay(16)
            }
        }
    }

    // Glassmorphic background
    val darkGradient = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF0D0D1E),
            Color(0xFF14142B),
            Color(0xFF090914)
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(darkGradient)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Header Title
            Text(
                text = "PRODUCER DJ MIXER",
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                color = Color(0xFF00FFCC),
                letterSpacing = 2.sp,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Split layout for Deck A and Deck B
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(490.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Deck A (Left)
                Box(modifier = Modifier.weight(1f)) {
                    DjDeck(
                        deckName = "DECK A",
                        song = songA,
                        isPlaying = isPlayingA,
                        volume = volumeA,
                        pitch = pitchA,
                        position = positionA,
                        duration = durationA,
                        rotationAngle = rotationAngleA,
                        accentColor = Color(0xFF00FFCC),
                        onLoadClick = { showLoadDialogA = true },
                        onPlayPauseClick = { DjMixerManager.togglePlayPauseA() },
                        onVolumeChange = { DjMixerManager.setVolumeA(it) },
                        onPitchChange = { DjMixerManager.setPitchA(it) },
                        onSeekChange = { DjMixerManager.seekA(it) },
                        onEqBassChange = { DjMixerManager.setEqBassA(it) },
                        onEqMidChange = { DjMixerManager.setEqMidA(it) },
                        onEqTrebleChange = { DjMixerManager.setEqTrebleA(it) }
                    )
                }

                // Deck B (Right)
                Box(modifier = Modifier.weight(1f)) {
                    DjDeck(
                        deckName = "DECK B",
                        song = songB,
                        isPlaying = isPlayingB,
                        volume = volumeB,
                        pitch = pitchB,
                        position = positionB,
                        duration = durationB,
                        rotationAngle = rotationAngleB,
                        accentColor = Color(0xFFFF007F),
                        onLoadClick = { showLoadDialogB = true },
                        onPlayPauseClick = { DjMixerManager.togglePlayPauseB() },
                        onVolumeChange = { DjMixerManager.setVolumeB(it) },
                        onPitchChange = { DjMixerManager.setPitchB(it) },
                        onSeekChange = { DjMixerManager.seekB(it) },
                        onEqBassChange = { DjMixerManager.setEqBassB(it) },
                        onEqMidChange = { DjMixerManager.setEqMidB(it) },
                        onEqTrebleChange = { DjMixerManager.setEqTrebleB(it) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Center Panel: Combined Waveform & Crossfader
            CenterMixerControls(
                crossfader = crossfader,
                isPlayingA = isPlayingA,
                isPlayingB = isPlayingB,
                onCrossfaderChange = { DjMixerManager.setCrossfader(it) }
            )
        }
    }

    // Song Selection Dialogs
    if (showLoadDialogA) {
        SongPickerDialog(
            title = "Nạp Nhạc Vào Deck A",
            songs = viewModel.music.collectAsState().value,
            onDismiss = { showLoadDialogA = false },
            onSongSelected = {
                DjMixerManager.loadSongA(it)
                showLoadDialogA = false
            }
        )
    }

    if (showLoadDialogB) {
        SongPickerDialog(
            title = "Nạp Nhạc Vào Deck B",
            songs = viewModel.music.collectAsState().value,
            onDismiss = { showLoadDialogB = false },
            onSongSelected = {
                DjMixerManager.loadSongB(it)
                showLoadDialogB = false
            }
        )
    }
}

@Composable
fun DjDeck(
    deckName: String,
    song: MusicItem?,
    isPlaying: Boolean,
    volume: Float,
    pitch: Float,
    position: Long,
    duration: Long,
    rotationAngle: Float,
    accentColor: Color,
    onLoadClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onPitchChange: (Float) -> Unit,
    onSeekChange: (Long) -> Unit,
    onEqBassChange: (Float) -> Unit,
    onEqMidChange: (Float) -> Unit,
    onEqTrebleChange: (Float) -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF16162E).copy(alpha = 0.7f)),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Row: Deck Name & Load Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = deckName,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = accentColor,
                    letterSpacing = 1.sp
                )
                IconButton(
                    onClick = onLoadClick,
                    modifier = Modifier
                        .size(32.dp)
                        .background(accentColor.copy(alpha = 0.2f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Load song",
                        tint = accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Vinyl Turntable
            Box(
                modifier = Modifier
                    .size(130.dp)
                    .clip(CircleShape)
                    .background(Color.Black)
                    .border(2.dp, Color.DarkGray, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                // Vinyl grooves background
                Canvas(modifier = Modifier.fillMaxSize().rotate(rotationAngle)) {
                    val rMax = size.minDimension / 2
                    // Draw lines represent grooves
                    for (r in (rMax.toInt() - 10) downTo 20 step 12) {
                        drawCircle(
                            color = Color.White.copy(alpha = 0.05f),
                            radius = r.toFloat(),
                            style = Stroke(width = 1.dp.toPx())
                        )
                    }
                    // Slipmat marker (white line so user can see it spinning)
                    val slipmatWidth = 4.dp.toPx()
                    val centerX = size.width / 2f
                    val centerY = size.height / 2f
                    val markerStart = 25.dp.toPx()
                    drawLine(
                        color = Color.White.copy(alpha = 0.4f),
                        start = androidx.compose.ui.geometry.Offset(centerX + markerStart, centerY),
                        end = androidx.compose.ui.geometry.Offset(centerX + rMax - 5.dp.toPx(), centerY),
                        strokeWidth = slipmatWidth
                    )
                }

                // Center song thumbnail or default
                Box(
                    modifier = Modifier
                        .size(45.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.3f))
                        .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (song != null && song.thumbnailUrl.isNotBlank()) {
                        AsyncImage(
                            model = song.thumbnailUrl,
                            contentDescription = "Song Thumbnail",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    // Center spindle hole
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color.Black)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Song Info (Title & Artist)
            Text(
                text = song?.title ?: "TRỐNG - CHẠM (+)",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            Text(
                text = song?.artist ?: "Không có nghệ sĩ",
                fontSize = 11.sp,
                color = Color.Gray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Deck Controls: Play/Pause, EQ controls, Pitch & Volume
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left Column: Vertical Pitch & Volume Sliders
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(65.dp)
                ) {
                    // Pitch (playback speed multiplier)
                    Text("PITCH: ${"%.2f".format(pitch)}x", fontSize = 9.sp, color = Color.LightGray)
                    Slider(
                        value = pitch,
                        onValueChange = onPitchChange,
                        valueRange = 0.5f..2.0f,
                        colors = SliderDefaults.colors(
                            activeTrackColor = accentColor,
                            thumbColor = accentColor
                        ),
                        modifier = Modifier
                            .height(100.dp)
                            .rotate(-90f) // Vertical slider emulation
                    )
                    Spacer(modifier = Modifier.height(30.dp))
                    // Volume
                    Text("VOL: ${(volume * 100).toInt()}%", fontSize = 9.sp, color = Color.LightGray)
                    Slider(
                        value = volume,
                        onValueChange = onVolumeChange,
                        valueRange = 0f..1f,
                        colors = SliderDefaults.colors(
                            activeTrackColor = Color.White,
                            thumbColor = Color.White
                        ),
                        modifier = Modifier
                            .height(100.dp)
                            .rotate(-90f)
                    )
                }

                // Right Column: Play button & EQ Sliders (Compact)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    // Play / Pause Circle Button
                    Button(
                        onClick = onPlayPauseClick,
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.size(45.dp)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(15.dp))

                    // Parametric EQ Sliders
                    var eqBass by remember { mutableStateOf(0.0f) }
                    var eqMid by remember { mutableStateOf(0.0f) }
                    var eqTreble by remember { mutableStateOf(0.0f) }

                    // BASS fader
                    Text("BASS", fontSize = 9.sp, color = Color(0xFF00E5FF), fontWeight = FontWeight.Bold)
                    Slider(
                        value = eqBass,
                        onValueChange = {
                            eqBass = it
                            onEqBassChange(it)
                        },
                        valueRange = -1.0f..1.0f,
                        colors = SliderDefaults.colors(
                            activeTrackColor = Color(0xFF00E5FF),
                            thumbColor = Color(0xFF00E5FF)
                        ),
                        modifier = Modifier.fillMaxWidth().height(28.dp)
                    )

                    // MID fader
                    Text("MID", fontSize = 9.sp, color = Color(0xFFBD00FF), fontWeight = FontWeight.Bold)
                    Slider(
                        value = eqMid,
                        onValueChange = {
                            eqMid = it
                            onEqMidChange(it)
                        },
                        valueRange = -1.0f..1.0f,
                        colors = SliderDefaults.colors(
                            activeTrackColor = Color(0xFFBD00FF),
                            thumbColor = Color(0xFFBD00FF)
                        ),
                        modifier = Modifier.fillMaxWidth().height(28.dp)
                    )

                    // TREBLE fader
                    Text("TREBLE", fontSize = 9.sp, color = Color(0xFFFF007F), fontWeight = FontWeight.Bold)
                    Slider(
                        value = eqTreble,
                        onValueChange = {
                            eqTreble = it
                            onEqTrebleChange(it)
                        },
                        valueRange = -1.0f..1.0f,
                        colors = SliderDefaults.colors(
                            activeTrackColor = Color(0xFFFF007F),
                            thumbColor = Color(0xFFFF007F)
                        ),
                        modifier = Modifier.fillMaxWidth().height(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Time & Seek Slider
            val progressText = formatDuration(position)
            val totalText = formatDuration(duration)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(progressText, fontSize = 9.sp, color = Color.Gray)
                Text(totalText, fontSize = 9.sp, color = Color.Gray)
            }
            Slider(
                value = if (duration > 0) position.toFloat() else 0f,
                onValueChange = { onSeekChange(it.toLong()) },
                valueRange = 0f..(if (duration > 0) duration.toFloat() else 1f),
                colors = SliderDefaults.colors(
                    activeTrackColor = accentColor.copy(alpha = 0.6f),
                    inactiveTrackColor = Color.DarkGray
                ),
                modifier = Modifier.fillMaxWidth().height(16.dp)
            )
        }
    }
}

@Composable
fun CenterMixerControls(
    crossfader: Float,
    isPlayingA: Boolean,
    isPlayingB: Boolean,
    onCrossfaderChange: (Float) -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F0F23).copy(alpha = 0.9f)),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Visualizer Screen
            Text(
                text = "MAIN OUTPUT VISUALIZER",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color.LightGray,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black)
                    .border(1.dp, Color.DarkGray.copy(alpha = 0.5f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                AnimatedWaveformCanvas(isPlayingA = isPlayingA, isPlayingB = isPlayingB)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Crossfader Slider
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("DECK A", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00FFCC))
                Text("CROSSFADER", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                Text("DECK B", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF007F))
            }

            Slider(
                value = crossfader,
                onValueChange = onCrossfaderChange,
                valueRange = 0f..1f,
                colors = SliderDefaults.colors(
                    activeTrackColor = Color(0xFFFF007F),
                    inactiveTrackColor = Color(0xFF00FFCC),
                    thumbColor = Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            )
        }
    }
}

@Composable
fun AnimatedWaveformCanvas(isPlayingA: Boolean, isPlayingB: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "waveform")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val centerY = height / 2f
        val points = 60
        val step = width / points

        // Draw center baseline
        drawLine(
            color = Color.DarkGray.copy(alpha = 0.3f),
            start = androidx.compose.ui.geometry.Offset(0f, centerY),
            end = androidx.compose.ui.geometry.Offset(width, centerY),
            strokeWidth = 1.dp.toPx()
        )

        // Draw dual wave channels
        for (i in 0 until points) {
            val x = i * step
            var amplitudeA = 0f
            var amplitudeB = 0f

            if (isPlayingA) {
                // Sine wave simulation for Deck A (teal)
                amplitudeA = (height * 0.3f) * kotlin.math.sin(i * 0.2f + phase) +
                        (height * 0.1f) * kotlin.math.cos(i * 0.5f - phase)
                // Add minor noise
                amplitudeA += Random.nextFloat() * 4.dp.toPx() - 2.dp.toPx()
            }

            if (isPlayingB) {
                // Sine wave simulation for Deck B (pink)
                amplitudeB = (height * 0.3f) * kotlin.math.sin(i * 0.25f - phase) +
                        (height * 0.12f) * kotlin.math.cos(i * 0.4f + phase)
                // Add minor noise
                amplitudeB += Random.nextFloat() * 4.dp.toPx() - 2.dp.toPx()
            }

            // Draw Deck A bars
            if (isPlayingA) {
                val h = centerY + amplitudeA
                drawLine(
                    color = Color(0xFF00FFCC).copy(alpha = 0.7f),
                    start = androidx.compose.ui.geometry.Offset(x, centerY),
                    end = androidx.compose.ui.geometry.Offset(x, h),
                    strokeWidth = 2.dp.toPx()
                )
            }

            // Draw Deck B bars
            if (isPlayingB) {
                val h = centerY - amplitudeB
                drawLine(
                    color = Color(0xFFFF007F).copy(alpha = 0.7f),
                    start = androidx.compose.ui.geometry.Offset(x, centerY),
                    end = androidx.compose.ui.geometry.Offset(x, h),
                    strokeWidth = 2.dp.toPx()
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongPickerDialog(
    title: String,
    songs: List<MusicItem>,
    onDismiss: () -> Unit,
    onSongSelected: (MusicItem) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    
    val filteredSongs = remember(songs, searchQuery) {
        if (searchQuery.isBlank()) {
            songs
        } else {
            songs.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                it.artist.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A3A)),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.7f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Text(
                    text = title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF00FFCC),
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                // Search field
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Tìm kiếm bài hát...", color = Color.Gray) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF00FFCC),
                        unfocusedBorderColor = Color.DarkGray
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    shape = RoundedCornerShape(12.dp)
                )

                if (filteredSongs.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Không tìm thấy bài hát nào.\nHãy quét nhạc ở Tab Thư viện nhạc.",
                            color = Color.LightGray,
                            textAlign = TextAlign.Center,
                            fontSize = 14.sp
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredSongs) { song ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSongSelected(song) }
                                    .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MusicNote,
                                    contentDescription = null,
                                    tint = Color(0xFF00FFCC),
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = song.title,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = song.artist,
                                        fontSize = 12.sp,
                                        color = Color.Gray,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Close Button
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("ĐÓNG", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private fun formatDuration(durationMs: Long): String {
    if (durationMs <= 0) return "00:00"
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}
