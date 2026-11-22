package com.archangel.ai

import com.archangel.ai.market.DcaPlan
import com.archangel.ai.market.OrderMath
import com.archangel.ai.market.SymbolRules
import com.archangel.ai.phone.CommandParser
import com.archangel.ai.phone.PhoneAction
import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TradingTest {
    private fun p(t: String) = CommandParser.parse(t)

    @Test fun marketOrderStillWorks() = assertEquals(PhoneAction.Order(true, "BTCUSDT", 20.0), p("купи биткоин на 20 долларов"))

    @Test fun limitOrderThousands() = assertEquals(
        PhoneAction.Order(true, "BTCUSDT", 50.0, limitPrice = 90000.0),
        p("Архангел, купи биткоин на 50 долларов, если упадёт до 90 тысяч"),
    )

    @Test fun limitOrderByPrice() = assertEquals(
        PhoneAction.Order(true, "ETHUSDT", 20.0, limitPrice = 3000.0),
        p("купи эфир на 20 по цене 3000"),
    )

    @Test fun sellLimit() = assertEquals(
        PhoneAction.Order(false, "SOLUSDT", 30.0, limitPrice = 250.0),
        p("продай солану на 30 когда вырастет до 250"),
    )

    @Test fun orderWithStops() = assertEquals(
        PhoneAction.Order(true, "BTCUSDT", 20.0, stopPct = 0.03, takePct = 0.09),
        p("купи биткоин на 20 со стопом 3 процента и тейком 9"),
    )

    @Test fun orderWithShield() = assertEquals(
        PhoneAction.Order(true, "ETHUSDT", 15.0, stopPct = 0.05, takePct = 0.10),
        p("купи эфир на 15 с защитой"),
    )

    @Test fun protectHolding() = assertEquals(PhoneAction.Protect("BTCUSDT", 0.05, 0.10), p("защити биткоин"))
    @Test fun protectWithNumbers() = assertEquals(
        PhoneAction.Protect("ETHUSDT", 0.04, 0.12),
        p("поставь стоп-лосс на эфир 4 процента и тейк 12"),
    )

    @Test fun cancelAll() = assertEquals(PhoneAction.CancelOrders("BTCUSDT"), p("отмени все ордера по биткоину"))
    @Test fun cancelNoCoin() = assertEquals(PhoneAction.CancelOrders(null), p("отмени ордера"))

    @Test fun dcaMonday() = assertEquals(PhoneAction.DcaAdd("ETHUSDT", 10.0, Calendar.MONDAY), p("покупай эфир на 10 долларов каждый понедельник"))
    @Test fun dcaDaily() = assertEquals(PhoneAction.DcaAdd("BTCUSDT", 5.0, null), p("докупай биткоин на 5 каждый день"))

    @Test fun orderTag() = assertEquals(
        PhoneAction.Order(true, "BTCUSDT", 20.0, limitPrice = 90000.0, stopPct = 0.05, takePct = 0.1),
        CommandParser.parseActionTag("""{"type":"order","side":"BUY","symbol":"BTCUSDT","usdt":20,"price":90000,"stop":5,"take":10}"""),
    )

    // ── Расчёты
    @Test fun floorStep() {
        assertEquals("0.00123", OrderMath.plain(OrderMath.floorStep(0.0012399, "0.00001000")))
        assertEquals("97250.1", OrderMath.plain(OrderMath.floorStep(97250.19, "0.10000000")))
        assertEquals("12", OrderMath.plain(OrderMath.floorStep(12.9, "1.00000000")))
    }

    @Test fun protectionPrices() {
        val (sl, tp) = OrderMath.protection(100.0, 0.05, 0.10)
        assertEquals(95.0, sl, 1e-9)
        assertEquals(110.0, tp, 1e-9)
    }

    @Test fun positionSize() {
        // Депозит 1000, риск 1%, стоп 5% → позиция 200
        assertEquals(200.0, OrderMath.positionSize(1000.0, 0.01, 0.05), 1e-9)
        assertEquals(10.0, OrderMath.riskUsd(200.0, 0.05), 1e-9)
    }

    @Test fun checkMinNotional() {
        val r = SymbolRules("0.01", "0.00001", 0.00001, 5.0)
        assertTrue(OrderMath.check(r, OrderMath.floorStep(0.00001, "0.00001"), 1_000_000.0).isEmpty())
        assertTrue(OrderMath.check(r, OrderMath.floorStep(0.00001, "0.00001"), 1000.0).isNotEmpty())
    }

    @Test fun dcaNextRunMonday() {
        val now = Calendar.getInstance().apply { set(2026, Calendar.NOVEMBER, 22, 12, 0, 0) } // воскресенье
        val next = Calendar.getInstance().apply { timeInMillis = DcaPlan.nextRun(DcaPlan(1, "BTCUSDT", 10.0, Calendar.MONDAY), now) }
        assertEquals(Calendar.MONDAY, next.get(Calendar.DAY_OF_WEEK))
        assertEquals(23, next.get(Calendar.DAY_OF_MONTH))
        assertEquals(10, next.get(Calendar.HOUR_OF_DAY))
    }

    @Test fun dcaNextRunDailyTomorrow() {
        val now = Calendar.getInstance().apply { set(2026, Calendar.NOVEMBER, 22, 12, 0, 0) }
        val next = Calendar.getInstance().apply { timeInMillis = DcaPlan.nextRun(DcaPlan(1, "BTCUSDT", 10.0, null), now) }
        assertEquals(23, next.get(Calendar.DAY_OF_MONTH))
    }
}
