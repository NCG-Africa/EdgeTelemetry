package com.nathanclair.edgetelemetrysdk

import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.telephony.TelephonyManager
import android.util.Log
import io.opentelemetry.api.OpenTelemetry

private const val TAG = "Edge | NetworkPerformanceMonitor"


/**
 * NetworkPerformanceMonitor provides enhanced metrics for network performance.
 * This complements the automatic OkHttp instrumentation by adding higher-level metrics.
 */
class NetworkPerformanceMonitor(private val openTelemetry: OpenTelemetry) {

    private val tracer = openTelemetry.getTracer("network-performance")
    private val apiPerformanceTracker = mutableMapOf<String, ApiPerformanceStats>()

    // Track threshold for slow network requests (in milliseconds)
    private var slowRequestThreshold = 1500L
    val connectionQualityManager = ConnectionQualityManager()

    /**
     * Set the threshold for what is considered a slow network request.
     *
     * @param thresholdMs Time in milliseconds
     */
    fun setSlowRequestThreshold(thresholdMs: Long) {
        slowRequestThreshold = thresholdMs
    }

    /**
     * Record detailed metrics for a network request.
     * Call this method to manually track network performance for non-OkHttp requests
     * or to add additional metrics to OkHttp requests.
     *
     * @param url The request URL
     * @param method The HTTP method
     * @param startTimeMs The request start time in milliseconds
     * @param endTimeMs The request end time in milliseconds
     * @param bytesSent The number of bytes sent
     * @param bytesReceived The number of bytes received
     * @param statusCode The HTTP status code
     * @param apiName Optional API name for grouping metrics (e.g., "login-api", "product-service")
     */
    fun recordNetworkMetrics(
        url: String,
        method: String,
        startTimeMs: Long,
        endTimeMs: Long,
        bytesSent: Long,
        bytesReceived: Long,
        statusCode: Int,
        apiName: String? = null
    ) {
        val duration = endTimeMs - startTimeMs
        val host = extractHost(url)
        val path = extractPath(url)

        // Create span for this network operation
        val span = tracer.spanBuilder("network-request")
            .setAttribute("http.url", url)
            .setAttribute("http.method", method)
            .setAttribute("http.status_code", statusCode.toLong())
            .setAttribute("network.duration_ms", duration)
            .setAttribute("network.bytes_sent", bytesSent)
            .setAttribute("network.bytes_received", bytesReceived)
            .setAttribute("network.host", host)
            .setAttribute("network.path", path)

        // Add API name if provided
        apiName?.let {
            span.setAttribute("api.name", it)
        }

        // Check if this was a slow request
        if (duration > slowRequestThreshold) {
            span.setAttribute("network.slow_request", true)
            span.setAttribute("network.slow_threshold_ms", slowRequestThreshold)
        }

        // Add connection quality metrics
        val connectionQuality = connectionQualityManager.getCurrentQuality()
        span.setAttribute("network.connection_type", connectionQuality.connectionType)
        span.setAttribute("network.signal_strength", connectionQuality.signalStrength.toLong())

        // Start and end the span immediately since we already have all the data
        span.startSpan().end()

        // Update API performance tracking
        apiName?.let {
            updateApiPerformance(it, duration, statusCode == 200)
        }
    }

    /**
     * Get a summary of performance metrics for a specific API.
     *
     * @param apiName The name of the API
     * @return API performance statistics or null if no data available
     */
    fun getApiPerformanceStats(apiName: String): ApiPerformanceStats? {
        return apiPerformanceTracker[apiName]
    }

    /**
     * Reset performance tracking for all APIs.
     */
    fun resetApiPerformanceStats() {
        apiPerformanceTracker.clear()
    }

    /**
     * Get the current connection quality.
     *
     * @return Current connection quality information
     */
    fun getCurrentConnectionQuality(): ConnectionQuality {
        return connectionQualityManager.getCurrentQuality()
    }

    private fun extractHost(url: String): String {
        return try {
            val uri = Uri.parse(url)
            uri.host ?: "unknown"
        } catch (e: Exception) {
            "unknown"
        }
    }

    private fun extractPath(url: String): String {
        return try {
            val uri = Uri.parse(url)
            uri.path ?: "/"
        } catch (e: Exception) {
            "/"
        }
    }

    private fun updateApiPerformance(apiName: String, durationMs: Long, isSuccess: Boolean) {
        val stats = apiPerformanceTracker.getOrPut(apiName) { ApiPerformanceStats(apiName) }
        stats.addRequest(durationMs, isSuccess)
    }

