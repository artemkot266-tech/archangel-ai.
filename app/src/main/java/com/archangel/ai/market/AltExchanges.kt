package com.archangel.ai.market

import com.archangel.ai.ai.FriendlyException
import com.archangel.ai.ai.Http
import com.archangel.ai.data.AppSettings
import com.archangel.ai.data.Exchange
import com.archangel.ai.data.ExchangeCreds
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Другие биржи: Bybit и OKX — баланс и рыночные ордера (есть демо-счета),
 * WhiteBIT — только баланс. Подписи запросов — по официальной документации бирж (API v5 / v4).
 */
object AltExchanges {

    suspend fun balances(s: AppSettings): List<Balance> {
        val c = s.altCreds
        return when (s.exchange) {
            Exchange.BYBIT -> Bybit.balances(c, s.altDemo)
            Exchange.OKX -> Okx.balances(c, s.altDemo)
            Exchange.WHITEBIT -> WhiteBit.balances(c)
            Exchange.BINANCE -> throw FriendlyException("Для Binance используется основной клиент.")
        }.filter { it.free + it.locked > 0 }
            .sortedByDescending { if (it.asset == "USDT") Double.MAX_VALUE else it.free + it.locked }
    }

    /** Рыночный ордер на сумму в USDT. Возвращает текст для пользователя. */
    suspend fun marketOrder(s: AppSettings, symbol: String, buy: Boolean, usdt: Double): String {
        val c = s.altCreds
        val id = when (s.exchange) {
            Exchange.BYBIT -> Bybit.marketOrder(c, s.altDemo, symbol, buy, usdt)
            Exchange.OKX -> Okx.marketOrder(c, s.altDemo, symbol, buy, usdt)
            else -> throw FriendlyException("На ${s.exchange.title} сделки из Архангела пока недоступны — только просмотр баланса.")
        }
        val coin = symbol.removeSuffix("USDT")
        return "${s.exchange.title}: ордер на ${if (buy) "покупку" else "продажу"} $coin на ${Indicators.formatPrice(usdt)} USDT отправлен (№ $id)."
    }

