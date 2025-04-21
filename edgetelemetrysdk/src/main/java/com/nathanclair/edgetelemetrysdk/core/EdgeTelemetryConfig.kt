package com.nathanclair.edgetelemetrysdk.core

class EdgeTelemetryConfig private constructor(
    val enableCrashReporting: Boolean,
    val enableAnrDetection: Boolean,
    val enableNetworkChangeDetection: Boolean,
    val enableActivityLifecycleInstrumentation: Boolean,
    val enableFragmentLifecycleMonitoring: Boolean,
    val enableSlowRenderDetection: Boolean,
    val enableOfflineBuffering: Boolean,
    val offlineStorageSizeBytes: Long,
    val slowRenderThresholdMs: Long,
    val serviceName: String,
    val serviceVersion: String,
    val environment: String,
    val additionalAttributes: Map<String, String>
) {
    class Builder {
        private var enableCrashReporting = true
        private var enableAnrDetection = true
        private var enableNetworkChangeDetection = true
        private var enableActivityLifecycleInstrumentation = true
        private var enableFragmentLifecycleMonitoring = true
        private var enableSlowRenderDetection = true
        private var enableOfflineBuffering = true
        private var offlineStorageSizeBytes = 10 * 1024 * 1024L // 10MB default
        private var slowRenderThresholdMs = 700L // 700ms threshold
        private var serviceName = "unknown_service"
        private var serviceVersion = "0.1.0"
        private var environment = "production"
        private val additionalAttributes = mutableMapOf<String, String>()

        // Add builder methods for the new properties
        fun serviceName(name: String) = apply { serviceName = name }
        fun serviceVersion(version: String) = apply { serviceVersion = version }
        fun environment(env: String) = apply { environment = env }

        // Builder functions for each option
        fun build() = EdgeTelemetryConfig(
            enableCrashReporting,
            enableAnrDetection,
            enableNetworkChangeDetection,
            enableActivityLifecycleInstrumentation,
            enableFragmentLifecycleMonitoring,
            enableSlowRenderDetection,
            enableOfflineBuffering,
            offlineStorageSizeBytes,
            slowRenderThresholdMs,
            serviceName,
            serviceVersion,
            environment,
            additionalAttributes.toMap()
        )
    }
}