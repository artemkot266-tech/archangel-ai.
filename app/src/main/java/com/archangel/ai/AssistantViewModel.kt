package com.archangel.ai

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.archangel.ai.ai.FriendlyException
import com.archangel.ai.ai.Turn
import com.archangel.ai.core.CoreState
import com.archangel.ai.data.AppSettings
import com.archangel.ai.data.ChatMessage
import com.archangel.ai.data.Role
import com.archangel.ai.market.Indicators
import com.archangel.ai.phone.AppScreen
import com.archangel.ai.phone.CommandParser
import com.archangel.ai.phone.PhoneAction
import com.archangel.ai.phone.PhoneController
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.archangel.ai.phone.MusicApp
import com.archangel.ai.phone.TaxiApp
import com.archangel.ai.tasks.TaskStore
import com.archangel.ai.data.MemoryStore
import com.archangel.ai.features.CalendarHelper
import com.archangel.ai.features.Guardian
import com.archangel.ai.features.HomeAssistant
import com.archangel.ai.features.ImageUtil
import com.archangel.ai.features.Weather
import com.archangel.ai.market.AlertScheduler
import com.archangel.ai.market.AlertStore
import com.archangel.ai.voice.Speaker
import com.archangel.ai.voice.VoiceOption
import com.archangel.ai.voice.SpeechInput
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Действие, ожидающее подтверждения пользователя. */
data class PendingAction(val action: PhoneAction, val title: String, val details: String, val danger: Boolean)

/**
 * Мозг интерфейса: чат, голос, ядро и выполнение команд.
 * Живёт на уровне активности, поэтому переживает поворот экрана.
 */
/** Приветствие по времени суток. */
object Greetings {
    fun forNow(): String = when (java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)) {
        in 5..11 -> "Доброе утро"
        in 12..17 -> "Добрый день"
        in 18..22 -> "Добрый вечер"
        else -> "Доброй ночи"
    }
}

/** Приветствие звучит один раз за запуск, а не при каждом повороте экрана. */
private var greeted = false

class AssistantViewModel(app: Application) : AndroidViewModel(app) {
    private val graph = app as ArchangelApp
    val settings: StateFlow<AppSettings> = graph.settings.settings

    val messages = mutableStateListOf<ChatMessage>()
    var core by mutableStateOf(CoreState.IDLE)
        private set
    var level by mutableFloatStateOf(0f)
        private set
    var partial by mutableStateOf("")
        private set
    /** Короткая строка состояния под сферой (ошибки голоса и т. п.). */
    var hint by mutableStateOf("")
        private set
    var draft by mutableStateOf("")
    var pending by mutableStateOf<PendingAction?>(null)
        private set
    /** Запрос на переход по разделам — UI забирает его и обнуляет. */
    var navigateTo by mutableStateOf<AppScreen?>(null)
    /** Монета, которую попросили показать голосом. */
    var marketRequest by mutableStateOf<String?>(null)

    private var nextId = 0L
    private var job: Job? = null

    private val speech = SpeechInput(
        context = app,
        onPartial = { if (!passive) partial = it },
        onFinal = ::onVoiceFinal,
        onLevel = { if (!passive) level = it },
        onError = { msg ->
            if (passive) {
                passive = false
                standbyFailures++
                refreshStandby(if (standbyFailures > 5) 8_000 else 400)
            } else {
                partial = ""
                hint = msg
                if (core == CoreState.LISTENING) core = CoreState.IDLE
            }
        },
    )

    /** Голоса системного синтезатора — для выбора в настройках и при знакомстве. */
    var voiceOptions by mutableStateOf<List<VoiceOption>>(emptyList())
        private set

    /** Если задано — следующая распознанная фраза уходит сюда (например, в поле «Имя»), а не в чат. */
    var dictation: ((String) -> Unit)? = null

    private val speaker = Speaker(
        context = app,
        onStart = { core = CoreState.SPEAKING },
        onDone = {
            if (core == CoreState.SPEAKING) core = CoreState.IDLE
            // Задали уточняющий вопрос голосом — сразу слушаем ответ
            if (autoListen) {
                autoListen = false
                listenIfAllowed()
            }
        },
        onVoicesReady = { voiceOptions = it },
    )

    // ───────────── Уточняющие вопросы ─────────────

    /** Чего ждём от пользователя после уточняющего вопроса. */
    private sealed class Ask {
        object YouTubeQuery : Ask()
        data class MusicAppQ(val query: String?) : Ask()
        data class MusicQuery(val app: MusicApp) : Ask()
        data class TaxiDest(val app: TaxiApp?) : Ask()
        data class TaxiAppQ(val dest: String) : Ask()
        object NoteText : Ask()
        data class ReminderText(val at: Long?) : Ask()
        data class ReminderTime(val text: String) : Ask()
        object RememberText : Ask()
        data class AlertPrice(val symbol: String) : Ask()
        data class CalTitle(val at: Long?) : Ask()
        data class CalTime(val title: String) : Ask()
    }