    internal fun hmacHex(algo: String, secret: String, data: String): String {
        val mac = Mac.getInstance(algo)
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), algo))
        return mac.doFinal(data.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    internal fun hmacBase64(secret: String, data: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return java.util.Base64.getEncoder().encodeToString(mac.doFinal(data.toByteArray(Charsets.UTF_8)))
    }

    private fun need(c: ExchangeCreds, title: String) {
        if (c.key.isBlank() || c.secret.isBlank()) throw FriendlyException("$title не подключён: добавьте ключи в настройках.")
    }

    // ───────── Bybit (API v5)
    object Bybit {
        private fun base(demo: Boolean) = if (demo) "https://api-demo.bybit.com" else "https://api.bybit.com"
        private const val RECV = "10000"

        /** Подпись Bybit: HMAC-SHA256(timestamp + apiKey + recvWindow + (query | body)). */
        fun sign(secret: String, ts: String, key: String, payload: String) = hmacHex("HmacSHA256", secret, ts + key + RECV + payload)

        private fun headers(c: ExchangeCreds, payload: String): Map<String, String> {
            val ts = System.currentTimeMillis().toString()
            return mapOf(
                "X-BAPI-API-KEY" to c.key, "X-BAPI-TIMESTAMP" to ts, "X-BAPI-RECV-WINDOW" to RECV,
                "X-BAPI-SIGN" to sign(c.secret, ts, c.key, payload), "Content-Type" to "application/json",
            )
        }

        private fun check(body: String): JSONObject {
            val o = runCatching { JSONObject(body) }.getOrNull() ?: throw FriendlyException("Bybit: непонятный ответ.")
            if (o.optInt("retCode", -1) != 0) throw FriendlyException(explain(o.optInt("retCode"), o.optString("retMsg")))
            return o
        }

        private fun explain(code: Int, msg: String) = when (code) {
            10003, 10004, 33004 -> "Bybit: ключ не подходит или неверная подпись. Проверьте ключ и режим демо/реальный."
            10002 -> "Bybit: часы телефона расходятся с биржей. Включите автоматическое время."
            10005 -> "Bybit: у ключа нет прав на эту операцию (нужна спот-торговля)."
            170131, 170136 -> "Bybit: недостаточно средств."
            170140 -> "Bybit: сумма ордера меньше минимальной."
            else -> "Bybit: ошибка $code ($msg)"
        }

        suspend fun balances(c: ExchangeCreds, demo: Boolean): List<Balance> {
            need(c, "Bybit")
            val q = "accountType=UNIFIED"
            val res = Http.request("${base(demo)}/v5/account/wallet-balance?$q", headers = headers(c, q))
            val o = check(res.body)
            val list = o.getJSONObject("result").getJSONArray("list")
            if (list.length() == 0) return emptyList()
            val coins = list.getJSONObject(0).getJSONArray("coin")
            return (0 until coins.length()).map { i ->
                val x = coins.getJSONObject(i)
                val total = x.optString("walletBalance").toDoubleOrNull() ?: 0.0
                val locked = x.optString("locked").toDoubleOrNull() ?: 0.0
                Balance(x.getString("coin"), (total - locked).coerceAtLeast(0.0), locked)
            }
        }

        suspend fun marketOrder(c: ExchangeCreds, demo: Boolean, symbol: String, buy: Boolean, usdt: Double): String {
            need(c, "Bybit")
            val body = JSONObject()
                .put("category", "spot").put("symbol", symbol).put("side", if (buy) "Buy" else "Sell")
                .put("orderType", "Market").put("qty", "%.2f".format(Locale.US, usdt)).put("marketUnit", "quoteCoin")
                .toString()
            val res = Http.request("${base(demo)}/v5/order/create", method = "POST", headers = headers(c, body), body = body)
            return check(res.body).getJSONObject("result").optString("orderId")
        }
    }

    // ───────── OKX (API v5)
    object Okx {
        private const val BASE = "https://www.okx.com"

        fun timestamp(now: Long = System.currentTimeMillis()): String =
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(now))

        /** Подпись OKX: Base64(HMAC-SHA256(timestamp + METHOD + path + body)). */
        fun sign(secret: String, ts: String, method: String, path: String, body: String) = hmacBase64(secret, ts + method + path + body)

        private fun headers(c: ExchangeCreds, demo: Boolean, method: String, path: String, body: String): Map<String, String> {
            val ts = timestamp()
            return buildMap {
                put("OK-ACCESS-KEY", c.key); put("OK-ACCESS-SIGN", sign(c.secret, ts, method, path, body))
                put("OK-ACCESS-TIMESTAMP", ts); put("OK-ACCESS-PASSPHRASE", c.passphrase)
                put("Content-Type", "application/json")
                if (demo) put("x-simulated-trading", "1")
            }
        }

        private fun check(body: String): JSONObject {
            val o = runCatching { JSONObject(body) }.getOrNull() ?: throw FriendlyException("OKX: непонятный ответ.")
            if (o.optString("code") != "0") {
                val code = o.optString("code")
                val detail = o.optJSONArray("data")?.optJSONObject(0)?.optString("sMsg").orEmpty().ifBlank { o.optString("msg") }
                throw FriendlyException(
                    when (code) {
                        "50111", "50113", "50114" -> "OKX: ключ, подпись или пароль ключа (passphrase) не подходят."
                        "50101" -> "OKX: ключ не от того режима — проверьте переключатель демо/реальный."
                        "50102" -> "OKX: часы телефона расходятся с биржей. Включите автоматическое время."
                        else -> "OKX: ошибка $code ($detail)"
                    }
                )
            }
            return o
        }

        suspend fun balances(c: ExchangeCreds, demo: Boolean): List<Balance> {
            need(c, "OKX")
            val path = "/api/v5/account/balance"
            val res = Http.request(BASE + path, headers = headers(c, demo, "GET", path, ""))
            val data = check(res.body).getJSONArray("data")
            if (data.length() == 0) return emptyList()
            val details = data.getJSONObject(0).getJSONArray("details")
            return (0 until details.length()).map { i ->
                val x = details.getJSONObject(i)
                val avail = x.optString("availBal").toDoubleOrNull() ?: x.optString("cashBal").toDoubleOrNull() ?: 0.0
                val frozen = x.optString("frozenBal").toDoubleOrNull() ?: 0.0
                Balance(x.getString("ccy"), avail, frozen)
            }
        }

        suspend fun marketOrder(c: ExchangeCreds, demo: Boolean, symbol: String, buy: Boolean, usdt: Double): String {
            need(c, "OKX")
            val path = "/api/v5/trade/order"
            val inst = symbol.removeSuffix("USDT") + "-USDT"
            val body = JSONObject()
                .put("instId", inst).put("tdMode", "cash").put("side", if (buy) "buy" else "sell")
                .put("ordType", "market").put("sz", "%.2f".format(Locale.US, usdt)).put("tgtCcy", "quote_ccy")
                .toString()
            val res = Http.request(BASE + path, method = "POST", headers = headers(c, demo, "POST", path, body), body = body)
            val d = check(res.body).getJSONArray("data").getJSONObject(0)
            if (d.optString("sCode", "0") != "0") throw FriendlyException("OKX: ${d.optString("sMsg")}")
            return d.optString("ordId")
        }
    }

    // ───────── WhiteBIT (API v4) — только баланс
    object WhiteBit {
        private const val BASE = "https://whitebit.com"

        /** Подпись WhiteBIT: payload = Base64(body), signature = hex(HMAC-SHA512(payload)). */
        fun signedHeaders(c: ExchangeCreds, body: String): Map<String, String> {
            val payload = java.util.Base64.getEncoder().encodeToString(body.toByteArray(Charsets.UTF_8))
            return mapOf(
                "Content-Type" to "application/json", "X-TXC-APIKEY" to c.key,
                "X-TXC-PAYLOAD" to payload, "X-TXC-SIGNATURE" to hmacHex("HmacSHA512", c.secret, payload),
            )
        }

        suspend fun balances(c: ExchangeCreds): List<Balance> {
            need(c, "WhiteBIT")
            val path = "/api/v4/trade-account/balance"
            val body = JSONObject().put("request", path).put("nonce", System.currentTimeMillis()).toString()
            val res = Http.request(BASE + path, method = "POST", headers = signedHeaders(c, body), body = body)
            val o = runCatching { JSONObject(res.body) }.getOrNull() ?: throw FriendlyException("WhiteBIT: непонятный ответ.")
            if (!res.ok) throw FriendlyException("WhiteBIT: ${o.optString("message", "ошибка ${res.code}")}. Проверьте ключ.")
            return o.keys().asSequence().map { k ->
                val x = o.getJSONObject(k)
                Balance(k, x.optString("available").toDoubleOrNull() ?: 0.0, x.optString("freeze").toDoubleOrNull() ?: 0.0)
            }.toList()
        }
    }
}
