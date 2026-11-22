package com.archangel.ai.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.archangel.ai.learn.Academy
import com.archangel.ai.ui.components.HairlineDivider
import com.archangel.ai.ui.components.MonoLabel
import com.archangel.ai.ui.components.Panel
import com.archangel.ai.ui.components.Pill
import com.archangel.ai.ui.components.SectionHeader
import com.archangel.ai.ui.theme.Bone
import com.archangel.ai.ui.theme.Ember
import com.archangel.ai.ui.theme.Smoke
import com.archangel.ai.ui.theme.TextStyles

/**
 * «Обучение»: список уроков по группам или один урок.
 * @param current "" — список, иначе id урока.
 */
@Composable
fun AcademyScreen(
    current: String,
    onOpen: (String?) -> Unit,
    onSpeak: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val lesson = Academy.byId(current)
    BackHandler { onOpen(if (lesson != null) "" else null) }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
    ) {
        Spacer(Modifier.height(10.dp))
        Pill(
            if (lesson != null) "К урокам" else "К рынку",
            onClick = { onOpen(if (lesson != null) "" else null) },
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            height = 40.dp,
        )

        if (lesson == null) {
            SectionHeader("03.A", "Обучение", trailing = Academy.UPDATED, modifier = Modifier.padding(vertical = 14.dp))
            Text(
                "Короткие уроки: от первых шагов и пополнения бирж до стоп-лоссов и психологии. " +
                    "Голосом: «Архангел, научи ставить стоп-лосс» или «как пополнить Bybit».",
                style = TextStyles.BodySmall,
            )
            Academy.groups.forEach { g ->
                Spacer(Modifier.height(22.dp))
                MonoLabel(g, color = Bone)
                Spacer(Modifier.height(6.dp))
                Academy.lessons.filter { it.group == g }.forEach { l ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onOpen(l.id) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(l.title, style = TextStyles.Body, modifier = Modifier.weight(1f))
                        Text("›", style = TextStyles.Body, color = Smoke)
                    }
                    HairlineDivider()
                }
            }
        } else {
            Spacer(Modifier.height(18.dp))
            MonoLabel(lesson.group, color = Smoke)
            Spacer(Modifier.height(6.dp))
            Text(lesson.title, style = TextStyles.Title)
            Spacer(Modifier.height(14.dp))
            Panel(Modifier.fillMaxWidth()) {
                Text(lesson.summary, style = TextStyles.Body.copy(fontSize = 15.sp), color = Bone)
            }
            Spacer(Modifier.height(16.dp))
            lesson.body.forEach { p ->
                val step = p.firstOrNull()?.isDigit() == true || p.startsWith("•")
                Row(Modifier.padding(vertical = 5.dp)) {
                    if (!step) {
                        Text("—", style = TextStyles.Body, color = Ember)
                        Spacer(Modifier.width(10.dp))
                    }
                    Text(p, style = TextStyles.Body.copy(fontSize = 15.sp))
                }
            }
            Spacer(Modifier.height(18.dp))
            Pill("Прочитать вслух", onClick = { onSpeak(lesson.summary + " " + lesson.body.joinToString(" ")) }, icon = Icons.Filled.VolumeUp)

            // Соседние уроки той же группы
            val same = Academy.lessons.filter { it.group == lesson.group && it.id != lesson.id }
            if (same.isNotEmpty()) {
                Spacer(Modifier.height(24.dp))
                MonoLabel("Ещё в разделе «${lesson.group}»", color = Smoke)
                same.forEach { l ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onOpen(l.id) }
                            .padding(vertical = 12.dp),
                    ) { Text(l.title, style = TextStyles.Body, modifier = Modifier.weight(1f)); Text("›", color = Smoke) }
                    HairlineDivider()
                }
            }
        }
        Spacer(Modifier.height(120.dp))
    }
}
