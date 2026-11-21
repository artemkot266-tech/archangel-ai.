package com.archangel.ai.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.archangel.ai.AssistantViewModel
import com.archangel.ai.Greetings
import com.archangel.ai.data.AppSettings
import com.archangel.ai.data.Brain
import com.archangel.ai.data.VoiceEngine
import com.archangel.ai.ui.components.Chip
import com.archangel.ai.ui.components.MonoLabel
import com.archangel.ai.ui.components.Panel
import com.archangel.ai.ui.components.Pill
import com.archangel.ai.ui.components.RoundIcon
import com.archangel.ai.ui.core.ORB_ASPECT
import com.archangel.ai.ui.core.Orb
import com.archangel.ai.ui.theme.Ash
import com.archangel.ai.ui.theme.Bone
import com.archangel.ai.ui.theme.Ember
import com.archangel.ai.ui.theme.Hairline
import com.archangel.ai.ui.theme.Obsidian
import com.archangel.ai.ui.theme.Smoke
import com.archangel.ai.ui.theme.Surface1
import com.archangel.ai.ui.theme.TextStyles

private const val STEPS = 6

/**
 * Знакомство при первом запуске: Архангел здоровается голосом, рассказывает о себе,
 * узнаёт имя, помогает подключить «мозг» и выбрать голос.
 */
