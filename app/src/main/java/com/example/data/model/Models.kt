package com.example.data.model

import com.squareup.moshi.JsonClass

enum class ThemeMode(val label: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark");

    companion object {
        fun fromKey(key: String?): ThemeMode =
            entries.firstOrNull { it.name == key } ?: SYSTEM
    }
}

enum class AiProvider(val displayName: String, val subtitle: String, val supportsAudio: Boolean) {
    GEMINI("Google Gemini", "Audio/Video + Text", true),
    GROQ("Groq", "whisper-large-v3-turbo & llama-3.3-70b", true),
    OPENROUTER("OpenRouter", "Free-tier Text, Hinglish & Magic", false),
    OPENAI("OpenAI", "whisper-1 & gpt-4o-mini", true);

    companion object {
        fun fromKey(key: String?): AiProvider =
            entries.firstOrNull { it.name == key } ?: GEMINI
    }
}

enum class GeminiModelOption(val modelId: String, val displayLabel: String, val description: String) {
    GEMINI_3_8_FLASH("gemini-3.8-flash", "gemini-3.8-flash", "Default / Fast / Free Tier"),
    GEMINI_4_HYDROGEN("gemini-4-hydrogen", "gemini-4-hydrogen", "Hydrogen — Fast / Daily Tasks"),
    GEMINI_4_NEON("gemini-4-neon", "gemini-4-neon", "Neon — Balanced / Mobile"),
    GEMINI_4_CARBON("gemini-4-carbon", "gemini-4-carbon", "Carbon — Advanced / AI Pro"),
    GEMINI_3_8_PRO("gemini-3.8-pro", "gemini-3.8-pro", "Pro"),
    CUSTOM("custom", "Custom Model ID...", "Enter any exact model ID from AI Studio");

    companion object {
        fun fromKey(key: String?): GeminiModelOption =
            entries.firstOrNull { it.name == key } ?: GEMINI_3_8_FLASH
    }
}

enum class AudioSaveBehavior(val title: String, val subtitle: String) {
    DEFAULT_DOWNLOADS(
        "Default Downloads Folder",
        "One-tap direct download for audio, .TXT, and .SRT files"
    ),
    CHOOSE_OR_SHARE(
        "Choose Location / Share Sheet",
        "Open native Share Sheet or choose a custom destination"
    );

    companion object {
        fun fromKey(key: String?): AudioSaveBehavior =
            entries.firstOrNull { it.name == key } ?: DEFAULT_DOWNLOADS
    }
}

enum class PlaybackSpeed(val label: String, val multiplier: Float) {
    X0_5("0.5x", 0.5f),
    X1("1x", 1.0f),
    X1_5("1.5x", 1.5f),
    X2("2x", 2.0f);

    fun next(): PlaybackSpeed = when (this) {
        X0_5 -> X1
        X1 -> X1_5
        X1_5 -> X2
        X2 -> X0_5
    }
}

enum class RetentionOption(val label: String, val durationMs: Long?) {
    ONE_HOUR("1 Hour", 60L * 60L * 1000L),
    TWENTY_FOUR_HOURS("24 Hours", 24L * 60L * 60L * 1000L),
    SEVEN_DAYS("7 Days", 7L * 24L * 60L * 60L * 1000L),
    ONE_MONTH("1 Month", 30L * 24L * 60L * 60L * 1000L),
    PERMANENT("Keep forever", null);

    fun computeExpiryTimestamp(fromMs: Long = System.currentTimeMillis()): Long? {
        return durationMs?.let { fromMs + it }
    }

    companion object {
        fun fromKey(key: String?): RetentionOption =
            entries.firstOrNull { it.name == key } ?: SEVEN_DAYS
    }
}

enum class WordChunkMode(val label: String, val shortLabel: String, val maxWords: Int?) {
    ONE_WORD("1 Word", "1", 1),
    TWO_WORDS("2 Words", "2", 2),
    THREE_WORDS("3 Words", "3", 3),
    FULL_LINE("Full Line", "Line", null);

    companion object {
        fun fromKey(key: String?): WordChunkMode =
            entries.firstOrNull { it.name == key } ?: FULL_LINE
    }
}

enum class LanguageMode(val label: String) {
    ORIGINAL("Original"),
    ENGLISH("English"),
    HINGLISH("Hinglish");

    companion object {
        fun fromKey(key: String?): LanguageMode =
            entries.firstOrNull { it.name == key } ?: ORIGINAL
    }
}

enum class TextCasing(val label: String) {
    ORIGINAL("Original"),
    ALL_CAPS("ALL CAPS"),
    TITLE_CASE("Title Case"),
    LOWERCASE("lowercase");

    companion object {
        fun fromKey(key: String?): TextCasing =
            entries.firstOrNull { it.name == key } ?: ORIGINAL
    }
}

enum class CompressMediaType(val label: String) {
    VIDEO("Video"),
    AUDIO("Audio")
}

