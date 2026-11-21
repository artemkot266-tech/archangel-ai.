package com.archangel.ai.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.archangel.ai.core.CoreState
import com.archangel.ai.data.ChatMessage
import com.archangel.ai.data.Role
import com.archangel.ai.ui.components.MonoLabel
import com.archangel.ai.ui.components.RoundIcon
import com.archangel.ai.ui.components.SectionHeader
import com.archangel.ai.ui.theme.Ash
import com.archangel.ai.ui.theme.Bone
import com.archangel.ai.ui.theme.Ember
import com.archangel.ai.ui.theme.Hairline
import com.archangel.ai.ui.theme.Smoke
import com.archangel.ai.ui.theme.Surface1
import com.archangel.ai.ui.theme.Surface2
import com.archangel.ai.ui.theme.TextStyles
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ChatScreen(
    messages: List<ChatMessage>,
    state: CoreState,
    partial: String,
    draft: String,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onMic: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val thinking = state == CoreState.THINKING
    val extra = (if (thinking) 1 else 0) + (if (partial.isNotBlank()) 1 else 0)
    LaunchedEffect(messages.size, thinking, partial.isNotBlank()) {
        val total = messages.size + extra
        if (total > 0) listState.animateScrollToItem(total - 1)
    }

    Column(modifier.fillMaxSize()) {
        SectionHeader(
            "02", "Диалог",
            trailing = "${messages.count { it.role != Role.NOTE }}",
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
        )
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            items(messages, key = { it.id }) { MessageRow(it) }
            if (partial.isNotBlank()) item(key = "partial") { UserLine("$partial…", ghost = true) }
            if (thinking) item(key = "thinking") { ThinkingLine() }
        }
        InputBar(
            draft = draft,
            listening = state == CoreState.LISTENING,
            onDraftChange = onDraftChange,
            onSend = onSend,
            onMic = onMic,
        )
    }
}

private val timeFmt = SimpleDateFormat("HH:mm", Locale.US)

@Composable
private fun MessageRow(m: ChatMessage) {
    when (m.role) {
        Role.USER -> UserLine(m.text)
        Role.ASSISTANT -> Column(Modifier.fillMaxWidth()) {
            MonoLabel("Archangel · ${timeFmt.format(Date(m.time))}", color = Smoke)
            Spacer(Modifier.height(6.dp))
            SelectionContainer { Text(m.text, style = TextStyles.Body) }
        }
        Role.NOTE -> Row(verticalAlignment = Alignment.Top) {
            Box(
                Modifier
                    .padding(top = 7.dp)
                    .size(6.dp)
                    .background(Ember, CircleShape)
            )
            Spacer(Modifier.width(10.dp))
            Text(m.text, style = TextStyles.BodySmall.copy(color = Ash))
        }
    }
}

@Composable
private fun UserLine(text: String, ghost: Boolean = false) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Box(
            Modifier
                .widthIn(max = 300.dp)
                .alpha(if (ghost) 0.6f else 1f)
                .background(Surface2, RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp))
                .padding(horizontal = 16.dp, vertical = 11.dp)
        ) {
            Text(text, style = TextStyles.Body)
        }
    }
}

@Composable
private fun ThinkingLine() {
    val t = rememberInfiniteTransition(label = "dots")
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        MonoLabel("Archangel думает", color = Smoke)
        Spacer(Modifier.width(4.dp))
        repeat(3) { i ->
            val a by t.animateFloat(
                initialValue = 0.2f, targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(600, delayMillis = i * 180), RepeatMode.Reverse),
                label = "d$i",
            )
            Box(
                Modifier
                    .size(5.dp)
                    .alpha(a)
                    .background(Bone, CircleShape)
            )
        }
    }
}

@Composable
private fun InputBar(
    draft: String,
    listening: Boolean,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onMic: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .weight(1f)
                .heightIn(min = 52.dp)
                .background(Surface1, RoundedCornerShape(26.dp))
                .border(1.dp, Hairline, RoundedCornerShape(26.dp))
                .padding(horizontal = 20.dp, vertical = 14.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (draft.isEmpty()) Text("Спросите или прикажите…", style = TextStyles.Body.copy(color = Smoke))
            BasicTextField(
                value = draft,
                onValueChange = onDraftChange,
                textStyle = TextStyles.Body,
                cursorBrush = SolidColor(Ember),
                maxLines = 5,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (draft.isBlank()) {
            RoundIcon(
                if (listening) Icons.Filled.Stop else Icons.Filled.Mic,
                description = if (listening) "Остановить запись" else "Голосовой ввод",
                onClick = onMic,
                accent = listening,
            )
        } else {
            RoundIcon(Icons.AutoMirrored.Filled.Send, description = "Отправить", onClick = onSend, accent = true)
        }
    }
}