    private var awaiting: Ask? = null
    private var autoListen = false

    /** Разговор голосом: после вопроса автоматически включаем микрофон. */
    private fun listenIfAllowed() {
        val granted = ContextCompat.checkSelfPermission(graph, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) startListening()
    }

    private fun ask(a: Ask, question: String, viaVoice: Boolean) {
        awaiting = a
        add(Role.ASSISTANT, question)
        val s = settings.value
        if (s.speakReplies && speaker.speak(question, s)) {
            core = CoreState.SPEAKING
            autoListen = viaVoice
        } else {
            core = CoreState.IDLE
            if (viaVoice) listenIfAllowed()
        }
    }

    private fun isYes(t: String) =
        Regex("^(да|ага|угу|давай|ок|окей|как в прошлый раз|туда же|там же|как обычно|верно)").containsMatchIn(t.lowercase().trim())

    /** @return true, если фраза была ответом на уточняющий вопрос. */
    private fun resolveAwaiting(text: String, viaVoice: Boolean): Boolean {
        val a = awaiting ?: return false
        awaiting = null
        if (CommandParser.isCancel(text)) {
            reply("Хорошо, отменил.", viaVoice)
            return true
        }
        val ans = CommandParser.cleanAnswer(text, wakeNames())
        val s = settings.value
        when (a) {
            Ask.YouTubeQuery ->
                if (ans.isEmpty()) reply(graph.phone.youtube(""), viaVoice)
                else handle(PhoneAction.YouTube(ans), viaVoice)
            is Ask.MusicAppQ -> {
                val last = MusicApp.entries.firstOrNull { it.name == s.musicApp }
                val app = MusicApp.fromText(text) ?: if (isYes(text)) last else null
                if (app == null) {
                    val list = graph.phone.installedMusicApps().joinToString(", ") { it.title }
                    ask(Ask.MusicAppQ(a.query), "Не расслышал приложение. Доступны: $list. Какое?", viaVoice)
                } else handle(PhoneAction.Music(app, a.query), viaVoice)
            }
            is Ask.MusicQuery ->
                handle(PhoneAction.Music(a.app, if (CommandParser.isAnything(text)) "" else CommandParser.stripAppMention(ans)), viaVoice)
            is Ask.TaxiDest -> handle(PhoneAction.Taxi(ans.removePrefix("до ").removePrefix("в ").ifEmpty { null }, a.app), viaVoice)
            is Ask.TaxiAppQ -> {
                val last = TaxiApp.entries.firstOrNull { it.name == s.taxiApp }
                val app = TaxiApp.fromText(text) ?: if (isYes(text)) last else null
                if (app == null) {
                    val list = graph.phone.installedTaxiApps().joinToString(", ") { it.title }
                    ask(Ask.TaxiAppQ(a.dest), "Какое приложение: $list?", viaVoice)
                } else handle(PhoneAction.Taxi(a.dest, app), viaVoice)
            }
            Ask.NoteText -> handle(PhoneAction.Note(CommandParser.stripWakeName(text.trim(), wakeNames()).ifEmpty { null }), viaVoice)
            is Ask.ReminderText -> handle(PhoneAction.Reminder(text.trim().ifEmpty { null }, a.at), viaVoice)
            is Ask.ReminderTime -> {
                val (at, _) = CommandParser.parseWhen(" $text ")
                if (at == null) ask(a, "Не понял время. Скажите, например: «завтра в 9» или «через 20 минут».", viaVoice)
                else handle(PhoneAction.Reminder(a.text, at), viaVoice)
            }
            Ask.RememberText -> handle(PhoneAction.Remember(text.trim().ifEmpty { null }), viaVoice)
            is Ask.AlertPrice -> {
                val price = Regex("([0-9][0-9 ]*(?:[.,][0-9]+)?)\\s*(тысяч|тыс|к)?").find(text)?.let { pm ->
                    val base = pm.groupValues[1].replace(" ", "").replace(',', '.').toDoubleOrNull()
                    if (base != null && pm.groupValues[2].isNotEmpty()) base * 1000 else base
                }
                if (price == null) ask(a, "Назовите цену числом, например «сто тысяч» или «95000».", viaVoice)
                else handle(PhoneAction.PriceAlert(a.symbol, price), viaVoice)
            }
            is Ask.CalTitle -> handle(PhoneAction.CalendarAdd(text.trim().ifEmpty { null }, a.at), viaVoice)
            is Ask.CalTime -> {
                val (at, _) = CommandParser.parseWhen(" $text ")
                if (at == null) ask(a, "Не понял время. Например: «завтра в 15» или «в пятницу в 10».", viaVoice)
                else handle(PhoneAction.CalendarAdd(a.title, at), viaVoice)
            }
        }
        return true
    }

    private fun wakeNames() = listOf(settings.value.wakeName, "архангел")

    // ───────────── Режим ожидания: вызов по имени без касания ─────────────

