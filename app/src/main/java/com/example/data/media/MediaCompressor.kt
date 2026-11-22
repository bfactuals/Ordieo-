package com.example.data.media

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import com.example.data.model.AudioCompressPreset
import com.example.data.model.CompressMediaType
import com.example.data.model.CompressedResult
import com.example.data.model.PickedMediaInfo
import com.example.data.model.VideoCompressPreset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import kotlin.coroutines.coroutineContext
import kotlin.math.roundToInt

/**
 * 100% Native Android Media Compressor (zero FFmpeg).
 * Uses Android's MediaMetadataRetriever, MediaExtractor, MediaCodec, and MediaMuxer.
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
        val safeBase = info.displayName
            .substringBeforeLast('.')
            .replace(Regex("""[^a-zA-Z0-9._\- ]"""), "_")
            .trim()
            .ifBlank { "compressed" }
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
            CompressMediaType.VIDEO -> {
                val suffix = when (videoPreset) {
                    VideoCompressPreset.FOR_AI_ULTRA_SMALL -> "480p_ai"
                    VideoCompressPreset.SMALL -> "720p_small"
                    VideoCompressPreset.BALANCED -> "balanced"
                    VideoCompressPreset.BEST_QUALITY -> "high"
                }
                val outFile = File(outDir, "${safeBase}_${suffix}_$stamp.mp4")
                compressVideoNative(
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
     * Native Android video compressor using MediaMetadataRetriever + MediaCodec H.264 encoder
     * + MediaMuxer (re-encoding to standard SDR BT.709 when Fix HDR colours is enabled)
     * while preserving the audio track cleanly.
     */
    private suspend fun compressVideoNative(
        context: Context,
        uri: Uri,
        info: PickedMediaInfo,
        preset: VideoCompressPreset,
        fixHdrColors: Boolean,
        outputFile: File,
        onProgress: (Float, String) -> Unit
    ) {
        onProgress(0.04f, "Preparing video encoder...")
        val srcW = info.width.takeIf { it > 0 } ?: 1280
        val srcH = info.height.takeIf { it > 0 } ?: 720
        val isPortrait = srcH > srcW
        val targetShortEdge = preset.targetHeight.coerceAtMost(minOf(srcW, srcH).coerceAtLeast(320))
        val scale = targetShortEdge.toFloat() / minOf(srcW, srcH).coerceAtLeast(1).toFloat()
        val outW = (((srcW * scale).roundToInt() / 16) * 16).coerceAtLeast(320)
        val outH = (((srcH * scale).roundToInt() / 16) * 16).coerceAtLeast(240)
        val fps = if (preset == VideoCompressPreset.FOR_AI_ULTRA_SMALL) 15 else 24

        val videoFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, outW, outH).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, preset.videoBitrateKbps * 1000)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
            if (fixHdrColors && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                setInteger(MediaFormat.KEY_COLOR_STANDARD, MediaFormat.COLOR_STANDARD_BT709)
                setInteger(MediaFormat.KEY_COLOR_TRANSFER, MediaFormat.COLOR_TRANSFER_SDR_VIDEO)
                setInteger(MediaFormat.KEY_COLOR_RANGE, MediaFormat.COLOR_RANGE_LIMITED)
            }
        }

        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val retriever = MediaMetadataRetriever()
        val audioExtractor = MediaExtractor()
        var muxerStarted = false
        var videoTrackIndex = -1
        var audioTrackIndex = -1
        var hasCompatibleAudio = false
        var sourceAudioFormat: MediaFormat? = null

        try {
            // Inspect source audio track to mux alongside compressed video
            runCatching {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    audioExtractor.setDataSource(pfd.fileDescriptor)
                    for (i in 0 until audioExtractor.trackCount) {
                        val f = audioExtractor.getTrackFormat(i)
                        val m = f.getString(MediaFormat.KEY_MIME).orEmpty()
                        if (m.startsWith("audio/mp4a") || m.startsWith("audio/aac")) {
                            audioExtractor.selectTrack(i)
                            sourceAudioFormat = f
                            hasCompatibleAudio = true
                            break
                        }
                    }
                }
            }

            encoder.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val inputSurface = encoder.createInputSurface()
            encoder.start()

            retriever.setDataSource(context, uri)
            val durationMs = info.durationMs.coerceAtLeast(1000L)
            val frameStepMs = (1000L / fps).coerceAtLeast(33L)
            val totalFrames = (durationMs / frameStepMs).toInt().coerceIn(1, 3600)
            val bufInfo = MediaCodec.BufferInfo()

            fun drainEncoder(endOfStream: Boolean) {
                if (endOfStream) {
                    encoder.signalEndOfInputStream()
                }
                while (true) {
                    val outIdx = encoder.dequeueOutputBuffer(bufInfo, if (endOfStream) 10_000L else 0L)
                    when {
                        outIdx == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                            if (!endOfStream) break
                        }
                        outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            if (!muxerStarted) {
                                videoTrackIndex = muxer.addTrack(encoder.outputFormat)
                                if (hasCompatibleAudio && sourceAudioFormat != null) {
                                    audioTrackIndex = muxer.addTrack(sourceAudioFormat!!)
                                }
                                muxer.start()
                                muxerStarted = true
                            }
                        }
                        outIdx >= 0 -> {
                            val encoded = encoder.getOutputBuffer(outIdx)
                            if (encoded != null && bufInfo.size > 0 && muxerStarted &&
                                (bufInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0
                            ) {
                                encoded.position(bufInfo.offset)
                                encoded.limit(bufInfo.offset + bufInfo.size)
                                muxer.writeSampleData(videoTrackIndex, encoded, bufInfo)
                            }
                            encoder.releaseOutputBuffer(outIdx, false)
                            if ((bufInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break
                        }
                    }
                }
            }

            for (frameIdx in 0 until totalFrames) {
                coroutineContext.ensureActive()
                val timeUs = frameIdx * frameStepMs * 1000L
                val bmp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    retriever.getScaledFrameAtTime(
                        timeUs,
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                        outW,
                        outH
                    )
                } else {
                    retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                }

                if (bmp != null) {
                    val canvas = inputSurface.lockCanvas(null)
                    try {
                        val destRect = android.graphics.Rect(0, 0, canvas.width, canvas.height)
                        // Drawing ARGB_8888 bitmap onto SDR surface normalizes HDR tone mapping cleanly
                        val sdrBitmap = if (bmp.config != Bitmap.Config.ARGB_8888) {
                            bmp.copy(Bitmap.Config.ARGB_8888, false)
                        } else bmp
                        canvas.drawBitmap(sdrBitmap, null, destRect, null)
                        if (sdrBitmap !== bmp) sdrBitmap.recycle()
                    } finally {
                        inputSurface.unlockCanvasAndPost(canvas)
                        bmp.recycle()
                    }
                }
                drainEncoder(false)

                val pct = (frameIdx + 1).toFloat() / totalFrames.toFloat()
                val label = if (fixHdrColors) {
                    "Compressing ${if (isPortrait) outW else outH}p SDR..."
                } else {
                    "Compressing ${if (isPortrait) outW else outH}p..."
                }
                onProgress(0.05f + pct * 0.80f, label)
            }

            drainEncoder(true)
            inputSurface.release()

            // Mux preserved AAC audio stream alongside video
            if (muxerStarted && hasCompatibleAudio && audioTrackIndex >= 0) {
                onProgress(0.90f, "Merging audio track...")
                val audioBuf = ByteBuffer.allocate(256 * 1024)
                val audioInfo = MediaCodec.BufferInfo()
                var firstAudioTs = -1L
                while (true) {
                    coroutineContext.ensureActive()
                    audioInfo.offset = 0
                    audioInfo.size = audioExtractor.readSampleData(audioBuf, 0)
                    if (audioInfo.size < 0) break
                    val ts = audioExtractor.sampleTime
                    if (firstAudioTs < 0 && ts >= 0) firstAudioTs = ts
                    audioInfo.presentationTimeUs = (ts - firstAudioTs).coerceAtLeast(0L)
                    audioInfo.flags = audioExtractor.sampleFlags
                    muxer.writeSampleData(audioTrackIndex, audioBuf, audioInfo)
                    audioExtractor.advance()
                }
            }
            onProgress(1f, "Done")
        } catch (e: Exception) {
            runCatching { outputFile.delete() }
            if (e is kotlinx.coroutines.CancellationException) throw e
            throw IllegalStateException("Video compression failed: ${e.message ?: "Unsupported codec"}", e)
        } finally {
            runCatching { retriever.release() }
            runCatching { audioExtractor.release() }
            runCatching { encoder.stop() }
            runCatching { encoder.release() }
            if (muxerStarted) runCatching { muxer.stop() }
            runCatching { muxer.release() }
        }
    }
}
