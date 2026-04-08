package com.holderzone.build_logic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

class AndroidSampleAppConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.application")
        pluginManager.apply("org.jetbrains.kotlin.android")
        pluginManager.apply("com.holderzone.android.compose")
        pluginManager.apply("com.google.devtools.ksp")
        pluginManager.apply("com.google.dagger.hilt.android")

        configureSampleAppDefaults()
        configureKotlinAndroid()

        dependencies {
            "implementation"(libs.findLibrary("androidx.core.ktx").get())
            "implementation"(libs.findLibrary("androidx.lifecycle.runtime.ktx").get())
            "implementation"(libs.findLibrary("androidx.lifecycle.viewmodel.ktx").get())
            "implementation"(libs.findLibrary("androidx.activity.compose").get())
            "implementation"(libs.findLibrary("hilt.android").get())
            "implementation"(libs.findLibrary("hilt.navigation.compose").get())
            "implementation"(libs.findLibrary("kotlinx.coroutines.android").get())
            "ksp"(libs.findLibrary("hilt.android.compiler").get())
            "debugImplementation"(libs.findLibrary("androidx.compose.ui.tooling").get())
        }
    }
}
