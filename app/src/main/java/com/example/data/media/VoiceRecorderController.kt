package com.example.data.media

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.sqrt

data class RecordingState(
    val isRecording: Boolean = false,
    val elapsedMs: Long = 0L,
    val liveAmplitudes: List<Float> = List(36) { 0.14f }
)

data class CompletedRecording(
    val projectId: String,
    val file: File,
    val durationMs: Long,
    val waveform: List<Float>
)

class VoiceRecorderController(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private var recorder: MediaRecorder? = null
    private var meterJob: Job? = null
    private var activeProjectId: String? = null
    private var activeFile: File? = null
    private var startTimeMs: Long = 0L
    private val capturedSamples = mutableListOf<Float>()

    private val _state = MutableStateFlow(RecordingState())
    val state: StateFlow<RecordingState> = _state.asStateFlow()

    fun startRecording(projectId: String): Result<Unit> {
        cancelRecording()
        return runCatching {
            val outFile = File(AudioExtractor.getAudioStorageDir(context), "$projectId.m4a")
            val mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            mediaRecorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(128000)
                setOutputFile(outFile.absolutePath)
                prepare()
                start()
            }

            recorder = mediaRecorder
            activeProjectId = projectId
            activeFile = outFile
            startTimeMs = System.currentTimeMillis()
            capturedSamples.clear()

            _state.value = RecordingState(
                isRecording = true,
                elapsedMs = 0L,
                liveAmplitudes = List(36) { 0.14f }
            )

            meterJob = scope.launch {
                val window = ArrayDeque<Float>(36)
                repeat(36) { window.addLast(0.14f) }
                while (isActive) {
                    delay(65L)
                    val rec = recorder ?: break
                    val rawAmp = runCatching { rec.maxAmplitude }.getOrDefault(0)
                    val normalized = sqrt((rawAmp / 32767.0).coerceIn(0.0, 1.0)).toFloat()
                        .coerceIn(0.14f, 1.0f)
                    capturedSamples.add(normalized)
                    if (window.size >= 36) window.removeFirst()
                    window.addLast(normalized)
                    _state.value = RecordingState(
                        isRecording = true,
                        elapsedMs = (System.currentTimeMillis() - startTimeMs).coerceAtLeast(0L),
                        liveAmplitudes = window.toList()
                    )
                }
            }
        }
    }

    fun stopRecording(): CompletedRecording? {
        meterJob?.cancel()
        meterJob = null

        val projId = activeProjectId
        val file = activeFile
        val elapsed = (System.currentTimeMillis() - startTimeMs).coerceAtLeast(1000L)

        val stoppedOk = runCatching {
            recorder?.stop()
            true
        }.getOrDefault(false)

        runCatching { recorder?.release() }
        recorder = null
        activeProjectId = null
        activeFile = null
        _state.value = RecordingState()

        if (!stoppedOk || projId == null || file == null || !file.exists() || file.length() == 0L) {
            runCatching { file?.delete() }
            return null
        }

        val waveform = resampleWaveform(capturedSamples, 56)
        return CompletedRecording(
            projectId = projId,
            file = file,
            durationMs = elapsed,
            waveform = waveform
        )
    }

    fun cancelRecording() {
        meterJob?.cancel()
        meterJob = null
        runCatching { recorder?.stop() }
        runCatching { recorder?.release() }
        recorder = null
        runCatching { activeFile?.delete() }
        activeFile = null
        activeProjectId = null
        capturedSamples.clear()
        _state.value = RecordingState()
    }

    private fun resampleWaveform(samples: List<Float>, targetBars: Int): List<Float> {
        if (samples.isEmpty()) return AudioExtractor.defaultWaveform()
        return List(targetBars) { idx ->
            val start = (idx * samples.size) / targetBars
            val end = (((idx + 1) * samples.size) / targetBars).coerceAtLeast(start + 1).coerceAtMost(samples.size)
            val slice = samples.subList(start.coerceIn(0, samples.lastIndex), end)
            (slice.average().toFloat()).coerceIn(0.14f, 1.0f)
        }
    }
}
