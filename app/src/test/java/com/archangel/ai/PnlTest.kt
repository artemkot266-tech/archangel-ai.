package com.archangel.ai

import com.archangel.ai.market.Holding
import com.archangel.ai.market.Pnl
import com.archangel.ai.market.Portfolio
import com.archangel.ai.market.Trade
import com.archangel.ai.phone.CommandParser
import com.archangel.ai.phone.PhoneAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PnlTest {
    private fun buy(t: Long, price: Double, qty: Double) = Trade("BTCUSDT", t, true, price, qty, price * qty, 0.0, "BNB")
    private fun sell(t: Long, price: Double, qty: Double) = Trade("BTCUSDT", t, false, price, qty, price * qty, 0.0, "BNB")

    @Test fun averageCostAndRealized() {
        // Купили 1 по 100 и 1 по 200 → средняя 150. Продали 1 по 180 → +30. Остался 1, цена 160 → +10 на бумаге.
        val (c, _) = Pnl.coin("BTCUSDT", listOf(buy(1, 100.0, 1.0), buy(2, 200.0, 1.0), sell(3, 180.0, 1.0)), 160.0)
        assertEquals(1.0, c.held, 1e-9)
        assertEquals(150.0, c.avgCost, 1e-9)
        assertEquals(30.0, c.realized, 1e-9)
        assertEquals(10.0, c.unrealized, 1e-9)
        assertEquals(1, c.wins)
    }

    @Test fun commissionInCoinReducesQty() {
        val t = Trade("BTCUSDT", 1, true, 100.0, 1.0, 100.0, 0.001, "BTC")
        val (c, _) = Pnl.coin("BTCUSDT", listOf(t), 100.0)
        assertEquals(0.999, c.held, 1e-9)
        assertEquals(-0.1, c.unrealized, 1e-6)
    }

    @Test fun sellingUnknownCoinsNotProfit() {
        // Монеты пришли с другой биржи: продажа без покупок не считается прибылью
        val (c, _) = Pnl.coin("BTCUSDT", listOf(sell(1, 100.0, 1.0)), 100.0)
        assertEquals(0.0, c.realized, 1e-9)
    }

    @Test fun periodFilter() {
        val trades = listOf(buy(1, 100.0, 2.0), sell(5, 110.0, 1.0), sell(20, 120.0, 1.0))
        val (c, _) = Pnl.coin("BTCUSDT", trades, 120.0, since = 10)
        assertEquals(20.0, c.realized, 1e-9) // только вторая продажа
    }

    @Test fun reportCurve() {
        val r = Pnl.report(mapOf("BTCUSDT" to listOf(buy(1, 100.0, 2.0), sell(2, 110.0, 1.0), sell(3, 90.0, 1.0))), mapOf("BTCUSDT" to 95.0))
        assertEquals(2, r.curve.size)
        assertEquals(10.0, r.curve[0].cumulative, 1e-9)
        assertEquals(0.0, r.curve[1].cumulative, 1e-9)
        assertEquals(1, r.wins); assertEquals(1, r.losses)
    }

    @Test fun concentration() {
        val p = Portfolio(100.0, 0.0, listOf(Holding("BTC", 1.0, 80.0, 0.0), Holding("USDT", 20.0, 20.0, 0.0)))
        assertNotNull(Pnl.concentrationHint(p))
        val q = Portfolio(100.0, 0.0, listOf(Holding("BTC", 1.0, 40.0, 0.0), Holding("ETH", 1.0, 30.0, 0.0), Holding("USDT", 30.0, 30.0, 0.0)))
        assertNull(Pnl.concentrationHint(q))
    }

    @Test fun voiceProfitWeek() = assertEquals(PhoneAction.Profit(7), CommandParser.parse("сколько я заработал за неделю"))
    @Test fun voiceProfitDefault() = assertEquals(PhoneAction.Profit(30), CommandParser.parse("мои итоги"))
    @Test fun voiceProfitAll() = assertEquals(PhoneAction.Profit(0), CommandParser.parse("какая у меня прибыль за всё время"))
}
