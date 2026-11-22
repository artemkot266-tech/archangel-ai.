package com.archangel.ai.market

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Песочница: бумажная торговля на виртуальные доллары по живым ценам.
 * Комиссия как на бирже — 0,1%. Ключи не нужны.
 */
data class PaperState(val cash: Double, val trades: List<Trade>, val start: Double = START) {
    companion object { const val START = 1000.0 }
}

data class PaperResult(val state: PaperState, val trade: Trade?, val error: String = "", val realized: Double? = null)

object PaperEngine {
    const val FEE = 0.001

    fun held(state: PaperState, symbol: String): Double =
        Pnl.coin(symbol, state.trades.filter { it.symbol == symbol }, 0.0).first.held

    fun buy(state: PaperState, symbol: String, usdt: Double, price: Double, time: Long = System.currentTimeMillis()): PaperResult {
        if (usdt <= 0 || price <= 0) return PaperResult(state, null, "Неверная сумма.")
        val fee = usdt * FEE
        if (usdt + fee > state.cash + 1e-9) return PaperResult(state, null, "В песочнице не хватает долларов: есть ${Indicators.formatPrice(state.cash)}.")
        val t = Trade(symbol, time, true, price, usdt / price, usdt, fee, "USDT")
        return PaperResult(state.copy(cash = state.cash - usdt - fee, trades = state.trades + t), t)
    }

    fun sell(state: PaperState, symbol: String, usdt: Double, price: Double, time: Long = System.currentTimeMillis()): PaperResult {
        val have = held(state, symbol)
        if (have <= 0) return PaperResult(state, null, "В песочнице нет ${symbol.removeSuffix("USDT")}.")
        val qty = minOf(usdt / price, have)
        val proceeds = qty * price
        val fee = proceeds * FEE
        val t = Trade(symbol, time, false, price, qty, proceeds, fee, "USDT")
        val before = Pnl.coin(symbol, state.trades.filter { it.symbol == symbol }, price).first.realized
        val next = state.copy(cash = state.cash + proceeds - fee, trades = state.trades + t)
        val after = Pnl.coin(symbol, next.trades.filter { it.symbol == symbol }, price).first.realized
        return PaperResult(next, t, realized = after - before)
    }

    /** Стоимость всего: доллары + монеты по текущим ценам. */
    fun equity(state: PaperState, prices: Map<String, Double>): Double =
        state.cash + state.trades.map { it.symbol }.distinct().sumOf { s -> held(state, s) * (prices[s] ?: 0.0) }
}

class PaperStore(context: Context) {
    private val file = File(context.filesDir, "paper.json")

    fun load(): PaperState = try {
        if (!file.exists()) PaperState(PaperState.START, emptyList()) else JSONObject(file.readText()).let { o ->
            val arr = o.optJSONArray("trades") ?: JSONArray()
            PaperState(
                o.getDouble("cash"),
                (0 until arr.length()).map { i ->
                    val t = arr.getJSONObject(i)
                    Trade(t.getString("s"), t.getLong("t"), t.getBoolean("b"), t.getDouble("p"), t.getDouble("q"),
                        t.getDouble("v"), t.optDouble("f", 0.0), "USDT")
                },
                o.optDouble("start", PaperState.START),
            )
        }
    } catch (e: Exception) {
        PaperState(PaperState.START, emptyList())
    }

    fun save(s: PaperState) {
        val arr = JSONArray()
        s.trades.forEach { t ->
            arr.put(JSONObject().put("s", t.symbol).put("t", t.time).put("b", t.buy).put("p", t.price)
                .put("q", t.qty).put("v", t.quote).put("f", t.commission))
        }
        runCatching { file.writeText(JSONObject().put("cash", s.cash).put("start", s.start).put("trades", arr).toString()) }
    }

    fun reset(amount: Double = PaperState.START): PaperState = PaperState(amount, emptyList(), amount).also { save(it) }
}

/** Дневник трейдера: почему вошли и чем кончилось. */
data class JournalEntry(
    val id: Long,
    val time: Long,
    val symbol: String,
    val buy: Boolean,
    val usdt: Double,
    val price: Double,
    val reason: String,
    val paper: Boolean,
)

class JournalStore(context: Context) {
    private val file = File(context.filesDir, "journal.json")

    fun all(): List<JournalEntry> = try {
        if (!file.exists()) emptyList() else JSONArray(file.readText()).let { a ->
            (0 until a.length()).map {
                val o = a.getJSONObject(it)
                JournalEntry(o.getLong("id"), o.getLong("time"), o.getString("symbol"), o.getBoolean("buy"),
                    o.getDouble("usdt"), o.getDouble("price"), o.optString("reason"), o.optBoolean("paper"))
            }
        }
    } catch (e: Exception) {
        emptyList()
    }

    fun add(e: JournalEntry) = save((listOf(e) + all()).take(300))
    fun setReason(id: Long, reason: String) = save(all().map { if (it.id == id) it.copy(reason = reason.trim()) else it })
    fun remove(id: Long) = save(all().filter { it.id != id })

    private fun save(list: List<JournalEntry>) {
        val a = JSONArray()
        list.forEach { e ->
            a.put(JSONObject().put("id", e.id).put("time", e.time).put("symbol", e.symbol).put("buy", e.buy)
                .put("usdt", e.usdt).put("price", e.price).put("reason", e.reason).put("paper", e.paper))
        }
        runCatching { file.writeText(a.toString()) }
    }
}

/**
 * Защита от эмоций: после трёх убыточных продаж подряд Архангел предлагает паузу на сутки.
 * Не запрещает — предупреждает перед каждой сделкой, пока пауза идёт.
 */
object TiltGuard {
    private const val PREFS = "archangel_tilt"
    const val PAUSE_MS = 24 * 3_600_000L

    /** Чистая логика: пауза до какого времени после очередного результата (0 — паузы нет). */
    fun pauseUntil(results: List<Double>, now: Long): Long =
        if (results.size >= 3 && results.takeLast(3).all { it < 0 }) now + PAUSE_MS else 0L

    fun record(context: Context, pnl: Double) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val list = (p.getString("results", "") ?: "").split(',').mapNotNull { it.toDoubleOrNull() } + pnl
        val until = pauseUntil(list, System.currentTimeMillis())
        p.edit().putString("results", list.takeLast(10).joinToString(","))
            .apply { if (until > 0) putLong("until", until).putString("results", "") }
            .apply()
    }

    fun activeUntil(context: Context): Long {
        val until = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong("until", 0)
        return if (until > System.currentTimeMillis()) until else 0
    }

    fun clear(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
}
