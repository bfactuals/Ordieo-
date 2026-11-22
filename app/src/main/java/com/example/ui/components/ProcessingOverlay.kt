package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.ProcessingOverlayState
import com.example.ui.theme.TranscribeTextStyles
import com.example.ui.theme.TranscribeThemeTokens
import com.example.ui.theme.withTabularNums

@Composable
fun ProcessingOverlay(
    state: ProcessingOverlayState,
    modifier: Modifier = Modifier
) {
    val colors = TranscribeThemeTokens.colors

    AnimatedVisibility(
        visible = state.isVisible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.overlay)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
                .testTag("processing_overlay"),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 18.dp)
                    .widthIn(max = 380.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(colors.card)
                    .border(1.dp, colors.line, RoundedCornerShape(22.dp))
                    .padding(20.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.5.dp,
                        color = colors.accent,
                        trackColor = colors.soft
                    )
                    Text(
                        text = "Processing Media",
                        style = TranscribeTextStyles.ScreenTitle,
                        color = colors.text
                    )
                }

                if (state.detail.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = state.detail,
                        style = TranscribeTextStyles.SubText,
                        color = colors.muted
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    state.steps.forEachIndexed { idx, step ->
                        val isCompleted = idx < state.currentStepIndex ||
                            (idx == state.currentStepIndex && idx == state.steps.lastIndex)
                        val isActive = idx == state.currentStepIndex && !isCompleted

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            isCompleted -> colors.goodBg
                                            isActive -> colors.soft
                                            else -> colors.card2
                                        }
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = when {
                                            isCompleted -> colors.good
                                            isActive -> colors.accent
                                            else -> colors.line
                                        },
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                when {
                                    isCompleted -> {
                                        Icon(
                                            imageVector = Icons.Rounded.Check,
                                            contentDescription = null,
                                            tint = colors.good,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                    isActive -> {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(13.dp),
                                            strokeWidth = 2.dp,
                                            color = colors.accent
                                        )
                                    }
                                    else -> {
                                        Text(
                                            text = "${idx + 1}",
                                            style = TranscribeTextStyles.SubText.withTabularNums(),
                                            color = colors.muted
                                        )
                                    }
                                }
                            }

                            Text(
                                text = step.label,
                                style = TranscribeTextStyles.ListRow.copy(
                                    fontWeight = if (isActive || isCompleted) FontWeight.SemiBold else FontWeight.Medium
                                ),
                                color = when {
                                    isCompleted -> colors.good
                                    isActive -> colors.text
                                    else -> colors.muted
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
