package com.nathanclair.edgetelemetrysdk

import android.app.Application
import android.content.Context
import com.nathanclair.edgetelemetrysdk.model.EdgeConfig

interface EdgeTelemetry {
    // Enable auto-instrumentation for network calls
    fun enableNetworkMonitoring(enabled: Boolean = true)

    // Create a span for manual instrumentation
    fun createSpan(name: String): EdgeSpan

    fun trackUserInteraction(interactionType: String, elementId: String, additionalAttributes: Map<String, String> = emptyMap())

    fun enableActivityTracking(application: Application)

    companion object {
        // Will hold implementation instance
        private var instance: EdgeTelemetry? = null

        // Initialize the SDK
        // Initialize with default config
        fun initialize(context: Context) {
            initialize(context, EdgeConfig())
        }

        // Initialize with custom config
        fun initialize(context: Context, config: EdgeConfig) {
            if (instance == null) {
                instance = EdgeTelemetryImpl(context, config)
            }
        }

        // Get the singleton instance
        fun getInstance(): EdgeTelemetry {
            return instance ?: throw IllegalStateException("EdgeTelemetry not initialized. Call initialize() first.")
        }

        // Shut down the SDK
        fun shutdown() {
            instance?.let {
                if (it is EdgeTelemetryImpl) {
                    it.shutdownImpl()
                }
            }
            instance = null
        }
    }
}