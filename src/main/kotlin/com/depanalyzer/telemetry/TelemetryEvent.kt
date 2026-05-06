package com.depanalyzer.telemetry

import com.depanalyzer.BuildInfo
import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.NON_NULL)
data class TelemetryEvent(
    val appId: String = "dependency-scanner",
    val appVersion: String = BuildInfo.VERSION,
    val os: String = System.getProperty("os.name") ?: "unknown",
    val eventType: String,
    val sessionId: String = TelemetryConfig.sessionId,
    val arch: String? = System.getProperty("os.arch"),
    val feature: String? = null,
    val durationMs: Long? = null,
    val errorType: String? = null,
    val errorMessage: String? = null
)
