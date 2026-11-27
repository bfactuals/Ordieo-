package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.VideoFile
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.media.MediaCompressor
import com.example.data.media.VideoSplitter
import com.example.data.model.CustomDurationUnit
import com.example.data.model.SplitDurationPreset
import com.example.data.model.SplitPartItem
import com.example.data.model.SplitPartStatus
import com.example.data.model.SplitUiState
import com.example.data.model.VideoCompressPreset
import com.example.data.srt.SrtEngine
import com.example.ui.components.AppToggleSwitch
import com.example.ui.components.PrimaryActionButton
import com.example.ui.components.SecondaryActionButton
import com.example.ui.components.SectionLabel
import com.example.ui.components.StatusChip
import com.example.ui.theme.TranscribeTextStyles
import com.example.ui.theme.TranscribeThemeTokens
import com.example.ui.theme.withTabularNums
import kotlin.math.roundToInt

/**
 * LazyColumn items for the Split tab inside CompressScreen.
 * Uses virtualized LazyListScope.items for the parts list so 60+ minute videos stay smooth.
 */
fun LazyListScope.splitTabItems(
    splitState: SplitUiState,
    onPickVideo: () -> Unit,
    onSelectDurationPreset: (SplitDurationPreset) -> Unit,
    onChangeCustomDuration: (String) -> Unit,
    onSelectCustomUnit: (CustomDurationUnit) -> Unit,
    onTogglePartSelected: (Int) -> Unit,
    onToggleSelectAll: () -> Unit,
    onToggleCompressWhileExporting: (Boolean) -> Unit,
    onSelectVideoPreset: (VideoCompressPreset) -> Unit,
    onToggleFixHdr: (Boolean) -> Unit,
    onSkipCurrentPart: () -> Unit,
    onSkipQueuedPart: (Int) -> Unit,
    onCancelAll: () -> Unit,
    onSharePart: (SplitPartItem) -> Unit
) {
    val video = splitState.selectedVideo

    // 1. Pick Video / Selected Video Info Card
    item(key = "split_file_card") {
        val colors = TranscribeThemeTokens.colors
        if (video == null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(colors.card)
                    .border(1.dp, colors.line, RoundedCornerShape(22.dp))
                    .clickable(enabled = !splitState.isExporting, onClick = onPickVideo)
                    .padding(20.dp)
                    .testTag("split_pick_video_card"),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(colors.soft),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ContentCut,
                        contentDescription = null,
                        tint = colors.accentText,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Text(
                    text = "Select a Video to Split",
                    style = TranscribeTextStyles.CardTitle,
                    color = colors.text
                )
                Text(
                    text = "Cut long videos (18–60+ min) into 1-min, 2-min, 5-min, or custom parts",
                    style = TranscribeTextStyles.SubText,
                    color = colors.muted
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(colors.card)
                    .border(1.dp, colors.line, RoundedCornerShape(22.dp))
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(colors.soft),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.VideoFile,
                        contentDescription = null,
                        tint = colors.accentText,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = video.displayName,
                        style = TranscribeTextStyles.CardTitle,
                        color = colors.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    val meta = buildList {
                        if (video.width > 0 && video.height > 0) {
                            add("${video.width}×${video.height}")
                        }
                        add(SrtEngine.formatDurationShort(video.durationMs))
                        if (video.sizeBytes > 0L) {
                            add(MediaCompressor.formatBytes(video.sizeBytes))
                        }
                    }.joinToString(" · ")
                    Text(
                        text = meta,
                        style = TranscribeTextStyles.SubText.withTabularNums(),
                        color = colors.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Box(
                    modifier = Modifier
                        .height(40.dp)
                        .clip(RoundedCornerShape(99.dp))
                        .background(colors.card2)
                        .border(1.dp, colors.line, RoundedCornerShape(99.dp))
                        .clickable(enabled = !splitState.isExporting, onClick = onPickVideo)
                        .padding(horizontal = 16.dp)
                        .testTag("split_change_video_btn"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Change",
                        style = TranscribeTextStyles.ListRow,
                        color = if (splitState.isExporting) colors.muted else colors.text
                    )
                }
            }
        }
    }

    // 2. Split Duration Chips (1 min, 2 min, 5 min, Custom)
    item(key = "split_duration_section") {
        val colors = TranscribeThemeTokens.colors
        Column(modifier = Modifier.fillMaxWidth()) {
            SectionLabel(text = "SPLIT DURATION")
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SplitDurationPreset.entries.forEach { preset ->
                    val selected = preset == splitState.durationPreset
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(99.dp))
                            .background(if (selected) colors.accent else colors.card)
                            .border(
                                width = 1.dp,
                                color = if (selected) colors.accent else colors.line,
                                shape = RoundedCornerShape(99.dp)
                            )
                            .clickable(enabled = !splitState.isExporting) {
                                onSelectDurationPreset(preset)
                            }
                            .testTag("split_duration_${preset.name.lowercase()}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = preset.label,
                            style = TranscribeTextStyles.ListRow.copy(
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                            ),
                            color = if (selected) colors.onAccent else colors.text,
                            maxLines = 1
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = splitState.durationPreset == SplitDurationPreset.CUSTOM,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = splitState.customDurationInput,
                        onValueChange = onChangeCustomDuration,
                        enabled = !splitState.isExporting,
                        singleLine = true,
                        placeholder = {
                            Text(
                                text = "e.g. 30",
                                style = TranscribeTextStyles.SubText,
                                color = colors.muted
                            )
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        textStyle = TranscribeTextStyles.ListRow.withTabularNums().copy(color = colors.text),
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
                            .testTag("split_custom_duration_input")
                    )

                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(99.dp))
                            .background(colors.card)
                            .border(1.dp, colors.line, RoundedCornerShape(99.dp))
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        CustomDurationUnit.entries.forEach { unit ->
                            val selected = unit == splitState.customDurationUnit
                            Box(
                                modifier = Modifier
                                    .height(38.dp)
                                    .clip(RoundedCornerShape(99.dp))
                                    .background(if (selected) colors.accent else Color.Transparent)
                                    .clickable(enabled = !splitState.isExporting) {
                                        onSelectCustomUnit(unit)
                                    }
                                    .padding(horizontal = 16.dp)
                                    .testTag("split_custom_unit_${unit.name.lowercase()}"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = unit.label,
                                    style = TranscribeTextStyles.ListRow.copy(
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                                    ),
                                    color = if (selected) colors.onAccent else colors.muted
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // 3. Compress While Exporting Toggle (Default OFF) + Presets when ON
    item(key = "split_compress_toggle") {
        val colors = TranscribeThemeTokens.colors
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(colors.card)
                    .border(1.dp, colors.line, RoundedCornerShape(22.dp))
                    .clickable(enabled = !splitState.isExporting) {
                        onToggleCompressWhileExporting(!splitState.compressWhileExporting)
                    }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(
                        text = "Compress while exporting",
                        style = TranscribeTextStyles.CardTitle,
                        color = colors.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (splitState.compressWhileExporting) {
                            "Hardware Media3 H.264 transcode per part"
                        } else {
                            "Instant lossless stream copy (no re-encoding)"
                        },
                        style = TranscribeTextStyles.SubText,
                        color = colors.muted
                    )
                }
                AppToggleSwitch(
                    checked = splitState.compressWhileExporting,
                    onCheckedChange = { if (!splitState.isExporting) onToggleCompressWhileExporting(it) },
                    modifier = Modifier.testTag("split_compress_switch")
                )
            }

            AnimatedVisibility(
                visible = splitState.compressWhileExporting,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SectionLabel(text = "VIDEO PRESETS")
                    VideoCompressPreset.entries.forEach { preset ->
                        val selected = preset == splitState.videoPreset
                        val estPerPart = video?.let {
                            val chunkInfo = it.copy(durationMs = splitState.effectiveChunkDurationMs)
                            "~${MediaCompressor.formatBytes(MediaCompressor.estimateVideoBytes(chunkInfo, preset))}/part"
                        }
                        PresetRadioCard(
                            title = preset.title,
                            subtitle = preset.subtitle,
                            estimateText = estPerPart,
                            selected = selected,
                            enabled = !splitState.isExporting,
                            tag = "split_video_preset_${preset.name.lowercase()}",
                            onClick = { onSelectVideoPreset(preset) }
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(22.dp))
                            .background(colors.card)
                            .border(1.dp, colors.line, RoundedCornerShape(22.dp))
                            .clickable(enabled = !splitState.isExporting) {
                                onToggleFixHdr(!splitState.fixHdrColors)
                            }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(
                                text = "Fix HDR colours",
                                style = TranscribeTextStyles.CardTitle,
                                color = colors.text,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "OpenGL HDR-to-SDR tone-mapping per part",
                                style = TranscribeTextStyles.SubText,
                                color = colors.muted
                            )
                        }
                        AppToggleSwitch(
                            checked = splitState.fixHdrColors,
                            onCheckedChange = { if (!splitState.isExporting) onToggleFixHdr(it) },
                            modifier = Modifier.testTag("split_fix_hdr_switch")
                        )
                    }
                }
            }
        }
    }

    // 4. Live Sequential Export Progress Card (while running)
    if (splitState.isExporting) {
        item(key = "split_export_progress") {
            SplitLiveProgressCard(
                splitState = splitState,
                onSkipCurrentPart = onSkipCurrentPart,
                onCancelAll = onCancelAll
            )
        }
    }

    // 5. Selectable Parts List
    if (splitState.parts.isNotEmpty()) {
        item(key = "split_parts_header") {
            val colors = TranscribeThemeTokens.colors
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionLabel(text = "PARTS (${splitState.selectedCount}/${splitState.parts.size})")
                if (!splitState.isExporting) {
                    Box(
                        modifier = Modifier
                            .height(34.dp)
                            .clip(RoundedCornerShape(99.dp))
                            .background(colors.soft)
                            .clickable(onClick = onToggleSelectAll)
                            .padding(horizontal = 12.dp)
                            .testTag("split_select_all_btn"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (splitState.allSelected) "Deselect All" else "Select All",
                            style = TranscribeTextStyles.SubText.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.softText
                        )
                    }
                }
            }
        }

        items(splitState.parts, key = { "split_part_${it.index}" }) { part ->
            SplitPartRowCard(
                part = part,
                isExportingQueue = splitState.isExporting,
                onToggleSelected = { onTogglePartSelected(part.index) },
                onSkipPart = { onSkipQueuedPart(part.index) },
                onSharePart = { onSharePart(part) }
            )
        }
    }
}

