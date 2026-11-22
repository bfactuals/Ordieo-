package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.VideoFile
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.AppConfig
import com.example.R
import com.example.data.media.RecordingState
import com.example.data.model.AiProvider
import com.example.data.model.DailyUsage
import com.example.data.model.RetentionOption
import com.example.data.remote.UpdatePromptState
import com.example.data.srt.SrtEngine
import com.example.ui.theme.TranscribeTextStyles
import com.example.ui.theme.TranscribeThemeTokens
import com.example.ui.theme.withTabularNums
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddActionBottomSheet(
    onDismiss: () -> Unit,
    onUploadVideo: () -> Unit,
    onUploadAudio: () -> Unit,
    onRecordVoice: () -> Unit,
    onConvertHinglish: () -> Unit
) {
    val colors = TranscribeThemeTokens.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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
                .padding(start = 18.dp, end = 18.dp, top = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Start New",
                style = TranscribeTextStyles.ScreenTitle,
                color = colors.text
            )
            Text(
                text = "All options save locally offline first — no API key required to open the Editor.",
                style = TranscribeTextStyles.SubText,
                color = colors.muted
            )

            Spacer(modifier = Modifier.height(4.dp))

            AddOptionRow(
                icon = Icons.Outlined.VideoFile,
                title = "Upload Video",
                subtitle = "Extracts audio offline (MP4, MOV, WebM) & opens Editor",
                tag = "option_upload_video",
                onClick = {
                    onDismiss()
                    onUploadVideo()
                }
            )

            AddOptionRow(
                icon = Icons.Outlined.AudioFile,
                title = "Upload Audio",
                subtitle = "Saves MP3, WAV, or M4A locally & opens Editor",
                tag = "option_upload_audio",
                onClick = {
                    onDismiss()
                    onUploadAudio()
                }
            )

            AddOptionRow(
                icon = Icons.Outlined.Mic,
                title = "Record Voice",
                subtitle = "Built-in minimalist mic recorder with live waveform",
                tag = "option_record_voice",
                onClick = {
                    onDismiss()
                    onRecordVoice()
                }
            )

            AddOptionRow(
                icon = Icons.Outlined.Translate,
                title = "Import .SRT / .TXT",
                subtitle = "Load subtitles or text to edit & convert to Hinglish",
                tag = "option_convert_hinglish",
                onClick = {
                    onDismiss()
                    onConvertHinglish()
                }
            )
        }
    }
}

@Composable
private fun AddOptionRow(
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
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(colors.soft),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = colors.accentText,
                modifier = Modifier.size(22.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = TranscribeTextStyles.CardTitle,
                color = colors.text
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = TranscribeTextStyles.SubText,
                color = colors.muted
            )
        }
    }
}

@Composable
fun VoiceRecorderDialog(
    state: RecordingState,
    onCancel: () -> Unit,
    onStopAndSave: () -> Unit
) {
    val colors = TranscribeThemeTokens.colors

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 18.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(colors.card)
                .border(1.dp, colors.line, RoundedCornerShape(28.dp))
                .padding(start = 18.dp, end = 18.dp, top = 20.dp, bottom = 24.dp)
                .testTag("voice_recorder_dialog"),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(colors.danger)
                )
                Text(
                    text = "Recording Voice",
                    style = TranscribeTextStyles.CardTitle,
                    color = colors.text
                )
            }

            Text(
                text = SrtEngine.formatTimestampCompact(state.elapsedMs),
                style = TranscribeTextStyles.BigStatValue,
                color = colors.text
            )

            // Live animated amplitude waveform (strictly solid accent bars, no gradients)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(74.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(colors.card2)
                    .border(1.dp, colors.line, RoundedCornerShape(18.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxWidth().height(54.dp)) {
                    val bars = state.liveAmplitudes
                    if (bars.isEmpty()) return@Canvas
                    val gap = 3.dp.toPx()
                    val barW = ((size.width - gap * (bars.size - 1)) / bars.size).coerceAtLeast(2.dp.toPx())
                    val centerY = size.height / 2f
                    bars.forEachIndexed { idx, amp ->
                        val h = (amp.coerceIn(0.14f, 1f) * size.height).coerceAtLeast(5.dp.toPx())
                        val x = idx * (barW + gap)
                        drawRoundRect(
                            color = colors.accent,
                            topLeft = Offset(x, centerY - h / 2f),
                            size = Size(barW, h),
                            cornerRadius = CornerRadius(barW / 2f, barW / 2f)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SecondaryActionButton(
                    onClick = onCancel,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("cancel_recording_btn")
                ) {
                    Text(
                        text = "Cancel",
                        style = TranscribeTextStyles.ButtonLabel,
                        color = colors.muted
                    )
                }

                PrimaryActionButton(
                    onClick = onStopAndSave,
                    containerColor = colors.danger,
                    contentColor = Color.White,
                    modifier = Modifier
                        .weight(1.3f)
                        .testTag("stop_save_recording_btn")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Stop,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Stop & Open",
                        style = TranscribeTextStyles.ButtonLabel,
                        color = Color.White
                    )
                }
            }
        }
    }
}

