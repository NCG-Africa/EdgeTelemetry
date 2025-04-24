package com.nathanclair.edgetelemetrysdk.exporters

import android.util.Log
import com.nathanclair.edgetelemetrysdk.core.EdgeTelemetry
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.StatusCode
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Helper class for working with crash reporting in EdgeTelemetry
 */
class EdgeTelemetryCrashReporter private constructor() {
    companion object {
        private const val TAG = "EdgeTelemetryCrash"
        private val defaultUncaughtExceptionHandlers = CopyOnWriteArrayList<Thread.UncaughtExceptionHandler>()
        private var installed = false

        /**
         * Install the crash reporter
         */
        @JvmStatic
        fun install() {
            if (installed) {
                Log.w(TAG, "Crash reporter already installed")
                return
            }

            val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
            if (defaultHandler != null) {
                defaultUncaughtExceptionHandlers.add(defaultHandler)
            }

            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                reportCrash(thread, throwable)

                // Call any previous handlers
                for (handler in defaultUncaughtExceptionHandlers) {
                    try {
                        handler.uncaughtException(thread, throwable)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in previous uncaught exception handler", e)
                    }
                }
            }

            installed = true
            Log.i(TAG, "EdgeTelemetry crash reporter installed")
        }

        /**
         * Report a crash manually
         */
        @JvmStatic
        fun reportCrash(thread: Thread, throwable: Throwable) {
            try {
                Log.e(TAG, "Reporting crash in thread: ${thread.name}", throwable)

                // Create attributes with crash details
                val attributes = Attributes.builder()
                    .put(AttributeKey.stringKey("error.type"), throwable.javaClass.name)
                    .put(AttributeKey.stringKey("error.message"), throwable.message ?: "")
                    .put(AttributeKey.stringKey("error.stack_trace"), throwable.stackTraceToString())
                    .put(AttributeKey.stringKey("thread.name"), thread.name)
                    .put(AttributeKey.stringKey("thread.id"), thread.id.toString())
                    .build()

                // Log the crash as an event
                EdgeTelemetryLogger.get("app.crash")
                    .event("application.crash", attributes)

                // Also record the crash in the current span if available
                val currentSpan = EdgeTelemetry.getCurrentSpan()
                if (currentSpan != null && currentSpan.isRecording) {
                    currentSpan.recordException(throwable)
                    currentSpan.setStatus(StatusCode.ERROR, throwable.message ?: "Uncaught exception")
                }

                // Create a new span specifically for the crash if no current span
                if (currentSpan == null || !currentSpan.isRecording) {
                    val crashTracer = EdgeTelemetryTracer.get("app.crashes")
                    val crashSpan = crashTracer.spanBuilder("application.crash")?.startSpan()
                    if (crashSpan != null) {
                        crashSpan.setAllAttributes(attributes)
                        crashSpan.recordException(throwable)
                        crashSpan.setStatus(StatusCode.ERROR, throwable.message ?: "Uncaught exception")
                        crashSpan.end()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error reporting crash", e)
            }
        }

        /**
         * Add an additional uncaught exception handler
         */
        @JvmStatic
        fun addUncaughtExceptionHandler(handler: Thread.UncaughtExceptionHandler) {
            defaultUncaughtExceptionHandlers.add(handler)
        }
    }
}