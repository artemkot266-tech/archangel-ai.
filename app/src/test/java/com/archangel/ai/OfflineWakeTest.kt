package com.archangel.ai

import com.archangel.ai.voice.OfflineWake
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OfflineWakeTest {
    @Test fun grammarIncludesDefaultAndCustom() =
        assertEquals(listOf("джарвис", "архангел"), OfflineWake.grammarWords(listOf("Джарвис", "Архангел")))

    @Test fun grammarSplitsMultiWord() =
        assertEquals(listOf("железный", "друг", "архангел"), OfflineWake.grammarWords(listOf("Железный друг")))

    private fun res(vararg w: Pair<String, Double>): String {
        val arr = org.json.JSONArray()
        w.forEach { arr.put(org.json.JSONObject().put("word", it.first).put("conf", it.second)) }
        return org.json.JSONObject().put("result", arr).put("text", w.joinToString(" ") { it.first }).toString()
    }
    private val names = listOf("архангел")

    @Test fun wakeAlone() = assertEquals("", OfflineWake.match(res("архангел" to 0.97), names))
    @Test fun wakeWithCommand() = assertEquals("включи музыку", OfflineWake.match(res("архангел" to 0.95, "включи" to 0.9, "музыку" to 0.9), names))
    @Test fun wakeSecondWord() = assertEquals("время", OfflineWake.match(res("эй" to 0.6, "архангел" to 0.9, "время" to 0.9), names))
    @Test fun lowConfidenceIgnored() = assertNull(OfflineWake.match(res("архангел" to 0.55), names))
    @Test fun otherFormIgnored() = assertNull(OfflineWake.match(res("про" to 0.9, "архангела" to 0.95), names))
    @Test fun midSentenceIgnored() = assertNull(OfflineWake.match(res("я" to 0.9, "смотрел" to 0.9, "архангел" to 0.95), names))
    @Test fun emptyIgnored() = assertNull(OfflineWake.match("""{"text":""}""", names))
}
