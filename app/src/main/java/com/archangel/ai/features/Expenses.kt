package com.archangel.ai.features

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Calendar
import java.util.Locale

/** Один расход: «потратил 200 на кофе». currency — «₽», «грн», «$», «€», «₸» или пусто (не названа). */
data class Expense(val id: Long, val amount: Double, val currency: String, val category: String, val at: Long)

enum class ExpensePeriod(val title: String) {
    TODAY("сегодня"), WEEK("за неделю"), MONTH("за этот месяц"), ALL("за всё время")
}

/** Учёт расходов голосом: разбор фраз и подсчёт итогов. Без интернета и без ИИ. */
object Expenses {

    private val amountRe = Regex(
        "(?iu)(\\d[\\d ]*(?:[.,]\\d+)?)\\s*(?:(тысяч[\\p{L}]*|тыс|к)(?![\\p{L}\\d_]))?\\.?\\s*" +
            "(?:(руб[\\p{L}]*|р|₽|грн|гривн[\\p{L}]*|гривен|доллар[\\p{L}]*|бакс[\\p{L}]*|\\\$|usd|евро|€|eur|тенге|₸)(?![\\p{L}\\d_]))?"
    )

    private fun currencyOf(word: String): String {
        val w = word.lowercase()
        return when {
            w.isEmpty() -> ""
            w.startsWith("руб") || w == "р" || w == "₽" -> "₽"
            w.startsWith("грн") || w.startsWith("гривн") || w == "гривен" -> "грн"
            w.startsWith("доллар") || w.startsWith("бакс") || w == "$" || w == "usd" -> "$"
            w == "евро" || w == "€" || w == "eur" -> "€"
            w == "тенге" || w == "₸" -> "₸"
            else -> ""
        }
    }

    /**
     * «200 на кофе», «на такси 350 рублей», «2,5 тысячи на бензин» → (сумма, валюта, на что).
     * Нет суммы — null.
     */
    fun parseSpend(rest: String): Triple<Double, String, String>? {
        val text = rest.trim()
        val m = amountRe.find(text) ?: return null
        val base = m.groupValues[1].replace(" ", "").replace(',', '.').toDoubleOrNull() ?: return null
        val amount = if (m.groupValues[2].isNotEmpty()) base * 1000 else base
        if (amount <= 0) return null
        val currency = currencyOf(m.groupValues[3])
        val category = (text.removeRange(m.range) + " ")
            .replace(Regex("(?iu)(^|\\s)(сегодня|вчера|денег|всего)(?=\\s)"), " ")
            .trim()
            .replace(Regex("(?iu)^(на|за|в|для)\\s+"), "")
            .replace(Regex("\\s+"), " ")
            .trim(' ', ',', '.', ':', '-')
        return Triple(amount, currency, category.ifEmpty { "прочее" })
    }

    /** «за неделю», «сегодня», «в этом месяце» → период. По умолчанию — текущий месяц. */
    fun periodOf(text: String): ExpensePeriod {
        val t = text.lowercase()
        return when {
            Regex("(?iu)(сегодня|за день)").containsMatchIn(t) -> ExpensePeriod.TODAY
            Regex("(?iu)недел").containsMatchIn(t) -> ExpensePeriod.WEEK
            Regex("(?iu)(все время|всё время|всего|вообще)").containsMatchIn(t) -> ExpensePeriod.ALL
            else -> ExpensePeriod.MONTH
        }
    }

    /** «на кофе за неделю» → «кофе» (фильтр по категории) или null. */
    fun categoryFilterOf(text: String): String? =
        Regex("(?iu)(?:^|\\s)на\\s+([\\p{L}\\d_]+)").find(text)?.groupValues?.get(1)
            ?.takeIf { it !in setOf("этой", "этом", "прошлой", "неделе", "неделю", "сегодня") }

    fun periodStart(period: ExpensePeriod, now: Calendar = Calendar.getInstance()): Long {
        val c = now.clone() as Calendar
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        return when (period) {
            ExpensePeriod.TODAY -> c.timeInMillis
            ExpensePeriod.WEEK -> { c.add(Calendar.DAY_OF_YEAR, -6); c.timeInMillis }
            ExpensePeriod.MONTH -> { c.set(Calendar.DAY_OF_MONTH, 1); c.timeInMillis }
            ExpensePeriod.ALL -> 0L
        }
    }

