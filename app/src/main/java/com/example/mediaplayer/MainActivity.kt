package com.example.mediaplayer

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mediaplayer.data.MusicPlayerManager
import com.example.mediaplayer.data.DjMixerManager
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
    DjMixerManager.release()
  }
}

@Composable
fun SplashView() {
  val infiniteTransition = rememberInfiniteTransition(label = "splash")
  
  // Rotating pulse for soundwaves
  val rotation by infiniteTransition.animateFloat(
    initialValue = 0f,
    targetValue = 360f,
    animationSpec = infiniteRepeatable(
      animation = tween(4000, easing = LinearEasing),
      repeatMode = RepeatMode.Restart
    ),
    label = "rotation"
  )

  // Pulsing scale for the center icon
  val scale by infiniteTransition.animateFloat(
    initialValue = 0.85f,
    targetValue = 1.15f,
    animationSpec = infiniteRepeatable(
      animation = tween(1000, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "scale"
  )

  // Beautiful background gradient: Indigo to Deep Violet
  val bgGradient = Brush.verticalGradient(
    colors = listOf(
      Color(0xFF1A1A2E),
      Color(0xFF16213E),
      Color(0xFF0F3460)
    )
  )

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(bgGradient),
    contentAlignment = Alignment.Center
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center
    ) {
      Box(
        modifier = Modifier
          .size(160.dp)
          .scale(scale),
        contentAlignment = Alignment.Center
      ) {
        // Outer concentric audio waves
        Canvas(modifier = Modifier.fillMaxSize().rotate(rotation)) {
          val canvasSize = size.minDimension
          val radius1 = canvasSize / 2f
          val radius2 = canvasSize / 2.8f
          
          // Draw outer soundwave circles
          drawCircle(
            color = Color(0xFF4ECCA3).copy(alpha = 0.15f),
            radius = radius1,
            style = Stroke(width = 4.dp.toPx())
          )
          drawCircle(
            color = Color(0xFF4ECCA3).copy(alpha = 0.35f),
            radius = radius2,
            style = Stroke(width = 3.dp.toPx())
          )
        }

        // Central glowing orb
        Box(
          modifier = Modifier
            .size(90.dp)
            .clip(CircleShape)
            .background(
              Brush.radialGradient(
                colors = listOf(
                  Color(0xFF4ECCA3),
                  Color(0xFF232931)
                )
              )
            ),
          contentAlignment = Alignment.Center
        ) {
          // Play icon in the center of the orb
          Icon(
            imageVector = Icons.Default.PlayArrow,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(48.dp)
          )
        }
      }

      Spacer(modifier = Modifier.height(32.dp))

      Text(
        text = "PREMIUM PLAYER",
        fontSize = 24.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 4.sp,
        color = Color(0xFF4ECCA3)
      )

      Spacer(modifier = Modifier.height(8.dp))

      Text(
        text = "Experience media in high quality",
        fontSize = 13.sp,
        fontWeight = FontWeight.Light,
        color = Color.White.copy(alpha = 0.7f),
        letterSpacing = 1.sp
      )
    }
  }
}
