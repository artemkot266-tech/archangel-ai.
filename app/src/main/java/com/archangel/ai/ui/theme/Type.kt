package com.archangel.ai.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.archangel.ai.R

/** Geist — единый шрифт интерфейса: простой и современный. */
val Geist = FontFamily(
    Font(R.font.geist_light, FontWeight.Light),
    Font(R.font.geist_regular, FontWeight.Normal),
    Font(R.font.geist_medium, FontWeight.Medium),
)

/** Geist Mono — только для цен и чисел, чтобы цифры не «прыгали». */
val GeistMono = FontFamily(
    Font(R.font.geist_mono, FontWeight.Normal),
    Font(R.font.geist_mono_medium, FontWeight.Medium),
)

object TextStyles {
    val Display = TextStyle(
        fontFamily = Geist, fontWeight = FontWeight.Medium,
        fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = (-0.02).em, color = Bone,
    )
    val Title = TextStyle(
        fontFamily = Geist, fontWeight = FontWeight.Medium,
        fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = (-0.01).em, color = Bone,
    )
    val Body = TextStyle(fontFamily = Geist, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp, color = Bone)
    val BodySmall = TextStyle(fontFamily = Geist, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp, color = Ash)
    val Button = TextStyle(fontFamily = Geist, fontWeight = FontWeight.Medium, fontSize = 15.sp)
    val Label = TextStyle(fontFamily = Geist, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp, color = Ash)
    val Mono = TextStyle(fontFamily = GeistMono, fontWeight = FontWeight.Normal, fontSize = 13.sp, color = Bone)
    /** Крупные числа: цена, сумма. Моноширинные цифры, обычное начертание. */
    val Digits = TextStyle(
        fontFamily = Geist, fontWeight = FontWeight.Medium, fontSize = 40.sp,
        letterSpacing = (-0.02).em, fontFeatureSettings = "tnum", color = Bone,
    )
    /** Логотип-надпись. */
    val Wordmark = TextStyle(
        fontFamily = Geist, fontWeight = FontWeight.Medium, fontSize = 15.sp,
        letterSpacing = 0.18.em, color = Bone,
    )
}
