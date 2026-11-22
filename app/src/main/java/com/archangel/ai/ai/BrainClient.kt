package com.archangel.ai.ai

import com.archangel.ai.data.AppSettings
import com.archangel.ai.data.Brain
import org.json.JSONArray
import org.json.JSONObject

/** Одна реплика для ИИ: role = "user" или "assistant". */
data class Turn(val role: String, val text: String, val imageJpegBase64: String? = null)

/**
 * «Мозг» ассистента: отправляет диалог в Claude, DeepSeek
 * или любой OpenAI-совместимый сервис и возвращает текст ответа.
 */
class BrainClient {

    suspend fun ask(settings: AppSettings, system: String, turns: List<Turn>, maxTokens: Int = 1024): String {
        val key = settings.keyFor()
        if (key.isBlank()) {
            throw FriendlyException(
                "Мозг не подключён: добавьте API-ключ ${settings.brain.title} в настройках."
            )
        }
        val clean = normalize(turns)
        if (clean.isEmpty()) throw FriendlyException("Пустой запрос.")
        return when (settings.brain) {
            Brain.CLAUDE -> askClaude(settings, key, system, clean, maxTokens)
            else -> askWithFallback(settings, key, system, clean, maxTokens)
        }
    }

    /**
     * Модель могла смениться или быть недоступной на бесплатном тарифе — тогда пробуем запасные
     * (только если пользователь не задал модель сам). Удачную запоминаем до перезапуска.
     */
    private val workingModel = HashMap<Brain, String>()

    private suspend fun askWithFallback(
        s: AppSettings, key: String, system: String, turns: List<Turn>, maxTokens: Int,
    ): String {
        val custom = s.models[s.brain]?.isNotBlank() == true
        val candidates = if (custom) listOf(s.modelFor())
        else (listOfNotNull(workingModel[s.brain]) + s.brain.defaultModel + s.brain.fallbackModels).distinct()
        var last: FriendlyException? = null
        for (model in candidates) {
            try {
                val text = askOpenAiCompatible(s.copy(models = s.models + (s.brain to model)), key, system, turns, maxTokens)
                workingModel[s.brain] = model
                return text
            } catch (e: ModelUnavailable) {
                last = FriendlyException(e.message ?: "Модель недоступна")
            }
        }
        throw last ?: FriendlyException("Модель недоступна.")
    }

    /** Ошибка, при которой стоит попробовать другую модель (не ключ и не сеть). */
    private class ModelUnavailable(message: String) : Exception(message)

    /** API требуют чередования user/assistant и первой реплики от пользователя. */
    private fun normalize(turns: List<Turn>): List<Turn> {
        val out = ArrayList<Turn>()
        for (t in turns.filter { it.text.isNotBlank() }) {
            if (out.isEmpty() && t.role != "user") continue
            if (out.isNotEmpty() && out.last().role == t.role) {
                out[out.lastIndex] = Turn(t.role, out.last().text + "\n\n" + t.text, t.imageJpegBase64 ?: out.last().imageJpegBase64)
            } else out.add(t)
        }
        while (out.isNotEmpty() && out.last().role != "user") out.removeAt(out.lastIndex)
        return out
    }

    private suspend fun askClaude(
        s: AppSettings, key: String, system: String, turns: List<Turn>, maxTokens: Int,
    ): String {
        val messages = JSONArray()
        turns.forEach { t ->
            val content: Any = if (t.imageJpegBase64 != null) {
                JSONArray()
                    .put(
                        JSONObject().put("type", "image").put(
                            "source",
                            JSONObject().put("type", "base64").put("media_type", "image/jpeg").put("data", t.imageJpegBase64),
                        )
                    )
                    .put(JSONObject().put("type", "text").put("text", t.text))
            } else t.text
            messages.put(JSONObject().put("role", t.role).put("content", content))
        }
        val body = JSONObject()
            .put("model", s.modelFor())
            .put("max_tokens", maxTokens)
            .put("system", system)
            .put("messages", messages)
        val res = Http.request(
            url = s.baseUrlFor().trimEnd('/') + "/v1/messages",
            method = "POST",
            headers = mapOf(
                "x-api-key" to key,
                "anthropic-version" to "2023-06-01",
                "content-type" to "application/json",
            ),
            body = body.toString(),
        )
        if (!res.ok) throw FriendlyException(explain(res, Brain.CLAUDE))
        val content = JSONObject(res.body).optJSONArray("content") ?: JSONArray()
        val sb = StringBuilder()
        for (i in 0 until content.length()) {
            val part = content.getJSONObject(i)
            if (part.optString("type") == "text") sb.append(part.optString("text"))
        }
        return sb.toString().trim().ifEmpty { throw FriendlyException("ИИ вернул пустой ответ.") }
    }

    private suspend fun askOpenAiCompatible(
        s: AppSettings, key: String, system: String, turns: List<Turn>, maxTokens: Int,
    ): String {
        val messages = JSONArray().put(JSONObject().put("role", "system").put("content", system))
        turns.forEach { t ->
            val content: Any = if (t.imageJpegBase64 != null) {
                JSONArray()
                    .put(JSONObject().put("type", "text").put("text", t.text))
                    .put(
                        JSONObject().put("type", "image_url")
                            .put("image_url", JSONObject().put("url", "data:image/jpeg;base64," + t.imageJpegBase64))
                    )
            } else t.text
            messages.put(JSONObject().put("role", t.role).put("content", content))
        }
        val body = JSONObject()
            .put("model", s.modelFor())
            .put("max_tokens", maxTokens)
            .put("messages", messages)
        val res = Http.request(
            url = s.baseUrlFor().trimEnd('/') + "/chat/completions",
            method = "POST",
            headers = mapOf(
                "Authorization" to "Bearer $key",
                "Content-Type" to "application/json",
            ),
            body = body.toString(),
        )
        if (!res.ok) {
            val modelProblem = res.code == 404 || (res.code == 400 && res.body.contains("model", ignoreCase = true)) ||
                (res.code == 429 && s.brain.fallbackModels.isNotEmpty())
            if (modelProblem && s.models[s.brain]?.isNotBlank() == true && s.brain.fallbackModels.isNotEmpty()) {
                throw ModelUnavailable(explain(res, s.brain))
            }
            throw FriendlyException(explain(res, s.brain))
        }
        val choices = JSONObject(res.body).optJSONArray("choices")
        val text = choices?.optJSONObject(0)?.optJSONObject("message")?.optString("content").orEmpty()
        return text.trim().ifEmpty { throw FriendlyException("ИИ вернул пустой ответ.") }
    }

    private fun explain(res: HttpResult, brain: Brain): String {
        val apiMessage = runCatching {
            val o = JSONObject(res.body)
            o.optJSONObject("error")?.optString("message") ?: o.optString("message")
        }.getOrNull().orEmpty().take(200)
        val human = when (res.code) {
            400 -> "Запрос отклонён (проверьте название модели)."
            401, 403 -> "Ключ ${brain.title} не подходит или у него нет доступа."
            402 -> "На балансе ${brain.title} закончились средства."
            413 -> "Слишком длинный запрос для этой модели. Очистите историю чата."
            404 -> "Модель или адрес API не найдены."
            429 -> "Слишком много запросов или исчерпан лимит. Подождите немного."
            in 500..599 -> "Сервер ${brain.title} сейчас недоступен."
            else -> "Ошибка ${res.code}."
        }
        return if (apiMessage.isNotBlank()) "$human\n($apiMessage)" else human
    }
}