/**
 * Dedicated Dialog (not a swipe-dismissing bottom sheet) so touch-scrolling inside
 * pasted text or long .SRT files never conflicts with or dismisses the modal.
 */
@Composable
fun HinglishConverterModal(
    importedTitle: String,
    importedContent: String,
    onDismiss: () -> Unit,
    onPickFile: () -> Unit,
    onOpenInEditor: (content: String, title: String) -> Unit
) {
    val colors = TranscribeThemeTokens.colors

    var title by remember { mutableStateOf(importedTitle) }
    var text by remember { mutableStateOf(importedContent) }

    LaunchedEffect(importedTitle, importedContent) {
        if (importedTitle.isNotBlank()) title = importedTitle
        if (importedContent.isNotBlank()) text = importedContent
    }

    val isSrtDetected = remember(text) {
        text.contains("-->")
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 18.dp, vertical = 20.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(colors.card)
                .border(1.dp, colors.line, RoundedCornerShape(28.dp))
                .padding(start = 18.dp, end = 18.dp, top = 20.dp, bottom = 24.dp)
                .testTag("hinglish_converter_sheet"),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Import .SRT / .TXT",
                        style = TranscribeTextStyles.ScreenTitle,
                        color = colors.text
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Loads offline into Editor — convert to Hinglish anytime",
                        style = TranscribeTextStyles.SubText,
                        color = colors.muted
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.cd_close),
                        tint = colors.muted
                    )
                }
            }

            SecondaryActionButton(
                onClick = onPickFile,
                borderColor = colors.accent,
                contentColor = colors.accentText,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("pick_txt_srt_file_btn")
            ) {
                Icon(
                    imageVector = Icons.Outlined.Description,
                    contentDescription = null,
                    tint = colors.accentText,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Upload .SRT or .TXT File",
                    color = colors.accentText,
                    style = TranscribeTextStyles.ButtonLabel
                )
            }

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                placeholder = {
                    Text(
                        text = "Project title (optional)",
                        style = TranscribeTextStyles.ListRow,
                        color = colors.muted
                    )
                },
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
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
            )

            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = {
                    Text(
                        text = "Paste Hindi / English text or full .SRT subtitles here...\nScroll freely — SRT timestamps are strictly preserved.",
                        color = colors.muted,
                        style = TranscribeTextStyles.SubText
                    )
                },
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
                    .fillMaxWidth()
                    .heightIn(min = 160.dp, max = 260.dp)
                    .testTag("hinglish_raw_input")
            )

            if (text.isNotBlank()) {
                StatusChip(
                    text = if (isSrtDetected) {
                        "SRT format detected — timestamps preserved"
                    } else {
                        "Plain text detected — ready for Editor"
                    },
                    backgroundColor = if (isSrtDetected) colors.goodBg else colors.soft,
                    textColor = if (isSrtDetected) colors.good else colors.softText
                )
            }

            PrimaryActionButton(
                onClick = { onOpenInEditor(text, title.ifBlank { "Imported Transcript" }) },
                enabled = text.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("run_hinglish_conversion_btn")
            ) {
                Text(
                    text = "Open in Editor",
                    style = TranscribeTextStyles.ButtonLabel
                )
            }
        }
    }
}

