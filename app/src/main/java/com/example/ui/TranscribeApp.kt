package com.example.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.CREATOR_CONFIG
import com.example.data.model.AudioSaveBehavior
import com.example.data.model.CompressMediaType
import com.example.data.model.ThemeMode
import com.example.ui.components.HinglishConverterModal
import com.example.ui.components.MissingApiKeyDialog
import com.example.ui.components.ProcessingOverlay
import com.example.ui.components.SettingsModalFlow
import com.example.ui.components.SettingsSubPage
import com.example.ui.components.UpdateAvailableDialog
import com.example.ui.components.UsageModalDialog
import com.example.ui.components.VoiceRecorderDialog
import com.example.ui.screens.CompressScreen
import com.example.ui.screens.EditorScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.MagicScreen
import com.example.ui.theme.TranscribeTextStyles
import com.example.ui.theme.TranscribeTheme
import com.example.ui.theme.TranscribeThemeTokens
import com.example.viewmodel.ToastEvent
import com.example.viewmodel.TranscribeViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class ActiveScreen {
    HOME,
    EDITOR,
    MAGIC,
    COMPRESS
}

@Composable
fun TranscribeApp(
    viewModel: TranscribeViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val dailyUsage by viewModel.dailyUsage.collectAsStateWithLifecycle()
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    val activeProject by viewModel.activeProject.collectAsStateWithLifecycle()
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val recordingState by viewModel.recordingState.collectAsStateWithLifecycle()
    val storageStats by viewModel.storageStats.collectAsStateWithLifecycle()
    val processingState by viewModel.processingState.collectAsStateWithLifecycle()
    val isTranslatingEnglish by viewModel.isTranslatingEnglish.collectAsStateWithLifecycle()
    val isConvertingHinglish by viewModel.isConvertingHinglish.collectAsStateWithLifecycle()
    val isGeneratingMagic by viewModel.isGeneratingMagic.collectAsStateWithLifecycle()
    val pendingQueue by viewModel.pendingQueue.collectAsStateWithLifecycle()
    val isRetryingQueue by viewModel.isRetryingQueue.collectAsStateWithLifecycle()
    val compressState by viewModel.compressState.collectAsStateWithLifecycle()
    val splitState by viewModel.splitState.collectAsStateWithLifecycle()
    val showMissingApiKeyDialog by viewModel.showMissingApiKeyDialog.collectAsStateWithLifecycle()
    val remoteConfig by viewModel.remoteConfig.collectAsStateWithLifecycle()
    val updatePrompt by viewModel.updatePrompt.collectAsStateWithLifecycle()
    val isCheckingUpdate by viewModel.isCheckingUpdate.collectAsStateWithLifecycle()

    val systemDark = isSystemInDarkTheme()
    val effectiveDarkTheme = when (settings.themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }

    var showCompressScreen by remember { mutableStateOf(false) }
    var showMagicScreen by remember { mutableStateOf(false) }

    var showUsageModal by remember { mutableStateOf(false) }
    var showSettingsModal by remember { mutableStateOf(false) }
    var settingsInitialPage by remember { mutableStateOf(SettingsSubPage.ROOT) }
    var showHinglishModal by remember { mutableStateOf(false) }
    var hinglishImportedTitle by remember { mutableStateOf("") }
    var hinglishImportedContent by remember { mutableStateOf("") }

    var activeToast by remember { mutableStateOf<ToastEvent?>(null) }

    LaunchedEffect(activeProject) {
        if (activeProject == null) {
            showMagicScreen = false
        }
    }

    LaunchedEffect(Unit) {
        viewModel.toastEvents.collect { event ->
            activeToast = event
            delay(if (event.isError) 4200L else 2600L)
            if (activeToast == event) {
                activeToast = null
            }
        }
    }

    // Runtime Microphone Permission for Built-in Voice Recorder
    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.startVoiceRecording()
        } else {
            viewModel.showToast("Microphone permission is required to record voice", isError = true)
        }
    }

    fun launchVoiceRecorder() {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            viewModel.startVoiceRecording()
        } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // Zero-permission SAF Launchers
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importVideoOffline(uri)
        }
    }

    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importAudioOffline(uri)
        }
    }

    val compressMediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.selectCompressFile(uri)
        }
    }

    val splitVideoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.selectSplitVideo(uri)
        }
    }

    val textSrtPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val read = viewModel.readTextFromUri(uri)
                if (read != null) {
                    hinglishImportedTitle = read.first
                    hinglishImportedContent = read.second
                } else {
                    viewModel.showToast("Could not read selected file", isError = true)
                }
            }
        }
    }

    // JSON Backup & Restore Launchers
    val exportBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            viewModel.exportBackupToUri(uri)
        }
    }

    val importBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importBackupFromUri(uri)
        }
    }

    fun copyToClipboard(label: String, text: String) {
        if (text.isBlank()) {
            viewModel.showToast("Nothing to copy")
            return
        }
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
        viewModel.showToast(if (label == "Transcript") "Copied!" else "$label copied!")
    }

    fun openExternalUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        runCatching { context.startActivity(intent) }
            .onFailure { viewModel.showToast("Unable to open link", isError = true) }
    }

    fun shareAppText() {
        val shareCfg = remoteConfig.share
        val shareTitle = shareCfg.title.ifBlank { "Share App" }
        val sharePayload = listOf(shareCfg.message, shareCfg.url)
            .filter { it.isNotBlank() }
            .joinToString("\n")
            .ifBlank { "${CREATOR_CONFIG.appShareIntro}\n${CREATOR_CONFIG.appShareUrl}".trim() }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, shareTitle)
            putExtra(Intent.EXTRA_TITLE, shareTitle)
            putExtra(Intent.EXTRA_TEXT, sharePayload)
        }
        val chooser = Intent.createChooser(intent, shareTitle)
        runCatching { context.startActivity(chooser) }
            .onFailure { copyToClipboard("App link", sharePayload) }
    }

    fun safeFileName(title: String, ext: String): String {
        val cleaned = title.replace(Regex("""[^a-zA-Z0-9_\- ]"""), "").trim().ifBlank { "transcript" }
        return "${cleaned.replace(' ', '_')}.$ext"
    }

    val currentScreen = when {
        showCompressScreen -> ActiveScreen.COMPRESS
        activeProject != null && showMagicScreen -> ActiveScreen.MAGIC
        activeProject != null -> ActiveScreen.EDITOR
        else -> ActiveScreen.HOME
    }

    TranscribeTheme(darkTheme = effectiveDarkTheme) {
        Box(modifier = Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = currentScreen,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "screen_transition"
            ) { screen ->
                when (screen) {
                    ActiveScreen.HOME -> {
                        HomeScreen(
                            projects = projects,
                            dailyUsage = dailyUsage,
                            pendingQueue = pendingQueue,
                            isRetryingQueue = isRetryingQueue,
                            isDarkTheme = effectiveDarkTheme,
                            promo = remoteConfig.promo,
                            onToggleTheme = { viewModel.toggleLightDark(effectiveDarkTheme) },
                            onOpenUsageModal = { showUsageModal = true },
                            onOpenSettings = {
                                settingsInitialPage = SettingsSubPage.ROOT
                                showSettingsModal = true
                            },
                            onUploadVideo = {
                                videoPickerLauncher.launch(arrayOf("video/mp4", "video/quicktime", "video/webm", "video/*"))
                            },
                            onUploadAudio = {
                                audioPickerLauncher.launch(arrayOf("audio/mpeg", "audio/wav", "audio/mp4", "audio/x-m4a", "audio/*"))
                            },
                            onRecordVoice = { launchVoiceRecorder() },
                            onImportTextSrt = {
                                hinglishImportedTitle = ""
                                hinglishImportedContent = ""
                                showHinglishModal = true
                            },
                            onOpenCompress = {
                                viewModel.syncCompressDefaultsFromSettings()
                                showCompressScreen = true
                            },
                            onOpenPromoUrl = { url -> openExternalUrl(url) },
                            onRetryQueue = { viewModel.retryPendingQueue() },
                            onOpenProject = { viewModel.openProject(it) },
                            onRenameProject = { id, newName -> viewModel.renameProject(id, newName) },
                            onTogglePinProject = { id, pinned -> viewModel.toggleProjectPin(id, pinned) },
                            onChangeProjectExpiry = { id, opt -> viewModel.updateProjectExpiry(id, opt) },
                            onDeleteProject = { viewModel.deleteProject(it) }
                        )
                    }

                    ActiveScreen.COMPRESS -> {
                        CompressScreen(
                            state = compressState,
                            splitState = splitState,
                            onBack = { showCompressScreen = false },
                            onSelectMediaType = { viewModel.setCompressMediaType(it) },
                            onPickFile = {
                                val mimes = if (compressState.mediaType == CompressMediaType.VIDEO) {
                                    arrayOf("video/mp4", "video/quicktime", "video/webm", "video/*")
                                } else {
                                    arrayOf("audio/mpeg", "audio/wav", "audio/mp4", "audio/x-m4a", "audio/*")
                                }
                                compressMediaPickerLauncher.launch(mimes)
                            },
                            onSelectVideoPreset = { viewModel.setVideoCompressPreset(it) },
                            onSelectAudioPreset = { viewModel.setAudioCompressPreset(it) },
                            onToggleFixHdr = { viewModel.setFixHdrColors(it) },
                            onStartCompress = { viewModel.startCompression() },
                            onCancelCompress = { viewModel.cancelCompression() },
                            onSaveDownload = { viewModel.saveCompressedResultToDownloads() },
                            onShareResult = { viewModel.shareCompressedResult() },
                            onUseForTranscription = {
                                viewModel.useCompressedForTranscription {
                                    showCompressScreen = false
                                }
                            },
                            onPickSplitVideo = {
                                splitVideoPickerLauncher.launch(
                                    arrayOf("video/mp4", "video/quicktime", "video/webm", "video/*")
                                )
                            },
                            onSelectSplitDurationPreset = { viewModel.setSplitDurationPreset(it) },
                            onChangeSplitCustomDuration = { viewModel.setSplitCustomDuration(it) },
                            onSelectSplitCustomUnit = { viewModel.setSplitCustomUnit(it) },
                            onToggleSplitPartSelected = { viewModel.toggleSplitPartSelected(it) },
                            onToggleSelectAllSplitParts = { viewModel.toggleSelectAllSplitParts() },
                            onToggleSplitCompress = { viewModel.setSplitCompressEnabled(it) },
                            onSelectSplitVideoPreset = { viewModel.setSplitVideoPreset(it) },
                            onToggleSplitFixHdr = { viewModel.setSplitFixHdr(it) },
                            onStartSplitExport = { viewModel.startSplitExport() },
                            onSkipCurrentSplitPart = { viewModel.skipCurrentSplitPart() },
                            onSkipQueuedSplitPart = { viewModel.skipQueuedSplitPart(it) },
                            onCancelAllSplitExports = { viewModel.cancelAllSplitExports() },
                            onShareSplitPart = { viewModel.shareSavedSplitPart(it) },
                            onShareAllSavedSplitParts = { viewModel.shareAllSavedSplitParts() }
                        )
                    }

                    ActiveScreen.EDITOR -> {
                        val currentProject = activeProject ?: return@AnimatedContent
                        EditorScreen(
                            project = currentProject,
                            playback = playbackState,
                            providerSummary = settings.activeProviderSummary,
                            isTranslatingEnglish = isTranslatingEnglish,
                            isConvertingHinglish = isConvertingHinglish,
                            onBack = { viewModel.closeEditor() },
                            onUpdateTitle = { viewModel.updateTitle(it) },
                            onUpdateExpiry = { opt -> viewModel.updateProjectExpiry(currentProject.id, opt) },
                            onDownloadAudio = {
                                when (settings.audioSaveBehavior) {
                                    AudioSaveBehavior.DEFAULT_DOWNLOADS -> {
                                        viewModel.saveActiveAudioDirectToDownloads(
                                            safeFileName(currentProject.title, "m4a")
                                        )
                                    }
                                    AudioSaveBehavior.CHOOSE_OR_SHARE -> {
                                        viewModel.shareActiveAudio()
                                    }
                                }
                            },
                            onSave = { viewModel.saveActiveProject() },
                            onTranscribeNow = { viewModel.transcribeActiveProject() },
                            onTogglePlayPause = { viewModel.togglePlayPause() },
                            onCyclePlaybackSpeed = { viewModel.cyclePlaybackSpeed() },
                            onSeekTo = { pos, auto -> viewModel.seekTo(pos, auto) },
                            onSelectWordChunk = { viewModel.setWordChunkMode(it) },
                            onSelectLanguageMode = { viewModel.selectLanguageMode(it) },
                            onTranslateToEnglish = { viewModel.translateActiveToEnglish() },
                            onConvertHinglishOffline = { viewModel.convertActiveToHinglishOffline() },
                            onSmartSpellFixHinglish = { viewModel.smartSpellFixActiveHinglish() },
                            onUpdateSegmentText = { id, text -> viewModel.updateSegmentText(id, text) },
                            onSplitSegment = { id -> viewModel.splitSegment(id) },
                            onMergeSegmentWithNext = { id -> viewModel.mergeSegmentWithNext(id) },
                            onDeleteSegment = { id -> viewModel.deleteSegment(id) },
                            onReplaceAll = { find, rep -> viewModel.replaceAllText(find, rep) },
                            onToggleRemoveFillerWords = { viewModel.setRemoveFillerWords(it) },
                            onToggleStripPunctuation = { viewModel.setStripPunctuation(it) },
                            onSelectTextCasing = { viewModel.setTextCasing(it) },
                            onAdjustTimestampShift = { viewModel.adjustTimestampShift(it) },
                            onResetTimestampShift = { viewModel.resetTimestampShift() },
                            onOpenMagicScreen = { showMagicScreen = true },
                            onCopyText = { label, text -> copyToClipboard(label, text) },
                            onExportTxt = {
                                val name = safeFileName(currentProject.title, "txt")
                                val content = viewModel.buildExportTxt()
                                when (settings.audioSaveBehavior) {
                                    AudioSaveBehavior.DEFAULT_DOWNLOADS ->
                                        viewModel.saveTextDirectToDownloads(name, "text/plain", content)
                                    AudioSaveBehavior.CHOOSE_OR_SHARE ->
                                        viewModel.shareTextExport(name, "text/plain", content, "Export .TXT")
                                }
                            },
                            onExportSrt = {
                                val name = safeFileName(currentProject.title, "srt")
                                val content = viewModel.buildExportSrt()
                                when (settings.audioSaveBehavior) {
                                    AudioSaveBehavior.DEFAULT_DOWNLOADS ->
                                        viewModel.saveTextDirectToDownloads(name, "application/x-subrip", content)
                                    AudioSaveBehavior.CHOOSE_OR_SHARE ->
                                        viewModel.shareTextExport(name, "application/x-subrip", content, "Export .SRT")
                                }
                            },
                            onShareSrt = {
                                val name = safeFileName(currentProject.title, "srt")
                                val content = viewModel.buildExportSrt()
                                viewModel.shareTextExport(name, "application/x-subrip", content, "Share .SRT Subtitles")
                            }
                        )
                    }

                    ActiveScreen.MAGIC -> {
                        val currentProject = activeProject ?: return@AnimatedContent
                        MagicScreen(
                            projectTitle = currentProject.title,
                            magicContent = currentProject.magicContent,
                            isGenerating = isGeneratingMagic,
                            onBack = { showMagicScreen = false },
                            onGenerate = { force -> viewModel.generateMagicAssets(force) },
                            onCopyItem = { label, text -> copyToClipboard(label, text) }
                        )
                    }
                }
            }

            // Built-in Minimalist Voice Recorder Dialog
            if (recordingState.isRecording) {
                VoiceRecorderDialog(
                    state = recordingState,
                    onCancel = { viewModel.cancelVoiceRecording() },
                    onStopAndSave = { viewModel.stopAndSaveVoiceRecording() }
                )
            }

            // Scroll-safe Hinglish / SRT / TXT Importer Dialog
            if (showHinglishModal) {
                HinglishConverterModal(
                    importedTitle = hinglishImportedTitle,
                    importedContent = hinglishImportedContent,
                    onDismiss = { showHinglishModal = false },
                    onPickFile = {
                        textSrtPickerLauncher.launch(arrayOf("text/*", "application/x-subrip", "*/*"))
                    },
                    onOpenInEditor = { rawText, title ->
                        showHinglishModal = false
                        viewModel.openTextOrSrtInEditorOffline(rawText, title)
                    }
                )
            }

            // Daily Usage Modal
            if (showUsageModal) {
                UsageModalDialog(
                    usage = dailyUsage,
                    providerSummary = settings.activeProviderSummary,
                    onDismiss = { showUsageModal = false },
                    onReset = { viewModel.resetDailyUsage() },
                    onOpenAiStudioQuota = { openExternalUrl("https://aistudio.google.com/") }
                )
            }

            // Native-Style Settings Modal with Sub-Screens
            if (showSettingsModal) {
                SettingsModalFlow(
                    settings = settings,
                    storageStats = storageStats,
                    remoteConfig = remoteConfig,
                    isCheckingUpdate = isCheckingUpdate,
                    initialPage = settingsInitialPage,
                    onDismiss = { showSettingsModal = false },
                    onRefreshStorage = { viewModel.refreshStorageStats() },
                    onSelectThemeMode = { viewModel.setThemeMode(it) },
                    onSelectProvider = { viewModel.selectProvider(it) },
                    onSelectGeminiModel = { viewModel.selectGeminiModel(it) },
                    onSaveGeminiCustomModel = { viewModel.setGeminiCustomModelId(it) },
                    onSaveOpenRouterModel = { viewModel.setOpenRouterModelId(it) },
                    onSaveApiKey = { provider, key -> viewModel.saveApiKey(provider, key) },
                    onRemoveApiKey = { provider -> viewModel.removeApiKey(provider) },
                    onSelectDefaultRetention = { viewModel.setDefaultRetention(it) },
                    onAddDictionaryRule = { wrong, correct -> viewModel.addAutoCorrectRule(wrong, correct) },
                    onRemoveDictionaryRule = { id -> viewModel.removeAutoCorrectRule(id) },
                    onSelectAudioSaveBehavior = { viewModel.setAudioSaveBehavior(it) },
                    onSelectDefaultVideoPreset = { viewModel.setDefaultVideoPreset(it) },
                    onToggleDefaultFixHdr = { viewModel.setDefaultFixHdr(it) },
                    onSelectDefaultAudioPreset = { viewModel.setDefaultAudioPreset(it) },
                    onShareAudioFiles = { ids -> viewModel.shareAudioFiles(ids) },
                    onDeleteAudioFiles = { ids -> viewModel.deleteAudioFiles(ids) },
                    onExportBackup = { exportBackupLauncher.launch("ordieo_backup.json") },
                    onImportBackup = { importBackupLauncher.launch(arrayOf("application/json", "text/*", "*/*")) },
                    onClearAllStorage = { viewModel.clearAllStorage() },
                    onOpenExternalUrl = { url -> openExternalUrl(url) },
                    onShareApp = { shareAppText() },
                    onCopyUpiId = { upi -> copyToClipboard("UPI ID", upi) },
                    onCheckUpdate = { viewModel.checkForUpdatesManually() }
                )
            }

            // Missing API Key Prompt -> jumps directly to AI Models sub-screen
            if (showMissingApiKeyDialog) {
                MissingApiKeyDialog(
                    provider = settings.provider,
                    onDismiss = { viewModel.dismissMissingApiKeyDialog() },
                    onOpenSettings = {
                        settingsInitialPage = SettingsSubPage.PROVIDER_AND_KEY
                        showSettingsModal = true
                    }
                )
            }

            // Remote Config Update Popup (optional once-per-version or forced if below minVersion)
            if (updatePrompt.visible) {
                UpdateAvailableDialog(
                    state = updatePrompt,
                    onDismissLater = { viewModel.dismissUpdatePrompt() },
                    onUpdateNow = { url ->
                        if (url.isNotBlank()) {
                            openExternalUrl(url)
                        }
                        if (!updatePrompt.forced) {
                            viewModel.dismissUpdatePrompt()
                        }
                    }
                )
            }

            // Step-by-step Processing Overlay
            ProcessingOverlay(state = processingState)

            // Crisp Floating Toast Notification Banner
            AnimatedVisibility(
                visible = activeToast != null,
                enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 12.dp, start = 18.dp, end = 18.dp)
            ) {
                activeToast?.let { toast ->
                    FloatingToastBanner(toast = toast)
                }
            }
        }
    }
}

@Composable
private fun FloatingToastBanner(toast: ToastEvent) {
    val colors = TranscribeThemeTokens.colors
    val tint = if (toast.isError) colors.danger else colors.good

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(99.dp))
            .background(colors.card)
            .border(1.dp, tint, RoundedCornerShape(99.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (toast.isError) Icons.Outlined.ErrorOutline else Icons.Outlined.CheckCircle,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = toast.message,
            style = TranscribeTextStyles.ListRow,
            color = colors.text
        )
    }
}
