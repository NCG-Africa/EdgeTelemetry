package com.nathanclair.edgetelemetrysdk

import io.opentelemetry.android.features.diskbuffering.DiskBufferingConfig
import java.io.File


/**
 * Types of instrumentations supported by EdgeTelemetry.
 */
enum class InstrumentationType {
    SESSION,
    ANR,
    CRASH,
    NETWORK_CHANGE,
    SLOW_RENDERING,
    HTTP,
    NETWORK_PERFORMANCE,
    METRICS
}


/**
 * Configuration options for EdgeTelemetry SDK.
 */

data class EdgeTelemetryConfig(
    val tracesEndpoint: String = "http://localhost:4318/v1/traces",
    val logsEndpoint: String = "http://localhost:4318/v1/logs",
    val metricsEndpoint: String = "http://localhost:4318/v1/metrics",
    val appName: String = "DefaultApp",
    val sampleRate: Double = 1.0,
    val diskBuffering: DiskBufferingConfig = DiskBufferingConfig(
        enabled = true,
        maxCacheSize = 10_000_000,
        debugEnabled = true
    ),
    val enabledInstrumentations: Set<InstrumentationType> = InstrumentationType.values().toSet(),
    val additionalAttributes: Map<String, String> = emptyMap(),
    val slowRequestThresholdMs: Long = 1500L,
    val metricExportIntervalMs: Long = 60000L
){
    /**
     * Builder for EdgeTelemetryConfig.
     */
    class Builder {
        private var tracesEndpoint: String = "http://localhost:4318/v1/traces"
        private var logsEndpoint: String = "http://localhost:4318/v1/logs"
        private var metricsEndpoint: String = "http://localhost:4318/v1/metrics"
        private var appName: String = "DefaultApp"
        private var sampleRate: Double = 1.0
        private var diskBufferingEnabled: Boolean = true
        private var diskBufferingMaxSize: Long = 10_000_000
        private var diskBufferingDebug: Boolean = true
        private var enabledInstrumentations: MutableSet<InstrumentationType> = InstrumentationType.values().toMutableSet()
        private val additionalAttributes: MutableMap<String, String> = mutableMapOf()
        private var slowRequestThresholdMs: Long = 1500L
        private var metricExportIntervalMs: Long = 60000L
        // Add these properties to your Builder class
        private var maxFileAgeForWriteMillis: Long = 30_000 // 30 seconds
        private var minFileAgeForReadMillis: Long = 33_000  // 33 seconds
        private var maxFileAgeForReadMillis: Long = 18 * 60 * 60 * 1000 // 18 hours
        private var signalsBufferDir: File? = null
        private var maxCacheFileSize: Int = 1024 * 1024 // 1MB

        fun setTracesEndpoint(endpoint: String) = apply { this.tracesEndpoint = endpoint }
        fun setLogsEndpoint(endpoint: String) = apply { this.logsEndpoint = endpoint }
        fun setAppName(name: String) = apply { this.appName = name }
        fun setSampleRate(rate: Double) = apply { this.sampleRate = rate }
        fun disableDiskBuffering() = apply { this.diskBufferingEnabled = false }
        fun setDiskBufferingMaxSize(size: Long) = apply { this.diskBufferingMaxSize = size }
        fun setDiskBufferingDebug(debug: Boolean) = apply { this.diskBufferingDebug = debug }
        fun setSlowRequestThreshold(thresholdMs: Long) = apply {
            this.slowRequestThresholdMs = thresholdMs
        }

        fun disableInstrumentation(type: InstrumentationType) = apply {
            this.enabledInstrumentations.remove(type)
        }

        fun enableInstrumentation(type: InstrumentationType) = apply {
            this.enabledInstrumentations.add(type)
        }

        fun addAttribute(key: String, value: String) = apply {
            this.additionalAttributes[key] = value
        }
        fun setMetricsEndpoint(endpoint: String) = apply { this.metricsEndpoint = endpoint }
        fun setMetricExportInterval(intervalMs: Long) = apply { this.metricExportIntervalMs = intervalMs }



        fun build(): EdgeTelemetryConfig {
            val diskBuffering = DiskBufferingConfig.create(
                enabled = diskBufferingEnabled,
                maxCacheSize = diskBufferingMaxSize.toInt(),
                maxFileAgeForWriteMillis = maxFileAgeForWriteMillis,
                minFileAgeForReadMillis = minFileAgeForReadMillis,
                maxFileAgeForReadMillis = maxFileAgeForReadMillis,
                maxCacheFileSize = maxCacheFileSize,
                signalsBufferDir = signalsBufferDir,
                debugEnabled = diskBufferingDebug
            )


            return EdgeTelemetryConfig(
                tracesEndpoint = tracesEndpoint,
                logsEndpoint = logsEndpoint,
                metricsEndpoint = metricsEndpoint,
                appName = appName,
                sampleRate = sampleRate,
                diskBuffering = diskBuffering,
                enabledInstrumentations = enabledInstrumentations,
                additionalAttributes = additionalAttributes,
                slowRequestThresholdMs = slowRequestThresholdMs,
                metricExportIntervalMs = metricExportIntervalMs
            )
        }
    }

    companion object {
        fun builder(): Builder = Builder()
    }
}



