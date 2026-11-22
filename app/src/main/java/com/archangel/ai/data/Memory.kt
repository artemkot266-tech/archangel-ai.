package com.archangel.ai.data

import android.content.Context
import org.json.JSONArray
import java.io.File

/** Память о пользователе: факты, которые он сам попросил запомнить. Учитываются в ответах ИИ. */
class MemoryStore(context: Context) {
    private val file = File(context.filesDir, "memory.json")

    fun facts(): List<String> = try {
        if (!file.exists()) emptyList() else JSONArray(file.readText()).let { a -> (0 until a.length()).map { a.getString(it) } }
    } catch (e: Exception) {
        emptyList()
    }

    fun add(fact: String) {
        val f = fact.trim().trimEnd('.').replaceFirstChar { it.uppercaseChar() }
        if (f.isEmpty() || facts().any { it.equals(f, ignoreCase = true) }) return
        save((facts() + f).takeLast(60))
    }

    /** Удаляет факты, где встречается слово; возвращает, сколько удалено. */
    fun forget(what: String): Int {
        val w = what.lowercase().trim()
        val all = facts()
        val keep = if (w in setOf("всё", "все", "все факты", "всё обо мне", "все обо мне")) emptyList()
        else all.filterNot { it.lowercase().contains(w) }
        save(keep)
        return all.size - keep.size
    }

    fun remove(fact: String) = save(facts().filterNot { it == fact })

    private fun save(list: List<String>) {
        val a = JSONArray(); list.forEach { a.put(it) }
        runCatching { file.writeText(a.toString()) }
    }
}
