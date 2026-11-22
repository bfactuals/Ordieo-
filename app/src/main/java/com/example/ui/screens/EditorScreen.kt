package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.media.PlaybackState
import com.example.data.model.LanguageMode
import com.example.data.model.ProjectModel
import com.example.data.model.RetentionOption
import com.example.data.model.TextCasing
import com.example.data.model.TranscriptSegment
import com.example.data.model.WordChunkMode
import com.example.data.srt.FormattedCue
import com.example.data.srt.SrtEngine
import com.example.ui.components.ExpiryPickerDialog
import com.example.ui.components.ExportBottomSheet
import com.example.ui.components.InteractiveWaveformScrubber
import com.example.ui.components.PrimaryActionButton
import com.example.ui.components.SegmentActionsDialog
import com.example.ui.components.StatusChip
import com.example.ui.components.ToolsDrawerSheet
import com.example.ui.theme.StageHighlight
import com.example.ui.theme.StageTextMuted
import com.example.ui.theme.StageTextPrimary
import com.example.ui.theme.TranscribeTextStyles
import com.example.ui.theme.TranscribeThemeTokens
import com.example.ui.theme.fontFamilyForText
import com.example.ui.theme.withTabularNums

@Composable
fun EditorScreen(
    project: ProjectModel,
    playback: PlaybackState,
    providerSummary: String,
    isTranslatingEnglish: Boolean,
    isConvertingHinglish: Boolean,
    onBack: () -> Unit,
    onUpdateTitle: (String) -> Unit,
    onUpdateExpiry: (RetentionOption) -> Unit,
    onDownloadAudio: () -> Unit,
    onSave: () -> Unit,
    onTranscribeNow: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onCyclePlaybackSpeed: () -> Unit,
    onSeekTo: (Long, Boolean) -> Unit,
    onSelectWordChunk: (WordChunkMode) -> Unit,
    onSelectLanguageMode: (LanguageMode) -> Unit,
    onTranslateToEnglish: () -> Unit,
    onConvertHinglishOffline: () -> Unit,
    onSmartSpellFixHinglish: () -> Unit,
    onUpdateSegmentText: (Int, String) -> Unit,
    onSplitSegment: (Int) -> Unit,
    onMergeSegmentWithNext: (Int) -> Unit,
    onDeleteSegment: (Int) -> Unit,
    onReplaceAll: (String, String) -> Unit,
    onToggleRemoveFillerWords: (Boolean) -> Unit,
    onToggleStripPunctuation: (Boolean) -> Unit,
    onSelectTextCasing: (TextCasing) -> Unit,
    onAdjustTimestampShift: (Long) -> Unit,
    onResetTimestampShift: () -> Unit,
    onOpenMagicScreen: () -> Unit,
    onCopyText: (label: String, text: String) -> Unit,
    onExportTxt: () -> Unit,
    onExportSrt: () -> Unit,
    onShareSrt: () -> Unit
) {
    BackHandler { onBack() }

    val colors = TranscribeThemeTokens.colors
    var showExpiryDialog by remember { mutableStateOf(false) }
    var showFindReplace by remember { mutableStateOf(false) }
    var showToolsDrawer by remember { mutableStateOf(false) }
    var showExportSheet by remember { mutableStateOf(false) }
    var actionTargetSegment by remember { mutableStateOf<Pair<Int, TranscriptSegment>?>(null) }
    var findQuery by remember { mutableStateOf("") }
    var replaceQuery by remember { mutableStateOf("") }

    val activeSegments = project.activeSegments
    val matchCount = remember(activeSegments, findQuery) {
        SrtEngine.countMatches(activeSegments, findQuery)
    }

    val formattedCues = remember(
        activeSegments,
        project.wordChunkMode,
        project.removeFillerWords,
        project.stripPunctuation,
        project.textCasing,
        project.timestampShiftMs
    ) {
        SrtEngine.buildFormattedCues(
            segments = activeSegments,
            wordChunkMode = project.wordChunkMode,
            removeFillerWords = project.removeFillerWords,
            stripPunctuation = project.stripPunctuation,
            casing = project.textCasing,
            timestampShiftMs = project.timestampShiftMs
        )
    }

    val activeCue = remember(formattedCues, playback.positionMs) {
        SrtEngine.findActiveCue(formattedCues, playback.positionMs)
            ?: formattedCues.firstOrNull()
    }

    val activeWordIndex = remember(activeCue, playback.positionMs, playback.isPlaying) {
        if (activeCue == null) -1
        else SrtEngine.findActiveWordIndex(activeCue, playback.positionMs)
    }

    val listState = rememberLazyListState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .widthIn(max = 760.dp)
                .align(Alignment.TopCenter)
        ) {
            // 1. Header: Back, editable filename, countdown chip, offline Download Audio icon, Save pill
            EditorTopBar(
                title = project.title,
                expiryLabel = if (project.isPinned) "Kept" else formatRemainingExpiry(project.expiresAt, project.retentionOption),
                hasAudio = project.hasAudio,
                onBack = onBack,
                onUpdateTitle = onUpdateTitle,
                onExpiryClick = { showExpiryDialog = true },
                onDownloadAudio = onDownloadAudio,
                onSave = onSave
            )

            // 2. Scrollable Editor Content
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag("transcript_editor_list"),
                contentPadding = PaddingValues(
                    start = 18.dp,
                    end = 18.dp,
                    top = 6.dp,
                    bottom = 104.dp
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Live SRT Stage (--stage dark bg, radius 22, 1dp --line)
                // Top-left "LIVE SRT", Top-right segment control (1 | 2 | 3 | Line), centered 30sp Bold caption
                // Plus integrated Play button (42dp), 50-bar waveform, timestamps, speed pill (0.5x | 1x | 1.5x | 2x)
                item(key = "live_stage_and_player") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        LiveSrtStageBox(
                            hasAudio = project.hasAudio,
                            wordChunkMode = project.wordChunkMode,
                            activeCue = activeCue,
                            activeWordIndex = activeWordIndex,
                            onSelectWordChunk = onSelectWordChunk
                        )

                        PlayerBarCard(
                            waveform = project.waveform,
                            playback = playback,
                            onTogglePlayPause = onTogglePlayPause,
                            onCyclePlaybackSpeed = onCyclePlaybackSpeed,
                            onSeekFraction = { frac ->
                                val target = (playback.durationMs * frac).toLong()
                                onSeekTo(target, false)
                            }
                        )
                    }
                }

                // Status Row: "N segments · Provider" on left, "Transcribe Now" / "Re-transcribe" on right
                item(key = "status_row") {
                    if (project.hasAudio && !project.hasTranscript) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "0 segments · $providerSummary",
                                    style = TranscribeTextStyles.SubText.withTabularNums(),
                                    color = colors.muted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            PrimaryActionButton(
                                onClick = onTranscribeNow,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("transcribe_now_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.GraphicEq,
                                    contentDescription = null,
                                    tint = colors.onAccent,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Transcribe Now",
                                    style = TranscribeTextStyles.ButtonLabel
                                )
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${activeSegments.size} segments · $providerSummary",
                                style = TranscribeTextStyles.SubText.withTabularNums(),
                                color = colors.muted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            if (project.hasAudio) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Row(
                                    modifier = Modifier
                                        .height(34.dp)
                                        .clip(RoundedCornerShape(99.dp))
                                        .background(colors.card)
                                        .border(1.dp, colors.line, RoundedCornerShape(99.dp))
                                        .clickable(onClick = onTranscribeNow)
                                        .padding(horizontal = 12.dp)
                                        .testTag("re_transcribe_btn"),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Refresh,
                                        contentDescription = null,
                                        tint = colors.accentText,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "Re-transcribe",
                                        style = TranscribeTextStyles.SubText,
                                        color = colors.accentText
                                    )
                                }
                            }
                        }
                    }
                }

                // Language Tabs: Original | English | Hinglish
                item(key = "language_tabs") {
                    LanguageModeTabs(
                        languageMode = project.languageMode,
                        isTranslatingEnglish = isTranslatingEnglish,
                        isConvertingHinglish = isConvertingHinglish,
                        onSelectLanguageMode = onSelectLanguageMode
                    )
                }

                // Expandable Inline Find & Replace Bar (toggled by Find in bottom dock)
                item(key = "find_replace_panel") {
                    AnimatedVisibility(
                        visible = showFindReplace,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        FindReplaceBar(
                            findQuery = findQuery,
                            replaceQuery = replaceQuery,
                            matchCount = matchCount,
                            onFindChange = { findQuery = it },
                            onReplaceChange = { replaceQuery = it },
                            onReplaceAll = { onReplaceAll(findQuery, replaceQuery) },
                            onClose = { showFindReplace = false }
                        )
                    }
                }

                // Segment Cards
                if (activeSegments.isEmpty()) {
                    item(key = "transcript_empty") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(22.dp))
                                .background(colors.card)
                                .border(1.dp, colors.line, RoundedCornerShape(22.dp))
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = if (project.hasAudio) "Audio Saved Offline" else "No transcript lines yet",
                                style = TranscribeTextStyles.CardTitle,
                                color = colors.text
                            )
                            Text(
                                text = if (project.hasAudio) {
                                    "Play, scrub, or download audio offline anytime. Tap Transcribe Now above to generate timestamped captions."
                                } else {
                                    "Import a .SRT or .TXT file to begin editing."
                                },
                                style = TranscribeTextStyles.SubText,
                                color = colors.muted,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    itemsIndexed(
                        items = activeSegments,
                        key = { _, seg -> "${project.languageMode.name}_${seg.id}" }
                    ) { index, seg ->
                        val shiftedStart = (seg.startMs + project.timestampShiftMs).coerceAtLeast(0L)
                        val shiftedEnd = (seg.endMs + project.timestampShiftMs).coerceAtLeast(shiftedStart + 200L)
                        val isCurrent = activeCue?.sourceSegmentId == seg.id

                        TranscriptSegmentCard(
                            segment = seg,
                            shiftedStartMs = shiftedStart,
                            shiftedEndMs = shiftedEnd,
                            isCurrent = isCurrent,
                            onSeek = { onSeekTo(shiftedStart, true) },
                            onLongPress = { actionTargetSegment = Pair(index, seg) },
                            onTextChange = { updated -> onUpdateSegmentText(seg.id, updated) }
                        )
                    }
                }
            }
        }

        // 3. Floating Bottom Pill Dock (--card, 1dp --line, 4 buttons: Find, Tools, Magic filled --accent, Export)
        EditorFloatingBottomDock(
            findActive = showFindReplace,
            onFindClick = { showFindReplace = !showFindReplace },
            onToolsClick = { showToolsDrawer = true },
            onMagicClick = onOpenMagicScreen,
            onExportClick = { showExportSheet = true },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .widthIn(max = 760.dp)
                .navigationBarsPadding()
                .padding(horizontal = 14.dp, vertical = 14.dp)
        )
    }

    if (showExpiryDialog) {
        ExpiryPickerDialog(
            currentOption = project.retentionOption,
            onDismiss = { showExpiryDialog = false },
            onSelect = onUpdateExpiry
        )
    }

    if (showToolsDrawer) {
        ToolsDrawerSheet(
            segments = activeSegments,
            removeFillerWords = project.removeFillerWords,
            stripPunctuation = project.stripPunctuation,
            textCasing = project.textCasing,
            timestampShiftMs = project.timestampShiftMs,
            isTranslatingEnglish = isTranslatingEnglish,
            isConvertingHinglish = isConvertingHinglish,
            onDismiss = { showToolsDrawer = false },
            onTranslateToEnglish = onTranslateToEnglish,
            onConvertHinglishOffline = onConvertHinglishOffline,
            onSmartSpellFixHinglish = onSmartSpellFixHinglish,
            onToggleRemoveFillerWords = onToggleRemoveFillerWords,
            onToggleStripPunctuation = onToggleStripPunctuation,
            onSelectTextCasing = onSelectTextCasing,
            onAdjustTimestampShift = onAdjustTimestampShift,
            onResetTimestampShift = onResetTimestampShift,
            onReplaceAll = onReplaceAll
        )
    }

    if (showExportSheet) {
        ExportBottomSheet(
            hasTranscript = project.hasTranscript,
            onDismiss = { showExportSheet = false },
            onExportSrt = onExportSrt,
            onExportTxt = onExportTxt,
            onCopyText = {
                val formatted = SrtEngine.exportPlainText(
                    segments = project.activeSegments,
                    removeFillerWords = project.removeFillerWords,
                    stripPunctuation = project.stripPunctuation,
                    casing = project.textCasing
                )
                onCopyText("Transcript", formatted)
            },
            onShare = onShareSrt
        )
    }

    actionTargetSegment?.let { (index, seg) ->
        val wordCount = seg.text.trim().split(Regex("""\s+""")).count { it.isNotBlank() }
        SegmentActionsDialog(
            segmentIndex = index + 1,
            canSplit = wordCount >= 2,
            canMergeNext = index < activeSegments.lastIndex,
            onDismiss = { actionTargetSegment = null },
            onSplit = { onSplitSegment(seg.id) },
            onMergeNext = { onMergeSegmentWithNext(seg.id) },
            onDelete = { onDeleteSegment(seg.id) }
        )
    }
}

