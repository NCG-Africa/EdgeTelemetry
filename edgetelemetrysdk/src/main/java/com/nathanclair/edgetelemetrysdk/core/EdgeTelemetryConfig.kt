package com.nathanclair.edgetelemetrysdk.core

import com.nathanclair.edgetelemetrysdk.core.EdgeTelemetry.Companion.networkAttributesExtractors
import io.opentelemetry.android.instrumentation.AndroidInstrumentation
import io.opentelemetry.android.instrumentation.network.NetworkAttributesExtractor
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.common.AttributeKey

/**
 * Transport type for sending telemetry data
 */
enum class TransportType {
    HTTP,
    GRPC
}

/**
 * Configuration class for EdgeTelemetry
 */
class EdgeTelemetryConfig {
    /**
     * Transport type to use for sending telemetry data
     * Default: HTTP
     */
    var transportType: TransportType = TransportType.HTTP

    /**
     * Endpoint for sending trace data
     * Default: http://localhost:4318/v1/traces
     */
    var tracesEndpoint: String = "http://localhost:4318/v1/traces"

    /**
     * Endpoint for sending log data
     * Default: http://localhost:4318/v1/logs
     */
    var logsEndpoint: String = "http://localhost:4318/v1/logs"

    /**
     * Headers to include with all requests to the exporter endpoints
     */
    val headers: MutableMap<String, String> = mutableMapOf()

    /**
     * Global attributes to include with all telemetry data
     */
    var globalAttributes: Attributes = Attributes.empty()

    /**
     * Whether to enable disk buffering for offline telemetry collection
     * Default: true
     */
    var diskBuffering: Boolean = true

    /**
     * Maximum size of the disk buffer in bytes
     * Default: 10 MB
     */
    var maxDiskBufferSize: Int = 10 * 1024 * 1024

    /**
     * Whether to include network attributes in telemetry data
     * Default: true
     */
    var includeNetworkAttributes: Boolean = true

    /**
     * Whether to include screen attributes in telemetry data
     * Default: true
     */
    var includeScreenAttributes: Boolean = true

    /**
     * Whether to enable SDK initialization events
     * Default: true
     */
    var enableSdkInitEvents: Boolean = true

    /**
     * Whether to enable debug mode
     * Default: false
     */
    var debugMode: Boolean = false

    /**
     * Whether to enable session instrumentation
     * Default: true
     */
    var enableSessionInstrumentation: Boolean = true

    /**
     * Whether to enable ANR detection
     * Default: true
     */
    var enableAnrDetection: Boolean = true

    /**
     * ANR detection interval in milliseconds
     * Default: 4000ms (4 seconds)
     */
    var anrDetectionIntervalMs: Long = 4000

    /**
     * ANR detection threshold in milliseconds
     * Default: 5000ms (5 seconds)
     */
    var anrDetectionThresholdMs: Long = 5000

    /**
     * Whether to enable slow rendering detection
     * Default: true
     */
    var enableSlowRenderingDetection: Boolean = true

    /**
     * Slow rendering detection poll interval in milliseconds
     * Default: 1000ms (1 second)
     */
    var slowRenderingDetectionPollIntervalMs: Long = 1000

    /**
     * List of custom instrumentations to add
     */
    val instrumentations: MutableList<AndroidInstrumentation> = mutableListOf()


    /**
     * Whether to track screen views (activities and fragments)
     * Default: true
     */
    var trackScreenViews: Boolean = true

    /**
     * Whether to track navigation events
     * Default: true
     */
    var trackNavigation: Boolean = true

    /**
     * Whether to automatically instrument user clicks
     * Default: false (to avoid unexpected behavior)
     */
    var autoTrackUserClicks: Boolean = false

    /**
     * Add a global attribute
     */
    fun addGlobalAttribute(key: String, value: String): EdgeTelemetryConfig {
        val attributesBuilder = Attributes.builder()
        if (globalAttributes != Attributes.empty()) {
            // Copy existing attributes - use traditional for-loop to avoid lambda type issues
            for (entry in globalAttributes.asMap().entries) {
                @Suppress("UNCHECKED_CAST")
                attributesBuilder.put(entry.key as AttributeKey<Any>, entry.value)
            }
        }
        attributesBuilder.put(AttributeKey.stringKey(key), value)
        globalAttributes = attributesBuilder.build()
        return this
    }

    /**
     * Set service name
     */
    fun setServiceName(name: String): EdgeTelemetryConfig {
        return addGlobalAttribute("service.name", name)
    }

    /**
     * Set service version
     */
    fun setServiceVersion(version: String): EdgeTelemetryConfig {
        return addGlobalAttribute("service.version", version)
    }

    /**
     * Set environment
     */
    fun setEnvironment(env: String): EdgeTelemetryConfig {
        return addGlobalAttribute("environment", env)
    }

    /**
     * Add a header to be included with all requests to the exporter endpoints
     */
    fun addHeader(key: String, value: String): EdgeTelemetryConfig {
        headers[key] = value
        return this
    }

    /**
     * Add a custom instrumentation
     */
    fun addInstrumentation(instrumentation: AndroidInstrumentation): EdgeTelemetryConfig {
        instrumentations.add(instrumentation)
        return this
    }

    /**
     * Set global attributes
     */
    fun setGlobalAttributes(attributes: Attributes): EdgeTelemetryConfig {
        this.globalAttributes = attributes
        return this
    }

    /**
     * Add a custom network attributes extractor
     */
    fun addNetworkAttributesExtractor(extractor: NetworkAttributesExtractor): EdgeTelemetryConfig {
        networkAttributesExtractors.add(extractor)
        return this
    }

}