@Composable
fun UsageModalDialog(
    usage: DailyUsage,
    providerSummary: String,
    onDismiss: () -> Unit,
    onReset: () -> Unit,
    onOpenAiStudioQuota: () -> Unit
) {
    val colors = TranscribeThemeTokens.colors
    val numberFmt = remember { NumberFormat.getIntegerInstance(Locale.US) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 18.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(colors.card)
                .border(1.dp, colors.line, RoundedCornerShape(28.dp))
                .padding(start = 18.dp, end = 18.dp, top = 20.dp, bottom = 24.dp)
                .testTag("usage_modal")
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(13.dp))
                            .background(colors.soft),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Bolt,
                            contentDescription = null,
                            tint = colors.accentText,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Daily Usage",
                            style = TranscribeTextStyles.ScreenTitle,
                            color = colors.text
                        )
                        Text(
                            text = "Resets automatically at midnight",
                            style = TranscribeTextStyles.SubText,
                            color = colors.muted
                        )
                    }
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("close_usage_modal")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.cd_close),
                        tint = colors.muted
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MetricBox(
                    label = "REQUESTS TODAY",
                    value = numberFmt.format(usage.requestsToday),
                    modifier = Modifier.weight(1f)
                )
                MetricBox(
                    label = "TOKENS USED",
                    value = numberFmt.format(usage.tokensToday),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Active: $providerSummary",
                style = TranscribeTextStyles.SubText,
                color = colors.muted
            )

            Spacer(modifier = Modifier.height(16.dp))

            SecondaryActionButton(
                onClick = onOpenAiStudioQuota,
                borderColor = colors.line,
                contentColor = colors.accentText,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("open_ai_studio_quota_btn")
            ) {
                Text(
                    text = "View Official Quota in AI Studio",
                    color = colors.accentText,
                    style = TranscribeTextStyles.ButtonLabel
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                    contentDescription = null,
                    tint = colors.accentText,
                    modifier = Modifier.size(16.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onReset,
                    modifier = Modifier
                        .heightIn(min = 44.dp)
                        .testTag("reset_usage_btn")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = null,
                        tint = colors.muted,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Reset Counter",
                        style = TranscribeTextStyles.ListRow,
                        color = colors.muted
                    )
                }

                Box(
                    modifier = Modifier
                        .height(40.dp)
                        .clip(RoundedCornerShape(99.dp))
                        .background(colors.accent)
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 22.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Done",
                        style = TranscribeTextStyles.ListRow,
                        color = colors.onAccent
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricBox(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    val colors = TranscribeThemeTokens.colors
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(colors.card2)
            .border(1.dp, colors.line, RoundedCornerShape(18.dp))
            .padding(16.dp)
    ) {
        SectionLabel(text = label)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = value,
            style = TranscribeTextStyles.BigStatValue,
            color = colors.text
        )
    }
}

@Composable
fun ExpiryPickerDialog(
    currentOption: RetentionOption,
    onDismiss: () -> Unit,
    onSelect: (RetentionOption) -> Unit
) {
    val colors = TranscribeThemeTokens.colors

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.card,
        titleContentColor = colors.text,
        shape = RoundedCornerShape(24.dp),
        title = {
            Text(
                text = "Auto-Delete Timer",
                style = TranscribeTextStyles.ScreenTitle,
                color = colors.text
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Choose how long this project and its local audio stay on your device.",
                    style = TranscribeTextStyles.SubText,
                    color = colors.muted
                )
                Spacer(modifier = Modifier.height(4.dp))
                RetentionOption.entries.forEach { option ->
                    val isSelected = option == currentOption
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isSelected) colors.soft else colors.card2)
                            .border(
                                width = 1.dp,
                                color = if (isSelected) colors.accent else colors.line,
                                shape = RoundedCornerShape(16.dp)
                            )
                            .clickable {
                                onSelect(option)
                                onDismiss()
                            }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = option.label,
                            style = TranscribeTextStyles.ListRow,
                            color = if (isSelected) colors.softText else colors.text
                        )
                        if (isSelected) {
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
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", style = TranscribeTextStyles.ListRow, color = colors.muted)
            }
        }
    )
}

