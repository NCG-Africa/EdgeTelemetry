package com.nathanclair.edgetelemetrysdk

import android.app.Application
import android.content.Context
import com.nathanclair.edgetelemetrysdk.model.EdgeConfig
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import io.opentelemetry.context.propagation.ContextPropagators
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor
import io.opentelemetry.semconv.ResourceAttributes
import okhttp3.Interceptor


class EdgeTelemetryImpl(
    context: Context,
    config: EdgeConfig
): EdgeTelemetry {
    private var openTelemetry: OpenTelemetry
    private var networkMonitoringEnabled = false
    private val networkInterceptor: EdgeNetworkInterceptor
    private var activityLifecycleCallbacks: EdgeActivityLifecycleCallbacks? = null


    init {
        // Initialize OpenTelemetry with the config
        val resource = Resource.getDefault()
            .merge(Resource.create(
                Attributes.of(
                ResourceAttributes.SERVICE_NAME, config.serviceName,
                ResourceAttributes.SERVICE_VERSION, getAppVersion(context)
            )))

        val spanExporter = OtlpGrpcSpanExporter.builder()
            .setEndpoint(config.collectorUrl)
            .build()

        val tracerProvider = SdkTracerProvider.builder()
            .setResource(resource)
            .addSpanProcessor(BatchSpanProcessor.builder(spanExporter).build())
            .build()

        openTelemetry = OpenTelemetrySdk.builder()
            .setTracerProvider(tracerProvider)
            .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
            .build()

        // Initialize the network interceptor
        networkInterceptor = EdgeNetworkInterceptor(openTelemetry)
    }

    private fun getAppVersion(context: Context): String {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        } catch (e: Exception) {
            "unknown"
        }
    }

    override fun enableNetworkMonitoring(enabled: Boolean) {
        networkMonitoringEnabled = enabled
    }

    override fun createSpan(name: String): EdgeSpan {
        return EdgeSpanImpl(name, openTelemetry)
    }

    override fun trackUserInteraction(
        interactionType: String,
        elementId: String,
        additionalAttributes: Map<String, String>
    ) {
        if (!networkMonitoringEnabled) {
            // Skip if monitoring is disabled
            return
        }

        val span = createSpan("user_interaction.$interactionType")
            .setAttribute("interaction.type", interactionType)
            .setAttribute("interaction.element_id", elementId)

        // Add any additional attributes
        additionalAttributes.forEach { (key, value) ->
            span.setAttribute(key, value)
        }

        // Start and end the span (for discrete events like clicks)
        span.start().end()
    }

    override fun enableActivityTracking(application: Application) {
        if (activityLifecycleCallbacks == null) {
            activityLifecycleCallbacks = EdgeActivityLifecycleCallbacks(this)
            application.registerActivityLifecycleCallbacks(activityLifecycleCallbacks)
        }
    }

    override fun getNetworkInterceptor(): okhttp3.Interceptor {
        if (!networkMonitoringEnabled) {
            enableNetworkMonitoring(true)
        }
        return networkInterceptor
    }



    fun shutdownImpl() {
        if (openTelemetry is OpenTelemetrySdk) {
            (openTelemetry as OpenTelemetrySdk).close()
        }
    }
}