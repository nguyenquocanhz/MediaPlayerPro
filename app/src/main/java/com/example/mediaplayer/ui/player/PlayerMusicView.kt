package com.example.mediaplayer.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mediaplayer.data.MusicItem
import com.example.mediaplayer.data.MusicPlayerManager
import com.example.mediaplayer.data.MusicRepeatMode
import com.example.mediaplayer.data.VideoScanner
import kotlin.math.max
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerMusicView(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentSong by MusicPlayerManager.currentSong.collectAsState()
    val isPlaying by MusicPlayerManager.isPlaying.collectAsState()
    val position by MusicPlayerManager.position.collectAsState()
    val duration by MusicPlayerManager.duration.collectAsState()
    val playlist by MusicPlayerManager.playlist.collectAsState()
    val repeatMode by MusicPlayerManager.repeatMode.collectAsState()

    var showQueue by remember { mutableStateOf(false) }

    // CD rotation animation using a single Animatable to avoid snapping
    val rotationAngle = remember { Animatable(0f) }
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            rotationAngle.animateTo(
                targetValue = rotationAngle.value + 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(15000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                )
            )
        } else {
            rotationAngle.stop()
        }
    }

    if (currentSong == null) return

    val song = currentSong!!

    // Premium background gradient (Deep Space Black to Dark Royal Indigo)
    val backgroundGradient = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF0F0C1B),
            Color(0xFF15102A),
            Color(0xFF0A0813)
        )
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundGradient)
    ) {
        // Main Player Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .statusBarsPadding()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header Section
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Thu nhỏ trình phát",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Text(
                    text = "ĐANG PHÁT",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )

                IconButton(onClick = { showQueue = !showQueue }) {
                    Icon(
                        imageVector = Icons.Default.QueueMusic,
                        contentDescription = "Danh sách phát",
                        tint = if (showQueue) Color(0xFF4ECCA3) else Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            // CD Section
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                // Outer glowing circle
                Box(
                    modifier = Modifier
                        .size(280.dp)
                        .clip(CircleShape)
                        .border(1.5.dp, Color(0xFF4ECCA3).copy(alpha = 0.25f), CircleShape)
                        .background(Color.Black.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    // Vinyl CD rotating disc
                    Box(
                        modifier = Modifier
                            .size(260.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF111116))
                            .rotate(rotationAngle.value),
                        contentAlignment = Alignment.Center
                    ) {
                        // Concentric lines representing Vinyl texture
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
                            val strokeWidth = 1f
                            val step = 12f
                            val maxRadius = size.minDimension / 2f - 20.dp.toPx()
                            
                            var r = 30.dp.toPx()
                            while (r < maxRadius) {
                                drawCircle(
                                    color = Color.White.copy(alpha = 0.05f),
                                    radius = r,
                                    center = center,
                                    style = Stroke(width = strokeWidth)
                                )
                                r += step
                            }

                            // Reflection sweeps/wedges representing light shine on vinyl record to show rotation clearly!
                            drawArc(
                                color = Color.White.copy(alpha = 0.04f),
                                startAngle = 40f,
                                sweepAngle = 25f,
                                useCenter = true
                            )
                            drawArc(
                                color = Color.White.copy(alpha = 0.04f),
                                startAngle = 220f,
                                sweepAngle = 25f,
                                useCenter = true
                            )
                        }

                        // Song art center label (Thumbnail or Fallback Gradient)
                        Box(
                            modifier = Modifier
                                .size(90.dp)
                                .clip(CircleShape)
                                .border(1.5.dp, Color.White.copy(alpha = 0.3f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            if (song.thumbnailUrl.isNotBlank()) {
                                AsyncImage(
                                    model = song.thumbnailUrl,
                                    contentDescription = "Ảnh đại diện bài hát",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.radialGradient(
                                                colors = listOf(
                                                    Color(0xFF4ECCA3),
                                                    Color(0xFF1A2E35),
                                                    Color(0xFF000000)
                                                )
                                            )
                                        )
                                )
                            }

                            // Middle spindle hole overlay
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF0F0C1B))
                                    .border(1.5.dp, Color.White.copy(alpha = 0.6f), CircleShape)
                            )
                        }
                    }
                }
            }

            // Info Section
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = song.title,
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = song.artist,
                    color = Color(0xFF4ECCA3),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Slider & Time stamps
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                Slider(
                    value = if (duration > 0) position.toFloat() else 0f,
                    onValueChange = { MusicPlayerManager.seekTo(it.toLong()) },
                    valueRange = 0f..max(1f, duration.toFloat()),
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF4ECCA3),
                        activeTrackColor = Color(0xFF4ECCA3),
                        inactiveTrackColor = Color.White.copy(alpha = 0.15f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = VideoScanner.formatDuration(position),
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = VideoScanner.formatDuration(duration),
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Control Buttons Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Shuffle mode placeholder
                IconButton(onClick = { /* Shuffle behavior could be added if needed, toggle state */ }) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = "Ngẫu nhiên",
                        tint = Color.White.copy(alpha = 0.3f), // currently disabled placeholder
                        modifier = Modifier.size(24.dp)
                    )
                }

                IconButton(onClick = { MusicPlayerManager.previous() }) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Bài trước",
                        tint = Color.White,
                        modifier = Modifier.size(38.dp)
                    )
                }

                // Play / Pause central orb
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFF4ECCA3),
                                    Color(0xFF389D7E)
                                )
                            )
                        )
                        .border(2.dp, Color.White.copy(alpha = 0.3f), CircleShape)
                        .clickable { MusicPlayerManager.togglePlayPause() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Tạm dừng" else "Phát",
                        tint = Color(0xFF0F0C1B),
                        modifier = Modifier.size(34.dp)
                    )
                }

                IconButton(onClick = { MusicPlayerManager.next() }) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Bài tiếp theo",
                        tint = Color.White,
                        modifier = Modifier.size(38.dp)
                    )
                }

                // Repeat Mode Button
                IconButton(onClick = { MusicPlayerManager.toggleRepeatMode() }) {
                    val icon = when (repeatMode) {
                        MusicRepeatMode.OFF -> Icons.Default.Repeat
                        MusicRepeatMode.ONE -> Icons.Default.RepeatOne
                        MusicRepeatMode.ALL -> Icons.Default.Repeat
                    }
                    val tint = when (repeatMode) {
                        MusicRepeatMode.OFF -> Color.White.copy(alpha = 0.4f)
                        MusicRepeatMode.ONE -> Color(0xFF4ECCA3)
                        MusicRepeatMode.ALL -> Color(0xFF4ECCA3)
                    }
                    Icon(
                        imageVector = icon,
                        contentDescription = "Chế độ lặp",
                        tint = tint,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        // Slide-up Queue Drawer Overlay
        AnimatedVisibility(
            visible = showQueue,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable { showQueue = false }
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.55f)
                        .align(Alignment.BottomCenter)
                        .clickable(enabled = false) {}, // prevent click-through
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF151224)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Danh sách hàng đợi (${playlist.size})",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(onClick = { showQueue = false }) {
                                Icon(Icons.Default.Close, contentDescription = "Đóng", tint = Color.White)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            itemsIndexed(playlist) { idx, item ->
                                val isCurrent = item.path == song.path
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            MusicPlayerManager.playPlaylist(playlist, idx)
                                        },
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isCurrent) Color(0xFF4ECCA3).copy(alpha = 0.15f) else Color.White.copy(alpha = 0.03f)
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = if (isCurrent && isPlaying) Icons.Default.VolumeUp else Icons.Default.MusicNote,
                                            contentDescription = null,
                                            tint = if (isCurrent) Color(0xFF4ECCA3) else Color.White.copy(alpha = 0.4f),
                                            modifier = Modifier.size(20.dp)
                                        )

                                        Spacer(modifier = Modifier.width(12.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = item.title,
                                                color = if (isCurrent) Color(0xFF4ECCA3) else Color.White,
                                                fontSize = 14.sp,
                                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = item.artist,
                                                color = Color.White.copy(alpha = 0.6f),
                                                fontSize = 11.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
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
    }
}
