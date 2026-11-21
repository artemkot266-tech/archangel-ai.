package com.archangel.ai

import com.archangel.ai.market.BinanceClient
import com.archangel.ai.market.Indicators
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IndicatorsTest {
    @Test fun sma() {
        val r = Indicators.sma(listOf(1.0, 2.0, 3.0, 4.0, 5.0), 3)
        assertNull(r[1])
        assertEquals(2.0, r[2]!!, 1e-9)
        assertEquals(4.0, r[4]!!, 1e-9)
    }

    @Test fun emaStartsWithSma() {
        val r = Indicators.ema(listOf(2.0, 4.0, 6.0, 8.0), 3)
        assertEquals(4.0, r[2]!!, 1e-9)
        assertEquals(6.0, r[3]!!, 1e-9) // 8*0.5 + 4*0.5
    }

    @Test fun rsiAllUpIs100() {
        val r = Indicators.rsi((1..30).map { it.toDouble() })
        assertEquals(100.0, r.last()!!, 1e-9)
    }

    @Test fun rsiInRange() {
        val prices = (0 until 100).map { 100 + 10 * kotlin.math.sin(it / 5.0) }
        val r = Indicators.rsi(prices).filterNotNull()
        assertTrue(r.all { it in 0.0..100.0 })
    }

    @Test fun macdHistogramIsLineMinusSignal() {
        val prices = (0 until 120).map { 50 + it * 0.3 + 3 * kotlin.math.sin(it / 4.0) }
        val m = Indicators.macd(prices)
        val i = prices.lastIndex
        assertEquals(m.macd[i]!! - m.signal[i]!!, m.hist[i]!!, 1e-9)
    }

    @Test fun priceFormat() {
        assertEquals("97 250.50", Indicators.formatPrice(97250.5))
        assertEquals("1.234", Indicators.formatPrice(1.2344))
    }

    // Пример подписи из документации Binance
    @Test fun hmacMatchesBinanceDocs() {
        val secret = "NhqPtmdSJYdKjVHjA7PZj4Mge3R5YNiP1e3UZjInClVN65XAbvqqM6A7H5fATj0j"
        val query = "symbol=LTCBTC&side=BUY&type=LIMIT&timeInForce=GTC&quantity=1&price=0.1&recvWindow=5000&timestamp=1499827319559"
        assertEquals(
            "c8db56825ae71d6d79447849e617115f4a920fa2acdcab2b053c4b2838bd6b71",
            BinanceClient.hmacSha256(secret, query),
        )
    }
}
