plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.bytebuddy)
    id("maven-publish")
}

android {
    namespace = "com.nathanclair.edgetelemetrysdk"
    compileSdk = 34

    defaultConfig {
        minSdk = 24

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    //okhttp
    implementation(libs.okhttp)

    //Open Telemetry
    implementation(libs.opentelemetry.android.agent)
    // Additional OpenTelemetry dependencies
    implementation("io.opentelemetry:opentelemetry-api:1.35.0")
    implementation("io.opentelemetry:opentelemetry-sdk:1.35.0")
    implementation("io.opentelemetry:opentelemetry-sdk-trace:1.35.0")
    implementation("io.opentelemetry:opentelemetry-exporter-otlp:1.35.0")
    implementation("io.opentelemetry.proto:opentelemetry-proto:1.1.0-alpha")
    implementation("io.opentelemetry.instrumentation:opentelemetry-instrumentation-api:1.35.0")
    implementation("io.opentelemetry:opentelemetry-exporter-otlp:1.35.0")
    implementation("io.opentelemetry.proto:opentelemetry-proto:1.1.0-alpha")
    implementation("io.opentelemetry.android.instrumentation:sessions:0.11.0-alpha")
    implementation("io.opentelemetry:opentelemetry-api-incubator:1.31.0-alpha")
    implementation("io.opentelemetry.android.instrumentation:okhttp3-library:0.11.0-alpha")
    byteBuddy("io.opentelemetry.android.instrumentation:okhttp3-agent:0.11.0-alpha")

}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])

                groupId = "com.github.mktowett"
                artifactId = "edge-telemetry-sdk"
                version = "0.1.5"
            }
        }

        repositories {
            maven {
                name = "localRepo"
                url = uri("${layout.buildDirectory}/repo")
            }
            mavenLocal()
        }
    }
}