package com.example.data.media

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import com.example.data.model.PickedMediaInfo
import com.example.data.model.SplitPartItem
import com.example.data.model.VideoCompressPreset
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.util.Locale
import kotlin.coroutines.coroutineContext

data class SplitExportedPart(
    val savedUri: Uri,
    val fileName: String,
    val outputSizeBytes: Long
)

/**
 * Native Android Video Splitter supporting:
 * 1) Instant lossless stream copy via MediaExtractor + MediaMuxer (no re-encoding)
 * 2) Hardware-accelerated cut + compress via Media3 Transformer ClippingConfiguration
 *
 * Every finished part is committed immediately to MediaStore (Movies/Ordieo_Splits/)
 * before the temp file is removed. Incomplete temp files are always deleted on skip/cancel/error.
 */
object VideoSplitter {

    private const val MIN_CHUNK_MS = 1_000L
    private const val MIN_TRAILING_PART_MS = 500L

    /**
     * Splits a total duration into sequential 1-based [SplitPartItem] ranges.
     * Any tiny remainder (< 500 ms) after at least one full part is merged into the last part
     * so we never create a corrupt 0-frame tail clip.
     */
    fun buildSplitParts(durationMs: Long, chunkDurationMs: Long): List<SplitPartItem> {
        if (durationMs <= 0L) return emptyList()
        val safeChunk = chunkDurationMs.coerceAtLeast(MIN_CHUNK_MS)
        val parts = mutableListOf<SplitPartItem>()
        var start = 0L
        var index = 1
        while (start < durationMs) {
            val remaining = durationMs - start
            if (remaining < MIN_TRAILING_PART_MS && parts.isNotEmpty()) {
                val last = parts.removeAt(parts.lastIndex)
                parts.add(last.copy(endMs = durationMs))
                break
            }
            val end = (start + safeChunk).coerceAtMost(durationMs)
            parts.add(
                SplitPartItem(
                    index = index,
                    startMs = start,
                    endMs = end,
                    selected = true
                )
            )
            start = end
            index++
        }
        return parts
    }

    /**
     * Builds a clean MediaStore display name such as `my_video_part_01.mp4` (or `_part_001.mp4` for 100+ parts).
     */
    fun formatPartFileName(baseName: String, partIndex: Int, totalParts: Int): String {
        val safeBase = baseName
            .substringBeforeLast('.')
            .replace(Regex("""[^a-zA-Z0-9._\-]+"""), "_")
            .trim('_')
            .ifBlank { "video" }
        val width = if (totalParts >= 100) 3 else 2
        val padded = partIndex.coerceAtLeast(1).toString().padStart(width, '0')
        return "${safeBase}_part_${padded}.mp4"
    }

    /**
     * Formats a timestamp cleanly as `MM:SS` (or `H:MM:SS` for videos >= 1 hour).
     */
    fun formatRangeTimestamp(ms: Long): String {
        val totalSeconds = (ms.coerceAtLeast(0L) / 1000L)
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0L) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    /**
     * Exports a single split part to a temporary file, immediately commits it to
     * `Movies/Ordieo_Splits/<fileName>` via MediaStore, and deletes the temporary file.
     */
    suspend fun exportSinglePart(
        context: Context,
        video: PickedMediaInfo,
        part: SplitPartItem,
        totalParts: Int,
        compressWhileExporting: Boolean,
        videoPreset: VideoCompressPreset,
        fixHdrColors: Boolean,
        onProgress: (Float, String) -> Unit
    ): SplitExportedPart {
        val uri = Uri.parse(video.uriString)
        val fileName = formatPartFileName(video.displayName, part.index, totalParts)
        val tempDir = File(context.cacheDir, "split_tmp").apply { if (!exists()) mkdirs() }
        val tempFile = File(tempDir, "split_p${part.index}_${System.currentTimeMillis()}.mp4")

        try {
            if (compressWhileExporting) {
                MediaCompressor.compressVideoWithTransformer(
                    context = context,
                    uri = uri,
                    info = video,
                    preset = videoPreset,
                    fixHdrColors = fixHdrColors,
                    outputFile = tempFile,
                    clipStartMs = part.startMs,
                    clipEndMs = part.endMs,
                    onProgress = { pct, status ->
                        onProgress((pct * 0.92f).coerceIn(0.02f, 0.92f), status)
                    }
                )
            } else {
                splitSegmentInstant(
                    context = context,
                    uri = uri,
                    startMs = part.startMs,
                    endMs = part.endMs,
                    outputFile = tempFile,
                    onProgress = { pct, status ->
                        onProgress((pct * 0.92f).coerceIn(0.02f, 0.92f), status)
                    }
                )
            }

            coroutineContext.ensureActive()
            onProgress(0.95f, "Saving to Gallery...")
            val outputSize = tempFile.length()
            val savedUri = LocalFileSaver.saveVideoToMoviesSplits(
                context = context,
                sourceFile = tempFile,
                fileName = fileName
            ).getOrThrow()

            onProgress(1f, "Saved")
            return SplitExportedPart(
                savedUri = savedUri,
                fileName = fileName,
                outputSizeBytes = outputSize
            )
        } catch (e: CancellationException) {
            runCatching { tempFile.delete() }
            throw e
        } catch (e: Exception) {
            runCatching { tempFile.delete() }
            throw e
        } finally {
            runCatching { tempFile.delete() }
        }
    }

