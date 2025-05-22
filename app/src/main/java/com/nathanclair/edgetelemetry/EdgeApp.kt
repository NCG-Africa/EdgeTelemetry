package com.nathanclair.edgetelemetry

import android.app.Application
import com.nathanclair.edgetelemetrysdk.EdgeTelemetry
import com.nathanclair.edgetelemetrysdk.EdgeTelemetryConfig

class EdgeApp: Application() {

    override fun onCreate() {
        super.onCreate()
        val config = EdgeTelemetryConfig.builder()
            .setAppName("ScoutGlobal")
            .setTracesEndpoint("https://5274-41-90-172-27.ngrok-free.app/v1/traces")
            .setLogsEndpoint("https://5274-41-90-172-27.ngrok-free.app/v1/traces")
            .setMetricsEndpoint("https://5274-41-90-172-27.ngrok-free.app/v1/traces")
            .addAttribute("environment", "production")
            .addAttribute("version", "1.0.0")
            .addAttribute("apiKey", "edge-47A9F1C2-5B74-4E2A-AC39-9D3B6F7412CE")
            .build()

        EdgeTelemetry.getInstance().initialize(this, config)
    }
}