package com.archangel.ai.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** «Мозг» ассистента — какой ИИ отвечает. */
enum class Brain(
    val title: String,
    val defaultModel: String,
    val defaultBaseUrl: String,
    val keyHint: String,
) {
    CLAUDE("Claude", "claude-sonnet-5-5", "https://api.anthropic.com", "console.anthropic.com"),
    DEEPSEEK("DeepSeek", "deepseek-chat", "https://api.deepseek.com", "platform.deepseek.com"),
    OPENAI("OpenAI-совместимый", "gpt-4o-mini", "https://api.openai.com/v1", "любой OpenAI-совместимый сервис"),
}

data class AppSettings(
    val brain: Brain = Brain.DEEPSEEK,
    val keys: Map<Brain, String> = emptyMap(),
    val models: Map<Brain, String> = emptyMap(),
    val baseUrls: Map<Brain, String> = emptyMap(),
    val persona: String = DEFAULT_PERSONA,
    val speakReplies: Boolean = true,
    val autoSendVoice: Boolean = true,
    val speechRate: Float = 1.05f,
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
            "Ты — Архангел, личный ИИ-ассистент. Отвечай по-русски, кратко и по делу, " +
                "без лишней воды: ответы часто озвучиваются голосом."
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
            .getOrDefault(Brain.DEEPSEEK)
        return AppSettings(
            brain = brain,
            keys = Brain.entries.associateWith { SecretBox.decrypt(prefs.getString("key_${it.name}", "") ?: "") },
            models = Brain.entries.associateWith { prefs.getString("model_${it.name}", "") ?: "" },
            baseUrls = Brain.entries.associateWith { prefs.getString("url_${it.name}", "") ?: "" },
            persona = prefs.getString("persona", null) ?: AppSettings.DEFAULT_PERSONA,
            speakReplies = prefs.getBoolean("speak", true),
            autoSendVoice = prefs.getBoolean("autosend", true),
            speechRate = prefs.getFloat("rate", 1.05f),
            binanceKey = SecretBox.decrypt(prefs.getString("bn_key", "") ?: ""),
            binanceSecret = SecretBox.decrypt(prefs.getString("bn_secret", "") ?: ""),
            binanceTestnet = prefs.getBoolean("bn_testnet", true),
        )
    }

    fun save(s: AppSettings) {
        prefs.edit().apply {
            putString("brain", s.brain.name)
            Brain.entries.forEach {
                putString("key_${it.name}", SecretBox.encrypt(s.keyFor(it).trim()))
                putString("model_${it.name}", s.models[it].orEmpty().trim())
                putString("url_${it.name}", s.baseUrls[it].orEmpty().trim())
            }
            putString("persona", s.persona)
            putBoolean("speak", s.speakReplies)
            putBoolean("autosend", s.autoSendVoice)
            putFloat("rate", s.speechRate)
            putString("bn_key", SecretBox.encrypt(s.binanceKey.trim()))
            putString("bn_secret", SecretBox.encrypt(s.binanceSecret.trim()))
            putBoolean("bn_testnet", s.binanceTestnet)
        }.apply()
        _settings.value = s.copy(
            binanceKey = s.binanceKey.trim(),
            binanceSecret = s.binanceSecret.trim(),
            keys = s.keys.mapValues { it.value.trim() },
        )
    }
}
