package com.example.mediaplayer

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mediaplayer.data.MusicPlayerManager
import com.example.mediaplayer.theme.MediaPlayerTheme
import kotlinx.coroutines.delay

class MainActivity : FragmentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    
    // Initialize Music Player ExoPlayer
    MusicPlayerManager.init(this)

    enableEdgeToEdge()
    setContent {
      MediaPlayerTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
          var showSplash by remember { mutableStateOf(true) }
          
          LaunchedEffect(Unit) {
            delay(2000L)
            showSplash = false
          }
          
          if (showSplash) {
            SplashView()
          } else {
            MainNavigation()
          }
        }
      }
    }
  }

  override fun onDestroy() {
    super.onDestroy()
    // Release music player resources on destroy
    MusicPlayerManager.release()
  }
}

@Composable
fun SplashView() {
  val infiniteTransition = rememberInfiniteTransition(label = "splash")

  // Pulsing scale for the center icon
  val scale by infiniteTransition.animateFloat(
    initialValue = 0.95f,
    targetValue = 1.05f,
    animationSpec = infiniteRepeatable(
      animation = tween(1200, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "scale"
  )

  // Beautiful vertical gradient: Bright Blue to Deep Navy Blue
  val bgGradient = Brush.verticalGradient(
    colors = listOf(
      Color(0xFF0084FF),
      Color(0xFF0055D0),
      Color(0xFF002A80)
    )
  )

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(bgGradient),
    contentAlignment = Alignment.Center
  ) {
    // 1. Floating Semi-transparent Music Notes in the background
    Box(modifier = Modifier.fillMaxSize()) {
      // Top-left note
      Icon(
        imageVector = Icons.Default.MusicNote,
        contentDescription = null,
        tint = Color.White.copy(alpha = 0.12f),
        modifier = Modifier
          .size(64.dp)
          .offset(x = 40.dp, y = 120.dp)
          .rotate(-15f)
      )
      // Top-right note
      Icon(
        imageVector = Icons.Default.MusicNote,
        contentDescription = null,
        tint = Color.White.copy(alpha = 0.08f),
        modifier = Modifier
          .size(48.dp)
          .offset(x = 280.dp, y = 200.dp)
          .rotate(20f)
      )
      // Bottom-right note
      Icon(
        imageVector = Icons.Default.MusicNote,
        contentDescription = null,
        tint = Color.White.copy(alpha = 0.12f),
        modifier = Modifier
          .size(80.dp)
          .offset(x = 260.dp, y = 520.dp)
          .rotate(10f)
      )
    }

    // 2. Left & Right Visualizer Waveforms (semi-transparent)
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 24.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      // Left visualizer bars
      Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        val leftHeights = listOf(24.dp, 40.dp, 64.dp, 56.dp, 32.dp, 72.dp, 48.dp, 28.dp)
        leftHeights.forEach { height ->
          Box(
            modifier = Modifier
              .width(4.dp)
              .height(height)
              .background(Color.White.copy(alpha = 0.15f), shape = RoundedCornerShape(2.dp))
          )
        }
      }

      // Right visualizer bars
      Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        val rightHeights = listOf(28.dp, 48.dp, 72.dp, 32.dp, 56.dp, 64.dp, 40.dp, 24.dp)
        rightHeights.forEach { height ->
          Box(
            modifier = Modifier
              .width(4.dp)
              .height(height)
              .background(Color.White.copy(alpha = 0.15f), shape = RoundedCornerShape(2.dp))
          )
        }
      }
    }

    // Center Content Column
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center
    ) {
      // 3. Central App Logo (Beautiful dynamic Compose-drawn version of the new icon)
      Box(
        modifier = Modifier
          .size(130.dp)
          .scale(scale)
          .background(
            brush = Brush.linearGradient(
              colors = listOf(Color(0xFF00A2FF), Color(0xFF0066FF))
            ),
            shape = RoundedCornerShape(32.dp)
          )
          .border(1.5.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(32.dp)),
        contentAlignment = Alignment.Center
      ) {
        // Circle Track, Progress Arc, and Dot
        Canvas(modifier = Modifier.fillMaxSize()) {
          val centerOffset = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
          val radius = size.minDimension / 4.4f
          
          // Track circle (25% opacity white)
          drawCircle(
            color = Color.White.copy(alpha = 0.25f),
            radius = radius,
            center = centerOffset,
            style = Stroke(width = 3.5.dp.toPx())
          )
          
          // Active progress arc (starts at 135 deg, sweeps 270 deg clockwise)
          drawArc(
            color = Color.White,
            startAngle = 135f,
            sweepAngle = 270f,
            useCenter = false,
            topLeft = androidx.compose.ui.geometry.Offset(centerOffset.x - radius, centerOffset.y - radius),
            size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
            style = Stroke(width = 3.5.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
          )
          
          // Thumb dot at 135 degrees
          val angleRad = Math.toRadians(135.0)
          val thumbX = centerOffset.x + radius * Math.cos(angleRad).toFloat()
          val thumbY = centerOffset.y + radius * Math.sin(angleRad).toFloat()
          
          drawCircle(
            color = Color.White,
            radius = 5.dp.toPx(),
            center = androidx.compose.ui.geometry.Offset(thumbX, thumbY)
          )
        }
        
        // Play triangle inside logo
        Box(
          modifier = Modifier
            .size(40.dp)
            .offset(x = 2.dp),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = Icons.Default.PlayArrow,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.fillMaxSize()
          )
        }
      }

      Spacer(modifier = Modifier.height(40.dp))

      // 4. App Title & Subtitle
      Text(
        text = "MediaPlayer",
        fontSize = 32.sp,
        fontWeight = FontWeight.ExtraBold,
        color = Color.White,
        letterSpacing = 1.sp
      )

      Spacer(modifier = Modifier.height(8.dp))

      Text(
        text = "Play it your way",
        fontSize = 15.sp,
        fontWeight = FontWeight.Normal,
        color = Color.White.copy(alpha = 0.75f),
        letterSpacing = 0.5.sp
      )
    }

    // 5. Loading Indicator at the bottom
    Column(
      modifier = Modifier
        .align(Alignment.BottomCenter)
        .padding(bottom = 64.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      androidx.compose.material3.CircularProgressIndicator(
        color = Color.White.copy(alpha = 0.9f),
        strokeWidth = 3.dp,
        modifier = Modifier.size(32.dp)
      )
      Text(
        text = "Loading...",
        fontSize = 13.sp,
        fontWeight = FontWeight.Light,
        color = Color.White.copy(alpha = 0.6f),
        letterSpacing = 1.sp
      )
    }
  }
}
