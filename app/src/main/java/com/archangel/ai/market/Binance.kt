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

data class Holding(val asset: String, val amount: Double, val value: Double, val change24: Double)

/** Портфель в USDT: стоимость, изменение за сутки (взвешенное), позиции по убыванию стоимости. */
data class Portfolio(val total: Double, val change24: Double, val items: List<Holding>)

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
 * Сделки и баланс — с демо-счёта (Binance Demo Mode) или реального счёта, в зависимости от настройки.
 */
class BinanceClient {
    private val publicBase = "https://api.binance.com"
    private fun tradeBase(testnet: Boolean) =
        if (testnet) "https://demo-api.binance.com" else "https://api.binance.com"

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

    /** Котировки сразу по нескольким парам одним запросом. */
    suspend fun tickers(symbols: List<String>): List<Ticker> {
        if (symbols.isEmpty()) return emptyList()
        val list = symbols.joinToString(",", "[", "]") { "\"$it\"" }
        val res = Http.request("$publicBase/api/v3/ticker/24hr?symbols=" + URLEncoder.encode(list, "UTF-8"))
        if (!res.ok) throw FriendlyException(explain(res))
        val arr = JSONArray(res.body)
        val bySymbol = (0 until arr.length()).map { arr.getJSONObject(it) }.associateBy { it.getString("symbol") }
        return symbols.mapNotNull { s ->
            bySymbol[s]?.let { o ->
                Ticker(
                    symbol = s,
                    last = o.getString("lastPrice").toDouble(),
                    changePct = o.getString("priceChangePercent").toDouble(),
                    high = o.getString("highPrice").toDouble(),
                    low = o.getString("lowPrice").toDouble(),
                    quoteVolume = o.getString("quoteVolume").toDouble(),
                )
            }
        }
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

    suspend fun portfolio(key: String, secret: String, testnet: Boolean): Portfolio = valuate(balances(key, secret, testnet))

    /** Оценка любых балансов (в т.ч. с других бирж) в USDT по ценам Binance. */
    suspend fun valuate(balances: List<Balance>): Portfolio {
        val stable = setOf("USDT", "USDC", "FDUSD", "BUSD", "TUSD", "DAI")
        val items = balances.mapNotNull { b ->
            val amount = b.free + b.locked
            if (b.asset in stable) Holding(b.asset, amount, amount, 0.0)
            else runCatching { ticker(b.asset + "USDT") }.getOrNull()?.let { t ->
                Holding(b.asset, amount, amount * t.last, t.changePct)
            }
        }.filter { it.value >= 0.01 }.sortedByDescending { it.value }
        val total = items.sumOf { it.value }
        val change = if (total > 0) items.sumOf { it.value * it.change24 } / total else 0.0
        return Portfolio(total, change, items)
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

    // ───────────── Лимитные ордера, защита, открытые ордера ─────────────

    private val rulesCache = HashMap<String, SymbolRules>()

    suspend fun rules(symbol: String): SymbolRules {
        rulesCache[symbol]?.let { return it }
        val res = Http.request("$publicBase/api/v3/exchangeInfo?symbol=$symbol")
        if (!res.ok) throw FriendlyException(explain(res))
        val sym = JSONObject(res.body).getJSONArray("symbols").getJSONObject(0)
        val filters = sym.getJSONArray("filters")
        var tick = "0.01"; var step = "0.00001"; var minQty = 0.0; var minNotional = 5.0
        for (i in 0 until filters.length()) {
            val f = filters.getJSONObject(i)
            when (f.getString("filterType")) {
                "PRICE_FILTER" -> tick = f.getString("tickSize")
                "LOT_SIZE" -> { step = f.getString("stepSize"); minQty = f.getString("minQty").toDouble() }
                "NOTIONAL" -> minNotional = f.optString("minNotional", "5").toDoubleOrNull() ?: 5.0
                "MIN_NOTIONAL" -> minNotional = f.optString("minNotional", "5").toDoubleOrNull() ?: 5.0
            }
        }
        return SymbolRules(tick, step, minQty, minNotional).also { rulesCache[symbol] = it }
    }

    /** Лимитный ордер на сумму в USDT: количество считается и округляется по правилам пары. */
    suspend fun limitOrder(
        symbol: String, buy: Boolean, price: Double, usdt: Double, key: String, secret: String, testnet: Boolean,
    ): OrderResult {
        val r = rules(symbol)
        val p = OrderMath.floorStep(price, r.tickSize)
        val qty = OrderMath.floorStep(usdt / p.toDouble(), r.stepSize)
        OrderMath.check(r, qty, p.toDouble()).takeIf { it.isNotEmpty() }?.let { throw FriendlyException(it) }
        val res = signed(
            "POST", "/api/v3/order",
            mapOf(
                "symbol" to symbol, "side" to if (buy) "BUY" else "SELL", "type" to "LIMIT",
                "timeInForce" to "GTC", "price" to OrderMath.plain(p), "quantity" to OrderMath.plain(qty),
                "newOrderRespType" to "RESULT",
            ),
            key, secret, testnet,
        )
        val o = JSONObject(res.body)
        return OrderResult(
            orderId = o.optLong("orderId"),
            status = o.optString("status"),
            executedQty = o.optString("origQty", "0").toDoubleOrNull() ?: 0.0,
            quoteQty = (o.optString("origQty", "0").toDoubleOrNull() ?: 0.0) * p.toDouble(),
        )
    }

    /**
     * Защита позиции одним OCO-ордером на продажу: тейк-профит выше цены и стоп-лосс ниже.
     * Сработал один — второй отменяется биржей.
     */
    suspend fun protect(
        symbol: String, quantity: Double, takeProfit: Double, stopLoss: Double,
        key: String, secret: String, testnet: Boolean,
    ): Long {
        val r = rules(symbol)
        val qty = OrderMath.floorStep(quantity, r.stepSize)
        val tp = OrderMath.floorStep(takeProfit, r.tickSize)
        val sl = OrderMath.floorStep(stopLoss, r.tickSize)
        // Лимит стопа чуть ниже триггера, чтобы при резком падении ордер исполнился
        val slLimit = OrderMath.floorStep(stopLoss * 0.995, r.tickSize)
        OrderMath.check(r, qty, sl.toDouble()).takeIf { it.isNotEmpty() }?.let { throw FriendlyException(it) }
        val res = signed(
            "POST", "/api/v3/orderList/oco",
            mapOf(
                "symbol" to symbol, "side" to "SELL", "quantity" to OrderMath.plain(qty),
                "aboveType" to "LIMIT_MAKER", "abovePrice" to OrderMath.plain(tp),
                "belowType" to "STOP_LOSS_LIMIT", "belowStopPrice" to OrderMath.plain(sl),
                "belowPrice" to OrderMath.plain(slLimit), "belowTimeInForce" to "GTC",
            ),
            key, secret, testnet,
        )
        return JSONObject(res.body).optLong("orderListId")
    }

    suspend fun openOrders(key: String, secret: String, testnet: Boolean, symbol: String? = null): List<OpenOrder> {
        val res = signed("GET", "/api/v3/openOrders", symbol?.let { mapOf("symbol" to it) } ?: emptyMap(), key, secret, testnet)
        val arr = JSONArray(res.body)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            OpenOrder(
                orderId = o.getLong("orderId"),
                orderListId = o.optLong("orderListId", -1),
                symbol = o.getString("symbol"),
                side = o.getString("side"),
                type = o.getString("type"),
                price = o.optString("price", "0").toDoubleOrNull() ?: 0.0,
                stopPrice = o.optString("stopPrice", "0").toDoubleOrNull() ?: 0.0,
                origQty = o.optString("origQty", "0").toDoubleOrNull() ?: 0.0,
                executedQty = o.optString("executedQty", "0").toDoubleOrNull() ?: 0.0,
                time = o.optLong("time"),
            )
        }.sortedByDescending { it.time }
    }

    /** Отмена ордера. Отмена одной части OCO снимает обе. */
    suspend fun cancelOrder(symbol: String, orderId: Long, key: String, secret: String, testnet: Boolean) {
        signed("DELETE", "/api/v3/order", mapOf("symbol" to symbol, "orderId" to orderId.toString()), key, secret, testnet)
    }

    suspend fun cancelAll(symbol: String, key: String, secret: String, testnet: Boolean) {
        signed("DELETE", "/api/v3/openOrders", mapOf("symbol" to symbol), key, secret, testnet)
    }

    /** История сделок по паре (до 1000 последних). */
    suspend fun myTrades(symbol: String, key: String, secret: String, testnet: Boolean): List<Trade> {
        val res = signed("GET", "/api/v3/myTrades", mapOf("symbol" to symbol, "limit" to "1000"), key, secret, testnet)
        val arr = JSONArray(res.body)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Trade(
                symbol = symbol,
                time = o.getLong("time"),
                buy = o.getBoolean("isBuyer"),
                price = o.getString("price").toDouble(),
                qty = o.getString("qty").toDouble(),
                quote = o.getString("quoteQty").toDouble(),
                commission = o.optString("commission", "0").toDoubleOrNull() ?: 0.0,
                commissionAsset = o.optString("commissionAsset"),
            )
        }
    }

    /** Свободный остаток монеты (например, после покупки — сколько реально можно продать). */
    suspend fun free(asset: String, key: String, secret: String, testnet: Boolean): Double =
        balances(key, secret, testnet).firstOrNull { it.asset == asset }?.free ?: 0.0

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
            -1013, -1111 -> "Ордер не прошёл правила пары: сумма слишком мала (обычно минимум ~5 USDT) или цена слишком далеко от рынка."
            -2011 -> "Ордер не найден — возможно, он уже исполнен или отменён."
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
