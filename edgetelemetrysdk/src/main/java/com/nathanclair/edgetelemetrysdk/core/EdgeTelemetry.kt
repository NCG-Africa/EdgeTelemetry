package com.nathanclair.edgetelemetrysdk.core

import android.app.Application
import android.content.Context
import io.opentelemetry.android.OpenTelemetryRum
import io.opentelemetry.android.config.OtelRumConfig
import io.opentelemetry.android.instrumentation.crash.CrashDetails
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.common.AttributesBuilder
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor


class EdgeTelemetry private constructor(
    private val applicationContext: Context,
    private val collectionUrl: String,
    private val config: EdgeTelemetryConfig,
    private var crashAttributesExtractor: AttributesExtractor<CrashDetails, Void>? = null
) {
    companion object {

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
        setupCrashReporting()
        //setupAnrDetection()
        //setupNetworkChangeDetection()
        //setupActivityLifecycleInstrumentation()
        //setupFragmentLifecycleMonitoring()
        //setupSlowRenderDetection()
        //setupOfflineBuffering()
        setupOtelSdk(collectionUrl)
    }

    private fun setupOtelSdk(collectionUrl: String) {
        // Create configuration for OpenTelemetry RUM
        val config = OtelRumConfig().apply {
            // Set global attributes from our configuration
            setGlobalAttributes(
                Attributes.builder()
                    .put("sdk.name", "EdgeTelemetry")
                    .put("sdk.version", "0.1.0")
                    .build()
            )
        }

        // Build OpenTelemetryRum instance
        val openTelemetryRumBuilder = OpenTelemetryRum.builder(applicationContext as Application, config).apply {
            // Add OTLP exporter for the collection URL
            addTracerProviderCustomizer { tracerProviderBuilder, _ ->
                // Create OTLP exporter with the provided collection URL
                val otlpExporter = OtlpGrpcSpanExporter.builder()
                    .setEndpoint(collectionUrl)
                    .build()

                // Create batch processor for efficient sending of spans
                val batchSpanProcessor = BatchSpanProcessor.builder(otlpExporter).build()

                // Add the processor to the tracer provider
                tracerProviderBuilder.addSpanProcessor(batchSpanProcessor)
            }
        }

        // Build and store the OpenTelemetry instance
        openTelemetryRum = openTelemetryRumBuilder.build()

        // Log successful initialization
        android.util.Log.i("EdgeTelemetry", "EdgeTelemetry SDK successfully initialized with endpoint: $collectionUrl")
    }


    private fun setupCrashReporting() {
        // If crash reporting is disabled in the config, exit early
        if (!config.enableCrashReporting) {
            android.util.Log.d("EdgeTelemetry", "Crash reporting is disabled")
            return
        }

        // Store the previous default handler
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

        val customExceptionHandler = Thread.UncaughtExceptionHandler { thread, throwable ->
            try {
                // Log the crash details
                android.util.Log.e("EdgeTelemetry", "Uncaught Exception in thread: ${thread.name}", throwable)

                // Create a span for the crash
                val tracer = openTelemetryRum?.openTelemetry?.tracerProvider?.get("edge-telemetry")

                val crashSpan = tracer?.spanBuilder("uncaught_exception")
                    ?.setAttribute("error", true)
                    ?.setAttribute("thread.name", thread.name)
                    ?.setAttribute("exception.type", throwable.javaClass.name)
                    ?.setAttribute("exception.message", throwable.message ?: "No message")
                    ?.setAttribute("device.id", getDeviceId())
                    ?.setAttribute("service.name", config.serviceName)
                    ?.setAttribute("service.version", config.serviceVersion)
                    ?.startSpan()

                // Record the exception
                crashSpan?.recordException(throwable)

                // End the span
                crashSpan?.end()

                // Call the previous default handler
                defaultHandler?.uncaughtException(thread, throwable)
            } catch (e: Exception) {
                android.util.Log.e("EdgeTelemetry", "Error in crash reporting", e)
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }

        // Set the new default uncaught exception handler
        Thread.setDefaultUncaughtExceptionHandler(customExceptionHandler)

        android.util.Log.i("EdgeTelemetry", "Crash reporting handler configured")
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

    // Helper method to create resource information
    private fun createResource(): Resource {
        return Resource.getDefault().toBuilder()
            .put("service.name", config.serviceName)
            .put("service.version", config.serviceVersion)
            .put("deployment.environment", config.environment)
            .put("telemetry.sdk.name", "edge-telemetry")
            .put("telemetry.sdk.language", "kotlin")
            .put("telemetry.sdk.platform", "android")
            .build()
    }

}