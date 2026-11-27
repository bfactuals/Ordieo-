package com.example.data.media

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.annotation.OptIn
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.example.data.model.AudioCompressPreset
import com.example.data.model.CompressMediaType
import com.example.data.model.CompressedResult
import com.example.data.model.PickedMediaInfo
import com.example.data.model.VideoCompressPreset
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.roundToInt

/**
 * 100% Native Android Media Compressor (zero FFmpeg).
 * Uses hardware-accelerated Media3 Transformer (H.264/AVC + OpenGL HDR-to-SDR tone mapping)
 * for video compression, and Android MediaExtractor/MediaCodec/MediaMuxer for audio compression.
 */
object MediaCompressor {

    fun getCompressedStorageDir(context: Context): File {
        return File(context.filesDir, "compressed").apply {
            if (!exists()) mkdirs()
        }
    }

    suspend fun probeMedia(context: Context, uri: Uri): PickedMediaInfo = withContext(Dispatchers.IO) {
        var displayName = "Media File"
        var sizeBytes = 0L
        runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIdx >= 0) {
                        cursor.getString(nameIdx)?.takeIf { it.isNotBlank() }?.let { displayName = it }
                    }
                    if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                        sizeBytes = cursor.getLong(sizeIdx).coerceAtLeast(0L)
                    }
                }
            }
        }

        if (sizeBytes <= 0L) {
            runCatching {
                context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                    if (afd.length > 0L) sizeBytes = afd.length
                }
            }
        }

        var durationMs = 0L
        var width = 0
        var height = 0
        var isHdr = false
        val mimeType = context.contentResolver.getType(uri).orEmpty()

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            val rawW = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull() ?: 0
            val rawH = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull() ?: 0
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                ?.toIntOrNull() ?: 0
            if (rotation == 90 || rotation == 270) {
                width = rawH
                height = rawW
            } else {
                width = rawW
                height = rawH
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val colorTransfer = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_COLOR_TRANSFER)
                    ?.toIntOrNull()
                isHdr = colorTransfer == MediaFormat.COLOR_TRANSFER_ST2084 ||
                    colorTransfer == MediaFormat.COLOR_TRANSFER_HLG
            }
        } catch (_: Exception) {
            // Leave metadata defaults
        } finally {
            runCatching { retriever.release() }
        }

        PickedMediaInfo(
            uriString = uri.toString(),
            displayName = displayName,
            sizeBytes = sizeBytes,
            durationMs = durationMs.coerceAtLeast(1000L),
            width = width,
            height = height,
            mimeType = mimeType,
            isHdrLikely = isHdr
        )
    }

    fun estimateVideoBytes(info: PickedMediaInfo, preset: VideoCompressPreset): Long {
        val seconds = (info.durationMs / 1000.0).coerceAtLeast(1.0)
        val totalKbps = (preset.videoBitrateKbps + preset.audioBitrateKbps).toDouble()
        val estimated = ((totalKbps * 1000.0 / 8.0) * seconds).toLong()
        return if (info.sizeBytes > 0L) {
            estimated.coerceIn(64L * 1024L, info.sizeBytes)
        } else {
            estimated.coerceAtLeast(64L * 1024L)
        }
    }

    fun estimateAudioBytes(info: PickedMediaInfo, preset: AudioCompressPreset): Long {
        val seconds = (info.durationMs / 1000.0).coerceAtLeast(1.0)
        val estimated = when (preset) {
            // Mono 16 kHz 16-bit PCM WAV = 32,000 bytes/sec (~1.92 MB/min)
            AudioCompressPreset.FOR_AI_TRANSCRIBE -> (32_000.0 * seconds).toLong() + 44L
            // ~96 kbps AAC M4A = 12,000 bytes/sec (~0.72 MB/min)
            AudioCompressPreset.STANDARD_AUDIO -> (12_000.0 * seconds).toLong() + 4096L
        }
        return if (info.sizeBytes > 0L) {
            estimated.coerceIn(16L * 1024L, info.sizeBytes.coerceAtLeast(estimated.coerceAtMost(info.sizeBytes * 2)))
        } else {
            estimated.coerceAtLeast(16L * 1024L)
        }
    }

    fun formatBytes(bytes: Long): String {
        val safe = bytes.coerceAtLeast(0L)
        val kb = 1024.0
        val mb = kb * 1024.0
        val gb = mb * 1024.0
        return when {
            safe >= gb -> String.format(Locale.US, "%.2f GB", safe / gb)
            safe >= mb -> String.format(Locale.US, "%.1f MB", safe / mb)
            safe >= kb -> String.format(Locale.US, "%.0f KB", safe / kb)
            else -> "$safe B"
        }
    }

    internal fun sanitizeBaseName(displayName: String, fallback: String = "video"): String {
        return displayName
            .substringBeforeLast('.')
            .replace(Regex("""[^a-zA-Z0-9._\- ]"""), "_")
            .trim()
            .replace(' ', '_')
            .ifBlank { fallback }
    }

    suspend fun compressMedia(
        context: Context,
        mediaType: CompressMediaType,
        info: PickedMediaInfo,
        videoPreset: VideoCompressPreset,
        audioPreset: AudioCompressPreset,
        fixHdrColors: Boolean,
        onProgress: (Float, String) -> Unit
    ): CompressedResult = withContext(Dispatchers.IO) {
        val uri = Uri.parse(info.uriString)
        val safeBase = sanitizeBaseName(info.displayName, fallback = "compressed")
        val stamp = System.currentTimeMillis() / 1000L
        val outDir = getCompressedStorageDir(context)

        when (mediaType) {
            CompressMediaType.AUDIO -> {
                when (audioPreset) {
                    AudioCompressPreset.FOR_AI_TRANSCRIBE -> {
                        val outFile = File(outDir, "${safeBase}_16khz_ai_$stamp.wav")
                        compressAudioToMono16kWav(context, uri, info.durationMs, outFile, onProgress)
                        CompressedResult(
                            filePath = outFile.absolutePath,
                            fileName = outFile.name,
                            mimeType = "audio/wav",
                            outputSizeBytes = outFile.length(),
                            originalSizeBytes = info.sizeBytes,
                            mediaType = CompressMediaType.AUDIO
                        )
                    }
                    AudioCompressPreset.STANDARD_AUDIO -> {
                        val outFile = File(outDir, "${safeBase}_compact_$stamp.m4a")
                        compressAudioToStandardM4a(context, uri, info.durationMs, outFile, onProgress)
                        CompressedResult(
                            filePath = outFile.absolutePath,
                            fileName = outFile.name,
                            mimeType = "audio/mp4",
                            outputSizeBytes = outFile.length(),
                            originalSizeBytes = info.sizeBytes,
                            mediaType = CompressMediaType.AUDIO
                        )
                    }
                }
            }
            CompressMediaType.VIDEO,
            CompressMediaType.SPLIT -> {
                val suffix = when (videoPreset) {
                    VideoCompressPreset.FOR_AI_ULTRA_SMALL -> "480p_ai"
                    VideoCompressPreset.SMALL -> "720p_small"
                    VideoCompressPreset.BALANCED -> "balanced"
                    VideoCompressPreset.BEST_QUALITY -> "high"
                }
                val outFile = File(outDir, "${safeBase}_${suffix}_$stamp.mp4")
                compressVideoWithTransformer(
                    context = context,
                    uri = uri,
                    info = info,
                    preset = videoPreset,
                    fixHdrColors = fixHdrColors,
                    outputFile = outFile,
                    onProgress = onProgress
                )
                CompressedResult(
                    filePath = outFile.absolutePath,
                    fileName = outFile.name,
                    mimeType = "video/mp4",
                    outputSizeBytes = outFile.length(),
                    originalSizeBytes = info.sizeBytes,
                    mediaType = CompressMediaType.VIDEO
                )
            }
        }
    }

    /**
     * Decodes any audio/video URI via MediaExtractor + MediaCodec PCM, downmixes to Mono,
     * resamples to 16 kHz 16-bit PCM, and writes a clean RIFF WAV optimized for AI transcription.
     */
    private suspend fun compressAudioToMono16kWav(
        context: Context,
        uri: Uri,
        durationMs: Long,
        outputFile: File,
        onProgress: (Float, String) -> Unit
    ) {
        onProgress(0.05f, "Reading audio stream...")
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        val pcmAccumulator = ByteArrayOutputStream()
        var srcSampleRate = 44100
        var srcChannels = 1

        try {
            val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                ?: throw IllegalStateException("Unable to open selected audio file.")
            pfd.use { descriptor ->
                extractor.setDataSource(descriptor.fileDescriptor)
                var audioTrack = -1
                var format: MediaFormat? = null
                for (i in 0 until extractor.trackCount) {
                    val f = extractor.getTrackFormat(i)
                    val m = f.getString(MediaFormat.KEY_MIME) ?: continue
                    if (m.startsWith("audio/")) {
                        audioTrack = i
                        format = f
                        break
                    }
                }
                if (audioTrack == -1 || format == null) {
                    throw IllegalStateException("No audio track found in the selected file.")
                }

                val trackMime = format.getString(MediaFormat.KEY_MIME) ?: "audio/mp4a-latm"
                srcSampleRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                    format.getInteger(MediaFormat.KEY_SAMPLE_RATE).coerceAtLeast(8000)
                } else 44100
                srcChannels = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                    format.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceIn(1, 2)
                } else 1

                extractor.selectTrack(audioTrack)
                decoder = MediaCodec.createDecoderByType(trackMime)
                decoder.configure(format, null, null, 0)
                decoder.start()

                val bufferInfo = MediaCodec.BufferInfo()
                var inputDone = false
                var outputDone = false
                val totalUs = (durationMs * 1000L).coerceAtLeast(1_000_000L)

                while (!outputDone) {
                    coroutineContext.ensureActive()
                    if (!inputDone) {
                        val inIdx = decoder.dequeueInputBuffer(10_000L)
                        if (inIdx >= 0) {
                            val inBuf = decoder.getInputBuffer(inIdx)
                            if (inBuf != null) {
                                val sampleSize = extractor.readSampleData(inBuf, 0)
                                if (sampleSize < 0) {
                                    decoder.queueInputBuffer(
                                        inIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                    )
                                    inputDone = true
                                } else {
                                    val pts = extractor.sampleTime.coerceAtLeast(0L)
                                    decoder.queueInputBuffer(inIdx, 0, sampleSize, pts, 0)
                                    extractor.advance()
                                    val pct = (pts.toFloat() / totalUs.toFloat()).coerceIn(0f, 0.85f)
                                    onProgress(0.05f + pct * 0.78f, "Converting to Mono 16 kHz...")
                                }
                            }
                        }
                    }

                    val outIdx = decoder.dequeueOutputBuffer(bufferInfo, 10_000L)
                    when {
                        outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            val outFmt = decoder.outputFormat
                            if (outFmt.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                                srcSampleRate = outFmt.getInteger(MediaFormat.KEY_SAMPLE_RATE).coerceAtLeast(8000)
                            }
                            if (outFmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                                srcChannels = outFmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceIn(1, 2)
                            }
                        }
                        outIdx >= 0 -> {
                            val outBuf = decoder.getOutputBuffer(outIdx)
                            if (outBuf != null && bufferInfo.size > 0) {
                                val chunk = ByteArray(bufferInfo.size)
                                outBuf.position(bufferInfo.offset)
                                outBuf.limit(bufferInfo.offset + bufferInfo.size)
                                outBuf.get(chunk)
                                pcmAccumulator.write(chunk)
                            }
                            decoder.releaseOutputBuffer(outIdx, false)
                            if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                outputDone = true
                            }
                        }
                    }
                }
            }
        } finally {
            runCatching { decoder?.stop() }
            runCatching { decoder?.release() }
            runCatching { extractor.release() }
        }

        coroutineContext.ensureActive()
        onProgress(0.88f, "Writing compact WAV...")
        val rawPcm = pcmAccumulator.toByteArray()
        val resampledMono16k = downmixAndResampleTo16kMono(rawPcm, srcSampleRate, srcChannels)
        writeWavFile(outputFile, resampledMono16k, 16000, 1)
        onProgress(1f, "Done")
    }

    private fun downmixAndResampleTo16kMono(
        pcm16Le: ByteArray,
        srcSampleRate: Int,
        srcChannels: Int
    ): ByteArray {
        val totalShorts = pcm16Le.size / 2
        if (totalShorts == 0) return ByteArray(0)
        val shortBuf = ByteBuffer.wrap(pcm16Le).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val frameCount = totalShorts / srcChannels.coerceAtLeast(1)
        val mono = ShortArray(frameCount)
        if (srcChannels == 1) {
            shortBuf.get(mono, 0, frameCount)
        } else {
            for (i in 0 until frameCount) {
                var sum = 0
                for (c in 0 until srcChannels) {
                    sum += shortBuf.get(i * srcChannels + c).toInt()
                }
                mono[i] = (sum / srcChannels).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
        }

        val targetRate = 16000
        if (srcSampleRate == targetRate) {
            val out = ByteBuffer.allocate(mono.size * 2).order(ByteOrder.LITTLE_ENDIAN)
            mono.forEach { out.putShort(it) }
            return out.array()
        }

        val ratio = srcSampleRate.toDouble() / targetRate.toDouble()
        val outLen = (frameCount / ratio).toInt().coerceAtLeast(1)
        val outBuf = ByteBuffer.allocate(outLen * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until outLen) {
            val srcPos = i * ratio
            val idx0 = srcPos.toInt().coerceIn(0, frameCount - 1)
            val idx1 = (idx0 + 1).coerceIn(0, frameCount - 1)
            val frac = srcPos - idx0
            val sample = (mono[idx0] * (1.0 - frac) + mono[idx1] * frac)
                .roundToInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
            outBuf.putShort(sample)
        }
        return outBuf.array()
    }

    private fun writeWavFile(file: File, pcmData: ByteArray, sampleRate: Int, channels: Int) {
        val byteRate = sampleRate * channels * 2
        val dataSize = pcmData.size
        val chunkSize = 36 + dataSize
        RandomAccessFile(file, "rw").use { raf ->
            raf.setLength(0L)
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            header.put("RIFF".toByteArray(Charsets.US_ASCII))
            header.putInt(chunkSize)
            header.put("WAVE".toByteArray(Charsets.US_ASCII))
            header.put("fmt ".toByteArray(Charsets.US_ASCII))
            header.putInt(16) // PCM subchunk size
            header.putShort(1) // PCM format
            header.putShort(channels.toShort())
            header.putInt(sampleRate)
            header.putInt(byteRate)
            header.putShort((channels * 2).toShort())
            header.putShort(16) // bits per sample
            header.put("data".toByteArray(Charsets.US_ASCII))
            header.putInt(dataSize)
            raf.write(header.array())
            raf.write(pcmData)
        }
    }

    /**
     * Produces a compact standard M4A audio file via native MediaExtractor + MediaMuxer
     * (re-encoding to AAC 96 kbps if source is uncompressed WAV/PCM).
     */
    private suspend fun compressAudioToStandardM4a(
        context: Context,
        uri: Uri,
        durationMs: Long,
        outputFile: File,
        onProgress: (Float, String) -> Unit
    ) {
        onProgress(0.1f, "Optimizing audio container...")
        val extractor = MediaExtractor()
        try {
            val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                ?: throw IllegalStateException("Unable to open selected audio file.")
            pfd.use { descriptor ->
                extractor.setDataSource(descriptor.fileDescriptor)
                var audioTrack = -1
                var format: MediaFormat? = null
                for (i in 0 until extractor.trackCount) {
                    val f = extractor.getTrackFormat(i)
                    val m = f.getString(MediaFormat.KEY_MIME) ?: continue
                    if (m.startsWith("audio/")) {
                        audioTrack = i
                        format = f
                        break
                    }
                }
                if (audioTrack == -1 || format == null) {
                    throw IllegalStateException("No audio track found in the selected file.")
                }

                val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.contains("mp4a", ignoreCase = true) || mime.contains("aac", ignoreCase = true)) {
                    extractor.selectTrack(audioTrack)
                    val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                    var started = false
                    try {
                        val outTrack = muxer.addTrack(format)
                        muxer.start()
                        started = true
                        val buf = ByteBuffer.allocate(256 * 1024)
                        val info = MediaCodec.BufferInfo()
                        val totalUs = (durationMs * 1000L).coerceAtLeast(1_000_000L)
                        var firstTs = -1L
                        while (true) {
                            coroutineContext.ensureActive()
                            info.offset = 0
                            info.size = extractor.readSampleData(buf, 0)
                            if (info.size < 0) break
                            val ts = extractor.sampleTime
                            if (firstTs < 0 && ts >= 0) firstTs = ts
                            info.presentationTimeUs = (ts - firstTs).coerceAtLeast(0L)
                            info.flags = extractor.sampleFlags
                            muxer.writeSampleData(outTrack, buf, info)
                            extractor.advance()
                            val pct = (info.presentationTimeUs.toFloat() / totalUs.toFloat()).coerceIn(0f, 1f)
                            onProgress(0.1f + pct * 0.85f, "Packing compact M4A...")
                        }
                    } finally {
                        if (started) runCatching { muxer.stop() }
                        runCatching { muxer.release() }
                    }
                    onProgress(1f, "Done")
                    return
                }
            }
        } finally {
            runCatching { extractor.release() }
        }

        // For MP3/WAV/OGG: transcode via MediaCodec AAC 96 kbps into M4A
        transcodeAudioToAacM4a(context, uri, durationMs, outputFile, onProgress)
    }

    private suspend fun transcodeAudioToAacM4a(
        context: Context,
        uri: Uri,
        durationMs: Long,
        outputFile: File,
        onProgress: (Float, String) -> Unit
    ) {
        val tempWav = File(outputFile.parentFile, "tmp_${System.currentTimeMillis()}.wav")
        try {
            compressAudioToMono16kWav(context, uri, durationMs, tempWav) { p, _ ->
                onProgress(p * 0.55f, "Decoding audio stream...")
            }
            val rawBytes = tempWav.readBytes()
            val pcm = if (rawBytes.size > 44) rawBytes.copyOfRange(44, rawBytes.size) else rawBytes

            val encFormat = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, 16000, 1).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, 64_000)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
            }
            val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var muxerStarted = false
            var outTrack = -1
            try {
                encoder.configure(encFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                encoder.start()
                val bufInfo = MediaCodec.BufferInfo()
                var offset = 0
                var inputDone = false
                var outputDone = false
                var ptsUs = 0L

                while (!outputDone) {
                    coroutineContext.ensureActive()
                    if (!inputDone) {
                        val inIdx = encoder.dequeueInputBuffer(10_000L)
                        if (inIdx >= 0) {
                            val inBuf = encoder.getInputBuffer(inIdx)!!
                            inBuf.clear()
                            if (offset >= pcm.size) {
                                encoder.queueInputBuffer(inIdx, 0, 0, ptsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                val chunkLen = minOf(inBuf.remaining(), pcm.size - offset)
                                inBuf.put(pcm, offset, chunkLen)
                                encoder.queueInputBuffer(inIdx, 0, chunkLen, ptsUs, 0)
                                offset += chunkLen
                                ptsUs += (chunkLen / 2L) * 1_000_000L / 16000L
                                val pct = offset.toFloat() / pcm.size.coerceAtLeast(1).toFloat()
                                onProgress(0.55f + pct * 0.42f, "Encoding compact M4A...")
                            }
                        }
                    }

                    val outIdx = encoder.dequeueOutputBuffer(bufInfo, 10_000L)
                    when {
                        outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            outTrack = muxer.addTrack(encoder.outputFormat)
                            muxer.start()
                            muxerStarted = true
                        }
                        outIdx >= 0 -> {
                            val outBuf = encoder.getOutputBuffer(outIdx)!!
                            if (bufInfo.size > 0 && muxerStarted && (bufInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                                outBuf.position(bufInfo.offset)
                                outBuf.limit(bufInfo.offset + bufInfo.size)
                                muxer.writeSampleData(outTrack, outBuf, bufInfo)
                            }
                            encoder.releaseOutputBuffer(outIdx, false)
                            if ((bufInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                outputDone = true
                            }
                        }
                    }
                }
            } finally {
                runCatching { encoder.stop() }
                runCatching { encoder.release() }
                if (muxerStarted) runCatching { muxer.stop() }
                runCatching { muxer.release() }
            }
            onProgress(1f, "Done")
        } finally {
            runCatching { tempWav.delete() }
        }
    }

    /**
     * Hardware-accelerated Android video compressor powered by Media3 Transformer.
     * Targets the device's hardware H.264 (video/avc) encoder with preset bitrate/resolution,
     * optional OpenGL HDR-to-SDR tone-mapping, and optional ClippingConfiguration for split exports.
     */
    @OptIn(UnstableApi::class)
    internal suspend fun compressVideoWithTransformer(
        context: Context,
        uri: Uri,
        info: PickedMediaInfo,
        preset: VideoCompressPreset,
        fixHdrColors: Boolean,
        outputFile: File,
        clipStartMs: Long? = null,
        clipEndMs: Long? = null,
        onProgress: (Float, String) -> Unit
    ) {
        onProgress(0.02f, "Starting hardware encoder...")
        outputFile.parentFile?.let { if (!it.exists()) it.mkdirs() }
        if (outputFile.exists()) {
            runCatching { outputFile.delete() }
        }

        val srcW = info.width
        val srcH = info.height
        val srcShortSide = if (srcW > 0 && srcH > 0) minOf(srcW, srcH) else 0
        val effectiveTargetShortSide = if (srcShortSide > 0) {
            preset.targetHeight.coerceAtMost(srcShortSide)
        } else {
            preset.targetHeight
        }

        val videoEffects = buildList<Effect> {
            // Downscale proportionally by short side (handles both portrait & landscape without upscaling)
            if (srcShortSide == 0 || srcShortSide > preset.targetHeight) {
                add(Presentation.createForShortSide(preset.targetHeight))
            }
        }

        val mediaItem = if (clipStartMs != null && clipEndMs != null && clipEndMs > clipStartMs) {
            MediaItem.Builder()
                .setUri(uri)
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(clipStartMs.coerceAtLeast(0L))
                        .setEndPositionMs(clipEndMs)
                        .build()
                )
                .build()
        } else {
            MediaItem.fromUri(uri)
        }
        val editedMediaItem = EditedMediaItem.Builder(mediaItem)
            .setEffects(Effects(/* audioProcessors = */ emptyList(), /* videoEffects = */ videoEffects))
            .build()

        val sequence = EditedMediaItemSequence.Builder(editedMediaItem).build()
        val hdrMode = if (fixHdrColors) {
            Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL
        } else {
            Composition.HDR_MODE_KEEP_HDR
        }
        val composition = Composition.Builder(sequence)
            .setHdrMode(hdrMode)
            .build()

        val videoEncoderSettings = VideoEncoderSettings.Builder()
            .setBitrate(preset.videoBitrateKbps * 1000)
            .build()

        val encoderFactory = DefaultEncoderFactory.Builder(context)
            .setRequestedVideoEncoderSettings(videoEncoderSettings)
            .setEnableFallback(true)
            .build()

        val statusLabel = if (fixHdrColors) {
            "Compressing ${effectiveTargetShortSide}p H.264 SDR..."
        } else {
            "Compressing ${effectiveTargetShortSide}p H.264..."
        }

        // Media3 Transformer requires creation, start(), getProgress(), and cancel() on a single Looper thread.
        withContext(Dispatchers.Main.immediate) {
            var transformer: Transformer? = null
            var progressJob: kotlinx.coroutines.Job? = null
            try {
                suspendCancellableCoroutine<Unit> { continuation ->
                    val listener = object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                            progressJob?.cancel()
                            onProgress(1f, "Done")
                            if (continuation.isActive) {
                                continuation.resume(Unit)
                            }
                        }

                        override fun onError(
                            composition: Composition,
                            exportResult: ExportResult,
                            exportException: ExportException
                        ) {
                            progressJob?.cancel()
                            runCatching { outputFile.delete() }
                            if (continuation.isActive) {
                                val detail = exportException.message?.takeIf { it.isNotBlank() }
                                    ?: exportException.errorCodeName
                                continuation.resumeWithException(
                                    IllegalStateException("Video compression failed: $detail", exportException)
                                )
                            }
                        }
                    }

                    val builtTransformer = Transformer.Builder(context)
                        .setVideoMimeType(MimeTypes.VIDEO_H264)
                        .setAudioMimeType(MimeTypes.AUDIO_AAC)
                        .setEncoderFactory(encoderFactory)
                        .addListener(listener)
                        .build()
                    transformer = builtTransformer

                    continuation.invokeOnCancellation {
                        progressJob?.cancel()
                        runCatching { builtTransformer.cancel() }
                        runCatching { outputFile.delete() }
                    }

                    builtTransformer.start(composition, outputFile.absolutePath)

                    val progressHolder = ProgressHolder()
                    progressJob = launch {
                        while (isActive && continuation.isActive) {
                            val state = runCatching { builtTransformer.getProgress(progressHolder) }
                                .getOrDefault(Transformer.PROGRESS_STATE_NOT_STARTED)
                            when (state) {
                                Transformer.PROGRESS_STATE_AVAILABLE -> {
                                    val fraction = (progressHolder.progress.coerceIn(0, 100) / 100f)
                                        .coerceIn(0.03f, 0.99f)
                                    onProgress(fraction, statusLabel)
                                }
                                Transformer.PROGRESS_STATE_WAITING_FOR_AVAILABILITY -> {
                                    onProgress(0.05f, statusLabel)
                                }
                                Transformer.PROGRESS_STATE_UNAVAILABLE -> {
                                    onProgress(0.25f, statusLabel)
                                }
                                Transformer.PROGRESS_STATE_NOT_STARTED -> break
                            }
                            delay(100L)
                        }
                    }
                }
            } catch (e: CancellationException) {
                progressJob?.cancel()
                runCatching { transformer?.cancel() }
                runCatching { outputFile.delete() }
                throw e
            } catch (e: Exception) {
                progressJob?.cancel()
                runCatching { outputFile.delete() }
                throw e
            } finally {
                progressJob?.cancel()
            }
        }
    }
}
