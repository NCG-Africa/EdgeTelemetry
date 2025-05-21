package com.nathanclair.edgetelemetry

import android.app.Application
import com.nathanclair.edgetelemetrysdk.EdgeTelemetry
import com.nathanclair.edgetelemetrysdk.EdgeTelemetryConfig

class EdgeApp: Application() {

    override fun onCreate() {
        super.onCreate()
        val config = EdgeTelemetryConfig.builder()
            .setAppName("ScoutGlobal")
            .setTracesEndpoint("https://05d7-41-90-172-101.ngrok-free.app/ingest/v1/logs/")
            .setLogsEndpoint("https://05d7-41-90-172-101.ngrok-free.app/ingest/v1/logs/")
            .setMetricsEndpoint("https://05d7-41-90-172-101.ngrok-free.app/ingest/v1/logs/")
            .addAttribute("environment", "production")
            .addAttribute("version", "1.0.0")
            .build()

        EdgeTelemetry.getInstance().initialize(this, config)
    }
}