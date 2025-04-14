package com.nathanclair.edgetelemetrysdk

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.Tracer

class EdgeSpanImpl(
    private val name: String,
    openTelemetry: OpenTelemetry
) : EdgeSpan {
    private val tracer: Tracer = openTelemetry.getTracer("com.nathanclair.edgetelemetrysdk")
    private var span: Span? = null

    override fun setAttribute(key: String, value: String): EdgeSpan {
        span?.setAttribute(key, value)
        return this
    }

    override fun recordError(throwable: Throwable): EdgeSpan {
        span?.recordException(throwable)
        span?.setStatus(StatusCode.ERROR)
        return this
    }

    override fun start(): EdgeSpan {
        if (span == null) {
            span = tracer.spanBuilder(name).startSpan()
        }
        return this
    }

    override fun end() {
        span?.end()
        span = null
    }
}