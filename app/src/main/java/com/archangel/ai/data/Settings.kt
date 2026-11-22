package com.archangel.ai.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** «Мозг» ассистента — какой ИИ отвечает. Всё, кроме Claude, говорит на OpenAI-совместимом API. */
enum class Brain(
    val title: String,
    val defaultModel: String,
    val defaultBaseUrl: String,
    val keyUrl: String,
    val free: Boolean,
    val note: String,
    val recommended: Boolean = false,
    /** Понимает ли картинки («зрение»). */
    val vision: Boolean = true,
    /** Запасные модели: если основная недоступна на тарифе — пробуем следующую. */
    val fallbackModels: List<String> = emptyList(),
) {
    GEMINI(
        "Gemini", "gemini-3.8-flash", "https://generativelanguage.googleapis.com/v1beta/openai",
        "https://aistudio.google.com/apikey", true, "Рекомендуем: бесплатно, умный, видит фото",
        recommended = true,
        fallbackModels = listOf("gemini-3.5-flash", "gemini-2.5-flash", "gemini-2.5-flash-lite"),
    ),
    GROQ(
        "Groq", "openai/gpt-oss-120b", "https://api.groq.com/openai/v1",
        "https://console.groq.com/keys", true, "Бесплатно, очень быстрый",
        vision = false,
        fallbackModels = listOf("openai/gpt-oss-20b"),
    ),
    OPENROUTER(
        "OpenRouter", "openrouter/free", "https://openrouter.ai/api/v1",
        "https://openrouter.ai/settings/keys", true, "Бесплатные модели, ~50 запросов в день",
    ),
    DEEPSEEK(
        "DeepSeek", "deepseek-chat", "https://api.deepseek.com",
        "https://platform.deepseek.com/api_keys", false, "Платно, но очень дёшево",
        vision = false,
    ),
    CLAUDE(
        "Claude", "claude-sonnet-5-5", "https://api.anthropic.com",
        "https://console.anthropic.com/settings/keys", false, "Платно, самый умный",
    ),
    OPENAI(
        "Другой", "gpt-4o-mini", "https://api.openai.com/v1",
        "https://platform.openai.com/api-keys", false, "Любой OpenAI-совместимый сервис",
    ),
}

/** Материал сферы в покое (во время работы цвет показывает состояние). */
enum class OrbTheme(val title: String, val core: Long, val rim: Long) {
    PLATINUM("Платина", 0xFFD4D4D8, 0xFFF4F4F5),
    GOLD("Жидкое золото", 0xFFE8B04A, 0xFFFFE3A3),
    MERCURY("Ртуть", 0xFF9AA3AD, 0xFFE9EEF3),
    LAVA("Лава", 0xFFFF4A1C, 0xFFFF9A5A),
    EMERALD("Изумруд", 0xFF2FD39A, 0xFFB9F5DC),
    AMETHYST("Аметист", 0xFF9B7BFF, 0xFFDCCFFF),
}

/** Биржа для баланса и сделок. Графики и анализ — всегда по публичным данным Binance. */
enum class Exchange(val title: String, val hasDemo: Boolean, val needsPassphrase: Boolean, val canTrade: Boolean) {
    BINANCE("Binance", true, false, true),
    BYBIT("Bybit", true, false, true),
    OKX("OKX", true, true, true),
    WHITEBIT("WhiteBIT", false, false, false),
}

data class ExchangeCreds(val key: String = "", val secret: String = "", val passphrase: String = "")

/** Чем озвучивать ответы. */
enum class VoiceEngine(val title: String) {
    SYSTEM("Системный · бесплатно"),
    ELEVENLABS("ElevenLabs · как в кино"),
}

