package com.depanalyzer.tui

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class TuiKeymapTest {
    @Test
    fun `registers required shortcuts`() {
        assertEquals(
            setOf("↑", "↓", "PgUp", "PgDn", "w", "s", "u", "U", "a", "x", "f", "q"),
            TuiKeymap.registeredShortcutKeys()
        )
    }
}
