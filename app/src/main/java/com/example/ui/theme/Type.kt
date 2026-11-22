package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.R

val InterFontFamily = FontFamily(
    Font(R.font.inter, FontWeight.Normal),
    Font(R.font.inter, FontWeight.Medium),
    Font(R.font.inter, FontWeight.SemiBold),
    Font(R.font.inter, FontWeight.Bold)
)

val DevanagariFontFamily = FontFamily(
    Font(R.font.noto_sans_devanagari, FontWeight.Normal),
    Font(R.font.noto_sans_devanagari, FontWeight.Medium),
    Font(R.font.noto_sans_devanagari, FontWeight.SemiBold),
    Font(R.font.noto_sans_devanagari, FontWeight.Bold)
)

/**
 * Automatically selects Noto Sans Devanagari whenever Hindi/Devanagari glyphs are present,
 * falling back to Inter for Latin/Hinglish/English text.
 */
fun fontFamilyForText(text: String): FontFamily {
    for (ch in text) {
        if (ch in '\u0900'..'\u097F') return DevanagariFontFamily
    }
    return InterFontFamily
}

fun TextStyle.withTabularNums(): TextStyle = copy(fontFeatureSettings = "tnum")

object TranscribeTextStyles {
    val HomeAppName = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 29.sp,
        letterSpacing = (-0.48).sp // -0.02em
    )

    val ScreenTitle = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.3).sp
    )

    val StageCaption = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        lineHeight = 37.5.sp // 1.25
    )

    val BigStatValue = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        fontFeatureSettings = "tnum"
    )

    val CardTitle = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 21.sp
    )

    val BodyTranscript = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 23.25.sp // 1.55
    )

    val ListRow = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 19.6.sp // 1.4
    )

    val SubText = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.5.sp
    )

    val SectionLabel = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 1.1.sp // 0.1em
    )

    val MicroPromoLabel = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 10.sp,
        lineHeight = 13.sp,
        letterSpacing = 0.8.sp // 0.08em
    )

    val ButtonLabel = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp
    )
}

val TranscribeTypography = Typography(
    displaySmall = TranscribeTextStyles.StageCaption,
    headlineMedium = TranscribeTextStyles.HomeAppName,
    headlineSmall = TranscribeTextStyles.ScreenTitle,
    titleLarge = TranscribeTextStyles.ScreenTitle,
    titleMedium = TranscribeTextStyles.CardTitle,
    titleSmall = TranscribeTextStyles.ListRow,
    bodyLarge = TranscribeTextStyles.BodyTranscript,
    bodyMedium = TranscribeTextStyles.ListRow,
    bodySmall = TranscribeTextStyles.SubText,
    labelLarge = TranscribeTextStyles.ButtonLabel,
    labelMedium = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 17.sp
    ),
    labelSmall = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp
    )
)
