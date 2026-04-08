package com.holderzone.build_logic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.BasePluginExtension

class AndroidCameraFamilyLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.holderzone.android.library")

            val versionInfo = loadCameraFamilyVersionInfo()
            version = versionInfo.versionName
            extensions.extraProperties["moduleVersionCode"] = versionInfo.versionCode
            extensions.extraProperties["cameraFamilyVersionName"] = versionInfo.versionName
            extensions.findByType(BasePluginExtension::class.java)?.archivesName?.set(
                "${project.name}-${versionInfo.versionName}"
            )

            tasks.register("printModuleVersion") {
                group = "versioning"
                description = "Prints the module artifact and version information."
                doLast {
                    println("${project.path} -> ${project.name}:${versionInfo.versionName} (${versionInfo.versionCode})")
                }
            }
        }
    }
}
