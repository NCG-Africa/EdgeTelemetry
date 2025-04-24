package com.nathanclair.edgetelemetrysdk.core

import android.app.Activity
import android.app.Application
import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.NavController
import com.nathanclair.edgetelemetrysdk.exporters.EdgeTelemetryCrashReporter
import com.nathanclair.edgetelemetrysdk.exporters.EdgeTelemetryUser
import com.nathanclair.edgetelemetrysdk.util.CompatUtil
import io.opentelemetry.android.OpenTelemetryRum
import io.opentelemetry.android.OpenTelemetryRumBuilder
import io.opentelemetry.android.config.OtelRumConfig
import io.opentelemetry.android.features.diskbuffering.DiskBufferingConfig
import io.opentelemetry.android.instrumentation.AndroidInstrumentationLoader
import io.opentelemetry.android.instrumentation.activity.ActivityLifecycleInstrumentation
import io.opentelemetry.android.instrumentation.common.ScreenNameExtractor
import io.opentelemetry.android.instrumentation.network.NetworkAttributesExtractor
import io.opentelemetry.android.instrumentation.sessions.SessionInstrumentation
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.AttributesBuilder
import io.opentelemetry.api.logs.LogRecordBuilder
import io.opentelemetry.api.logs.Logger
import io.opentelemetry.api.logs.Severity
import io.opentelemetry.api.metrics.LongCounter
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Context
import io.opentelemetry.exporter.otlp.http.logs.OtlpHttpLogRecordExporter
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter
import io.opentelemetry.exporter.otlp.logs.OtlpGrpcLogRecordExporter
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor
import io.opentelemetry.instrumentation.library.okhttp.v3_0.OkHttpInstrumentation
import okhttp3.Interceptor
import okhttp3.Response
import java.util.concurrent.atomic.AtomicBoolean


/**
 * EdgeTelemetry is a wrapper around OpenTelemetryRum that provides a simplified
 * interface for integrating telemetry into Android applications.
 */