    /**
     * Instant lossless stream copy using Android's native [MediaExtractor] + [MediaMuxer].
     * Performs zero re-encoding: seeks to the nearest preceding video sync frame, copies video
     * and audio packets with rebased timestamps starting at 0, and preserves rotation metadata.
     */
    private suspend fun splitSegmentInstant(
        context: Context,
        uri: Uri,
        startMs: Long,
        endMs: Long,
        outputFile: File,
        onProgress: (Float, String) -> Unit
    ) = withContext(Dispatchers.IO) {
        onProgress(0.04f, "Preparing lossless stream copy...")
        outputFile.parentFile?.let { if (!it.exists()) it.mkdirs() }
        if (outputFile.exists()) {
            runCatching { outputFile.delete() }
        }

        val startUs = startMs.coerceAtLeast(0L) * 1000L
        val endUs = endMs.coerceAtLeast(startMs + 100L) * 1000L
        val segmentDurationUs = (endUs - startUs).coerceAtLeast(1_000L)

        // Extract rotation metadata so portrait videos keep exact orientation in output MP4
        val rotationDegrees = runCatching {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                    ?.toIntOrNull() ?: 0
            } finally {
                runCatching { retriever.release() }
            }
        }.getOrDefault(0)

        val videoExtractor = MediaExtractor()
        val audioExtractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        var muxerStarted = false

