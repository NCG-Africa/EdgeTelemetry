package com.nathanclair.click_logger_plugin

import com.android.build.api.instrumentation.InstrumentationParameters
import com.android.build.api.instrumentation.InstrumentationScope
import com.android.build.api.variant.AndroidComponentsExtension
import org.gradle.api.Plugin
import org.gradle.api.Project

class ClickLoggerPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        // Ensure this plugin only applies to Android projects
        project.plugins.withId("com.android.application") {
            val androidComponents = project.extensions.getByType(AndroidComponentsExtension::class.java)

            androidComponents.onVariants { variant ->
                variant.instrumentation.transformClassesWith(
                    ClickLoggerTransformer::class.java,
                    InstrumentationScope.ALL // 👈 Add this line
                ) {}
            }
        }
    }

}