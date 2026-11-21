package com.archangel.ai.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.archangel.ai.R

/** Geist — основной текст интерфейса. */
val Geist = FontFamily(
    Font(R.font.geist_light, FontWeight.Light),
    Font(R.font.geist_regular, FontWeight.Normal),
    Font(R.font.geist_medium, FontWeight.Medium),
)

/** Geist Mono — служебные подписи КАПСОМ. */
val GeistMono = FontFamily(
    Font(R.font.geist_mono, FontWeight.Normal),
    Font(R.font.geist_mono_medium, FontWeight.Medium),
)

/** Cormorant Italic — «человеческий» голос: приветствия, состояния, заголовки. */
val Cormorant = FontFamily(
    Font(R.font.cormorant_italic, FontWeight.Medium, FontStyle.Italic),
    Font(R.font.cormorant_regular, FontWeight.Normal),
)

/** Doto — точечный шрифт для цифр и латинского логотипа (кириллицы в нём нет). */
val Doto = FontFamily(Font(R.font.doto_bold, FontWeight.Bold))

object TextStyles {
    val Display = TextStyle(
        fontFamily = Cormorant, fontStyle = FontStyle.Italic, fontWeight = FontWeight.Medium,
        fontSize = 42.sp, lineHeight = 44.sp, letterSpacing = (-0.01).em, color = Bone,
    )
    val Title = TextStyle(
        fontFamily = Cormorant, fontStyle = FontStyle.Italic, fontWeight = FontWeight.Medium,
        fontSize = 30.sp, lineHeight = 34.sp, color = Bone,
    )
    val Body = TextStyle(fontFamily = Geist, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp, color = Bone)
    val BodySmall = TextStyle(fontFamily = Geist, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp, color = Ash)
    val Button = TextStyle(fontFamily = Geist, fontWeight = FontWeight.Medium, fontSize = 15.sp, letterSpacing = 0.01.em)
    val Label = TextStyle(
        fontFamily = GeistMono, fontWeight = FontWeight.Medium, fontSize = 10.5.sp,
        letterSpacing = 0.16.em, color = Ash,
    )
    val Mono = TextStyle(fontFamily = GeistMono, fontWeight = FontWeight.Normal, fontSize = 13.sp, color = Bone)
    val Digits = TextStyle(fontFamily = Doto, fontWeight = FontWeight.Bold, fontSize = 44.sp, letterSpacing = 0.02.em, color = Bone)
}
