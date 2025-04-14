package com.nathanclair.edgetelemetrysdk

interface EdgeSpan {
    // Add attributes to the span
    fun setAttribute(key: String, value: String): EdgeSpan

    // Record an error
    fun recordError(throwable: Throwable): EdgeSpan

    // Start and end the span
    fun start(): EdgeSpan
    fun end()
}