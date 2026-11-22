package com.example

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.TranscribeThemeTokens

/**
 * Remote config endpoint hosted for zero-cost About, Share, Support, Promo, and Update controls.
 * Never store or fetch any API keys or secrets in this file.
 */
const val REMOTE_CONFIG_URL: String =
    "https://raw.githubusercontent.com/bfactuals/transcribe-config/refs/heads/main/config.json"

/**
 * Central branding config so the app name and logo icon can be updated in a single place.
 */
object AppConfig {
    const val APP_NAME: String = "ordieo"
    const val APP_VERSION: String = "v2.0.0"
}

/**
 * Built-in offline default creator & support metadata (strictly no GitHub links in UI).
 * Used as fallback whenever offline or before remote config is cached.
 */
object CREATOR_CONFIG {
    const val creatorName: String = "ordieo"
    const val creatorTagline: String = "Audio converter, AI transcriber, and on-device video compressor."
    const val instagramHandle: String = "@ordieo.app"
    const val instagramUrl: String = "https://instagram.com/ordieo.app"
    const val youtubeHandle: String = "ordieo"
    const val youtubeUrl: String = "https://youtube.com/@ordieo"
    const val contactEmail: String = "hello@ordieo.app"
    const val appShareUrl: String = "https://ordieo.app"
    const val appShareIntro: String = "Check out ordieo — a fast, private audio converter, AI transcriber with live SRT & Hinglish captions, and on-device video compressor."
    const val supportUpiId: String = "ordieo@upi"
    const val supportNote: String = "Every contribution keeps ordieo 100% ad-free, local, and independent."
    const val supportLinkLabel: String = "Send Feedback or Feature Request"
    const val supportLinkUrl: String = "mailto:hello@ordieo.app?subject=ordieo%20Feedback"
}

/**
 * Soundwave logo: rounded square (proportional to 44x44 with 13.dp radius) filled with --accent,
 * containing 4 vertical rounded soundwave bars (proportional 4.dp width, 2.dp radius) in --bg color.
 */
@Composable
fun AppLogo(
    modifier: Modifier = Modifier,
    size: Dp = 44.dp
) {
    val colors = TranscribeThemeTokens.colors
    Canvas(modifier = modifier.size(size)) {
        val scale = this.size.width / 44.dp.toPx()
        val cornerRadiusPx = 13.dp.toPx() * scale
        drawRoundRect(
            color = colors.accent,
            topLeft = Offset.Zero,
            size = this.size,
            cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx)
        )

        val barWidth = 4.dp.toPx() * scale
        val barRadius = CornerRadius(2.dp.toPx() * scale, 2.dp.toPx() * scale)
        val barGap = 4.dp.toPx() * scale
        val barHeights = floatArrayOf(12f, 22f, 17f, 10f)
        val totalBarsWidth = barWidth * barHeights.size + barGap * (barHeights.size - 1)
        val startX = (this.size.width - totalBarsWidth) / 2f
        val centerY = this.size.height / 2f

        barHeights.forEachIndexed { index, heightDp ->
            val barH = heightDp.dp.toPx() * scale
            val x = startX + index * (barWidth + barGap)
            val y = centerY - barH / 2f
            drawRoundRect(
                color = colors.bg,
                topLeft = Offset(x, y),
                size = Size(barWidth, barH),
                cornerRadius = barRadius
            )
        }
    }
}
