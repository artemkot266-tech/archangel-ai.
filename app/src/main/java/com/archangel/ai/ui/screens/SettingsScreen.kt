package com.archangel.ai.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.archangel.ai.BuildConfig
import com.archangel.ai.data.AppSettings
import com.archangel.ai.data.Brain
import com.archangel.ai.ui.components.MonoLabel
import com.archangel.ai.ui.components.Pill
import com.archangel.ai.ui.components.SectionHeader
import com.archangel.ai.ui.theme.Ash
import com.archangel.ai.ui.theme.Bone
import com.archangel.ai.ui.theme.Ember
import com.archangel.ai.ui.theme.Hairline
import com.archangel.ai.ui.theme.Obsidian
import com.archangel.ai.ui.theme.Smoke
import com.archangel.ai.ui.theme.Surface1
import com.archangel.ai.ui.theme.Surface2
import com.archangel.ai.ui.theme.TextStyles
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun SettingsScreen(
    settings: AppSettings,
    onSave: (AppSettings) -> Unit,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var s by remember(settings) { mutableStateOf(settings) }
    var saved by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    LaunchedEffect(saved) { if (saved) { delay(2000); saved = false } }
    val dirty = s != settings

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
    ) {
        SectionHeader("04", "Настройки", modifier = Modifier.padding(vertical = 14.dp))
        Text("Как думает\nи говорит Архангел.", style = TextStyles.Display.copy(fontSize = 34.sp, lineHeight = 36.sp))

        // ── Мозг
        Spacer(Modifier.height(28.dp))
        MonoLabel("Мозг", color = Bone)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Brain.entries.forEach { b ->
                val active = s.brain == b
                Surface(
                    onClick = { s = s.copy(brain = b) },
                    shape = RoundedCornerShape(16.dp),
                    color = if (active) Bone else Color.Transparent,
                    border = if (active) null else BorderStroke(1.dp, Hairline),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 64.dp),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            if (b == Brain.OPENAI) "Другой" else b.title,
                            style = TextStyles.Button,
                            color = if (active) Obsidian else Bone,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            if (s.keyFor(b).isNotBlank()) "ключ есть" else "нет ключа",
                            style = TextStyles.Label.copy(fontSize = 9.5.sp),
                            color = if (active) Obsidian.copy(alpha = 0.6f) else Smoke,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Field(
            label = "API-ключ ${s.brain.title}",
            value = s.keyFor(),
            onChange = { v -> s = s.copy(keys = s.keys + (s.brain to v)) },
            secret = true,
            hint = "Получить: ${s.brain.keyHint}",
        )
        Field(
            label = "Модель",
            value = s.models[s.brain].orEmpty(),
            placeholder = s.brain.defaultModel,
            onChange = { v -> s = s.copy(models = s.models + (s.brain to v)) },
        )
        Field(
            label = "Адрес API",
            value = s.baseUrls[s.brain].orEmpty(),
            placeholder = s.brain.defaultBaseUrl,
            onChange = { v -> s = s.copy(baseUrls = s.baseUrls + (s.brain to v)) },
            hint = if (s.brain == Brain.OPENAI) "Подойдёт OpenAI, OpenRouter, Groq и любой совместимый сервис" else null,
        )
        Field(
            label = "Характер",
            value = s.persona,
            onChange = { s = s.copy(persona = it) },
            singleLine = false,
        )

        // ── Голос
        Spacer(Modifier.height(20.dp))
        MonoLabel("Голос", color = Bone)
        Spacer(Modifier.height(6.dp))
        Toggle("Озвучивать ответы", s.speakReplies) { s = s.copy(speakReplies = it) }
        Toggle("Отправлять сразу после фразы", s.autoSendVoice) { s = s.copy(autoSendVoice = it) }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
            Text("Скорость речи", style = TextStyles.Body, modifier = Modifier.weight(1f))
            MonoLabel(String.format(Locale.US, "%.2f×", s.speechRate), color = Bone)
        }
        Slider(
            value = s.speechRate,
            onValueChange = { s = s.copy(speechRate = it) },
            valueRange = 0.6f..1.8f,
            colors = SliderDefaults.colors(
                thumbColor = Bone, activeTrackColor = Bone, inactiveTrackColor = Hairline,
            ),
        )

        // ── Биржа
        Spacer(Modifier.height(20.dp))
        MonoLabel("Биржа · Binance", color = Bone)
        Spacer(Modifier.height(10.dp))
        Field(label = "API Key", value = s.binanceKey, onChange = { s = s.copy(binanceKey = it) }, secret = true)
        Field(label = "Secret Key", value = s.binanceSecret, onChange = { s = s.copy(binanceSecret = it) }, secret = true)
        Toggle(
            "Тестовая биржа (testnet)",
            s.binanceTestnet,
            sub = if (s.binanceTestnet) "Ключ с testnet.binance.vision, деньги ненастоящие"
            else "ВНИМАНИЕ: сделки на реальные деньги",
            subColor = if (s.binanceTestnet) Smoke else Ember,
        ) { s = s.copy(binanceTestnet = it) }
        Text(
            "Создавайте ключ только с правом спот-торговли и БЕЗ права вывода средств. " +
                "Ключи хранятся зашифрованными только на этом телефоне.",
            style = TextStyles.BodySmall,
            modifier = Modifier.padding(top = 6.dp),
        )

        // ── Действия
        Spacer(Modifier.height(24.dp))
        Pill(
            if (saved) "Сохранено" else "Сохранить",
            onClick = { onSave(s); saved = true },
            accent = dirty,
            filledBone = !dirty,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        Pill(
            if (confirmClear) "Точно очистить? Нажмите ещё раз" else "Очистить историю чата",
            onClick = {
                if (confirmClear) { onClearHistory(); confirmClear = false } else confirmClear = true
            },
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(32.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("ARCHANGEL", style = TextStyles.Digits.copy(fontSize = 15.sp, color = Smoke))
            Spacer(Modifier.width(10.dp))
            MonoLabel("v${BuildConfig.VERSION_NAME}", color = Smoke)
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    placeholder: String = "",
    secret: Boolean = false,
    singleLine: Boolean = true,
    hint: String? = null,
) {
    var reveal by remember { mutableStateOf(false) }
    Column(Modifier.padding(bottom = 12.dp)) {
        MonoLabel(label)
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .background(Surface1, RoundedCornerShape(16.dp))
                .border(1.dp, Hairline, RoundedCornerShape(16.dp))
                .padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .padding(vertical = 14.dp)
            ) {
                if (value.isEmpty() && placeholder.isNotEmpty()) {
                    Text(placeholder, style = TextStyles.Mono.copy(color = Smoke))
                }
                BasicTextField(
                    value = value,
                    onValueChange = onChange,
                    singleLine = singleLine,
                    maxLines = if (singleLine) 1 else 6,
                    textStyle = if (singleLine) TextStyles.Mono else TextStyles.Body.copy(fontSize = 15.sp),
                    cursorBrush = SolidColor(Ember),
                    visualTransformation = if (secret && !reveal) PasswordVisualTransformation() else VisualTransformation.None,
                    keyboardOptions = if (secret) KeyboardOptions(keyboardType = KeyboardType.Password) else KeyboardOptions.Default,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (secret) {
                IconButton(onClick = { reveal = !reveal }) {
                    Icon(
                        if (reveal) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = if (reveal) "Скрыть" else "Показать",
                        tint = Ash,
                    )
                }
            } else Spacer(Modifier.width(12.dp))
        }
        if (hint != null) {
            Spacer(Modifier.height(4.dp))
            Text(hint, style = TextStyles.BodySmall.copy(fontSize = 12.sp, color = Smoke))
        }
    }
}

@Composable
private fun Toggle(
    title: String,
    checked: Boolean,
    sub: String? = null,
    subColor: Color = Smoke,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = TextStyles.Body)
            if (sub != null) Text(sub, style = TextStyles.BodySmall.copy(fontSize = 12.sp, color = subColor))
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Obsidian,
                checkedTrackColor = Bone,
                checkedBorderColor = Bone,
                uncheckedThumbColor = Ash,
                uncheckedTrackColor = Surface2,
                uncheckedBorderColor = Hairline,
            ),
        )
    }
}
