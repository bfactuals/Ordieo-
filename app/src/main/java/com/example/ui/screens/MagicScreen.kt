package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.MagicContent
import com.example.ui.components.PrimaryActionButton
import com.example.ui.components.SectionLabel
import com.example.ui.components.StatusChip
import com.example.ui.theme.TranscribeTextStyles
import com.example.ui.theme.TranscribeThemeTokens
import com.example.ui.theme.withTabularNums

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MagicScreen(
    projectTitle: String,
    magicContent: MagicContent?,
    isGenerating: Boolean,
    onBack: () -> Unit,
    onGenerate: (forceRegenerate: Boolean) -> Unit,
    onCopyItem: (label: String, text: String) -> Unit
) {
    BackHandler { onBack() }

    val colors = TranscribeThemeTokens.colors

    LaunchedEffect(Unit) {
        if (magicContent == null && !isGenerating) {
            onGenerate(false)
        }
    }

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
            // Top Header: Back + "Magic" + "1 API request" chip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 10.dp, end = 18.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(44.dp)
                            .testTag("magic_back_btn")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back),
                            tint = colors.text
                        )
                    }
                    Column {
                        Text(
                            text = "Magic",
                            style = TranscribeTextStyles.ScreenTitle,
                            color = colors.text
                        )
                        if (projectTitle.isNotBlank()) {
                            Text(
                                text = projectTitle,
                                style = TranscribeTextStyles.SubText,
                                color = colors.muted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                StatusChip(
                    text = "1 API request",
                    backgroundColor = colors.goodBg,
                    textColor = colors.good
                )
            }

            // Content Area
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag("magic_screen_list"),
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (isGenerating) {
                    item(key = "magic_skeletons") {
                        MagicSkeletonLoaderList()
                    }
                } else if (magicContent == null) {
                    item(key = "magic_empty") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(22.dp))
                                .background(colors.card)
                                .border(1.dp, colors.line, RoundedCornerShape(22.dp))
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(colors.soft),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.AutoAwesome,
                                    contentDescription = null,
                                    tint = colors.accentText,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Text(
                                text = "Generate Creator Assets",
                                style = TranscribeTextStyles.CardTitle,
                                color = colors.text
                            )
                            Text(
                                text = "Get viral hooks, 5 punchy titles, description, hashtags, and a pinned comment from your transcript in 1 API request.",
                                style = TranscribeTextStyles.SubText,
                                color = colors.muted
                            )
                        }
                    }
                } else {
                    // 1. VIRAL HOOKS (3-5 opening lines)
                    item(key = "magic_hooks") {
                        val allHooks = magicContent.viralHooks
                            .mapIndexed { i, h -> "${i + 1}. $h" }
                            .joinToString("\n")
                        MagicSectionCard(
                            title = "VIRAL HOOKS",
                            copyLabel = "Copy",
                            copyTag = "copy_magic_hooks",
                            onCopy = { onCopyItem("Viral Hooks", allHooks) }
                        ) {
                            if (magicContent.viralHooks.isEmpty()) {
                                Text("No hooks generated.", style = TranscribeTextStyles.SubText, color = colors.muted)
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    magicContent.viralHooks.forEachIndexed { idx, hook ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(14.dp))
                                                .background(colors.card2)
                                                .border(1.dp, colors.line, RoundedCornerShape(14.dp))
                                                .clickable { onCopyItem("Hook ${idx + 1}", hook) }
                                                .padding(horizontal = 14.dp, vertical = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Text(
                                                text = hook,
                                                style = TranscribeTextStyles.ListRow,
                                                color = colors.text,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Icon(
                                                imageVector = Icons.Outlined.ContentCopy,
                                                contentDescription = "Copy hook",
                                                tint = colors.muted,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 2. BEST 5 TITLES / THUMBNAIL TEXT (5 numbered rows with individual copy icon)
                    item(key = "magic_titles") {
                        val allTitles = magicContent.thumbnailTitles.joinToString("\n")
                        MagicSectionCard(
                            title = "BEST 5 TITLES / THUMBNAIL TEXT",
                            copyLabel = "Copy all",
                            copyTag = "copy_magic_thumbnails",
                            onCopy = { onCopyItem("Titles", allTitles) }
                        ) {
                            if (magicContent.thumbnailTitles.isEmpty()) {
                                Text("No titles generated.", style = TranscribeTextStyles.SubText, color = colors.muted)
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    magicContent.thumbnailTitles.take(5).forEachIndexed { idx, title ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(14.dp))
                                                .background(colors.card2)
                                                .border(1.dp, colors.line, RoundedCornerShape(14.dp))
                                                .clickable { onCopyItem("Title ${idx + 1}", title) }
                                                .padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(24.dp)
                                                    .clip(CircleShape)
                                                    .background(colors.soft),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = "${idx + 1}",
                                                    style = TranscribeTextStyles.SubText.withTabularNums().copy(
                                                        fontWeight = FontWeight.SemiBold
                                                    ),
                                                    color = colors.softText
                                                )
                                            }
                                            Text(
                                                text = title,
                                                style = TranscribeTextStyles.ListRow,
                                                color = colors.text,
                                                modifier = Modifier.weight(1f)
                                            )
                                            IconButton(
                                                onClick = { onCopyItem("Title ${idx + 1}", title) },
                                                modifier = Modifier.size(44.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Outlined.ContentCopy,
                                                    contentDescription = "Copy title ${idx + 1}",
                                                    tint = colors.muted,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 3. DESCRIPTION (--card2 card with Copy pill)
                    item(key = "magic_description") {
                        MagicSectionCard(
                            title = "DESCRIPTION",
                            copyLabel = "Copy",
                            copyTag = "copy_magic_captions",
                            onCopy = { onCopyItem("Description", magicContent.captions) }
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(colors.card2)
                                    .border(1.dp, colors.line, RoundedCornerShape(16.dp))
                                    .clickable { onCopyItem("Description", magicContent.captions) }
                                    .padding(14.dp)
                            ) {
                                Text(
                                    text = magicContent.captions.ifBlank { "No description generated." },
                                    style = TranscribeTextStyles.BodyTranscript,
                                    color = colors.text
                                )
                            }
                        }
                    }

                    // 4. HASHTAGS & SEO (chips + Copy all pill)
                    item(key = "magic_hashtags") {
                        val allTags = magicContent.hashtags.joinToString(" ")
                        MagicSectionCard(
                            title = "HASHTAGS & SEO",
                            copyLabel = "Copy all",
                            copyTag = "copy_magic_hashtags",
                            onCopy = { onCopyItem("Hashtags", allTags) }
                        ) {
                            if (magicContent.hashtags.isEmpty()) {
                                Text("No hashtags generated.", style = TranscribeTextStyles.SubText, color = colors.muted)
                            } else {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    magicContent.hashtags.forEach { tag ->
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(99.dp))
                                                .background(colors.soft)
                                                .clickable { onCopyItem("Hashtag", tag) }
                                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Text(
                                                text = tag,
                                                style = TranscribeTextStyles.SubText.copy(fontWeight = FontWeight.Medium),
                                                color = colors.softText
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 5. PINNED COMMENT IDEA (1-2 engaging questions)
                    item(key = "magic_pinned") {
                        MagicSectionCard(
                            title = "PINNED COMMENT IDEA",
                            copyLabel = "Copy",
                            copyTag = "copy_magic_pinned",
                            onCopy = { onCopyItem("Pinned Comment", magicContent.pinnedComment) }
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(colors.card2)
                                    .border(1.dp, colors.line, RoundedCornerShape(16.dp))
                                    .clickable { onCopyItem("Pinned Comment", magicContent.pinnedComment) }
                                    .padding(14.dp)
                            ) {
                                Text(
                                    text = magicContent.pinnedComment.ifBlank { "No pinned comment generated." },
                                    style = TranscribeTextStyles.BodyTranscript,
                                    color = colors.text
                                )
                            }
                        }
                    }
                }
            }

            // Bottom Primary Button: Regenerate
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 14.dp)
            ) {
                PrimaryActionButton(
                    onClick = { onGenerate(true) },
                    enabled = !isGenerating,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("magic_regenerate_btn")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = null,
                        tint = if (isGenerating) colors.muted else colors.onAccent,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isGenerating) "Generating..." else "Regenerate",
                        style = TranscribeTextStyles.ButtonLabel
                    )
                }
            }
        }
    }
}

@Composable
private fun MagicSectionCard(
    title: String,
    copyLabel: String,
    copyTag: String,
    onCopy: () -> Unit,
    content: @Composable () -> Unit
) {
    val colors = TranscribeThemeTokens.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(colors.card)
            .border(1.dp, colors.line, RoundedCornerShape(22.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SectionLabel(text = title, modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.width(8.dp))
            Row(
                modifier = Modifier
                    .height(32.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(colors.card2)
                    .border(1.dp, colors.line, RoundedCornerShape(99.dp))
                    .clickable(onClick = onCopy)
                    .padding(horizontal = 12.dp)
                    .testTag(copyTag),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.ContentCopy,
                    contentDescription = stringResource(R.string.cd_copy),
                    tint = colors.muted,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = copyLabel,
                    style = TranscribeTextStyles.SubText,
                    color = colors.text
                )
            }
        }
        content()
    }
}

@Composable
private fun MagicSkeletonLoaderList() {
    val colors = TranscribeThemeTokens.colors
    val infinite = rememberInfiniteTransition(label = "magic_skeleton")
    val pulseAlpha by infinite.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850),
            repeatMode = RepeatMode.Reverse
        ),
        label = "skeleton_alpha"
    )

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val labels = listOf(
            "VIRAL HOOKS",
            "BEST 5 TITLES / THUMBNAIL TEXT",
            "DESCRIPTION",
            "HASHTAGS & SEO",
            "PINNED COMMENT IDEA"
        )
        labels.forEachIndexed { idx, label ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(colors.card)
                    .border(1.dp, colors.line, RoundedCornerShape(22.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SectionLabel(text = label)
                val barCount = if (idx == 1) 3 else 2
                repeat(barCount) { line ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(if (line == barCount - 1) 0.72f else 1f)
                            .height(16.dp)
                            .alpha(pulseAlpha)
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.card2)
                    )
                }
            }
        }
    }
}
