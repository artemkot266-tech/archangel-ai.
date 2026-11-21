package com.archangel.ai.ui.theme

import androidx.compose.ui.graphics.Color

/*
 * Палитра «Обсидиан и угли».
 * Тёплый чёрный, текст цвета кости и ОДИН акцент — тлеющий оранжевый.
 * Никакого неона: акцент работает только там, где важно (голос, риск, падение цены).
 */

// Основа
val Obsidian = Color(0xFF0A0908)
val Surface1 = Color(0xFF131110)
val Surface2 = Color(0xFF1B1815)
val Hairline = Color(0xFF2A2521)
val HairlineSoft = Color(0xFF1F1B18)

// Текст
val Bone = Color(0xFFEDE6DA)
val Ash = Color(0xFF9C948A)
val Smoke = Color(0xFF6E675F)

// Акцент
val Ember = Color(0xFFFF5A1F)
val EmberGlow = Color(0xFFFFB38A)
val EmberDeep = Color(0xFF2A1309)

// Состояния сферы
val Platinum = Color(0xFFD9D4CC)
val Steel = Color(0xFF8E9BB3)
val Frost = Color(0xFFE6ECFF)

// Рынок: рост — полая свеча цвета кости, падение — залитая углём.
val MarketUp = Bone
val MarketDown = Ember