enum class VideoCompressPreset(
    val title: String,
    val subtitle: String,
    val targetHeight: Int,
    val videoBitrateKbps: Int,
    val audioBitrateKbps: Int
) {
    FOR_AI_ULTRA_SMALL(
        title = "For AI · Ultra-Small",
        subtitle = "480p · ~2–3 MB/min for fast AI uploads",
        targetHeight = 480,
        videoBitrateKbps = 380,
        audioBitrateKbps = 48
    ),
    SMALL(
        title = "Small",
        subtitle = "720p · quick to share",
        targetHeight = 720,
        videoBitrateKbps = 1200,
        audioBitrateKbps = 96
    ),
    BALANCED(
        title = "Balanced · Recommended",
        subtitle = "Clear picture, much smaller",
        targetHeight = 1080,
        videoBitrateKbps = 2400,
        audioBitrateKbps = 128
    ),
    BEST_QUALITY(
        title = "Best quality",
        subtitle = "Near original clarity",
        targetHeight = 1080,
        videoBitrateKbps = 4800,
        audioBitrateKbps = 160
    );

    companion object {
        fun fromKey(key: String?): VideoCompressPreset =
            entries.firstOrNull { it.name == key } ?: FOR_AI_ULTRA_SMALL
    }
}

enum class AudioCompressPreset(
    val title: String,
    val subtitle: String,
    val sampleRateHz: Int,
    val channelCount: Int
) {
    FOR_AI_TRANSCRIBE(
        title = "For AI / Transcribe",
        subtitle = "Mono 16 kHz WAV · compact & speech-ready",
        sampleRateHz = 16000,
        channelCount = 1
    ),
    STANDARD_AUDIO(
        title = "Standard Audio",
        subtitle = "Compact M4A · clear playback",
        sampleRateHz = 44100,
        channelCount = 2
    );

    companion object {
        fun fromKey(key: String?): AudioCompressPreset =
            entries.firstOrNull { it.name == key } ?: FOR_AI_TRANSCRIBE
    }
}

data class PickedMediaInfo(
    val uriString: String,
    val displayName: String,
    val sizeBytes: Long,
    val durationMs: Long,
    val width: Int = 0,
    val height: Int = 0,
    val mimeType: String = "",
    val isHdrLikely: Boolean = false
)

data class CompressedResult(
    val filePath: String,
    val fileName: String,
    val mimeType: String,
    val outputSizeBytes: Long,
    val originalSizeBytes: Long,
    val mediaType: CompressMediaType
)

data class CompressUiState(
    val mediaType: CompressMediaType = CompressMediaType.VIDEO,
    val selectedMedia: PickedMediaInfo? = null,
    val videoPreset: VideoCompressPreset = VideoCompressPreset.FOR_AI_ULTRA_SMALL,
    val audioPreset: AudioCompressPreset = AudioCompressPreset.FOR_AI_TRANSCRIBE,
    val fixHdrColors: Boolean = true,
    val isCompressing: Boolean = false,
    val progress: Float = 0f,
    val statusText: String = "",
    val result: CompressedResult? = null
)

enum class PendingJobType(val label: String) {
    TRANSCRIBE("Transcription"),
    TRANSLATE_ENGLISH("English translation"),
    SMART_HINGLISH("Smart Hinglish")
}

data class PendingQueueJob(
    val projectId: String,
    val projectTitle: String,
    val jobType: PendingJobType,
    val lastError: String,
    val createdAt: Long = System.currentTimeMillis()
)

@JsonClass(generateAdapter = true)
data class WordTiming(
    val word: String,
    val startMs: Long,
    val endMs: Long
)

@JsonClass(generateAdapter = true)
data class TranscriptSegment(
    val id: Int,
    val startMs: Long,
    val endMs: Long,
    val text: String,
    val words: List<WordTiming> = emptyList()
)

@JsonClass(generateAdapter = true)
data class MagicContent(
    val viralHooks: List<String> = emptyList(),
    val captions: String = "",
    val hashtags: List<String> = emptyList(),
    val thumbnailTitles: List<String> = emptyList(),
    val pinnedComment: String = ""
)

@JsonClass(generateAdapter = true)
data class AutoCorrectRule(
    val id: String,
    val wrong: String,
    val correct: String
)

data class ProjectModel(
    val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val isPinned: Boolean = false,
    val retentionOption: RetentionOption,
    val expiresAt: Long?,
    val audioFilePath: String?,
    val audioDurationMs: Long,
    val waveform: List<Float>,
    val originalSegments: List<TranscriptSegment>,
    val englishSegments: List<TranscriptSegment>? = null,
    val hinglishSegments: List<TranscriptSegment>?,
    val languageMode: LanguageMode,
    val wordChunkMode: WordChunkMode,
    val removeFillerWords: Boolean = false,
    val stripPunctuation: Boolean,
    val textCasing: TextCasing,
    val timestampShiftMs: Long,
    val magicContent: MagicContent?
) {
    val activeSegments: List<TranscriptSegment>
        get() = when {
            languageMode == LanguageMode.ENGLISH && !englishSegments.isNullOrEmpty() -> englishSegments
            languageMode == LanguageMode.HINGLISH && !hinglishSegments.isNullOrEmpty() -> hinglishSegments
            else -> originalSegments
        }

    val hasAudio: Boolean
        get() = !audioFilePath.isNullOrBlank()

    val hasTranscript: Boolean
        get() = originalSegments.isNotEmpty() ||
            !englishSegments.isNullOrEmpty() ||
            !hinglishSegments.isNullOrEmpty()
}

