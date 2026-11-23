package com.archangel.ai.features

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Calendar
import java.util.Locale

/** Одна запись: сколько выпито (мл) и когда. */
data class WaterEntry(val id: Long, val ml: Int, val at: Long)

/** Трекер воды: разбор фраз «выпил стакан воды» и подсчёт за день. Без интернета и без ИИ. */
object Water {
    const val GLASS = 250
    const val BOTTLE = 500
    const val DEFAULT_GOAL = 2000

    private val words = mapOf(
        "один" to 1.0, "одну" to 1.0, "одна" to 1.0, "два" to 2.0, "две" to 2.0, "три" to 3.0,
        "четыре" to 4.0, "пять" to 5.0, "пол" to 0.5, "половину" to 0.5, "полтора" to 1.5, "полторы" to 1.5,
    )

    private val unitRe = Regex(
        "(?iu)(?<![\\p{L}\\d_])(?:(\\d+(?:[.,]\\d+)?|один|одну|одна|два|две|три|четыре|пять|половину|полтора|полторы|пол)\\s*-?\\s*)?" +
            "(мл|миллилитр[\\p{L}]*|л|литр[\\p{L}]*|стакан[\\p{L}]*|круж[\\p{L}]*|чашк[\\p{L}]*|чашечк[\\p{L}]*|бутыл[\\p{L}]*|глот[\\p{L}]*)(?![\\p{L}\\d_])"
    )
    private val bareNumberRe = Regex("(?iu)(?<![\\p{L}\\d_])(\\d{2,4})(?![\\p{L}\\d_])")

    /**
     * «стакан воды» → 250, «2 стакана» → 500, «300 мл» → 300, «пол литра» / «0,5 л» → 500,
     * «бутылку» → 500, «воды» без количества → 250. Неразумные значения (больше 3 л) → null.
     */
    fun parseAmount(text: String): Int? {
        val t = text.lowercase().replace('ё', 'е').replace("пол-", "пол ").replace("поллитр", "пол литр")
        val m = unitRe.find(t)
        val ml: Double = if (m != null) {
            val n = m.groupValues[1].let { if (it.isEmpty()) 1.0 else words[it] ?: it.replace(',', '.').toDoubleOrNull() ?: 1.0 }
            val unit = m.groupValues[2]
            when {
                unit == "мл" || unit.startsWith("миллилитр") -> n
                unit == "л" || unit.startsWith("литр") -> n * 1000
                unit.startsWith("бутыл") -> n * BOTTLE
                unit.startsWith("глот") -> n * 30
                else -> n * GLASS // стакан, кружка, чашка
            }
        } else {
            bareNumberRe.find(t)?.groupValues?.get(1)?.toDoubleOrNull() ?: GLASS.toDouble()
        }
        val v = Math.round(ml).toInt()
        return v.takeIf { it in 1..3000 }
    }

    private val waterWordRe = Regex("(?iu)(?<![\\p{L}\\d_])(вода|воды|воду|водой|водички|водичку|водичка)(?![\\p{L}\\d_])")

    /** Есть ли во фразе именно вода (не «водка»). */
    fun mentionsWater(text: String): Boolean = waterWordRe.containsMatchIn(text)

    /** Названо ли количество: «стакан», «300 мл», «пол литра». */
    fun hasAmount(text: String): Boolean = unitRe.containsMatchIn(text.lowercase().replace('ё', 'е')) ||
        bareNumberRe.containsMatchIn(text)

    /** «норма воды 2,5 литра», «1800 мл» → мл. Разумные пределы 500…6000. */
    fun parseGoal(text: String): Int? {
        val t = text.lowercase().replace('ё', 'е')
        val m = Regex("(?iu)(?<![\\p{L}\\d_])(\\d+(?:[.,]\\d+)?|два|три|полтора|полторы)\\s*(мл|миллилитр[\\p{L}]*|л|литр[\\p{L}]*)?(?![\\p{L}\\d_])").find(t) ?: return null
        val n = words[m.groupValues[1]] ?: m.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
        val unit = m.groupValues[2]
        val ml = when {
            unit == "мл" || unit.startsWith("миллилитр") -> n
            unit.isNotEmpty() -> n * 1000
            n <= 10 -> n * 1000 // «норма воды 2» — литры
            else -> n
        }
        return Math.round(ml).toInt().takeIf { it in 500..6000 }
    }

