package com.nathanclair.edgetelemetrysdk

import android.util.Log
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.trace.export.SpanExporter
import io.opentelemetry.sdk.trace.data.SpanData
import org.json.JSONObject

class LoggingSpanExporter(private val tag: String = "EdgeTelemetry-Export") : SpanExporter {

    override fun export(spans: Collection<SpanData>): CompletableResultCode {
        for (span in spans) {
            try {
                val json = JSONObject().apply {
                    put("traceId", span.traceId)
                    put("spanId", span.spanId)
                    put("name", span.name)
                    put("startEpochNanos", span.startEpochNanos)
                    put("endEpochNanos", span.endEpochNanos)
                    put("status", span.status.statusCode.name)
                    put("attributes", JSONObject(span.attributes.asMap().mapKeys { it.key.key }))
                    put("events", span.events.map {
                        mapOf(
                            "name" to it.name,
                            "timestamp" to it.epochNanos,
                            "attributes" to it.attributes.asMap().mapKeys { attr -> attr.key.key }
                        )
                    })
                }

                Log.d(tag, "Span Exported:\n$json")
            } catch (e: Exception) {
                Log.e(tag, "Failed to log span", e)
            }
        }
        return CompletableResultCode.ofSuccess()
    }

    override fun flush(): CompletableResultCode = CompletableResultCode.ofSuccess()

    override fun shutdown(): CompletableResultCode = CompletableResultCode.ofSuccess()
}