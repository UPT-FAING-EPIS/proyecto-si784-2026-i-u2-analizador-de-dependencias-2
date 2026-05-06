package com.depanalyzer.telemetry

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue

class TelemetryClientTest {
    @AfterTest
    fun tearDown() {
        TelemetryConfig.resetForTests()
    }

    @Test
    fun `send does not throw when telemetry is disabled`() {
        TelemetryConfig.disable()

        val result = runCatching {
            TelemetryClient.send(TelemetryEvent(eventType = "app_start"))
        }

        assertTrue(result.isSuccess)
    }

    @Test
    fun `send does not throw when server is unreachable`() {
        TelemetryConfig.resetForTests()
        TelemetryConfig.setBaseUrlForTests("http://localhost:1")

        val result = runCatching {
            TelemetryClient.send(TelemetryEvent(eventType = "app_start"))
            Thread.sleep(500)
        }

        assertTrue(result.isSuccess)
    }
}
