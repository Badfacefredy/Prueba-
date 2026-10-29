package com.example.player

import android.content.Context
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.net.Uri
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

class AudioPlayerManager(
    private val scope: CoroutineScope
) {
    private var mediaPlayer: MediaPlayer? = null
    private var tickerJob: Job? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _currentSpeed = MutableStateFlow(1.0f)
    val currentSpeed: StateFlow<Float> = _currentSpeed.asStateFlow()

    private val _isLoaded = MutableStateFlow(false)
    val isLoaded: StateFlow<Boolean> = _isLoaded.asStateFlow()

    private val _trackName = MutableStateFlow("Sin canción cargada")
    val trackName: StateFlow<String> = _trackName.asStateFlow()

    fun loadFromFile(file: File, displayName: String = file.name) {
        releasePlayer()
        try {
            val player = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
            }
            setupPlayer(player, displayName)
        } catch (e: Exception) {
            Log.e("AudioPlayerManager", "Error loading file: ${file.path}", e)
        }
    }

    fun loadFromUri(context: Context, uri: Uri, displayName: String) {
        releasePlayer()
        try {
            val player = MediaPlayer().apply {
                setDataSource(context, uri)
                prepare()
            }
            setupPlayer(player, displayName)
        } catch (e: Exception) {
            Log.e("AudioPlayerManager", "Error loading uri: $uri", e)
        }
    }

    private fun setupPlayer(player: MediaPlayer, displayName: String) {
        mediaPlayer = player
        _durationMs.value = player.duration.toLong().coerceAtLeast(0L)
        _currentPositionMs.value = 0L
        _isPlaying.value = false
        _isLoaded.value = true
        _trackName.value = displayName

        player.setOnCompletionListener {
            _isPlaying.value = false
            _currentPositionMs.value = _durationMs.value
            stopTicker()
        }

        applySpeed(_currentSpeed.value)
    }

    fun togglePlayPause() {
        val player = mediaPlayer ?: return
        if (player.isPlaying) {
            pause()
        } else {
            play()
        }
    }

    fun play() {
        val player = mediaPlayer ?: return
        if (!_isPlaying.value) {
            try {
                player.start()
                _isPlaying.value = true
                applySpeed(_currentSpeed.value)
                startTicker()
            } catch (e: Exception) {
                Log.e("AudioPlayerManager", "Error starting playback", e)
            }
        }
    }

    fun pause() {
        val player = mediaPlayer ?: return
        if (_isPlaying.value) {
            try {
                player.pause()
                _isPlaying.value = false
                stopTicker()
                _currentPositionMs.value = player.currentPosition.toLong()
            } catch (e: Exception) {
                Log.e("AudioPlayerManager", "Error pausing playback", e)
            }
        }
    }

    fun seekTo(positionMs: Long) {
        val player = mediaPlayer ?: return
        val clamped = positionMs.coerceIn(0L, _durationMs.value)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                player.seekTo(clamped, MediaPlayer.SEEK_CLOSEST)
            } else {
                player.seekTo(clamped.toInt())
            }
            _currentPositionMs.value = clamped
        } catch (e: Exception) {
            Log.e("AudioPlayerManager", "Error seeking", e)
        }
    }

    fun skipBy(deltaMs: Long) {
        seekTo(_currentPositionMs.value + deltaMs)
    }

    fun setSpeed(speed: Float) {
        _currentSpeed.value = speed
        applySpeed(speed)
    }

    private fun applySpeed(speed: Float) {
        val player = mediaPlayer ?: return
        try {
            val params = player.playbackParams ?: PlaybackParams()
            params.speed = speed
            player.playbackParams = params
        } catch (e: Exception) {
            Log.w("AudioPlayerManager", "PlaybackParams speed not supported: $speed", e)
        }
    }

    private fun startTicker() {
        stopTicker()
        tickerJob = scope.launch(Dispatchers.Default) {
            while (isActive && _isPlaying.value) {
                mediaPlayer?.let { player ->
                    try {
                        if (player.isPlaying) {
                            _currentPositionMs.value = player.currentPosition.toLong()
                        }
                    } catch (_: Exception) { }
                }
                delay(25) // Smooth 40fps updates
            }
        }
    }

    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    fun release() {
        releasePlayer()
    }

    private fun releasePlayer() {
        stopTicker()
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (_: Exception) { }
        mediaPlayer = null
        _isPlaying.value = false
        _isLoaded.value = false
        _currentPositionMs.value = 0L
    }
}
