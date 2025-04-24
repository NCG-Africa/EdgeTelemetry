package com.nathanclair.edgetelemetrysdk.util

import android.util.Log
import io.opentelemetry.android.config.OtelRumConfig

/**
 * Utility class for backward compatibility
 */
object CompatUtil {
    private const val TAG = "CompatUtil"

    /**
     * Sets the slow rendering detection poll interval in a backward compatible way
     */
    fun setSlowRenderingDetectionPollInterval(config: OtelRumConfig, millis: Long) {
        try {
            // Use reflection to find the appropriate method
            val methods = config.javaClass.methods
            
            // Find a method that takes a Long parameter
            val longMethod = methods.find { it.name == "setSlowRenderingDetectionPollInterval" && 
                                         it.parameterTypes.size == 1 && 
                                         it.parameterTypes[0] == Long::class.javaPrimitiveType }
            
            if (longMethod != null) {
                // Call the method that accepts a Long directly
                longMethod.invoke(config, millis)
                return
            }
            
            // Find a method that might take a Duration parameter
            val otherMethod = methods.find { it.name == "setSlowRenderingDetectionPollInterval" &&
                                          it.parameterTypes.size == 1 }
            
            if (otherMethod != null) {
                // Try to create a Duration object via reflection
                try {
                    val durationClass = Class.forName("java.time.Duration")
                    val ofMillisMethod = durationClass.getMethod("ofMillis", Long::class.javaPrimitiveType)
                    val duration = ofMillisMethod.invoke(null, millis)
                    
                    // Call the method with the Duration object
                    otherMethod.invoke(config, duration)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to create Duration object: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set slow rendering detection poll interval: ${e.message}")
        }
    }

    /**
     * Sets the ANR detection interval in a backward compatible way
     */
    fun setAnrDetectionInterval(config: OtelRumConfig, millis: Long) {
        try {
            val method = config.javaClass.getMethod("setAnrDetectionInterval", Long::class.javaPrimitiveType)
            method.invoke(config, millis)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set ANR detection interval: ${e.message}")
        }
    }

    /**
     * Sets the ANR detection threshold in a backward compatible way
     */
    fun setAnrDetectionThreshold(config: OtelRumConfig, millis: Long) {
        try {
            val method = config.javaClass.getMethod("setAnrDetectionThreshold", Long::class.javaPrimitiveType)
            method.invoke(config, millis)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set ANR detection threshold: ${e.message}")
        }
    }
}