    /** 4350.0 → «4 350», 12.5 → «12,50». */
    fun formatAmount(v: Double): String =
        if (v == Math.floor(v)) String.format(Locale.US, "%,.0f", v).replace(',', ' ')
        else String.format(Locale.US, "%,.2f", v).replace(',', ' ').replace('.', ',')

    fun money(v: Double, currency: String) = formatAmount(v) + if (currency.isEmpty()) "" else " $currency"

    /** Итоги по валютам: «4 350 ₽ и 20 $». */
    fun totalText(list: List<Expense>): String =
        list.groupBy { it.currency }
            .map { (cur, l) -> money(l.sumOf { it.amount }, cur) }
            .joinToString(" и ")
            .ifEmpty { "0" }

    private fun matches(category: String, filter: String): Boolean {
        val stem = filter.lowercase().let { if (it.length > 4) it.dropLast(1) else it }
        return category.lowercase().split(' ').any { it.startsWith(stem) }
    }

    /** «За сегодня всего: 450 ₽.» — короткая добавка после записи расхода. */
    fun summaryToday(all: List<Expense>, now: Calendar = Calendar.getInstance()): String {
        val from = periodStart(ExpensePeriod.TODAY, now)
        val l = all.filter { it.at >= from }
        return if (l.isEmpty()) "" else "За сегодня всего: ${totalText(l)}."
    }

    /** Голосовой ответ на «сколько я потратил…». */
    fun summary(all: List<Expense>, period: ExpensePeriod, category: String? = null, now: Calendar = Calendar.getInstance()): String {
        val from = periodStart(period, now)
        val inPeriod = all.filter { it.at >= from && it.at <= now.timeInMillis + 60_000 }
        if (category != null) {
            val l = inPeriod.filter { matches(it.category, category) }
            return if (l.isEmpty()) "На «$category» ${period.title} расходов нет."
            else "На «$category» ${period.title}: ${totalText(l)}."
        }
        if (inPeriod.isEmpty()) return "Расходов ${period.title} нет. Скажите, например: «потратил 200 на кофе»."
        val top = inPeriod.groupBy { it.category to it.currency }
            .map { (k, l) -> Triple(k.first, k.second, l.sumOf { it.amount }) }
            .sortedByDescending { it.third }
            .take(3)
            .joinToString(", ") { "${it.first} ${money(it.third, it.second)}" }
        val head = period.title.replaceFirstChar { it.uppercase() }
        return "$head потрачено ${totalText(inPeriod)}. Больше всего: $top."
    }
}

/** Расходы — JSON-файл во внутренней памяти приложения. */
class ExpenseStore(context: Context) {
    private val file = File(context.filesDir, "expenses.json")

    fun all(): List<Expense> = try {
        if (!file.exists()) emptyList()
        else JSONArray(file.readText()).let { a ->
            (0 until a.length()).map { i ->
                val o = a.getJSONObject(i)
                Expense(o.getLong("id"), o.getDouble("amount"), o.optString("currency"), o.optString("category", "прочее"), o.getLong("at"))
            }
        }.sortedByDescending { it.at }
    } catch (e: Exception) {
        emptyList()
    }

    fun add(amount: Double, currency: String, category: String): Expense {
        val now = System.currentTimeMillis()
        val e = Expense(now, amount, currency, category, now)
        save(all() + e)
        return e
    }

    fun delete(id: Long) = save(all().filter { it.id != id })

    /** Удаляет последний добавленный расход и возвращает его. */
    fun undoLast(): Expense? {
        val last = all().maxByOrNull { it.id } ?: return null
        delete(last.id)
        return last
    }

    private fun save(list: List<Expense>) {
        val a = JSONArray()
        list.forEach {
            a.put(JSONObject().put("id", it.id).put("amount", it.amount).put("currency", it.currency).put("category", it.category).put("at", it.at))
        }
        runCatching { file.writeText(a.toString()) }
    }
}
