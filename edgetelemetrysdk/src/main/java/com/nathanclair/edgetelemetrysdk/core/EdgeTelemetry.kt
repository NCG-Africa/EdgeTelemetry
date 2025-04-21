package com.nathanclair.edgetelemetrysdk.core

import android.app.Application
import android.content.Context
import android.util.Log
import io.opentelemetry.android.OpenTelemetryRum
import io.opentelemetry.android.config.OtelRumConfig
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor
import java.util.concurrent.TimeUnit

/**
 * Core class for the Edge Telemetry SDK.
 * Provides OpenTelemetry-based telemetry collection for Android applications.
 */
class EdgeTelemetry private constructor(
    private val applicationContext: Context,
    private val collectionUrl: String,
    private val config: EdgeTelemetryConfig
) {
    companion object {
        private const val TAG = "EdgeTelemetry"

        @Volatile
        private var instance: EdgeTelemetry? = null
        var openTelemetryRum: OpenTelemetryRum? = null

        fun initialize(
            application: Application,
            collectionUrl: String,
            configBlock: EdgeTelemetryConfig.Builder.() -> Unit = {}
        ): EdgeTelemetry {
            return instance ?: synchronized(this) {
                instance ?: buildEdgeTelemetry(application, collectionUrl, configBlock).also { instance = it }
            }
        }

        private fun buildEdgeTelemetry(
            application: Application,
            collectionUrl: String,
            configBlock: EdgeTelemetryConfig.Builder.() -> Unit
        ): EdgeTelemetry {
            val configBuilder = EdgeTelemetryConfig.Builder()
            configBuilder.apply(configBlock)
            val config = configBuilder.build()

            return EdgeTelemetry(application, collectionUrl, config)
        }

        fun getInstance(): EdgeTelemetry {
            return instance ?: throw IllegalStateException(
                "EdgeTelemetry not initialized. Call initialize() first."
            )
        }
    }

    init {
        setupOtelSdk(collectionUrl)
        if (config.enableCrashReporting) {
            setupCustomCrashHandler()
        }
    }

    private fun setupOtelSdk(collectionUrl: String) {
        Log.i(TAG, "Initializing EdgeTelemetry SDK with endpoint: $collectionUrl")

        // Create configuration for OpenTelemetry RUM
        val otelConfig = OtelRumConfig().apply {
            // Set global attributes from our configuration
            setGlobalAttributes(
                Attributes.builder()
                    .put("sdk.name", "EdgeTelemetry")
                    .put("sdk.version", "0.1.0")
                    .put("service.name", config.serviceName)
                    .put("service.version", config.serviceVersion)
                    .put("deployment.environment", config.environment)
                    .put("device.id", getDeviceId())
                    .apply {
                        // Add any additional attributes from config
                        config.additionalAttributes.forEach { (key, value) ->
                            put(key, value)
                        }
                    }
                    .build()
            )

            // In your version, crash reporting appears to be built-in
            if (config.enableCrashReporting) {
                Log.i(TAG, "Configuring crash reporting")
                // Try to check if the enableCrashReporting method exists
                try {
                    val methods = this.javaClass.methods
                    val crashMethod = methods.find { it.name == "enableCrashReporting" && it.parameterTypes.size == 1 }
                    if (crashMethod != null) {
                        crashMethod.invoke(this, true)
                        Log.i(TAG, "Crash reporting explicitly enabled via method: ${crashMethod.name}")
                    } else {
                        Log.d(TAG, "No enableCrashReporting method found, using default crash handling")
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "Error enabling crash reporting: ${e.message}")
                }
            }
        }

        // Build OpenTelemetryRum instance
        val openTelemetryRumBuilder = OpenTelemetryRum.builder(applicationContext as Application, otelConfig).apply {
            // Keep using gRPC exporter since it seems to be the default for your implementation
            addTracerProviderCustomizer { tracerProviderBuilder, _ ->
                // Create OTLP exporter with the provided collection URL
                // Ensure the endpoint is correct for the server
                val baseUrl = collectionUrl.trimEnd('/')
                val tracesEndpoint = "$baseUrl/v1/traces"

                // Set longer timeout for crash reporting to work
                val exporter = OtlpGrpcSpanExporter.builder()
                    .setEndpoint(tracesEndpoint)
                    .setTimeout(30000, java.util.concurrent.TimeUnit.MILLISECONDS) // 30 second timeout
                    .build()

                Log.i(TAG, "Configured gRPC exporter with endpoint: $tracesEndpoint")

                // Use a more aggressive batch processor configuration for crash reporting
                val batchSpanProcessor = BatchSpanProcessor.builder(exporter)
                    .setScheduleDelay(500, java.util.concurrent.TimeUnit.MILLISECONDS) // Shorter schedule delay
                    .setMaxQueueSize(2048) // Larger queue
                    .setMaxExportBatchSize(512) // Larger batch size
                    .build()

                // Add the processor to the tracer provider
                tracerProviderBuilder.addSpanProcessor(batchSpanProcessor)
            }
        }

        // Build and store the OpenTelemetry instance
        try {
            openTelemetryRum = openTelemetryRumBuilder.build()
            Log.i(TAG, "EdgeTelemetry SDK successfully initialized")
            Log.i(TAG, "RUM session ID: ${openTelemetryRum?.rumSessionId}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize EdgeTelemetry SDK", e)
        }
    }

    // Add a custom crash handler to ensure crashes are properly captured and reported
    private fun setupCustomCrashHandler() {
        Log.i(TAG, "Setting up custom crash handler")

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                Log.e(TAG, "Uncaught exception in thread: ${thread.name}", throwable)

                // Create a span for the crash that will be exported to your server
                val tracer = openTelemetryRum?.openTelemetry?.tracerProvider?.get("edge-telemetry")
                val span = tracer?.spanBuilder("app.crash")
                    ?.setAttribute("error", true)
                    ?.setAttribute("error.type", "crash")
                    ?.setAttribute("exception.type", throwable.javaClass.name)
                    ?.setAttribute("exception.message", throwable.message ?: "No message")
                    ?.setAttribute("exception.stacktrace", throwable.stackTraceToString())
                    ?.setAttribute("thread.name", thread.name)
                    ?.setAttribute("thread.id", thread.id.toString())
                    ?.setAttribute("service.name", config.serviceName)
                    ?.setAttribute("service.version", config.serviceVersion)
                    ?.setAttribute("crash.severity", "fatal")
                    ?.setAttribute("device.id", getDeviceId())
                    ?.startSpan()

                span?.recordException(throwable)

                // End the span
                span?.end()

                // Create a special "crash reporter" thread that won't die with the main thread
                val crashReporterThread = Thread {
                    try {
                        // Give the batch processor some time to export
                        Log.i(TAG, "Waiting for crash data to be exported")
                        Thread.sleep(2000) // Wait longer - 2 seconds
                        Log.i(TAG, "Crash reporter thread completed")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in crash reporter thread", e)
                    }
                }

                // Mark as daemon so it doesn't prevent app termination
                crashReporterThread.isDaemon = true
                crashReporterThread.name = "edge-telemetry-crash-reporter"
                crashReporterThread.start()

                try {
                    // Wait for the crash reporter thread to finish
                    crashReporterThread.join(3000) // Wait max 3 seconds
                } catch (e: Exception) {
                    Log.e(TAG, "Error waiting for crash reporter thread", e)
                }

                Log.i(TAG, "Crash data should be exported to collector")

                // Call the default handler after reporting
                defaultHandler?.uncaughtException(thread, throwable)
            } catch (e: Exception) {
                Log.e(TAG, "Error in custom crash handler", e)
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }

        Log.i(TAG, "Custom crash handler configured")
    }

    // Method to manually report a handled exception
    fun reportHandledException(throwable: Throwable, additionalAttributes: Map<String, String> = emptyMap()) {
        try {
            Log.d(TAG, "Reporting handled exception: ${throwable.message}")

            val tracer = openTelemetryRum?.openTelemetry?.tracerProvider?.get("edge-telemetry")

            // Create a builder for attributes
            val attributesBuilder = Attributes.builder()
                .put("error", true)
                .put("error.type", "handled_exception")
                .put("exception.type", throwable.javaClass.name)
                .put("exception.message", throwable.message ?: "No message")
                .put("exception.stacktrace", throwable.stackTraceToString())
                .put("service.name", config.serviceName)
                .put("service.version", config.serviceVersion)
                .put("crash.severity", "handled")
                .put("device.id", getDeviceId())

            // Add any additional attributes
            additionalAttributes.forEach { (key, value) ->
                attributesBuilder.put(key, value)
            }

            // Create and end the span
            val span = tracer?.spanBuilder("app.exception")
                ?.setAllAttributes(attributesBuilder.build())
                ?.startSpan()

            span?.recordException(throwable)
            span?.end()

            // Allow some time for the batch processor to send the data
            try {
                Thread.sleep(300)
            } catch (e: InterruptedException) {
                // Ignore
            }

            Log.d(TAG, "Handled exception reported successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to report handled exception", e)
        }
    }

    // Convenience method to test crash reporting
    fun testCrashReporting() {
        try {
            Log.i(TAG, "Testing exception reporting")
            throw RuntimeException("Test exception for EdgeTelemetry")
        } catch (e: Exception) {
            reportHandledException(e, mapOf("test" to "true", "purpose" to "testing_crash_reporting"))
            Log.i(TAG, "Test exception reported successfully")
        }
    }

    // Helper function to get a device identifier
    private fun getDeviceId(): String {
        return try {
            android.provider.Settings.Secure.getString(
                applicationContext.contentResolver,
                android.provider.Settings.Secure.ANDROID_ID
            ) ?: "unknown_device"
        } catch (e: Exception) {
            "unknown_device"
        }
    }
}