class EdgeTelemetry private constructor() {
    companion object {

        private const val TAG = "EdgeTelemetry"
        private var instance: EdgeTelemetry? = null
        private var initialized = AtomicBoolean(false)
        var rum: OpenTelemetryRum? = null
        var networkAttributesExtractors: MutableList<NetworkAttributesExtractor> = mutableListOf()

        /**
         * Initialize EdgeTelemetry with the application context
         */
        @JvmStatic
        fun initialize(application: Application) {
            initialize(application, EdgeTelemetryConfig())
        }

        /**
         * Initialize EdgeTelemetry with the application context and custom configuration
         */
        @JvmStatic
        fun initialize(application: Application, config: EdgeTelemetryConfig) {
            if (initialized.getAndSet(true)) {
                Log.w(TAG, "EdgeTelemetry already initialized, ignoring this call")
                return
            }

            try {
                Log.i(TAG, "Initializing EdgeTelemetry SDK")

                // Install crash reporter early in the initialization process
                EdgeTelemetryCrashReporter.install()

                val otelConfig = buildOtelConfig(config)
                val builder = OpenTelemetryRum.builder(application, otelConfig)

                // Configure exporters based on the provided configuration
                setupExporters(builder, config)

                // Configure OkHttp instrumentation with default settings
                configureDefaultOkHttpInstrumentation()


                // Add session instrumentation if needed
                if (config.enableSessionInstrumentation) {
                    builder.addInstrumentation(SessionInstrumentation())
                }

                // Add any custom instrumentations
                config.instrumentations.forEach { instrumentation ->
                    builder.addInstrumentation(instrumentation)
                }

                // Configure activity tracking if needed
                if (config.trackScreenViews) {
                    configureActivityTracking(builder, config)
                }

                // Build the OpenTelemetryRum instance
                rum = builder.build()

                if (rum != null) {

                    Log.d(TAG, "EdgeTelemetry initialized successfully. Session ID: ${rum!!.rumSessionId}")
                    instance = EdgeTelemetry()

                    // Re-apply user identification if it was set before initialization
                    val existingUserId = EdgeTelemetryUser.getUserId()
                    val existingAttributes = EdgeTelemetryUser.getUserAttributes()
                    if (existingUserId != null) {
                        EdgeTelemetryUser.identify(existingUserId, existingAttributes)
                    }

                } else {
                    Log.e(TAG, "Failed to initialize EdgeTelemetry: RUM instance is null")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize EdgeTelemetry", e)
                initialized.set(false)
            }
        }

        private fun buildOtelConfig(config: EdgeTelemetryConfig): OtelRumConfig {
            val otelConfig = OtelRumConfig()

            // Set global attributes explicitly with the Attributes overload
            val attributes = config.globalAttributes
            otelConfig.setGlobalAttributes(attributes)

            // Configure disk buffering
            val diskBufferingConfig = DiskBufferingConfig(
                enabled = config.diskBuffering,
                maxCacheSize = config.maxDiskBufferSize,
                debugEnabled = config.debugMode
            )
            otelConfig.setDiskBufferingConfig(diskBufferingConfig)

            // Apply other configuration settings
            if (!config.includeNetworkAttributes) {
                otelConfig.disableNetworkAttributes()
            }

            if (!config.includeScreenAttributes) {
                otelConfig.disableScreenAttributes()
            }

            if (!config.enableSdkInitEvents) {
                otelConfig.disableSdkInitializationEvents()
            }

            // Configure ANR detection - enabled by default
            if (config.enableAnrDetection) {
               // ANR detection is enabled by default in the Android agent
               // We can customize the ANR detection threshold if needed
               CompatUtil.setAnrDetectionInterval(otelConfig, config.anrDetectionIntervalMs)
               CompatUtil.setAnrDetectionThreshold(otelConfig, config.anrDetectionThresholdMs)
           }

           // Configure Slow Rendering detection - enabled by default
           if (config.enableSlowRenderingDetection) {
               // Slow rendering detection is enabled by default in the Android agent
               // We can customize the detection interval if needed
               CompatUtil.setSlowRenderingDetectionPollInterval(otelConfig, config.slowRenderingDetectionPollIntervalMs)
           }

            return otelConfig
        }

        private fun setupExporters(builder: OpenTelemetryRumBuilder, config: EdgeTelemetryConfig) {
            when (config.transportType) {
                TransportType.HTTP -> {
                    // Configure HTTP exporters
                    builder.addSpanExporterCustomizer {
                        OtlpHttpSpanExporter.builder()
                            .setEndpoint(config.tracesEndpoint)
                            .apply { config.headers.forEach { (key, value) -> addHeader(key, value) } }
                            .build()
                    }

                    builder.addLogRecordExporterCustomizer {
                        OtlpHttpLogRecordExporter.builder()
                            .setEndpoint(config.logsEndpoint)
                            .apply { config.headers.forEach { (key, value) -> addHeader(key, value) } }
                            .build()
                    }
                }

                TransportType.GRPC -> {
                    // Configure gRPC exporters
                    builder.addSpanExporterCustomizer {
                        OtlpGrpcSpanExporter.builder()
                            .setEndpoint(config.tracesEndpoint)
                            .apply { config.headers.forEach { (key, value) -> addHeader(key, value) } }
                            .build()
                    }

                    builder.addLogRecordExporterCustomizer {
                        OtlpGrpcLogRecordExporter.builder()
                            .setEndpoint(config.logsEndpoint)
                            .apply { config.headers.forEach { (key, value) -> addHeader(key, value) } }
                            .build()
                    }
                }
            }
        }

        /**
         * Get a tracer instance with the provided name
         */
        @JvmStatic
        fun getTracer(name: String): Tracer? {
            return rum?.openTelemetry?.tracerProvider?.get(name)
        }

        /**
         * Get a counter for metrics with the provided name
         */
        @JvmStatic
        fun getCounter(name: String, scope: String = "edge.telemetry"): LongCounter? {
            return rum?.openTelemetry?.meterProvider?.get(scope)?.counterBuilder(name)?.build()
        }

        /**
         * Create an event builder for logging with the provided scope and event name
         */
        @JvmStatic
        fun createEventBuilder(scopeName: String, eventName: String): LogRecordBuilder? {
            val logger = rum?.openTelemetry?.logsBridge?.loggerBuilder(scopeName)?.build()

            // Try to use ExtendedLogRecordBuilder if available
            try {
                val builder = logger?.logRecordBuilder()
                // Check if the builder is an ExtendedLogRecordBuilder
                if (builder is io.opentelemetry.api.incubator.logs.ExtendedLogRecordBuilder) {
                    return builder.setEventName(eventName)
                }

                // If not, use standard LogRecordBuilder with event name as attribute
                return builder?.setAttribute(AttributeKey.stringKey("event.name"), eventName)
            } catch (e: Exception) {
                Log.w(TAG, "Error creating event builder: ${e.message}")
                return logger?.logRecordBuilder()
            }
        }

        /**
         * Get the current RUM session ID
         */
        @JvmStatic
        fun getSessionId(): String? {
            return rum?.rumSessionId
        }

        /**
         * Get a logger instance with the provided name
         */
        @JvmStatic
        fun getLogger(name: String): Logger? {
            return rum?.openTelemetry?.logsBridge?.loggerBuilder(name)?.build()
        }

        /**
         * Get the current active span, or null if no span is active
         */
        @JvmStatic
        fun getCurrentSpan(): Span? {
            return Span.current()
        }

        /**
         * Check if EdgeTelemetry is initialized
         */
        @JvmStatic
        fun isInitialized(): Boolean {
            return initialized.get() && rum != null
        }

        /**
         * Configure OkHttp instrumentation
         * This must be called BEFORE initializing EdgeTelemetry
         */
        @JvmStatic
        fun configureOkHttpInstrumentation(configure: (OkHttpInstrumentation) -> Unit) {
            val instrumentation = AndroidInstrumentationLoader.getInstrumentation(OkHttpInstrumentation::class.java)
            if (instrumentation != null) {
                configure(instrumentation)
            } else {
                Log.w(TAG, "OkHttp instrumentation not found. Make sure you've added the correct dependencies.")
            }
        }

        // Inside your EdgeTelemetry class, add this method to automatically configure OkHttp instrumentation
        private fun configureDefaultOkHttpInstrumentation() {
            try {
                val okHttpInstrumentation = AndroidInstrumentationLoader.getInstrumentation(OkHttpInstrumentation::class.java)
                if (okHttpInstrumentation != null) {
                    Log.d(TAG, "Configuring default OkHttp instrumentation")

                    // Use the correct types for the AttributesExtractor
                    okHttpInstrumentation.addAttributesExtractor(object :
                        AttributesExtractor<Interceptor.Chain, Response> {

                        override fun onStart(attributes: AttributesBuilder,
                                             parentContext: Context,
                                             chain: Interceptor.Chain) {
                            // Extract attributes from the request
                            val request = chain.request()
                            attributes.put(
                                AttributeKey.stringKey("http.request.url"),
                                request.url.toString())

                            // Add user attributes to network requests
                            val userId = EdgeTelemetryUser.getUserId()
                            val userAttrs = EdgeTelemetryUser.getUserAttributes()

                            if (userId != null) {
                                attributes.put(AttributeKey.stringKey("user.id"), userId)
                            }

                            userAttrs.forEach { (key, value) ->
                                attributes.put(AttributeKey.stringKey("user.$key"), value)
                            }
                        }

                        override fun onEnd(attributes: AttributesBuilder,
                                           context: Context,
                                           chain: Interceptor.Chain,
                                           response: okhttp3.Response?,
                                           error: Throwable?) {
                            // Extract attributes from the response
                            if (response != null) {
                                attributes.put(io.opentelemetry.api.common.AttributeKey.longKey("http.response.status_code"),
                                    response.code.toLong())
                            }

                            // Extract error information if available
                            if (error != null) {
                                attributes.put(
                                    AttributeKey.stringKey("error.type"),
                                    error.javaClass.name)
                                error.message?.let { errorMsg ->
                                    attributes.put(
                                        AttributeKey.stringKey("error.message"),
                                        errorMsg)
                                }
                            }
                        }
                    })

                    // Configure headers to capture
                    okHttpInstrumentation.capturedRequestHeaders.add("User-Agent")
                    okHttpInstrumentation.capturedResponseHeaders.add("Content-Type")
                    okHttpInstrumentation.capturedResponseHeaders.add("Content-Length")

                    Log.d(TAG, "Default OkHttp instrumentation configured successfully")
                } else {
                    Log.w(TAG, "OkHttp instrumentation not found. Network request details may be limited.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error configuring default OkHttp instrumentation", e)
            }
        }

        private fun configureActivityTracking(builder: OpenTelemetryRumBuilder, config: EdgeTelemetryConfig) {
            if (config.trackScreenViews) {
                // The OpenTelemetry Android agent already tracks activities, but we can enhance this
                try {
                    val activityInstrumentation = AndroidInstrumentationLoader.getInstrumentation(
                        ActivityLifecycleInstrumentation::class.java
                    )

                    activityInstrumentation?.setScreenNameExtractor(object : ScreenNameExtractor {
                        override fun extract(activity: Activity): String {
                            return "${activity.javaClass.simpleName}Screen"
                        }

                        override fun extract(fragment: Fragment?): String {
                            return fragment?.javaClass?.simpleName?.plus(" Screen") ?: "Unknown Screen"
                        }
                    })

                    Log.d(TAG, "Activity tracking configured successfully")
                } catch (e: Exception) {
                    Log.w(TAG, "Could not configure activity tracking: ${e.message}")
                }
            }
        }

        /**
         * Track a user interaction such as a button click
         */
        @JvmStatic
        fun trackUserInteraction(interactionType: String, elementId: String, additionalAttributes: Map<String, String> = emptyMap()) {
            val tracer = getTracer("user.interactions")

            tracer?.spanBuilder("user.$interactionType")?.apply {
                setAttribute("interaction.type", interactionType)
                setAttribute("element.id", elementId)

                // Add any additional attributes
                additionalAttributes.forEach { (key, value) ->
                    setAttribute(key, value)
                }

                // Create the span and end it explicitly
                val span = startSpan()
                try {
                    // Span is now active for the current context
                    // You can add any additional operations here if needed
                } finally {
                    span.end()
                }
            }

            // Also log this as an event
            val logger = getLogger("user.interactions")
            logger?.logRecordBuilder()
                ?.setSeverity(Severity.INFO)
                ?.setBody("User $interactionType on $elementId")
                ?.setAttribute("interaction.type", interactionType)
                ?.setAttribute("element.id", elementId)
                ?.also { builder ->
                    additionalAttributes.forEach { (key, value) ->
                        builder.setAttribute(key, value)
                    }
                }
                ?.emit()
        }

        /**
         * Automatically track clicks on all child views of the provided view
         */
        @JvmStatic
        fun trackViewClicks(rootView: View) {
            try {
                if (rootView is ViewGroup) {
                    for (i in 0 until rootView.childCount) {
                        val childView = rootView.getChildAt(i)

                        // Recursively track all child views
                        trackViewClicks(childView)
                    }
                }

                // Define a tag ID to mark tracked views
                val tagKey = "edge_telemetry_tracked".hashCode()

                // Check if we've already tracked this view
                if (rootView.getTag(tagKey) != true) {
                    // Add our telemetry click listener, which will call any existing listener
                    val existingListener = rootView.hasOnClickListeners()

                    if (existingListener) {
                        // We can't access the existing listener directly, so we'll create a new one
                        // that wraps our telemetry tracking
                        val newListener = View.OnClickListener { view ->
                            // Track the interaction first
                            val resourceId = try {
                                view.context.resources.getResourceEntryName(view.id)
                            } catch (e: Exception) {
                                "view_${view.id}"
                            }

                            trackUserInteraction("click", resourceId)

                            // Note: We can't call the existing listener directly since we can't access it
                            // This means clicks might behave differently after tracking is applied
                            // if there was an existing listener
                        }

                        rootView.setOnClickListener(newListener)
                    } else {
                        // No existing listener, just add our tracking listener
                        rootView.setOnClickListener { view ->
                            val resourceId = try {
                                view.context.resources.getResourceEntryName(view.id)
                            } catch (e: Exception) {
                                "view_${view.id}"
                            }

                            trackUserInteraction("click", resourceId)
                        }
                    }

                    // Mark this view as tracked
                    rootView.setTag(tagKey, true)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error setting up click tracking: ${e.message}")
            }
        }

        /**
         * Track navigation events with the Navigation Component
         */
        @JvmStatic
        fun trackNavigation(navController: NavController) {
            navController.addOnDestinationChangedListener { _, destination, arguments ->
                val destinationName = destination.label?.toString() ?: destination.id.toString()

                val tracer = getTracer("navigation")
                tracer?.spanBuilder("navigation.change")
                    ?.setAttribute("navigation.destination", destinationName)
                    ?.setAttribute("navigation.id", destination.id.toString())
                    ?.also { builder ->
                        // Add user attributes
                        val userId = EdgeTelemetryUser.getUserId()
                        val userAttrs = EdgeTelemetryUser.getUserAttributes()

                        if (userId != null) {
                            builder.setAttribute("user.id", userId)
                        }

                        userAttrs.forEach { (key, value) ->
                            builder.setAttribute("user.$key", value)
                        }
                    }
                    ?.startSpan()
                    ?.end()

                // Also log this as an event
                val eventBuilder = createEventBuilder("navigation", "navigation.destination_changed")
                eventBuilder?.setAttribute("navigation.destination", destinationName)
                    ?.setAttribute("navigation.id", destination.id.toString())
                    ?.also { builder ->
                        // Add user attributes
                        val userId = EdgeTelemetryUser.getUserId()
                        val userAttrs = EdgeTelemetryUser.getUserAttributes()

                        if (userId != null) {
                            builder.setAttribute("user.id", userId)
                        }

                        userAttrs.forEach { (key, value) ->
                            builder.setAttribute("user.$key", value)
                        }
                    }
                    ?.emit()
            }
        }


        /**
         * Identify the current user for telemetry data
         */
        @JvmStatic
        fun identifyUser(userId: String, attributes: Map<String, String> = emptyMap()) {
            EdgeTelemetryUser.identify(userId, attributes)
        }

        /**
         * Clear the current user identification
         */
        @JvmStatic
        fun clearUser() {
            EdgeTelemetryUser.clearUser()
        }

    }
}