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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.VideoFile
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.AppConfig
import com.example.AppLogo
import com.example.R
import com.example.data.model.DailyUsage
import com.example.data.model.LanguageMode
import com.example.data.model.PendingQueueJob
import com.example.data.model.ProjectModel
import com.example.data.model.RetentionOption
import com.example.data.remote.RemotePromoConfig
import com.example.data.srt.SrtEngine
import com.example.ui.components.EmptyWaveformIllustration
import com.example.ui.components.ExpiryPickerDialog
import com.example.ui.components.RenameProjectDialog
import com.example.ui.components.SectionLabel
import com.example.ui.components.StatusChip
import com.example.ui.theme.TranscribeTextStyles
import com.example.ui.theme.TranscribeThemeTokens
import com.example.ui.theme.withTabularNums
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    projects: List<ProjectModel>,
    dailyUsage: DailyUsage,
    pendingQueue: List<PendingQueueJob>,
    isRetryingQueue: Boolean,
    isDarkTheme: Boolean,
    promo: RemotePromoConfig,
    onToggleTheme: () -> Unit,
    onOpenUsageModal: () -> Unit,
    onOpenSettings: () -> Unit,
    onUploadVideo: () -> Unit,
    onUploadAudio: () -> Unit,
    onRecordVoice: () -> Unit,
    onImportTextSrt: () -> Unit,
    onOpenCompress: () -> Unit,
    onOpenPromoUrl: (String) -> Unit,
    onRetryQueue: () -> Unit,
    onOpenProject: (ProjectModel) -> Unit,
    onRenameProject: (String, String) -> Unit,
    onTogglePinProject: (String, Boolean) -> Unit,
    onChangeProjectExpiry: (String, RetentionOption) -> Unit,
    onDeleteProject: (String) -> Unit
) {
    val colors = TranscribeThemeTokens.colors
    var expiryTargetProject by remember { mutableStateOf<ProjectModel?>(null) }
    var renameTargetProject by remember { mutableStateOf<ProjectModel?>(null) }
    var searchExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val filteredProjects = remember(projects, searchQuery) {
        val q = searchQuery.trim()
        val matched = if (q.isEmpty()) {
            projects
        } else {
            projects.filter { project ->
                project.title.contains(q, ignoreCase = true) ||
                    project.originalSegments.any { it.text.contains(q, ignoreCase = true) } ||
                    (project.englishSegments?.any { it.text.contains(q, ignoreCase = true) } == true) ||
                    (project.hinglishSegments?.any { it.text.contains(q, ignoreCase = true) } == true)
            }
        }
        matched.sortedWith(compareByDescending<ProjectModel> { it.isPinned }.thenByDescending { it.updatedAt })
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
                .widthIn(max = 720.dp)
                .align(Alignment.TopCenter)
        ) {
            // 1. Top Row: Logo (36dp) + App Name (24/700) + Search + Theme + Settings
            HomeTopRow(
                isDarkTheme = isDarkTheme,
                searchExpanded = searchExpanded,
                onToggleSearch = {
                    searchExpanded = !searchExpanded
                    if (!searchExpanded) searchQuery = ""
                },
                onToggleTheme = onToggleTheme,
                onOpenSettings = onOpenSettings
            )

            // 2. Status Pills Row: "Auto: Hindi / English" + Daily Usage pill ("0 req")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp)
                    .padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusChip(
                    text = stringResource(R.string.badge_auto_lang),
                    backgroundColor = colors.soft,
                    textColor = colors.softText
                )

                Row(
                    modifier = Modifier
                        .height(30.dp)
                        .clip(RoundedCornerShape(99.dp))
                        .background(colors.card)
                        .border(1.dp, colors.line, RoundedCornerShape(99.dp))
                        .clickable(onClick = onOpenUsageModal)
                        .padding(horizontal = 12.dp)
                        .testTag("usage_pill"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Bolt,
                        contentDescription = stringResource(R.string.cd_usage_pill),
                        tint = colors.accentText,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "${dailyUsage.requestsToday} req",
                        style = TranscribeTextStyles.SubText.withTabularNums(),
                        color = colors.text
                    )
                }
            }

            // Expandable Search Bar
            AnimatedVisibility(
                visible = searchExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            text = "Search by title or transcript text...",
                            style = TranscribeTextStyles.SubText,
                            color = colors.muted
                        )
                    },
                    singleLine = true,
                    textStyle = TranscribeTextStyles.ListRow.copy(color = colors.text),
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = null,
                            tint = colors.muted,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Clear search",
                                    tint = colors.muted,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
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
                        .padding(horizontal = 18.dp)
                        .padding(bottom = 10.dp)
                        .testTag("home_search_input")
                )
            }

            // 3. Main Scrollable Feed: START NEW 2x2 Bento + Compress Quick Tile + RECENT List
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag("projects_list"),
                contentPadding = PaddingValues(
                    start = 18.dp,
                    end = 18.dp,
                    top = 4.dp,
                    bottom = if (pendingQueue.isNotEmpty()) 96.dp else 28.dp
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // START NEW Section
                item(key = "start_new_section") {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        SectionLabel(text = "START NEW")
                        Spacer(modifier = Modifier.height(8.dp))

                        // Row 1: Video (--accent) & Audio (--card2)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            BentoActionTile(
                                icon = Icons.Outlined.VideoFile,
                                title = "Video",
                                subtitle = "Extract audio",
                                containerColor = colors.accent,
                                borderColor = colors.accent,
                                iconBoxColor = colors.onAccent.copy(alpha = 0.14f),
                                iconTint = colors.onAccent,
                                titleColor = colors.onAccent,
                                subtitleColor = colors.onAccentMuted,
                                tag = "bento_upload_video",
                                onClick = onUploadVideo,
                                modifier = Modifier.weight(1f)
                            )
                            BentoActionTile(
                                icon = Icons.Outlined.AudioFile,
                                title = "Audio",
                                subtitle = "MP3 · WAV · M4A",
                                containerColor = colors.card2,
                                borderColor = colors.line,
                                iconBoxColor = colors.soft,
                                iconTint = colors.accentText,
                                titleColor = colors.text,
                                subtitleColor = colors.muted,
                                tag = "bento_upload_audio",
                                onClick = onUploadAudio,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Row 2: Record (--card2) & Hinglish (--card)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            BentoActionTile(
                                icon = Icons.Outlined.Mic,
                                title = "Record",
                                subtitle = "Live waveform",
                                containerColor = colors.card2,
                                borderColor = colors.line,
                                iconBoxColor = colors.soft,
                                iconTint = colors.accentText,
                                titleColor = colors.text,
                                subtitleColor = colors.muted,
                                tag = "bento_record_voice",
                                onClick = onRecordVoice,
                                modifier = Modifier.weight(1f)
                            )
                            BentoActionTile(
                                icon = Icons.Outlined.Translate,
                                title = "Hinglish",
                                subtitle = "SRT · TXT convert",
                                containerColor = colors.card,
                                borderColor = colors.line,
                                iconBoxColor = colors.soft,
                                iconTint = colors.accentText,
                                titleColor = colors.text,
                                subtitleColor = colors.muted,
                                tag = "bento_import_srt",
                                onClick = onImportTextSrt,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Full-width Quick Tile: Compress Video & Audio
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(22.dp))
                                .background(colors.card2)
                                .border(1.dp, colors.line, RoundedCornerShape(22.dp))
                                .clickable(onClick = onOpenCompress)
                                .padding(horizontal = 16.dp, vertical = 14.dp)
                                .testTag("bento_compress_tile"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(13.dp))
                                    .background(colors.soft),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Compress,
                                    contentDescription = "Compress Video and Audio",
                                    tint = colors.accentText,
                                    modifier = Modifier.size(21.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Compress Video & Audio",
                                    style = TranscribeTextStyles.CardTitle,
                                    color = colors.text,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Shrink to 2–3 MB for AI · Fix HDR",
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
                }

                // Remote-Controlled "FROM THE CREATOR" Promo Card (shown only if enabled & not expired)
                if (promo.isActive()) {
                    item(key = "creator_promo_card") {
                        CreatorPromoCard(
                            promo = promo,
                            onOpen = { onOpenPromoUrl(promo.url) }
                        )
                    }
                }

                // RECENT Section Header with count on right (8dp gap below label handled by top spacing)
                item(key = "recent_header") {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 0.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SectionLabel(text = "RECENT")
                        Text(
                            text = "${filteredProjects.size}",
                            style = TranscribeTextStyles.SubText.withTabularNums(),
                            color = colors.muted
                        )
                    }
                }

                // Projects or Empty State
                if (projects.isEmpty()) {
                    item(key = "home_empty_state") {
                        HomeEmptyCard()
                    }
                } else if (filteredProjects.isEmpty()) {
                    item(key = "home_search_empty") {
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
                                text = "No matching projects",
                                style = TranscribeTextStyles.CardTitle,
                                color = colors.text
                            )
                            Text(
                                text = "Try a different title or keyword from your transcripts.",
                                style = TranscribeTextStyles.SubText,
                                color = colors.muted,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    items(filteredProjects, key = { it.id }) { project ->
                        ProjectCard(
                            project = project,
                            onClick = { onOpenProject(project) },
                            onRename = { renameTargetProject = project },
                            onTogglePin = { onTogglePinProject(project.id, project.isPinned) },
                            onChangeExpiry = { expiryTargetProject = project },
                            onDelete = { onDeleteProject(project.id) }
                        )
                    }
                }
            }
        }

        // Pending Queue Banner (shown at bottom only if an API job is waiting after failure)
        if (pendingQueue.isNotEmpty()) {
            PendingQueueBanner(
                count = pendingQueue.size,
                isRetrying = isRetryingQueue,
                onRetryNow = onRetryQueue,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .widthIn(max = 720.dp)
                    .navigationBarsPadding()
                    .padding(horizontal = 18.dp, vertical = 14.dp)
            )
        }
    }

    expiryTargetProject?.let { target ->
        ExpiryPickerDialog(
            currentOption = target.retentionOption,
            onDismiss = { expiryTargetProject = null },
            onSelect = { newOption ->
                onChangeProjectExpiry(target.id, newOption)
            }
        )
    }

    renameTargetProject?.let { target ->
        RenameProjectDialog(
            currentTitle = target.title,
            onDismiss = { renameTargetProject = null },
            onConfirm = { newName ->
                onRenameProject(target.id, newName)
                renameTargetProject = null
            }
        )
    }
}

