package com.example.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.PreferencesManager
import com.example.data.media.AudioExtractor
import com.example.data.media.AudioPlayerController
import com.example.data.media.LocalFileSaver
import com.example.data.media.MediaCompressor
import com.example.data.media.PlaybackState
import com.example.data.media.RecordingState
import com.example.data.media.VoiceRecorderController
import com.example.data.model.AiProvider
import com.example.data.model.AppSettings
import com.example.data.model.AudioCompressPreset
import com.example.data.model.AudioSaveBehavior
import com.example.data.model.CompressMediaType
import com.example.data.model.CompressUiState
import com.example.data.model.DailyUsage
import com.example.data.model.GeminiModelOption
import com.example.data.model.LanguageMode
import com.example.data.model.PendingJobType
import com.example.data.model.PendingQueueJob
import com.example.data.model.PlaybackSpeed
import com.example.data.model.ProcessingOverlayState
import com.example.data.model.ProcessingStep
import com.example.data.model.ProjectModel
import com.example.data.model.RetentionOption
import com.example.data.model.StorageStats
import com.example.data.model.TextCasing
import com.example.data.model.ThemeMode
import com.example.data.model.TranscriptSegment
import com.example.data.model.VideoCompressPreset
import com.example.data.model.WordChunkMode
import com.example.AppConfig
import com.example.data.remote.AiRequestConfig
import com.example.data.remote.AiService
import com.example.data.remote.ApiKeyInvalidException
import com.example.data.remote.RateLimitException
import com.example.data.remote.RemoteConfig
import com.example.data.remote.RemoteConfigFetcher
import com.example.data.remote.RemoteConfigParser
import com.example.data.remote.UpdatePromptState
import com.example.data.repository.ProjectRepository
import com.example.data.srt.SrtEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

sealed class ToastEvent(val message: String, val isError: Boolean = false) {
    class Info(message: String) : ToastEvent(message, false)
    class Error(message: String) : ToastEvent(message, true)
}

class TranscribeViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val preferencesManager = PreferencesManager(appContext)
    private val database = AppDatabase.getInstance(appContext)
    private val repository = ProjectRepository(appContext, database.projectDao())
    private val aiService = AiService()
    private val remoteConfigFetcher = RemoteConfigFetcher()
    private val audioPlayer = AudioPlayerController(viewModelScope)
    private val voiceRecorder = VoiceRecorderController(appContext, viewModelScope)

    val settings: StateFlow<AppSettings> = preferencesManager.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = AppSettings()
    )

    val dailyUsage: StateFlow<DailyUsage> = preferencesManager.dailyUsageFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = DailyUsage()
    )

    val projects: StateFlow<List<ProjectModel>> = repository.projectsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val playbackState: StateFlow<PlaybackState> = audioPlayer.state
    val recordingState: StateFlow<RecordingState> = voiceRecorder.state

    private val _activeProject = MutableStateFlow<ProjectModel?>(null)
    val activeProject: StateFlow<ProjectModel?> = _activeProject.asStateFlow()

    private val _storageStats = MutableStateFlow(StorageStats())
    val storageStats: StateFlow<StorageStats> = _storageStats.asStateFlow()

    private val _processingState = MutableStateFlow(ProcessingOverlayState())
    val processingState: StateFlow<ProcessingOverlayState> = _processingState.asStateFlow()

    private val _isConvertingHinglish = MutableStateFlow(false)
    val isConvertingHinglish: StateFlow<Boolean> = _isConvertingHinglish.asStateFlow()

    private val _isTranslatingEnglish = MutableStateFlow(false)
    val isTranslatingEnglish: StateFlow<Boolean> = _isTranslatingEnglish.asStateFlow()

    private val _isGeneratingMagic = MutableStateFlow(false)
    val isGeneratingMagic: StateFlow<Boolean> = _isGeneratingMagic.asStateFlow()

    private val _pendingQueue = MutableStateFlow<List<PendingQueueJob>>(emptyList())
    val pendingQueue: StateFlow<List<PendingQueueJob>> = _pendingQueue.asStateFlow()

    private val _isRetryingQueue = MutableStateFlow(false)
    val isRetryingQueue: StateFlow<Boolean> = _isRetryingQueue.asStateFlow()

    private val _compressState = MutableStateFlow(CompressUiState())
    val compressState: StateFlow<CompressUiState> = _compressState.asStateFlow()

    private var compressJob: Job? = null

    private val _showMissingApiKeyDialog = MutableStateFlow(false)
    val showMissingApiKeyDialog: StateFlow<Boolean> = _showMissingApiKeyDialog.asStateFlow()

    private val _remoteConfig = MutableStateFlow(RemoteConfig())
    val remoteConfig: StateFlow<RemoteConfig> = _remoteConfig.asStateFlow()

    private val _updatePrompt = MutableStateFlow(UpdatePromptState())
    val updatePrompt: StateFlow<UpdatePromptState> = _updatePrompt.asStateFlow()

    private val _isCheckingUpdate = MutableStateFlow(false)
    val isCheckingUpdate: StateFlow<Boolean> = _isCheckingUpdate.asStateFlow()

    private val _toastEvents = MutableSharedFlow<ToastEvent>(extraBufferCapacity = 10)
    val toastEvents: SharedFlow<ToastEvent> = _toastEvents.asSharedFlow()

    init {
        viewModelScope.launch {
            preferencesManager.ensureDailyResetOnLaunch()
            val deletedCount = repository.purgeExpiredProjects()
            if (deletedCount > 0) {
                val label = if (deletedCount == 1) "1 expired project" else "$deletedCount expired projects"
                _toastEvents.tryEmit(ToastEvent.Info("Auto-deleted $label"))
            }
            refreshStorageStats()
        }
        viewModelScope.launch {
            loadAndRefreshRemoteConfig()
        }
    }

    private suspend fun loadAndRefreshRemoteConfig() {
        // 1. Load cached JSON immediately for instant offline fallback
        val cachedJson = runCatching {
            preferencesManager.cachedRemoteConfigJsonFlow.first()
        }.getOrNull()
        val cachedConfig = RemoteConfigParser.parseOrDefault(cachedJson)
        _remoteConfig.value = cachedConfig

        val dismissedVersion = runCatching {
            preferencesManager.dismissedUpdateVersionFlow.first()
        }.getOrNull().orEmpty()
        evaluateUpdatePrompt(cachedConfig, dismissedVersion, forceShowIfAvailable = false)

        // 2. Silently fetch fresh config.json with cache-busting ?t=<now>
        val fetched = remoteConfigFetcher.fetchLatest()
        if (fetched != null) {
            val (rawJson, freshConfig) = fetched
            preferencesManager.saveCachedRemoteConfigJson(rawJson)
            _remoteConfig.value = freshConfig
            evaluateUpdatePrompt(freshConfig, dismissedVersion, forceShowIfAvailable = false)
        }
    }

    private fun evaluateUpdatePrompt(
        config: RemoteConfig,
        dismissedVersion: String,
        forceShowIfAvailable: Boolean
    ) {
        val update = config.update
        val isForced = update.isForceUpdate(AppConfig.APP_VERSION)
        val isAvailable = update.isUpdateAvailable(AppConfig.APP_VERSION)
        if (!isForced && !isAvailable) return

        val normalizedLatest = RemoteConfigParser.normalizeVersion(update.latestVersion)
        val alreadyDismissed = normalizedLatest.isNotEmpty() &&
            RemoteConfigParser.normalizeVersion(dismissedVersion) == normalizedLatest

        if (isForced || forceShowIfAvailable || !alreadyDismissed) {
            _updatePrompt.value = UpdatePromptState(
                visible = true,
                forced = isForced,
                latestVersion = update.latestVersion,
                title = update.title.ifBlank { "Update Available" },
                message = update.message.ifBlank {
                    "A newer version (${update.latestVersion}) of ${AppConfig.APP_NAME} is ready."
                },
                url = update.url
            )
        }
    }

    fun dismissUpdatePrompt() {
        val current = _updatePrompt.value
        if (current.forced) return
        _updatePrompt.value = current.copy(visible = false)
        val normalized = RemoteConfigParser.normalizeVersion(current.latestVersion)
        if (normalized.isNotEmpty()) {
            viewModelScope.launch {
                preferencesManager.setDismissedUpdateVersion(normalized)
            }
        }
    }

    fun checkForUpdatesManually() {
        if (_isCheckingUpdate.value) return
        viewModelScope.launch {
            _isCheckingUpdate.value = true
            try {
                val fetched = remoteConfigFetcher.fetchLatest()
                if (fetched == null) {
                    // Offline: still check cached config in case an update was already cached
                    val cached = _remoteConfig.value
                    if (cached.update.isForceUpdate(AppConfig.APP_VERSION) ||
                        cached.update.isUpdateAvailable(AppConfig.APP_VERSION)
                    ) {
                        evaluateUpdatePrompt(cached, dismissedVersion = "", forceShowIfAvailable = true)
                    } else {
                        _toastEvents.tryEmit(ToastEvent.Info("Unable to check for updates (offline)"))
                    }
                    return@launch
                }
                val (rawJson, freshConfig) = fetched
                preferencesManager.saveCachedRemoteConfigJson(rawJson)
                _remoteConfig.value = freshConfig

                if (freshConfig.update.isForceUpdate(AppConfig.APP_VERSION) ||
                    freshConfig.update.isUpdateAvailable(AppConfig.APP_VERSION)
                ) {
                    evaluateUpdatePrompt(freshConfig, dismissedVersion = "", forceShowIfAvailable = true)
                } else {
                    _toastEvents.tryEmit(
                        ToastEvent.Info("You're on the latest version (${AppConfig.APP_VERSION})")
                    )
                }
            } finally {
                _isCheckingUpdate.value = false
            }
        }
    }

    fun refreshStorageStats() {
        viewModelScope.launch {
            _storageStats.value = repository.computeStorageStats()
        }
    }

    // --- Settings, Models & Write-Only Keys ---

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            preferencesManager.setThemeMode(mode)
        }
    }

    fun toggleLightDark(currentlyDark: Boolean) {
        val next = if (currentlyDark) ThemeMode.LIGHT else ThemeMode.DARK
        viewModelScope.launch {
            preferencesManager.setThemeMode(next)
        }
    }

    fun selectProvider(provider: AiProvider) {
        viewModelScope.launch {
            preferencesManager.setProvider(provider)
        }
    }

    fun selectGeminiModel(option: GeminiModelOption) {
        viewModelScope.launch {
            preferencesManager.setGeminiModelOption(option)
        }
    }

    fun setGeminiCustomModelId(modelId: String) {
        viewModelScope.launch {
            preferencesManager.setGeminiCustomModelId(modelId)
        }
    }

    fun setOpenRouterModelId(modelId: String) {
        viewModelScope.launch {
            preferencesManager.setOpenRouterModelId(modelId)
        }
    }

    fun saveApiKey(provider: AiProvider, key: String) {
        val clean = key.trim()
        if (clean.isEmpty()) {
            _toastEvents.tryEmit(ToastEvent.Error("Enter a valid API key before saving"))
            return
        }
        viewModelScope.launch {
            preferencesManager.saveApiKey(provider, clean)
            _toastEvents.tryEmit(ToastEvent.Info("${provider.displayName} API key locked & saved"))
        }
    }

    fun removeApiKey(provider: AiProvider) {
        viewModelScope.launch {
            preferencesManager.removeApiKey(provider)
            _toastEvents.tryEmit(ToastEvent.Info("${provider.displayName} API key removed"))
        }
    }

    fun setDefaultRetention(option: RetentionOption) {
        viewModelScope.launch {
            preferencesManager.setDefaultRetention(option)
        }
    }

    fun setAudioSaveBehavior(behavior: AudioSaveBehavior) {
        viewModelScope.launch {
            preferencesManager.setAudioSaveBehavior(behavior)
        }
    }

    fun setDefaultVideoPreset(preset: VideoCompressPreset) {
        viewModelScope.launch {
            preferencesManager.setDefaultVideoPreset(preset)
            _compressState.update { it.copy(videoPreset = preset) }
        }
    }

    fun setDefaultFixHdr(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setDefaultFixHdr(enabled)
            _compressState.update { it.copy(fixHdrColors = enabled) }
        }
    }

    fun setDefaultAudioPreset(preset: AudioCompressPreset) {
        viewModelScope.launch {
            preferencesManager.setDefaultAudioPreset(preset)
            _compressState.update { it.copy(audioPreset = preset) }
        }
    }

    fun addAutoCorrectRule(wrong: String, correct: String) {
        if (wrong.isBlank() || correct.isBlank()) {
            _toastEvents.tryEmit(ToastEvent.Error("Enter both the wrong word and correct replacement"))
            return
        }
        viewModelScope.launch {
            preferencesManager.addAutoCorrectRule(wrong, correct)
            _toastEvents.tryEmit(ToastEvent.Info("Added rule: ${wrong.trim()} → ${correct.trim()}"))
        }
    }

    fun removeAutoCorrectRule(ruleId: String) {
        viewModelScope.launch {
            preferencesManager.removeAutoCorrectRule(ruleId)
        }
    }

    fun resetDailyUsage() {
        viewModelScope.launch {
            preferencesManager.resetDailyUsage()
            _toastEvents.tryEmit(ToastEvent.Info("Today's usage stats reset"))
        }
    }

    fun deleteAudioFiles(projectIds: Set<String>) {
        if (projectIds.isEmpty()) return
        viewModelScope.launch {
            if (_activeProject.value?.id in projectIds) {
                audioPlayer.release()
                _activeProject.value = _activeProject.value?.copy(audioFilePath = null, audioDurationMs = 0L)
            }
            val count = repository.deleteAudioFilesForProjects(projectIds)
            refreshStorageStats()
            _toastEvents.tryEmit(
                ToastEvent.Info("Deleted $count audio ${if (count == 1) "file" else "files"}")
            )
        }
    }

    fun shareAudioFiles(projectIds: Set<String>) {
        val items = _storageStats.value.audioItems.filter { it.projectId in projectIds }
        val files = items.map { File(it.filePath) }.filter { it.exists() }
        if (files.isEmpty()) {
            _toastEvents.tryEmit(ToastEvent.Error("No audio files selected"))
            return
        }
        val res = LocalFileSaver.shareFiles(appContext, files, "audio/*", "Share Audio Files")
        if (res.isFailure) {
            _toastEvents.tryEmit(ToastEvent.Error("Unable to open Share Sheet"))
        }
    }

    fun clearAllStorage() {
        viewModelScope.launch {
            audioPlayer.release()
            _activeProject.value = null
            repository.clearAllStorage()
            refreshStorageStats()
            _toastEvents.tryEmit(ToastEvent.Info("All local projects and audio cleared"))
        }
    }

    fun dismissMissingApiKeyDialog() {
        _showMissingApiKeyDialog.value = false
    }

    private fun requireActiveApiKey(): AiRequestConfig? {
        val s = settings.value
        if (s.activeApiKey.isBlank()) {
            _showMissingApiKeyDialog.value = true
            return null
        }
        return AiRequestConfig(
            provider = s.provider,
            apiKey = s.activeApiKey,
            geminiModelId = s.resolvedGeminiModelId,
            openRouterModelId = s.resolvedOpenRouterModelId
        )
    }

    // --- Project Navigation & Management ---

    fun openProject(project: ProjectModel) {
        _activeProject.value = project
        val estimatedDuration = project.audioDurationMs.takeIf { it > 0L }
            ?: (project.activeSegments.maxOfOrNull { it.endMs } ?: 5000L)
        audioPlayer.loadAudio(project.audioFilePath, estimatedDuration)
    }

    fun closeEditor() {
        val current = _activeProject.value
        if (current != null) {
            viewModelScope.launch {
                repository.saveProject(current.copy(updatedAt = System.currentTimeMillis()))
                refreshStorageStats()
            }
        }
        audioPlayer.release()
        _activeProject.value = null
    }

    fun toggleProjectPin(projectId: String, currentlyPinned: Boolean) {
        val next = !currentlyPinned
        viewModelScope.launch {
            repository.updateProjectPinned(projectId, next)
            val current = _activeProject.value
            if (current != null && current.id == projectId) {
                _activeProject.value = current.copy(isPinned = next)
            }
            _toastEvents.tryEmit(ToastEvent.Info(if (next) "Project pinned to top" else "Project unpinned"))
        }
    }

    fun updateProjectExpiry(projectId: String, option: RetentionOption) {
        viewModelScope.launch {
            repository.updateProjectExpiry(projectId, option)
            val current = _activeProject.value
            if (current != null && current.id == projectId) {
                val now = System.currentTimeMillis()
                _activeProject.value = current.copy(
                    retentionOption = option,
                    expiresAt = option.computeExpiryTimestamp(now),
                    updatedAt = now
                )
            }
            _toastEvents.tryEmit(ToastEvent.Info("Expiry updated to ${option.label}"))
        }
    }

    fun renameProject(projectId: String, newTitle: String) {
        val clean = newTitle.trim()
        if (clean.isEmpty()) {
            _toastEvents.tryEmit(ToastEvent.Error("Enter a valid project name"))
            return
        }
        viewModelScope.launch {
            repository.updateProjectTitle(projectId, clean)
            val current = _activeProject.value
            if (current != null && current.id == projectId) {
                _activeProject.value = current.copy(title = clean, updatedAt = System.currentTimeMillis())
            }
            _toastEvents.tryEmit(ToastEvent.Info("Renamed to $clean"))
        }
    }

    fun deleteProject(projectId: String) {
        viewModelScope.launch {
            if (_activeProject.value?.id == projectId) {
                audioPlayer.release()
                _activeProject.value = null
            }
            _pendingQueue.update { list -> list.filterNot { it.projectId == projectId } }
            repository.deleteProject(projectId)
            refreshStorageStats()
            _toastEvents.tryEmit(ToastEvent.Info("Project permanently deleted"))
        }
    }

    // --- Decoupled Offline-First Creation Flows (+ Button Options) ---

    /**
     * Option 1: Upload Video -> extracts audio locally offline first, saves immediately,
     * and opens Editor Screen right away (no API key required!).
     */
    fun importVideoOffline(uri: Uri) {
        val projectId = UUID.randomUUID().toString()
        val retention = settings.value.defaultRetention

        viewModelScope.launch {
            _processingState.value = ProcessingOverlayState(
                isVisible = true,
                steps = listOf(ProcessingStep.EXTRACTING_AUDIO, ProcessingStep.DONE),
                currentStepIndex = 0,
                detail = "Extracting audio track locally on device..."
            )
            try {
                val extracted = AudioExtractor.extractAudioFromVideo(appContext, uri, projectId)
                val now = System.currentTimeMillis()
                val project = ProjectModel(
                    id = projectId,
                    title = extracted.displayName,
                    createdAt = now,
                    updatedAt = now,
                    isPinned = false,
                    retentionOption = retention,
                    expiresAt = retention.computeExpiryTimestamp(now),
                    audioFilePath = extracted.file.absolutePath,
                    audioDurationMs = extracted.durationMs,
                    waveform = extracted.waveform,
                    originalSegments = emptyList(),
                    hinglishSegments = null,
                    languageMode = LanguageMode.ORIGINAL,
                    wordChunkMode = WordChunkMode.FULL_LINE,
                    removeFillerWords = false,
                    stripPunctuation = false,
                    textCasing = TextCasing.ORIGINAL,
                    timestampShiftMs = 0L,
                    magicContent = null
                )
                repository.saveProject(project)
                refreshStorageStats()

                _processingState.value = _processingState.value.copy(
                    currentStepIndex = 1,
                    detail = "Audio extracted & saved locally!"
                )
                delay(320L)
                _processingState.value = ProcessingOverlayState(isVisible = false)
                openProject(project)
            } catch (e: Exception) {
                _processingState.value = ProcessingOverlayState(isVisible = false)
                _toastEvents.tryEmit(ToastEvent.Error(e.message ?: "Failed to extract audio from video"))
            }
        }
    }

    /**
     * Option 2: Upload Audio -> saves locally offline immediately and opens Editor Screen right away.
     */
    fun importAudioOffline(uri: Uri) {
        val projectId = UUID.randomUUID().toString()
        val retention = settings.value.defaultRetention

        viewModelScope.launch {
            _processingState.value = ProcessingOverlayState(
                isVisible = true,
                steps = listOf(ProcessingStep.SAVING_AUDIO, ProcessingStep.DONE),
                currentStepIndex = 0,
                detail = "Saving audio locally on device..."
            )
            try {
                val imported = AudioExtractor.importAudioFile(appContext, uri, projectId)
                val now = System.currentTimeMillis()
                val project = ProjectModel(
                    id = projectId,
                    title = imported.displayName,
                    createdAt = now,
                    updatedAt = now,
                    isPinned = false,
                    retentionOption = retention,
                    expiresAt = retention.computeExpiryTimestamp(now),
                    audioFilePath = imported.file.absolutePath,
                    audioDurationMs = imported.durationMs,
                    waveform = imported.waveform,
                    originalSegments = emptyList(),
                    hinglishSegments = null,
                    languageMode = LanguageMode.ORIGINAL,
                    wordChunkMode = WordChunkMode.FULL_LINE,
                    removeFillerWords = false,
                    stripPunctuation = false,
                    textCasing = TextCasing.ORIGINAL,
                    timestampShiftMs = 0L,
                    magicContent = null
                )
                repository.saveProject(project)
                refreshStorageStats()

                _processingState.value = _processingState.value.copy(
                    currentStepIndex = 1,
                    detail = "Audio saved locally!"
                )
                delay(280L)
                _processingState.value = ProcessingOverlayState(isVisible = false)
                openProject(project)
            } catch (e: Exception) {
                _processingState.value = ProcessingOverlayState(isVisible = false)
                _toastEvents.tryEmit(ToastEvent.Error(e.message ?: "Failed to import audio"))
            }
        }
    }

    /**
     * Option 3: Record Voice -> minimalist built-in mic recorder.
     */
    fun startVoiceRecording(): Boolean {
        val projectId = UUID.randomUUID().toString()
        val res = voiceRecorder.startRecording(projectId)
        if (res.isFailure) {
            _toastEvents.tryEmit(ToastEvent.Error("Could not start microphone recording"))
            return false
        }
        return true
    }

    fun stopAndSaveVoiceRecording() {
        val completed = voiceRecorder.stopRecording()
        if (completed == null) {
            _toastEvents.tryEmit(ToastEvent.Error("Recording was too short to save"))
            return
        }
        val retention = settings.value.defaultRetention
        val timeLabel = SimpleDateFormat("MMM d, h:mm a", Locale.US).format(Date())

        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val project = ProjectModel(
                id = completed.projectId,
                title = "Voice Note ($timeLabel)",
                createdAt = now,
                updatedAt = now,
                isPinned = false,
                retentionOption = retention,
                expiresAt = retention.computeExpiryTimestamp(now),
                audioFilePath = completed.file.absolutePath,
                audioDurationMs = completed.durationMs,
                waveform = completed.waveform,
                originalSegments = emptyList(),
                hinglishSegments = null,
                languageMode = LanguageMode.ORIGINAL,
                wordChunkMode = WordChunkMode.FULL_LINE,
                removeFillerWords = false,
                stripPunctuation = false,
                textCasing = TextCasing.ORIGINAL,
                timestampShiftMs = 0L,
                magicContent = null
            )
            repository.saveProject(project)
            refreshStorageStats()
            openProject(project)
            _toastEvents.tryEmit(ToastEvent.Info("Voice recording saved locally"))
        }
    }

    fun cancelVoiceRecording() {
        voiceRecorder.cancelRecording()
    }

    /**
     * Option 4: Convert File to Hinglish -> immediately opens the loaded text/SRT inside the
     * Editor Screen offline so the user can view, scroll, edit, use Tools, and convert anytime.
     */
    fun openTextOrSrtInEditorOffline(rawContent: String, suggestedTitle: String) {
        if (rawContent.isBlank()) {
            _toastEvents.tryEmit(ToastEvent.Error("Upload a .SRT / .TXT file or paste text first"))
            return
        }
        val projectId = UUID.randomUUID().toString()
        val retention = settings.value.defaultRetention

        viewModelScope.launch {
            val (parsedSegments, isSrt) = SrtEngine.parseSrtOrPlainText(rawContent)
            if (parsedSegments.isEmpty()) {
                _toastEvents.tryEmit(ToastEvent.Error("No readable lines or SRT cues found"))
                return@launch
            }
            val now = System.currentTimeMillis()
            val durationMs = parsedSegments.maxOfOrNull { it.endMs } ?: 5000L
            val project = ProjectModel(
                id = projectId,
                title = suggestedTitle.ifBlank { if (isSrt) "Imported Subtitles" else "Imported Text" },
                createdAt = now,
                updatedAt = now,
                isPinned = false,
                retentionOption = retention,
                expiresAt = retention.computeExpiryTimestamp(now),
                audioFilePath = null,
                audioDurationMs = durationMs,
                waveform = AudioExtractor.defaultWaveform(),
                originalSegments = parsedSegments,
                hinglishSegments = null,
                languageMode = LanguageMode.ORIGINAL,
                wordChunkMode = WordChunkMode.FULL_LINE,
                removeFillerWords = false,
                stripPunctuation = false,
                textCasing = TextCasing.ORIGINAL,
                timestampShiftMs = 0L,
                magicContent = null
            )
            repository.saveProject(project)
            refreshStorageStats()
            openProject(project)
            _toastEvents.tryEmit(
                ToastEvent.Info(
                    if (isSrt) "Loaded ${parsedSegments.size} SRT cues. Tap Convert to Hinglish anytime."
                    else "Loaded ${parsedSegments.size} lines in Editor."
                )
            )
        }
    }

    suspend fun readTextFromUri(uri: Uri): Pair<String, String>? = withContext(Dispatchers.IO) {
        runCatching {
            val title = AudioExtractor.resolveDisplayName(appContext, uri, "Imported Subtitle")
            val text = appContext.contentResolver.openInputStream(uri)?.bufferedReader()?.use {
                it.readText()
            }.orEmpty()
            Pair(title, text)
        }.getOrNull()
    }

    // --- On-Demand AI Transcription ("Transcribe Now" / "Re-Transcribe") ---

    fun transcribeActiveProject() {
        val current = _activeProject.value ?: return
        val audioPath = current.audioFilePath
        if (audioPath.isNullOrBlank() || !File(audioPath).exists()) {
            _toastEvents.tryEmit(ToastEvent.Error("No local audio file attached to this project"))
            return
        }
        val config = requireActiveApiKey() ?: return

        viewModelScope.launch {
            _processingState.value = ProcessingOverlayState(
                isVisible = true,
                steps = listOf(ProcessingStep.TRANSCRIBING, ProcessingStep.DONE),
                currentStepIndex = 0,
                detail = "Transcribing audio with ${config.provider.displayName}..."
            )

            try {
                val audioFile = File(audioPath)
                val mimeType = guessAudioMimeFromFile(audioFile)
                val aiResult = aiService.transcribeAudio(
                    config = config,
                    audioFile = audioFile,
                    mimeType = mimeType,
                    durationMs = current.audioDurationMs
                )
                preferencesManager.recordUsage(aiResult.tokensUsed)

                // Automatically apply user's Custom Auto-Correct Dictionary
                val correctedSegments = SrtEngine.applyAutoCorrectDictionary(
                    segments = aiResult.data,
                    rules = settings.value.autoCorrectRules
                )

                val latest = _activeProject.value ?: current
                val updated = latest.copy(
                    originalSegments = correctedSegments,
                    englishSegments = null,
                    hinglishSegments = null,
                    languageMode = LanguageMode.ORIGINAL,
                    updatedAt = System.currentTimeMillis()
                )
                _activeProject.value = updated
                repository.saveProject(updated)
                removePendingJob(updated.id, PendingJobType.TRANSCRIBE)
                refreshStorageStats()

                _processingState.value = _processingState.value.copy(
                    currentStepIndex = 1,
                    detail = "Transcription complete!"
                )
                delay(380L)
                _processingState.value = ProcessingOverlayState(isVisible = false)
                _toastEvents.tryEmit(ToastEvent.Info("Transcribed ${correctedSegments.size} segments"))
            } catch (e: Exception) {
                _processingState.value = ProcessingOverlayState(isVisible = false)
                if (e !is ApiKeyInvalidException) {
                    enqueuePendingJob(current.id, current.title, PendingJobType.TRANSCRIBE, e.message ?: "API failed")
                }
                emitApiError(e)
            }
        }
    }

    private fun enqueuePendingJob(projectId: String, title: String, type: PendingJobType, error: String) {
        _pendingQueue.update { list ->
            val filtered = list.filterNot { it.projectId == projectId && it.jobType == type }
            filtered + PendingQueueJob(
                projectId = projectId,
                projectTitle = title,
                jobType = type,
                lastError = error
            )
        }
    }

    private fun removePendingJob(projectId: String, type: PendingJobType) {
        _pendingQueue.update { list ->
            list.filterNot { it.projectId == projectId && it.jobType == type }
        }
    }

    fun retryPendingQueue() {
        val jobs = _pendingQueue.value
        if (jobs.isEmpty() || _isRetryingQueue.value) return
        val config = requireActiveApiKey() ?: return

        viewModelScope.launch {
            _isRetryingQueue.value = true
            var completedCount = 0
            for (job in jobs) {
                val project = repository.getProjectById(job.projectId)
                if (project == null) {
                    removePendingJob(job.projectId, job.jobType)
                    continue
                }
                try {
                    when (job.jobType) {
                        PendingJobType.TRANSCRIBE -> {
                            val path = project.audioFilePath
                            if (path.isNullOrBlank() || !File(path).exists()) {
                                removePendingJob(job.projectId, job.jobType)
                                continue
                            }
                            val file = File(path)
                            val res = aiService.transcribeAudio(
                                config = config,
                                audioFile = file,
                                mimeType = guessAudioMimeFromFile(file),
                                durationMs = project.audioDurationMs
                            )
                            preferencesManager.recordUsage(res.tokensUsed)
                            val corrected = SrtEngine.applyAutoCorrectDictionary(res.data, settings.value.autoCorrectRules)
                            val updated = project.copy(
                                originalSegments = corrected,
                                languageMode = LanguageMode.ORIGINAL,
                                updatedAt = System.currentTimeMillis()
                            )
                            repository.saveProject(updated)
                            if (_activeProject.value?.id == updated.id) _activeProject.value = updated
                            removePendingJob(job.projectId, job.jobType)
                            completedCount++
                        }
                        PendingJobType.TRANSLATE_ENGLISH -> {
                            if (project.originalSegments.isEmpty()) {
                                removePendingJob(job.projectId, job.jobType)
                                continue
                            }
                            val res = aiService.translateToEnglish(config, project.originalSegments)
                            preferencesManager.recordUsage(res.tokensUsed)
                            val corrected = SrtEngine.applyAutoCorrectDictionary(res.data, settings.value.autoCorrectRules)
                            val updated = project.copy(
                                englishSegments = corrected,
                                languageMode = LanguageMode.ENGLISH,
                                updatedAt = System.currentTimeMillis()
                            )
                            repository.saveProject(updated)
                            if (_activeProject.value?.id == updated.id) _activeProject.value = updated
                            removePendingJob(job.projectId, job.jobType)
                            completedCount++
                        }
                        PendingJobType.SMART_HINGLISH -> {
                            if (project.originalSegments.isEmpty()) {
                                removePendingJob(job.projectId, job.jobType)
                                continue
                            }
                            val res = aiService.smartSpellFixHinglish(config, project.originalSegments)
                            preferencesManager.recordUsage(res.tokensUsed)
                            val corrected = SrtEngine.applyAutoCorrectDictionary(res.data, settings.value.autoCorrectRules)
                            val updated = project.copy(
                                hinglishSegments = corrected,
                                languageMode = LanguageMode.HINGLISH,
                                updatedAt = System.currentTimeMillis()
                            )
                            repository.saveProject(updated)
                            if (_activeProject.value?.id == updated.id) _activeProject.value = updated
                            removePendingJob(job.projectId, job.jobType)
                            completedCount++
                        }
                    }
                } catch (e: Exception) {
                    emitApiError(e)
                    break
                }
            }
            _isRetryingQueue.value = false
            if (completedCount > 0) {
                refreshStorageStats()
                _toastEvents.tryEmit(
                    ToastEvent.Info("Completed $completedCount queued ${if (completedCount == 1) "file" else "files"}")
                )
            }
        }
    }

    private fun guessAudioMimeFromFile(file: File): String = when (file.extension.lowercase()) {
        "mp3" -> "audio/mpeg"
        "wav" -> "audio/wav"
        "ogg" -> "audio/ogg"
        "webm" -> "audio/webm"
        "flac" -> "audio/flac"
        else -> "audio/mp4"
    }

    // --- Editor Operations ---

    fun updateTitle(newTitle: String) {
        val current = _activeProject.value ?: return
        _activeProject.value = current.copy(title = newTitle)
    }

    fun saveActiveProject() {
        val current = _activeProject.value ?: return
        viewModelScope.launch {
            val updated = current.copy(
                title = current.title.ifBlank { "Untitled Transcription" },
                updatedAt = System.currentTimeMillis()
            )
            _activeProject.value = updated
            repository.saveProject(updated)
            refreshStorageStats()
            _toastEvents.tryEmit(ToastEvent.Info("Project saved locally"))
        }
    }

    fun setWordChunkMode(mode: WordChunkMode) {
        val current = _activeProject.value ?: return
        val updated = current.copy(wordChunkMode = mode, updatedAt = System.currentTimeMillis())
        _activeProject.value = updated
        persistQuietly(updated)
    }

    fun setRemoveFillerWords(enabled: Boolean) {
        val current = _activeProject.value ?: return
        val updated = current.copy(removeFillerWords = enabled, updatedAt = System.currentTimeMillis())
        _activeProject.value = updated
        persistQuietly(updated)
    }

    fun setStripPunctuation(enabled: Boolean) {
        val current = _activeProject.value ?: return
        val updated = current.copy(stripPunctuation = enabled, updatedAt = System.currentTimeMillis())
        _activeProject.value = updated
        persistQuietly(updated)
    }

    fun setTextCasing(casing: TextCasing) {
        val current = _activeProject.value ?: return
        val updated = current.copy(textCasing = casing, updatedAt = System.currentTimeMillis())
        _activeProject.value = updated
        persistQuietly(updated)
    }

    fun adjustTimestampShift(deltaMs: Long) {
        val current = _activeProject.value ?: return
        val nextShift = (current.timestampShiftMs + deltaMs).coerceIn(-60_000L, 60_000L)
        val updated = current.copy(timestampShiftMs = nextShift, updatedAt = System.currentTimeMillis())
        _activeProject.value = updated
        persistQuietly(updated)
    }

    fun resetTimestampShift() {
        val current = _activeProject.value ?: return
        val updated = current.copy(timestampShiftMs = 0L, updatedAt = System.currentTimeMillis())
        _activeProject.value = updated
        persistQuietly(updated)
    }

    private fun updateActiveLanguageSegments(
        project: ProjectModel,
        transform: (List<TranscriptSegment>) -> List<TranscriptSegment>
    ): ProjectModel {
        val now = System.currentTimeMillis()
        return when {
            project.languageMode == LanguageMode.ENGLISH && !project.englishSegments.isNullOrEmpty() ->
                project.copy(englishSegments = transform(project.englishSegments), updatedAt = now)
            project.languageMode == LanguageMode.HINGLISH && !project.hinglishSegments.isNullOrEmpty() ->
                project.copy(hinglishSegments = transform(project.hinglishSegments), updatedAt = now)
            else ->
                project.copy(originalSegments = transform(project.originalSegments), updatedAt = now)
        }
    }

    fun updateSegmentText(segmentId: Int, newText: String) {
        val current = _activeProject.value ?: return
        val updated = updateActiveLanguageSegments(current) { list ->
            list.map { seg ->
                if (seg.id == segmentId) seg.copy(text = newText, words = emptyList()) else seg
            }
        }
        _activeProject.value = updated
    }

    fun splitSegment(segmentId: Int) {
        val current = _activeProject.value ?: return
        val beforeSize = current.activeSegments.size
        val updated = updateActiveLanguageSegments(current) { list ->
            SrtEngine.splitSegment(list, segmentId)
        }
        if (updated.activeSegments.size > beforeSize) {
            _activeProject.value = updated
            persistQuietly(updated)
            _toastEvents.tryEmit(ToastEvent.Info("Segment split into two"))
        } else {
            _toastEvents.tryEmit(ToastEvent.Info("Need at least 2 words to split"))
        }
    }

    fun mergeSegmentWithNext(segmentId: Int) {
        val current = _activeProject.value ?: return
        val beforeSize = current.activeSegments.size
        val updated = updateActiveLanguageSegments(current) { list ->
            SrtEngine.mergeWithNextSegment(list, segmentId)
        }
        if (updated.activeSegments.size < beforeSize) {
            _activeProject.value = updated
            persistQuietly(updated)
            _toastEvents.tryEmit(ToastEvent.Info("Merged with next segment"))
        } else {
            _toastEvents.tryEmit(ToastEvent.Info("No next segment to merge"))
        }
    }

    fun deleteSegment(segmentId: Int) {
        val current = _activeProject.value ?: return
        val updated = updateActiveLanguageSegments(current) { list ->
            SrtEngine.deleteSegment(list, segmentId)
        }
        _activeProject.value = updated
        persistQuietly(updated)
        _toastEvents.tryEmit(ToastEvent.Info("Segment deleted"))
    }

    fun replaceAllText(find: String, replaceWith: String): Int {
        if (find.isEmpty()) return 0
        val current = _activeProject.value ?: return 0
        var count = 0
        val regex = Regex(Regex.escape(find), RegexOption.IGNORE_CASE)

        val updated = updateActiveLanguageSegments(current) { list ->
            list.map { seg ->
                val matches = regex.findAll(seg.text).count()
                if (matches > 0) {
                    count += matches
                    seg.copy(text = regex.replace(seg.text, Regex.escapeReplacement(replaceWith)), words = emptyList())
                } else {
                    seg
                }
            }
        }
        _activeProject.value = updated
        persistQuietly(updated)
        if (count > 0) {
            _toastEvents.tryEmit(ToastEvent.Info("Replaced $count occurrence${if (count == 1) "" else "s"}"))
        } else {
            _toastEvents.tryEmit(ToastEvent.Info("No matches found"))
        }
        return count
    }

    fun selectLanguageMode(mode: LanguageMode) {
        val current = _activeProject.value ?: return
        when (mode) {
            LanguageMode.ORIGINAL -> {
                val updated = current.copy(languageMode = LanguageMode.ORIGINAL, updatedAt = System.currentTimeMillis())
                _activeProject.value = updated
                persistQuietly(updated)
            }
            LanguageMode.ENGLISH -> {
                if (current.originalSegments.isEmpty()) {
                    _toastEvents.tryEmit(ToastEvent.Error("Transcribe or add text first"))
                    return
                }
                if (!current.englishSegments.isNullOrEmpty()) {
                    val updated = current.copy(languageMode = LanguageMode.ENGLISH, updatedAt = System.currentTimeMillis())
                    _activeProject.value = updated
                    persistQuietly(updated)
                } else {
                    translateActiveToEnglish()
                }
            }
            LanguageMode.HINGLISH -> {
                if (current.originalSegments.isEmpty()) {
                    _toastEvents.tryEmit(ToastEvent.Error("Transcribe or add text first"))
                    return
                }
                if (!current.hinglishSegments.isNullOrEmpty()) {
                    val updated = current.copy(languageMode = LanguageMode.HINGLISH, updatedAt = System.currentTimeMillis())
                    _activeProject.value = updated
                    persistQuietly(updated)
                } else {
                    convertActiveToHinglishOffline()
                }
            }
        }
    }

    /**
     * 100% Offline instant Devanagari -> Romanized Hinglish transliteration + Auto-Correct dictionary.
     */
    fun convertActiveToHinglishOffline() {
        val current = _activeProject.value ?: return
        if (current.originalSegments.isEmpty()) {
            _toastEvents.tryEmit(ToastEvent.Error("Transcribe or add text first"))
            return
        }
        val transliterated = SrtEngine.transliterateDevanagariToHinglishOffline(current.originalSegments)
        val corrected = SrtEngine.applyAutoCorrectDictionary(transliterated, settings.value.autoCorrectRules)
        val updated = current.copy(
            hinglishSegments = corrected,
            languageMode = LanguageMode.HINGLISH,
            updatedAt = System.currentTimeMillis()
        )
        _activeProject.value = updated
        persistQuietly(updated)
        _toastEvents.tryEmit(ToastEvent.Info("Converted to Hinglish offline"))
    }

    /**
     * Uses AI API to translate transcript segments to natural English while preserving timestamps.
     */
    fun translateActiveToEnglish() {
        val current = _activeProject.value ?: return
        if (current.originalSegments.isEmpty()) {
            _toastEvents.tryEmit(ToastEvent.Error("Transcribe or add text first"))
            return
        }
        val config = requireActiveApiKey() ?: return

        viewModelScope.launch {
            _isTranslatingEnglish.value = true
            _processingState.value = ProcessingOverlayState(
                isVisible = true,
                steps = listOf(ProcessingStep.TRANSLATING_ENGLISH, ProcessingStep.DONE),
                currentStepIndex = 0,
                detail = "Translating ${current.originalSegments.size} segments to English..."
            )
            try {
                val aiResult = aiService.translateToEnglish(
                    config = config,
                    segments = current.originalSegments
                )
                preferencesManager.recordUsage(aiResult.tokensUsed)
                val corrected = SrtEngine.applyAutoCorrectDictionary(
                    segments = aiResult.data,
                    rules = settings.value.autoCorrectRules
                )
                val latest = _activeProject.value ?: current
                val updated = latest.copy(
                    englishSegments = corrected,
                    languageMode = LanguageMode.ENGLISH,
                    updatedAt = System.currentTimeMillis()
                )
                _activeProject.value = updated
                repository.saveProject(updated)
                removePendingJob(updated.id, PendingJobType.TRANSLATE_ENGLISH)

                _processingState.value = _processingState.value.copy(
                    currentStepIndex = 1,
                    detail = "English translation ready!"
                )
                delay(280L)
                _processingState.value = ProcessingOverlayState(isVisible = false)
                _toastEvents.tryEmit(ToastEvent.Info("Translated to English"))
            } catch (e: Exception) {
                _processingState.value = ProcessingOverlayState(isVisible = false)
                if (e !is ApiKeyInvalidException) {
                    enqueuePendingJob(current.id, current.title, PendingJobType.TRANSLATE_ENGLISH, e.message ?: "API failed")
                }
                emitApiError(e)
            } finally {
                _isTranslatingEnglish.value = false
            }
        }
    }

    /**
     * Uses AI API for AI Spell-Fix & Smart Hinglish (fixes phonetic errors like वाटसेप -> WhatsApp
     * while preserving exact segment timestamps).
     */
    fun smartSpellFixActiveHinglish() {
        val current = _activeProject.value ?: return
        if (current.originalSegments.isEmpty()) {
            _toastEvents.tryEmit(ToastEvent.Error("Transcribe or add text first"))
            return
        }
        val config = requireActiveApiKey() ?: return

        viewModelScope.launch {
            _isConvertingHinglish.value = true
            _processingState.value = ProcessingOverlayState(
                isVisible = true,
                steps = listOf(ProcessingStep.CONVERTING_HINGLISH, ProcessingStep.DONE),
                currentStepIndex = 0,
                detail = "Fixing spelling & converting ${current.originalSegments.size} lines..."
            )
            try {
                val aiResult = aiService.smartSpellFixHinglish(
                    config = config,
                    segments = current.originalSegments
                )
                preferencesManager.recordUsage(aiResult.tokensUsed)

                val corrected = SrtEngine.applyAutoCorrectDictionary(
                    segments = aiResult.data,
                    rules = settings.value.autoCorrectRules
                )

                val latest = _activeProject.value ?: current
                val updated = latest.copy(
                    hinglishSegments = corrected,
                    languageMode = LanguageMode.HINGLISH,
                    updatedAt = System.currentTimeMillis()
                )
                _activeProject.value = updated
                repository.saveProject(updated)
                removePendingJob(updated.id, PendingJobType.SMART_HINGLISH)

                _processingState.value = _processingState.value.copy(
                    currentStepIndex = 1,
                    detail = "Smart Hinglish ready!"
                )
                delay(300L)
                _processingState.value = ProcessingOverlayState(isVisible = false)
                _toastEvents.tryEmit(ToastEvent.Info("AI Spell-Fix & Smart Hinglish applied"))
            } catch (e: Exception) {
                _processingState.value = ProcessingOverlayState(isVisible = false)
                if (e !is ApiKeyInvalidException) {
                    enqueuePendingJob(current.id, current.title, PendingJobType.SMART_HINGLISH, e.message ?: "API failed")
                }
                emitApiError(e)
            } finally {
                _isConvertingHinglish.value = false
            }
        }
    }

    fun generateMagicAssets(forceRegenerate: Boolean = false) {
        val current = _activeProject.value ?: return
        if (!forceRegenerate && current.magicContent != null) return
        val config = requireActiveApiKey() ?: return

        val transcriptText = SrtEngine.exportPlainText(
            segments = current.activeSegments,
            removeFillerWords = current.removeFillerWords,
            stripPunctuation = false,
            casing = TextCasing.ORIGINAL
        )
        if (transcriptText.isBlank()) {
            _toastEvents.tryEmit(ToastEvent.Error("Transcribe or add text first"))
            return
        }

        viewModelScope.launch {
            _isGeneratingMagic.value = true
            try {
                val aiResult = aiService.generateMagicContent(
                    config = config,
                    transcriptText = transcriptText
                )
                preferencesManager.recordUsage(aiResult.tokensUsed)

                val latest = _activeProject.value ?: current
                val updated = latest.copy(
                    magicContent = aiResult.data,
                    updatedAt = System.currentTimeMillis()
                )
                _activeProject.value = updated
                repository.saveProject(updated)
            } catch (e: Exception) {
                emitApiError(e)
            } finally {
                _isGeneratingMagic.value = false
            }
        }
    }

    // --- Audio Playback ---

    fun togglePlayPause() = audioPlayer.togglePlayPause()

    fun seekTo(positionMs: Long, autoPlay: Boolean = false) = audioPlayer.seekTo(positionMs, autoPlay)

    fun cyclePlaybackSpeed() = audioPlayer.cyclePlaybackSpeed()

    fun setPlaybackSpeed(speed: PlaybackSpeed) = audioPlayer.setPlaybackSpeed(speed)

    // --- Exports & Backup / Restore ---

    fun buildExportTxt(): String {
        val project = _activeProject.value ?: return ""
        return SrtEngine.exportPlainText(
            segments = project.activeSegments,
            removeFillerWords = project.removeFillerWords,
            stripPunctuation = project.stripPunctuation,
            casing = project.textCasing
        )
    }

    fun buildExportSrt(): String {
        val project = _activeProject.value ?: return ""
        return SrtEngine.exportSrt(
            segments = project.activeSegments,
            wordChunkMode = project.wordChunkMode,
            removeFillerWords = project.removeFillerWords,
            stripPunctuation = project.stripPunctuation,
            casing = project.textCasing,
            timestampShiftMs = project.timestampShiftMs
        )
    }

    fun saveTextDirectToDownloads(fileName: String, mimeType: String, content: String) {
        viewModelScope.launch {
            val res = LocalFileSaver.saveTextToDownloads(appContext, fileName, mimeType, content)
            if (res.isSuccess) {
                _toastEvents.tryEmit(ToastEvent.Info(res.getOrNull() ?: "Saved to Downloads"))
            } else {
                _toastEvents.tryEmit(ToastEvent.Error("Save failed: ${res.exceptionOrNull()?.message}"))
            }
        }
    }

    fun shareTextExport(fileName: String, mimeType: String, content: String, chooserTitle: String) {
        val res = LocalFileSaver.shareTextFile(appContext, fileName, mimeType, content, chooserTitle)
        if (res.isFailure) {
            _toastEvents.tryEmit(ToastEvent.Error("Unable to open Share Sheet"))
        }
    }

    fun saveActiveAudioDirectToDownloads(fileName: String) {
        val audioPath = _activeProject.value?.audioFilePath
        if (audioPath.isNullOrBlank()) {
            _toastEvents.tryEmit(ToastEvent.Error("No audio file attached to this project"))
            return
        }
        viewModelScope.launch {
            val file = File(audioPath)
            val mime = guessAudioMimeFromFile(file)
            val res = LocalFileSaver.saveFileToDownloads(appContext, fileName, mime, file)
            if (res.isSuccess) {
                _toastEvents.tryEmit(ToastEvent.Info(res.getOrNull() ?: "Audio saved to Downloads"))
            } else {
                _toastEvents.tryEmit(ToastEvent.Error("Audio save failed: ${res.exceptionOrNull()?.message}"))
            }
        }
    }

    fun shareActiveAudio() {
        val audioPath = _activeProject.value?.audioFilePath
        if (audioPath.isNullOrBlank()) {
            _toastEvents.tryEmit(ToastEvent.Error("No audio file attached to this project"))
            return
        }
        val file = File(audioPath)
        val res = LocalFileSaver.shareFiles(appContext, listOf(file), guessAudioMimeFromFile(file), "Save or Share Audio")
        if (res.isFailure) {
            _toastEvents.tryEmit(ToastEvent.Error("Unable to open Share Sheet"))
        }
    }

    fun exportBackupToUri(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val res = runCatching {
                val json = repository.exportBackupJson(settings.value.autoCorrectRules)
                appContext.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(json.toByteArray(Charsets.UTF_8))
                } ?: error("Unable to write backup file")
            }
            if (res.isSuccess) {
                _toastEvents.tryEmit(ToastEvent.Info("Backup exported (.json)"))
            } else {
                _toastEvents.tryEmit(ToastEvent.Error("Backup export failed"))
            }
        }
    }

    fun importBackupFromUri(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val res = runCatching {
                val raw = appContext.contentResolver.openInputStream(uri)?.bufferedReader()?.use {
                    it.readText()
                } ?: error("Unable to read backup file")
                val (count, rules) = repository.importBackupJson(raw)
                preferencesManager.mergeAutoCorrectRules(rules)
                Pair(count, rules.size)
            }
            if (res.isSuccess) {
                val (pCount, rCount) = res.getOrThrow()
                refreshStorageStats()
                _toastEvents.tryEmit(
                    ToastEvent.Info("Restored $pCount projects & $rCount dictionary rules")
                )
            } else {
                _toastEvents.tryEmit(
                    ToastEvent.Error("Import failed: ${res.exceptionOrNull()?.message ?: "Invalid JSON"}")
                )
            }
        }
    }

    // --- Native Media Compression ---

    fun syncCompressDefaultsFromSettings() {
        val s = settings.value
        _compressState.update { state ->
            if (state.isCompressing) state
            else state.copy(
                videoPreset = s.defaultVideoPreset,
                fixHdrColors = s.defaultFixHdr,
                audioPreset = s.defaultAudioPreset
            )
        }
    }

    fun setCompressMediaType(type: CompressMediaType) {
        if (_compressState.value.isCompressing) return
        _compressState.update {
            if (it.mediaType == type) it
            else it.copy(mediaType = type, selectedMedia = null, result = null, progress = 0f, statusText = "")
        }
    }

    fun selectCompressFile(uri: Uri) {
        if (_compressState.value.isCompressing) return
        viewModelScope.launch {
            val info = MediaCompressor.probeMedia(appContext, uri)
            _compressState.update {
                it.copy(
                    selectedMedia = info,
                    result = null,
                    progress = 0f,
                    statusText = ""
                )
            }
        }
    }

    fun setVideoCompressPreset(preset: VideoCompressPreset) {
        if (_compressState.value.isCompressing) return
        _compressState.update { it.copy(videoPreset = preset, result = null) }
    }

    fun setAudioCompressPreset(preset: AudioCompressPreset) {
        if (_compressState.value.isCompressing) return
        _compressState.update { it.copy(audioPreset = preset, result = null) }
    }

    fun setFixHdrColors(enabled: Boolean) {
        if (_compressState.value.isCompressing) return
        _compressState.update { it.copy(fixHdrColors = enabled, result = null) }
    }

    fun startCompression() {
        val current = _compressState.value
        val media = current.selectedMedia
        if (media == null) {
            _toastEvents.tryEmit(ToastEvent.Error("Select a file to compress first"))
            return
        }
        if (current.isCompressing) return

        compressJob?.cancel()
        compressJob = viewModelScope.launch {
            _compressState.update {
                it.copy(isCompressing = true, progress = 0.02f, statusText = "Starting...", result = null)
            }
            try {
                val res = MediaCompressor.compressMedia(
                    context = appContext,
                    mediaType = current.mediaType,
                    info = media,
                    videoPreset = current.videoPreset,
                    audioPreset = current.audioPreset,
                    fixHdrColors = current.fixHdrColors,
                    onProgress = { progress, status ->
                        _compressState.update {
                            it.copy(progress = progress.coerceIn(0f, 1f), statusText = status)
                        }
                    }
                )
                _compressState.update {
                    it.copy(isCompressing = false, progress = 1f, statusText = "Done", result = res)
                }
                _toastEvents.tryEmit(
                    ToastEvent.Info("Compressed to ${MediaCompressor.formatBytes(res.outputSizeBytes)}")
                )
            } catch (_: CancellationException) {
                _compressState.update {
                    it.copy(isCompressing = false, progress = 0f, statusText = "")
                }
                _toastEvents.tryEmit(ToastEvent.Info("Compression cancelled"))
            } catch (e: Exception) {
                _compressState.update {
                    it.copy(isCompressing = false, progress = 0f, statusText = "")
                }
                _toastEvents.tryEmit(ToastEvent.Error(e.message ?: "Compression failed"))
            }
        }
    }

    fun cancelCompression() {
        compressJob?.cancel()
        compressJob = null
    }

    fun saveCompressedResultToDownloads() {
        val res = _compressState.value.result ?: return
        viewModelScope.launch {
            val file = File(res.filePath)
            val saved = LocalFileSaver.saveFileToDownloads(appContext, res.fileName, res.mimeType, file)
            if (saved.isSuccess) {
                _toastEvents.tryEmit(ToastEvent.Info(saved.getOrNull() ?: "Saved to Downloads"))
            } else {
                _toastEvents.tryEmit(ToastEvent.Error("Save failed: ${saved.exceptionOrNull()?.message}"))
            }
        }
    }

    fun shareCompressedResult() {
        val res = _compressState.value.result ?: return
        val file = File(res.filePath)
        val shared = LocalFileSaver.shareFiles(appContext, listOf(file), res.mimeType, "Share Compressed File")
        if (shared.isFailure) {
            _toastEvents.tryEmit(ToastEvent.Error("Unable to open Share Sheet"))
        }
    }

    fun useCompressedForTranscription(onOpened: () -> Unit) {
        val res = _compressState.value.result ?: return
        val file = File(res.filePath)
        if (!file.exists()) {
            _toastEvents.tryEmit(ToastEvent.Error("Compressed file not found"))
            return
        }
        val uri = Uri.fromFile(file)
        if (res.mediaType == CompressMediaType.VIDEO) {
            importVideoOffline(uri)
        } else {
            importAudioOffline(uri)
        }
        onOpened()
    }

    fun showToast(message: String, isError: Boolean = false) {
        _toastEvents.tryEmit(if (isError) ToastEvent.Error(message) else ToastEvent.Info(message))
    }

    private fun persistQuietly(project: ProjectModel) {
        viewModelScope.launch {
            repository.saveProject(project)
        }
    }

    private fun emitApiError(e: Exception) {
        val msg = when (e) {
            is RateLimitException -> e.message ?: "Rate limit reached. Try again shortly."
            is ApiKeyInvalidException -> e.message ?: "Invalid API key. Check Settings."
            else -> e.message ?: "Processing failed. Please try again."
        }
        _toastEvents.tryEmit(ToastEvent.Error(msg))
    }

    override fun onCleared() {
        super.onCleared()
        compressJob?.cancel()
        audioPlayer.release()
        voiceRecorder.cancelRecording()
    }
}
