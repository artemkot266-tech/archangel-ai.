package com.archangel.ai.market

import java.util.Locale
import kotlin.math.abs

/** Уровень на графике: поддержка (ниже цены) или сопротивление (выше), сколько раз цена от него отталкивалась. */
data class Level(val price: Double, val support: Boolean, val touches: Int) {
    val reason get() = "касаний: $touches"
}

enum class Trend(val label: String) { UP("рост"), DOWN("падение"), FLAT("боковик") }

/** Сигнал для уведомления. key — чтобы не повторять одно и то же. */
data class Signal(val symbol: String, val key: String, val title: String, val text: String)

data class FearGreed(val value: Int, val label: String)

data class NewsItem(val title: String, val link: String)

/** Чистые функции анализа рынка — покрыты юнит-тестами. */
object Analytics {

    /**
     * Уровни по разворотным точкам (локальные минимумы/максимумы, k свечей с каждой стороны).
     * Близкие точки (в пределах tolerance) склеиваются, чем больше касаний — тем сильнее уровень.
     */
    fun levels(candles: List<Candle>, price: Double, k: Int = 3, tolerance: Double = 0.006, maxEach: Int = 2): List<Level> {
        if (candles.size < 2 * k + 1 || price <= 0) return emptyList()
        val points = ArrayList<Double>()
        for (i in k until candles.size - k) {
            val c = candles[i]
            val around = (i - k..i + k).filter { it != i }.map { candles[it] }
            if (around.all { c.high >= it.high }) points.add(c.high)
            if (around.all { c.low <= it.low }) points.add(c.low)
        }
        // Склейка близких точек в кластеры
        val clusters = ArrayList<MutableList<Double>>()
        for (p in points.sorted()) {
            val last = clusters.lastOrNull()
            if (last != null && abs(p - last.average()) / price <= tolerance) last.add(p) else clusters.add(mutableListOf(p))
        }
        val all = clusters.map { it.average() to it.size }
        val supports = all.filter { it.first < price }.sortedWith(compareByDescending<Pair<Double, Int>> { it.second }.thenByDescending { it.first })
            .take(maxEach).map { Level(it.first, true, it.second) }
        val resistances = all.filter { it.first > price }.sortedWith(compareByDescending<Pair<Double, Int>> { it.second }.thenBy { it.first })
            .take(maxEach).map { Level(it.first, false, it.second) }
        return (supports + resistances).sortedByDescending { it.price }
    }

    /** Тренд: цена и EMA20 выше EMA50, EMA20 растёт — рост; зеркально — падение; иначе боковик. */
    fun trend(candles: List<Candle>): Trend {
        if (candles.size < 60) return Trend.FLAT
        val closes = candles.map { it.close }
        val e20 = Indicators.ema(closes, 20)
        val e50 = Indicators.ema(closes, 50)
        val last = closes.last()
        val a = e20.last() ?: return Trend.FLAT
        val b = e50.last() ?: return Trend.FLAT
        val a5 = e20[e20.size - 6] ?: return Trend.FLAT
        val slope = (a - a5) / a5
        return when {
            last > a && a > b && slope > 0.001 -> Trend.UP
            last < a && a < b && slope < -0.001 -> Trend.DOWN
            else -> Trend.FLAT
        }
    }

    /**
     * События на закрытых свечах (последняя, ещё не закрытая, отбрасывается вызывающим):
     * RSI вышел из зоны 30/70, пробой максимума/минимума 48 свечей, всплеск объёма, разворот MACD.
     */
    fun detectSignals(symbol: String, closed: List<Candle>): List<Signal> {
        if (closed.size < 60) return emptyList()
        val coin = symbol.removeSuffix("USDT")
        val f = Indicators::formatPrice
        val out = ArrayList<Signal>()
        val closes = closed.map { it.close }
        val last = closed.last()

        val rsi = Indicators.rsi(closes)
        val r1 = rsi.last(); val r0 = rsi[rsi.size - 2]
        if (r1 != null && r0 != null) {
            if (r0 >= 30 && r1 < 30) out += Signal(symbol, "rsi_low", "$coin перепродан", "RSI упал до ${"%.0f".format(Locale.US, r1)}. Часто после этого бывает отскок — но не всегда.")
            if (r0 <= 70 && r1 > 70) out += Signal(symbol, "rsi_high", "$coin перекуплен", "RSI вырос до ${"%.0f".format(Locale.US, r1)}. Возможна коррекция.")
        }

        val prev = closed.dropLast(1).takeLast(48)
        val hi = prev.maxOf { it.high }
        val lo = prev.minOf { it.low }
        if (last.close > hi) out += Signal(symbol, "break_up", "$coin пробил вверх", "Закрытие ${f(last.close)} выше максимума ${f(hi)} за 48 свечей.")
        if (last.close < lo) out += Signal(symbol, "break_down", "$coin пробил вниз", "Закрытие ${f(last.close)} ниже минимума ${f(lo)} за 48 свечей.")

        val avgVol = closed.dropLast(1).takeLast(20).map { it.volume }.average()
        if (avgVol > 0 && last.volume > avgVol * 3) {
            val ch = (last.close - last.open) / last.open * 100
            out += Signal(symbol, "volume", "$coin: всплеск объёма", "Объём в ${"%.1f".format(Locale.US, last.volume / avgVol)} раза выше обычного, свеча ${"%+.2f".format(Locale.US, ch)}%.")
        }

        val hist = Indicators.macd(closes).hist
        val h1 = hist.last(); val h0 = hist[hist.size - 2]
        if (h1 != null && h0 != null) {
            if (h0 <= 0 && h1 > 0) out += Signal(symbol, "macd_up", "$coin: MACD развернулся вверх", "Линия MACD пересекла сигнальную снизу вверх.")
            if (h0 >= 0 && h1 < 0) out += Signal(symbol, "macd_down", "$coin: MACD развернулся вниз", "Линия MACD пересекла сигнальную сверху вниз.")
        }
        return out
    }

