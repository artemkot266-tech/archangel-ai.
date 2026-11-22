package com.archangel.ai

import com.archangel.ai.learn.Academy
import com.archangel.ai.market.Analytics
import com.archangel.ai.market.Candle
import com.archangel.ai.market.Ticker
import com.archangel.ai.market.Trend
import com.archangel.ai.phone.CommandParser
import com.archangel.ai.phone.PhoneAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalyticsTest {
    private fun c(i: Int, close: Double, high: Double = close + 1, low: Double = close - 1, vol: Double = 100.0, open: Double = close) =
        Candle(i * 3_600_000L, open, high, low, close, vol)

    private fun series(vararg closes: Double) = closes.mapIndexed { i, v -> c(i, v) }

    @Test fun trendUp() {
        val list = (0 until 120).map { c(it, 100.0 + it) }
        assertEquals(Trend.UP, Analytics.trend(list))
    }

    @Test fun trendDown() {
        val list = (0 until 120).map { c(it, 300.0 - it) }
        assertEquals(Trend.DOWN, Analytics.trend(list))
    }

    @Test fun trendFlatShort() = assertEquals(Trend.FLAT, Analytics.trend(series(1.0, 2.0, 3.0)))

    @Test fun levelsFindSupportAndResistance() {
        // Пила между 90 и 110 — несколько касаний обоих уровней
        val list = (0 until 80).map { i ->
            val phase = i % 10
            val v = if (phase < 5) 90.0 + phase * 4 else 110.0 - (phase - 5) * 4
            c(i, v, high = v + 0.2, low = v - 0.2)
        }
        val lv = Analytics.levels(list, price = 100.0)
        assertTrue(lv.any { it.support && it.price in 89.0..91.5 && it.touches >= 3 })
        assertTrue(lv.any { !it.support && it.price in 106.0..111.0 && it.touches >= 3 })
    }

    @Test fun breakoutSignal() {
        val base = (0 until 70).map { c(it, 100.0 + (it % 3)) }
        val last = c(70, 120.0, high = 121.0, low = 101.0, open = 101.0)
        val sigs = Analytics.detectSignals("BTCUSDT", base + last)
        assertTrue(sigs.any { it.key == "break_up" })
    }

    @Test fun volumeSignal() {
        val base = (0 until 70).map { c(it, 100.0 + (it % 3), vol = 100.0) }
        val last = c(70, 101.0, vol = 1000.0)
        assertTrue(Analytics.detectSignals("ETHUSDT", base + last).any { it.key == "volume" })
    }

    @Test fun noSignalsOnQuietMarket() {
        val base = (0 until 80).map { c(it, 100.0 + (it % 2) * 0.5, vol = 100.0) }
        assertTrue(Analytics.detectSignals("ETHUSDT", base).none { it.key == "volume" || it.key.startsWith("break") })
    }

    @Test fun fearGreedParse() {
        val fg = Analytics.parseFearGreed("""{"name":"Fear and Greed Index","data":[{"value":"72","value_classification":"Greed","timestamp":"1"}]}""")
        assertEquals(72, fg?.value)
        assertEquals("жадность", fg?.label)
    }

    @Test fun rssParse() {
        val xml = """<rss><channel><title>X</title>
            <item><title><![CDATA[Биткоин обновил максимум]]></title><link>https://a.b/1</link></item>
            <item><title>Ethereum &amp; ETF</title><link>https://a.b/2</link></item>
            </channel></rss>"""
        val items = Analytics.parseRss(xml)
        assertEquals(2, items.size)
        assertEquals("Биткоин обновил максимум", items[0].title)
        assertEquals("Ethereum & ETF", items[1].title)
        assertEquals("Биткоин обновил максимум", Analytics.newsFor("BTCUSDT", items).first().title)
    }

    @Test fun spokenSummary() {
        val t = listOf(Ticker("BTCUSDT", 97_250.0, 2.13, 0.0, 0.0, 0.0), Ticker("ETHUSDT", 3_400.0, -1.0, 0.0, 0.0, 0.0))
        val text = Analytics.spokenSummary(t, null, null)
        assertTrue(text, text.contains("биткоин 97,3 тыс., плюс 2,1%"))
        assertTrue(text, text.contains("эфир 3400, минус 1,0%"))
    }

    // ── Обучение
    @Test fun lessonDepositBybit() = assertEquals("deposit_bybit", Academy.find("как пополнить байбит")?.id)
    @Test fun lessonDepositGeneric() = assertEquals("deposit_choose", Academy.find("как пополнить биржу")?.id)
    @Test fun lessonWhitebit() = assertEquals("deposit_whitebit", Academy.find("как закинуть деньги на вайтбит")?.id)
    @Test fun lessonStop() = assertEquals("stoploss", Academy.find("научи ставить стоп-лосс")?.id)
    @Test fun lessonRsi() = assertEquals("rsi", Academy.find("что такое rsi")?.id)
    @Test fun lessonNone() = assertNull(Academy.find("объясни теорию относительности"))
    @Test fun lessonIdsUnique() = assertEquals(Academy.lessons.size, Academy.lessons.map { it.id }.toSet().size)
    @Test fun lessonGroupsValid() = assertTrue(Academy.lessons.all { it.group in Academy.groups })

    // ── Голос
    private fun p(t: String) = CommandParser.parse(t)
    @Test fun voiceMarket() = assertEquals(PhoneAction.MarketSummary, p("Архангел, что с рынком?"))
    @Test fun voiceMarketCrypto() = assertEquals(PhoneAction.MarketSummary, p("как там крипта"))
    @Test fun voiceMarketReview() = assertEquals(PhoneAction.MarketSummary, p("обзор рынка"))
    @Test fun voiceBriefingStill() = assertEquals(PhoneAction.Briefing, p("сводка"))
    @Test fun voiceLesson() = assertEquals(PhoneAction.Lesson("stoploss"), p("научи меня ставить стоп-лосс"))
    @Test fun voiceLessonExplain() = assertEquals(PhoneAction.Lesson("rsi"), p("объясни, что такое RSI"))
    @Test fun voiceLessonDeposit() = assertEquals(PhoneAction.Lesson("deposit_okx"), p("как пополнить OKX"))
    @Test fun voiceAcademy() = assertEquals(PhoneAction.Lesson(null), p("открой обучение"))
    @Test fun voiceExplainOther() = assertTrue(p("объясни теорию относительности") !is PhoneAction.Lesson)
    @Test fun voiceTag() = assertEquals(PhoneAction.MarketSummary, CommandParser.parseActionTag("""{"type":"market_summary"}"""))
}