@Composable
fun MissingApiKeyDialog(
    provider: AiProvider,
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val colors = TranscribeThemeTokens.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.card,
        titleContentColor = colors.text,
        textContentColor = colors.muted,
        shape = RoundedCornerShape(24.dp),
        icon = {
            Icon(
                imageVector = Icons.Outlined.Key,
                contentDescription = null,
                tint = colors.accent
            )
        },
        title = {
            Text(
                text = "API Key Required",
                style = TranscribeTextStyles.ScreenTitle,
                color = colors.text
            )
        },
        text = {
            Text(
                text = "Add your ${provider.displayName} API key in Settings to run AI transcription, Hinglish conversion, or Magic tools. Offline editing and audio downloads work without a key.",
                style = TranscribeTextStyles.ListRow,
                color = colors.muted
            )
        },
        confirmButton = {
            Box(
                modifier = Modifier
                    .height(40.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(colors.accent)
                    .clickable {
                        onDismiss()
                        onOpenSettings()
                    }
                    .padding(horizontal = 18.dp)
                    .testTag("prompt_open_settings_btn"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Configure Key",
                    style = TranscribeTextStyles.ListRow,
                    color = colors.onAccent
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Not Now", style = TranscribeTextStyles.ListRow, color = colors.muted)
            }
        }
    )
}

@Composable
fun RenameProjectDialog(
    currentTitle: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val colors = TranscribeThemeTokens.colors
    var draft by remember(currentTitle) { mutableStateOf(currentTitle) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.card,
        titleContentColor = colors.text,
        shape = RoundedCornerShape(24.dp),
        title = {
            Text(
                text = "Rename Project",
                style = TranscribeTextStyles.ScreenTitle,
                color = colors.text
            )
        },
        text = {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                placeholder = {
                    Text("Project name", style = TranscribeTextStyles.SubText, color = colors.muted)
                },
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
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
                    .testTag("rename_project_input")
            )
        },
        confirmButton = {
            Box(
                modifier = Modifier
                    .height(40.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(if (draft.isNotBlank()) colors.accent else colors.card2)
                    .clickable(enabled = draft.isNotBlank()) { onConfirm(draft.trim()) }
                    .padding(horizontal = 18.dp)
                    .testTag("rename_project_confirm_btn"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Save",
                    style = TranscribeTextStyles.ListRow,
                    color = if (draft.isNotBlank()) colors.onAccent else colors.muted
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", style = TranscribeTextStyles.ListRow, color = colors.muted)
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportBottomSheet(
    title: String,
    supportNote: String,
    upiId: String,
    supportUrl: String,
    supportUrlLabel: String,
    onDismiss: () -> Unit,
    onCopyUpi: () -> Unit,
    onOpenSupportLink: () -> Unit
) {
    val colors = TranscribeThemeTokens.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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
                .padding(start = 18.dp, end = 18.dp, top = 20.dp, bottom = 24.dp)
                .testTag("support_bottom_sheet"),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title.ifBlank { "Support the Creator" },
                    style = TranscribeTextStyles.ScreenTitle,
                    color = colors.text,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(44.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.cd_close),
                        tint = colors.muted
                    )
                }
            }

            if (supportNote.isNotBlank()) {
                Text(
                    text = supportNote,
                    style = TranscribeTextStyles.BodyTranscript,
                    color = colors.muted
                )
            }

            // UPI ID row with 1-tap Copy button (only if provided)
            if (upiId.isNotBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(colors.card2)
                        .border(1.dp, colors.line, RoundedCornerShape(18.dp))
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        SectionLabel(text = "UPI ID")
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = upiId,
                            style = TranscribeTextStyles.CardTitle,
                            color = colors.text
                        )
                    }
                    Box(
                        modifier = Modifier
                            .height(40.dp)
                            .clip(RoundedCornerShape(99.dp))
                            .background(colors.accent)
                            .clickable(onClick = onCopyUpi)
                            .padding(horizontal = 16.dp)
                            .testTag("copy_upi_btn"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Copy",
                            style = TranscribeTextStyles.ListRow,
                            color = colors.onAccent
                        )
                    }
                }
            }

            if (supportUrl.isNotBlank()) {
                SecondaryActionButton(
                    onClick = onOpenSupportLink,
                    borderColor = colors.accent,
                    contentColor = colors.accentText,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("open_support_feedback_btn")
                ) {
                    Text(
                        text = supportUrlLabel.ifBlank { "Send Feedback or Support" },
                        style = TranscribeTextStyles.ButtonLabel,
                        color = colors.accentText
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                        contentDescription = null,
                        tint = colors.accentText,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

/**
 * Remote-config driven Update Popup (--overlay dimmer, --card2 modal).
 * When forced (installed version < minVersion), "Later" is hidden and dismissal is locked.
 */
@Composable
fun UpdateAvailableDialog(
    state: UpdatePromptState,
    onDismissLater: () -> Unit,
    onUpdateNow: (String) -> Unit
) {
    val colors = TranscribeThemeTokens.colors
    Dialog(
        onDismissRequest = {
            if (!state.forced) onDismissLater()
        },
        properties = DialogProperties(
            dismissOnBackPress = !state.forced,
            dismissOnClickOutside = !state.forced,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(colors.card2)
                    .border(1.dp, colors.line, RoundedCornerShape(28.dp))
                    .padding(start = 18.dp, end = 18.dp, top = 20.dp, bottom = 24.dp)
                    .testTag("update_available_dialog"),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(colors.soft),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.SystemUpdate,
                            contentDescription = null,
                            tint = colors.accentText,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = state.title.ifBlank { "Update Available" },
                            style = TranscribeTextStyles.ScreenTitle,
                            color = colors.text
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        StatusChip(
                            text = "${AppConfig.APP_VERSION} → ${state.latestVersion}",
                            backgroundColor = if (state.forced) colors.warnBg else colors.goodBg,
                            textColor = if (state.forced) colors.warn else colors.good
                        )
                    }
                }

                Text(
                    text = if (state.forced) {
                        state.message.ifBlank {
                            "A required update is available to keep ${AppConfig.APP_NAME} running smoothly. Please update now to continue."
                        }
                    } else {
                        state.message.ifBlank {
                            "A newer version of ${AppConfig.APP_NAME} is available with improvements and fixes."
                        }
                    },
                    style = TranscribeTextStyles.BodyTranscript,
                    color = colors.muted
                )

                Spacer(modifier = Modifier.height(4.dp))

                if (state.forced) {
                    PrimaryActionButton(
                        onClick = { onUpdateNow(state.url) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("update_now_btn")
                    ) {
                        Text(
                            text = "Update",
                            style = TranscribeTextStyles.ButtonLabel
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        SecondaryActionButton(
                            onClick = onDismissLater,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("update_later_btn")
                        ) {
                            Text(
                                text = "Later",
                                style = TranscribeTextStyles.ButtonLabel,
                                color = colors.text
                            )
                        }
                        PrimaryActionButton(
                            onClick = { onUpdateNow(state.url) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("update_now_btn")
                        ) {
                            Text(
                                text = "Update",
                                style = TranscribeTextStyles.ButtonLabel
                            )
                        }
                    }
                }
            }
        }
    }
}
