package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.VideoFile
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.media.MediaCompressor
import com.example.data.model.AudioCompressPreset
import com.example.data.model.CompressMediaType
import com.example.data.model.CompressUiState
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

@Composable
fun CompressScreen(
    state: CompressUiState,
    onBack: () -> Unit,
    onSelectMediaType: (CompressMediaType) -> Unit,
    onPickFile: () -> Unit,
    onSelectVideoPreset: (VideoCompressPreset) -> Unit,
    onSelectAudioPreset: (AudioCompressPreset) -> Unit,
    onToggleFixHdr: (Boolean) -> Unit,
    onStartCompress: () -> Unit,
    onCancelCompress: () -> Unit,
    onSaveDownload: () -> Unit,
    onShareResult: () -> Unit,
    onUseForTranscription: () -> Unit
) {
    BackHandler {
        if (state.isCompressing) onCancelCompress() else onBack()
    }

    val colors = TranscribeThemeTokens.colors
    val media = state.selectedMedia

    val estimatedBytes = if (media != null) {
        when (state.mediaType) {
            CompressMediaType.VIDEO -> MediaCompressor.estimateVideoBytes(media, state.videoPreset)
            CompressMediaType.AUDIO -> MediaCompressor.estimateAudioBytes(media, state.audioPreset)
        }
    } else 0L

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .widthIn(max = 760.dp)
                .align(Alignment.TopCenter)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 10.dp, end = 18.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                IconButton(
                    onClick = {
                        if (state.isCompressing) onCancelCompress() else onBack()
                    },
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("compress_back_btn")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.cd_back),
                        tint = colors.text
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Compress",
                        style = TranscribeTextStyles.ScreenTitle,
                        color = colors.text
                    )
                    Text(
                        text = "100% on-device native media compression",
                        style = TranscribeTextStyles.SubText,
                        color = colors.muted
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Video | Audio Segment Control
                item(key = "compress_segment") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(99.dp))
                            .background(colors.card)
                            .border(1.dp, colors.line, RoundedCornerShape(99.dp))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        CompressMediaType.entries.forEach { type ->
                            val selected = type == state.mediaType
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp)
                                    .clip(RoundedCornerShape(99.dp))
                                    .background(if (selected) colors.accent else Color.Transparent)
                                    .clickable(enabled = !state.isCompressing) { onSelectMediaType(type) }
                                    .testTag("compress_type_${type.name.lowercase()}"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = type.label,
                                    style = TranscribeTextStyles.ListRow.copy(
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                                    ),
                                    color = if (selected) colors.onAccent else colors.muted
                                )
                            }
                        }
                    }
                }

                // File Info Card or Pick File Card
                item(key = "compress_file_card") {
                    if (media == null) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(22.dp))
                                .background(colors.card)
                                .border(1.dp, colors.line, RoundedCornerShape(22.dp))
                                .clickable(enabled = !state.isCompressing, onClick = onPickFile)
                                .padding(20.dp)
                                .testTag("compress_pick_file_card"),
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
                                    imageVector = if (state.mediaType == CompressMediaType.VIDEO) {
                                        Icons.Outlined.VideoFile
                                    } else {
                                        Icons.Outlined.AudioFile
                                    },
                                    contentDescription = null,
                                    tint = colors.accentText,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Text(
                                text = if (state.mediaType == CompressMediaType.VIDEO) {
                                    "Select a Video to Compress"
                                } else {
                                    "Select an Audio File to Compress"
                                },
                                style = TranscribeTextStyles.CardTitle,
                                color = colors.text
                            )
                            Text(
                                text = if (state.mediaType == CompressMediaType.VIDEO) {
                                    "Shrink large videos to 2–3 MB for fast AI uploads & fix HDR colours"
                                } else {
                                    "Convert to Mono 16 kHz WAV for AI or compact M4A"
                                },
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
                                    imageVector = if (state.mediaType == CompressMediaType.VIDEO) {
                                        Icons.Outlined.VideoFile
                                    } else {
                                        Icons.Outlined.AudioFile
                                    },
                                    contentDescription = null,
                                    tint = colors.accentText,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = media.displayName,
                                    style = TranscribeTextStyles.CardTitle,
                                    color = colors.text,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                val detailParts = buildList {
                                    if (media.width > 0 && media.height > 0) {
                                        add("${media.width}×${media.height}")
                                    }
                                    add(SrtEngine.formatDurationShort(media.durationMs))
                                    if (media.sizeBytes > 0L) {
                                        add(MediaCompressor.formatBytes(media.sizeBytes))
                                    }
                                }.joinToString(" · ")
                                Text(
                                    text = detailParts,
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
                                    .clickable(enabled = !state.isCompressing, onClick = onPickFile)
                                    .padding(horizontal = 16.dp)
                                    .testTag("compress_change_file_btn"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Change",
                                    style = TranscribeTextStyles.ListRow,
                                    color = colors.text
                                )
                            }
                        }
                    }
                }

                // Presets Section (strict 8dp below SectionLabel, 10dp between cards)
                if (state.mediaType == CompressMediaType.VIDEO) {
                    item(key = "video_presets") {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            SectionLabel(text = "VIDEO PRESETS")
                            Spacer(modifier = Modifier.height(8.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                VideoCompressPreset.entries.forEach { preset ->
                                    val selected = preset == state.videoPreset
                                    val estLabel = media?.let {
                                        "~${MediaCompressor.formatBytes(MediaCompressor.estimateVideoBytes(it, preset))}"
                                    }
                                    PresetRadioCard(
                                        title = preset.title,
                                        subtitle = preset.subtitle,
                                        estimateText = estLabel,
                                        selected = selected,
                                        enabled = !state.isCompressing,
                                        tag = "video_preset_${preset.name.lowercase()}",
                                        onClick = { onSelectVideoPreset(preset) }
                                    )
                                }
                            }
                        }
                    }

                    // Fix HDR colours Switch (default ON)
                    item(key = "fix_hdr_switch") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(22.dp))
                                .background(colors.card)
                                .border(1.dp, colors.line, RoundedCornerShape(22.dp))
                                .clickable(enabled = !state.isCompressing) { onToggleFixHdr(!state.fixHdrColors) }
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
                                    text = "Re-encode to standard SDR BT.709 to prevent washed-out video",
                                    style = TranscribeTextStyles.SubText,
                                    color = colors.muted
                                )
                            }
                            AppToggleSwitch(
                                checked = state.fixHdrColors,
                                onCheckedChange = { if (!state.isCompressing) onToggleFixHdr(it) },
                                modifier = Modifier.testTag("fix_hdr_switch")
                            )
                        }
                    }
                } else {
                    item(key = "audio_presets") {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            SectionLabel(text = "AUDIO PRESETS")
                            Spacer(modifier = Modifier.height(8.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                AudioCompressPreset.entries.forEach { preset ->
                                    val selected = preset == state.audioPreset
                                    val estLabel = media?.let {
                                        "~${MediaCompressor.formatBytes(MediaCompressor.estimateAudioBytes(it, preset))}"
                                    }
                                    PresetRadioCard(
                                        title = preset.title,
                                        subtitle = preset.subtitle,
                                        estimateText = estLabel,
                                        selected = selected,
                                        enabled = !state.isCompressing,
                                        tag = "audio_preset_${preset.name.lowercase()}",
                                        onClick = { onSelectAudioPreset(preset) }
                                    )
                                }
                            }
                        }
                    }
                }

                // Before -> After Visual Bar Card
                if (media != null && media.sizeBytes > 0L) {
                    item(key = "before_after_card") {
                        val afterBytes = state.result?.outputSizeBytes ?: estimatedBytes
                        val ratio = (afterBytes.toFloat() / media.sizeBytes.coerceAtLeast(1L).toFloat())
                            .coerceIn(0.05f, 1f)
                        val savedPct = ((1f - ratio) * 100f).roundToInt().coerceIn(0, 99)
                        val prefix = if (state.result != null) "" else "~"

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
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${MediaCompressor.formatBytes(media.sizeBytes)} → $prefix${MediaCompressor.formatBytes(afterBytes)}",
                                    style = TranscribeTextStyles.CardTitle.withTabularNums(),
                                    color = colors.text
                                )
                                StatusChip(
                                    text = "$savedPct% smaller",
                                    backgroundColor = colors.goodBg,
                                    textColor = colors.good
                                )
                            }

                            // Two-layer visual comparison bar
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(10.dp)
                                    .clip(RoundedCornerShape(99.dp))
                                    .background(colors.card2)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(ratio)
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(99.dp))
                                        .background(colors.accent)
                                )
                            }
                        }
                    }
                }

                // Live Progress Card (while compressing)
                if (state.isCompressing) {
                    item(key = "compress_progress") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(22.dp))
                                .background(colors.card2)
                                .border(1.dp, colors.accent, RoundedCornerShape(22.dp))
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = state.statusText.ifBlank { "Compressing..." },
                                    style = TranscribeTextStyles.ListRow,
                                    color = colors.text
                                )
                                Text(
                                    text = "${(state.progress * 100f).roundToInt()}%",
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
                                        .fillMaxWidth(state.progress.coerceIn(0.03f, 1f))
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(99.dp))
                                        .background(colors.accent)
                                )
                            }
                        }
                    }
                }

                // Completed Result Actions Card
                val result = state.result
                if (result != null && !state.isCompressing) {
                    item(key = "compress_result_card") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(22.dp))
                                .background(colors.card)
                                .border(1.dp, colors.good, RoundedCornerShape(22.dp))
                                .padding(16.dp)
                                .testTag("compress_result_card"),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.CheckCircle,
                                    contentDescription = null,
                                    tint = colors.good,
                                    modifier = Modifier.size(22.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Compression Complete",
                                        style = TranscribeTextStyles.CardTitle,
                                        color = colors.text
                                    )
                                    Text(
                                        text = "${result.fileName} · ${MediaCompressor.formatBytes(result.outputSizeBytes)}",
                                        style = TranscribeTextStyles.SubText.withTabularNums(),
                                        color = colors.muted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                SecondaryActionButton(
                                    onClick = onSaveDownload,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("compress_save_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Download,
                                        contentDescription = null,
                                        tint = colors.text,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Save", style = TranscribeTextStyles.ButtonLabel, color = colors.text)
                                }
                                SecondaryActionButton(
                                    onClick = onShareResult,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("compress_share_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Share,
                                        contentDescription = null,
                                        tint = colors.text,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Share", style = TranscribeTextStyles.ButtonLabel, color = colors.text)
                                }
                            }

                            PrimaryActionButton(
                                onClick = onUseForTranscription,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("compress_use_transcribe_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.GraphicEq,
                                    contentDescription = null,
                                    tint = colors.onAccent,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Use for Transcription", style = TranscribeTextStyles.ButtonLabel)
                            }
                        }
                    }
                }
            }

            // Bottom Action Bar: Compress / Cancel
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 14.dp)
            ) {
                if (state.isCompressing) {
                    SecondaryActionButton(
                        onClick = onCancelCompress,
                        borderColor = colors.danger,
                        contentColor = colors.danger,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("compress_cancel_btn")
                    ) {
                        Text(
                            text = "Cancel",
                            style = TranscribeTextStyles.ButtonLabel,
                            color = colors.danger
                        )
                    }
                } else {
                    PrimaryActionButton(
                        onClick = onStartCompress,
                        enabled = media != null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("compress_start_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Compress,
                            contentDescription = null,
                            tint = if (media != null) colors.onAccent else colors.muted,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Compress",
                            style = TranscribeTextStyles.ButtonLabel
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PresetRadioCard(
    title: String,
    subtitle: String,
    estimateText: String?,
    selected: Boolean,
    enabled: Boolean,
    tag: String,
    onClick: () -> Unit
) {
    val colors = TranscribeThemeTokens.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(if (selected) colors.card2 else colors.card)
            .border(
                width = 1.dp,
                color = if (selected) colors.accent else colors.line,
                shape = RoundedCornerShape(22.dp)
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Custom flat radio ring
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .border(
                    width = if (selected) 6.dp else 2.dp,
                    color = if (selected) colors.accent else colors.muted,
                    shape = CircleShape
                )
        )

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
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (!estimateText.isNullOrBlank()) {
            StatusChip(
                text = estimateText,
                backgroundColor = if (selected) colors.soft else colors.card2,
                textColor = if (selected) colors.softText else colors.muted
            )
        }
    }
}
