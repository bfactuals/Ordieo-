package com.example.data.media

import android.media.AudioAttributes
import android.media.MediaPlayer
import com.example.data.model.PlaybackSpeed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

data class PlaybackState(
    val isPrepared: Boolean = false,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val hasAudioFile: Boolean = false,
    val speed: PlaybackSpeed = PlaybackSpeed.X1
)

class AudioPlayerController(private val scope: CoroutineScope) {

    private var mediaPlayer: MediaPlayer? = null
    private var tickerJob: Job? = null
    private var loadedPath: String? = null

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    fun loadAudio(filePath: String?, fallbackDurationMs: Long) {
        if (filePath == loadedPath && mediaPlayer != null) return
        val preservedSpeed = _state.value.speed
        release()
        loadedPath = filePath

        if (filePath.isNullOrBlank() || !File(filePath).exists()) {
            _state.value = PlaybackState(
                isPrepared = false,
                isPlaying = false,
                positionMs = 0L,
                durationMs = fallbackDurationMs.coerceAtLeast(1000L),
                hasAudioFile = false,
                speed = preservedSpeed
            )
            return
        }

        try {
            val mp = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(filePath)
                prepare()
                setOnCompletionListener {
                    stopTicker()
                    _state.value = _state.value.copy(
                        isPlaying = false,
                        positionMs = _state.value.durationMs
                    )
                }
            }
            mediaPlayer = mp
            val dur = mp.duration.toLong().takeIf { it > 0L } ?: fallbackDurationMs.coerceAtLeast(1000L)
            _state.value = PlaybackState(
                isPrepared = true,
                isPlaying = false,
                positionMs = 0L,
                durationMs = dur,
                hasAudioFile = true,
                speed = preservedSpeed
            )
        } catch (_: Exception) {
            release()
            _state.value = PlaybackState(
                isPrepared = false,
                isPlaying = false,
                positionMs = 0L,
                durationMs = fallbackDurationMs.coerceAtLeast(1000L),
                hasAudioFile = false,
                speed = preservedSpeed
            )
        }
    }

    fun cyclePlaybackSpeed() {
        setPlaybackSpeed(_state.value.speed.next())
    }

    fun setPlaybackSpeed(speed: PlaybackSpeed) {
        _state.value = _state.value.copy(speed = speed)
        val mp = mediaPlayer ?: return
        runCatching {
            val wasPlaying = mp.isPlaying
            val params = mp.playbackParams
            params.speed = speed.multiplier
            mp.playbackParams = params
            if (!wasPlaying && mp.isPlaying) {
                mp.pause()
            }
        }
    }

    fun togglePlayPause() {
        val current = _state.value
        if (current.hasAudioFile && mediaPlayer != null) {
            val mp = mediaPlayer ?: return
            if (mp.isPlaying) {
                mp.pause()
                stopTicker()
                _state.value = current.copy(
                    isPlaying = false,
                    positionMs = mp.currentPosition.toLong()
                )
            } else {
                if (mp.currentPosition >= mp.duration - 150) {
                    mp.seekTo(0)
                }
                applySpeedToPlayer(mp, current.speed)
                mp.start()
                _state.value = current.copy(isPlaying = true)
                startAudioTicker()
            }
        } else {
            if (current.isPlaying) {
                stopTicker()
                _state.value = current.copy(isPlaying = false)
            } else {
                val startPos = if (current.positionMs >= current.durationMs - 150) 0L else current.positionMs
                _state.value = current.copy(isPlaying = true, positionMs = startPos)
                startVirtualTicker()
            }
        }
    }

    fun seekTo(positionMs: Long, autoPlay: Boolean = false) {
        val current = _state.value
        val clamped = positionMs.coerceIn(0L, current.durationMs.coerceAtLeast(1L))
        val mp = mediaPlayer
        if (current.hasAudioFile && mp != null) {
            runCatching { mp.seekTo(clamped.toInt()) }
            _state.value = current.copy(positionMs = clamped)
            if (autoPlay && !mp.isPlaying) {
                applySpeedToPlayer(mp, current.speed)
                mp.start()
                _state.value = _state.value.copy(isPlaying = true)
                startAudioTicker()
            }
        } else {
            _state.value = current.copy(positionMs = clamped)
            if (autoPlay && !current.isPlaying) {
                _state.value = _state.value.copy(isPlaying = true)
                startVirtualTicker()
            }
        }
    }

    fun pause() {
        val mp = mediaPlayer
        if (mp != null && runCatching { mp.isPlaying }.getOrDefault(false)) {
            runCatching { mp.pause() }
        }
        stopTicker()
        _state.value = _state.value.copy(isPlaying = false)
    }

    private fun applySpeedToPlayer(mp: MediaPlayer, speed: PlaybackSpeed) {
        runCatching {
            val params = mp.playbackParams
            if (params.speed != speed.multiplier) {
                params.speed = speed.multiplier
                mp.playbackParams = params
            }
        }
    }

    private fun startAudioTicker() {
        stopTicker()
        tickerJob = scope.launch {
            while (isActive) {
                val mp = mediaPlayer ?: break
                val playing = runCatching { mp.isPlaying }.getOrDefault(false)
                if (!playing) break
                val pos = runCatching { mp.currentPosition.toLong() }.getOrDefault(_state.value.positionMs)
                _state.value = _state.value.copy(positionMs = pos, isPlaying = true)
                delay(40L)
            }
        }
    }

    private fun startVirtualTicker() {
        stopTicker()
        tickerJob = scope.launch {
            var lastTick = System.currentTimeMillis()
            while (isActive) {
                delay(40L)
                val now = System.currentTimeMillis()
                val cur = _state.value
                val delta = ((now - lastTick) * cur.speed.multiplier).toLong()
                lastTick = now
                if (!cur.isPlaying) break
                val nextPos = cur.positionMs + delta
                if (nextPos >= cur.durationMs) {
                    _state.value = cur.copy(positionMs = cur.durationMs, isPlaying = false)
                    break
                } else {
                    _state.value = cur.copy(positionMs = nextPos)
                }
            }
        }
    }

    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    fun release() {
        stopTicker()
        mediaPlayer?.let { mp ->
            runCatching {
                if (mp.isPlaying) mp.stop()
                mp.release()
            }
        }
        mediaPlayer = null
        loadedPath = null
        _state.value = PlaybackState()
    }
}
