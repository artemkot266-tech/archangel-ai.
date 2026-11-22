package com.archangel.ai.market

import java.math.BigDecimal
import java.math.RoundingMode

/** Правила пары на бирже: шаг цены, шаг количества, минимальная сумма ордера. */
data class SymbolRules(val tickSize: String, val stepSize: String, val minQty: Double, val minNotional: Double)

data class OpenOrder(
    val orderId: Long,
    val orderListId: Long,
    val symbol: String,
    val side: String,
    val type: String,
    val price: Double,
    val stopPrice: Double,
    val origQty: Double,
    val executedQty: Double,
    val time: Long,
) {
    val buy get() = side == "BUY"
    /** Человеческое название типа ордера. */
    val kind get() = when (type) {
        "LIMIT" -> "лимит"
        "LIMIT_MAKER" -> "тейк-профит"
        "STOP_LOSS_LIMIT", "STOP_LOSS" -> "стоп-лосс"
        "TAKE_PROFIT_LIMIT", "TAKE_PROFIT" -> "тейк-профит"
        "MARKET" -> "рыночный"
        else -> type.lowercase()
    }
}

/** Расчёты для ордеров. Чистые функции — покрыты тестами. */
object OrderMath {
    /** Округление вниз до шага биржи (0.00001000 и т.п.). */
    fun floorStep(value: Double, step: String): BigDecimal {
        val st = BigDecimal(step).stripTrailingZeros()
        if (st.signum() <= 0) return BigDecimal.valueOf(value)
        val n = BigDecimal.valueOf(value).divide(st, 0, RoundingMode.FLOOR)
        return n.multiply(st).setScale(maxOf(st.scale(), 0), RoundingMode.FLOOR)
    }

    fun plain(v: BigDecimal): String = v.stripTrailingZeros().toPlainString()

    /** Цены защиты от цены входа: стоп ниже на stopPct, цель выше на takePct (доли: 0.05 = 5%). */
    fun protection(entry: Double, stopPct: Double, takePct: Double): Pair<Double, Double> =
        entry * (1 - stopPct) to entry * (1 + takePct)

    /** Сколько потеряете в долларах, если сработает стоп. */
    fun riskUsd(positionUsd: Double, stopPct: Double) = positionUsd * stopPct

    /** Размер позиции под допустимый риск: депозит × риск% ÷ расстояние до стопа. */
    fun positionSize(deposit: Double, riskPct: Double, stopPct: Double): Double =
        if (stopPct <= 0) 0.0 else deposit * riskPct / stopPct

    /** Соотношение прибыль/риск. */
    fun rewardRisk(stopPct: Double, takePct: Double) = if (stopPct <= 0) 0.0 else takePct / stopPct

    /** Проверка перед ордером: пустая строка — всё хорошо. */
    fun check(rules: SymbolRules, qty: BigDecimal, price: Double): String {
        val q = qty.toDouble()
        return when {
            q <= 0 || q < rules.minQty -> "Слишком маленькое количество для этой пары."
            q * price < rules.minNotional -> "Минимальная сумма ордера — около ${"%.2f".format(java.util.Locale.US, rules.minNotional)} USDT."
            else -> ""
        }
    }
}
