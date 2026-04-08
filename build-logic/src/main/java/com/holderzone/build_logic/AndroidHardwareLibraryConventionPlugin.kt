package com.holderzone.build_logic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.BasePluginExtension

class AndroidHardwareLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.holderzone.android.library")

            val versionInfo = loadVersionInfo(project.file("version.properties"))
            version = versionInfo.versionName
            project.extensions.extraProperties["moduleVersionCode"] = versionInfo.versionCode
            project.extensions.extraProperties["artifactId"] = versionInfo.artifactId.orEmpty()
            extensions.findByType(BasePluginExtension::class.java)?.archivesName?.set(
                "${versionInfo.artifactId}-${versionInfo.versionName}"
            )

            tasks.register("printModuleVersion") {
                group = "versioning"
                description = "Prints the module artifact and version information."
                doLast {
                    println("${project.path} -> ${versionInfo.artifactId}:${versionInfo.versionName} (${versionInfo.versionCode})")
                }
            }
        }
    }
}
