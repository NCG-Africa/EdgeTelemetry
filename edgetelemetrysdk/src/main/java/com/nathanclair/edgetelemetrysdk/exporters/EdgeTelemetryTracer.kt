package com.nathanclair.edgetelemetrysdk.exporters

import com.nathanclair.edgetelemetrysdk.core.EdgeTelemetry
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanBuilder
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.context.Context
import io.opentelemetry.context.Scope
import java.util.concurrent.TimeUnit
import java.util.function.Supplier

/**
 * Helper class for working with traces in EdgeTelemetry
 */
class EdgeTelemetryTracer(private val tracerName: String) {

    /**
     * Create a new span builder with the given name
     */
    fun spanBuilder(spanName: String): SpanBuilder? {
        val builder = EdgeTelemetry.getTracer(tracerName)?.spanBuilder(spanName)

        // Apply user attributes if available
        val userId = EdgeTelemetryUser.getUserId()
        val userAttrs = EdgeTelemetryUser.getUserAttributes()

        if (builder != null) {
            if (userId != null) {
                builder.setAttribute("user.id", userId)
            }

            userAttrs.forEach { (key, value) ->
                builder.setAttribute("user.$key", value)
            }
        }

        return builder
    }

    /**
     * Create a span, run the provided function, and then end the span
     */
    fun <T> span(name: String, attributes: Attributes = Attributes.empty(), function: () -> T): T {
        val span = spanBuilder(name)?.startSpan() ?: return function()
        return try {
            span.setAllAttributes(attributes)
            val scope = span.makeCurrent()
            scope.use {
                function()
            }
        } catch (e: Exception) {
            span.recordException(e)
            span.setStatus(StatusCode.ERROR, e.message ?: "Unknown error")
            throw e
        } finally {
            span.end()
        }
    }

    /**
     * Create a span, run the provided function, and then end the span
     * with a supplier pattern for Java interop
     */
    fun <T> spanWithSupplier(name: String, attributes: Attributes = Attributes.empty(), supplier: Supplier<T>): T {
        return span(name, attributes) { supplier.get() }
    }

    /**
     * Add an attribute to the current span
     */
    fun <T> addAttribute(key: AttributeKey<T>, value: T) {
        Span.current().setAttribute(key, value)
    }

    /**
     * Add an event to the current span
     */
    fun addEvent(name: String, attributes: Attributes = Attributes.empty()) {
        Span.current().addEvent(name, attributes)
    }

    /**
     * Add an event with a timestamp to the current span
     */
    fun addEvent(name: String, timestamp: Long, unit: TimeUnit, attributes: Attributes = Attributes.empty()) {
        Span.current().addEvent(name, attributes, timestamp, unit)
    }

    /**
     * Record an exception in the current span
     */
    fun recordException(exception: Throwable) {
        Span.current().recordException(exception)
    }

    /**
     * Set the status of the current span
     */
    fun setStatus(statusCode: StatusCode, description: String = "") {
        Span.current().setStatus(statusCode, description)
    }

    /**
     * Create and make current a span and return a Scope instance that
     * must be closed using Scope.close() or try-with-resources/use
     */
    fun withSpan(name: String, attributes: Attributes = Attributes.empty()): Pair<Span, Scope> {
        val span = spanBuilder(name)?.startSpan() ?:
        throw IllegalStateException("Failed to create span. Is EdgeTelemetry initialized?")
        span.setAllAttributes(attributes)
        val scope = span.makeCurrent()
        return Pair(span, scope)
    }

    companion object {
        /**
         * Get a tracer with the provided name
         */
        @JvmStatic
        fun get(name: String): EdgeTelemetryTracer {
            return EdgeTelemetryTracer(name)
        }
    }
}