    private var passive = false
    private var standbyFailures = 0
    private var standbyJob: Job? = null
    /** Приложение на экране (UI сообщает из жизненного цикла активности). */
    var foreground by mutableStateOf(false)

    private fun micGranted() =
        ContextCompat.checkSelfPermission(graph, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun standbyWanted(): Boolean {
        val s = settings.value
        return s.standby && s.onboardingDone && foreground && core == CoreState.IDLE &&
            pending == null && awaiting == null && micGranted()
    }

    /** Включает/выключает фоновое ожидание имени в зависимости от состояния. */
    fun refreshStandby(delayMs: Long = 600) {
        standbyJob?.cancel()
        standbyJob = viewModelScope.launch {
            kotlinx.coroutines.delay(delayMs)
            if (standbyWanted()) {
                if (!speech.isListening) {
                    passive = true
                    speech.start()
                }
            } else if (passive) {
                passive = false
                speech.stop()
            }
        }
    }

    private fun handleWake(text: String) {
        val norm = text.lowercase().replace('ё', 'е')
        val names = wakeNames().map { it.lowercase().replace('ё', 'е') }.filter { it.isNotBlank() }
        val pos = names.mapNotNull { n -> norm.indexOf(n).takeIf { it >= 0 }?.let { it + n.length } }.minOrNull()
        if (pos == null) {
            standbyFailures = 0
            refreshStandby(200)
            return
        }
        standbyFailures = 0
        val rest = text.substring(minOf(pos, text.length)).trim(' ', ',', '.', '!', '?')
        if (rest.isEmpty()) {
            speakLine("Слушаю.")
            autoListen = true
        } else send(rest, fromVoice = true)
    }

    // ───────────── Зрение: фото в вопросе ─────────────

    var pendingImage by mutableStateOf<String?>(null)
        private set

    fun attachImage(uri: android.net.Uri) {
        viewModelScope.launch {
            val path = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { ImageUtil.saveScaled(graph, uri) }
            if (path == null) hint = "Не удалось открыть фото." else pendingImage = path
        }
    }

    fun clearImage() { pendingImage = null }

    // ───────────── Заметки и напоминания ─────────────

    private val tasks = TaskStore(app)
    var notes by mutableStateOf(tasks.notes())
        private set
    var reminders by mutableStateOf(tasks.reminders())
        private set
    /** UI запрашивает разрешение на уведомления, когда появилось первое напоминание. */
    var needNotificationPermission by mutableStateOf(false)

    fun refreshTasks() {
        tasks.pruneFired()
        notes = tasks.notes()
        reminders = tasks.reminders()
    }

    fun addNote(text: String) {
        if (text.isBlank()) return
        tasks.addNote(text.trim()); refreshTasks()
    }

    fun deleteNote(id: Long) { tasks.deleteNote(id); refreshTasks() }

    // ───────────── Память ─────────────

    private val memory = MemoryStore(app)
    var memoryFacts by mutableStateOf(memory.facts())
        private set

    fun forgetFact(fact: String) { memory.remove(fact); memoryFacts = memory.facts() }

    // ───────────── Разрешения, которые нужны для отдельных функций ─────────────

    var needLocationPermission by mutableStateOf(false)
    var needCalendarPermission by mutableStateOf(false)
    private var retryAction: Pair<PhoneAction, Boolean>? = null

    /** UI сообщает результат запроса разрешения — повторяем действие, ради которого просили. */
    fun permissionResult(granted: Boolean) {
        val r = retryAction ?: return
        retryAction = null
        if (granted) handle(r.first, r.second)
    }
    fun deleteReminder(id: Long) { tasks.deleteReminder(id); refreshTasks() }

    init {
        viewModelScope.launch {
            androidx.compose.runtime.snapshotFlow { Triple(core, foreground, pending) }.collect { refreshStandby() }
        }
        viewModelScope.launch { settings.collect { refreshStandby() } }
        val saved = graph.chatStore.load()
        messages.addAll(saved)
        nextId = (saved.maxOfOrNull { it.id } ?: -1L) + 1
        if (saved.isEmpty()) {
            add(Role.ASSISTANT, "Архангел на связи. Спросите что угодно или дайте команду: «позвони маме», «будильник на 7:30», «график биткоина».", persist = false)
        }
    }

    // ───────────── Голос ─────────────

    /** Нажатие на сферу: начать слушать / закончить / прервать ответ. */
    fun onCoreTap() {
        when (core) {
            CoreState.IDLE -> startListening()
            CoreState.LISTENING -> speech.finish()
            CoreState.SPEAKING -> {
                speaker.stop(); core = CoreState.IDLE
            }
            CoreState.THINKING -> {
                job?.cancel(); core = CoreState.IDLE; hint = "Запрос отменён"
            }
        }
    }

    fun startListening() {
        passive = false
        speaker.stop()
        hint = ""
        partial = ""
        core = CoreState.LISTENING
        speech.start()
    }

    fun stopListening() {
        speech.stop()
        level = 0f
        partial = ""
        if (core == CoreState.LISTENING) core = CoreState.IDLE
    }

    private fun onVoiceFinal(text: String) {
        if (passive) {
            passive = false
            handleWake(text)
            return
        }
        partial = ""
        core = CoreState.IDLE
        dictation?.let { target ->
            dictation = null
            target(text)
            return
        }
        if (settings.value.autoSendVoice) send(text, fromVoice = true) else draft = text
    }

    // ───────────── Чат ─────────────

    fun send(raw: String, fromVoice: Boolean = false) {
        val text = raw.trim()
        val image = pendingImage
        if (text.isEmpty() && image == null) return
        if (!fromVoice) draft = ""
        hint = ""
        if (image != null) {
            pendingImage = null
            awaiting = null
            add(Role.USER, text.ifBlank { "Что на этом фото?" }, image = image)
            if (!settings.value.brain.vision) {
                add(Role.NOTE, "${settings.value.brain.title} не видит фото. Переключите мозг на Gemini (бесплатно) или Claude в настройках.")
                return
            }
            askBrain(fromVoice)
            return
        }
        add(Role.USER, text)
        if (resolveAwaiting(text, fromVoice)) return
        val local = CommandParser.parse(text, wakeNames())
        if (local != null) handle(local, viaVoice = fromVoice) else askBrain(fromVoice)
    }

    private fun askBrain(viaVoice: Boolean) {
        job?.cancel()
        core = CoreState.THINKING
        job = viewModelScope.launch {
            try {
                val s = settings.value
                val recent = messages.filter { it.role != Role.NOTE }.takeLast(24)
                val lastImage = recent.lastOrNull()?.image
                val imageB64 = lastImage?.let {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { ImageUtil.base64(it) }
                }
                val history = recent.mapIndexed { i, m ->
                    Turn(
                        if (m.fromUser) "user" else "assistant",
                        if (m.image != null && i != recent.lastIndex) m.text + " [фото]" else m.text,
                        if (i == recent.lastIndex) imageB64 else null,
                    )
                }
                val reply = graph.brain.ask(s, systemPrompt(s), history)
                val (clean, action) = CommandParser.extractActionTag(reply)
                if (clean.isNotBlank()) {
                    add(Role.ASSISTANT, clean)
                    say(clean)
                } else core = CoreState.IDLE
                action?.let { handle(it, viaVoice) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: FriendlyException) {
                add(Role.NOTE, e.message ?: "Ошибка")
                core = CoreState.IDLE
            } catch (e: Exception) {
                add(Role.NOTE, "Непредвиденная ошибка: ${e.javaClass.simpleName}")
                core = CoreState.IDLE
            }
        }
    }

    private fun systemPrompt(s: AppSettings): String {
        val now = SimpleDateFormat("d MMMM yyyy, EEEE, HH:mm", Locale("ru")).format(Date())
        return buildString {
            appendLine(s.persona)
            if (!s.wakeName.equals("Архангел", ignoreCase = true)) {
                appendLine("Пользователь зовёт тебя «${s.wakeName}» (изначальное имя — Архангел). Откликайся на оба.")
            }
            if (s.userName.isNotBlank()) appendLine("Пользователя зовут ${s.userName} — обращайся к нему так.")
            val facts = memory.facts()
            if (facts.isNotEmpty()) {
                appendLine("Что пользователь сам попросил запомнить о себе:")
                facts.forEach { appendLine("- $it") }
            }
            appendLine("Ты работаешь в Android-приложении ARCHANGEL AI на телефоне пользователя. Сейчас: $now.")
            appendLine("Если просьба неоднозначна — задай один короткий уточняющий вопрос, а не угадывай.")
            appendLine("Не используй markdown-таблицы. Если вопрос про трейдинг — помогай по существу, но помни, что это не финансовый совет.")
            appendLine()
            append(CommandParser.ACTION_PROMPT)
        }
    }

    private fun say(text: String) {
        val s = settings.value
        if (s.speakReplies && speaker.speak(text, s)) core = CoreState.SPEAKING
        else core = CoreState.IDLE
    }

    /** Сказать фразу вслух (знакомство, приветствие) — без записи в чат. */
    fun speakLine(text: String, s: AppSettings = settings.value) {
        if (speaker.speak(text, s)) core = CoreState.SPEAKING
    }

    /** «Послушать» голос с ещё не сохранёнными настройками. */
    fun previewVoice(draft: AppSettings) {
        val name = draft.userName.ifBlank { "сэр" }
        speakLine("Добрый вечер, $name. Все системы в норме. Чем могу быть полезен?", draft)
    }

    /** Приветствие при запуске — один раз за жизнь процесса. */
    fun greetOnLaunch() {
        if (greeted) return
        greeted = true
        val s = settings.value
        if (!s.onboardingDone || !s.greetOnLaunch) return
        val hello = Greetings.forNow() + (if (s.userName.isNotBlank()) ", ${s.userName}." else ".")
        val tail = if (s.brainReady) " Я на связи." else
            " Мой мозг пока не подключён — откройте настройки, я подскажу, где взять бесплатный ключ."
        hint = ""
        speakLine(hello + tail)
    }

    /** Проверка ключа мозга короткими запросом. */
    fun testBrain(draft: AppSettings, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                graph.brain.ask(
                    draft, "Ты — проверка связи. Ответь одним словом.",
                    listOf(Turn("user", "Скажи: готов")), maxTokens = 400,
                )
                onResult(true, "Ключ работает — ${draft.brain.title} на связи.")
            } catch (e: FriendlyException) {
                val msg = e.message.orEmpty()
                // Пустой ответ «думающей» модели — ключ при этом рабочий
                if (msg.contains("пустой")) onResult(true, "Ключ работает — ${draft.brain.title} на связи.")
                else onResult(false, msg)
            } catch (e: Exception) {
                onResult(false, "Не удалось проверить: ${e.javaClass.simpleName}")
            }
        }
    }

