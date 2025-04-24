package com.nathanclair.edgetelemetrysdk.exporters

import com.nathanclair.edgetelemetrysdk.core.EdgeTelemetry
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.logs.Logger
import io.opentelemetry.api.logs.Severity

/**
 * Helper class for working with logs and events in EdgeTelemetry
 */
class EdgeTelemetryLogger(private val scope: String) {
    private val logger: Logger? = EdgeTelemetry.getLogger(scope)

    /**
     * Log a message with INFO severity
     */
    fun info(message: String, attributes: Attributes = Attributes.empty()) {
        logger?.logRecordBuilder()
            ?.setSeverity(Severity.INFO)
            ?.setBody(message)
            ?.setAllAttributes(attributes)
            ?.emit()
    }

    /**
     * Log a message with ERROR severity
     */
    fun error(message: String, exception: Throwable? = null, attributes: Attributes = Attributes.empty()) {
        val builder = logger?.logRecordBuilder()
            ?.setSeverity(Severity.ERROR)
            ?.setBody(message)
            ?.setAllAttributes(attributes)

        if (exception != null) {
            builder?.setAttribute(AttributeKey.stringKey("exception.type"), exception.javaClass.name)
            builder?.setAttribute(AttributeKey.stringKey("exception.message"), exception.message ?: "")
            builder?.setAttribute(AttributeKey.stringKey("exception.stacktrace"), exception.stackTraceToString())
        }

        builder?.emit()
    }

    /**
     * Log a message with WARN severity
     */
    fun warn(message: String, attributes: Attributes = Attributes.empty()) {
        logger?.logRecordBuilder()
            ?.setSeverity(Severity.WARN)
            ?.setBody(message)
            ?.setAllAttributes(attributes)
            ?.emit()
    }

    /**
     * Log a message with DEBUG severity
     */
    fun debug(message: String, attributes: Attributes = Attributes.empty()) {
        logger?.logRecordBuilder()
            ?.setSeverity(Severity.DEBUG)
            ?.setBody(message)
            ?.setAllAttributes(attributes)
            ?.emit()
    }

    /**
     * Create an event with the given name and attributes
     */
    fun event(name: String, attributes: Attributes = Attributes.empty()) {
        EdgeTelemetry.createEventBuilder(scope, name)
            ?.setAllAttributes(attributes)
            ?.emit()
    }

    /**
     * Create a custom log with the given severity, message and attributes
     */
    fun log(severity: Severity, message: String, attributes: Attributes = Attributes.empty()) {
        logger?.logRecordBuilder()
            ?.setSeverity(severity)
            ?.setBody(message)
            ?.setAllAttributes(attributes)
            ?.emit()
    }

    companion object {
        /**
         * Get a logger with the provided scope name
         */
        @JvmStatic
        fun get(scope: String): EdgeTelemetryLogger {
            return EdgeTelemetryLogger(scope)
        }
    }
}