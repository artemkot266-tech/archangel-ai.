package com.archangel.ai

import com.archangel.ai.ai.GodMode
import com.archangel.ai.ai.GodRole
import com.archangel.ai.phone.CommandParser
import com.archangel.ai.phone.PhoneAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GodModeTest {
    private fun p(t: String) = CommandParser.parse(t)

    @Test fun upgrade() = assertEquals(PhoneAction.GodMode(true), p("Архангел, апгрейд"))
    @Test fun upgradeAlt() = assertEquals(PhoneAction.GodMode(true), p("включи режим бога"))
    @Test fun downgrade() = assertEquals(PhoneAction.GodMode(false), p("обычный режим"))
    @Test fun exitGod() = assertEquals(PhoneAction.GodMode(false), p("выйди из режима бога"))
    @Test fun roleTeacher() = assertEquals(PhoneAction.GodRoleSet("TEACHER"), p("стань учителем"))
    @Test fun roleFriend() = assertEquals(PhoneAction.GodRoleSet("FRIEND"), p("будь моим другом"))
    @Test fun roleEnglish() = assertEquals(PhoneAction.GodRoleSet("ENGLISH"), p("стань репетитором английского"))
    @Test fun summary() = assertEquals(PhoneAction.GodSummary, p("подведи итог"))
    @Test fun notARole() = assertTrue(p("будь добр, включи фонарик") !is PhoneAction.GodRoleSet)
    @Test fun profitStill() = assertEquals(PhoneAction.Profit(30), p("мои итоги"))

    @Test fun spokenShort() = assertEquals("Ответ 42.", GodMode.spoken("Ответ 42."))
    @Test fun spokenLong() {
        val long = "Главное: делайте шаг за шагом. " + "Подробность номер такая-то и ещё немного текста. ".repeat(20)
        val s = GodMode.spoken(long)
        assertTrue(s.startsWith("Главное: делайте шаг за шагом."))
        assertTrue(s.endsWith("Подробности — на экране."))
        assertTrue(s.length < 520)
    }

    @Test fun splitSummary() {
        val (body, facts) = GodMode.splitSummary("1. Обсудили план.\n2. Шаг — купить кроссовки.\nЗАПОМНИТЬ: готовится к марафону весной\nЗАПОМНИТЬ: бегает по утрам")
        assertEquals("1. Обсудили план.\n2. Шаг — купить кроссовки.", body)
        assertEquals(listOf("готовится к марафону весной", "бегает по утрам"), facts)
    }

    @Test fun roleFromText() = assertEquals(GodRole.SOLVER, GodRole.fromText("решателем задач"))
}
