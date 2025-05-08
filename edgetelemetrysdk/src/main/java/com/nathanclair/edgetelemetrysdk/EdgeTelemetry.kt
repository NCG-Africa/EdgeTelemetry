package com.nathanclair.edgetelemetrysdk

import android.app.Application
import android.net.Uri
import android.util.Log
import io.opentelemetry.android.OpenTelemetryRum
import io.opentelemetry.android.OpenTelemetryRumBuilder
import io.opentelemetry.android.config.OtelRumConfig
import io.opentelemetry.android.features.diskbuffering.DiskBufferingConfig
import io.opentelemetry.android.instrumentation.AndroidInstrumentationLoader
import io.opentelemetry.android.instrumentation.anr.AnrInstrumentation
import io.opentelemetry.android.instrumentation.crash.CrashReporterInstrumentation
import io.opentelemetry.android.instrumentation.network.NetworkChangeInstrumentation
import io.opentelemetry.android.instrumentation.sessions.SessionInstrumentation
import io.opentelemetry.android.instrumentation.slowrendering.SlowRenderingInstrumentation
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.metrics.DoubleHistogram
import io.opentelemetry.api.metrics.LongCounter
import io.opentelemetry.api.metrics.LongUpDownCounter
import io.opentelemetry.api.metrics.Meter
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.exporter.otlp.http.logs.OtlpHttpLogRecordExporter
import io.opentelemetry.exporter.otlp.http.metrics.OtlpHttpMetricExporter
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter
import io.opentelemetry.instrumentation.library.okhttp.v3_0.OkHttpInstrumentation
import io.opentelemetry.sdk.logs.export.SimpleLogRecordProcessor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import java.io.File

private const val TAG = "EdgeTelemetry"

class EdgeTelemetry private constructor() {

    private var openTelemetryRum: OpenTelemetryRum? = null
    private var networkPerformanceMonitor: NetworkPerformanceMonitor? = null

    // Add metrics-related fields
    private var metricsMeter: Meter? = null
    private var appStartupTimeMetric: DoubleHistogram? = null
    private var networkRequestCounter: LongCounter? = null
    private var networkErrorCounter: LongCounter? = null
    private var memoryUsageGauge: LongUpDownCounter? = null
    private var screenViewCounter: LongCounter? = null

    companion object {
        @Volatile
        private var instance: EdgeTelemetry? = null

        /**
         * Get the singleton instance of EdgeTelemetry.
         */
        @JvmStatic
        fun getInstance(): EdgeTelemetry {
            return instance ?: synchronized(this) {
                instance ?: EdgeTelemetry().also { instance = it }
            }
        }
    }

    /**
     * Initialize the EdgeTelemetry SDK with default configuration.
     *
     * @param application The application context
     * @return The initialized SDK instance
     */
    fun initialize(application: Application): EdgeTelemetry {
        return initialize(application, EdgeTelemetryConfig.builder().build())
    }

    /**
     * Initialize the EdgeTelemetry SDK with custom endpoints.
     *
     * @param application The application context
     * @param tracesEndpoint The endpoint URL for traces
     * @param logsEndpoint The endpoint URL for logs
     * @return The initialized SDK instance
     */
    fun initialize(
        application: Application,
        tracesEndpoint: String,
        logsEndpoint: String
    ): EdgeTelemetry {
        val config = EdgeTelemetryConfig.builder()
            .setTracesEndpoint(tracesEndpoint)
            .setLogsEndpoint(logsEndpoint)
            .build()

        return initialize(application, config)
    }

    /**
     * Initialize the EdgeTelemetry SDK with custom endpoints including metrics.
     *
     * @param application The application context
     * @param tracesEndpoint The endpoint URL for traces
     * @param logsEndpoint The endpoint URL for logs
     * @param metricsEndpoint The endpoint URL for metrics
     * @return The initialized SDK instance
     */
    fun initialize(
        application: Application,
        tracesEndpoint: String,
        logsEndpoint: String,
        metricsEndpoint: String
    ): EdgeTelemetry {
        val config = EdgeTelemetryConfig.builder()
            .setTracesEndpoint(tracesEndpoint)
            .setLogsEndpoint(logsEndpoint)
            .setMetricsEndpoint(metricsEndpoint)
            .build()

        return initialize(application, config)
    }