data class AppSettings(
    val brain: Brain = Brain.GEMINI,
    val keys: Map<Brain, String> = emptyMap(),
    val models: Map<Brain, String> = emptyMap(),
    val baseUrls: Map<Brain, String> = emptyMap(),
    val persona: String = DEFAULT_PERSONA,
    val speakReplies: Boolean = true,
    val autoSendVoice: Boolean = true,
    val speechRate: Float = 1.0f,
    // Голос
    val voiceEngine: VoiceEngine = VoiceEngine.SYSTEM,
    val systemVoice: String = "",
    val voicePitch: Float = 0.88f,
    val elevenKey: String = "",
    val elevenVoiceId: String = DEFAULT_ELEVEN_VOICE,
    // Знакомство
    val userName: String = "",
    /** Как пользователь зовёт ассистента голосом («Архангел», «Джарвис»…). */
    val wakeName: String = "Архангел",
    /** Последнее выбранное музыкальное приложение и такси — предлагаются первыми. */
    val musicApp: String = "",
    val taxiApp: String = "",
    val onboardingDone: Boolean = false,
    val greetOnLaunch: Boolean = true,
    // Внешний вид и брифинг
    val orbTheme: OrbTheme = OrbTheme.PLATINUM,
    val briefingEnabled: Boolean = false,
    val briefingHour: Int = 8,
    val briefingMinute: Int = 0,
    /** Режим ожидания: пока приложение открыто, слушает и откликается на имя без касания. */
    val standby: Boolean = false,
    /** Слушать имя и в фоне — как умная колонка (служба со значком в шторке). */
    val standbyBackground: Boolean = false,
    /** Фоновое прослушивание только на зарядке — бережёт батарею. */
    val standbyOnlyCharging: Boolean = false,
    /** Home Assistant: адрес вида http://192.168.1.10:8123 и долгоживущий токен. */
    val haUrl: String = "",
    val haToken: String = "",
    // Биржа
    val binanceKey: String = "",
    val binanceSecret: String = "",
    val binanceTestnet: Boolean = true,
    /** Автосигналы раз в час по списку монет. */
    val signalsEnabled: Boolean = false,
    val watchlist: List<String> = DEFAULT_WATCHLIST,
    /** Защита от эмоций: пауза после трёх убыточных сделок подряд. */
    val tiltGuard: Boolean = true,
    /** Активная биржа и ключи других бирж (Bybit, OKX, WhiteBIT). */
    val exchange: Exchange = Exchange.BINANCE,
    val altKeys: Map<Exchange, ExchangeCreds> = emptyMap(),
    /** Демо-счёт на Bybit/OKX. */
    val altDemo: Boolean = true,
) {
    val altCreds get() = altKeys[exchange] ?: ExchangeCreds()
    val altReady get() = exchange != Exchange.BINANCE && altCreds.key.isNotBlank() && altCreds.secret.isNotBlank() &&
        (!exchange.needsPassphrase || altCreds.passphrase.isNotBlank())
    /** Деньги ненастоящие (демо) на активной бирже. */
    val tradeDemo get() = if (exchange == Exchange.BINANCE) binanceTestnet else altDemo && exchange.hasDemo
    /** Активная биржа подключена. */
    val activeReady get() = if (exchange == Exchange.BINANCE) exchangeReady else altReady

    fun keyFor(b: Brain = brain) = keys[b].orEmpty()
    fun modelFor(b: Brain = brain) = models[b]?.takeIf { it.isNotBlank() } ?: b.defaultModel
    fun baseUrlFor(b: Brain = brain) = baseUrls[b]?.takeIf { it.isNotBlank() } ?: b.defaultBaseUrl
    val brainReady get() = keyFor().isNotBlank()
    val exchangeReady get() = binanceKey.isNotBlank() && binanceSecret.isNotBlank()
    val homeReady get() = haUrl.isNotBlank() && haToken.isNotBlank()

    companion object {
        const val DEFAULT_PERSONA =
            "Ты — Архангел, личный ИИ-ассистент в манере безупречного британского дворецкого: " +
                "спокойный, вежливый, с лёгкой иронией. Отвечай по-русски, кратко и по делу: " +
                "ответы часто озвучиваются голосом."
        /** «George» в ElevenLabs — тёплый британский мужской голос. */
        const val DEFAULT_ELEVEN_VOICE = "JBFqnCBsd6RMkjVDRZzb"
        val DEFAULT_WATCHLIST = listOf("BTCUSDT", "ETHUSDT", "SOLUSDT")
    }
}

