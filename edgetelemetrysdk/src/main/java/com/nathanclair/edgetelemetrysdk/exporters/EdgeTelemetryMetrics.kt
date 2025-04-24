package com.nathanclair.edgetelemetrysdk.exporters

import com.nathanclair.edgetelemetrysdk.core.EdgeTelemetry
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.metrics.DoubleHistogram
import io.opentelemetry.api.metrics.LongCounter
import io.opentelemetry.api.metrics.LongUpDownCounter
import io.opentelemetry.api.metrics.Meter

/**
 * Helper class for working with metrics in EdgeTelemetry
 */
class EdgeTelemetryMetrics(private val scope: String) {
    private val meter = EdgeTelemetry.rum?.openTelemetry?.meterProvider?.get(scope)

    /**
     * Create a counter with the given name and description
     */
    fun createCounter(name: String, description: String = ""): LongCounter? {
        return meter?.counterBuilder(name)
            ?.setDescription(description)
            ?.build()
    }

    /**
     * Create an up-down counter with the given name and description
     */
    fun createUpDownCounter(name: String, description: String = ""): LongUpDownCounter? {
        return meter?.upDownCounterBuilder(name)
            ?.setDescription(description)
            ?.build()
    }

    /**
     * Create a histogram with the given name and description
     */
    fun createHistogram(name: String, description: String = ""): DoubleHistogram? {
        return meter?.histogramBuilder(name)
            ?.setDescription(description)
            ?.build()
    }

    /**
     * Increment a counter by the given amount with the provided attributes
     */
    fun count(name: String, amount: Long = 1, attributes: Attributes = Attributes.empty()) {
        val counter = meter?.counterBuilder(name)?.build()
        counter?.add(amount, attributes)
    }

    /**
     * Record a measurement in a histogram with the provided attributes
     */
    fun record(name: String, value: Double, attributes: Attributes = Attributes.empty()) {
        val histogram = meter?.histogramBuilder(name)?.build()
        histogram?.record(value, attributes)
    }

    /**
     * Get the underlying OpenTelemetry Meter instance
     */
    fun getMeter(): Meter? {
        return meter
    }

    companion object {
        /**
         * Get a metrics helper with the provided scope name
         */
        @JvmStatic
        fun get(scope: String): EdgeTelemetryMetrics {
            return EdgeTelemetryMetrics(scope)
        }
    }
}