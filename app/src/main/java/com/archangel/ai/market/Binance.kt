package com.archangel.ai.market

import com.archangel.ai.ai.FriendlyException
import com.archangel.ai.ai.Http
import com.archangel.ai.ai.HttpResult
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

data class Ticker(
    val symbol: String,
    val last: Double,
    val changePct: Double,
    val high: Double,
    val low: Double,
    val quoteVolume: Double,
)

data class Balance(val asset: String, val free: Double, val locked: Double)

data class OrderResult(
    val orderId: Long,
    val status: String,
    val executedQty: Double,
    val quoteQty: Double,
) {
    val avgPrice get() = if (executedQty > 0) quoteQty / executedQty else 0.0
}

/**
 * Клиент Binance Spot.
 * Котировки и графики всегда берутся с настоящей биржи (публичные данные, ключ не нужен).
 * Сделки и баланс — с тестовой (testnet) или реальной биржи, в зависимости от настройки.
 */
class BinanceClient {
    private val publicBase = "https://api.binance.com"
    private fun tradeBase(testnet: Boolean) =
        if (testnet) "https://testnet.binance.vision" else "https://api.binance.com"

    /** Разница часов телефона и биржи — иначе подписанные запросы отклоняются (-1021). */
    private var timeOffset: Long? = null

    suspend fun klines(symbol: String, interval: String, limit: Int = 200): List<Candle> {
        val res = Http.request("$publicBase/api/v3/klines?symbol=$symbol&interval=$interval&limit=$limit")
        if (!res.ok) throw FriendlyException(explain(res))
        val arr = JSONArray(res.body)
        return (0 until arr.length()).map { i ->
            val k = arr.getJSONArray(i)
            Candle(
                openTime = k.getLong(0),
                open = k.getString(1).toDouble(),
                high = k.getString(2).toDouble(),
                low = k.getString(3).toDouble(),
                close = k.getString(4).toDouble(),
                volume = k.getString(5).toDouble(),
            )
        }
    }

    suspend fun ticker(symbol: String): Ticker {
        val res = Http.request("$publicBase/api/v3/ticker/24hr?symbol=$symbol")
        if (!res.ok) throw FriendlyException(explain(res))
        val o = JSONObject(res.body)
        return Ticker(
            symbol = symbol,
            last = o.getString("lastPrice").toDouble(),
            changePct = o.getString("priceChangePercent").toDouble(),
            high = o.getString("highPrice").toDouble(),
            low = o.getString("lowPrice").toDouble(),
            quoteVolume = o.getString("quoteVolume").toDouble(),
        )
    }

    suspend fun balances(key: String, secret: String, testnet: Boolean): List<Balance> {
        val res = signed("GET", "/api/v3/account", mapOf("omitZeroBalances" to "true"), key, secret, testnet)
        val arr = JSONObject(res.body).optJSONArray("balances") ?: JSONArray()
        return (0 until arr.length()).map { i ->
            val b = arr.getJSONObject(i)
            Balance(b.getString("asset"), b.getString("free").toDouble(), b.getString("locked").toDouble())
        }.filter { it.free + it.locked > 0 }
            .sortedByDescending { if (it.asset == "USDT") Double.MAX_VALUE else it.free + it.locked }
    }

    /** Рыночный ордер на сумму в USDT (quoteOrderQty) — не нужно считать шаг лота. */
    suspend fun marketOrder(
        symbol: String, buy: Boolean, usdt: Double, key: String, secret: String, testnet: Boolean,
    ): OrderResult {
        val params = mapOf(
            "symbol" to symbol,
            "side" to if (buy) "BUY" else "SELL",
            "type" to "MARKET",
            "quoteOrderQty" to "%.2f".format(java.util.Locale.US, usdt),
            "newOrderRespType" to "RESULT",
        )
        val res = signed("POST", "/api/v3/order", params, key, secret, testnet)
        val o = JSONObject(res.body)
        return OrderResult(
            orderId = o.optLong("orderId"),
            status = o.optString("status"),
            executedQty = o.optString("executedQty", "0").toDoubleOrNull() ?: 0.0,
            quoteQty = o.optString("cummulativeQuoteQty", "0").toDoubleOrNull() ?: 0.0,
        )
    }

    private suspend fun serverOffset(testnet: Boolean): Long {
        timeOffset?.let { return it }
        val res = Http.request("${tradeBase(testnet)}/api/v3/time")
        val offset = if (res.ok) JSONObject(res.body).getLong("serverTime") - System.currentTimeMillis() else 0L
        timeOffset = offset
        return offset
    }

    private suspend fun signed(
        method: String, path: String, params: Map<String, String>,
        key: String, secret: String, testnet: Boolean,
    ): HttpResult {
        if (key.isBlank() || secret.isBlank()) {
            throw FriendlyException("Биржа не подключена: добавьте API-ключ Binance в настройках.")
        }
        val all = params + mapOf(
            "timestamp" to (System.currentTimeMillis() + serverOffset(testnet)).toString(),
            "recvWindow" to "10000",
        )
        val query = all.entries.joinToString("&") { "${it.key}=${URLEncoder.encode(it.value, "UTF-8")}" }
        val signature = hmacSha256(secret, query)
        val url = "${tradeBase(testnet)}$path?$query&signature=$signature"
        val res = Http.request(url, method = method, headers = mapOf("X-MBX-APIKEY" to key))
        if (!res.ok) {
            if (res.body.contains("-1021")) timeOffset = null
            throw FriendlyException(explain(res))
        }
        return res
    }

    private fun explain(res: HttpResult): String {
        val o = runCatching { JSONObject(res.body) }.getOrNull()
        val code = o?.optInt("code") ?: 0
        val msg = o?.optString("msg").orEmpty()
        val human = when (code) {
            -2014, -2015 -> "Ключ биржи не подходит, нет прав на торговлю или IP не в белом списке."
            -1021 -> "Часы телефона расходятся с биржей. Повторите попытку."
            -1013, -1111 -> "Сумма слишком мала или не соответствует правилам пары (обычно минимум ~5 USDT)."
            -2010 -> "Биржа отклонила ордер: недостаточно средств или ограничение."
            -1121 -> "Такой торговой пары нет."
            else -> when (res.code) {
                451 -> "Binance недоступен из этого региона."
                429, 418 -> "Слишком много запросов к бирже. Подождите минуту."
                in 500..599 -> "Биржа сейчас недоступна."
                else -> "Ошибка биржи ${res.code}."
            }
        }
        return if (msg.isNotBlank()) "$human\n($msg)" else human
    }

    companion object {
        fun hmacSha256(secret: String, data: String): String {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
            return mac.doFinal(data.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        }

        val INTERVALS = listOf("15m" to "15м", "1h" to "1ч", "4h" to "4ч", "1d" to "1д", "1w" to "1н")
        val FAVORITES = listOf("BTCUSDT", "ETHUSDT", "SOLUSDT", "BNBUSDT", "XRPUSDT", "TONUSDT", "DOGEUSDT")
    }
}