    fun stopSpeaking() {
        speaker.stop()
        if (core == CoreState.SPEAKING) core = CoreState.IDLE
    }

    // ───────────── Действия ─────────────

    /** Запрос действия из интерфейса (например, ордер из раздела «Рынок»). */
    fun request(action: PhoneAction) = handle(action, viaVoice = false)

    private fun handle(action: PhoneAction, viaVoice: Boolean) {
        when (action) {
            is PhoneAction.Screen -> {
                navigateTo = action.screen
                core = CoreState.IDLE
            }
            is PhoneAction.Market -> {
                marketRequest = action.symbol
                navigateTo = AppScreen.MARKET
                reply("Открываю ${action.symbol.removeSuffix("USDT")}.", viaVoice)
            }
            is PhoneAction.Order -> {
                val s = settings.value
                val mode = if (s.binanceTestnet) "ДЕМО-СЧЁТ — ненастоящие деньги" else "РЕАЛЬНЫЙ СЧЁТ"
                pending = PendingAction(
                    action = action,
                    title = (if (action.buy) "Купить " else "Продать ") + action.symbol.removeSuffix("USDT"),
                    details = "Рыночный ордер на ${Indicators.formatPrice(action.usdt)} USDT · ${action.symbol}\nРежим: $mode",
                    danger = !s.binanceTestnet,
                )
                core = CoreState.IDLE
            }
            is PhoneAction.Call -> {
                pending = PendingAction(action, "Позвонить", "Кому: ${action.target}", danger = false)
                core = CoreState.IDLE
            }
            is PhoneAction.Sms -> {
                pending = PendingAction(action, "Сообщение", "Кому: ${action.target}\n«${action.text}»", danger = false)
                core = CoreState.IDLE
            }
            is PhoneAction.YouTube ->
                if (action.query == null) ask(Ask.YouTubeQuery, "Что найти на YouTube?", viaVoice)
                else reply(graph.phone.youtube(action.query), viaVoice)
            is PhoneAction.Music -> handleMusic(action, viaVoice)
            is PhoneAction.Taxi -> handleTaxi(action, viaVoice)
            is PhoneAction.Note -> {
                val text = action.text?.trim().orEmpty()
                if (text.isEmpty()) ask(Ask.NoteText, "Что записать?", viaVoice)
                else {
                    addNote(text)
                    reply("Записал: «$text».", viaVoice)
                }
            }
            PhoneAction.ShowNotes -> {
                refreshTasks()
                navigateTo = AppScreen.TASKS
                val n = notes
                reply(
                    if (n.isEmpty()) "Заметок пока нет. Скажите: «запиши заметку…»."
                    else "Заметок: ${n.size}. Последние: " + n.take(3).joinToString("; ") { it.text } + ".",
                    viaVoice,
                )
            }
            is PhoneAction.Reminder -> {
                val text = action.text?.trim().orEmpty()
                val at = action.at
                when {
                    text.isEmpty() -> ask(Ask.ReminderText(at), "О чём напомнить?", viaVoice)
                    at == null -> ask(Ask.ReminderTime(text), "Когда напомнить?", viaVoice)
                    at <= System.currentTimeMillis() ->
                        ask(Ask.ReminderTime(text), "Это время уже прошло. Когда напомнить?", viaVoice)
                    else -> {
                        tasks.addReminder(text, at)
                        refreshTasks()
                        needNotificationPermission = true
                        reply("Напомню ${CommandParser.describeTime(at)}: $text.", viaVoice)
                    }
                }
            }
            PhoneAction.ShowReminders -> {
                refreshTasks()
                navigateTo = AppScreen.TASKS
                val list = reminders
                reply(
                    if (list.isEmpty()) "Напоминаний нет."
                    else "Напоминаний: ${list.size}. Ближайшее — ${CommandParser.describeTime(list.first().at)}: ${list.first().text}.",
                    viaVoice,
                )
            }
            is PhoneAction.Remember -> {
                val fact = action.fact?.trim().orEmpty()
                if (fact.isEmpty()) ask(Ask.RememberText, "Что запомнить?", viaVoice)
                else {
                    memory.add(fact); memoryFacts = memory.facts()
                    reply("Запомнил: ${fact.replaceFirstChar { it.lowercaseChar() }}.", viaVoice)
                }
            }
            is PhoneAction.Forget -> {
                val n = memory.forget(action.what)
                memoryFacts = memory.facts()
                reply(if (n > 0) "Забыл." else "Я ничего такого не помню.", viaVoice)
            }
            PhoneAction.ShowMemory -> {
                val f = memory.facts()
                reply(
                    if (f.isEmpty()) "Пока ничего. Скажите «запомни, что…» — и я буду это учитывать."
                    else "Вот что я помню: " + f.joinToString("; ") + ".",
                    viaVoice,
                )
            }
            is PhoneAction.Weather -> {
                if (!Weather.hasLocationPermission(graph)) {
                    retryAction = action to viaVoice
                    needLocationPermission = true
                    reply("Чтобы сказать погоду, мне нужно примерное местоположение — разрешите доступ.", viaVoice)
                } else background(viaVoice) { Weather.describe(Weather.fetch(graph), action.day) }
            }
            PhoneAction.Briefing -> background(viaVoice) { buildBriefing() }
            PhoneAction.Agenda -> {
                if (!CalendarHelper.canRead(graph)) {
                    retryAction = action to viaVoice
                    needCalendarPermission = true
                }
                refreshTasks()
                val today = todayReminders()
                val cal = if (CalendarHelper.canRead(graph)) CalendarHelper.describe(CalendarHelper.eventsForDay(graph)) + " " else ""
                reply(cal + (if (today.isEmpty()) "Напоминаний на сегодня нет." else "Напоминания: $today."), viaVoice)
            }
            PhoneAction.Portfolio -> background(viaVoice) {
                val s = settings.value
                if (!s.exchangeReady) "Биржа не подключена — добавьте ключ Binance в настройках."
                else {
                    val p = graph.binance.portfolio(s.binanceKey, s.binanceSecret, s.binanceTestnet)
                    if (p.items.isEmpty()) "На счёте пусто."
                    else "Портфель${if (s.binanceTestnet) " на демо-счёте" else ""}: ${Indicators.formatPrice(p.total)} USDT, " +
                        "за сутки ${if (p.change24 >= 0) "+" else ""}${String.format(Locale.US, "%.2f", p.change24)}%. " +
                        "Крупнейшие: " + p.items.take(3).joinToString(", ") { "${it.asset} — ${Indicators.formatPrice(it.value)}" } + "."
                }
            }
            is PhoneAction.PriceAlert -> {
                val price = action.price
                if (price == null || price <= 0) {
                    ask(Ask.AlertPrice(action.symbol), "На какой цене ${action.symbol.removeSuffix("USDT")} сообщить?", viaVoice)
                } else background(viaVoice) {
                    val now = graph.binance.ticker(action.symbol).last
                    val above = price > now
                    AlertStore(graph).add(action.symbol, price, above)
                    AlertScheduler.ensure(graph)
                    needNotificationPermission = true
                    alertsVersion++
                    "Сообщу, когда ${action.symbol.removeSuffix("USDT")} будет ${if (above) "выше" else "ниже"} " +
                        "${Indicators.formatPrice(price)}. Сейчас ${Indicators.formatPrice(now)}. Проверяю цену каждые 15 минут."
                }
            }
            is PhoneAction.CalendarAdd -> {
                val title = action.title?.trim().orEmpty()
                val at = action.at
                when {
                    title.isEmpty() -> ask(Ask.CalTitle(at), "Как назвать событие?", viaVoice)
                    at == null -> ask(Ask.CalTime(title), "Когда?", viaVoice)
                    CalendarHelper.insert(graph, title, at) ->
                        reply("Открываю календарь: «$title», ${CommandParser.describeTime(at)}. Нажмите «Сохранить».", viaVoice)
                    else -> reply("Не нашёл приложение календаря.", viaVoice)
                }
            }
            is PhoneAction.SmartHome -> {
                val s = settings.value
                when {
                    s.homeReady -> background(viaVoice) { HomeAssistant.control(s, action.on, action.target) }
                    action.target.trim() == "свет" -> reply(graph.phone.execute(PhoneAction.Flashlight(action.on)), viaVoice)
                    graph.phone.isInstalled("com.xiaomi.smarthome") -> {
                        graph.phone.execute(PhoneAction.OpenApp("mi home"))
                        reply(
                            "Умный дом ещё не подключён ко мне — открываю Mi Home. Чтобы я управлял голосом, " +
                                "подключите Home Assistant в настройках.",
                            viaVoice,
                        )
                    }
                    else -> reply(
                        "Умный дом не подключён. Если у вас Home Assistant — добавьте адрес и токен в настройках, " +
                            "и я буду управлять светом и техникой голосом.",
                        viaVoice,
                    )
                }
            }
            is PhoneAction.GuardCheck -> {
                val text = action.text
                if (text.isNullOrBlank()) {
                    reply(
                        "Пришлите, что проверить: напишите «проверь» и вставьте ссылку или текст, " +
                            "или в любом приложении нажмите «Поделиться» → «Проверить у Архангела».",
                        viaVoice,
                    )
                } else {
                    val v = Guardian.analyze(text)
                    val local = Guardian.summary(v)
                    if (!settings.value.brainReady) reply(local, viaVoice)
                    else background(viaVoice) {
                        val opinion = runCatching {
                            graph.brain.ask(
                                settings.value,
                                "Ты — эксперт по кибербезопасности и мошенничеству. Отвечай по-русски, кратко: " +
                                    "вердикт (безопасно / подозрительно / мошенничество), 1–3 причины и что делать.",
                                listOf(Turn("user", "Проверь это:\n$text\n\nАвтоматические признаки: ${v.flags.joinToString("; ").ifEmpty { "не найдены" }}")),
                                maxTokens = 500,
                            )
                        }.getOrNull()
                        if (opinion == null) local else "$local\n\nМнение ИИ: $opinion"
                    }
                }
            }
            PhoneAction.ReadMessages -> reply(
                "Читать чужие уведомления я не могу: Google Play Защита блокирует установку приложений с таким доступом " +
                    "не из Play Маркета. Откройте мессенджер — или скажите «открой Телеграм».",
                viaVoice,
            )
            else -> reply(graph.phone.execute(action), viaVoice)
        }
    }