        try {
            setExtractorSource(context, videoExtractor, uri)
            var srcVideoTrack = -1
            var srcAudioTrack = -1
            var videoFormat: MediaFormat? = null
            var audioFormat: MediaFormat? = null

            for (i in 0 until videoExtractor.trackCount) {
                val format = videoExtractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                if (srcVideoTrack < 0 && mime.startsWith("video/")) {
                    srcVideoTrack = i
                    videoFormat = format
                } else if (srcAudioTrack < 0 && mime.startsWith("audio/")) {
                    srcAudioTrack = i
                    audioFormat = format
                }
            }

            if (srcVideoTrack < 0 || videoFormat == null) {
                error("No video track found in selected file.")
            }

            val createdMuxer = MediaMuxer(
                outputFile.absolutePath,
                MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
            )
            muxer = createdMuxer
            if (rotationDegrees != 0) {
                runCatching { createdMuxer.setOrientationHint(rotationDegrees) }
            }

            val muxVideoTrack = createdMuxer.addTrack(videoFormat)
            var muxAudioTrack = -1
            if (srcAudioTrack >= 0 && audioFormat != null) {
                muxAudioTrack = runCatching { createdMuxer.addTrack(audioFormat) }.getOrDefault(-1)
            }

            createdMuxer.start()
            muxerStarted = true

            // Allocate generous sample buffer for 4K / high-bitrate keyframes
            val maxVideoInput = runCatching {
                videoFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
            }.getOrDefault(0)
            val bufferSize = maxVideoInput.coerceIn(512 * 1024, 4 * 1024 * 1024)
            val sampleBuffer = ByteBuffer.allocateDirect(bufferSize)
            val bufferInfo = MediaCodec.BufferInfo()

            // 1. Copy Video Track (keyframe-aligned start)
            videoExtractor.selectTrack(srcVideoTrack)
            videoExtractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

            var baseTimestampUs = -1L
            var lastVideoPtsUs = -1L
            var videoSamplesWritten = 0
            val videoWeight = if (muxAudioTrack >= 0) 0.72f else 0.92f

            while (true) {
                coroutineContext.ensureActive()
                sampleBuffer.clear()
                val sampleSize = videoExtractor.readSampleData(sampleBuffer, 0)
                if (sampleSize < 0) break

                val sampleTimeUs = videoExtractor.sampleTime
                if (sampleTimeUs < 0L) break
                if (sampleTimeUs >= endUs && videoSamplesWritten > 0) break

                if (baseTimestampUs < 0L) {
                    baseTimestampUs = sampleTimeUs
                }

                var rebasedPts = (sampleTimeUs - baseTimestampUs).coerceAtLeast(0L)
                if (rebasedPts < lastVideoPtsUs) {
                    // Guard against slight B-frame DTS/PTS inversion in quirky containers
                    rebasedPts = lastVideoPtsUs
                }
                lastVideoPtsUs = rebasedPts

                bufferInfo.offset = 0
                bufferInfo.size = sampleSize
                bufferInfo.presentationTimeUs = rebasedPts
                bufferInfo.flags = mapExtractorFlagsToCodecFlags(videoExtractor.sampleFlags)

                createdMuxer.writeSampleData(muxVideoTrack, sampleBuffer, bufferInfo)
                videoSamplesWritten++

                if (videoSamplesWritten % 12 == 0) {
                    val elapsed = (sampleTimeUs - startUs).coerceAtLeast(0L)
                    val ratio = (elapsed.toFloat() / segmentDurationUs.toFloat()).coerceIn(0f, 1f)
                    onProgress(0.06f + ratio * videoWeight, "Splitting stream...")
                }

                if (!videoExtractor.advance()) break
            }

            if (videoSamplesWritten == 0) {
                error("No video frames found in selected time range.")
            }

            // 2. Copy Audio Track aligned to the exact video start timestamp
            if (muxAudioTrack >= 0 && srcAudioTrack >= 0) {
                setExtractorSource(context, audioExtractor, uri)
                audioExtractor.selectTrack(srcAudioTrack)
                val audioAnchorUs = if (baseTimestampUs >= 0L) baseTimestampUs else startUs
                audioExtractor.seekTo(audioAnchorUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

                var lastAudioPtsUs = -1L
                var audioSamplesWritten = 0

                while (true) {
                    coroutineContext.ensureActive()
                    sampleBuffer.clear()
                    val sampleSize = audioExtractor.readSampleData(sampleBuffer, 0)
                    if (sampleSize < 0) break

                    val sampleTimeUs = audioExtractor.sampleTime
                    if (sampleTimeUs < 0L) break
                    if (sampleTimeUs >= endUs) break

                    if (sampleTimeUs < audioAnchorUs) {
                        if (!audioExtractor.advance()) break
                        continue
                    }

                    var rebasedPts = (sampleTimeUs - audioAnchorUs).coerceAtLeast(0L)
                    if (rebasedPts <= lastAudioPtsUs) {
                        rebasedPts = lastAudioPtsUs + 1L
                    }
                    lastAudioPtsUs = rebasedPts

                    bufferInfo.offset = 0
                    bufferInfo.size = sampleSize
                    bufferInfo.presentationTimeUs = rebasedPts
                    bufferInfo.flags = mapExtractorFlagsToCodecFlags(audioExtractor.sampleFlags)

                    createdMuxer.writeSampleData(muxAudioTrack, sampleBuffer, bufferInfo)
                    audioSamplesWritten++

                    if (audioSamplesWritten % 25 == 0) {
                        val elapsed = (sampleTimeUs - audioAnchorUs).coerceAtLeast(0L)
                        val ratio = (elapsed.toFloat() / segmentDurationUs.toFloat()).coerceIn(0f, 1f)
                        onProgress(0.78f + ratio * 0.18f, "Copying audio track...")
                    }

                    if (!audioExtractor.advance()) break
                }
            }

            onProgress(1f, "Done")
        } catch (e: CancellationException) {
            runCatching { outputFile.delete() }
            throw e
        } catch (e: Exception) {
            runCatching { outputFile.delete() }
            throw IllegalStateException("Lossless split failed: ${e.message ?: "Unsupported container"}", e)
        } finally {
            runCatching { videoExtractor.release() }
            runCatching { audioExtractor.release() }
            if (muxerStarted) {
                runCatching { muxer?.stop() }
            }
            runCatching { muxer?.release() }
        }
    }

    private fun setExtractorSource(context: Context, extractor: MediaExtractor, uri: Uri) {
        try {
            extractor.setDataSource(context, uri, null)
        } catch (_: Exception) {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                extractor.setDataSource(pfd.fileDescriptor)
            } ?: error("Cannot open selected video file.")
        }
    }

    private fun mapExtractorFlagsToCodecFlags(sampleFlags: Int): Int {
        var codecFlags = 0
        if ((sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
            codecFlags = codecFlags or MediaCodec.BUFFER_FLAG_KEY_FRAME
        }
        if ((sampleFlags and MediaExtractor.SAMPLE_FLAG_PARTIAL_FRAME) != 0) {
            codecFlags = codecFlags or MediaCodec.BUFFER_FLAG_PARTIAL_FRAME
        }
        return codecFlags
    }
}
