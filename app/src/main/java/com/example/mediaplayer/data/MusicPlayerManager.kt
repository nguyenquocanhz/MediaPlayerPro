package com.example.mediaplayer.data

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class MusicRepeatMode {
    OFF,   // Turn off repeat
    ONE,   // Repeat once (repeat 1 song)
    ALL    // Repeat all (loop playlist)
}

object MusicPlayerManager {
    private var exoPlayer: ExoPlayer? = null
    private var applicationContext: Context? = null
    
    private val _currentSong = MutableStateFlow<MusicItem?>(null)
    val currentSong = _currentSong.asStateFlow()
    
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()
    
    private val _position = MutableStateFlow(0L)
    val position = _position.asStateFlow()
    
    private val _duration = MutableStateFlow(0L)
    val duration = _duration.asStateFlow()
    
    private val _playlist = MutableStateFlow<List<MusicItem>>(emptyList())
    val playlist = _playlist.asStateFlow()
    
    private val _repeatMode = MutableStateFlow(MusicRepeatMode.ALL)
    val repeatMode = _repeatMode.asStateFlow()
    
    private var currentIndex = -1
    private var updateJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    fun init(context: Context) {
        applicationContext = context.applicationContext
        if (exoPlayer == null) {
            exoPlayer = ExoPlayer.Builder(context.applicationContext).build().apply {
                repeatMode = Player.REPEAT_MODE_OFF
                addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(playing: Boolean) {
                        _isPlaying.value = playing
                        if (playing) {
                            startPositionUpdates()
                        } else {
                            stopPositionUpdates()
                        }
                    }
                    override fun onPlaybackStateChanged(state: Int) {
                        if (state == Player.STATE_READY) {
                            _duration.value = duration
                        } else if (state == Player.STATE_ENDED) {
                            handleSongEnded()
                        }
                    }
                })
            }
        }
    }
    
    private fun startService() {
        val context = applicationContext ?: return
        val intent = android.content.Intent(context, MusicService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }
    
    fun toggleRepeatMode() {
        _repeatMode.value = when (_repeatMode.value) {
            MusicRepeatMode.OFF -> MusicRepeatMode.ALL
            MusicRepeatMode.ALL -> MusicRepeatMode.ONE
            MusicRepeatMode.ONE -> MusicRepeatMode.OFF
        }
    }
    
    private fun handleSongEnded() {
        val list = _playlist.value
        if (list.isEmpty() || currentIndex == -1) return
        
        when (_repeatMode.value) {
            MusicRepeatMode.ONE -> {
                // Repeat once
                val player = exoPlayer ?: return
                player.seekTo(0)
                player.prepare()
                player.play()
            }
            MusicRepeatMode.OFF -> {
                // Turn off repeat: play next if not at the end of playlist
                if (currentIndex < list.size - 1) {
                    currentIndex++
                    play(list[currentIndex])
                } else {
                    _isPlaying.value = false
                }
            }
            MusicRepeatMode.ALL -> {
                // Repeat all: loop playlist
                currentIndex = (currentIndex + 1) % list.size
                play(list[currentIndex])
            }
        }
    }
    
    fun play(song: MusicItem) {
        val player = exoPlayer ?: return
        _currentSong.value = song
        player.stop()
        player.clearMediaItems()
        
        val mediaItem = MediaItem.fromUri(Uri.parse(song.path))
        player.setMediaItem(mediaItem)
        player.prepare()
        player.play()
        
        // Find if this song is in playlist, update index
        val list = _playlist.value
        val foundIndex = list.indexOfFirst { it.path == song.path }
        if (foundIndex != -1) {
            currentIndex = foundIndex
        } else {
            // Set single song playlist
            _playlist.value = listOf(song)
            currentIndex = 0
        }
        
        startService()
    }
    
    fun playPlaylist(list: List<MusicItem>, index: Int) {
        _playlist.value = list
        currentIndex = index
        if (index in list.indices) {
            play(list[index])
        }
    }
    
    fun togglePlayPause() {
        val player = exoPlayer ?: return
        if (player.isPlaying) {
            player.pause()
        } else {
            if (player.playbackState == Player.STATE_IDLE || player.playbackState == Player.STATE_ENDED) {
                player.prepare()
            }
            player.play()
            startService()
        }
    }
    
    fun next() {
        val list = _playlist.value
        if (list.isEmpty()) return
        if (currentIndex != -1) {
            currentIndex = (currentIndex + 1) % list.size
            play(list[currentIndex])
        }
    }
    
    fun previous() {
        val list = _playlist.value
        if (list.isEmpty()) return
        if (currentIndex != -1) {
            currentIndex = if (currentIndex - 1 < 0) list.size - 1 else currentIndex - 1
            play(list[currentIndex])
        }
    }
    
    fun seekTo(positionMs: Long) {
        exoPlayer?.seekTo(positionMs)
        _position.value = positionMs
    }
    
    private fun startPositionUpdates() {
        stopPositionUpdates()
        updateJob = scope.launch {
            while (isActive) {
                exoPlayer?.let {
                    _position.value = it.currentPosition
                }
                delay(500)
            }
        }
    }
    
    private fun stopPositionUpdates() {
        updateJob?.cancel()
        updateJob = null
    }
    
    fun release() {
        stopPositionUpdates()
        exoPlayer?.release()
        exoPlayer = null
    }
}
