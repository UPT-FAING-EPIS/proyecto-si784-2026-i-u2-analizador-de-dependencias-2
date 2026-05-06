package com.depanalyzer.telemetry

import kotlin.test.*

class TelemetryConfigTest {
    @AfterTest
    fun tearDown() {
        TelemetryConfig.resetForTests()
    }

    @Test
    fun `sessionId is stable within the same process`() {
        assertEquals(TelemetryConfig.sessionId, TelemetryConfig.sessionId)
    }

    @Test
    fun `ingestUrl appends ingest endpoint`() {
        TelemetryConfig.setBaseUrlForTests("https://example.test")

        assertEquals("https://example.test/ingest", TelemetryConfig.ingestUrl)
    }

    @Test
    fun `disable turns telemetry off`() {
        TelemetryConfig.resetForTests()
        TelemetryConfig.disable()

        assertFalse(TelemetryConfig.enabled)
    }

    @Test
    fun `default baseUrl is configured pulso endpoint`() {
        TelemetryConfig.resetForTests()

        assertTrue(TelemetryConfig.baseUrl.contains("pulsoback.anvian.net"))
    }
}