@Composable
private fun HomeTopRow(
    isDarkTheme: Boolean,
    searchExpanded: Boolean,
    onToggleSearch: () -> Unit,
    onToggleTheme: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val colors = TranscribeThemeTokens.colors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f)
        ) {
            AppLogo(size = 36.dp)
            Text(
                text = AppConfig.APP_NAME,
                style = TranscribeTextStyles.HomeAppName,
                color = colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            HeaderRoundButton(
                onClick = onToggleSearch,
                active = searchExpanded,
                tag = "home_search_btn"
            ) {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = stringResource(R.string.cd_search),
                    tint = if (searchExpanded) colors.accentText else colors.text,
                    modifier = Modifier.size(20.dp)
                )
            }

            HeaderRoundButton(
                onClick = onToggleTheme,
                tag = "theme_toggle_btn"
            ) {
                Icon(
                    imageVector = if (isDarkTheme) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                    contentDescription = stringResource(R.string.cd_theme_toggle),
                    tint = colors.text,
                    modifier = Modifier.size(20.dp)
                )
            }

            HeaderRoundButton(
                onClick = onOpenSettings,
                tag = "settings_btn"
            ) {
                Icon(
                    imageVector = Icons.Outlined.Settings,
                    contentDescription = stringResource(R.string.cd_settings),
                    tint = colors.text,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun HeaderRoundButton(
    onClick: () -> Unit,
    active: Boolean = false,
    tag: String,
    content: @Composable () -> Unit
) {
    val colors = TranscribeThemeTokens.colors
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(if (active) colors.soft else colors.card)
            .border(
                width = 1.dp,
                color = if (active) colors.accent else colors.line,
                shape = CircleShape
            )
            .clickable(onClick = onClick)
            .testTag(tag),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
private fun BentoActionTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    containerColor: Color,
    borderColor: Color,
    iconBoxColor: Color,
    iconTint: Color,
    titleColor: Color,
    subtitleColor: Color,
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
            .clickable(onClick = onClick)
            .padding(16.dp)
            .testTag(tag),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(iconBoxColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = TranscribeTextStyles.CardTitle,
                color = titleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = TranscribeTextStyles.SubText,
                color = subtitleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ProjectCard(
    project: ProjectModel,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onTogglePin: () -> Unit,
    onChangeExpiry: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = TranscribeThemeTokens.colors
    var menuExpanded by remember { mutableStateOf(false) }

    val dateFormatter = remember { SimpleDateFormat("MMM d, h:mm a", Locale.US) }
    val formattedDate = remember(project.updatedAt) {
        dateFormatter.format(Date(project.updatedAt))
    }
    val isKept = project.isPinned || project.retentionOption == RetentionOption.PERMANENT || project.expiresAt == null
    val isUnderOneHour = remember(project.expiresAt, isKept) {
        if (isKept) return@remember false
        val exp = project.expiresAt ?: return@remember false
        (exp - System.currentTimeMillis()) < 60L * 60L * 1000L
    }
    val countdownLabel = remember(project.expiresAt, project.retentionOption, project.isPinned) {
        if (project.isPinned) "Kept"
        else formatRemainingExpiry(project.expiresAt, project.retentionOption)
    }

    val borderStrokeColor = if (project.isPinned) colors.accent else colors.line

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(colors.card)
            .border(1.dp, borderStrokeColor, RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .padding(start = 16.dp, top = 14.dp, bottom = 14.dp, end = 6.dp)
            .testTag("project_card_${project.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 46dp --soft icon tile
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(colors.soft),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (project.hasAudio) Icons.Outlined.GraphicEq else Icons.Outlined.Subtitles,
                contentDescription = null,
                tint = colors.accentText,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (project.isPinned) {
                    Icon(
                        imageVector = Icons.Rounded.PushPin,
                        contentDescription = "Pinned",
                        tint = colors.accentText,
                        modifier = Modifier.size(14.dp)
                    )
                }
                Text(
                    text = project.title,
                    style = TranscribeTextStyles.CardTitle,
                    color = colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = formattedDate,
                    style = TranscribeTextStyles.SubText,
                    color = colors.muted
                )
                if (project.hasAudio && project.audioDurationMs > 0L) {
                    Text(
                        text = "· ${SrtEngine.formatDurationShort(project.audioDurationMs)}",
                        style = TranscribeTextStyles.SubText.withTabularNums(),
                        color = colors.muted
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                StatusChip(
                    text = countdownLabel,
                    backgroundColor = when {
                        isKept -> colors.goodBg
                        isUnderOneHour -> colors.warnBg
                        else -> colors.card2
                    },
                    textColor = when {
                        isKept -> colors.good
                        isUnderOneHour -> colors.warn
                        else -> colors.muted
                    },
                    borderColor = when {
                        isKept -> colors.good.copy(alpha = 0.45f)
                        isUnderOneHour -> colors.warn.copy(alpha = 0.55f)
                        else -> colors.line
                    }
                )

                if (project.hasAudio && !project.hasTranscript) {
                    StatusChip(
                        text = "Ready to transcribe",
                        backgroundColor = colors.soft,
                        textColor = colors.softText
                    )
                } else if (project.languageMode != LanguageMode.ORIGINAL) {
                    StatusChip(
                        text = project.languageMode.label,
                        backgroundColor = colors.soft,
                        textColor = colors.softText
                    )
                }
            }
        }

        // 3-dot Menu: Rename, Pin forever, Change auto-delete, Delete
        Box {
            IconButton(
                onClick = { menuExpanded = true },
                modifier = Modifier
                    .size(44.dp)
                    .testTag("project_menu_${project.id}")
            ) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = stringResource(R.string.cd_project_menu),
                    tint = colors.muted
                )
            }

            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
                containerColor = colors.card
            ) {
                DropdownMenuItem(
                    text = {
                        Text(
                            text = "Rename",
                            style = TranscribeTextStyles.ListRow,
                            color = colors.text
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = null,
                            tint = colors.accentText,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    onClick = {
                        menuExpanded = false
                        onRename()
                    }
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            text = if (project.isPinned) "Unpin" else "Pin forever",
                            style = TranscribeTextStyles.ListRow,
                            color = colors.text
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = if (project.isPinned) Icons.Rounded.PushPin else Icons.Outlined.PushPin,
                            contentDescription = null,
                            tint = colors.accentText,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    onClick = {
                        menuExpanded = false
                        onTogglePin()
                    }
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            text = "Change auto-delete",
                            style = TranscribeTextStyles.ListRow,
                            color = colors.text
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Schedule,
                            contentDescription = null,
                            tint = colors.accentText,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    onClick = {
                        menuExpanded = false
                        onChangeExpiry()
                    }
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            text = "Delete",
                            style = TranscribeTextStyles.ListRow,
                            color = colors.danger
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = null,
                            tint = colors.danger,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    onClick = {
                        menuExpanded = false
                        onDelete()
                    }
                )
            }
        }
    }
}

@Composable
private fun PendingQueueBanner(
    count: Int,
    isRetrying: Boolean,
    onRetryNow: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = TranscribeThemeTokens.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(colors.card2)
            .border(1.dp, colors.line, RoundedCornerShape(22.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("pending_queue_banner"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(colors.warn)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (count == 1) "1 file waiting for API" else "$count files waiting for API",
                style = TranscribeTextStyles.CardTitle,
                color = colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "Retries automatically",
                style = TranscribeTextStyles.SubText,
                color = colors.muted,
                maxLines = 1
            )
        }
        Box(
            modifier = Modifier
                .height(40.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(colors.accent)
                .clickable(enabled = !isRetrying, onClick = onRetryNow)
                .padding(horizontal = 16.dp)
                .testTag("retry_queue_btn"),
            contentAlignment = Alignment.Center
        ) {
            if (isRetrying) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = colors.onAccent
                )
            } else {
                Text(
                    text = "Retry now",
                    style = TranscribeTextStyles.ListRow.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.onAccent
                )
            }
        }
    }
}

@Composable
private fun CreatorPromoCard(
    promo: RemotePromoConfig,
    onOpen: () -> Unit
) {
    val colors = TranscribeThemeTokens.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(colors.card2)
            .border(1.dp, colors.line, RoundedCornerShape(22.dp))
            .clickable(onClick = onOpen)
            .padding(16.dp)
            .testTag("home_creator_promo_card"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = promo.label.uppercase(),
                style = TranscribeTextStyles.MicroPromoLabel,
                color = colors.accentText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = promo.title,
                style = TranscribeTextStyles.CardTitle,
                color = colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (promo.subtitle.isNotBlank()) {
                Text(
                    text = promo.subtitle,
                    style = TranscribeTextStyles.SubText,
                    color = colors.muted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Box(
            modifier = Modifier
                .height(40.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(colors.accent)
                .clickable(onClick = onOpen)
                .padding(horizontal = 16.dp)
                .testTag("home_creator_promo_open_btn"),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = promo.ctaLabel.ifBlank { "Open" },
                style = TranscribeTextStyles.ListRow.copy(fontWeight = FontWeight.SemiBold),
                color = colors.onAccent,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun HomeEmptyCard() {
    val colors = TranscribeThemeTokens.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(colors.card)
            .border(1.dp, colors.line, RoundedCornerShape(22.dp))
            .padding(horizontal = 20.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        EmptyWaveformIllustration(
            modifier = Modifier
                .width(180.dp)
                .height(48.dp)
        )
        Text(
            text = stringResource(R.string.empty_title),
            style = TranscribeTextStyles.CardTitle,
            color = colors.text,
            textAlign = TextAlign.Center
        )
        Text(
            text = "Pick an action above to extract audio, record voice, compress media, or import .SRT subtitles offline.",
            style = TranscribeTextStyles.SubText,
            color = colors.muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 300.dp)
        )
    }
}

fun formatRemainingExpiry(expiresAt: Long?, option: RetentionOption): String {
    if (option == RetentionOption.PERMANENT || expiresAt == null) return "Kept"
    val diffMs = (expiresAt - System.currentTimeMillis()).coerceAtLeast(0L)
    val totalMinutes = diffMs / 60_000L
    val totalHours = totalMinutes / 60L
    val totalDays = totalHours / 24L
    return when {
        totalDays >= 1L -> "${totalDays}d left"
        totalHours >= 1L -> "${totalHours}h left"
        totalMinutes >= 1L -> "${totalMinutes}m left"
        else -> "<1m left"
    }
}