data class DailyUsage(
    val dateKey: String = "",
    val requestsToday: Int = 0,
    val tokensToday: Long = 0L
)

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val provider: AiProvider = AiProvider.GEMINI,
    val geminiModelOption: GeminiModelOption = GeminiModelOption.GEMINI_3_8_FLASH,
    val geminiCustomModelId: String = "",
    val openRouterModelId: String = "meta-llama/llama-3.3-70b-instruct:free",
    val geminiApiKey: String = "",
    val groqApiKey: String = "",
    val openRouterApiKey: String = "",
    val openAiApiKey: String = "",
    val defaultRetention: RetentionOption = RetentionOption.SEVEN_DAYS,
    val audioSaveBehavior: AudioSaveBehavior = AudioSaveBehavior.DEFAULT_DOWNLOADS,
    val defaultVideoPreset: VideoCompressPreset = VideoCompressPreset.FOR_AI_ULTRA_SMALL,
    val defaultFixHdr: Boolean = true,
    val defaultAudioPreset: AudioCompressPreset = AudioCompressPreset.FOR_AI_TRANSCRIBE,
    val autoCorrectRules: List<AutoCorrectRule> = emptyList()
) {
    fun apiKeyFor(target: AiProvider): String = when (target) {
        AiProvider.GEMINI -> geminiApiKey.trim()
        AiProvider.GROQ -> groqApiKey.trim()
        AiProvider.OPENROUTER -> openRouterApiKey.trim()
        AiProvider.OPENAI -> openAiApiKey.trim()
    }

    val activeApiKey: String
        get() = apiKeyFor(provider)

    val resolvedGeminiModelId: String
        get() = if (geminiModelOption == GeminiModelOption.CUSTOM) {
            geminiCustomModelId.trim().ifBlank { GeminiModelOption.GEMINI_3_8_FLASH.modelId }
        } else {
            geminiModelOption.modelId
        }

    val resolvedOpenRouterModelId: String
        get() = openRouterModelId.trim().ifBlank { "meta-llama/llama-3.3-70b-instruct:free" }

    val activeProviderSummary: String
        get() = when (provider) {
            AiProvider.GEMINI -> "Gemini · $resolvedGeminiModelId"
            AiProvider.GROQ -> "Groq · Whisper + Llama 3.3"
            AiProvider.OPENROUTER -> "OpenRouter · ${resolvedOpenRouterModelId.substringAfterLast('/')}"
            AiProvider.OPENAI -> "OpenAI · Whisper + GPT-4o mini"
        }
}

data class AudioFileItem(
    val projectId: String,
    val projectTitle: String,
    val filePath: String,
    val fileName: String,
    val sizeBytes: Long,
    val updatedAt: Long
)

data class StorageStats(
    val totalBytes: Long = 0L,
    val audioBytes: Long = 0L,
    val transcriptBytes: Long = 0L,
    val audioItems: List<AudioFileItem> = emptyList()
)

@JsonClass(generateAdapter = true)
data class BackupProjectItem(
    val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val isPinned: Boolean = false,
    val retentionOption: String,
    val originalSegments: List<TranscriptSegment>,
    val englishSegments: List<TranscriptSegment>? = null,
    val hinglishSegments: List<TranscriptSegment>? = null,
    val languageMode: String,
    val wordChunkMode: String,
    val removeFillerWords: Boolean = false,
    val stripPunctuation: Boolean = false,
    val textCasing: String = "ORIGINAL",
    val timestampShiftMs: Long = 0L,
    val magicContent: MagicContent? = null
)

@JsonClass(generateAdapter = true)
data class BackupPayload(
    val version: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val dictionary: List<AutoCorrectRule> = emptyList(),
    val projects: List<BackupProjectItem> = emptyList()
)

enum class ProcessingStep(val label: String) {
    EXTRACTING_AUDIO("Extracting Audio..."),
    SAVING_AUDIO("Saving Audio..."),
    PARSING_FILE("Parsing File..."),
    TRANSCRIBING("Transcribing..."),
    TRANSLATING_ENGLISH("Translating to English..."),
    CONVERTING_HINGLISH("Converting to Hinglish..."),
    DONE("Done")
}

data class ProcessingOverlayState(
    val isVisible: Boolean = false,
    val steps: List<ProcessingStep> = emptyList(),
    val currentStepIndex: Int = 0,
    val detail: String = ""
)
