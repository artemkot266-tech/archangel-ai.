package com.archangel.ai.ui.screens

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.archangel.ai.features.Expense
import com.archangel.ai.features.ExpensePeriod
import com.archangel.ai.features.Expenses
import com.archangel.ai.phone.CommandParser
import com.archangel.ai.tasks.Note
import com.archangel.ai.tasks.Reminder
import com.archangel.ai.ui.components.MonoLabel
import com.archangel.ai.ui.components.Pill
import com.archangel.ai.ui.components.SectionHeader
import com.archangel.ai.ui.theme.Ash
import com.archangel.ai.ui.theme.Bone
import com.archangel.ai.ui.theme.Ember
import com.archangel.ai.ui.theme.Hairline
import com.archangel.ai.ui.theme.Smoke
import com.archangel.ai.ui.theme.Surface1
import com.archangel.ai.ui.theme.TextStyles
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** «Дела»: напоминания, заметки и расходы. Всё создаётся и голосом: «напомни…», «запиши заметку…». */
@Composable
fun TasksScreen(
    notes: List<Note>,
    reminders: List<Reminder>,
    onRefresh: () -> Unit,
    onAddNote: (String) -> Unit,
    onDeleteNote: (Long) -> Unit,
    onDeleteReminder: (Long) -> Unit,
    onSay: (String) -> Unit,
    expenses: List<Expense> = emptyList(),
    onDeleteExpense: (Long) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) { onRefresh() }
    var draft by rememberSaveable { mutableStateOf("") }
    var reminderDraft by rememberSaveable { mutableStateOf("") }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
    ) {
        SectionHeader("03", "Дела", modifier = Modifier.padding(vertical = 14.dp))

        // ── Напоминания
        Text("Напоминания", style = TextStyles.Title)
        Spacer(Modifier.height(4.dp))
        Text("Скажите: «Напомни завтра в 9 позвонить в сервис» или «через 20 минут выключить плиту».", style = TextStyles.BodySmall)
        Spacer(Modifier.height(12.dp))
        InputRow(
            value = reminderDraft,
            placeholder = "Напомни… (например: завтра в 10 купить хлеб)",
            onChange = { reminderDraft = it },
            onDone = {
                if (reminderDraft.isNotBlank()) {
                    val phrase = reminderDraft.trim()
                    onSay(if (phrase.lowercase().startsWith("напомни")) phrase else "напомни $phrase")
                    reminderDraft = ""
                }
            },
        )
        Spacer(Modifier.height(10.dp))
        if (reminders.isEmpty()) {
            Text("Пока пусто.", style = TextStyles.BodySmall.copy(color = Smoke))
        }
        reminders.forEach { r ->
            ItemRow(
                title = r.text,
                sub = CommandParser.describeTime(r.at),
                accent = true,
                onDelete = { onDeleteReminder(r.id) },
            )
        }

        // ── Заметки
        Spacer(Modifier.height(28.dp))
        Text("Заметки", style = TextStyles.Title)
        Spacer(Modifier.height(4.dp))
        Text("Скажите: «Запиши заметку: купить молоко».", style = TextStyles.BodySmall)
        Spacer(Modifier.height(12.dp))
        InputRow(
            value = draft,
            placeholder = "Новая заметка",
            onChange = { draft = it },
            onDone = { if (draft.isNotBlank()) { onAddNote(draft); draft = "" } },
        )
        Spacer(Modifier.height(10.dp))
        if (notes.isEmpty()) {
            Text("Пока пусто.", style = TextStyles.BodySmall.copy(color = Smoke))
        }
        val fmt = SimpleDateFormat("d MMM, HH:mm", Locale("ru"))
        notes.forEach { n ->
            ItemRow(title = n.text, sub = fmt.format(Date(n.created)), accent = false, onDelete = { onDeleteNote(n.id) })
        }

        // ── Расходы
        Spacer(Modifier.height(28.dp))
        Text("Расходы", style = TextStyles.Title)
        Spacer(Modifier.height(4.dp))
        Text("Скажите: «Потратил 200 на кофе» или «Сколько я потратил за неделю?».", style = TextStyles.BodySmall)
        Spacer(Modifier.height(12.dp))
        var expenseDraft by rememberSaveable { mutableStateOf("") }
        InputRow(
            value = expenseDraft,
            placeholder = "Например: 350 такси",
            onChange = { expenseDraft = it },
            onDone = {
                if (expenseDraft.isNotBlank()) {
                    onSay("потратил ${expenseDraft.trim()}")
                    expenseDraft = ""
                }
            },
        )
        Spacer(Modifier.height(10.dp))
        val monthFrom = Expenses.periodStart(ExpensePeriod.MONTH)
        val month = expenses.filter { it.at >= monthFrom }
        if (expenses.isEmpty()) {
            Text("Пока пусто.", style = TextStyles.BodySmall.copy(color = Smoke))
        } else {
            MonoLabel("ЭТОТ МЕСЯЦ · ${Expenses.totalText(month)}", color = Ember)
            Spacer(Modifier.height(6.dp))
        }
        expenses.take(20).forEach { e ->
            ItemRow(
                title = "${Expenses.money(e.amount, e.currency)} — ${e.category}",
                sub = fmt.format(Date(e.at)),
                accent = false,
                onDelete = { onDeleteExpense(e.id) },
            )
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun InputRow(value: String, placeholder: String, onChange: (String) -> Unit, onDone: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .weight(1f)
                .heightIn(min = 52.dp)
                .background(Surface1, RoundedCornerShape(16.dp))
                .border(1.dp, Hairline, RoundedCornerShape(16.dp))
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            if (value.isEmpty()) Text(placeholder, style = TextStyles.Body.copy(color = Smoke))
            BasicTextField(
                value = value,
                onValueChange = onChange,
                textStyle = TextStyles.Body,
                cursorBrush = SolidColor(Ember),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onDone() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.width(8.dp))
        Pill("Добавить", onClick = onDone, enabled = value.isNotBlank())
    }
}

@Composable
private fun ItemRow(title: String, sub: String, accent: Boolean, onDelete: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(6.dp)
                .background(if (accent) Ember else Bone, CircleShape)
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = TextStyles.Body)
            MonoLabel(sub, color = Ash)
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Close, contentDescription = "Удалить", tint = Smoke)
        }
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Hairline)
    )
}
