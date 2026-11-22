package com.archangel.ai.features

import com.archangel.ai.ai.FriendlyException
import com.archangel.ai.ai.Http
import com.archangel.ai.data.AppSettings
import org.json.JSONArray
import org.json.JSONObject

/**
 * Умный дом через Home Assistant (REST API).
 * Устройства Xiaomi/Mi Home, Google, Tuya и др. подключаются к Home Assistant,
 * а Архангел управляет ими по имени: «включи свет в спальне».
 */
object HomeAssistant {
    private data class Entity(val id: String, val name: String) {
        val domain get() = id.substringBefore('.')
    }

    private val deviceDomains = listOf(
        listOf("свет", "ламп", "люстр", "подсветк", "гирлянд") to listOf("light", "switch"),
        listOf("розетк", "чайник", "бойлер") to listOf("switch"),
        listOf("кондиционер", "обогреват") to listOf("climate", "switch"),
        listOf("вентилятор") to listOf("fan", "switch"),
        listOf("пылесос") to listOf("vacuum"),
        listOf("телевизор", "телик") to listOf("media_player", "switch"),
        listOf("увлажнител") to listOf("humidifier", "switch"),
    )
    private val stop = setOf("в", "на", "во", "у", "и", "мне", "пожалуйста", "весь", "все", "всё")

    private fun headers(s: AppSettings) = mapOf("Authorization" to "Bearer ${s.haToken}", "Content-Type" to "application/json")

    suspend fun control(s: AppSettings, on: Boolean, target: String): String {
        val base = s.haUrl.trimEnd('/')
        val res = Http.request("$base/api/states", headers = headers(s), readTimeoutMs = 15_000)
        if (res.code == 401) throw FriendlyException("Home Assistant не принял токен. Создайте новый: профиль → Безопасность → Долгосрочные токены.")
        if (!res.ok) throw FriendlyException("Home Assistant не отвечает (${res.code}). Проверьте адрес и что телефон в той же сети.")
        val arr = JSONArray(res.body)
        val entities = (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val id = o.getString("entity_id")
            Entity(id, o.optJSONObject("attributes")?.optString("friendly_name").orEmpty().ifBlank { id })
        }
        val t = target.lowercase()
        val domains = deviceDomains.firstOrNull { (words, _) -> words.any { t.contains(it) } }?.second
        val pool = entities.filter { domains == null || it.domain in domains }
        if (pool.isEmpty()) return "Не нашёл такого устройства в Home Assistant."
        // Слова-уточнения: «в спальне» → «спальн»
        val deviceWords = deviceDomains.flatMap { it.first }
        val hints = t.split(' ', ',').map { it.trim() }.filter { w -> w.length > 2 && w !in stop && deviceWords.none { w.startsWith(it) } }
            .map { if (it.length > 5) it.take(it.length - 2) else it }
        val scored = pool.map { e ->
            val hay = (e.name + " " + e.id).lowercase()
            e to hints.count { hay.contains(it) }
        }.sortedByDescending { it.second }
        val best = scored.first()
        val chosen = when {
            hints.isEmpty() && pool.size == 1 -> pool.first()
            hints.isNotEmpty() && best.second > 0 -> best.first
            pool.size == 1 -> pool.first()
            else -> return "Уточните, какое именно: " + pool.take(5).joinToString(", ") { it.name } + "."
        }
        val service = when (chosen.domain) {
            "vacuum" -> if (on) "start" else "return_to_base"
            else -> if (on) "turn_on" else "turn_off"
        }
        val call = Http.request(
            "$base/api/services/${chosen.domain}/$service", method = "POST", headers = headers(s),
            body = JSONObject().put("entity_id", chosen.id).toString(), readTimeoutMs = 15_000,
        )
        if (!call.ok) throw FriendlyException("Home Assistant не выполнил команду (${call.code}).")
        return (if (on) "Включаю" else "Выключаю") + " «${chosen.name}»."
    }
}