/** Хранит настройки в SharedPreferences; все ключи — в зашифрованном виде. */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("archangel_settings", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()
    val current: AppSettings get() = _settings.value

    private fun load(): AppSettings {
        val brain = runCatching { Brain.valueOf(prefs.getString("brain", null) ?: "") }
            .getOrDefault(Brain.GEMINI)
        val engine = runCatching { VoiceEngine.valueOf(prefs.getString("voice_engine", null) ?: "") }
            .getOrDefault(VoiceEngine.SYSTEM)
        return AppSettings(
            brain = brain,
            keys = Brain.entries.associateWith { SecretBox.decrypt(prefs.getString("key_${it.name}", "") ?: "") },
            models = Brain.entries.associateWith { prefs.getString("model_${it.name}", "") ?: "" },
            baseUrls = Brain.entries.associateWith { prefs.getString("url_${it.name}", "") ?: "" },
            persona = prefs.getString("persona", null) ?: AppSettings.DEFAULT_PERSONA,
            speakReplies = prefs.getBoolean("speak", true),
            autoSendVoice = prefs.getBoolean("autosend", true),
            speechRate = prefs.getFloat("rate", 1.0f),
            voiceEngine = engine,
            systemVoice = prefs.getString("system_voice", "") ?: "",
            voicePitch = prefs.getFloat("pitch", 0.88f),
            elevenKey = SecretBox.decrypt(prefs.getString("eleven_key", "") ?: ""),
            elevenVoiceId = prefs.getString("eleven_voice", null)?.takeIf { it.isNotBlank() }
                ?: AppSettings.DEFAULT_ELEVEN_VOICE,
            userName = prefs.getString("user_name", "") ?: "",
            wakeName = prefs.getString("wake_name", null)?.takeIf { it.isNotBlank() } ?: "Архангел",
            musicApp = prefs.getString("music_app", "") ?: "",
            taxiApp = prefs.getString("taxi_app", "") ?: "",
            onboardingDone = prefs.getBoolean("onboarding_done", false),
            greetOnLaunch = prefs.getBoolean("greet", true),
            orbTheme = runCatching { OrbTheme.valueOf(prefs.getString("orb_theme", null) ?: "") }.getOrDefault(OrbTheme.PLATINUM),
            briefingEnabled = prefs.getBoolean("briefing", false),
            briefingHour = prefs.getInt("briefing_hour", 8),
            briefingMinute = prefs.getInt("briefing_minute", 0),
            standby = prefs.getBoolean("standby", false),
            standbyBackground = prefs.getBoolean("standby_bg", false),
            standbyOnlyCharging = prefs.getBoolean("standby_charging", false),
            haUrl = prefs.getString("ha_url", "") ?: "",
            haToken = SecretBox.decrypt(prefs.getString("ha_token", "") ?: ""),
            binanceKey = SecretBox.decrypt(prefs.getString("bn_key", "") ?: ""),
            binanceSecret = SecretBox.decrypt(prefs.getString("bn_secret", "") ?: ""),
            binanceTestnet = prefs.getBoolean("bn_testnet", true),
            signalsEnabled = prefs.getBoolean("signals", false),
            watchlist = (prefs.getString("watchlist", null) ?: AppSettings.DEFAULT_WATCHLIST.joinToString(","))
                .split(',').map { it.trim().uppercase() }.filter { it.isNotEmpty() },
            tiltGuard = prefs.getBoolean("tilt_guard", true),
            exchange = runCatching { Exchange.valueOf(prefs.getString("exchange", null) ?: "") }.getOrDefault(Exchange.BINANCE),
            altKeys = Exchange.entries.filter { it != Exchange.BINANCE }.associateWith {
                ExchangeCreds(
                    SecretBox.decrypt(prefs.getString("ex_key_${it.name}", "") ?: ""),
                    SecretBox.decrypt(prefs.getString("ex_secret_${it.name}", "") ?: ""),
                    SecretBox.decrypt(prefs.getString("ex_pass_${it.name}", "") ?: ""),
                )
            },
            altDemo = prefs.getBoolean("alt_demo", true),
        )
    }

    fun save(s: AppSettings) {
        val clean = s.copy(
            keys = s.keys.mapValues { it.value.trim() },
            elevenKey = s.elevenKey.trim(),
            elevenVoiceId = s.elevenVoiceId.trim().ifBlank { AppSettings.DEFAULT_ELEVEN_VOICE },
            userName = s.userName.trim(),
            wakeName = s.wakeName.trim().ifBlank { "Архангел" },
            binanceKey = s.binanceKey.trim(),
            binanceSecret = s.binanceSecret.trim(),
        )
        prefs.edit().apply {
            putString("brain", clean.brain.name)
            Brain.entries.forEach {
                putString("key_${it.name}", SecretBox.encrypt(clean.keyFor(it)))
                putString("model_${it.name}", clean.models[it].orEmpty().trim())
                putString("url_${it.name}", clean.baseUrls[it].orEmpty().trim())
            }
            putString("persona", clean.persona)
            putBoolean("speak", clean.speakReplies)
            putBoolean("autosend", clean.autoSendVoice)
            putFloat("rate", clean.speechRate)
            putString("voice_engine", clean.voiceEngine.name)
            putString("system_voice", clean.systemVoice)
            putFloat("pitch", clean.voicePitch)
            putString("eleven_key", SecretBox.encrypt(clean.elevenKey))
            putString("eleven_voice", clean.elevenVoiceId)
            putString("user_name", clean.userName)
            putString("wake_name", clean.wakeName)
            putString("music_app", clean.musicApp)
            putString("taxi_app", clean.taxiApp)
            putBoolean("onboarding_done", clean.onboardingDone)
            putBoolean("greet", clean.greetOnLaunch)
            putString("orb_theme", clean.orbTheme.name)
            putBoolean("briefing", clean.briefingEnabled)
            putInt("briefing_hour", clean.briefingHour)
            putInt("briefing_minute", clean.briefingMinute)
            putBoolean("standby", clean.standby)
            putBoolean("standby_bg", clean.standbyBackground)
            putBoolean("standby_charging", clean.standbyOnlyCharging)
            putString("ha_url", clean.haUrl.trim().trimEnd('/'))
            putString("ha_token", SecretBox.encrypt(clean.haToken.trim()))
            putString("bn_key", SecretBox.encrypt(clean.binanceKey))
            putString("bn_secret", SecretBox.encrypt(clean.binanceSecret))
            putBoolean("bn_testnet", clean.binanceTestnet)
            putBoolean("signals", clean.signalsEnabled)
            putString("watchlist", clean.watchlist.distinct().joinToString(","))
            putBoolean("tilt_guard", clean.tiltGuard)
            putString("exchange", clean.exchange.name)
            clean.altKeys.forEach { (ex, c) ->
                putString("ex_key_${ex.name}", SecretBox.encrypt(c.key.trim()))
                putString("ex_secret_${ex.name}", SecretBox.encrypt(c.secret.trim()))
                putString("ex_pass_${ex.name}", SecretBox.encrypt(c.passphrase.trim()))
            }
            putBoolean("alt_demo", clean.altDemo)
        }.commit() // синхронно: фоновые приёмники (брифинг) читают эти же настройки
        _settings.value = clean
    }

    fun update(block: (AppSettings) -> AppSettings) = save(block(current))
}
