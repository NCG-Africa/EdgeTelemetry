package com.nathanclair.edgetelemetrysdk.model

data class EdgeConfig(
    var collectorUrl: String = "https://your-default-collector:4317",
    var serviceName: String = "android-app",
    var useHttpProtocol: Boolean = true
)
