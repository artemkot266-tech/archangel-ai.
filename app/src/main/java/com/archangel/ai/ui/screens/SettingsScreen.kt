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
import com.archangel.ai.data.OrbTheme
import androidx.compose.foundation.clickable
import com.archangel.ai.voice.VoiceOption
import androidx.compose.ui.platform.LocalUriHandler
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
    voices: List<VoiceOption>,
    memory: List<String>,
    onForgetFact: (String) -> Unit,
    onSave: (AppSettings) -> Unit,
    onClearHistory: () -> Unit,
    onPreviewVoice: (AppSettings) -> Unit,
    onTestBrain: (AppSettings, (Boolean, String) -> Unit) -> Unit,
    onRestartOnboarding: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uri = LocalUriHandler.current
    var s by remember(settings) { mutableStateOf(settings) }
    var brainStatus by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var checking by remember { mutableStateOf(false) }
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
        Text("Как думает\nи говорит Архангел.", style = TextStyles.Display.copy(fontSize = 30.sp, lineHeight = 36.sp))

        // ── Мозг
        Spacer(Modifier.height(28.dp))
        MonoLabel("Мозг", color = Bone)
        Spacer(Modifier.height(10.dp))
        BrainTiles(Brain.entries, s.brain) { s = s.copy(brain = it) }
        Spacer(Modifier.height(14.dp))
        Field(
            label = "API-ключ ${s.brain.title}",
            value = s.keyFor(),
            onChange = { v -> brainStatus = null; s = s.copy(keys = s.keys + (s.brain to v)) },
            secret = true,
            hint = s.brain.note,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill(
                "Получить ключ",
                onClick = { runCatching { uri.openUri(s.brain.keyUrl) } },
                modifier = Modifier.weight(1f),
            )
            Pill(
                if (checking) "Проверяю…" else "Проверить",
                enabled = s.brainReady && !checking,
                onClick = {
                    checking = true
                    onTestBrain(s) { ok, msg -> brainStatus = ok to msg; checking = false }
                },
                modifier = Modifier.weight(1f),
            )
        }
        brainStatus?.let { (ok, msg) ->
            Text(msg, style = TextStyles.BodySmall.copy(color = if (ok) Bone else Ember), modifier = Modifier.padding(top = 8.dp))
        }
        Spacer(Modifier.height(14.dp))
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

        // ── Обращение
        Spacer(Modifier.height(20.dp))
        MonoLabel("Знакомство", color = Bone)
        Spacer(Modifier.height(10.dp))
        Field(label = "Как обращаться ко мне", value = s.userName, placeholder = "Имя", onChange = { s = s.copy(userName = it) })
        Field(
            label = "Как звать ассистента",
            value = s.wakeName,
            placeholder = "Архангел",
            onChange = { s = s.copy(wakeName = it) },
            hint = "Откликается и на это имя, и на «Архангел»: «${s.wakeName.ifBlank { "Архангел" }}, включи музыку».",
        )
        Toggle("Приветствовать при запуске", s.greetOnLaunch) { s = s.copy(greetOnLaunch = it) }

        // ── Голос
        Spacer(Modifier.height(20.dp))
        MonoLabel("Голос", color = Bone)
        Spacer(Modifier.height(10.dp))
        VoicePicker(s, voices, onChange = { s = it }, onPreview = { onPreviewVoice(s) })
        Spacer(Modifier.height(10.dp))
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

        // ── Режим ожидания
        Spacer(Modifier.height(20.dp))
        MonoLabel("Режим ожидания", color = Bone)
        Spacer(Modifier.height(6.dp))
        Toggle(
            "Отклик на имя без касания",
            s.standby,
            sub = "Пока Архангел открыт на экране, он слушает и откликается на «${s.wakeName.ifBlank { "Архангел" }}». " +
                "Расходует больше батареи; телефон может коротко пикать при перезапуске микрофона.",
        ) { s = s.copy(standby = it) }
        if (s.standby) {
            Toggle(
                "Слушать в фоне — как умная колонка",
                s.standbyBackground,
                sub = "Работает, даже когда приложение свёрнуто или экран выключен. В шторке будет значок «${s.wakeName.ifBlank { "Архангел" }} слушает».",
            ) { s = s.copy(standbyBackground = it) }
            if (s.standbyBackground) {
                Toggle("Только на зарядке", s.standbyOnlyCharging, sub = "Бережёт батарею: слушает, когда телефон заряжается.") {
                    s = s.copy(standbyOnlyCharging = it)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Чтобы телефон не закрывал Архангела в фоне, один раз разрешите (на Poco/Xiaomi это обязательно):",
                    style = TextStyles.BodySmall,
                )
                Spacer(Modifier.height(8.dp))
                val ctx = androidx.compose.ui.platform.LocalContext.current
                Pill("1. Батарея: без ограничений", onClick = { com.archangel.ai.features.BackgroundHelp.batteryUnrestricted(ctx) }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Pill("2. Автозапуск", onClick = { com.archangel.ai.features.BackgroundHelp.autostart(ctx) }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Pill("3. Поверх других окон", onClick = { com.archangel.ai.features.BackgroundHelp.overlay(ctx) }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Pill("4. Всплывающие окна в фоне (Xiaomi)", onClick = { com.archangel.ai.features.BackgroundHelp.xiaomiPermissions(ctx) }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                Text(
                    "3 и 4 нужны, чтобы из фона открывать YouTube, музыку, такси и звонки. В пункте 4 включите " +
                        "«Отображать всплывающие окна в фоне» и «Экран блокировки». И закрепите Архангела в недавних приложениях (замочек на карточке).",
                    style = TextStyles.BodySmall.copy(color = Smoke),
                )
            }
        }

        // ── Умный дом
        Spacer(Modifier.height(20.dp))
        MonoLabel("Умный дом · Home Assistant", color = Bone)
        Spacer(Modifier.height(6.dp))
        Text(
            "Xiaomi, Google, Tuya и другие устройства подключаются к Home Assistant, а я управляю ими голосом: " +
                "«включи свет в спальне», «выключи кондиционер».",
            style = TextStyles.BodySmall,
        )
        Spacer(Modifier.height(10.dp))
        Field(label = "Адрес", value = s.haUrl, placeholder = "http://192.168.1.10:8123", onChange = { s = s.copy(haUrl = it) })
        Field(
            label = "Токен",
            value = s.haToken,
            onChange = { s = s.copy(haToken = it) },
            secret = true,
            hint = "Home Assistant → ваш профиль → Безопасность → Долгосрочные токены доступа → Создать.",
        )

        // ── Сфера
        Spacer(Modifier.height(20.dp))
        MonoLabel("Сфера", color = Bone)
        Spacer(Modifier.height(10.dp))
        ChipFlow(OrbTheme.entries.map { it.title }, s.orbTheme.title) { t ->
            s = s.copy(orbTheme = OrbTheme.entries.first { it.title == t })
        }
        Text("Материал сферы в покое. Во время работы цвет показывает состояние.", style = TextStyles.BodySmall, modifier = Modifier.padding(top = 8.dp))

        // ── Утренний брифинг
        Spacer(Modifier.height(20.dp))
        MonoLabel("Утренний брифинг", color = Bone)
        Spacer(Modifier.height(6.dp))
        Toggle(
            "Каждое утро",
            s.briefingEnabled,
            sub = "Погода, планы, напоминания и рынок — уведомлением в выбранное время. Голосом: «брифинг».",
        ) { s = s.copy(briefingEnabled = it) }
        if (s.briefingEnabled) {
            Spacer(Modifier.height(6.dp))
            ChipFlow((5..11).map { "$it:00" } + listOf("7:30", "8:30"), "${s.briefingHour}:${"%02d".format(s.briefingMinute)}") { t ->
                val (h, m) = t.split(":").map { it.toInt() }
                s = s.copy(briefingHour = h, briefingMinute = m)
            }
        }

        // ── Память
        Spacer(Modifier.height(20.dp))
        MonoLabel("Память", color = Bone)
        Spacer(Modifier.height(6.dp))
        Text("Скажите «запомни, что…» — и я буду это учитывать. «Что ты обо мне знаешь?» — перечислю.", style = TextStyles.BodySmall)
        memory.forEach { f ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(f, style = TextStyles.Body, modifier = Modifier.weight(1f))
                Text("забыть", style = TextStyles.Label, color = Ash, modifier = Modifier.clickable { onForgetFact(f) }.padding(8.dp))
            }
        }

        // ── Биржа
        Spacer(Modifier.height(20.dp))
        MonoLabel("Биржа · Binance", color = Bone)
        Spacer(Modifier.height(10.dp))
        Field(label = "API Key", value = s.binanceKey, onChange = { s = s.copy(binanceKey = it) }, secret = true)
        Field(label = "Secret Key", value = s.binanceSecret, onChange = { s = s.copy(binanceSecret = it) }, secret = true)
        Toggle(
            "Демо-счёт (Binance Demo Trading)",
            s.binanceTestnet,
            sub = if (s.binanceTestnet) "Ключ: Binance → Demo Trading → API Key Management. Деньги ненастоящие"
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

        Spacer(Modifier.height(10.dp))
        Pill("Пройти знакомство заново", onClick = onRestartOnboarding, modifier = Modifier.fillMaxWidth())

        Spacer(Modifier.height(32.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("ARCHANGEL", style = TextStyles.Wordmark.copy(color = Smoke))
            Spacer(Modifier.width(10.dp))
            MonoLabel("v${BuildConfig.VERSION_NAME}", color = Smoke)
        }
        Spacer(Modifier.height(32.dp))
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ChipFlow(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { o -> com.archangel.ai.ui.components.Chip(o, onClick = { onSelect(o) }, selected = o == selected) }
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
