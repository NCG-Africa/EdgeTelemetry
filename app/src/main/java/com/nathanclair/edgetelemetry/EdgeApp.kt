package com.nathanclair.edgetelemetry

import android.app.Application
import com.nathanclair.edgetelemetrysdk.EdgeTelemetry
import com.nathanclair.edgetelemetrysdk.EdgeTelemetryConfig
import java.io.File

class EdgeApp: Application() {

    override fun onCreate() {
        super.onCreate()
        val config = EdgeTelemetryConfig.builder()
            .setAppName("ScoutGlobal")
            .setTracesEndpoint("https://f8ba-105-163-2-221.ngrok-free.app/otlp/v1/traces")
            .setLogsEndpoint("https://f8ba-105-163-2-221.ngrok-free.app/otlp/v1/traces")
            .setMetricsEndpoint("https://f8ba-105-163-2-221.ngrok-free.app/otlp/v1/traces")
            .addAttribute("environment", "production")
            .addAttribute("version", "1.0.0")
            .build()

        EdgeTelemetry.getInstance().initialize(this, config)
    }
}