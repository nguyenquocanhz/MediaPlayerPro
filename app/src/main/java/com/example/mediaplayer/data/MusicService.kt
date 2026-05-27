package com.example.mediaplayer.data

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.IBinder
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.mediaplayer.MainActivity
import com.example.mediaplayer.R
import kotlinx.coroutines.*

class MusicService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    private var job: Job? = null
    
    private var currentSongUrl: String? = null
    private var currentSongBitmap: Bitmap? = null
    private var isForeground = false

    companion object {
        private const val CHANNEL_ID = "media_player_music_channel"
        private const val NOTIFICATION_ID = 1001

        const val ACTION_PLAY_PAUSE = "com.example.mediaplayer.PLAY_PAUSE"
        const val ACTION_PREVIOUS = "com.example.mediaplayer.PREVIOUS"
        const val ACTION_NEXT = "com.example.mediaplayer.NEXT"
        const val ACTION_STOP = "com.example.mediaplayer.STOP"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        // Observe player states dynamically
        job = serviceScope.launch {
            launch {
                MusicPlayerManager.currentSong.collect { song ->
                    handlePlayerUpdate(song, MusicPlayerManager.isPlaying.value)
                }
            }
            launch {
                MusicPlayerManager.isPlaying.collect { playing ->
                    handlePlayerUpdate(MusicPlayerManager.currentSong.value, playing)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.action?.let { action ->
            when (action) {
                ACTION_PLAY_PAUSE -> MusicPlayerManager.togglePlayPause()
                ACTION_PREVIOUS -> MusicPlayerManager.previous()
                ACTION_NEXT -> MusicPlayerManager.next()
                ACTION_STOP -> {
                    MusicPlayerManager.togglePlayPause()
                    if (!MusicPlayerManager.isPlaying.value) {
                        stopServiceState()
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun handlePlayerUpdate(song: MusicItem?, isPlaying: Boolean) {
        if (song == null) {
            stopServiceState()
            return
        }

        // Rebuild notification with current cached bitmap first
        val defaultBitmap = try {
            val drawable = androidx.core.content.ContextCompat.getDrawable(this, R.mipmap.ic_launcher)
            if (drawable is android.graphics.drawable.BitmapDrawable) {
                drawable.bitmap
            } else if (drawable != null) {
                val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 512
                val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 512
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(bitmap)
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)
                bitmap
            } else {
                Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
            }
        } catch (e: Exception) {
            Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        }
        val bitmapToUse = if (song.thumbnailUrl == currentSongUrl) currentSongBitmap else null
        
        buildAndShowNotification(song, isPlaying, bitmapToUse ?: defaultBitmap)

        // If thumbnail has changed, fetch it asynchronously
        if (song.thumbnailUrl.isNotBlank() && song.thumbnailUrl != currentSongUrl) {
            currentSongUrl = song.thumbnailUrl
            fetchThumbnailBitmap(song.thumbnailUrl) { bitmap ->
                if (bitmap != null) {
                    currentSongBitmap = bitmap
                    // Verify the song hasn't changed while we were fetching
                    if (MusicPlayerManager.currentSong.value?.thumbnailUrl == song.thumbnailUrl) {
                        buildAndShowNotification(song, isPlaying, bitmap)
                    }
                }
            }
        } else if (song.thumbnailUrl.isBlank()) {
            currentSongUrl = null
            currentSongBitmap = null
        }
    }

    private fun buildAndShowNotification(song: MusicItem, isPlaying: Boolean, albumArt: Bitmap) {
        val playPauseIntent = Intent(this, MusicService::class.java).apply { action = ACTION_PLAY_PAUSE }
        val prevIntent = Intent(this, MusicService::class.java).apply { action = ACTION_PREVIOUS }
        val nextIntent = Intent(this, MusicService::class.java).apply { action = ACTION_NEXT }
        val stopIntent = Intent(this, MusicService::class.java).apply { action = ACTION_STOP }

        val pendingFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val pPlayPause = PendingIntent.getService(this, 1, playPauseIntent, pendingFlags)
        val pPrev = PendingIntent.getService(this, 2, prevIntent, pendingFlags)
        val pNext = PendingIntent.getService(this, 3, nextIntent, pendingFlags)
        val pStop = PendingIntent.getService(this, 4, stopIntent, pendingFlags)

        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pOpenApp = PendingIntent.getActivity(this, 0, openAppIntent, pendingFlags)

        // Native Android Notification MediaStyle
        val mediaStyle = android.app.Notification.MediaStyle()
            .setShowActionsInCompactView(0, 1, 2)

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            android.app.Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            android.app.Notification.Builder(this)
        }

        builder.setSmallIcon(R.drawable.ic_notification) // fallback small icon
            .setLargeIcon(albumArt)
            .setContentTitle(song.title)
            .setContentText(song.artist)
            .setContentIntent(pOpenApp)
            .setVisibility(android.app.Notification.VISIBILITY_PUBLIC)
            .setOngoing(isPlaying)
            .setStyle(mediaStyle)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Using setOngoing and channel importance takes care of silent notifications on API 26+
        } else {
            @Suppress("DEPRECATION")
            builder.setPriority(android.app.Notification.PRIORITY_LOW)
        }

        // Add actions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
            builder.addAction(
                android.app.Notification.Action.Builder(R.drawable.ic_skip_previous, "Previous", pPrev).build()
            )
            builder.addAction(
                android.app.Notification.Action.Builder(
                    if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play,
                    if (isPlaying) "Pause" else "Play",
                    pPlayPause
                ).build()
            )
            builder.addAction(
                android.app.Notification.Action.Builder(R.drawable.ic_skip_next, "Next", pNext).build()
            )
            builder.addAction(
                android.app.Notification.Action.Builder(R.drawable.ic_close, "Stop", pStop).build()
            )
        } else {
            @Suppress("DEPRECATION")
            builder.addAction(R.drawable.ic_skip_previous, "Previous", pPrev)
            @Suppress("DEPRECATION")
            builder.addAction(
                if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play,
                if (isPlaying) "Pause" else "Play",
                pPlayPause
            )
            @Suppress("DEPRECATION")
            builder.addAction(R.drawable.ic_skip_next, "Next", pNext)
            @Suppress("DEPRECATION")
            builder.addAction(R.drawable.ic_close, "Stop", pStop)
        }

        val notification = builder.build()

        if (isPlaying || !isForeground) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            isForeground = true
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_DETACH)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(false)
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(NOTIFICATION_ID, notification)
        }
    }

    private fun fetchThumbnailBitmap(url: String, callback: (Bitmap?) -> Unit) {
        serviceScope.launch {
            try {
                val loader = ImageLoader(this@MusicService)
                val request = ImageRequest.Builder(this@MusicService)
                    .data(url)
                    .allowHardware(false) // Critical: prevent crash in system notifications
                    .build()
                val result = loader.execute(request)
                if (result is SuccessResult) {
                    val bitmap = (result.drawable as? BitmapDrawable)?.bitmap
                    withContext(Dispatchers.Main) {
                        callback(bitmap)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        callback(null)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    callback(null)
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Music Controls"
            val descriptionText = "Show controls for playing music"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun stopServiceState() {
        serviceScope.launch {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                stopForeground(true)
            }
            isForeground = false
            stopSelf()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        job?.cancel()
        serviceJob.cancel()
    }
}