    /**
     * Class for tracking API performance statistics over time.
     */
    data class ApiPerformanceStats(
        val apiName: String,
        var requestCount: Int = 0,
        var successCount: Int = 0,
        var failureCount: Int = 0,
        var totalDurationMs: Long = 0,
        var minDurationMs: Long = Long.MAX_VALUE,
        var maxDurationMs: Long = 0
    ) {
        fun addRequest(durationMs: Long, isSuccess: Boolean) {
            requestCount++
            totalDurationMs += durationMs

            if (durationMs < minDurationMs) {
                minDurationMs = durationMs
            }

            if (durationMs > maxDurationMs) {
                maxDurationMs = durationMs
            }

            if (isSuccess) {
                successCount++
            } else {
                failureCount++
            }
        }

        fun getAverageDurationMs(): Float {
            return if (requestCount > 0) {
                totalDurationMs.toFloat() / requestCount
            } else {
                0f
            }
        }

        fun getSuccessRate(): Float {
            return if (requestCount > 0) {
                successCount.toFloat() / requestCount
            } else {
                0f
            }
        }
    }

    /**
     * Manager for tracking connection quality metrics.
     * Designed to work with minimal permissions (only ACCESS_NETWORK_STATE).
     */
    inner class ConnectionQualityManager {
        private var connectivityManager: ConnectivityManager? = null
        private var context: Context? = null
        private var hasNetworkStatePermission = false

        /**
         * Initialize the connection quality manager with application context.
         */
        fun initialize(context: Context) {
            this.context = context.applicationContext

            // Check if we have the required permission
            hasNetworkStatePermission = context.checkSelfPermission(
                android.Manifest.permission.ACCESS_NETWORK_STATE) == PackageManager.PERMISSION_GRANTED

            if (hasNetworkStatePermission) {
                connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            } else {
                Log.w(TAG, "Network monitoring limited: ACCESS_NETWORK_STATE permission not granted")
            }
        }

        /**
         * Get the current connection quality.
         */
        fun getCurrentQuality(): ConnectionQuality {
            // If we don't have permission, return unknown values
            if (!hasNetworkStatePermission) {
                return ConnectionQuality("unknown", -1)
            }

            val connectionType = getConnectionType()
            val signalStrength = getSignalStrength()

            return ConnectionQuality(connectionType, signalStrength)
        }

        private fun getConnectionType(): String {
            return try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    // Use newer API for Android M and above
                    val network = connectivityManager?.activeNetwork
                    val capabilities = connectivityManager?.getNetworkCapabilities(network)

                    when {
                        capabilities == null -> "none"
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> "bluetooth"
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "vpn"
                        else -> "other"
                    }
                } else {
                    // Use deprecated API for older Android versions
                    @Suppress("DEPRECATION")
                    val netInfo = connectivityManager?.activeNetworkInfo

                    when {
                        netInfo == null || !netInfo.isConnected -> "none"
                        @Suppress("DEPRECATION")
                        netInfo.type == ConnectivityManager.TYPE_WIFI -> "wifi"
                        @Suppress("DEPRECATION")
                        netInfo.type == ConnectivityManager.TYPE_MOBILE -> "cellular"
                        @Suppress("DEPRECATION")
                        netInfo.type == ConnectivityManager.TYPE_ETHERNET -> "ethernet"
                        @Suppress("DEPRECATION")
                        netInfo.type == ConnectivityManager.TYPE_BLUETOOTH -> "bluetooth"
                        @Suppress("DEPRECATION")
                        netInfo.type == ConnectivityManager.TYPE_VPN -> "vpn"
                        else -> "other"
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error determining connection type", e)
                "unknown"
            }
        }

        private fun getSignalStrength(): Int {
            return try {
                // We only attempt to get WiFi signal strength as it doesn't require special permissions
                if (getConnectionType() == "wifi") {
                    val wifiManager = context?.getSystemService(Context.WIFI_SERVICE) as? WifiManager

                    if (wifiManager != null) {
                        @Suppress("DEPRECATION")
                        val rssi = wifiManager.connectionInfo?.rssi ?: -100
                        WifiManager.calculateSignalLevel(rssi, 5)
                    } else {
                        -1
                    }
                } else {
                    // For other connection types, we return -1 to indicate we can't measure
                    -1
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error measuring signal strength", e)
                -1
            }
        }
    }

    /**
     * Data class representing connection quality.
     */
    data class ConnectionQuality(
        val connectionType: String,
        val signalStrength: Int
    )
}