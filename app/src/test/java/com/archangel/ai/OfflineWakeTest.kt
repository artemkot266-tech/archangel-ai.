package com.archangel.ai

import com.archangel.ai.voice.OfflineWake
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineWakeTest {
    @Test fun grammarIncludesDefaultAndCustom() =
        assertEquals(listOf("джарвис", "архангел"), OfflineWake.grammarWords(listOf("Джарвис", "Архангел")))

    @Test fun grammarSplitsMultiWord() =
        assertEquals(listOf("железный", "друг", "архангел"), OfflineWake.grammarWords(listOf("Железный друг")))

    @Test fun heardName() = assertTrue(OfflineWake.heard("[unk] архангел", listOf("архангел")))
    @Test fun ignoresUnknown() = assertFalse(OfflineWake.heard("[unk] [unk]", listOf("архангел")))
    @Test fun ignoresEmpty() = assertFalse(OfflineWake.heard("", listOf("архангел")))
}
