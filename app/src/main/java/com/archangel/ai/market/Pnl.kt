package com.archangel.ai.market

/** Сделка из истории биржи. qty — в монете, quote — в USDT, commission — в commissionAsset. */
data class Trade(
    val symbol: String,
    val time: Long,
    val buy: Boolean,
    val price: Double,
    val qty: Double,
    val quote: Double,
    val commission: Double,
    val commissionAsset: String,
)

/**
 * Итог по монете.
 * avgCost — средняя цена покупки оставшегося количества; realized — зафиксированная прибыль (продажи);
 * unrealized — бумажная прибыль по текущей цене.
 */
data class CoinPnl(
    val symbol: String,
    val held: Double,
    val avgCost: Double,
    val realized: Double,
    val unrealized: Double,
    val trades: Int,
    val wins: Int,
    val losses: Int,
) {
    val coin get() = symbol.removeSuffix("USDT")
    val total get() = realized + unrealized
}

/** Точка накопленной зафиксированной прибыли во времени — для графика. */
data class PnlPoint(val time: Long, val cumulative: Double)

data class PnlReport(val coins: List<CoinPnl>, val curve: List<PnlPoint>) {
    val realized get() = coins.sumOf { it.realized }
    val unrealized get() = coins.sumOf { it.unrealized }
    val total get() = realized + unrealized
    val trades get() = coins.sumOf { it.trades }
    val wins get() = coins.sumOf { it.wins }
    val losses get() = coins.sumOf { it.losses }
}

/** Расчёт прибыли методом средней цены. Чистые функции — покрыты тестами. */
object Pnl {
    /**
     * @param since учитывать зафиксированную прибыль только от продаж после этого момента (0 — за всё время).
     * Средняя цена считается по всей истории, иначе прибыль от старых покупок будет неверной.
     */
    fun coin(symbol: String, trades: List<Trade>, price: Double, since: Long = 0): Pair<CoinPnl, List<Pair<Long, Double>>> {
        val base = symbol.removeSuffix("USDT")
        var held = 0.0
        var cost = 0.0 // стоимость текущего остатка в USDT
        var realized = 0.0
        var wins = 0; var losses = 0; var counted = 0
        val events = ArrayList<Pair<Long, Double>>()
        for (t in trades.sortedBy { it.time }) {
            // Комиссия монетой уменьшает полученное количество, комиссия в USDT — увеличивает затраты/уменьшает выручку
            val feeBase = if (t.commissionAsset == base) t.commission else 0.0
            val feeQuote = if (t.commissionAsset == "USDT") t.commission else 0.0
            if (t.buy) {
                held += t.qty - feeBase
                cost += t.quote + feeQuote
            } else {
                val sold = minOf(t.qty, held).coerceAtLeast(0.0)
                val avg = if (held > 0) cost / held else 0.0
                val proceeds = t.quote - feeQuote - feeBase * t.price
                // Продажа монет, купленных не здесь (без известной цены покупки), в прибыль не идёт
                val known = if (t.qty > 0) sold / t.qty else 0.0
                val pnl = proceeds * known - avg * sold
                cost -= avg * sold
                held -= sold
                if (held < 1e-12) { held = 0.0; cost = 0.0 }
                if (t.time >= since) {
                    realized += pnl
                    counted++
                    if (pnl >= 0) wins++ else losses++
                    events += t.time to pnl
                }
            }
            if (t.buy && t.time >= since) counted++
        }
        val avgCost = if (held > 0) cost / held else 0.0
        val unrealized = if (held > 0 && price > 0) held * price - cost else 0.0
        return CoinPnl(symbol, held, avgCost, realized, unrealized, counted, wins, losses) to events
    }

    fun report(bySymbol: Map<String, List<Trade>>, prices: Map<String, Double>, since: Long = 0): PnlReport {
        val results = bySymbol.map { (sym, list) -> coin(sym, list, prices[sym] ?: 0.0, since) }
        val events = results.flatMap { it.second }.sortedBy { it.first }
        var acc = 0.0
        val curve = events.map { (t, v) -> acc += v; PnlPoint(t, acc) }
        return PnlReport(results.map { it.first }.filter { it.trades > 0 || it.held > 0 }.sortedByDescending { kotlin.math.abs(it.total) }, curve)
    }

    /** Подсказка о перекосе портфеля: одна монета (не стейблкоин) больше 60%. */
    fun concentrationHint(p: Portfolio): String? {
        if (p.total <= 0) return null
        val stable = setOf("USDT", "USDC", "FDUSD", "BUSD", "TUSD", "DAI")
        val top = p.items.filter { it.asset !in stable }.maxByOrNull { it.value } ?: return null
        val share = top.value / p.total
        return if (share > 0.6) "${top.asset} — ${(share * 100).toInt()}% портфеля. Если она резко упадёт, пострадает почти всё. Подумайте о распределении." else null
    }
}

/** Загрузка истории сделок и расчёт итогов — общий для экрана и голоса. */
object PnlLoader {
    private val stable = setOf("USDT", "USDC", "FDUSD", "BUSD", "TUSD", "DAI")

    suspend fun load(
        binance: BinanceClient, s: com.archangel.ai.data.AppSettings, extraSymbols: List<String>, days: Int,
    ): PnlReport {
        val held = runCatching { binance.balances(s.binanceKey, s.binanceSecret, s.binanceTestnet) }.getOrDefault(emptyList())
            .map { it.asset }.filter { it !in stable }.map { it + "USDT" }
        val symbols = (held + s.watchlist + extraSymbols).distinct().take(10)
        val trades = symbols.associateWith { sym ->
            runCatching { binance.myTrades(sym, s.binanceKey, s.binanceSecret, s.binanceTestnet) }.getOrDefault(emptyList())
        }.filterValues { it.isNotEmpty() }
        val prices = runCatching { binance.tickers(trades.keys.toList()) }.getOrDefault(emptyList()).associate { it.symbol to it.last }
        val since = if (days > 0) System.currentTimeMillis() - days * 86_400_000L else 0L
        return Pnl.report(trades, prices, since)
    }

    fun spoken(r: PnlReport, days: Int): String {
        val period = when (days) { 7 -> "за неделю"; 30 -> "за месяц"; 1 -> "за сутки"; else -> "за всё время" }
        if (r.coins.isEmpty()) return "Сделок пока нет."
        fun usd(v: Double) = (if (v >= 0) "плюс " else "минус ") + Analytics.spokenPrice(kotlin.math.abs(v)) + " доллара"
        val best = r.coins.maxByOrNull { it.total }
        return "Итог $period: ${usd(r.total)}. Зафиксировано ${usd(r.realized)}, на бумаге ${usd(r.unrealized)}." +
            (best?.takeIf { it.total > 0 }?.let { " Лучше всех — ${Analytics.coinName(it.symbol)}." } ?: "")
    }
}
