package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.ui.theme.TranscribeTextStyles
import com.example.ui.theme.TranscribeThemeTokens
import com.example.ui.theme.withTabularNums

/**
 * 11.sp SemiBold UPPERCASE, 0.1em letter-spacing, muted color.
 */
@Composable
fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier
) {
    val colors = TranscribeThemeTokens.colors
    Text(
        text = text.uppercase(),
        style = TranscribeTextStyles.SectionLabel,
        color = colors.muted,
        modifier = modifier
    )
}

/**
 * Flat custom toggle switch: 46.dp x 28.dp track with 22.dp circular knob.
 * Uses --accent when ON and --toggle-off when OFF.
 */
@Composable
fun AppToggleSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = TranscribeThemeTokens.colors
    val trackColor by animateColorAsState(
        targetValue = if (checked) colors.accent else colors.toggleOff,
        animationSpec = tween(durationMillis = 160),
        label = "toggle_track"
    )
    val knobOffset by animateDpAsState(
        targetValue = if (checked) 21.dp else 3.dp,
        animationSpec = tween(durationMillis = 160),
        label = "toggle_knob"
    )
    val knobColor = if (checked) colors.onAccent else Color.White

    Box(
        modifier = modifier
            .size(width = 46.dp, height = 28.dp)
            .clip(RoundedCornerShape(99.dp))
            .background(trackColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Switch,
                onClick = { onCheckedChange(!checked) }
            ),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .offset(x = knobOffset)
                .size(22.dp)
                .clip(CircleShape)
                .background(knobColor)
        )
    }
}

@Composable
fun SheetDragHandle() {
    val colors = TranscribeThemeTokens.colors
    Box(
        modifier = Modifier
            .padding(top = 12.dp, bottom = 4.dp)
            .size(width = 36.dp, height = 4.dp)
            .clip(CircleShape)
            .background(colors.line)
    )
}

/**
 * Standard 52.dp height, 16.dp radius, 15.sp SemiBold primary action button.
 */
@Composable
fun PrimaryActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color? = null,
    contentColor: Color? = null,
    content: @Composable RowScope.() -> Unit
) {
    val colors = TranscribeThemeTokens.colors
    val bg = containerColor ?: colors.accent
    val fg = contentColor ?: colors.onAccent
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = bg,
            contentColor = fg,
            disabledContainerColor = colors.card2,
            disabledContentColor = colors.muted
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
            disabledElevation = 0.dp
        ),
        contentPadding = PaddingValues(horizontal = 16.dp),
        modifier = modifier.height(52.dp),
        content = content
    )
}

/**
 * Standard 52.dp height, 16.dp radius, 1.dp solid --line secondary button.
 */
@Composable
fun SecondaryActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    borderColor: Color? = null,
    contentColor: Color? = null,
    content: @Composable RowScope.() -> Unit
) {
    val colors = TranscribeThemeTokens.colors
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor ?: colors.line),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = colors.card,
            contentColor = contentColor ?: colors.text,
            disabledContentColor = colors.muted
        ),
        contentPadding = PaddingValues(horizontal = 16.dp),
        modifier = modifier.height(52.dp),
        content = content
    )
}

/**
 * Status chip pill: 99.dp radius, 12.dp horizontal / 4.dp vertical padding.
 */
@Composable
fun StatusChip(
    text: String,
    backgroundColor: Color,
    textColor: Color,
    modifier: Modifier = Modifier,
    borderColor: Color? = null
) {
    val shape = RoundedCornerShape(99.dp)
    Box(
        modifier = modifier
            .heightIn(min = 28.dp)
            .clip(shape)
            .background(backgroundColor)
            .then(
                if (borderColor != null) Modifier.border(1.dp, borderColor, shape)
                else Modifier
            )
            .padding(horizontal = 12.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = TranscribeTextStyles.SubText.withTabularNums(),
            color = textColor,
            maxLines = 1
        )
    }
}
