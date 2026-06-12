package com.depanalyzer.tui

import com.github.ajalt.mordant.rendering.AnsiLevel
import org.junit.jupiter.api.Test
import java.nio.charset.Charset
import kotlin.test.assertEquals

class TerminalCapabilitiesDetectorTest {
    @Test
    fun `returns no ansi and no interactive mode in ci`() {
        val detector = TerminalCapabilitiesDetector(
            envProvider = { name -> if (name == "CI") "true" else null },
            hasConsole = { true }
        )

        val capabilities = detector.detect()

        assertEquals(AnsiLevel.NONE, capabilities.ansiLevel)
        assertEquals(false, capabilities.supportsInteractiveTui)
    }

    @Test
    fun `returns no ansi when there is no tty`() {
        val detector = TerminalCapabilitiesDetector(
            envProvider = { null },
            hasConsole = { false }
        )

        val capabilities = detector.detect()

        assertEquals(AnsiLevel.NONE, capabilities.ansiLevel)
        assertEquals(false, capabilities.supportsInteractiveTui)
    }

    @Test
    fun `disables unicode glyphs on windows console without utf8 charset`() {
        val detector = TerminalCapabilitiesDetector(
            envProvider = { null },
            hasConsole = { true },
            consoleCharsetProvider = { Charset.forName("windows-1252") },
            osNameProvider = { "Windows 11" }
        )

        val capabilities = detector.detect()

        assertEquals(false, capabilities.supportsUnicodeGlyphs)
    }

    @Test
    fun `keeps unicode glyphs on windows console with utf8 charset`() {
        val detector = TerminalCapabilitiesDetector(
            envProvider = { null },
            hasConsole = { true },
            consoleCharsetProvider = { Charsets.UTF_8 },
            osNameProvider = { "Windows 11" }
        )

        val capabilities = detector.detect()

        assertEquals(true, capabilities.supportsUnicodeGlyphs)
    }

    @Test
    fun `allows forcing unicode glyphs by environment variable`() {
        val detector = TerminalCapabilitiesDetector(
            envProvider = { name -> if (name == "DEPANALYZER_TUI_UNICODE") "true" else null },
            hasConsole = { true },
            consoleCharsetProvider = { Charset.forName("windows-1252") },
            osNameProvider = { "Windows 11" }
        )

        val capabilities = detector.detect()

        assertEquals(true, capabilities.supportsUnicodeGlyphs)
    }
}
