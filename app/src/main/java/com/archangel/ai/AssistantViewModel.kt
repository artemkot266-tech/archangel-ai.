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
        onPartial = { partial = it },
        onFinal = ::onVoiceFinal,
        onLevel = { level = it },
        onError = { msg ->
            partial = ""
            hint = msg
            if (core == CoreState.LISTENING) core = CoreState.IDLE
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
        onDone = { if (core == CoreState.SPEAKING) core = CoreState.IDLE },
        onVoicesReady = { voiceOptions = it },
    )

    init {
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
        if (text.isEmpty()) return
        if (!fromVoice) draft = ""
        hint = ""
        add(Role.USER, text)
        val local = CommandParser.parse(text)
        if (local != null) handle(local, viaVoice = fromVoice) else askBrain(fromVoice)
    }

    private fun askBrain(viaVoice: Boolean) {
        job?.cancel()
        core = CoreState.THINKING
        job = viewModelScope.launch {
            try {
                val s = settings.value
                val history = messages.filter { it.role != Role.NOTE }.takeLast(24).map {
                    Turn(if (it.fromUser) "user" else "assistant", it.text)
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
            if (s.userName.isNotBlank()) appendLine("Пользователя зовут ${s.userName} — обращайся к нему так.")
            appendLine("Ты работаешь в Android-приложении ARCHANGEL AI на телефоне пользователя. Сейчас: $now.")
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
            else -> reply(graph.phone.execute(action), viaVoice)
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

    private fun add(role: Role, text: String, persist: Boolean = true) {
        messages.add(ChatMessage(nextId++, text, role))
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
        speech.stop()
        speaker.release()
        super.onCleared()
    }
}
