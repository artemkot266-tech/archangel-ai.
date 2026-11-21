package com.archangel.ai.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

enum class Role { USER, ASSISTANT, NOTE }

data class ChatMessage(
    val id: Long,
    val text: String,
    val role: Role,
    val time: Long = System.currentTimeMillis(),
) {
    val fromUser get() = role == Role.USER
}

/** История чата в файле `chat.json`: переживает перезапуск приложения. */
class ChatStore(context: Context) {
    private val file = File(context.filesDir, "chat.json")

    fun load(): List<ChatMessage> = try {
        if (!file.exists()) emptyList() else {
            val arr = JSONArray(file.readText())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                ChatMessage(
                    id = o.getLong("id"),
                    text = o.getString("text"),
                    role = runCatching { Role.valueOf(o.getString("role")) }.getOrDefault(Role.NOTE),
                    time = o.optLong("time", 0L),
                )
            }
        }
    } catch (e: Exception) {
        emptyList()
    }

    fun save(messages: List<ChatMessage>) {
        val arr = JSONArray()
        // Храним последние 300 сообщений — этого хватает и файл не разрастается.
        messages.takeLast(300).forEach {
            arr.put(
                JSONObject()
                    .put("id", it.id)
                    .put("text", it.text)
                    .put("role", it.role.name)
                    .put("time", it.time)
            )
        }
        runCatching { file.writeText(arr.toString()) }
    }

    fun clear() {
        runCatching { file.delete() }
    }
}
