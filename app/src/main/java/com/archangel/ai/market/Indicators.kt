package com.archangel.ai.market

import java.util.Locale
import kotlin.math.abs

data class Candle(
    val openTime: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double,
) {
    val up get() = close >= open
}

/** Технические индикаторы. Чистый Kotlin — покрыт юнит-тестами. */
object Indicators {

    /** Простая скользящая средняя; первые n-1 значений — null. */
    fun sma(values: List<Double>, n: Int): List<Double?> {
        if (n <= 0) return values.map { null }
        val out = ArrayList<Double?>(values.size)
        var sum = 0.0
        for (i in values.indices) {
            sum += values[i]
            if (i >= n) sum -= values[i - n]
            out.add(if (i >= n - 1) sum / n else null)
        }
        return out
    }

    /** Экспоненциальная скользящая средняя, стартует с SMA первых n значений. */
    fun ema(values: List<Double>, n: Int): List<Double?> {
        val out = MutableList<Double?>(values.size) { null }
        if (n <= 0 || values.size < n) return out
        val k = 2.0 / (n + 1)
        var prev = values.take(n).average()
        out[n - 1] = prev
        for (i in n until values.size) {
            prev = values[i] * k + prev * (1 - k)
            out[i] = prev
        }
        return out
    }

    /** RSI по Уайлдеру (по умолчанию 14). */
    fun rsi(values: List<Double>, n: Int = 14): List<Double?> {
        val out = MutableList<Double?>(values.size) { null }
        if (values.size <= n) return out
        var gain = 0.0
        var loss = 0.0
        for (i in 1..n) {
            val d = values[i] - values[i - 1]
            if (d >= 0) gain += d else loss -= d
        }
        gain /= n; loss /= n
        out[n] = toRsi(gain, loss)
        for (i in n + 1 until values.size) {
            val d = values[i] - values[i - 1]
            gain = (gain * (n - 1) + (if (d > 0) d else 0.0)) / n
            loss = (loss * (n - 1) + (if (d < 0) -d else 0.0)) / n
            out[i] = toRsi(gain, loss)
        }
        return out
    }

    private fun toRsi(gain: Double, loss: Double) =
        if (loss == 0.0) (if (gain == 0.0) 50.0 else 100.0) else 100.0 - 100.0 / (1 + gain / loss)

    data class Macd(val macd: List<Double?>, val signal: List<Double?>, val hist: List<Double?>)

    fun macd(values: List<Double>, fast: Int = 12, slow: Int = 26, signalN: Int = 9): Macd {
        val f = ema(values, fast)
        val s = ema(values, slow)
        val line = values.indices.map { i -> val a = f[i]; val b = s[i]; if (a != null && b != null) a - b else null }
        val start = line.indexOfFirst { it != null }
        val signal = MutableList<Double?>(values.size) { null }
        if (start >= 0) {
            val sig = ema(line.drop(start).map { it!! }, signalN)
            sig.forEachIndexed { j, v -> signal[start + j] = v }
        }
        val hist = values.indices.map { i -> val m = line[i]; val sg = signal[i]; if (m != null && sg != null) m - sg else null }
        return Macd(line, signal, hist)
    }

    /** Простые уровни: минимумы/максимумы окна, грубо — поддержка и сопротивление. */
    fun supportResistance(candles: List<Candle>, window: Int = 50): Pair<Double, Double>? {
        if (candles.isEmpty()) return null
        val w = candles.takeLast(window)
        return w.minOf { it.low } to w.maxOf { it.high }
    }

    /** Форматирует цену: больше знаков для дешёвых монет. */
    fun formatPrice(p: Double): String = when {
        abs(p) >= 1000 -> String.format(Locale.US, "%,.2f", p).replace(',', '\u2009')
        abs(p) >= 1 -> String.format(Locale.US, "%.3f", p)
        abs(p) >= 0.01 -> String.format(Locale.US, "%.5f", p)
        else -> String.format(Locale.US, "%.8f", p)
    }
}