@Composable
fun OnboardingScreen(
    vm: AssistantViewModel,
    initial: AppSettings,
    onMic: () -> Unit,
    onFinish: (AppSettings) -> Unit,
) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    var draft by remember { mutableStateOf(initial) }
    val name = draft.userName.trim()

    // Каждый шаг Архангел проговаривает вслух
    LaunchedEffect(step) { vm.speakLine(lineFor(step, name), draft) }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
    ) {
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(STEPS) { i ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(3.dp)
                            .background(if (i <= step) Bone else Hairline, RoundedCornerShape(2.dp))
                    )
                }
            }
        }
        if (step < STEPS - 1) {
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Pill(
                    "Пропустить знакомство",
                    onClick = { vm.stopSpeaking(); onFinish(draft.copy(onboardingDone = true)) },
                    height = 36.dp,
                )
            }
        }

        // Сфера — живой собеседник; касание повторяет фразу
        Box(Modifier.fillMaxWidth().height(190.dp), contentAlignment = Alignment.Center) {
            Orb(
                state = vm.core,
                level = vm.level,
                modifier = Modifier
                    .height(190.dp)
                    .aspectRatio(ORB_ASPECT),
                onClick = { vm.speakLine(lineFor(step, name), draft) },
            )
        }

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            when (step) {
                0 -> Welcome()
                1 -> Features()
                2 -> NameStep(
                    name = draft.userName,
                    listening = vm.core == com.archangel.ai.core.CoreState.LISTENING,
                    partial = vm.partial,
                    onChange = { draft = draft.copy(userName = it) },
                    onMic = {
                        vm.dictation = { heard -> draft = draft.copy(userName = cleanName(heard)) }
                        onMic()
                    },
                )
                3 -> BrainStep(draft, onChange = { draft = it }, onTest = vm::testBrain)
                4 -> VoiceStep(draft, vm.voiceOptions, onChange = { draft = it }, onPreview = { vm.previewVoice(draft) })
                else -> DoneStep(name)
            }
            Spacer(Modifier.height(16.dp))
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (step > 0) Pill("Назад", onClick = { step -= 1 })
            val primary = when (step) {
                0 -> "Начать"
                2 -> if (name.isBlank()) "Пропустить" else "Дальше"
                3 -> if (draft.brainReady) "Дальше" else "Позже"
                STEPS - 1 -> "Начать работу"
                else -> "Дальше"
            }
            Pill(
                primary,
                accent = true,
                onClick = {
                    if (step < STEPS - 1) step += 1
                    else { vm.stopSpeaking(); onFinish(draft.copy(onboardingDone = true)) }
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private fun lineFor(step: Int, name: String): String = when (step) {
    0 -> "${Greetings.forNow()}. Я — Архангел, ваш личный ассистент. Позвольте коротко рассказать, " +
        "что я умею, и мы всё настроим. Это займёт пару минут."
    1 -> "Я понимаю голос и отвечаю голосом. Могу позвонить, написать сообщение, поставить будильник " +
        "или таймер, открыть приложение и проложить маршрут. Слежу за рынком, показываю графики и делаю разбор. " +
        "Звонки и сделки выполняю только с вашего подтверждения."
    2 -> "Как мне к вам обращаться? Напишите имя или нажмите на микрофон и скажите его."
    3 -> (if (name.isNotBlank()) "$name, ч" else "Ч") + "тобы думать и отвечать, мне нужен мозг — ключ одной " +
        "из нейросетей. Есть бесплатные варианты: Groq, Gemini или OpenRouter. Выберите любой, я подскажу, что делать."
    4 -> "Теперь выберем мой голос. Нажмите «Послушать», чтобы сравнить варианты."
    else -> "Всё готово" + (if (name.isNotBlank()) ", $name" else "") +
        ". Нажмите на сферу и говорите. Я к вашим услугам."
}

/** «Меня зовут Артём.» → «Артём». */
private fun cleanName(heard: String): String {
    var s = heard.trim().trimEnd('.', '!', '?')
    listOf("меня зовут", "зови меня", "называй меня", "обращайся ко мне", "я —", "я -", "я ")
        .forEach { p -> if (s.lowercase().startsWith(p)) s = s.substring(p.length).trim() }
    return s.split(" ").filter { it.isNotBlank() }.take(3)
        .joinToString(" ") { w -> w.replaceFirstChar { it.uppercaseChar() } }
}

@Composable
private fun Welcome() {
    Text("Добро пожаловать.", style = TextStyles.Display)
    Spacer(Modifier.height(12.dp))
    Text(
        "Я — Архангел, ваш личный ИИ-ассистент. За пару минут расскажу, что умею, и мы всё настроим: " +
            "как к вам обращаться, какой мозг подключить и каким голосом мне говорить.",
        style = TextStyles.Body.copy(color = Ash),
    )
}

@Composable
private fun Features() {
    Text("Что я умею", style = TextStyles.Display)
    Spacer(Modifier.height(18.dp))
    listOf(
        "Голос" to "Нажмите на сферу и говорите — отвечу голосом.",
        "Телефон" to "Звонки, SMS, будильники, таймеры, фонарик, приложения, маршрут.",
        "Рынок" to "Живые графики Binance, индикаторы и разбор от ИИ.",
        "Сделки" to "Ордера на бирже — только после вашего «Да». По умолчанию демо-счёт.",
        "Безопасность" to "Ключи шифруются и хранятся только на этом телефоне.",
    ).forEachIndexed { i, (title, text) ->
        Row(Modifier.padding(vertical = 9.dp)) {
            Box(
                Modifier
                    .padding(top = 7.dp)
                    .size(6.dp)
                    .background(if (i == 3) Ember else Bone, CircleShape)
            )
            Spacer(Modifier.width(14.dp))
            Column {
                Text(title, style = TextStyles.Title.copy(fontSize = 18.sp))
                Text(text, style = TextStyles.BodySmall)
            }
        }
    }
}

@Composable
private fun NameStep(
    name: String,
    listening: Boolean,
    partial: String,
    onChange: (String) -> Unit,
    onMic: () -> Unit,
) {
    Text("Как мне к вам\nобращаться?", style = TextStyles.Display)
    Spacer(Modifier.height(20.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .weight(1f)
                .heightIn(min = 56.dp)
                .background(Surface1, RoundedCornerShape(18.dp))
                .border(1.dp, Hairline, RoundedCornerShape(18.dp))
                .padding(horizontal = 18.dp, vertical = 16.dp),
        ) {
            if (name.isEmpty()) Text(if (listening && partial.isNotBlank()) partial else "Ваше имя", style = TextStyles.Body.copy(color = Smoke))
            BasicTextField(
                value = name,
                onValueChange = { onChange(it.take(40)) },
                singleLine = true,
                textStyle = TextStyles.Body.copy(fontSize = 18.sp),
                cursorBrush = SolidColor(Ember),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.width(10.dp))
        RoundIcon(Icons.Filled.Mic, if (listening) "Слушаю" else "Сказать имя", onClick = onMic, accent = listening)
    }
    Spacer(Modifier.height(10.dp))
    Text("Можно имя, отчество или «сэр» — как вам приятнее.", style = TextStyles.BodySmall)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BrainStep(draft: AppSettings, onChange: (AppSettings) -> Unit, onTest: (AppSettings, (Boolean, String) -> Unit) -> Unit) {
    val uri = LocalUriHandler.current
    val clipboard = LocalClipboardManager.current
    var status by remember(draft.brain) { mutableStateOf<Pair<Boolean, String>?>(null) }
    var checking by remember { mutableStateOf(false) }
    var reveal by remember { mutableStateOf(false) }

    Text("Подключим\nмой мозг.", style = TextStyles.Display)
    Spacer(Modifier.height(8.dp))
    Text("Нужен ключ одной нейросети. Начните с бесплатного — сменить можно в любой момент.", style = TextStyles.BodySmall)

    Spacer(Modifier.height(18.dp))
    MonoLabel("Бесплатно")
    Spacer(Modifier.height(8.dp))
    BrainTiles(Brain.entries.filter { it.free }, draft.brain) { onChange(draft.copy(brain = it)) }
    Spacer(Modifier.height(14.dp))
    MonoLabel("Платно")
    Spacer(Modifier.height(8.dp))
    BrainTiles(listOf(Brain.DEEPSEEK, Brain.CLAUDE), draft.brain) { onChange(draft.copy(brain = it)) }

    Spacer(Modifier.height(18.dp))
    Panel(Modifier.fillMaxWidth()) {
        Column {
            Text("Как получить ключ ${draft.brain.title}", style = TextStyles.Title.copy(fontSize = 18.sp))
            Spacer(Modifier.height(10.dp))
            StepLine(1, "Откройте сайт и войдите (можно через Google).")
            StepLine(2, "Нажмите «Create API Key» — «Создать ключ».")
            StepLine(3, "Скопируйте ключ и вставьте его ниже.")
            Spacer(Modifier.height(12.dp))
            Pill(
                "Открыть ${draft.brain.keyUrl.removePrefix("https://").substringBefore('/')}",
                onClick = { runCatching { uri.openUri(draft.brain.keyUrl) } },
                accent = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .background(Obsidian, RoundedCornerShape(16.dp))
                    .border(1.dp, Hairline, RoundedCornerShape(16.dp))
                    .padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f).padding(vertical = 14.dp)) {
                    if (draft.keyFor().isEmpty()) Text("Вставьте ключ", style = TextStyles.Mono.copy(color = Smoke))
                    BasicTextField(
                        value = draft.keyFor(),
                        onValueChange = { v -> status = null; onChange(draft.copy(keys = draft.keys + (draft.brain to v.trim()))) },
                        singleLine = true,
                        textStyle = TextStyles.Mono,
                        cursorBrush = SolidColor(Ember),
                        visualTransformation = if (reveal) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                IconButton(onClick = { reveal = !reveal }) {
                    Icon(if (reveal) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, if (reveal) "Скрыть" else "Показать", tint = Ash)
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("Вставить", onClick = {
                    clipboard.getText()?.text?.trim()?.takeIf { it.isNotEmpty() }?.let { v ->
                        status = null
                        onChange(draft.copy(keys = draft.keys + (draft.brain to v)))
                    }
                }, modifier = Modifier.weight(1f))
                Pill(
                    if (checking) "Проверяю…" else "Проверить",
                    enabled = draft.brainReady && !checking,
                    onClick = {
                        checking = true
                        onTest(draft) { ok, msg -> status = ok to msg; checking = false }
                    },
                    modifier = Modifier.weight(1f),
                )
            }
            status?.let { (ok, msg) ->
                Spacer(Modifier.height(10.dp))
                Text(msg, style = TextStyles.BodySmall.copy(color = if (ok) Bone else Ember))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BrainTiles(options: List<Brain>, selected: Brain, onSelect: (Brain) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { b ->
            val on = b == selected
            Surface(
                onClick = { onSelect(b) },
                shape = RoundedCornerShape(16.dp),
                color = if (on) Bone else Color.Transparent,
                border = if (on) null else BorderStroke(1.dp, Hairline),
            ) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Text(b.title, style = TextStyles.Button, color = if (on) Obsidian else Bone)
                    Text(b.note, style = TextStyles.Label.copy(fontSize = 11.sp), color = if (on) Obsidian.copy(alpha = 0.65f) else Smoke)
                }
            }
        }
    }
}

@Composable
private fun StepLine(n: Int, text: String) {
    Row(Modifier.padding(vertical = 4.dp)) {
        Text("$n", style = TextStyles.Mono.copy(color = Ember), modifier = Modifier.width(22.dp))
        Text(text, style = TextStyles.BodySmall.copy(color = Bone))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VoicePicker(
    draft: AppSettings,
    voices: List<com.archangel.ai.voice.VoiceOption>,
    onChange: (AppSettings) -> Unit,
    onPreview: () -> Unit,
) {
    var reveal by remember { mutableStateOf(false) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        VoiceEngine.entries.forEach { e ->
            Chip(e.title, onClick = { onChange(draft.copy(voiceEngine = e)) }, selected = draft.voiceEngine == e)
        }
    }
    Spacer(Modifier.height(14.dp))
    if (draft.voiceEngine == VoiceEngine.SYSTEM) {
        if (voices.isEmpty()) {
            Text("Голоса загружаются… Если список пуст, установите «Синтезатор речи Google» из Play Маркета.", style = TextStyles.BodySmall)
        } else {
            MonoLabel("Голос")
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("Авто", onClick = { onChange(draft.copy(systemVoice = "")) }, selected = draft.systemVoice.isBlank())
                voices.take(10).forEach { v ->
                    Chip(v.label, onClick = { onChange(draft.copy(systemVoice = v.name)) }, selected = draft.systemVoice == v.name)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Тон", style = TextStyles.Body, modifier = Modifier.weight(1f))
            MonoLabel(if (draft.voicePitch < 0.95f) "ниже, солиднее" else if (draft.voicePitch > 1.05f) "выше" else "обычный", color = Bone)
        }
        Slider(
            value = draft.voicePitch,
            onValueChange = { onChange(draft.copy(voicePitch = it)) },
            valueRange = 0.7f..1.3f,
            colors = SliderDefaults.colors(thumbColor = Bone, activeTrackColor = Bone, inactiveTrackColor = Hairline),
        )
    } else {
        Text(
            "Студийный голос как в кино. Ключ: elevenlabs.io → профиль → API Keys. " +
                "По умолчанию — «George», спокойный британский баритон. Есть бесплатный лимит на месяц.",
            style = TextStyles.BodySmall,
        )
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .background(Surface1, RoundedCornerShape(16.dp))
                .border(1.dp, Hairline, RoundedCornerShape(16.dp))
                .padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f).padding(vertical = 14.dp)) {
                if (draft.elevenKey.isEmpty()) Text("Ключ ElevenLabs", style = TextStyles.Mono.copy(color = Smoke))
                BasicTextField(
                    value = draft.elevenKey,
                    onValueChange = { onChange(draft.copy(elevenKey = it.trim())) },
                    singleLine = true,
                    textStyle = TextStyles.Mono,
                    cursorBrush = SolidColor(Ember),
                    visualTransformation = if (reveal) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            IconButton(onClick = { reveal = !reveal }) {
                Icon(if (reveal) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, if (reveal) "Скрыть" else "Показать", tint = Ash)
            }
        }
        val uri = LocalUriHandler.current
        Spacer(Modifier.height(8.dp))
        Pill("Открыть elevenlabs.io", onClick = { runCatching { uri.openUri("https://elevenlabs.io/app/settings/api-keys") } }, modifier = Modifier.fillMaxWidth())
    }
    Spacer(Modifier.height(14.dp))
    Pill("Послушать", onClick = onPreview, filledBone = true, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun VoiceStep(
    draft: AppSettings,
    voices: List<com.archangel.ai.voice.VoiceOption>,
    onChange: (AppSettings) -> Unit,
    onPreview: () -> Unit,
) {
    Text("Мой голос.", style = TextStyles.Display)
    Spacer(Modifier.height(8.dp))
    Text("Выберите голос и нажмите «Послушать». Всё это можно поменять в настройках.", style = TextStyles.BodySmall)
    Spacer(Modifier.height(18.dp))
    VoicePicker(draft, voices, onChange, onPreview)
}

@Composable
private fun DoneStep(name: String) {
    Text("Всё готово" + (if (name.isNotBlank()) ",\n$name." else "."), style = TextStyles.Display)
    Spacer(Modifier.height(14.dp))
    Text("Нажмите на сферу и скажите, например:", style = TextStyles.BodySmall)
    Spacer(Modifier.height(10.dp))
    listOf("«Какая погода завтра?»", "«Поставь будильник на 7:30»", "«График биткоина»", "«Позвони маме»").forEach {
        Text(it, style = TextStyles.Body, modifier = Modifier.padding(vertical = 4.dp))
    }
}
