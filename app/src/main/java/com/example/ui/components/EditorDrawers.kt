package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CallMerge
import androidx.compose.material.icons.outlined.CallSplit
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.TextCasing
import com.example.data.model.TranscriptSegment
import com.example.data.srt.SrtEngine
import com.example.ui.theme.TranscribeTextStyles
import com.example.ui.theme.TranscribeThemeTokens
import com.example.ui.theme.withTabularNums
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ToolsDrawerSheet(
    segments: List<TranscriptSegment>,
    removeFillerWords: Boolean,
    stripPunctuation: Boolean,
    textCasing: TextCasing,
    timestampShiftMs: Long,
    isTranslatingEnglish: Boolean,
    isConvertingHinglish: Boolean,
    onDismiss: () -> Unit,
    onTranslateToEnglish: () -> Unit,
    onConvertHinglishOffline: () -> Unit,
    onSmartSpellFixHinglish: () -> Unit,
    onToggleRemoveFillerWords: (Boolean) -> Unit,
    onToggleStripPunctuation: (Boolean) -> Unit,
    onSelectTextCasing: (TextCasing) -> Unit,
    onAdjustTimestampShift: (Long) -> Unit,
    onResetTimestampShift: () -> Unit,
    onReplaceAll: (String, String) -> Unit
) {
    val colors = TranscribeThemeTokens.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var findQuery by remember { mutableStateOf("") }
    var replaceQuery by remember { mutableStateOf("") }
    val matchCount = remember(segments, findQuery) {
        SrtEngine.countMatches(segments, findQuery)
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
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 18.dp, end = 18.dp, top = 20.dp, bottom = 24.dp)
                .testTag("tools_drawer"),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Editing Tools",
                        style = TranscribeTextStyles.ScreenTitle,
                        color = colors.text
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Offline tools preview live on stage and apply to exports",
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

            // 1. Top Action Tiles: Translate to English (Uses API) + Convert to Hinglish (Offline)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ToolActionTile(
                    title = "Translate to English",
                    badgeText = "Uses API",
                    containerColor = colors.accent,
                    borderColor = colors.accent,
                    titleColor = colors.onAccent,
                    badgeBgColor = colors.onAccent.copy(alpha = 0.16f),
                    badgeTextColor = colors.onAccent,
                    loading = isTranslatingEnglish,
                    tag = "tool_translate_english_btn",
                    onClick = {
                        onDismiss()
                        onTranslateToEnglish()
                    },
                    modifier = Modifier.weight(1f)
                )

                ToolActionTile(
                    title = "Convert to Hinglish",
                    badgeText = "Offline",
                    containerColor = colors.card2,
                    borderColor = colors.line,
                    titleColor = colors.text,
                    badgeBgColor = colors.goodBg,
                    badgeTextColor = colors.good,
                    loading = false,
                    tag = "tool_hinglish_offline_btn",
                    onClick = {
                        onConvertHinglishOffline()
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            // Full-width button: AI Spell-Fix & Smart Hinglish (Uses API)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(colors.card2)
                    .border(1.dp, colors.line, RoundedCornerShape(22.dp))
                    .clickable(enabled = !isConvertingHinglish) {
                        onDismiss()
                        onSmartSpellFixHinglish()
                    }
                    .padding(horizontal = 16.dp, vertical = 14.dp)
                    .testTag("tool_smart_hinglish_btn"),
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
                    if (isConvertingHinglish) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = colors.accent
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.AutoAwesome,
                            contentDescription = null,
                            tint = colors.accentText,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "AI Spell-Fix & Smart Hinglish",
                            style = TranscribeTextStyles.CardTitle,
                            color = colors.text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        StatusChip(
                            text = "Uses API",
                            backgroundColor = colors.soft,
                            textColor = colors.softText
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Fixes phonetic English like वाटसेप → WhatsApp while keeping timestamps",
                        style = TranscribeTextStyles.SubText,
                        color = colors.muted
                    )
                }
            }

            // 2. Remove Filler Words
            ToolToggleCard(
                title = "Remove filler words",
                subtitle = "umm, ahh, uh, matlab, मतलब",
                checked = removeFillerWords,
                tag = "remove_fillers_switch",
                onCheckedChange = onToggleRemoveFillerWords
            )

            // 3. Strip Punctuation
            ToolToggleCard(
                title = "Strip punctuation",
                subtitle = "Removes commas, periods, and symbols for clean captions",
                checked = stripPunctuation,
                tag = "strip_punctuation_switch",
                onCheckedChange = onToggleStripPunctuation
            )

            // 4. Text Casing
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(colors.card2)
                    .border(1.dp, colors.line, RoundedCornerShape(22.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SectionLabel(text = "TEXT CASING")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextCasing.entries.forEach { casing ->
                        val selected = casing == textCasing
                        Box(
                            modifier = Modifier
                                .height(40.dp)
                                .clip(RoundedCornerShape(99.dp))
                                .background(if (selected) colors.soft else colors.card)
                                .border(
                                    width = 1.dp,
                                    color = if (selected) colors.accent else colors.line,
                                    shape = RoundedCornerShape(99.dp)
                                )
                                .clickable { onSelectTextCasing(casing) }
                                .padding(horizontal = 16.dp)
                                .testTag("casing_${casing.name.lowercase()}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = casing.label,
                                style = TranscribeTextStyles.ListRow,
                                color = if (selected) colors.softText else colors.muted
                            )
                        }
                    }
                }
            }

            // 5. Timestamp Shift
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(colors.card2)
                    .border(1.dp, colors.line, RoundedCornerShape(22.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SectionLabel(text = "TIMESTAMP SHIFT")
                    val secShift = timestampShiftMs / 1000.0
                    val formattedShift = String.format(Locale.US, "%+.1fs", secShift)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(99.dp))
                                .background(if (timestampShiftMs != 0L) colors.soft else colors.card)
                                .border(
                                    1.dp,
                                    if (timestampShiftMs != 0L) colors.accent else colors.line,
                                    RoundedCornerShape(99.dp)
                                )
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = formattedShift,
                                style = TranscribeTextStyles.SubText.withTabularNums(),
                                color = if (timestampShiftMs != 0L) colors.softText else colors.muted
                            )
                        }
                        if (timestampShiftMs != 0L) {
                            TextButton(onClick = onResetTimestampShift) {
                                Text("Reset", color = colors.muted, style = TranscribeTextStyles.SubText)
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SecondaryActionButton(
                        onClick = { onAdjustTimestampShift(-500L) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("shift_minus_btn")
                    ) {
                        Text(
                            text = "-0.5s",
                            style = TranscribeTextStyles.ButtonLabel.withTabularNums(),
                            color = colors.text
                        )
                    }
                    SecondaryActionButton(
                        onClick = { onAdjustTimestampShift(500L) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("shift_plus_btn")
                    ) {
                        Text(
                            text = "+0.5s",
                            style = TranscribeTextStyles.ButtonLabel.withTabularNums(),
                            color = colors.text
                        )
                    }
                }
            }

            // 6. Find & Replace with live match count + All button
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(colors.card2)
                    .border(1.dp, colors.line, RoundedCornerShape(22.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SectionLabel(text = "FIND & REPLACE")
                    if (findQuery.isNotEmpty()) {
                        StatusChip(
                            text = "$matchCount ${if (matchCount == 1) "match" else "matches"}",
                            backgroundColor = if (matchCount > 0) colors.soft else colors.card,
                            textColor = if (matchCount > 0) colors.softText else colors.muted
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = findQuery,
                        onValueChange = { findQuery = it },
                        placeholder = {
                            Text("Find", style = TranscribeTextStyles.SubText, color = colors.muted)
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
                            .weight(1f)
                            .heightIn(min = 44.dp)
                            .testTag("tools_find_input")
                    )
                    OutlinedTextField(
                        value = replaceQuery,
                        onValueChange = { replaceQuery = it },
                        placeholder = {
                            Text("Replace", style = TranscribeTextStyles.SubText, color = colors.muted)
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
                            .weight(1f)
                            .heightIn(min = 44.dp)
                            .testTag("tools_replace_input")
                    )
                    Box(
                        modifier = Modifier
                            .height(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (findQuery.isNotEmpty() && matchCount > 0) colors.accent else colors.card)
                            .border(
                                1.dp,
                                if (findQuery.isNotEmpty() && matchCount > 0) colors.accent else colors.line,
                                RoundedCornerShape(12.dp)
                            )
                            .clickable(enabled = findQuery.isNotEmpty() && matchCount > 0) {
                                onReplaceAll(findQuery, replaceQuery)
                            }
                            .padding(horizontal = 16.dp)
                            .testTag("tools_replace_all_btn"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "All",
                            style = TranscribeTextStyles.ButtonLabel,
                            color = if (findQuery.isNotEmpty() && matchCount > 0) colors.onAccent else colors.muted
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolActionTile(
    title: String,
    badgeText: String,
    containerColor: Color,
    borderColor: Color,
    titleColor: Color,
    badgeBgColor: Color,
    badgeTextColor: Color,
    loading: Boolean,
    tag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .heightIn(min = 100.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(containerColor)
            .border(1.dp, borderColor, RoundedCornerShape(22.dp))
            .clickable(enabled = !loading, onClick = onClick)
            .padding(16.dp)
            .testTag(tag),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatusChip(
                text = badgeText,
                backgroundColor = badgeBgColor,
                textColor = badgeTextColor
            )
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = titleColor
                )
            } else {
                Icon(
                    imageVector = Icons.Outlined.Translate,
                    contentDescription = null,
                    tint = titleColor.copy(alpha = 0.85f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = title,
            style = TranscribeTextStyles.CardTitle,
            color = titleColor,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ToolToggleCard(
    title: String,
    subtitle: String,
    checked: Boolean,
    tag: String,
    onCheckedChange: (Boolean) -> Unit
) {
    val colors = TranscribeThemeTokens.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(colors.card2)
            .border(1.dp, colors.line, RoundedCornerShape(22.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
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
        AppToggleSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(tag)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportBottomSheet(
    hasTranscript: Boolean,
    onDismiss: () -> Unit,
    onExportSrt: () -> Unit,
    onExportTxt: () -> Unit,
    onCopyText: () -> Unit,
    onShare: () -> Unit
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
                .testTag("export_sheet"),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Export",
                style = TranscribeTextStyles.ScreenTitle,
                color = colors.text
            )
            Text(
                text = "Includes all active formatting, casing, and timestamp shifts",
                style = TranscribeTextStyles.SubText,
                color = colors.muted
            )

            Spacer(modifier = Modifier.height(4.dp))

            ExportOptionRow(
                icon = Icons.Outlined.Subtitles,
                title = ".SRT Subtitles",
                subtitle = "Timestamped cues ready for CapCut, Premiere & YouTube",
                enabled = hasTranscript,
                tag = "export_srt_btn",
                onClick = {
                    onDismiss()
                    onExportSrt()
                }
            )
            ExportOptionRow(
                icon = Icons.Outlined.Description,
                title = ".TXT Transcript",
                subtitle = "Clean plain text document without timestamps",
                enabled = hasTranscript,
                tag = "export_txt_btn",
                onClick = {
                    onDismiss()
                    onExportTxt()
                }
            )
            ExportOptionRow(
                icon = Icons.Outlined.ContentCopy,
                title = "Copy text",
                subtitle = "Copy formatted transcript to clipboard",
                enabled = hasTranscript,
                tag = "copy_text_btn",
                onClick = {
                    onDismiss()
                    onCopyText()
                }
            )
            ExportOptionRow(
                icon = Icons.Outlined.Share,
                title = "Share",
                subtitle = "Send .SRT subtitles via Android Share Sheet",
                enabled = hasTranscript,
                tag = "export_share_btn",
                onClick = {
                    onDismiss()
                    onShare()
                }
            )
        }
    }
}

@Composable
private fun ExportOptionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean,
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
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(if (enabled) colors.soft else colors.card),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = if (enabled) colors.accentText else colors.muted,
                modifier = Modifier.size(20.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = TranscribeTextStyles.CardTitle,
                color = if (enabled) colors.text else colors.muted
            )
            Text(
                text = subtitle,
                style = TranscribeTextStyles.SubText,
                color = colors.muted
            )
        }
    }
}

@Composable
fun SegmentActionsDialog(
    segmentIndex: Int,
    canSplit: Boolean,
    canMergeNext: Boolean,
    onDismiss: () -> Unit,
    onSplit: () -> Unit,
    onMergeNext: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = TranscribeThemeTokens.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.card,
        titleContentColor = colors.text,
        shape = RoundedCornerShape(24.dp),
        title = {
            Text(
                text = "Segment #$segmentIndex",
                style = TranscribeTextStyles.ScreenTitle,
                color = colors.text
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SegmentActionItem(
                    icon = Icons.Outlined.CallSplit,
                    label = "Split segment",
                    enabled = canSplit,
                    tint = colors.accentText,
                    onClick = {
                        onSplit()
                        onDismiss()
                    }
                )
                SegmentActionItem(
                    icon = Icons.Outlined.CallMerge,
                    label = "Merge with next",
                    enabled = canMergeNext,
                    tint = colors.accentText,
                    onClick = {
                        onMergeNext()
                        onDismiss()
                    }
                )
                SegmentActionItem(
                    icon = Icons.Outlined.DeleteOutline,
                    label = "Delete segment",
                    enabled = true,
                    tint = colors.danger,
                    onClick = {
                        onDelete()
                        onDismiss()
                    }
                )
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
private fun SegmentActionItem(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    tint: Color,
    onClick: () -> Unit
) {
    val colors = TranscribeThemeTokens.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.card2)
            .border(1.dp, colors.line, RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) tint else colors.muted.copy(alpha = 0.5f),
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = label,
            style = TranscribeTextStyles.ListRow.copy(fontWeight = FontWeight.Medium),
            color = if (enabled) (if (tint == colors.danger) colors.danger else colors.text) else colors.muted.copy(alpha = 0.5f)
        )
    }
}
