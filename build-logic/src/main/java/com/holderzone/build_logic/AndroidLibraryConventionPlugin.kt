package com.holderzone.build_logic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.library")
        pluginManager.apply("org.jetbrains.kotlin.android")

        configureLibraryDefaults()
        configureKotlinAndroid()

        dependencies {
            "implementation"(libs.findLibrary("androidx.core.ktx").get())
            "implementation"(libs.findLibrary("androidx.appcompat").get())
            "implementation"(libs.findLibrary("material").get())
            "implementation"(libs.findLibrary("kotlinx.coroutines.android").get())
            "testImplementation"(libs.findLibrary("junit").get())
            "androidTestImplementation"(libs.findLibrary("androidx.junit").get())
            "androidTestImplementation"(libs.findLibrary("androidx.espresso.core").get())
        }
    }
}