@Composable
private fun SplitLiveProgressCard(
    splitState: SplitUiState,
    onSkipCurrentPart: () -> Unit,
    onCancelAll: () -> Unit
) {
    val colors = TranscribeThemeTokens.colors
    val activeIdx = splitState.currentExportingPartIndex
    val pct = (splitState.currentPartProgress * 100f).roundToInt().coerceIn(0, 100)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(colors.card2)
            .border(1.dp, colors.accent, RoundedCornerShape(22.dp))
            .padding(16.dp)
            .testTag("split_live_progress_card"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Exporting Part ${splitState.currentExportOrdinal} of ${splitState.totalQueuedCount}...",
                    style = TranscribeTextStyles.CardTitle.withTabularNums(),
                    color = colors.text
                )
                val detail = buildString {
                    if (activeIdx != null) append("Part $activeIdx · ")
                    append(splitState.currentStatusText.ifBlank { "Working..." })
                }
                Text(
                    text = detail,
                    style = TranscribeTextStyles.SubText,
                    color = colors.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = "$pct%",
                style = TranscribeTextStyles.CardTitle.withTabularNums(),
                color = colors.accentText
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(colors.card)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(splitState.currentPartProgress.coerceIn(0.03f, 1f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(99.dp))
                    .background(colors.accent)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SecondaryActionButton(
                onClick = onSkipCurrentPart,
                modifier = Modifier
                    .weight(1f)
                    .testTag("split_skip_current_btn")
            ) {
                Icon(
                    imageVector = Icons.Rounded.SkipNext,
                    contentDescription = null,
                    tint = colors.text,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Skip this part",
                    style = TranscribeTextStyles.ButtonLabel,
                    color = colors.text,
                    maxLines = 1
                )
            }

            SecondaryActionButton(
                onClick = onCancelAll,
                borderColor = colors.danger,
                contentColor = colors.danger,
                modifier = Modifier
                    .weight(1f)
                    .testTag("split_cancel_all_btn")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = null,
                    tint = colors.danger,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Cancel All",
                    style = TranscribeTextStyles.ButtonLabel,
                    color = colors.danger,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun SplitPartRowCard(
    part: SplitPartItem,
    isExportingQueue: Boolean,
    onToggleSelected: () -> Unit,
    onSkipPart: () -> Unit,
    onSharePart: () -> Unit
) {
    val colors = TranscribeThemeTokens.colors
    val borderColor = when (part.status) {
        SplitPartStatus.EXPORTING -> colors.accent
        SplitPartStatus.SAVED -> colors.good.copy(alpha = 0.55f)
        SplitPartStatus.FAILED -> colors.danger.copy(alpha = 0.65f)
        else -> if (part.selected && !isExportingQueue) colors.line else colors.line
    }
    val bgColor = when (part.status) {
        SplitPartStatus.EXPORTING -> colors.card2
        else -> colors.card
    }

    val rangeText = "${VideoSplitter.formatRangeTimestamp(part.startMs)} – ${VideoSplitter.formatRangeTimestamp(part.endMs)}"
    val subLine = when {
        part.status == SplitPartStatus.SAVED && part.outputSizeBytes > 0L ->
            "$rangeText · ${MediaCompressor.formatBytes(part.outputSizeBytes)}"
        part.status == SplitPartStatus.FAILED && !part.errorMessage.isNullOrBlank() ->
            "$rangeText · ${part.errorMessage}"
        else -> rangeText
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(18.dp))
            .clickable(enabled = !isExportingQueue, onClick = onToggleSelected)
            .padding(start = 14.dp, end = 10.dp, top = 12.dp, bottom = 12.dp)
            .testTag("split_part_row_${part.index}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Custom flat checkbox (44dp touch alignment via clickable row + crisp 22dp indicator)
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(if (part.selected) colors.accent else Color.Transparent)
                .border(
                    width = 1.5.dp,
                    color = if (part.selected) colors.accent else colors.muted,
                    shape = RoundedCornerShape(7.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (part.selected) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = "Selected",
                    tint = colors.onAccent,
                    modifier = Modifier.size(15.dp)
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Part ${part.index}",
                style = TranscribeTextStyles.CardTitle.withTabularNums(),
                color = colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subLine,
                style = TranscribeTextStyles.SubText.withTabularNums(),
                color = if (part.status == SplitPartStatus.FAILED) colors.danger else colors.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Status chip
        when (part.status) {
            SplitPartStatus.SAVED -> {
                StatusChip(
                    text = "Saved",
                    backgroundColor = colors.goodBg,
                    textColor = colors.good,
                    borderColor = colors.good.copy(alpha = 0.45f)
                )
            }
            SplitPartStatus.EXPORTING -> {
                StatusChip(
                    text = "${(part.progress * 100f).roundToInt().coerceIn(0, 100)}%",
                    backgroundColor = colors.soft,
                    textColor = colors.accentText
                )
            }
            SplitPartStatus.QUEUED -> {
                StatusChip(
                    text = "Queued",
                    backgroundColor = colors.card2,
                    textColor = colors.muted
                )
            }
            SplitPartStatus.SKIPPED -> {
                StatusChip(
                    text = "Skipped",
                    backgroundColor = colors.card2,
                    textColor = colors.muted
                )
            }
            SplitPartStatus.FAILED -> {
                StatusChip(
                    text = "Failed",
                    backgroundColor = colors.card2,
                    textColor = colors.danger,
                    borderColor = colors.danger.copy(alpha = 0.55f)
                )
            }
            SplitPartStatus.CANCELLED -> {
                StatusChip(
                    text = "Cancelled",
                    backgroundColor = colors.card2,
                    textColor = colors.muted
                )
            }
            SplitPartStatus.IDLE -> Unit
        }

        // Inline Skip button for upcoming queued (or active) parts while running
        if (isExportingQueue && (part.status == SplitPartStatus.QUEUED || part.status == SplitPartStatus.EXPORTING)) {
            IconButton(
                onClick = onSkipPart,
                modifier = Modifier
                    .size(44.dp)
                    .testTag("split_skip_part_${part.index}")
            ) {
                Icon(
                    imageVector = Icons.Rounded.SkipNext,
                    contentDescription = "Skip Part ${part.index}",
                    tint = colors.muted,
                    modifier = Modifier.size(20.dp)
                )
            }
        } else if (part.status == SplitPartStatus.SAVED && !part.savedUriString.isNullOrBlank()) {
            IconButton(
                onClick = onSharePart,
                modifier = Modifier
                    .size(44.dp)
                    .testTag("split_share_part_${part.index}")
            ) {
                Icon(
                    imageVector = Icons.Outlined.Share,
                    contentDescription = "Share Part ${part.index}",
                    tint = colors.accentText,
                    modifier = Modifier.size(19.dp)
                )
            }
        }
    }
}

@Composable
internal fun SplitBottomActionBar(
    splitState: SplitUiState,
    onStartExport: () -> Unit,
    onSkipCurrentPart: () -> Unit,
    onCancelAll: () -> Unit,
    onShareAllSaved: () -> Unit
) {
    val colors = TranscribeThemeTokens.colors
    val savedCount = splitState.savedParts.size

    if (splitState.isExporting) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SecondaryActionButton(
                onClick = onSkipCurrentPart,
                modifier = Modifier
                    .weight(1f)
                    .testTag("split_bottom_skip_btn")
            ) {
                Icon(
                    imageVector = Icons.Rounded.SkipNext,
                    contentDescription = null,
                    tint = colors.text,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Skip this part", style = TranscribeTextStyles.ButtonLabel, color = colors.text)
            }
            SecondaryActionButton(
                onClick = onCancelAll,
                borderColor = colors.danger,
                contentColor = colors.danger,
                modifier = Modifier
                    .weight(1f)
                    .testTag("split_bottom_cancel_all_btn")
            ) {
                Text("Cancel All", style = TranscribeTextStyles.ButtonLabel, color = colors.danger)
            }
        }
    } else if (savedCount > 0) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SecondaryActionButton(
                onClick = onShareAllSaved,
                modifier = Modifier
                    .weight(0.95f)
                    .testTag("split_share_all_btn")
            ) {
                Icon(
                    imageVector = Icons.Outlined.Share,
                    contentDescription = null,
                    tint = colors.text,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Share ($savedCount)",
                    style = TranscribeTextStyles.ButtonLabel.withTabularNums(),
                    color = colors.text,
                    maxLines = 1
                )
            }
            PrimaryActionButton(
                onClick = onStartExport,
                enabled = splitState.selectedVideo != null && splitState.selectedCount > 0,
                modifier = Modifier
                    .weight(1.25f)
                    .testTag("split_export_selected_btn")
            ) {
                Icon(
                    imageVector = Icons.Outlined.ContentCut,
                    contentDescription = null,
                    tint = if (splitState.selectedVideo != null && splitState.selectedCount > 0) {
                        colors.onAccent
                    } else colors.muted,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Export (${splitState.selectedCount})",
                    style = TranscribeTextStyles.ButtonLabel.withTabularNums(),
                    maxLines = 1
                )
            }
        }
    } else {
        val canExport = splitState.selectedVideo != null && splitState.selectedCount > 0
        val label = if (splitState.selectedCount > 0) {
            val noun = if (splitState.selectedCount == 1) "part" else "parts"
            "Export Selected (${splitState.selectedCount} $noun)"
        } else {
            "Export Selected"
        }
        PrimaryActionButton(
            onClick = onStartExport,
            enabled = canExport,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("split_export_selected_btn")
        ) {
            Icon(
                imageVector = Icons.Outlined.ContentCut,
                contentDescription = null,
                tint = if (canExport) colors.onAccent else colors.muted,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                style = TranscribeTextStyles.ButtonLabel.withTabularNums()
            )
        }
    }
}