    /**
     * Initialize the EdgeTelemetry SDK with a configuration.
     *
     * @param application The application context
     * @param config The configuration to use
     * @return The initialized SDK instance
     */
    fun initialize(application: Application, config: EdgeTelemetryConfig): EdgeTelemetry {
        Log.i(TAG, "Initializing EdgeTelemetry with custom configuration")

        try {
            // Configure HTTP instrumentation if enabled
            if (InstrumentationType.HTTP in config.enabledInstrumentations) {
                configureOkHttpInstrumentation()
            }

            // Create DiskBufferingConfig with more relaxed timing parameters
            val diskBufferingConfig = DiskBufferingConfig.create(
                enabled = false
            )

            // Configure OpenTelemetry with settings from config
            val otelConfig = OtelRumConfig()
                .setGlobalAttributes(createAttributes(config))
                .setDiskBufferingConfig(diskBufferingConfig)

            // Build OpenTelemetry with services enabled
            val openTelemetryRumBuilder: OpenTelemetryRumBuilder = OpenTelemetryRum.builder(application, otelConfig)
                .addSpanExporterCustomizer {
                    OtlpHttpSpanExporter.builder()
                        .setEndpoint(config.tracesEndpoint)
                        .build()
                }
                .addLogRecordExporterCustomizer {
                    OtlpHttpLogRecordExporter.builder()
                        .setEndpoint(config.logsEndpoint)
                        .build()
                }

            // Add metrics exporter if metrics are enabled
            if (InstrumentationType.METRICS in config.enabledInstrumentations) {
                // Add metric exporter
                openTelemetryRumBuilder.addMetricExporterCustomizer {
                    OtlpHttpMetricExporter.builder()
                        .setEndpoint(config.metricsEndpoint)
                        .build()
                }

                // Configure the meter provider
                openTelemetryRumBuilder.addMeterProviderCustomizer { meterProviderBuilder, application ->
                    // Return the builder with any customizations
                    meterProviderBuilder
                }

                Log.d(TAG, "Metrics collection initialized")
            }


            // Add instrumentations based on config
            addInstrumentations(openTelemetryRumBuilder, config.enabledInstrumentations)

            openTelemetryRum = openTelemetryRumBuilder.build()
            Log.d(TAG, "EdgeTelemetry initialized with session ID: ${openTelemetryRum?.rumSessionId}")

            // Initialize network performance monitor if enabled
            if (InstrumentationType.NETWORK_PERFORMANCE in config.enabledInstrumentations) {
                openTelemetryRum?.openTelemetry?.let { otel ->
                    networkPerformanceMonitor = NetworkPerformanceMonitor(otel)
                        .apply {
                            connectionQualityManager.initialize(application)
                        }
                    Log.d(TAG, "Network performance monitoring initialized")
                }
            }

            // Initialize metrics if enabled
            if (InstrumentationType.METRICS in config.enabledInstrumentations) {
                initializeMetrics()
                Log.d(TAG, "Metrics collection initialized")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error initializing EdgeTelemetry", e)
        }
        return this
    }

    /**
     * Configure OkHttp instrumentation using ByteBuddy agent.
     */
    private fun configureOkHttpInstrumentation() {
        try {
            // Get the OkHttp instrumentation instance
            val okHttpInstrumentation = AndroidInstrumentationLoader.getInstrumentation(OkHttpInstrumentation::class.java)
            // Apply configuration options
            okHttpInstrumentation?.apply {
                // Capture important HTTP request headers
                capturedRequestHeaders = listOf(
                    "content-type",
                    "content-length",
                    "user-agent"
                )

                // Capture important HTTP response headers
                capturedResponseHeaders = listOf(
                    "content-type",
                    "content-length",
                    "cache-control",
                    "server"
                )
                setEmitExperimentalHttpClientTelemetry(true)
            }

            Log.d(TAG, "OkHttp instrumentation configured")
        } catch (e: Exception) {
            Log.e(TAG, "Error configuring OkHttp instrumentation", e)
        }
    }

    /**
     * Create attributes from configuration.
     */
    private fun createAttributes(config: EdgeTelemetryConfig): Attributes {
        val builder = Attributes.builder()

        // Add app name
        builder.put(AttributeKey.stringKey("appName"), config.appName)

        // Add all additional attributes
        config.additionalAttributes.forEach { (key, value) ->
            builder.put(AttributeKey.stringKey(key), value)
        }

        // Add default device attributes
        builder.put(AttributeKey.stringKey("device.os.version"), android.os.Build.VERSION.RELEASE)
        builder.put(AttributeKey.stringKey("device.model"), android.os.Build.MODEL)

        return builder.build()
    }

    /**
     * Add instrumentations based on enabled types.
     */
    private fun addInstrumentations(
        builder: OpenTelemetryRumBuilder,
        enabledInstrumentations: Set<InstrumentationType>
    ) {
        if (InstrumentationType.SESSION in enabledInstrumentations) {
            builder.addInstrumentation(SessionInstrumentation())
        }

        if (InstrumentationType.ANR in enabledInstrumentations) {
            builder.addInstrumentation(AnrInstrumentation())
        }

        if (InstrumentationType.CRASH in enabledInstrumentations) {
            builder.addInstrumentation(CrashReporterInstrumentation())
        }

        if (InstrumentationType.NETWORK_CHANGE in enabledInstrumentations) {
            builder.addInstrumentation(NetworkChangeInstrumentation())
        }

        if (InstrumentationType.SLOW_RENDERING in enabledInstrumentations) {
            builder.addInstrumentation(SlowRenderingInstrumentation())
        }
    }

    /**
     * Initialize metrics collection.
     */
    private fun initializeMetrics() {
        openTelemetryRum?.openTelemetry?.let { otel ->
            // Create a meter for this SDK
            metricsMeter = otel.getMeter("com.nathanclair.edgetelemetrysdk")

            // Create some standard metrics
            metricsMeter?.let { meter ->
                // App startup time metric
                appStartupTimeMetric = meter.histogramBuilder("app.startup.time")
                    .setDescription("Time taken for app to start in milliseconds")
                    .setUnit("ms")
                    .build()

                // Network request counter
                networkRequestCounter = meter.counterBuilder("network.requests")
                    .setDescription("Number of network requests")
                    .build()

                // Network error counter
                networkErrorCounter = meter.counterBuilder("network.errors")
                    .setDescription("Number of network errors")
                    .build()

                // Memory usage gauge
                memoryUsageGauge = meter.upDownCounterBuilder("memory.usage")
                    .setDescription("Memory usage in bytes")
                    .setUnit("bytes")
                    .build()

                // Screen view counter
                screenViewCounter = meter.counterBuilder("screen.views")
                    .setDescription("Number of screen views")
                    .build()
            }
        }
    }

    /**
     * Get the OpenTelemetry instance for manual instrumentation.
     */
    fun getOpenTelemetry(): OpenTelemetry? {
        return openTelemetryRum?.openTelemetry
    }

    /**
     * Get the current RUM session ID.
     */
    fun getRumSessionId(): String? {
        return openTelemetryRum?.rumSessionId
    }

    /**
     * Get a tracer for creating spans.
     */
    fun getTracer(name: String): Tracer? {
        return openTelemetryRum?.openTelemetry?.getTracer(name)
    }

    /**
     * Get a meter for creating metrics.
     */
    fun getMeter(name: String): Meter? {
        return openTelemetryRum?.openTelemetry?.getMeter(name)
    }

    /**
     * Set the user ID for the current session.
     * Call this method when a user logs in.
     *
     * @param userId The unique identifier for the user
     */
    fun setUserId(userId: String) {
        // Log user identification event
        val tracer = getTracer("user")
        tracer?.spanBuilder("user_identified")?.apply {
            setAttribute(AttributeKey.stringKey("user.id"), userId)
            startSpan().end()
        }
    }

    /**
     * Track when users view a screen in the app.
     *
     * @param screenName The name of the screen being viewed
     */
    fun trackScreenView(screenName: String) {
        // Create span for screen view
        val tracer = getTracer("ui")
        tracer?.spanBuilder("screen_view")?.apply {
            setAttribute(AttributeKey.stringKey("screen.name"), screenName)
            startSpan().end()
        }

        // Also count screen views in metrics if enabled
        screenViewCounter?.add(1, Attributes.builder()
            .put("screen.name", screenName)
            .build())
    }

    /**
     * Record app startup time as a metric.
     *
     * @param startupTimeMs The time it took for app to start in milliseconds
     */
    fun recordAppStartupTime(startupTimeMs: Long) {
        appStartupTimeMetric?.record(startupTimeMs.toDouble())
        Log.d(TAG, "Recorded app startup time: $startupTimeMs ms")
    }

    /**
     * Record current memory usage.
     *
     * @param memoryUsageBytes Current memory usage in bytes
     */
    fun recordMemoryUsage(memoryUsageBytes: Long) {
        // Reset the previous value by getting the difference
        val runtime = Runtime.getRuntime()
        val usedMemory = runtime.totalMemory() - runtime.freeMemory()

        memoryUsageGauge?.add(memoryUsageBytes - usedMemory)
        Log.d(TAG, "Recorded memory usage: $memoryUsageBytes bytes")
    }

    /**
     * Track an error that occurred in the application.
     *
     * @param throwable The exception that was thrown
     */
    fun trackError(throwable: Throwable) {
        val tracer = getTracer("errors")
        val span = tracer?.spanBuilder("error")?.apply {
            setAttribute(AttributeKey.stringKey("error.type"), throwable.javaClass.name)
            setAttribute(AttributeKey.stringKey("error.message"), throwable.message ?: "")
        }?.startSpan()

        try {
            span?.recordException(throwable)

            // Also increment error counter in metrics if enabled
            val errorAttributes = Attributes.builder()
                .put("error.type", throwable.javaClass.name)
                .build()

            if (throwable.message?.contains("network", ignoreCase = true) == true ||
                throwable is java.net.UnknownHostException ||
                throwable is java.io.IOException) {
                networkErrorCounter?.add(1, errorAttributes)
            }
        } finally {
            span?.end()
        }
    }

    /**
     * Get the network performance monitor.
     * Use this to access enhanced network metrics and API performance statistics.
     */
    fun getNetworkPerformanceMonitor(): NetworkPerformanceMonitor? {
        return networkPerformanceMonitor
    }

    /**
     * Track a network request with performance metrics.
     * This can be used for tracking non-OkHttp network requests.
     *
     * @param url The request URL
     * @param method The HTTP method (GET, POST, etc.)
     * @param durationMs Duration of the request in milliseconds
     * @param requestSizeBytes Size of the request in bytes (0 if unknown)
     * @param responseSizeBytes Size of the response in bytes (0 if unknown)
     * @param statusCode HTTP status code (e.g., 200, 404, 500)
     * @param apiName Optional name to group requests by API
     */
    fun trackNetworkRequest(
        url: String,
        method: String,
        durationMs: Long,
        requestSizeBytes: Long = 0,
        responseSizeBytes: Long = 0,
        statusCode: Int,
        apiName: String? = null
    ) {
        val currentTimeMs = System.currentTimeMillis()
        val startTimeMs = currentTimeMs - durationMs

        // Record in network performance monitor
        networkPerformanceMonitor?.recordNetworkMetrics(
            url = url,
            method = method,
            startTimeMs = startTimeMs,
            endTimeMs = currentTimeMs,
            bytesSent = requestSizeBytes,
            bytesReceived = responseSizeBytes,
            statusCode = statusCode,
            apiName = apiName
        ) ?: Log.w(TAG, "Network performance monitor not initialized")

        // Also track in metrics if enabled
        networkRequestCounter?.add(1, Attributes.builder()
            .put("http.url", url)
            .put("http.method", method)
            .put("http.status_code", statusCode.toLong())
            .put("api.name", apiName ?: "unknown")
            .build())

        // If error status code, track as network error
        if (statusCode >= 400) {
            networkErrorCounter?.add(1, Attributes.builder()
                .put("http.url", url)
                .put("http.method", method)
                .put("http.status_code", statusCode.toLong())
                .put("api.name", apiName ?: "unknown")
                .build())
        }
    }

    /**
     * Create a custom counter metric.
     *
     * @param name The name of the counter
     * @param description Description of what this counter measures
     * @param unit Optional unit of measurement
     * @return A LongCounter that can be incremented
     */
    fun createCounter(name: String, description: String, unit: String = "1"): LongCounter? {
        return metricsMeter?.counterBuilder(name)
            ?.setDescription(description)
            ?.setUnit(unit)
            ?.build()
    }

    /**
     * Create a custom gauge metric (implemented as UpDownCounter).
     *
     * @param name The name of the gauge
     * @param description Description of what this gauge measures
     * @param unit Optional unit of measurement
     * @return A LongUpDownCounter that can be set to specific values
     */
    fun createGauge(name: String, description: String, unit: String): LongUpDownCounter? {
        return metricsMeter?.upDownCounterBuilder(name)
            ?.setDescription(description)
            ?.setUnit(unit)
            ?.build()
    }

    /**
     * Create a custom histogram metric.
     *
     * @param name The name of the histogram
     * @param description Description of what this histogram measures
     * @param unit Optional unit of measurement
     * @return A DoubleHistogram that can record measurements
     */
    fun createHistogram(name: String, description: String, unit: String): DoubleHistogram? {
        return metricsMeter?.histogramBuilder(name)
            ?.setDescription(description)
            ?.setUnit(unit)
            ?.build()
    }


    /**
     * Simulates a crash for testing crash reporting functionality.
     *
     * @param message Custom exception message (default: "Test crash from EdgeTelemetry")
     * @param actuallyThrow If true, throws the exception (causing a real crash),
     *                     if false, captures it as if it were a crash without actually crashing
     * @param exceptionType The type of exception to simulate (default: RuntimeException)
     */
    fun simulateCrash(
        message: String = "Test crash from EdgeTelemetry",
        actuallyThrow: Boolean = false,
        exceptionType: Class<out Throwable> = RuntimeException::class.java
    ): Throwable {
        Log.i(TAG, "Simulating crash with message: $message, actuallyThrow: $actuallyThrow")

        try {
            // Create an exception of the requested type with the provided message
            val constructor = exceptionType.getConstructor(String::class.java)
            val exception = constructor.newInstance(message)

            // Create a fake stack trace that's easy to identify
            val simulatedStackTrace = arrayOf(
                StackTraceElement(
                    "com.nathanclair.edgetelemetrysdk.CrashSimulator",
                    "simulatedCrashMethod",
                    "CrashSimulator.kt",
                    42
                ),
                StackTraceElement(
                    "com.nathanclair.edgetelemetrysdk.EdgeTelemetry",
                    "simulateCrash",
                    "EdgeTelemetry.kt",
                    123
                ),
                // Include the actual stack for more context
                *Thread.currentThread().stackTrace
            )

            exception.stackTrace = simulatedStackTrace

            if (actuallyThrow) {
                // Use a separate thread to throw the exception
                // This ensures it's uncaught and triggers the crash reporter
                Thread(Runnable {
                    Thread.currentThread().name = "EdgeTelemetry-CrashSimulator"
                    Thread.sleep(100) // Small delay to ensure thread is ready
                    throw exception
                }).start()
            } else {
                // For non-crashing simulation, we'll use our trackError method
                // This will at least record the error through our telemetry
                trackError(exception)

                // Also use a special handler thread to simulate an uncaught exception
                // This is more likely to trigger the crash instrumentation
                val handler = Thread.getDefaultUncaughtExceptionHandler()
                Thread {
                    try {
                        Thread.currentThread().name = "EdgeTelemetry-SimulatedCrash"
                        throw exception
                    } catch (e: Throwable) {
                        // Manually invoke the uncaught exception handler
                        // This is what typically triggers crash reporting
                        handler?.uncaughtException(Thread.currentThread(), e)
                    }
                }.start()
            }

            return exception
        } catch (e: Exception) {
            Log.e(TAG, "Error while simulating crash", e)
            // Return the original exception if we encountered an error
            return RuntimeException("Failed to simulate crash: ${e.message}", e)
        }
    }


    /**
     * Simulates a network request for testing network monitoring functionality.
     * This helps visualize what data would be captured by automatic network instrumentation.
     *
     * @param url The URL to simulate calling (default: "https://api.example.com/test")
     * @param method The HTTP method to simulate (default: "GET")
     * @param statusCode The HTTP status code to simulate (default: 200)
     * @param durationMs How long the simulated request should take (default: random between 100-2000ms)
     * @param responseSizeBytes Size of the simulated response (default: random between 1KB-1MB)
     * @param requestSizeBytes Size of the simulated request (default: random between 100B-10KB)
     * @param apiName Optional name to group requests by API (default: "test-api")
     * @param simulateLatency If true, actually waits for the duration to simulate latency (default: false)
     * @param includeConnectionQuality If true, includes current network connection quality (default: true)
     * @return A Map containing details about the simulated request and collected telemetry
     */
    fun simulateNetworkRequest(
        url: String = "https://api.example.com/test",
        method: String = "GET",
        statusCode: Int = 200,
        durationMs: Long = (100..2000).random().toLong(),
        responseSizeBytes: Long = (1024..(1024*1024)).random().toLong(),
        requestSizeBytes: Long = (100..(10*1024)).random().toLong(),
        apiName: String = "test-api",
        simulateLatency: Boolean = false,
        includeConnectionQuality: Boolean = true
    ): Map<String, Any> {
        Log.i(TAG, "Simulating network request to $url with method $method")

        val startTimeMs = System.currentTimeMillis()

        // If simulateLatency is true, sleep for the duration to mimic network latency
        if (simulateLatency && durationMs > 0) {
            try {
                Thread.sleep(durationMs)
            } catch (e: InterruptedException) {
                Log.w(TAG, "Sleep interrupted during network simulation", e)
            }
        }

        val endTimeMs = if (simulateLatency) System.currentTimeMillis() else startTimeMs + durationMs

        // Use our NetworkPerformanceMonitor to record the network metrics
        // This will create the appropriate spans and update API performance stats
        networkPerformanceMonitor?.recordNetworkMetrics(
            url = url,
            method = method,
            startTimeMs = startTimeMs,
            endTimeMs = endTimeMs,
            bytesSent = requestSizeBytes,
            bytesReceived = responseSizeBytes,
            statusCode = statusCode,
            apiName = apiName
        )

        // Also manually track the request with our standard tracking method
        // This ensures both tracking systems receive the data
        trackNetworkRequest(
            url = url,
            method = method,
            durationMs = durationMs,
            requestSizeBytes = requestSizeBytes,
            responseSizeBytes = responseSizeBytes,
            statusCode = statusCode,
            apiName = apiName
        )

        // Collect data about what was recorded for the simulation summary
        val host = try {
            Uri.parse(url).host ?: "unknown"
        } catch (e: Exception) {
            "unknown"
        }

        val path = try {
            Uri.parse(url).path ?: "/"
        } catch (e: Exception) {
            "/"
        }

        // Get connection quality data if requested and available
        val connectionQuality = if (includeConnectionQuality) {
            networkPerformanceMonitor?.getCurrentConnectionQuality()
        } else null

        // Get the API performance stats after this request
        val apiStats = if (apiName != null) {
            networkPerformanceMonitor?.getApiPerformanceStats(apiName)
        } else null

        // Build a simulation summary with the data that would be sent to the backend
        val simulationSummary = mutableMapOf<String, Any>(
            "request" to mapOf(
                "url" to url,
                "method" to method,
                "host" to host,
                "path" to path,
                "startTimeMs" to startTimeMs,
                "endTimeMs" to endTimeMs,
                "durationMs" to durationMs,
                "requestSizeBytes" to requestSizeBytes,
                "responseSizeBytes" to responseSizeBytes,
                "statusCode" to statusCode,
                "apiName" to (apiName ?: "unknown"),
                "isSlowRequest" to (durationMs > (networkPerformanceMonitor?.slowRequestThreshold ?: 1500))
            )
        )

        // Include connection quality if available
        connectionQuality?.let {
            simulationSummary["connectionQuality"] = mapOf(
                "type" to it.connectionType,
                "signalStrength" to it.signalStrength
            )
        }

        // Include API stats if available
        apiStats?.let {
            simulationSummary["apiStats"] = mapOf(
                "apiName" to it.apiName,
                "requestCount" to it.requestCount,
                "successCount" to it.successCount,
                "failureCount" to it.failureCount,
                "totalDurationMs" to it.totalDurationMs,
                "minDurationMs" to it.minDurationMs,
                "maxDurationMs" to it.maxDurationMs,
                "avgDurationMs" to it.getAverageDurationMs(),
                "successRate" to it.getSuccessRate()
            )
        }

        // Include telemetry attributes that would be in the OpenTelemetry span
        val telemetryAttributes = mutableMapOf(
            "http.url" to url,
            "http.method" to method,
            "http.status_code" to statusCode,
            "network.duration_ms" to durationMs,
            "network.bytes_sent" to requestSizeBytes,
            "network.bytes_received" to responseSizeBytes,
            "network.host" to host,
            "network.path" to path,
            "simulated" to true
        )

        // Add slow request flag if applicable
        if (durationMs > (networkPerformanceMonitor?.slowRequestThreshold ?: 1500)) {
            telemetryAttributes["network.slow_request"] = true
            telemetryAttributes["network.slow_threshold_ms"] = networkPerformanceMonitor?.slowRequestThreshold ?: 1500
        }

        // Add connection quality if available
        connectionQuality?.let {
            telemetryAttributes["network.connection_type"] = it.connectionType
            telemetryAttributes["network.signal_strength"] = it.signalStrength
        }

        // Add API name if available
        apiName?.let {
            telemetryAttributes["api.name"] = it
        }

        simulationSummary["telemetryAttributes"] = telemetryAttributes

        // Log a detailed summary of what would be sent to the backend
        Log.d(TAG, "Network simulation complete. Data that would be sent to backend: $simulationSummary")

        return simulationSummary
    }
}