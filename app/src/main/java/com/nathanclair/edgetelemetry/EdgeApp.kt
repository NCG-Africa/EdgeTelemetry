package com.nathanclair.edgetelemetry

import android.app.Application
import com.nathanclair.edgetelemetrysdk.core.EdgeTelemetry

class EdgeApp: Application() {

    override fun onCreate() {
        super.onCreate()

        EdgeTelemetry.initialize(
            application = this,
            collectionUrl = "https://0452-105-163-1-229.ngrok-free.app/v1/traces", // Replace with your actual OTLP gRPC endpoint
        ) {
            // Optional configuration
            serviceName("NCG Sample App")
            serviceVersion("0.5.10-alpha")
            environment("production")
        }
    }
}