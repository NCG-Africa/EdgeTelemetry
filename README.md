# EdgeTelemetry SDK

A convenient wrapper around OpenTelemetry Android that provides automatic instrumentation and real user monitoring (RUM) for Android applications.

## Features

- Simple one-line initialization
- Automatic instrumentation of:
    - Application not responding (ANR) detection
    - Crash reporting
    - Network state changes
    - Slow rendering detection
    - HTTP request monitoring (OkHttp3)
    - User sessions
- Disk buffering for offline telemetry
- Custom event tracking
- Screen view tracking
- Error tracking

## Installation

Add the EdgeTelemetry SDK to your project:

```gradle
// In your app's build.gradle.kts
plugins {
    id("net.bytebuddy.byte-buddy-gradle-plugin")  // Required for OkHttp auto-instrumentation
}

dependencies {
    implementation("com.nathanclair:edgetelemetrysdk:1.0.0")
}
```

## Quick Start

Initialize EdgeTelemetry in your Application class:

```kotlin
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Initialize with default configuration (localhost endpoints)
        EdgeTelemetry.getInstance().initialize(this)
        
        // OR with custom endpoints
        EdgeTelemetry.getInstance().initialize(
            application = this,
            tracesEndpoint = "https://your-collector.com/v1/traces",
            logsEndpoint = "https://your-collector.com/v1/logs"
        )
    }
}
```

That's it! You now have automatic instrumentation for your app, including:
- OkHttp requests
- Application not responding (ANR) detection
- Crash reporting
- And more...

## Advanced Configuration

For more control, use the configuration builder:

```kotlin
val config = EdgeTelemetryConfig.builder()
    .setAppName("YourAppName")
    .setTracesEndpoint("https://your-collector.com/v1/traces")
    .setLogsEndpoint("https://your-collector.com/v1/logs")
    .addAttribute("environment", "production")
    .disableInstrumentation(InstrumentationType.SLOW_RENDERING) // Optional: disable specific instrumentations
    .build()

EdgeTelemetry.getInstance().initialize(application, config)
```

## Manual Tracking

EdgeTelemetry provides methods for manual tracking:

```kotlin
// Track screen views
EdgeTelemetry.getInstance().trackScreenView("HomeScreen")

// Track user identity
EdgeTelemetry.getInstance().setUserId("user-123")

// Track errors
try {
    // Some code that might throw
} catch (e: Exception) {
    EdgeTelemetry.getInstance().trackError(e)
}

// Track custom events
EdgeTelemetry.getInstance().trackEvent(
    "button_clicked", 
    mapOf("button_id" to "login_button", "screen" to "login")
)
```

## Manual Instrumentation

You can also access the underlying OpenTelemetry instance for advanced use cases:

```kotlin
val openTelemetry = EdgeTelemetry.getInstance().getOpenTelemetry()
val tracer = EdgeTelemetry.getInstance().getTracer("custom-component")

// Use standard OpenTelemetry APIs
val span = tracer?.spanBuilder("custom-operation")?.startSpan()
try {
    // Your code here
} finally {
    span?.end()
}
```

## License

[Your License]