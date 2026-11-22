package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Spellcheck
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.AppConfig
import com.example.AppLogo
import com.example.CREATOR_CONFIG
import com.example.R
import com.example.data.model.AiProvider
import com.example.data.model.AppSettings
import com.example.data.model.AudioCompressPreset
import com.example.data.model.AudioSaveBehavior
import com.example.data.model.GeminiModelOption
import com.example.data.model.RetentionOption
import com.example.data.model.StorageStats
import com.example.data.model.ThemeMode
import com.example.data.model.VideoCompressPreset
import com.example.data.remote.RemoteConfig
import com.example.ui.theme.TranscribeTextStyles
import com.example.ui.theme.TranscribeThemeTokens
import com.example.ui.theme.withTabularNums
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class SettingsSubPage(val title: String) {
    ROOT("Settings"),
    PROVIDER_AND_KEY("AI Models"),
    APPEARANCE("Appearance"),
    COMPRESSION("Compression"),
    AUTO_DELETE("Auto-delete"),
    DICTIONARY("Custom Auto-Correct"),
    STORAGE("Storage"),
    ABOUT("About")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsModalFlow(
    settings: AppSettings,
    storageStats: StorageStats,
    remoteConfig: RemoteConfig,
    isCheckingUpdate: Boolean,
    initialPage: SettingsSubPage = SettingsSubPage.ROOT,
    onDismiss: () -> Unit,
    onRefreshStorage: () -> Unit,
    onSelectThemeMode: (ThemeMode) -> Unit,
    onSelectProvider: (AiProvider) -> Unit,
    onSelectGeminiModel: (GeminiModelOption) -> Unit,
    onSaveGeminiCustomModel: (String) -> Unit,
    onSaveOpenRouterModel: (String) -> Unit,
    onSaveApiKey: (AiProvider, String) -> Unit,
    onRemoveApiKey: (AiProvider) -> Unit,
    onSelectDefaultRetention: (RetentionOption) -> Unit,
    onAddDictionaryRule: (String, String) -> Unit,
    onRemoveDictionaryRule: (String) -> Unit,
    onSelectAudioSaveBehavior: (AudioSaveBehavior) -> Unit,
    onSelectDefaultVideoPreset: (VideoCompressPreset) -> Unit,
    onToggleDefaultFixHdr: (Boolean) -> Unit,
    onSelectDefaultAudioPreset: (AudioCompressPreset) -> Unit,
    onShareAudioFiles: (Set<String>) -> Unit,
    onDeleteAudioFiles: (Set<String>) -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit,
    onClearAllStorage: () -> Unit,
    onOpenExternalUrl: (String) -> Unit,
    onShareApp: () -> Unit,
    onCopyUpiId: (String) -> Unit,
    onCheckUpdate: () -> Unit
) {
    val colors = TranscribeThemeTokens.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var currentPage by remember { mutableStateOf(initialPage) }

    LaunchedEffect(Unit) {
        onRefreshStorage()
    }

    LaunchedEffect(currentPage) {
        if (currentPage == SettingsSubPage.STORAGE) {
            onRefreshStorage()
        }
    }

    BackHandler(enabled = currentPage != SettingsSubPage.ROOT) {
        currentPage = SettingsSubPage.ROOT
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = colors.card,
        contentColor = colors.text,
        scrimColor = colors.overlay,
        dragHandle = { SheetDragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(top = 10.dp)
                .testTag("settings_modal")
        ) {
            // Header bar with Back arrow on sub-screens
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (currentPage != SettingsSubPage.ROOT) {
                        IconButton(
                            onClick = { currentPage = SettingsSubPage.ROOT },
                            modifier = Modifier
                                .size(44.dp)
                                .testTag("settings_sub_back_btn")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = stringResource(R.string.cd_back),
                                tint = colors.text
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(
                        text = currentPage.title,
                        style = TranscribeTextStyles.ScreenTitle,
                        color = colors.text
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("close_settings_btn")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.cd_close),
                        tint = colors.muted
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            AnimatedContent(
                targetState = currentPage,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "settings_subpage"
            ) { page ->
                when (page) {
                    SettingsSubPage.ROOT -> SettingsRootMenu(
                        settings = settings,
                        storageStats = storageStats,
                        onOpenPage = { currentPage = it }
                    )
                    SettingsSubPage.PROVIDER_AND_KEY -> ProviderAndApiKeySubScreen(
                        settings = settings,
                        onSelectProvider = onSelectProvider,
                        onSelectGeminiModel = onSelectGeminiModel,
                        onSaveGeminiCustomModel = onSaveGeminiCustomModel,
                        onSaveOpenRouterModel = onSaveOpenRouterModel,
                        onSaveApiKey = onSaveApiKey,
                        onRemoveApiKey = onRemoveApiKey
                    )
                    SettingsSubPage.APPEARANCE -> AppearanceSubScreen(
                        currentTheme = settings.themeMode,
                        onSelectThemeMode = onSelectThemeMode
                    )
                    SettingsSubPage.COMPRESSION -> CompressionSubScreen(
                        videoPreset = settings.defaultVideoPreset,
                        fixHdrColors = settings.defaultFixHdr,
                        audioPreset = settings.defaultAudioPreset,
                        onSelectVideoPreset = onSelectDefaultVideoPreset,
                        onToggleFixHdr = onToggleDefaultFixHdr,
                        onSelectAudioPreset = onSelectDefaultAudioPreset
                    )
                    SettingsSubPage.AUTO_DELETE -> AutoDeleteSubScreen(
                        currentRetention = settings.defaultRetention,
                        onSelectRetention = onSelectDefaultRetention
                    )
                    SettingsSubPage.DICTIONARY -> DictionarySubScreen(
                        settings = settings,
                        onAddRule = onAddDictionaryRule,
                        onRemoveRule = onRemoveDictionaryRule
                    )
                    SettingsSubPage.STORAGE -> StorageManagerSubScreen(
                        storageStats = storageStats,
                        saveBehavior = settings.audioSaveBehavior,
                        onSelectSaveBehavior = onSelectAudioSaveBehavior,
                        onShareAudioFiles = onShareAudioFiles,
                        onDeleteAudioFiles = onDeleteAudioFiles,
                        onExportBackup = onExportBackup,
                        onImportBackup = onImportBackup,
                        onClearAllStorage = onClearAllStorage
                    )
                    SettingsSubPage.ABOUT -> AboutSubScreen(
                        remoteConfig = remoteConfig,
                        isCheckingUpdate = isCheckingUpdate,
                        onOpenExternalUrl = onOpenExternalUrl,
                        onShareApp = onShareApp,
                        onCopyUpiId = onCopyUpiId,
                        onCheckUpdate = onCheckUpdate
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsRootMenu(
    settings: AppSettings,
    storageStats: StorageStats,
    onOpenPage: (SettingsSubPage) -> Unit
) {
    val colors = TranscribeThemeTokens.colors
    val keyStatus = if (settings.activeApiKey.isNotBlank()) "Key saved" else "No key"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        NativeSettingsRow(
            icon = Icons.Outlined.Key,
            title = "AI Models",
            subtitle = "${settings.activeProviderSummary} · $keyStatus",
            trailingValue = "",
            tag = "settings_row_provider",
            onClick = { onOpenPage(SettingsSubPage.PROVIDER_AND_KEY) }
        )

        NativeSettingsRow(
            icon = Icons.Outlined.Palette,
            title = "Appearance",
            subtitle = "Theme mode across dark and light surfaces",
            trailingValue = settings.themeMode.label,
            tag = "settings_row_appearance",
            onClick = { onOpenPage(SettingsSubPage.APPEARANCE) }
        )

        NativeSettingsRow(
            icon = Icons.Outlined.Compress,
            title = "Compression",
            subtitle = "${settings.defaultVideoPreset.title} · HDR ${if (settings.defaultFixHdr) "Fix ON" else "OFF"}",
            trailingValue = "",
            tag = "settings_row_compression",
            onClick = { onOpenPage(SettingsSubPage.COMPRESSION) }
        )

        NativeSettingsRow(
            icon = Icons.Outlined.Schedule,
            title = "Auto-delete",
            subtitle = "Automatic local cleanup for new projects",
            trailingValue = settings.defaultRetention.label,
            tag = "settings_row_auto_delete",
            onClick = { onOpenPage(SettingsSubPage.AUTO_DELETE) }
        )

        NativeSettingsRow(
            icon = Icons.Outlined.Spellcheck,
            title = "Custom Auto-Correct Dictionary",
            subtitle = "Auto-fix recurring names & terms",
            trailingValue = "${settings.autoCorrectRules.size}",
            tag = "settings_row_dictionary",
            onClick = { onOpenPage(SettingsSubPage.DICTIONARY) }
        )

        NativeSettingsRow(
            icon = Icons.Outlined.Storage,
            title = "Storage",
            subtitle = "Audio files, download behavior & local backup",
            trailingValue = formatBytesReadable(storageStats.totalBytes),
            tag = "settings_row_storage",
            onClick = { onOpenPage(SettingsSubPage.STORAGE) }
        )

        NativeSettingsRow(
            icon = Icons.Outlined.Info,
            title = "About",
            subtitle = "Creator info, share & support",
            trailingValue = AppConfig.APP_VERSION,
            tag = "settings_row_about",
            onClick = { onOpenPage(SettingsSubPage.ABOUT) }
        )

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(colors.goodBg)
                .border(1.dp, colors.line, RoundedCornerShape(18.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Lock,
                contentDescription = null,
                tint = colors.good,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = "100% local and private. API keys and media stay on your device.",
                style = TranscribeTextStyles.SubText,
                color = colors.good
            )
        }
    }
}

@Composable
private fun AppearanceSubScreen(
    currentTheme: ThemeMode,
    onSelectThemeMode: (ThemeMode) -> Unit
) {
    val colors = TranscribeThemeTokens.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "Choose how ${AppConfig.APP_NAME} looks. Switching applies immediately across all screens.",
            style = TranscribeTextStyles.SubText,
            color = colors.muted
        )
        Spacer(modifier = Modifier.height(2.dp))

        ThemeMode.entries.forEach { mode ->
            val selected = mode == currentTheme
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(if (selected) colors.soft else colors.card2)
                    .border(
                        width = 1.dp,
                        color = if (selected) colors.accent else colors.line,
                        shape = RoundedCornerShape(22.dp)
                    )
                    .clickable { onSelectThemeMode(mode) }
                    .padding(horizontal = 16.dp, vertical = 14.dp)
                    .testTag("theme_mode_${mode.name.lowercase()}"),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = mode.label,
                        style = TranscribeTextStyles.CardTitle,
                        color = if (selected) colors.softText else colors.text
                    )
                    Text(
                        text = when (mode) {
                            ThemeMode.SYSTEM -> "Follow Android system theme"
                            ThemeMode.LIGHT -> "Soft cool light surface"
                            ThemeMode.DARK -> "Deep indigo charcoal surface"
                        },
                        style = TranscribeTextStyles.SubText,
                        color = colors.muted
                    )
                }
                if (selected) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CompressionSubScreen(
    videoPreset: VideoCompressPreset,
    fixHdrColors: Boolean,
    audioPreset: AudioCompressPreset,
    onSelectVideoPreset: (VideoCompressPreset) -> Unit,
    onToggleFixHdr: (Boolean) -> Unit,
    onSelectAudioPreset: (AudioCompressPreset) -> Unit
) {
    val colors = TranscribeThemeTokens.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SectionLabel(text = "DEFAULT VIDEO PRESET")
        VideoCompressPreset.entries.forEach { preset ->
            val selected = preset == videoPreset
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (selected) colors.soft else colors.card2)
                    .border(1.dp, if (selected) colors.accent else colors.line, RoundedCornerShape(20.dp))
                    .clickable { onSelectVideoPreset(preset) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = preset.title,
                        style = TranscribeTextStyles.CardTitle,
                        color = if (selected) colors.softText else colors.text
                    )
                    Text(
                        text = preset.subtitle,
                        style = TranscribeTextStyles.SubText,
                        color = colors.muted
                    )
                }
                if (selected) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(colors.card2)
                .border(1.dp, colors.line, RoundedCornerShape(20.dp))
                .clickable { onToggleFixHdr(!fixHdrColors) }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(
                    text = "Fix HDR colours by default",
                    style = TranscribeTextStyles.CardTitle,
                    color = colors.text
                )
                Text(
                    text = "Re-encode HDR videos to SDR BT.709",
                    style = TranscribeTextStyles.SubText,
                    color = colors.muted
                )
            }
            AppToggleSwitch(checked = fixHdrColors, onCheckedChange = onToggleFixHdr)
        }

        Spacer(modifier = Modifier.height(4.dp))

        SectionLabel(text = "DEFAULT AUDIO PRESET")
        AudioCompressPreset.entries.forEach { preset ->
            val selected = preset == audioPreset
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (selected) colors.soft else colors.card2)
                    .border(1.dp, if (selected) colors.accent else colors.line, RoundedCornerShape(20.dp))
                    .clickable { onSelectAudioPreset(preset) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = preset.title,
                        style = TranscribeTextStyles.CardTitle,
                        color = if (selected) colors.softText else colors.text
                    )
                    Text(
                        text = preset.subtitle,
                        style = TranscribeTextStyles.SubText,
                        color = colors.muted
                    )
                }
                if (selected) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun NativeSettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    trailingValue: String,
    tag: String,
    onClick: () -> Unit
) {
    val colors = TranscribeThemeTokens.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(colors.card2)
            .border(1.dp, colors.line, RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(colors.soft),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.accentText,
                modifier = Modifier.size(20.dp)
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = TranscribeTextStyles.CardTitle,
                color = colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = TranscribeTextStyles.SubText,
                color = colors.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (trailingValue.isNotBlank()) {
            Text(
                text = trailingValue,
                style = TranscribeTextStyles.SubText.withTabularNums(),
                color = colors.muted
            )
        }

        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = colors.muted,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun ProviderAndApiKeySubScreen(
    settings: AppSettings,
    onSelectProvider: (AiProvider) -> Unit,
    onSelectGeminiModel: (GeminiModelOption) -> Unit,
    onSaveGeminiCustomModel: (String) -> Unit,
    onSaveOpenRouterModel: (String) -> Unit,
    onSaveApiKey: (AiProvider, String) -> Unit,
    onRemoveApiKey: (AiProvider) -> Unit
) {
    val colors = TranscribeThemeTokens.colors
    var providerMenuOpen by remember { mutableStateOf(false) }
    var geminiModelMenuOpen by remember { mutableStateOf(false) }

    val hasSavedKey = settings.activeApiKey.isNotBlank()
    var isReplacingKey by remember(settings.provider, hasSavedKey) { mutableStateOf(!hasSavedKey) }
    var keyDraft by remember(settings.provider, isReplacingKey) { mutableStateOf("") }

    var customGeminiDraft by remember(settings.geminiCustomModelId) {
        mutableStateOf(settings.geminiCustomModelId)
    }
    var openRouterModelDraft by remember(settings.openRouterModelId) {
        mutableStateOf(settings.openRouterModelId)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Provider Selector
        SettingsPanel(sectionTitle = "AI PROVIDER") {
            Box {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(colors.card)
                        .border(1.dp, colors.line, RoundedCornerShape(14.dp))
                        .clickable { providerMenuOpen = true }
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                        .testTag("provider_dropdown"),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = settings.provider.displayName,
                            style = TranscribeTextStyles.CardTitle,
                            color = colors.text
                        )
                        Text(
                            text = settings.provider.subtitle,
                            style = TranscribeTextStyles.SubText,
                            color = colors.muted
                        )
                    }
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = "Select AI provider",
                        tint = colors.muted
                    )
                }

                DropdownMenu(
                    expanded = providerMenuOpen,
                    onDismissRequest = { providerMenuOpen = false },
                    containerColor = colors.card
                ) {
                    AiProvider.entries.forEach { provider ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(
                                        text = provider.displayName,
                                        style = TranscribeTextStyles.ListRow,
                                        color = colors.text
                                    )
                                    Text(
                                        text = provider.subtitle,
                                        style = TranscribeTextStyles.SubText,
                                        color = colors.muted
                                    )
                                }
                            },
                            trailingIcon = {
                                if (provider == settings.provider) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = null,
                                        tint = colors.accent
                                    )
                                }
                            },
                            onClick = {
                                providerMenuOpen = false
                                onSelectProvider(provider)
                            }
                        )
                    }
                }
            }

            // Provider-specific Model Configuration
            if (settings.provider == AiProvider.GEMINI) {
                Spacer(modifier = Modifier.height(14.dp))
                SectionLabel(text = "GEMINI MODEL")
                Spacer(modifier = Modifier.height(8.dp))

                Box {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(colors.card)
                            .border(1.dp, colors.line, RoundedCornerShape(14.dp))
                            .clickable { geminiModelMenuOpen = true }
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                            .testTag("gemini_model_dropdown"),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = settings.geminiModelOption.displayLabel,
                                style = TranscribeTextStyles.CardTitle,
                                color = colors.text
                            )
                            Text(
                                text = settings.geminiModelOption.description,
                                style = TranscribeTextStyles.SubText,
                                color = colors.muted
                            )
                        }
                        Icon(
                            imageVector = Icons.Rounded.KeyboardArrowDown,
                            contentDescription = "Select Gemini model",
                            tint = colors.muted
                        )
                    }

                    DropdownMenu(
                        expanded = geminiModelMenuOpen,
                        onDismissRequest = { geminiModelMenuOpen = false },
                        containerColor = colors.card
                    ) {
                        GeminiModelOption.entries.forEach { option ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(
                                            text = option.displayLabel,
                                            style = TranscribeTextStyles.ListRow,
                                            color = colors.text
                                        )
                                        Text(
                                            text = option.description,
                                            style = TranscribeTextStyles.SubText,
                                            color = colors.muted
                                        )
                                    }
                                },
                                trailingIcon = {
                                    if (option == settings.geminiModelOption) {
                                        Icon(
                                            imageVector = Icons.Rounded.Check,
                                            contentDescription = null,
                                            tint = colors.accent
                                        )
                                    }
                                },
                                onClick = {
                                    geminiModelMenuOpen = false
                                    onSelectGeminiModel(option)
                                }
                            )
                        }
                    }
                }

                if (settings.geminiModelOption == GeminiModelOption.CUSTOM) {
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = customGeminiDraft,
                        onValueChange = {
                            customGeminiDraft = it
                            onSaveGeminiCustomModel(it)
                        },
                        placeholder = {
                            Text(
                                text = "e.g. gemini-3.8-flash-preview",
                                style = TranscribeTextStyles.SubText,
                                color = colors.muted
                            )
                        },
                        singleLine = true,
                        textStyle = TranscribeTextStyles.ListRow.copy(color = colors.text),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.accent,
                            unfocusedBorderColor = colors.line,
                            focusedContainerColor = colors.card,
                            unfocusedContainerColor = colors.card,
                            focusedTextColor = colors.text,
                            unfocusedTextColor = colors.text
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 44.dp)
                            .testTag("gemini_custom_model_input")
                    )
                }
            } else if (settings.provider == AiProvider.OPENROUTER) {
                Spacer(modifier = Modifier.height(14.dp))
                SectionLabel(text = "OPENROUTER MODEL ID")
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = openRouterModelDraft,
                    onValueChange = {
                        openRouterModelDraft = it
                        onSaveOpenRouterModel(it)
                    },
                    placeholder = {
                        Text(
                            text = "meta-llama/llama-3.3-70b-instruct:free",
                            style = TranscribeTextStyles.SubText,
                            color = colors.muted
                        )
                    },
                    singleLine = true,
                    textStyle = TranscribeTextStyles.ListRow.copy(color = colors.text),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.accent,
                        unfocusedBorderColor = colors.line,
                        focusedContainerColor = colors.card,
                        unfocusedContainerColor = colors.card,
                        focusedTextColor = colors.text,
                        unfocusedTextColor = colors.text
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp)
                        .testTag("openrouter_model_input")
                )
            }
        }

        // Write-Only API Key Security Panel (Strictly NO Eye/Reveal Icon!)
        SettingsPanel(sectionTitle = "WRITE-ONLY API KEY") {
            if (hasSavedKey && !isReplacingKey) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(colors.goodBg)
                        .border(1.dp, colors.good, RoundedCornerShape(14.dp))
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                        .testTag("saved_api_key_locked"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Lock,
                        contentDescription = null,
                        tint = colors.good,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "•••••••••••••••• (Saved)",
                        style = TranscribeTextStyles.ListRow,
                        color = colors.good,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SecondaryActionButton(
                        onClick = { isReplacingKey = true },
                        borderColor = colors.accent,
                        contentColor = colors.accentText,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("replace_api_key_btn")
                    ) {
                        Text("Replace", color = colors.accentText, style = TranscribeTextStyles.ButtonLabel)
                    }
                    SecondaryActionButton(
                        onClick = { onRemoveApiKey(settings.provider) },
                        borderColor = colors.danger,
                        contentColor = colors.danger,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("remove_api_key_btn")
                    ) {
                        Text("Remove", color = colors.danger, style = TranscribeTextStyles.ButtonLabel)
                    }
                }
            } else {
                Text(
                    text = "Once saved, your key is write-only and cannot be revealed or copied from Settings.",
                    style = TranscribeTextStyles.SubText,
                    color = colors.muted
                )
                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = keyDraft,
                    onValueChange = { keyDraft = it },
                    placeholder = {
                        Text(
                            text = when (settings.provider) {
                                AiProvider.GEMINI -> "Paste Google Gemini key (AIza...)"
                                AiProvider.GROQ -> "Paste Groq API key (gsk_...)"
                                AiProvider.OPENROUTER -> "Paste OpenRouter key (sk-or-...)"
                                AiProvider.OPENAI -> "Paste OpenAI API key (sk-...)"
                            },
                            style = TranscribeTextStyles.SubText,
                            color = colors.muted
                        )
                    },
                    singleLine = true,
                    textStyle = TranscribeTextStyles.ListRow.copy(color = colors.text),
                    visualTransformation = PasswordVisualTransformation(),
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Key,
                            contentDescription = null,
                            tint = colors.muted,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.accent,
                        unfocusedBorderColor = colors.line,
                        focusedContainerColor = colors.card,
                        unfocusedContainerColor = colors.card,
                        focusedTextColor = colors.text,
                        unfocusedTextColor = colors.text
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp)
                        .testTag("api_key_input")
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (hasSavedKey) {
                        SecondaryActionButton(
                            onClick = {
                                keyDraft = ""
                                isReplacingKey = false
                            },
                            modifier = Modifier.weight(0.8f)
                        ) {
                            Text("Cancel", style = TranscribeTextStyles.ButtonLabel, color = colors.muted)
                        }
                    }
                    PrimaryActionButton(
                        onClick = {
                            if (keyDraft.isNotBlank()) {
                                onSaveApiKey(settings.provider, keyDraft)
                                keyDraft = ""
                                isReplacingKey = false
                            }
                        },
                        enabled = keyDraft.isNotBlank(),
                        modifier = Modifier
                            .weight(1.2f)
                            .testTag("save_api_key_btn")
                    ) {
                        Text(
                            text = "Lock & Save Key",
                            style = TranscribeTextStyles.ButtonLabel
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AutoDeleteSubScreen(
    currentRetention: RetentionOption,
    onSelectRetention: (RetentionOption) -> Unit
) {
    val colors = TranscribeThemeTokens.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "Expired projects and their extracted audio files are permanently deleted automatically every time ${AppConfig.APP_NAME} opens.",
            style = TranscribeTextStyles.SubText,
            color = colors.muted
        )
        Spacer(modifier = Modifier.height(2.dp))

        RetentionOption.entries.forEach { option ->
            val selected = option == currentRetention
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(if (selected) colors.soft else colors.card2)
                    .border(
                        width = 1.dp,
                        color = if (selected) colors.accent else colors.line,
                        shape = RoundedCornerShape(22.dp)
                    )
                    .clickable { onSelectRetention(option) }
                    .padding(horizontal = 16.dp, vertical = 14.dp)
                    .testTag("default_retention_${option.name.lowercase()}"),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = option.label,
                    style = TranscribeTextStyles.CardTitle,
                    color = if (selected) colors.softText else colors.text
                )
                if (selected) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DictionarySubScreen(
    settings: AppSettings,
    onAddRule: (String, String) -> Unit,
    onRemoveRule: (String) -> Unit
) {
    val colors = TranscribeThemeTokens.colors
    var wrongDraft by remember { mutableStateOf("") }
    var correctDraft by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "Add recurring words, brand names, or Hinglish spellings. Rules apply automatically to all generated transcripts.",
            style = TranscribeTextStyles.SubText,
            color = colors.muted
        )

        SettingsPanel(sectionTitle = "ADD REPLACEMENT PAIR") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = wrongDraft,
                    onValueChange = { wrongDraft = it },
                    placeholder = { Text("Wrong word", style = TranscribeTextStyles.SubText, color = colors.muted) },
                    singleLine = true,
                    textStyle = TranscribeTextStyles.ListRow.copy(color = colors.text),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.accent,
                        unfocusedBorderColor = colors.line,
                        focusedContainerColor = colors.card,
                        unfocusedContainerColor = colors.card,
                        focusedTextColor = colors.text,
                        unfocusedTextColor = colors.text
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 44.dp)
                        .testTag("dict_wrong_input")
                )
                OutlinedTextField(
                    value = correctDraft,
                    onValueChange = { correctDraft = it },
                    placeholder = { Text("Correct word", style = TranscribeTextStyles.SubText, color = colors.muted) },
                    singleLine = true,
                    textStyle = TranscribeTextStyles.ListRow.copy(color = colors.text),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.accent,
                        unfocusedBorderColor = colors.line,
                        focusedContainerColor = colors.card,
                        unfocusedContainerColor = colors.card,
                        focusedTextColor = colors.text,
                        unfocusedTextColor = colors.text
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 44.dp)
                        .testTag("dict_correct_input")
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            PrimaryActionButton(
                onClick = {
                    if (wrongDraft.isNotBlank() && correctDraft.isNotBlank()) {
                        onAddRule(wrongDraft, correctDraft)
                        wrongDraft = ""
                        correctDraft = ""
                    }
                },
                enabled = wrongDraft.isNotBlank() && correctDraft.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("dict_add_btn")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = null,
                    tint = colors.onAccent,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Add Auto-Correct Pair", style = TranscribeTextStyles.ButtonLabel)
            }
        }

        if (settings.autoCorrectRules.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(colors.card2)
                    .border(1.dp, colors.line, RoundedCornerShape(22.dp))
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No auto-correct rules added yet.",
                    style = TranscribeTextStyles.SubText,
                    color = colors.muted
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                settings.autoCorrectRules.forEach { rule ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(colors.card2)
                            .border(1.dp, colors.line, RoundedCornerShape(18.dp))
                            .padding(start = 16.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = rule.wrong,
                                style = TranscribeTextStyles.ListRow,
                                color = colors.muted
                            )
                            Text(
                                text = "→",
                                style = TranscribeTextStyles.ListRow,
                                color = colors.accentText
                            )
                            Text(
                                text = rule.correct,
                                style = TranscribeTextStyles.CardTitle,
                                color = colors.text
                            )
                        }
                        IconButton(
                            onClick = { onRemoveRule(rule.id) },
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.DeleteOutline,
                                contentDescription = "Remove rule",
                                tint = colors.muted,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StorageManagerSubScreen(
    storageStats: StorageStats,
    saveBehavior: AudioSaveBehavior,
    onSelectSaveBehavior: (AudioSaveBehavior) -> Unit,
    onShareAudioFiles: (Set<String>) -> Unit,
    onDeleteAudioFiles: (Set<String>) -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit,
    onClearAllStorage: () -> Unit
) {
    val colors = TranscribeThemeTokens.colors
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    var showClearConfirm by remember { mutableStateOf(false) }
    val dateFmt = remember { SimpleDateFormat("MMM d", Locale.US) }

    val total = storageStats.totalBytes.coerceAtLeast(1L)
    val audioFraction = (storageStats.audioBytes.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    val transcriptFraction = (storageStats.transcriptBytes.toFloat() / total.toFloat()).coerceIn(0f, 1f)

    Box(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 18.dp, end = 18.dp, top = 6.dp, bottom = if (selectedIds.isNotEmpty()) 96.dp else 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 1. Visual Storage Meter
            SettingsPanel(sectionTitle = "LOCAL STORAGE") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        text = formatBytesReadable(storageStats.totalBytes),
                        style = TranscribeTextStyles.BigStatValue,
                        color = colors.text
                    )
                    Text(
                        text = "${storageStats.audioItems.size} audio ${if (storageStats.audioItems.size == 1) "file" else "files"}",
                        style = TranscribeTextStyles.SubText.withTabularNums(),
                        color = colors.muted
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Segmented Storage Meter Bar (solid flat tokens)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(CircleShape)
                        .background(colors.card)
                        .border(1.dp, colors.line, CircleShape),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    if (storageStats.totalBytes > 0L) {
                        if (audioFraction > 0f) {
                            Box(
                                modifier = Modifier
                                    .weight(audioFraction.coerceAtLeast(0.04f))
                                    .fillMaxHeight()
                                    .clip(CircleShape)
                                    .background(colors.accent)
                            )
                        }
                        if (transcriptFraction > 0f) {
                            Box(
                                modifier = Modifier
                                    .weight(transcriptFraction.coerceAtLeast(0.04f))
                                    .fillMaxHeight()
                                    .clip(CircleShape)
                                    .background(colors.good)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    LegendDot(
                        color = colors.accent,
                        label = "Audio: ${formatBytesReadable(storageStats.audioBytes)}"
                    )
                    LegendDot(
                        color = colors.good,
                        label = "Transcripts: ${formatBytesReadable(storageStats.transcriptBytes)}"
                    )
                }
            }

            // 2. Audio Files Manager with Select / Select All
            SettingsPanel(sectionTitle = "AUDIO FILES") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (selectionMode) "${selectedIds.size} selected" else "Saved & extracted audio",
                        style = TranscribeTextStyles.SubText.withTabularNums(),
                        color = colors.muted
                    )
                    if (storageStats.audioItems.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (selectionMode) {
                                val allSelected = selectedIds.size == storageStats.audioItems.size
                                TextButton(
                                    onClick = {
                                        selectedIds = if (allSelected) {
                                            emptySet()
                                        } else {
                                            storageStats.audioItems.map { it.projectId }.toSet()
                                        }
                                    },
                                    modifier = Modifier.testTag("storage_select_all_btn")
                                ) {
                                    Text(
                                        text = if (allSelected) "Clear" else "Select All",
                                        color = colors.accentText,
                                        style = TranscribeTextStyles.ListRow
                                    )
                                }
                            }
                            TextButton(
                                onClick = {
                                    selectionMode = !selectionMode
                                    if (!selectionMode) selectedIds = emptySet()
                                },
                                modifier = Modifier.testTag("storage_select_toggle_btn")
                            ) {
                                Text(
                                    text = if (selectionMode) "Done" else "Select",
                                    color = colors.accentText,
                                    style = TranscribeTextStyles.ListRow
                                )
                            }
                        }
                    }
                }

                if (storageStats.audioItems.isEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "No extracted or recorded audio files stored on device.",
                        style = TranscribeTextStyles.SubText,
                        color = colors.muted
                    )
                } else {
                    Spacer(modifier = Modifier.height(6.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        storageStats.audioItems.forEach { item ->
                            val isChecked = item.projectId in selectedIds
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (isChecked) colors.soft else colors.card)
                                    .border(
                                        1.dp,
                                        if (isChecked) colors.accent else colors.line,
                                        RoundedCornerShape(14.dp)
                                    )
                                    .clickable {
                                        if (!selectionMode) selectionMode = true
                                        selectedIds = if (isChecked) {
                                            selectedIds - item.projectId
                                        } else {
                                            selectedIds + item.projectId
                                        }
                                    }
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = when {
                                        selectionMode && isChecked -> Icons.Outlined.CheckCircle
                                        selectionMode -> Icons.Outlined.RadioButtonUnchecked
                                        else -> Icons.Outlined.AudioFile
                                    },
                                    contentDescription = null,
                                    tint = if (isChecked || !selectionMode) colors.accent else colors.muted,
                                    modifier = Modifier.size(19.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.projectTitle,
                                        style = TranscribeTextStyles.ListRow,
                                        color = colors.text,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${dateFmt.format(Date(item.updatedAt))} · ${item.fileName.substringAfterLast('.').uppercase()}",
                                        style = TranscribeTextStyles.SubText,
                                        color = colors.muted
                                    )
                                }
                                Text(
                                    text = formatBytesReadable(item.sizeBytes),
                                    style = TranscribeTextStyles.SubText.withTabularNums(),
                                    color = colors.muted
                                )
                            }
                        }
                    }
                }
            }

            // 3. Audio Save Behavior
            SettingsPanel(sectionTitle = "EXPORT SAVE BEHAVIOR") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AudioSaveBehavior.entries.forEach { behavior ->
                        val selected = behavior == saveBehavior
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (selected) colors.soft else colors.card)
                                .border(
                                    1.dp,
                                    if (selected) colors.accent else colors.line,
                                    RoundedCornerShape(14.dp)
                                )
                                .clickable { onSelectSaveBehavior(behavior) }
                                .padding(horizontal = 14.dp, vertical = 12.dp)
                                .testTag("save_behavior_${behavior.name.lowercase()}"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = behavior.title,
                                    style = TranscribeTextStyles.ListRow,
                                    color = if (selected) colors.softText else colors.text
                                )
                                Text(
                                    text = behavior.subtitle,
                                    style = TranscribeTextStyles.SubText,
                                    color = colors.muted
                                )
                            }
                            if (selected) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = null,
                                    tint = colors.accent,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 4. Local Backup & Restore
            SettingsPanel(sectionTitle = "LOCAL BACKUP & RESTORE") {
                Text(
                    text = "Export or restore your saved transcripts and Auto-Correct Dictionary as a portable JSON file.",
                    style = TranscribeTextStyles.SubText,
                    color = colors.muted
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SecondaryActionButton(
                        onClick = onExportBackup,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("export_backup_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Download,
                            contentDescription = null,
                            tint = colors.accentText,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Export .json", color = colors.text, style = TranscribeTextStyles.ListRow)
                    }

                    SecondaryActionButton(
                        onClick = onImportBackup,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("import_backup_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Upload,
                            contentDescription = null,
                            tint = colors.accentText,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Import .json", color = colors.text, style = TranscribeTextStyles.ListRow)
                    }
                }
            }

            // 5. Clear All Storage
            SecondaryActionButton(
                onClick = { showClearConfirm = true },
                borderColor = colors.danger,
                contentColor = colors.danger,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("clear_all_storage_btn")
            ) {
                Icon(
                    imageVector = Icons.Outlined.DeleteSweep,
                    contentDescription = null,
                    tint = colors.danger,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Clear Cache / All Storage",
                    color = colors.danger,
                    style = TranscribeTextStyles.ButtonLabel
                )
            }
        }

        // Floating Bottom Pill Dock for Multi-Selected Audio Files
        AnimatedVisibility(
            visible = selectedIds.isNotEmpty(),
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 14.dp, vertical = 14.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(99.dp))
                    .background(colors.card)
                    .border(1.dp, colors.line, RoundedCornerShape(99.dp))
                    .padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .clip(RoundedCornerShape(99.dp))
                        .background(colors.accent)
                        .clickable { onShareAudioFiles(selectedIds) }
                        .testTag("storage_share_selected_btn"),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Share,
                        contentDescription = null,
                        tint = colors.onAccent,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Share (${selectedIds.size})",
                        style = TranscribeTextStyles.ListRow.withTabularNums(),
                        color = colors.onAccent
                    )
                }

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .clip(RoundedCornerShape(99.dp))
                        .background(colors.danger)
                        .clickable {
                            onDeleteAudioFiles(selectedIds)
                            selectedIds = emptySet()
                            selectionMode = false
                        }
                        .testTag("storage_delete_selected_btn"),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteOutline,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Delete",
                        style = TranscribeTextStyles.ListRow,
                        color = Color.White
                    )
                }
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            containerColor = colors.card,
            titleContentColor = colors.text,
            textContentColor = colors.muted,
            shape = RoundedCornerShape(24.dp),
            title = {
                Text(
                    text = "Clear All Local Storage?",
                    style = TranscribeTextStyles.ScreenTitle,
                    color = colors.text
                )
            },
            text = {
                Text(
                    text = "This permanently deletes all saved transcripts, SRT projects, and extracted/recorded audio files from your device. Your API keys remain safely locked.",
                    style = TranscribeTextStyles.ListRow,
                    color = colors.muted
                )
            },
            confirmButton = {
                Box(
                    modifier = Modifier
                        .height(40.dp)
                        .clip(RoundedCornerShape(99.dp))
                        .background(colors.danger)
                        .clickable {
                            showClearConfirm = false
                            selectedIds = emptySet()
                            selectionMode = false
                            onClearAllStorage()
                        }
                        .padding(horizontal = 18.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Clear All", style = TranscribeTextStyles.ListRow, color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text("Cancel", style = TranscribeTextStyles.ListRow, color = colors.muted)
                }
            }
        )
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    val colors = TranscribeThemeTokens.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            style = TranscribeTextStyles.SubText.withTabularNums(),
            color = colors.muted
        )
    }
}

