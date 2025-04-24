package com.nathanclair.edgetelemetrysdk.exporters

import android.util.Log
import com.nathanclair.edgetelemetrysdk.core.EdgeTelemetry
import io.opentelemetry.api.common.AttributesBuilder
import io.opentelemetry.api.logs.Severity

/**
 * User identity management for EdgeTelemetry
 */
class EdgeTelemetryUser private constructor() {
    companion object {
        private const val TAG = "EdgeTelemetryUser"
        private var userId: String? = null
        private var userAttributes: Map<String, String> = emptyMap()

        /**
         * Set the user ID for telemetry data
         * @param id The unique identifier for the user
         * @param attributes Optional map of additional user attributes
         */
        @JvmStatic
        fun identify(id: String?, attributes: Map<String, String> = emptyMap()) {
            try {
                // Store the user information
                userId = id
                userAttributes = attributes

                if (!EdgeTelemetry.isInitialized()) {
                    Log.w(TAG, "EdgeTelemetry not initialized yet, user will be associated after initialization")
                    return
                }

                // Create a user identification event
                val logger = EdgeTelemetry.getLogger("user.identity")
                logger?.logRecordBuilder()
                    ?.setSeverity(Severity.INFO)
                    ?.setBody("User identified")
                    ?.also { builder ->
                        if (id != null) {
                            builder.setAttribute("user.id", id)
                        }
                        attributes.forEach { (key, value) ->
                            builder.setAttribute("user.$key", value)
                        }
                    }
                    ?.emit()

                Log.d(TAG, "User identified: $id")
            } catch (e: Exception) {
                Log.e(TAG, "Error identifying user", e)
            }
        }

        /**
         * Clear the current user identification
         */
        @JvmStatic
        fun clearUser() {
            identify(null)
        }

        /**
         * Get the current user ID
         */
        @JvmStatic
        fun getUserId(): String? {
            return userId
        }

        /**
         * Get current user attributes
         */
        @JvmStatic
        fun getUserAttributes(): Map<String, String> {
            return userAttributes
        }

        /**
         * Apply user attributes to a span or log
         * @param builder The attribute-capable builder to enhance with user attributes
         */
        @JvmStatic
        internal fun <T> applyUserAttributes(builder: T): T where T : AttributesBuilder {
            if (userId != null) {
                builder.put("user.id", userId!!)
            }

            userAttributes.forEach { (key, value) ->
                builder.put("user.$key", value)
            }

            return builder
        }
    }
}