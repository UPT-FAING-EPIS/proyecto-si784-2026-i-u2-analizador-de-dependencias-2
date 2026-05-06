package com.depanalyzer.telemetry

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import tools.jackson.databind.json.JsonMapper
import java.util.concurrent.TimeUnit

object TelemetryClient {
    private val json = "application/json; charset=utf-8".toMediaType()
    private val mapper = JsonMapper.builder().build()

    private val http = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .writeTimeout(3, TimeUnit.SECONDS)
        .build()

    fun send(event: TelemetryEvent) {
        if (!TelemetryConfig.enabled) return

        Thread {
            try {
                val payload = linkedMapOf<String, Any?>(
                    "appId" to event.appId,
                    "appVersion" to event.appVersion,
                    "os" to event.os,
                    "eventType" to event.eventType,
                    "sessionId" to event.sessionId,
                    "arch" to event.arch,
                    "feature" to event.feature,
                    "durationMs" to event.durationMs,
                    "errorType" to event.errorType,
                    "errorMessage" to event.errorMessage
                ).filterValues { it != null }

                val body = mapper.writeValueAsString(payload).toRequestBody(json)
                val request = Request.Builder()
                    .url(TelemetryConfig.ingestUrl)
                    .post(body)
                    .build()

                http.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        System.err.println("[telemetry] POST /ingest returned ${response.code} - skipping")
                    }
                }
            } catch (e: Exception) {
                System.err.println("[telemetry] send failed silently: ${e.message}")
            }
        }.also { it.isDaemon = true }.start()
    }
}
