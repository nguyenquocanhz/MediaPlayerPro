package com.example.mediaplayer.data

import android.content.Context
import android.media.audiofx.Equalizer
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object DjMixerManager {
    private var playerA: ExoPlayer? = null
    private var playerB: ExoPlayer? = null

    private var eqA: Equalizer? = null
    private var eqB: Equalizer? = null

    private val _currentSongA = MutableStateFlow<MusicItem?>(null)
    val currentSongA = _currentSongA.asStateFlow()

    private val _currentSongB = MutableStateFlow<MusicItem?>(null)
    val currentSongB = _currentSongB.asStateFlow()

    private val _isPlayingA = MutableStateFlow(false)
    val isPlayingA = _isPlayingA.asStateFlow()

    private val _isPlayingB = MutableStateFlow(false)
    val isPlayingB = _isPlayingB.asStateFlow()

    private val _volumeA = MutableStateFlow(1.0f)
    val volumeA = _volumeA.asStateFlow()

    private val _volumeB = MutableStateFlow(1.0f)
    val volumeB = _volumeB.asStateFlow()

    private val _crossfader = MutableStateFlow(0.5f)
    val crossfader = _crossfader.asStateFlow()

    private val _pitchA = MutableStateFlow(1.0f)
    val pitchA = _pitchA.asStateFlow()

    private val _pitchB = MutableStateFlow(1.0f)
    val pitchB = _pitchB.asStateFlow()

    private val _positionA = MutableStateFlow(0L)
    val positionA = _positionA.asStateFlow()

    private val _durationA = MutableStateFlow(0L)
    val durationA = _durationA.asStateFlow()

    private val _positionB = MutableStateFlow(0L)
    val positionB = _positionB.asStateFlow()

    private val _durationB = MutableStateFlow(0L)
    val durationB = _durationB.asStateFlow()

    // EQ bands: -1.0 to 1.0
    private val _eqBassA = MutableStateFlow(0.0f)
    private val _eqMidA = MutableStateFlow(0.0f)
    private val _eqTrebleA = MutableStateFlow(0.0f)

    private val _eqBassB = MutableStateFlow(0.0f)
    private val _eqMidB = MutableStateFlow(0.0f)
    private val _eqTrebleB = MutableStateFlow(0.0f)

    private var jobA: Job? = null
    private var jobB: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    fun init(context: Context) {
        val appContext = context.applicationContext
        if (playerA == null) {
            playerA = ExoPlayer.Builder(appContext).build().apply {
                repeatMode = Player.REPEAT_MODE_ALL
                addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(playing: Boolean) {
                        _isPlayingA.value = playing
                        if (playing) startUpdatesA() else stopUpdatesA()
                    }
                    override fun onPlaybackStateChanged(state: Int) {
                        if (state == Player.STATE_READY) {
                            _durationA.value = duration
                        }
                    }
                    override fun onAudioSessionIdChanged(audioSessionId: Int) {
                        setupEqA(audioSessionId)
                    }
                })
            }
        }

        if (playerB == null) {
            playerB = ExoPlayer.Builder(appContext).build().apply {
                repeatMode = Player.REPEAT_MODE_ALL
                addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(playing: Boolean) {
                        _isPlayingB.value = playing
                        if (playing) startUpdatesB() else stopUpdatesB()
                    }
                    override fun onPlaybackStateChanged(state: Int) {
                        if (state == Player.STATE_READY) {
                            _durationB.value = duration
                        }
                    }
                    override fun onAudioSessionIdChanged(audioSessionId: Int) {
                        setupEqB(audioSessionId)
                    }
                })
            }
        }
        updateAppliedVolumes()
    }

    fun loadSongA(song: MusicItem) {
        playerA?.let { player ->
            _currentSongA.value = song
            player.stop()
            player.clearMediaItems()
            val mediaItem = MediaItem.fromUri(Uri.parse(song.path))
            player.setMediaItem(mediaItem)
            player.prepare()
            _positionA.value = 0L
            _durationA.value = 0L
        }
    }

    fun loadSongB(song: MusicItem) {
        playerB?.let { player ->
            _currentSongB.value = song
            player.stop()
            player.clearMediaItems()
            val mediaItem = MediaItem.fromUri(Uri.parse(song.path))
            player.setMediaItem(mediaItem)
            player.prepare()
            _positionB.value = 0L
            _durationB.value = 0L
        }
    }

    fun togglePlayPauseA() {
        playerA?.let { player ->
            if (player.isPlaying) {
                player.pause()
            } else {
                if (player.playbackState == Player.STATE_IDLE || player.playbackState == Player.STATE_ENDED) {
                    player.prepare()
                }
                player.play()
            }
        }
    }

    fun togglePlayPauseB() {
        playerB?.let { player ->
            if (player.isPlaying) {
                player.pause()
            } else {
                if (player.playbackState == Player.STATE_IDLE || player.playbackState == Player.STATE_ENDED) {
                    player.prepare()
                }
                player.play()
            }
        }
    }

    fun seekA(positionMs: Long) {
        playerA?.seekTo(positionMs)
        _positionA.value = positionMs
    }

    fun seekB(positionMs: Long) {
        playerB?.seekTo(positionMs)
        _positionB.value = positionMs
    }

    fun setVolumeA(vol: Float) {
        _volumeA.value = vol.coerceIn(0f, 1f)
        updateAppliedVolumes()
    }

    fun setVolumeB(vol: Float) {
        _volumeB.value = vol.coerceIn(0f, 1f)
        updateAppliedVolumes()
    }

    fun setCrossfader(value: Float) {
        _crossfader.value = value.coerceIn(0f, 1f)
        updateAppliedVolumes()
    }

    fun setPitchA(pitch: Float) {
        val p = pitch.coerceIn(0.5f, 2.0f)
        _pitchA.value = p
        playerA?.playbackParameters = PlaybackParameters(p)
    }

    fun setPitchB(pitch: Float) {
        val p = pitch.coerceIn(0.5f, 2.0f)
        _pitchB.value = p
        playerB?.playbackParameters = PlaybackParameters(p)
    }

    private fun updateAppliedVolumes() {
        val cf = _crossfader.value
        // Professional constant power crossfader curve
        val factorA = if (cf <= 0.5f) 1.0f else ((1.0f - cf) / 0.5f)
        val factorB = if (cf >= 0.5f) 1.0f else (cf / 0.5f)

        playerA?.volume = _volumeA.value * factorA
        playerB?.volume = _volumeB.value * factorB
    }

    // EQ controls: value range is -1.0 to 1.0
    fun setEqBassA(value: Float) {
        _eqBassA.value = value.coerceIn(-1f, 1f)
        applyEqBand(eqA, 0, _eqBassA.value)
    }

    fun setEqMidA(value: Float) {
        _eqMidA.value = value.coerceIn(-1f, 1f)
        val eqInstance = eqA ?: return
        val band = if (eqInstance.numberOfBands >= 5) 2 else (eqInstance.numberOfBands / 2)
        applyEqBand(eqInstance, band, _eqMidA.value)
    }

    fun setEqTrebleA(value: Float) {
        _eqTrebleA.value = value.coerceIn(-1f, 1f)
        val eqInstance = eqA ?: return
        val band = if (eqInstance.numberOfBands >= 5) 4 else (eqInstance.numberOfBands - 1)
        applyEqBand(eqInstance, band, _eqTrebleA.value)
    }

    fun setEqBassB(value: Float) {
        _eqBassB.value = value.coerceIn(-1f, 1f)
        applyEqBand(eqB, 0, _eqBassB.value)
    }

    fun setEqMidB(value: Float) {
        _eqMidB.value = value.coerceIn(-1f, 1f)
        val eqInstance = eqB ?: return
        val band = if (eqInstance.numberOfBands >= 5) 2 else (eqInstance.numberOfBands / 2)
        applyEqBand(eqInstance, band, _eqMidB.value)
    }

    fun setEqTrebleB(value: Float) {
        _eqTrebleB.value = value.coerceIn(-1f, 1f)
        val eqInstance = eqB ?: return
        val band = if (eqInstance.numberOfBands >= 5) 4 else (eqInstance.numberOfBands - 1)
        applyEqBand(eqInstance, band, _eqTrebleB.value)
    }

    private fun setupEqA(audioSessionId: Int) {
        runCatching {
            eqA?.release()
            if (audioSessionId != 0) {
                eqA = Equalizer(0, audioSessionId).apply {
                    enabled = true
                }
                // Apply current flow values to the new equalizer
                applyEqBand(eqA, 0, _eqBassA.value)
                val eqInstance = eqA!!
                val midBand = if (eqInstance.numberOfBands >= 5) 2 else (eqInstance.numberOfBands / 2)
                applyEqBand(eqInstance, midBand, _eqMidA.value)
                val trebleBand = if (eqInstance.numberOfBands >= 5) 4 else (eqInstance.numberOfBands - 1)
                applyEqBand(eqInstance, trebleBand, _eqTrebleA.value)
            }
        }
    }

    private fun setupEqB(audioSessionId: Int) {
        runCatching {
            eqB?.release()
            if (audioSessionId != 0) {
                eqB = Equalizer(0, audioSessionId).apply {
                    enabled = true
                }
                // Apply current flow values to the new equalizer
                applyEqBand(eqB, 0, _eqBassB.value)
                val eqInstance = eqB!!
                val midBand = if (eqInstance.numberOfBands >= 5) 2 else (eqInstance.numberOfBands / 2)
                applyEqBand(eqInstance, midBand, _eqMidB.value)
                val trebleBand = if (eqInstance.numberOfBands >= 5) 4 else (eqInstance.numberOfBands - 1)
                applyEqBand(eqInstance, trebleBand, _eqTrebleB.value)
            }
        }
    }

    private fun applyEqBand(eq: Equalizer?, band: Int, normalizedValue: Float) {
        runCatching {
            val eqInstance = eq ?: return
            if (band < eqInstance.numberOfBands) {
                val range = eqInstance.bandLevelRange
                val minLevel = range[0]
                val maxLevel = range[1]
                // map -1f..1f to minLevel..maxLevel
                val level = minLevel + (maxLevel - minLevel) * (normalizedValue + 1f) / 2f
                eqInstance.setBandLevel(band.toShort(), level.toInt().toShort())
            }
        }
    }

    private fun startUpdatesA() {
        stopUpdatesA()
        jobA = scope.launch {
            while (isActive) {
                playerA?.let { _positionA.value = it.currentPosition }
                delay(200)
            }
        }
    }

    private fun stopUpdatesA() {
        jobA?.cancel()
        jobA = null
    }

    private fun startUpdatesB() {
        stopUpdatesB()
        jobB = scope.launch {
            while (isActive) {
                playerB?.let { _positionB.value = it.currentPosition }
                delay(200)
            }
        }
    }

    private fun stopUpdatesB() {
        jobB?.cancel()
        jobB = null
    }

    fun release() {
        stopUpdatesA()
        stopUpdatesB()
        runCatching {
            eqA?.release()
            eqA = null
            eqB?.release()
            eqB = null
        }
        playerA?.release()
        playerA = null
        playerB?.release()
        playerB = null
    }
}
