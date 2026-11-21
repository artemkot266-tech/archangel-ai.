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
) {
    GROQ(
        "Groq", "openai/gpt-oss-120b", "https://api.groq.com/openai/v1",
        "https://console.groq.com/keys", true, "Бесплатно, очень быстрый",
    ),
    GEMINI(
        "Gemini", "gemini-3.8-flash", "https://generativelanguage.googleapis.com/v1beta/openai",
        "https://aistudio.google.com/apikey", true, "Бесплатно от Google",
    ),
    OPENROUTER(
        "OpenRouter", "openrouter/free", "https://openrouter.ai/api/v1",
        "https://openrouter.ai/settings/keys", true, "Бесплатные модели, ~50 запросов в день",
    ),
    DEEPSEEK(
        "DeepSeek", "deepseek-chat", "https://api.deepseek.com",
        "https://platform.deepseek.com/api_keys", false, "Платно, но очень дёшево",
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

/** Чем озвучивать ответы. */
enum class VoiceEngine(val title: String) {
    SYSTEM("Системный · бесплатно"),
    ELEVENLABS("ElevenLabs · как в кино"),
}

data class AppSettings(
    val brain: Brain = Brain.GROQ,
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
    val onboardingDone: Boolean = false,
    val greetOnLaunch: Boolean = true,
    // Биржа
    val binanceKey: String = "",
    val binanceSecret: String = "",
    val binanceTestnet: Boolean = true,
) {
    fun keyFor(b: Brain = brain) = keys[b].orEmpty()
    fun modelFor(b: Brain = brain) = models[b]?.takeIf { it.isNotBlank() } ?: b.defaultModel
    fun baseUrlFor(b: Brain = brain) = baseUrls[b]?.takeIf { it.isNotBlank() } ?: b.defaultBaseUrl
    val brainReady get() = keyFor().isNotBlank()
    val exchangeReady get() = binanceKey.isNotBlank() && binanceSecret.isNotBlank()

    companion object {
        const val DEFAULT_PERSONA =
            "Ты — Архангел, личный ИИ-ассистент в манере безупречного британского дворецкого: " +
                "спокойный, вежливый, с лёгкой иронией. Отвечай по-русски, кратко и по делу: " +
                "ответы часто озвучиваются голосом."
        /** «George» в ElevenLabs — тёплый британский мужской голос. */
        const val DEFAULT_ELEVEN_VOICE = "JBFqnCBsd6RMkjVDRZzb"
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
            .getOrDefault(Brain.GROQ)
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
            onboardingDone = prefs.getBoolean("onboarding_done", false),
            greetOnLaunch = prefs.getBoolean("greet", true),
            binanceKey = SecretBox.decrypt(prefs.getString("bn_key", "") ?: ""),
            binanceSecret = SecretBox.decrypt(prefs.getString("bn_secret", "") ?: ""),
            binanceTestnet = prefs.getBoolean("bn_testnet", true),
        )
    }

    fun save(s: AppSettings) {
        val clean = s.copy(
            keys = s.keys.mapValues { it.value.trim() },
            elevenKey = s.elevenKey.trim(),
            elevenVoiceId = s.elevenVoiceId.trim().ifBlank { AppSettings.DEFAULT_ELEVEN_VOICE },
            userName = s.userName.trim(),
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
            putBoolean("onboarding_done", clean.onboardingDone)
            putBoolean("greet", clean.greetOnLaunch)
            putString("bn_key", SecretBox.encrypt(clean.binanceKey))
            putString("bn_secret", SecretBox.encrypt(clean.binanceSecret))
            putBoolean("bn_testnet", clean.binanceTestnet)
        }.apply()
        _settings.value = clean
    }

    fun update(block: (AppSettings) -> AppSettings) = save(block(current))
}
