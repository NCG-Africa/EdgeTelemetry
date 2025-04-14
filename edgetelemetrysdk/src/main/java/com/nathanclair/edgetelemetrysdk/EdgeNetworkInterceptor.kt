package com.nathanclair.edgetelemetrysdk

import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Context
import io.opentelemetry.context.propagation.TextMapSetter
import java.io.IOException

class EdgeNetworkInterceptor(private val openTelemetry: OpenTelemetry) : Interceptor {
    private val tracer: Tracer = openTelemetry.getTracer("com.nathanclair.edgetelemetrysdk.http")

    private val setter =
        TextMapSetter<Request.Builder> { carrier, key, value -> carrier?.header(key, value) }

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val requestBuilder = request.newBuilder()

        val spanName = "${request.method} ${request.url.host}${request.url.encodedPath}"
        val span = tracer.spanBuilder(spanName)
            .setSpanKind(SpanKind.CLIENT)
            .startSpan()

        try {
            span.setAttribute("http.method", request.method)
            span.setAttribute("http.url", request.url.toString())

            // Propagate context via headers
            openTelemetry.propagators.textMapPropagator.inject(
                Context.current().with(span),
                requestBuilder,
                setter
            )

            val modifiedRequest = requestBuilder.build()
            val startTimeMs = System.currentTimeMillis()
            val response = chain.proceed(modifiedRequest)
            val endTimeMs = System.currentTimeMillis()

            // Record response details
            span.setAttribute("http.status_code", response.code.toLong())
            span.setAttribute("http.response_time_ms", endTimeMs - startTimeMs)

            if (!response.isSuccessful) {
                span.setStatus(StatusCode.ERROR)
            }

            return response
        } catch (e: Exception) {
            span.recordException(e)
            span.setStatus(StatusCode.ERROR)
            throw e
        } finally {
            span.end()
        }
    }
}