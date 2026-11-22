package com.archangel.ai.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.archangel.ai.core.CoreState
import com.archangel.ai.data.AppSettings
import com.archangel.ai.data.ChatMessage
import com.archangel.ai.data.Role
import com.archangel.ai.ui.components.Chip
import com.archangel.ai.ui.components.MonoLabel
import com.archangel.ai.ui.core.ORB_ASPECT
import com.archangel.ai.ui.core.Orb
import com.archangel.ai.ui.theme.Ash
import com.archangel.ai.ui.theme.Bone
import com.archangel.ai.ui.theme.Ember
import com.archangel.ai.ui.theme.TextStyles
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun CoreScreen(
    state: CoreState,
    level: Float,
    partial: String,
    hint: String,
    settings: AppSettings,
    lastReply: ChatMessage?,
    onCoreTap: () -> Unit,
    onQuickCommand: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(20_000); now = System.currentTimeMillis()
        }
    }

    Column(
        modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
    ) {
        Spacer(Modifier.height(12.dp))
        // ── Шапка: логотип и состояние мозга
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("ARCHANGEL", style = TextStyles.Wordmark)
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .size(6.dp)
                    .background(if (settings.brainReady) Bone else Ember, CircleShape)
            )
            Spacer(Modifier.width(8.dp))
            MonoLabel(
                if (settings.brainReady) "Мозг · ${settings.brain.title}" else "Мозг не подключён",
                color = if (settings.brainReady) Ash else Ember,
            )
        }

        Spacer(Modifier.height(28.dp))
        // ── Приветствие: живой «человеческий» голос интерфейса
        Text(greeting(now), style = TextStyles.Display)
        Spacer(Modifier.height(6.dp))
        MonoLabel(SimpleDateFormat("EEE · d MMMM · HH:mm", Locale("ru")).format(Date(now)))

        // ── Сфера
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            Orb(
                state = state,
                level = level,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(ORB_ASPECT),
                theme = settings.orbTheme,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onCoreTap()
                },
            )
        }

        // ── Состояние и то, что сказано
        AnimatedContent(
            targetState = state,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "state",
            modifier = Modifier.fillMaxWidth(),
        ) { s ->
            Text(
                s.label + if (s == CoreState.IDLE) "." else "…",
                style = TextStyles.Title,
                color = if (s == CoreState.LISTENING) Ember else Bone,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(8.dp))
        val caption = when {
            partial.isNotBlank() -> "«$partial»"
            hint.isNotBlank() -> hint
            state == CoreState.IDLE && settings.standby -> "Режим ожидания: скажите «${settings.wakeName}» — и команду"
            state == CoreState.IDLE && lastReply != null -> lastReply.text
            else -> state.hint
        }
        Text(
            caption,
            style = TextStyles.BodySmall.copy(color = if (partial.isNotBlank()) Bone else Ash),
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 60.dp),
        )

        Spacer(Modifier.height(12.dp))
        // ── Быстрые команды
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf("График биткоина", "Таймер на 5 минут", "Включи фонарик", "Что нового в мире ИИ?")
                .forEach { Chip(it, onClick = { onQuickCommand(it) }) }
        }
        Spacer(Modifier.height(16.dp))
    }
}

/** Последний ответ ассистента — подпись под сферой в покое. */
fun lastAssistantReply(messages: List<ChatMessage>): ChatMessage? =
    messages.lastOrNull { it.role == Role.ASSISTANT }

private fun greeting(now: Long): String {
    val h = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.HOUR_OF_DAY)
    return when (h) {
        in 5..11 -> "Доброе утро."
        in 12..17 -> "Добрый день."
        in 18..22 -> "Добрый вечер."
        else -> "Доброй ночи."
    }
}
