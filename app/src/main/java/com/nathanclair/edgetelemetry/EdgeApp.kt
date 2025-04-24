package com.nathanclair.edgetelemetry

import android.app.Application
import com.nathanclair.edgetelemetrysdk.core.EdgeTelemetry
import com.nathanclair.edgetelemetrysdk.core.EdgeTelemetryConfig
import com.nathanclair.edgetelemetrysdk.core.TransportType
import com.nathanclair.edgetelemetrysdk.exporters.EdgeTelemetryCrashReporter
import java.util.jar.Attributes

class EdgeApp: Application() {

    override fun onCreate() {
        super.onCreate()

        val config = EdgeTelemetryConfig().apply {
            // Set the backend endpoints
            transportType = TransportType.HTTP
            tracesEndpoint = "https://3322-105-163-156-26.ngrok-free.app/telemetry/v1/ingest"
            logsEndpoint = "https://3322-105-163-156-26.ngrok-free.app/telemetry/v1/ingest"

            // Add authentication if needed
           // addHeader("Authorization", "Bearer your-token")

            // Set basic app information
            setServiceName("my-android-app")
            setServiceVersion("1.0.0")
            setEnvironment("production")

        }
        // Initialize EdgeTelemetry
        EdgeTelemetry.initialize(this, config)

        // Usage example:
        EdgeTelemetry.identifyUser("35768247", mapOf(
            "name" to "Marvin Towett",
            "email" to "marvintowett@gmail.com",
            "plan" to "Platinum"
        ))

    }
}