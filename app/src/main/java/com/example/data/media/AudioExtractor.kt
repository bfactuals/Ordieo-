package com.example.data.media

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.abs

data class ExtractedAudioResult(
    val file: File,
    val mimeType: String,
    val durationMs: Long,
    val displayName: String,
    val waveform: List<Float>
)

object AudioExtractor {

    private const val WAVEFORM_BARS = 56

    fun getAudioStorageDir(context: Context): File {
        return File(context.filesDir, "audio").apply {
            if (!exists()) mkdirs()
        }
    }

    fun resolveDisplayName(context: Context, uri: Uri, fallback: String): String {
        val name = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) cursor.getString(idx) else null
                    } else null
                }
        }.getOrNull()
        val raw = name?.takeIf { it.isNotBlank() } ?: fallback
        return raw.substringBeforeLast('.').ifBlank { fallback }
    }

    /**
     * Locally extracts the audio track from a selected video file into a lightweight .m4a
     * file inside app-private internal storage using MediaExtractor + MediaMuxer.
     */
    suspend fun extractAudioFromVideo(
        context: Context,
        videoUri: Uri,
        projectId: String
    ): ExtractedAudioResult = withContext(Dispatchers.IO) {
        val displayName = resolveDisplayName(context, videoUri, "Video Transcription")
        val audioDir = getAudioStorageDir(context)
        val m4aFile = File(audioDir, "$projectId.m4a")

        var extractedFile: File? = null
        var mimeType = "audio/mp4"

        val extractor = MediaExtractor()
        try {
            val pfd = context.contentResolver.openFileDescriptor(videoUri, "r")
                ?: throw IllegalStateException("Unable to open selected video file.")
            pfd.use { descriptor ->
                extractor.setDataSource(descriptor.fileDescriptor)
                var audioTrackIndex = -1
                var audioFormat: MediaFormat? = null
                for (i in 0 until extractor.trackCount) {
                    val format = extractor.getTrackFormat(i)
                    val trackMime = format.getString(MediaFormat.KEY_MIME) ?: continue
                    if (trackMime.startsWith("audio/")) {
                        audioTrackIndex = i
                        audioFormat = format
                        break
                    }
                }

                if (audioTrackIndex == -1 || audioFormat == null) {
                    throw IllegalStateException("No audio track found in the selected video.")
                }

                val trackMime = audioFormat.getString(MediaFormat.KEY_MIME) ?: "audio/mp4a-latm"
                val useWebm = trackMime.contains("opus", ignoreCase = true) ||
                    trackMime.contains("vorbis", ignoreCase = true)

                val targetFile = if (useWebm) File(audioDir, "$projectId.webm") else m4aFile
                val muxerFormat = if (useWebm) {
                    MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM
                } else {
                    MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
                }
                mimeType = if (useWebm) "audio/webm" else "audio/mp4"

                extractor.selectTrack(audioTrackIndex)
                val maxBufferSize = if (audioFormat.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                    audioFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE).coerceAtLeast(256 * 1024)
                } else {
                    256 * 1024
                }
                val buffer = ByteBuffer.allocate(maxBufferSize)
                val bufferInfo = MediaCodec.BufferInfo()

                val muxer = MediaMuxer(targetFile.absolutePath, muxerFormat)
                var muxerStarted = false
                try {
                    val muxerTrackIndex = muxer.addTrack(audioFormat)
                    muxer.start()
                    muxerStarted = true

                    var firstSampleTime = -1L
                    while (true) {
                        bufferInfo.offset = 0
                        bufferInfo.size = extractor.readSampleData(buffer, 0)
                        if (bufferInfo.size < 0) break

                        val sampleTime = extractor.sampleTime
                        if (firstSampleTime < 0 && sampleTime >= 0) {
                            firstSampleTime = sampleTime
                        }
                        bufferInfo.presentationTimeUs = (sampleTime - firstSampleTime).coerceAtLeast(0L)
                        bufferInfo.flags = extractor.sampleFlags
                        muxer.writeSampleData(muxerTrackIndex, buffer, bufferInfo)
                        extractor.advance()
                    }
                    extractedFile = targetFile
                } finally {
                    if (muxerStarted) {
                        runCatching { muxer.stop() }
                    }
                    runCatching { muxer.release() }
                }
            }
        } catch (e: Exception) {
            // If MediaMuxer couldn't remux an exotic container codec, fall back to copying the raw media
            runCatching { m4aFile.delete() }
            val fallbackExt = guessExtension(context.contentResolver.getType(videoUri))
            val fallbackFile = File(audioDir, "$projectId.$fallbackExt")
            context.contentResolver.openInputStream(videoUri)?.use { input ->
                fallbackFile.outputStream().use { output -> input.copyTo(output) }
            } ?: throw e
            extractedFile = fallbackFile
            mimeType = context.contentResolver.getType(videoUri) ?: "video/mp4"
        } finally {
            runCatching { extractor.release() }
        }

        val finalFile = extractedFile ?: throw IllegalStateException("Audio extraction failed.")
        val durationMs = probeDurationMs(finalFile)
        val waveform = extractWaveformPeaks(finalFile)

        ExtractedAudioResult(
            file = finalFile,
            mimeType = mimeType,
            durationMs = durationMs,
            displayName = displayName,
            waveform = waveform
        )
    }

    /**
     * Copies a selected audio file into local app storage and samples duration & waveform.
     */
    suspend fun importAudioFile(
        context: Context,
        audioUri: Uri,
        projectId: String
    ): ExtractedAudioResult = withContext(Dispatchers.IO) {
        val displayName = resolveDisplayName(context, audioUri, "Audio Transcription")
        val contentMime = context.contentResolver.getType(audioUri) ?: "audio/mpeg"
        val ext = guessExtension(contentMime)
        val targetFile = File(getAudioStorageDir(context), "$projectId.$ext")

        context.contentResolver.openInputStream(audioUri)?.use { input ->
            targetFile.outputStream().use { output ->
                input.copyTo(output)
            }
        } ?: throw IllegalStateException("Unable to read selected audio file.")

        val durationMs = probeDurationMs(targetFile)
        val waveform = extractWaveformPeaks(targetFile)

        ExtractedAudioResult(
            file = targetFile,
            mimeType = normalizeAudioMime(contentMime, ext),
            durationMs = durationMs,
            displayName = displayName,
            waveform = waveform
        )
    }

    private fun probeDurationMs(file: File): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?.coerceAtLeast(1000L) ?: 10_000L
        } catch (_: Exception) {
            10_000L
        } finally {
            runCatching { retriever.release() }
        }
    }

    /**
     * Samples compressed frame energy/size distribution across the audio track to produce
     * an authentic waveform profile without heavy full-file PCM decoding.
     */
    private fun extractWaveformPeaks(file: File): List<Float> {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(file.absolutePath)
            var audioTrack = -1
            for (i in 0 until extractor.trackCount) {
                val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    audioTrack = i
                    break
                }
            }
            if (audioTrack == -1) return defaultWaveform()

            extractor.selectTrack(audioTrack)
            val format = extractor.getTrackFormat(audioTrack)
            val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) {
                format.getLong(MediaFormat.KEY_DURATION).coerceAtLeast(1L)
            } else {
                0L
            }

            val buckets = DoubleArray(WAVEFORM_BARS)
            val counts = IntArray(WAVEFORM_BARS)
            val sampleBuf = ByteBuffer.allocate(32 * 1024)

            if (durationUs > 0L) {
                val stepUs = (durationUs / WAVEFORM_BARS).coerceAtLeast(10_000L)
                for (i in 0 until WAVEFORM_BARS) {
                    extractor.seekTo(i * stepUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                    var energy = 0.0
                    var reads = 0
                    while (reads < 4) {
                        val size = extractor.readSampleData(sampleBuf, 0)
                        if (size <= 0) break
                        var byteMag = 0.0
                        val limit = size.coerceAtMost(256)
                        for (b in 0 until limit) {
                            byteMag += abs(sampleBuf.get(b).toInt())
                        }
                        energy += (size.toDouble() * 0.65) + (byteMag / limit.coerceAtLeast(1)) * 12.0
                        reads++
                        extractor.advance()
                    }
                    buckets[i] = if (reads > 0) energy / reads else 0.0
                    counts[i] = reads
                }
            } else {
                var idx = 0
                while (idx < 600) {
                    val size = extractor.readSampleData(sampleBuf, 0)
                    if (size <= 0) break
                    val bucket = (idx * WAVEFORM_BARS) / 600
                    buckets[bucket.coerceIn(0, WAVEFORM_BARS - 1)] += size.toDouble()
                    counts[bucket.coerceIn(0, WAVEFORM_BARS - 1)]++
                    extractor.advance()
                    idx++
                }
                for (i in 0 until WAVEFORM_BARS) {
                    if (counts[i] > 0) buckets[i] /= counts[i]
                }
            }

            val maxVal = buckets.maxOrNull()?.takeIf { it > 0.0 } ?: return defaultWaveform()
            val minVal = buckets.filter { it > 0.0 }.minOrNull() ?: 0.0
            val range = (maxVal - minVal).coerceAtLeast(1.0)

            buckets.map { v ->
                val normalized = ((v - minVal * 0.6) / range).toFloat()
                normalized.coerceIn(0.14f, 1.0f)
            }
        } catch (_: Exception) {
            defaultWaveform()
        } finally {
            runCatching { extractor.release() }
        }
    }

    fun defaultWaveform(): List<Float> = List(WAVEFORM_BARS) { i ->
        val wave = kotlin.math.sin(i * 0.42) * 0.32 + kotlin.math.cos(i * 0.19) * 0.22 + 0.52
        wave.toFloat().coerceIn(0.16f, 0.95f)
    }

    private fun guessExtension(mimeType: String?): String = when {
        mimeType == null -> "m4a"
        mimeType.contains("mpeg") || mimeType.contains("mp3") -> "mp3"
        mimeType.contains("wav") -> "wav"
        mimeType.contains("ogg") -> "ogg"
        mimeType.contains("webm") -> "webm"
        mimeType.contains("flac") -> "flac"
        mimeType.contains("aac") || mimeType.contains("mp4") || mimeType.contains("m4a") -> "m4a"
        else -> "m4a"
    }

    private fun normalizeAudioMime(mimeType: String, ext: String): String = when (ext) {
        "mp3" -> "audio/mpeg"
        "wav" -> "audio/wav"
        "ogg" -> "audio/ogg"
        "webm" -> "audio/webm"
        "flac" -> "audio/flac"
        "m4a" -> "audio/mp4"
        else -> mimeType
    }
}
