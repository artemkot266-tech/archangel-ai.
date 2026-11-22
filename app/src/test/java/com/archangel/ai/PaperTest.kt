package com.archangel.ai

import com.archangel.ai.market.PaperEngine
import com.archangel.ai.market.PaperState
import com.archangel.ai.market.TiltGuard
import com.archangel.ai.phone.CommandParser
import com.archangel.ai.phone.PhoneAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PaperTest {
    private val start = PaperState(1000.0, emptyList())

    @Test fun buyAndSellWithFees() {
        val b = PaperEngine.buy(start, "BTCUSDT", 100.0, 50_000.0, time = 1)
        assertEquals(899.9, b.state.cash, 1e-9) // 100 + 0.1 комиссия
        assertEquals(0.002, PaperEngine.held(b.state, "BTCUSDT"), 1e-12)
        val s = PaperEngine.sell(b.state, "BTCUSDT", 1_000_000.0, 55_000.0, time = 2) // продать всё
        assertEquals(0.0, PaperEngine.held(s.state, "BTCUSDT"), 1e-12)
        assertEquals(899.9 + 110 - 0.11, s.state.cash, 1e-9) // выручка 110 − 0.11 комиссия
        assertEquals(110 - 0.11 - 100.1, s.realized!!, 1e-9)
    }

    @Test fun notEnoughCash() = assertTrue(PaperEngine.buy(start, "BTCUSDT", 1000.0, 1.0).error.isNotEmpty())
    @Test fun sellNothing() = assertTrue(PaperEngine.sell(start, "ETHUSDT", 10.0, 1.0).error.isNotEmpty())

    @Test fun equity() {
        val b = PaperEngine.buy(start, "ETHUSDT", 200.0, 2000.0, time = 1)
        assertEquals(1049.8, PaperEngine.equity(b.state, mapOf("ETHUSDT" to 2500.0)), 1e-9)
    }

    @Test fun tiltAfterThreeLosses() {
        assertEquals(0L, TiltGuard.pauseUntil(listOf(-1.0, -2.0), 100))
        assertEquals(0L, TiltGuard.pauseUntil(listOf(-1.0, 5.0, -2.0), 100))
        assertEquals(100 + TiltGuard.PAUSE_MS, TiltGuard.pauseUntil(listOf(5.0, -1.0, -2.0, -0.5), 100))
    }

    @Test fun voicePaper() = assertEquals(PhoneAction.PaperOrder(true, "BTCUSDT", 100.0), CommandParser.parse("купи в песочнице биткоин на 100"))
    @Test fun voicePaperSuffix() = assertEquals(PhoneAction.PaperOrder(false, "ETHUSDT", 50.0), CommandParser.parse("продай эфир на 50 в песочнице"))
    @Test fun voiceRealStillReal() = assertEquals(PhoneAction.Order(true, "BTCUSDT", 20.0), CommandParser.parse("купи биткоин на 20"))
}