    fun dayStart(now: Calendar = Calendar.getInstance()): Long {
        val c = now.clone() as Calendar
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    fun today(all: List<WaterEntry>, now: Calendar = Calendar.getInstance()): Int {
        val from = dayStart(now)
        return all.filter { it.at >= from }.sumOf { it.ml }
    }

    /** 1250 → «1 250 мл», 2000 → «2 л», 2500 → «2,5 л». */
    fun format(ml: Int): String = when {
        ml >= 1000 && ml % 100 == 0 ->
            (if (ml % 1000 == 0) "${ml / 1000}" else String.format(Locale.US, "%.1f", ml / 1000.0).replace('.', ',')) + " л"
        else -> String.format(Locale.US, "%,d", ml).replace(',', ' ') + " мл"
    }

    /** «Сегодня 1 250 мл из 2 л — осталось 750 мл.» */
    fun progressText(drunk: Int, goal: Int): String = when {
        drunk <= 0 -> "Сегодня воды ещё не было. Норма — ${format(goal)}. Скажите: «выпил стакан воды»."
        drunk >= goal -> "Сегодня ${format(drunk)} — норма ${format(goal)} выполнена. Отлично!"
        else -> "Сегодня ${format(drunk)} из ${format(goal)} — осталось ${format(goal - drunk)}."
    }

    /** Доля выполнения 0…1 для полоски на экране. */
    fun fraction(drunk: Int, goal: Int): Float = if (goal <= 0) 0f else (drunk.toFloat() / goal).coerceIn(0f, 1f)
}

/** Вода — JSON-файл во внутренней памяти приложения: норма и записи за последние 60 дней. */
class WaterStore(context: Context) {
    private val file = File(context.filesDir, "water.json")

    private fun load(): Pair<Int, List<WaterEntry>> = try {
        if (!file.exists()) Water.DEFAULT_GOAL to emptyList()
        else {
            val o = JSONObject(file.readText())
            val a = o.optJSONArray("log") ?: JSONArray()
            val list = (0 until a.length()).map { i ->
                val e = a.getJSONObject(i)
                WaterEntry(e.getLong("id"), e.getInt("ml"), e.getLong("at"))
            }
            o.optInt("goal", Water.DEFAULT_GOAL) to list
        }
    } catch (e: Exception) {
        Water.DEFAULT_GOAL to emptyList()
    }

    fun all(): List<WaterEntry> = load().second.sortedByDescending { it.at }

    fun goal(): Int = load().first

    fun setGoal(ml: Int) = save(ml, load().second)

    fun add(ml: Int): WaterEntry {
        val now = System.currentTimeMillis()
        val (goal, list) = load()
        val id = maxOf(now, (list.maxOfOrNull { it.id } ?: 0L) + 1)
        val e = WaterEntry(id, ml, now)
        save(goal, list + e)
        return e
    }

    fun delete(id: Long) { val (g, l) = load(); save(g, l.filter { it.id != id }) }

    /** Удаляет последнюю запись и возвращает её. */
    fun undoLast(): WaterEntry? {
        val last = load().second.maxByOrNull { it.id } ?: return null
        delete(last.id)
        return last
    }

    private fun save(goal: Int, list: List<WaterEntry>) {
        val keepFrom = System.currentTimeMillis() - 60L * 24 * 3600 * 1000
        val a = JSONArray()
        list.filter { it.at >= keepFrom }.forEach { a.put(JSONObject().put("id", it.id).put("ml", it.ml).put("at", it.at)) }
        runCatching { file.writeText(JSONObject().put("goal", goal).put("log", a).toString()) }
    }
}
