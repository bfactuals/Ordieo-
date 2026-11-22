package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.ui.theme.TranscribeThemeTokens
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun InteractiveWaveformScrubber(
    waveform: List<Float>,
    progress: Float,
    onSeekFraction: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = TranscribeThemeTokens.colors
    val targetBars = 50
    val bars = if (waveform.isEmpty()) {
        List(targetBars) { 0.35f }
    } else if (waveform.size == targetBars) {
        waveform
    } else {
        List(targetBars) { idx ->
            val srcPos = (idx.toFloat() / (targetBars - 1).coerceAtLeast(1)) * (waveform.size - 1)
            val lo = srcPos.toInt().coerceIn(0, waveform.lastIndex)
            val hi = (lo + 1).coerceIn(0, waveform.lastIndex)
            val frac = srcPos - lo
            waveform[lo] * (1f - frac) + waveform[hi] * frac
        }
    }
    val clampedProgress = progress.coerceIn(0f, 1f)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .testTag("waveform_scrubber")
            .pointerInput(bars.size) {
                detectTapGestures { offset ->
                    if (size.width > 0) {
                        onSeekFraction((offset.x / size.width.toFloat()).coerceIn(0f, 1f))
                    }
                }
            }
            .pointerInput(bars.size) {
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    if (size.width > 0) {
                        onSeekFraction((change.position.x / size.width.toFloat()).coerceIn(0f, 1f))
                    }
                }
            }
    ) {
        val count = bars.size
        val gap = 2.5.dp.toPx()
        val totalGap = gap * (count - 1)
        val barWidth = ((size.width - totalGap) / count).coerceAtLeast(1.8.dp.toPx())
        val maxBarHeight = size.height * 0.88f
        val centerY = size.height / 2f
        val progressX = size.width * clampedProgress

        bars.forEachIndexed { index, amp ->
            val x = index * (barWidth + gap)
            val barH = (amp.coerceIn(0.14f, 1f) * maxBarHeight).coerceAtLeast(4.dp.toPx())
            val top = centerY - barH / 2f
            val isPlayed = (x + barWidth / 2f) <= progressX
            val radius = CornerRadius(barWidth / 2f, barWidth / 2f)

            drawRoundRect(
                color = if (isPlayed) colors.accent else colors.waveOff,
                topLeft = Offset(x, top),
                size = Size(barWidth, barH),
                cornerRadius = radius
            )
        }
    }
}

@Composable
fun EmptyWaveformIllustration(
    modifier: Modifier = Modifier
) {
    val colors = TranscribeThemeTokens.colors
    val infiniteTransition = rememberInfiniteTransition(label = "empty_wave")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_phase"
    )

    Canvas(modifier = modifier) {
        val barCount = 29
        val gap = 4.dp.toPx()
        val barWidth = ((size.width - gap * (barCount - 1)) / barCount).coerceAtLeast(3.dp.toPx())
        val centerY = size.height / 2f
        val maxH = size.height * 0.85f

        for (i in 0 until barCount) {
            val normalizedPos = i.toFloat() / (barCount - 1)
            val envelope = sin(normalizedPos * PI).toFloat()
            val dynamic = 0.35f + 0.65f * ((sin(phase + i * 0.48f) + 1f) / 2f)
            val barH = (maxH * envelope * dynamic).coerceAtLeast(6.dp.toPx())
            val x = i * (barWidth + gap)
            val isCenter = i in 8..20
            drawRoundRect(
                color = if (isCenter) colors.accent else colors.waveOff,
                topLeft = Offset(x, centerY - barH / 2f),
                size = Size(barWidth, barH),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}