    /** Ответ api.alternative.me/fng/ → индекс и подпись по-русски. */
    fun parseFearGreed(json: String): FearGreed? = runCatching {
        val o = org.json.JSONObject(json).getJSONArray("data").getJSONObject(0)
        val v = o.getString("value").toInt()
        FearGreed(v, fearGreedLabel(v))
    }.getOrNull()

    fun fearGreedLabel(v: Int) = when {
        v <= 24 -> "сильный страх"
        v <= 45 -> "страх"
        v <= 55 -> "нейтрально"
        v <= 75 -> "жадность"
        else -> "сильная жадность"
    }

    /** Простой разбор RSS: заголовки и ссылки, CDATA поддерживается. */
    fun parseRss(xml: String, limit: Int = 30): List<NewsItem> {
        val items = Regex("<item[\\s>](.*?)</item>", RegexOption.DOT_MATCHES_ALL).findAll(xml).take(limit)
        return items.mapNotNull { m ->
            val body = m.groupValues[1]
            val title = tag(body, "title") ?: return@mapNotNull null
            NewsItem(title, tag(body, "link").orEmpty())
        }.toList()
    }

    private fun tag(body: String, name: String): String? {
        val raw = Regex("<$name[^>]*>(.*?)</$name>", RegexOption.DOT_MATCHES_ALL).find(body)?.groupValues?.get(1) ?: return null
        return raw.replace("<![CDATA[", "").replace("]]>", "")
            .replace("&amp;", "&").replace("&quot;", "\"").replace("&#039;", "'").replace("&lt;", "<").replace("&gt;", ">")
            .trim().takeIf { it.isNotEmpty() }
    }

    /** Слова, по которым новость относится к монете. */
    fun coinWords(symbol: String): List<String> = when (val c = symbol.removeSuffix("USDT").uppercase()) {
        "BTC" -> listOf("bitcoin", "биткоин", "биткойн", "btc")
        "ETH" -> listOf("ethereum", "эфир", "ethereum", "eth")
        "SOL" -> listOf("solana", "солан", "sol")
        "BNB" -> listOf("bnb", "binance")
        "XRP" -> listOf("xrp", "ripple")
        "TON" -> listOf("toncoin", "ton ", "telegram")
        "DOGE" -> listOf("doge", "dogecoin", "догикоин")
        else -> listOf(c.lowercase())
    }

    fun newsFor(symbol: String, all: List<NewsItem>, limit: Int = 5): List<NewsItem> {
        val words = coinWords(symbol)
        val hit = all.filter { n -> val t = n.title.lowercase() + " "; words.any { it in t } }
        return (hit.ifEmpty { all }).take(limit)
    }

    /** Короткая сводка для голоса: «Биткоин 97 тысяч, плюс 2 процента за сутки…». */
    fun spokenSummary(tickers: List<Ticker>, fng: FearGreed?, portfolio: Portfolio?): String = buildString {
        if (tickers.isEmpty()) { append("Не удалось получить котировки."); return@buildString }
        val parts = tickers.map { t ->
            "${coinName(t.symbol)} ${spokenPrice(t.last)}, ${spokenPct(t.changePct)}"
        }
        append("За сутки: ").append(parts.joinToString("; ")).append(". ")
        val best = tickers.maxByOrNull { it.changePct }
        val worst = tickers.minByOrNull { it.changePct }
        if (best != null && worst != null && best != worst) {
            append("Сильнее всех ${coinName(best.symbol)}, слабее всех ${coinName(worst.symbol)}. ")
        }
        fng?.let { append("Индекс страха и жадности ${it.value} — ${it.label}. ") }
        portfolio?.takeIf { it.total > 0 }?.let {
            append("Ваш портфель ${spokenPrice(it.total)} долларов, ${spokenPct(it.change24)}.")
        }
    }.trim()

    fun coinName(symbol: String) = when (val c = symbol.removeSuffix("USDT")) {
        "BTC" -> "биткоин"; "ETH" -> "эфир"; "SOL" -> "солана"; "BNB" -> "BNB"; "XRP" -> "рипл"
        "TON" -> "тон"; "DOGE" -> "доги"; else -> c
    }

    fun spokenPct(p: Double): String {
        val v = String.format(Locale.US, "%.1f", abs(p)).replace('.', ',')
        return when {
            abs(p) < 0.05 -> "без изменений"
            p > 0 -> "плюс $v%"
            else -> "минус $v%"
        }
    }

    fun spokenPrice(p: Double): String = when {
        p >= 10_000 -> "${String.format(Locale.US, "%.1f", p / 1000).replace('.', ',').removeSuffix(",0")} тыс."
        p >= 100 -> String.format(Locale.US, "%.0f", p)
        p >= 1 -> String.format(Locale.US, "%.2f", p).replace('.', ',')
        else -> String.format(Locale.US, "%.4f", p).replace('.', ',')
    }
}
