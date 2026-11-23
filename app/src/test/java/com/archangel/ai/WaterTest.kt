package com.archangel.ai

import com.archangel.ai.features.Water
import com.archangel.ai.features.WaterEntry
import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WaterTest {
    @Test fun glass() = assertEquals(250, Water.parseAmount("стакан воды"))
    @Test fun twoGlasses() = assertEquals(500, Water.parseAmount("2 стакана воды"))
    @Test fun twoGlassesWord() = assertEquals(500, Water.parseAmount("два стакана воды"))
    @Test fun ml() = assertEquals(300, Water.parseAmount("300 мл воды"))
    @Test fun mlNoSpace() = assertEquals(330, Water.parseAmount("330мл воды"))
    @Test fun halfLiter() = assertEquals(500, Water.parseAmount("пол литра воды"))
    @Test fun halfLiterOneWord() = assertEquals(500, Water.parseAmount("поллитра воды"))
    @Test fun decimalLiter() = assertEquals(500, Water.parseAmount("0,5 л воды"))
    @Test fun liter() = assertEquals(1000, Water.parseAmount("литр воды"))
    @Test fun oneAndHalf() = assertEquals(1500, Water.parseAmount("полтора литра воды"))
    @Test fun bottle() = assertEquals(500, Water.parseAmount("бутылку воды"))
    @Test fun noAmount() = assertEquals(250, Water.parseAmount("воды"))
    @Test fun bareNumber() = assertEquals(200, Water.parseAmount("200 воды"))
    @Test fun tooMuch() = assertNull(Water.parseAmount("10 литров воды"))

    @Test fun mentions() {
        assertTrue(Water.mentionsWater("стакан воды"))
        assertFalse(Water.mentionsWater("рюмку водки"))
        assertFalse(Water.mentionsWater("купить хлеб"))
    }

    @Test fun goal() {
        assertEquals(2500, Water.parseGoal("2,5 литра"))
        assertEquals(1800, Water.parseGoal("1800 мл"))
        assertEquals(2000, Water.parseGoal("2"))
        assertEquals(3000, Water.parseGoal("три литра"))
        assertNull(Water.parseGoal("сто"))
        assertNull(Water.parseGoal("20 литров"))
    }

    @Test fun format() {
        assertEquals("250 мл", Water.format(250))
        assertEquals("1 250 мл", Water.format(1250))
        assertEquals("2 л", Water.format(2000))
        assertEquals("2,5 л", Water.format(2500))
    }

    @Test fun todayCountsOnlyToday() {
        val now = Calendar.getInstance().apply { set(2026, Calendar.NOVEMBER, 23, 15, 0, 0); set(Calendar.MILLISECOND, 0) }
        val yesterday = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }.timeInMillis
        val list = listOf(WaterEntry(1, 250, now.timeInMillis), WaterEntry(2, 500, now.timeInMillis - 3600_000), WaterEntry(3, 1000, yesterday))
        assertEquals(750, Water.today(list, now))
    }

    @Test fun progress() {
        assertEquals("Сегодня 1 250 мл из 2 л — осталось 750 мл.", Water.progressText(1250, 2000))
        assertTrue(Water.progressText(2100, 2000).contains("выполнена"))
        assertTrue(Water.progressText(0, 2000).contains("ещё не было"))
        assertEquals(0.5f, Water.fraction(1000, 2000), 0.001f)
        assertEquals(1f, Water.fraction(5000, 2000), 0.001f)
    }
}