@Composable
private fun EditorTopBar(
    title: String,
    expiryLabel: String,
    hasAudio: Boolean,
    onBack: () -> Unit,
    onUpdateTitle: (String) -> Unit,
    onExpiryClick: () -> Unit,
    onDownloadAudio: () -> Unit,
    onSave: () -> Unit
) {
    val colors = TranscribeThemeTokens.colors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 10.dp, end = 18.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .size(44.dp)
                .testTag("editor_back_btn")
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.cd_back),
                tint = colors.text
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(10.dp))
                .padding(horizontal = 4.dp, vertical = 4.dp)
        ) {
            BasicTextField(
                value = title,
                onValueChange = onUpdateTitle,
                singleLine = true,
                textStyle = TranscribeTextStyles.ScreenTitle.copy(color = colors.text),
                cursorBrush = SolidColor(colors.accent),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("editor_title_input"),
                decorationBox = { inner ->
                    if (title.isBlank()) {
                        Text(
                            text = "Untitled",
                            style = TranscribeTextStyles.ScreenTitle,
                            color = colors.muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    inner()
                }
            )
        }

        Row(
            modifier = Modifier
                .height(34.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(colors.card)
                .border(1.dp, colors.line, RoundedCornerShape(99.dp))
                .clickable(onClick = onExpiryClick)
                .padding(horizontal = 10.dp)
                .testTag("editor_expiry_pill"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Schedule,
                contentDescription = "Change expiry timer",
                tint = colors.muted,
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = expiryLabel,
                style = TranscribeTextStyles.SubText.withTabularNums(),
                color = colors.muted,
                maxLines = 1
            )
        }

        if (hasAudio) {
            IconButton(
                onClick = onDownloadAudio,
                modifier = Modifier
                    .size(44.dp)
                    .testTag("download_audio_btn")
            ) {
                Icon(
                    imageVector = Icons.Outlined.Download,
                    contentDescription = stringResource(R.string.cd_download_audio),
                    tint = colors.text,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Box(
            modifier = Modifier
                .height(40.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(colors.accent)
                .clickable(onClick = onSave)
                .padding(horizontal = 16.dp)
                .testTag("editor_save_btn"),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Save",
                style = TranscribeTextStyles.ListRow.copy(fontWeight = FontWeight.SemiBold),
                color = colors.onAccent
            )
        }
    }
}

@Composable
private fun LiveSrtStageBox(
    hasAudio: Boolean,
    wordChunkMode: WordChunkMode,
    activeCue: FormattedCue?,
    activeWordIndex: Int,
    onSelectWordChunk: (WordChunkMode) -> Unit
) {
    val colors = TranscribeThemeTokens.colors
    val activeCueText = activeCue?.text.orEmpty()
    val stageFontFamily = remember(activeCueText) { fontFamilyForText(activeCueText) }

    val highlightedCaption = remember(activeCueText, activeWordIndex) {
        if (activeCueText.isBlank()) {
            null
        } else {
            val words = activeCueText.split(Regex("""\s+""")).filter { it.isNotBlank() }
            buildAnnotatedString {
                words.forEachIndexed { idx, word ->
                    if (idx > 0) append(" ")
                    val isActive = idx == activeWordIndex
                    withStyle(
                        style = SpanStyle(
                            color = if (isActive) StageHighlight else StageTextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    ) {
                        append(word)
                    }
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 152.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(colors.stage)
            .border(1.dp, colors.line, RoundedCornerShape(22.dp))
            .padding(16.dp)
            .testTag("live_srt_preview_box"),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top Bar inside Stage: "LIVE SRT" left, compact segment control (1 | 2 | 3 | Line) right
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "LIVE SRT",
                style = TranscribeTextStyles.SectionLabel,
                color = StageTextMuted
            )

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(99.dp))
                    .background(Color(0xFF14182B))
                    .border(1.dp, Color(0xFF262B45), RoundedCornerShape(99.dp))
                    .padding(2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                WordChunkMode.entries.forEach { mode ->
                    val selected = mode == wordChunkMode
                    Box(
                        modifier = Modifier
                            .height(26.dp)
                            .clip(RoundedCornerShape(99.dp))
                            .background(if (selected) colors.accent else Color.Transparent)
                            .clickable { onSelectWordChunk(mode) }
                            .padding(horizontal = 10.dp)
                            .testTag("chunk_${mode.name.lowercase()}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = mode.shortLabel,
                            style = TranscribeTextStyles.SubText.copy(
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                            ),
                            color = if (selected) colors.onAccent else StageTextMuted,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Centered 30sp Bold caption text
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp),
            contentAlignment = Alignment.Center
        ) {
            if (highlightedCaption != null) {
                Text(
                    text = highlightedCaption,
                    style = TranscribeTextStyles.StageCaption.copy(fontFamily = stageFontFamily),
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Text(
                    text = if (hasAudio) {
                        "Tap Transcribe Now for synced live captions"
                    } else {
                        "Tap play to preview synced captions"
                    },
                    style = TranscribeTextStyles.ListRow,
                    color = StageTextMuted,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun PlayerBarCard(
    waveform: List<Float>,
    playback: PlaybackState,
    onTogglePlayPause: () -> Unit,
    onCyclePlaybackSpeed: () -> Unit,
    onSeekFraction: (Float) -> Unit
) {
    val colors = TranscribeThemeTokens.colors
    val progress = if (playback.durationMs > 0L) {
        (playback.positionMs.toFloat() / playback.durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(colors.card)
            .border(1.dp, colors.line, RoundedCornerShape(22.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 42dp round Play button (--accent)
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(colors.accent)
                    .clickable(onClick = onTogglePlayPause)
                    .testTag("play_pause_btn"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (playback.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = stringResource(R.string.cd_play_pause),
                    tint = colors.onAccent,
                    modifier = Modifier.size(24.dp)
                )
            }

            // 50-bar seekable waveform
            InteractiveWaveformScrubber(
                waveform = waveform,
                progress = progress,
                onSeekFraction = onSeekFraction,
                modifier = Modifier
                    .weight(1f)
                    .height(34.dp)
            )

            // Speed pill (0.5x | 1x | 1.5x | 2x)
            Box(
                modifier = Modifier
                    .height(34.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(if (playback.speed.multiplier != 1f) colors.soft else colors.card2)
                    .border(
                        width = 1.dp,
                        color = if (playback.speed.multiplier != 1f) colors.accent else colors.line,
                        shape = RoundedCornerShape(99.dp)
                    )
                    .clickable(onClick = onCyclePlaybackSpeed)
                    .padding(horizontal = 10.dp)
                    .testTag("playback_speed_btn"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = playback.speed.label,
                    style = TranscribeTextStyles.SubText.withTabularNums().copy(fontWeight = FontWeight.SemiBold),
                    color = if (playback.speed.multiplier != 1f) colors.softText else colors.text
                )
            }
        }

        // Timestamps: 00:00.0 / total
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${SrtEngine.formatTimestampCompact(playback.positionMs)} / ${SrtEngine.formatTimestampCompact(playback.durationMs)}",
                style = TranscribeTextStyles.SubText.withTabularNums(),
                color = colors.muted
            )
            Text(
                text = "Long-press a card to split · merge · delete",
                style = TranscribeTextStyles.SubText,
                color = colors.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun LanguageModeTabs(
    languageMode: LanguageMode,
    isTranslatingEnglish: Boolean,
    isConvertingHinglish: Boolean,
    onSelectLanguageMode: (LanguageMode) -> Unit
) {
    val colors = TranscribeThemeTokens.colors
    val busy = isTranslatingEnglish || isConvertingHinglish

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(99.dp))
            .background(colors.card)
            .border(1.dp, colors.line, RoundedCornerShape(99.dp))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        LanguageMode.entries.forEach { mode ->
            val selected = mode == languageMode
            val modeLoading = (mode == LanguageMode.ENGLISH && isTranslatingEnglish) ||
                (mode == LanguageMode.HINGLISH && isConvertingHinglish)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(if (selected) colors.soft else Color.Transparent)
                    .border(
                        width = 1.dp,
                        color = if (selected) colors.accent else Color.Transparent,
                        shape = RoundedCornerShape(99.dp)
                    )
                    .clickable(enabled = !busy) { onSelectLanguageMode(mode) }
                    .testTag("lang_mode_${mode.name.lowercase()}"),
                contentAlignment = Alignment.Center
            ) {
                if (modeLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = colors.accent
                    )
                } else {
                    Text(
                        text = mode.label,
                        style = TranscribeTextStyles.ListRow.copy(
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                        ),
                        color = if (selected) colors.softText else colors.muted,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun FindReplaceBar(
    findQuery: String,
    replaceQuery: String,
    matchCount: Int,
    onFindChange: (String) -> Unit,
    onReplaceChange: (String) -> Unit,
    onReplaceAll: () -> Unit,
    onClose: () -> Unit
) {
    val colors = TranscribeThemeTokens.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(colors.card)
            .border(1.dp, colors.line, RoundedCornerShape(22.dp))
            .padding(14.dp)
            .testTag("find_replace_bar"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = findQuery,
                onValueChange = onFindChange,
                placeholder = { Text("Find", style = TranscribeTextStyles.SubText, color = colors.muted) },
                singleLine = true,
                textStyle = TranscribeTextStyles.ListRow.copy(color = colors.text),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.accent,
                    unfocusedBorderColor = colors.line,
                    focusedContainerColor = colors.card2,
                    unfocusedContainerColor = colors.card2,
                    focusedTextColor = colors.text,
                    unfocusedTextColor = colors.text
                ),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
            )
            OutlinedTextField(
                value = replaceQuery,
                onValueChange = onReplaceChange,
                placeholder = { Text("Replace", style = TranscribeTextStyles.SubText, color = colors.muted) },
                singleLine = true,
                textStyle = TranscribeTextStyles.ListRow.copy(color = colors.text),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.accent,
                    unfocusedBorderColor = colors.line,
                    focusedContainerColor = colors.card2,
                    unfocusedContainerColor = colors.card2,
                    focusedTextColor = colors.text,
                    unfocusedTextColor = colors.text
                ),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (findQuery.isNotEmpty()) {
                StatusChip(
                    text = "$matchCount ${if (matchCount == 1) "match" else "matches"}",
                    backgroundColor = if (matchCount > 0) colors.soft else colors.card2,
                    textColor = if (matchCount > 0) colors.softText else colors.muted
                )
            } else {
                Spacer(modifier = Modifier.width(1.dp))
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose, modifier = Modifier.size(44.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.cd_close),
                        tint = colors.muted,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Box(
                    modifier = Modifier
                        .height(40.dp)
                        .clip(RoundedCornerShape(99.dp))
                        .background(if (findQuery.isNotEmpty() && matchCount > 0) colors.accent else colors.card2)
                        .clickable(enabled = findQuery.isNotEmpty() && matchCount > 0, onClick = onReplaceAll)
                        .padding(horizontal = 16.dp)
                        .testTag("replace_all_btn"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "All",
                        style = TranscribeTextStyles.ListRow.copy(fontWeight = FontWeight.SemiBold),
                        color = if (findQuery.isNotEmpty() && matchCount > 0) colors.onAccent else colors.muted
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TranscriptSegmentCard(
    segment: TranscriptSegment,
    shiftedStartMs: Long,
    shiftedEndMs: Long,
    isCurrent: Boolean,
    onSeek: () -> Unit,
    onLongPress: () -> Unit,
    onTextChange: (String) -> Unit
) {
    val colors = TranscribeThemeTokens.colors
    val borderColor by animateColorAsState(
        targetValue = if (isCurrent) colors.accent else colors.line,
        label = "seg_border"
    )
    val bgColor by animateColorAsState(
        targetValue = if (isCurrent) colors.card2 else colors.card,
        label = "seg_bg"
    )
    val segmentFontFamily = remember(segment.text) { fontFamilyForText(segment.text) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(22.dp))
            .combinedClickable(
                onClick = onSeek,
                onLongClick = onLongPress
            )
            .padding(16.dp)
            .testTag("segment_card_${segment.id}"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Timestamp Pill (tap to seek; active uses --accent pill)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(99.dp))
                    .background(if (isCurrent) colors.accent else colors.card2)
                    .border(
                        1.dp,
                        if (isCurrent) colors.accent else colors.line,
                        RoundedCornerShape(99.dp)
                    )
                    .clickable(onClick = onSeek)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "${SrtEngine.formatTimestampCompact(shiftedStartMs)} – ${SrtEngine.formatTimestampCompact(shiftedEndMs)}",
                    style = TranscribeTextStyles.SubText.withTabularNums().copy(
                        fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Medium
                    ),
                    color = if (isCurrent) colors.onAccent else colors.muted
                )
            }

            Text(
                text = "#${segment.id}",
                style = TranscribeTextStyles.SubText.withTabularNums(),
                color = colors.muted
            )
        }

        // Editable transcript text (15sp Regular, line-height 1.55)
        BasicTextField(
            value = segment.text,
            onValueChange = onTextChange,
            textStyle = TranscribeTextStyles.BodyTranscript.copy(
                color = colors.text,
                fontFamily = segmentFontFamily
            ),
            cursorBrush = SolidColor(colors.accent),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("segment_input_${segment.id}")
        )
    }
}

@Composable
private fun EditorFloatingBottomDock(
    findActive: Boolean,
    onFindClick: () -> Unit,
    onToolsClick: () -> Unit,
    onMagicClick: () -> Unit,
    onExportClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = TranscribeThemeTokens.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(99.dp))
            .background(colors.card)
            .border(1.dp, colors.line, RoundedCornerShape(99.dp))
            .padding(6.dp)
            .testTag("editor_bottom_dock"),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DockButton(
            icon = Icons.Outlined.Search,
            label = "Find",
            filledAccent = false,
            highlighted = findActive,
            tag = "find_replace_btn",
            onClick = onFindClick,
            modifier = Modifier.weight(1f)
        )
        DockButton(
            icon = Icons.Outlined.Tune,
            label = "Tools",
            filledAccent = false,
            highlighted = false,
            tag = "tools_drawer_btn",
            onClick = onToolsClick,
            modifier = Modifier.weight(1f)
        )
        DockButton(
            icon = Icons.Outlined.AutoAwesome,
            label = "Magic",
            filledAccent = true,
            highlighted = false,
            tag = "magic_drawer_btn",
            onClick = onMagicClick,
            modifier = Modifier.weight(1.1f)
        )
        DockButton(
            icon = Icons.Outlined.IosShare,
            label = "Export",
            filledAccent = false,
            highlighted = false,
            tag = "editor_export_btn",
            onClick = onExportClick,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun DockButton(
    icon: ImageVector,
    label: String,
    filledAccent: Boolean,
    highlighted: Boolean,
    tag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = TranscribeThemeTokens.colors
    val bg = when {
        filledAccent -> colors.accent
        highlighted -> colors.soft
        else -> Color.Transparent
    }
    val fg = when {
        filledAccent -> colors.onAccent
        highlighted -> colors.softText
        else -> colors.text
    }

    Row(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(99.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp)
            .testTag(tag),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = fg,
            modifier = Modifier.size(17.dp)
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = label,
            style = TranscribeTextStyles.SubText.copy(fontWeight = FontWeight.SemiBold),
            color = fg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