@Composable
private fun AboutSubScreen(
    remoteConfig: RemoteConfig,
    isCheckingUpdate: Boolean,
    onOpenExternalUrl: (String) -> Unit,
    onShareApp: () -> Unit,
    onCopyUpiId: (String) -> Unit,
    onCheckUpdate: () -> Unit
) {
    val colors = TranscribeThemeTokens.colors
    var showSupportSheet by remember { mutableStateOf(false) }

    val about = remoteConfig.about
    val support = remoteConfig.support
    val hasUpdateAvailable = remoteConfig.update.isUpdateAvailable(AppConfig.APP_VERSION) ||
        remoteConfig.update.isForceUpdate(AppConfig.APP_VERSION)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Profile Card: AppLogo + Creator Name + Tagline
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(colors.card2)
                .border(1.dp, colors.line, RoundedCornerShape(22.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AppLogo(size = 44.dp)
                Column {
                    Text(
                        text = about.creatorName.ifBlank { CREATOR_CONFIG.creatorName },
                        style = TranscribeTextStyles.ScreenTitle,
                        color = colors.text
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = about.tagline.ifBlank { CREATOR_CONFIG.creatorTagline },
                        style = TranscribeTextStyles.SubText,
                        color = colors.muted
                    )
                }
            }
        }

        // FIND ME Social Cards (dynamically rendered from config.json; strictly NO GitHub links)
        if (about.links.isNotEmpty()) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Spacer(modifier = Modifier.height(4.dp))
                SectionLabel(text = "FIND ME")
                Spacer(modifier = Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    about.links.forEachIndexed { idx, link ->
                        val icon = when (link.type.lowercase(Locale.US)) {
                            "instagram", "ig" -> Icons.Outlined.CameraAlt
                            "youtube", "yt" -> Icons.Outlined.PlayCircleOutline
                            "email", "mail", "contact" -> Icons.Outlined.Email
                            else -> Icons.Outlined.Language
                        }
                        AboutLinkRow(
                            icon = icon,
                            title = link.label,
                            subtitle = link.subtitle,
                            tag = "about_link_${link.type}_$idx",
                            onClick = { onOpenExternalUrl(link.url) }
                        )
                    }
                }
            }
        }

        // Share App & Support Action Row (Hide Support if support.enabled is false)
        Column(modifier = Modifier.fillMaxWidth()) {
            Spacer(modifier = Modifier.height(4.dp))
            SectionLabel(text = "COMMUNITY")
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PrimaryActionButton(
                    onClick = onShareApp,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("about_share_app_btn")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Share,
                        contentDescription = null,
                        tint = colors.onAccent,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Share App", style = TranscribeTextStyles.ButtonLabel)
                }

                if (support.enabled) {
                    SecondaryActionButton(
                        onClick = { showSupportSheet = true },
                        borderColor = colors.accent,
                        contentColor = colors.accentText,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("about_support_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.FavoriteBorder,
                            contentDescription = null,
                            tint = colors.accentText,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Support", style = TranscribeTextStyles.ButtonLabel, color = colors.accentText)
                    }
                }
            }
        }

        // Version & Check Update Tile
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(colors.card2)
                .border(1.dp, colors.line, RoundedCornerShape(22.dp))
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = AppConfig.APP_NAME,
                    style = TranscribeTextStyles.CardTitle,
                    color = colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                StatusChip(
                    text = AppConfig.APP_VERSION,
                    backgroundColor = colors.soft,
                    textColor = colors.softText
                )
            }

            Box(
                modifier = Modifier
                    .height(40.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(if (hasUpdateAvailable) colors.accent else colors.card)
                    .border(
                        width = 1.dp,
                        color = if (hasUpdateAvailable) colors.accent else colors.line,
                        shape = RoundedCornerShape(99.dp)
                    )
                    .clickable(enabled = !isCheckingUpdate, onClick = onCheckUpdate)
                    .padding(horizontal = 16.dp)
                    .testTag("about_check_update_btn"),
                contentAlignment = Alignment.Center
            ) {
                if (isCheckingUpdate) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = if (hasUpdateAvailable) colors.onAccent else colors.accent
                    )
                } else {
                    Text(
                        text = if (hasUpdateAvailable) "Update Available" else "Check update",
                        style = TranscribeTextStyles.ListRow.copy(fontWeight = FontWeight.SemiBold),
                        color = if (hasUpdateAvailable) colors.onAccent else colors.text,
                        maxLines = 1
                    )
                }
            }
        }
    }

    if (showSupportSheet && support.enabled) {
        SupportBottomSheet(
            title = support.title,
            supportNote = support.message,
            upiId = support.upiId,
            supportUrl = support.url,
            supportUrlLabel = support.urlLabel,
            onDismiss = { showSupportSheet = false },
            onCopyUpi = { onCopyUpiId(support.upiId) },
            onOpenSupportLink = {
                showSupportSheet = false
                if (support.url.isNotBlank()) {
                    onOpenExternalUrl(support.url)
                }
            }
        )
    }
}

@Composable
private fun AboutLinkRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    tag: String,
    onClick: () -> Unit
) {
    val colors = TranscribeThemeTokens.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(colors.card2)
            .border(1.dp, colors.line, RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(colors.soft),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = colors.accentText,
                modifier = Modifier.size(20.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = TranscribeTextStyles.CardTitle,
                color = colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = TranscribeTextStyles.SubText,
                color = colors.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = colors.muted,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun SettingsPanel(
    sectionTitle: String,
    content: @Composable () -> Unit
) {
    val colors = TranscribeThemeTokens.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(colors.card2)
            .border(1.dp, colors.line, RoundedCornerShape(22.dp))
            .padding(16.dp)
    ) {
        SectionLabel(text = sectionTitle)
        Spacer(modifier = Modifier.height(8.dp))
        content()
    }
}

fun formatBytesReadable(bytes: Long): String {
    if (bytes <= 0L) return "0.0 MB"
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 0.1) {
        String.format(Locale.US, "%.1f MB", mb)
    } else {
        val kb = bytes / 1024.0
        String.format(Locale.US, "%.0f KB", kb.coerceAtLeast(1.0))
    }
}