    /** Текст из «Поделиться» другого приложения — проверяем «Защитником». */
    fun checkShared(text: String) {
        navigateTo = AppScreen.CHAT
        send("Проверь сообщение: $text")
    }

    /** Меняется, когда добавлено ценовое уведомление — «Рынок» перечитывает список. */
    var alertsVersion by mutableStateOf(0)
        private set

    /** Долгая операция с сетью: ядро «думает», результат — в чат и голосом. */
    private fun background(viaVoice: Boolean, block: suspend () -> String) {
        core = CoreState.THINKING
        viewModelScope.launch {
            try {
                reply(block(), viaVoice)
            } catch (e: CancellationException) {
                throw e
            } catch (e: FriendlyException) {
                add(Role.NOTE, e.message.orEmpty()); core = CoreState.IDLE
            } catch (e: Exception) {
                add(Role.NOTE, "Не получилось: ${e.javaClass.simpleName}"); core = CoreState.IDLE
            }
        }
    }

    private fun todayReminders(): String {
        val end = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 23); set(java.util.Calendar.MINUTE, 59)
        }.timeInMillis
        return reminders.filter { it.at <= end }.joinToString("; ") {
            SimpleDateFormat("HH:mm", Locale.US).format(Date(it.at)) + " — " + it.text
        }
    }

    /** Утренний брифинг: погода, планы, рынок. Работает без ИИ-ключа. */
    private suspend fun buildBriefing(): String {
        val s = settings.value
        val parts = ArrayList<String>()
        val name = if (s.userName.isNotBlank()) ", ${s.userName}" else ""
        parts += "${Greetings.forNow()}$name. Сегодня " + SimpleDateFormat("EEEE, d MMMM", Locale("ru")).format(Date()) + "."
        if (Weather.hasLocationPermission(graph)) {
            runCatching { Weather.describe(Weather.fetch(graph), 0) }.getOrNull()?.let { parts += it }
        }
        if (CalendarHelper.canRead(graph)) parts += CalendarHelper.describe(CalendarHelper.eventsForDay(graph))
        refreshTasks()
        todayReminders().takeIf { it.isNotEmpty() }?.let { parts += "Напоминания: $it." }
        val coins = listOf("BTCUSDT" to "Биткоин", "ETHUSDT" to "эфир")
        val market = coins.mapNotNull { (sym, title) ->
            runCatching { graph.binance.ticker(sym) }.getOrNull()?.let {
                "$title ${Indicators.formatPrice(it.last)}, ${if (it.changePct >= 0) "плюс" else "минус"} " +
                    String.format(Locale.US, "%.1f", kotlin.math.abs(it.changePct)) + "% за сутки"
            }
        }
        if (market.isNotEmpty()) parts += "Рынок: " + market.joinToString("; ") + "."
        parts += "Хорошего дня."
        return parts.joinToString(" ")
    }

    /** Запуск брифинга из уведомления. */
    fun briefing() = handle(PhoneAction.Briefing, viaVoice = true)

    private fun handleMusic(action: PhoneAction.Music, viaVoice: Boolean) {
        val installed = graph.phone.installedMusicApps()
        val last = installed.firstOrNull { it.name == settings.value.musicApp }
        val app = action.app ?: installed.singleOrNull()
        when {
            installed.isEmpty() -> reply("Не нашёл музыкальных приложений. Установите YouTube Music или Spotify.", viaVoice)
            app == null -> ask(
                Ask.MusicAppQ(action.query),
                if (last != null) "Включить в ${last.title}, как в прошлый раз, или в другом приложении?"
                else "В каком приложении включить: ${installed.joinToString(", ") { it.title }}?",
                viaVoice,
            )
            !graph.phone.isInstalled(app.pkg) -> ask(
                Ask.MusicAppQ(action.query),
                "${app.title} не установлен. Есть: ${installed.joinToString(", ") { it.title }}. Какое?",
                viaVoice,
            )
            action.query == null -> {
                rememberApps(music = app)
                ask(Ask.MusicQuery(app), "Что поставить? Исполнителя, песню, жанр — или «что угодно».", viaVoice)
            }
            else -> {
                rememberApps(music = app)
                reply(graph.phone.playMusic(app, action.query), viaVoice)
            }
        }
    }

    private fun handleTaxi(action: PhoneAction.Taxi, viaVoice: Boolean) {
        val installed = graph.phone.installedTaxiApps()
        if (installed.isEmpty()) {
            reply("Не нашёл приложений такси. Установите Uklon, Bolt или Uber — и я смогу вызывать машину.", viaVoice)
            return
        }
        if (action.destination.isNullOrBlank()) {
            ask(Ask.TaxiDest(action.app), "Куда едем?", viaVoice)
            return
        }
        val last = installed.firstOrNull { it.name == settings.value.taxiApp }
        val app = action.app?.takeIf { it in installed } ?: installed.singleOrNull()
        if (app == null) {
            ask(
                Ask.TaxiAppQ(action.destination),
                if (last != null) "Через ${last.title}, как в прошлый раз, или другое приложение?"
                else "Через какое приложение: ${installed.joinToString(", ") { it.title }}?",
                viaVoice,
            )
            return
        }
        rememberApps(taxi = app)
        reply(graph.phone.callTaxi(app, action.destination), viaVoice)
    }

    private fun rememberApps(music: MusicApp? = null, taxi: TaxiApp? = null) {
        graph.settings.update { s ->
            s.copy(musicApp = music?.name ?: s.musicApp, taxiApp = taxi?.name ?: s.taxiApp)
        }
    }

    private fun reply(text: String, speak: Boolean) {
        if (text.isBlank()) return
        add(Role.ASSISTANT, text)
        if (speak) say(text) else core = CoreState.IDLE
    }

    fun cancelPending() {
        pending?.let { add(Role.NOTE, "Отменено: ${it.title.lowercase()}.") }
        pending = null
    }

    /** Пользователь нажал «Да». [onOrderDone] — чтобы «Рынок» обновил баланс. */
    fun confirmPending(onOrderDone: () -> Unit = {}) {
        val p = pending ?: return
        pending = null
        when (val a = p.action) {
            is PhoneAction.Order -> placeOrder(a, onOrderDone)
            else -> reply(graph.phone.execute(a), speak = false)
        }
    }

    private fun placeOrder(a: PhoneAction.Order, onDone: () -> Unit) {
        val s = settings.value
        core = CoreState.THINKING
        viewModelScope.launch {
            try {
                val r = graph.binance.marketOrder(a.symbol, a.buy, a.usdt, s.binanceKey, s.binanceSecret, s.binanceTestnet)
                val coin = a.symbol.removeSuffix("USDT")
                val verb = if (a.buy) "Куплено" else "Продано"
                val tag = if (s.binanceTestnet) " · демо-счёт" else ""
                reply(
                    "$verb ${"%.6f".format(Locale.US, r.executedQty)} $coin на ${Indicators.formatPrice(r.quoteQty)} USDT " +
                        "(средняя ${Indicators.formatPrice(r.avgPrice)}). Статус: ${r.status}$tag.",
                    speak = false,
                )
                onDone()
            } catch (e: FriendlyException) {
                add(Role.NOTE, "Ордер не выполнен. ${e.message}")
                core = CoreState.IDLE
            } catch (e: Exception) {
                add(Role.NOTE, "Ордер не выполнен: ${e.javaClass.simpleName}")
                core = CoreState.IDLE
            }
        }
    }

    // ───────────── История ─────────────

    private fun add(role: Role, text: String, persist: Boolean = true, image: String? = null) {
        messages.add(ChatMessage(nextId++, text, role, image = image))
        if (persist) graph.chatStore.save(messages)
    }

    fun clearHistory() {
        job?.cancel()
        messages.clear()
        graph.chatStore.clear()
        core = CoreState.IDLE
        add(Role.ASSISTANT, "История очищена.", persist = false)
    }

    val phone: PhoneController get() = graph.phone

    override fun onCleared() {
        standbyJob?.cancel()
        speech.stop()
        speaker.release()
        super.onCleared()
    }
}
